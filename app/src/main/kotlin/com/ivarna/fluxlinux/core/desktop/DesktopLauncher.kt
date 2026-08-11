package com.ivarna.fluxlinux.core.desktop

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.ivarna.fluxlinux.core.data.terminalComponentFor
import com.ivarna.fluxlinux.core.service.DesktopSessionService
import com.ivarna.fluxlinux.core.terminal.HostScriptDeployer
import com.ivarna.fluxlinux.core.terminal.ShellCommandRunner
import com.ivarna.fluxlinux.core.terminal.ShellJob
import com.ivarna.fluxlinux.core.terminal.TerminalLauncher
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths
import com.ivarna.fluxlinux.core.utils.StateManager
import com.ivarna.fluxlinux.core.utils.TermuxX11Preferences
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Start/stop XFCE via embedded host scripts + in-process Termux:X11
 * (termux-lib / nativecode-ai parity — no external Termux / Termux:X11).
 *
 * Long-lived start uses [ShellCommandRunner.runStreamedCancelable] so other
 * install/script runners are never blocked. X11 opens on the first healthy
 * script line (not a blind preflight sleep). Desktop stdout is captured to
 * [GuiDesktopLog] for VIEW LOGS.
 */
object DesktopLauncher {

    private const val TAG = "DesktopLauncher"
    private val main = Handler(Looper.getMainLooper())

    enum class Phase { Idle, Starting, Running }

    data class UiState(
        val phase: Phase = Phase.Idle,
        val distroId: String? = null,
        /** Rolling in-memory transcript for live UI (mirrors file). */
        val logText: String = "",
        val logsAvailable: Boolean = false,
        /** True after first healthy start line — Open X11 is meaningful. */
        val displayReady: Boolean = false,
        /** One-shot signal for UI to auto-open log sheet when start begins / fails. */
        val autoShowLogsTick: Int = 0,
        val lastError: String? = null
    )

    private val _ui = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _ui.asStateFlow()

    @Volatile private var guiShellJob: ShellJob? = null
    @Volatile private var guiUserStopping: Boolean = false
    @Volatile private var guiX11Launched: Boolean = false
    private val healthyLineSeen = AtomicBoolean(false)

    /**
     * @param onResult main-thread; true when desktop accepted (first healthy line
     *   or still running — not early fail).
     */
    fun start(ctx: Context, distroId: String, onResult: ((Boolean) -> Unit)? = null) {
        val app = ctx.applicationContext
        TerminalLauncher.prepareHost(app) { ok ->
            if (!ok) {
                toast(app, "Host not ready — open Settings to initialize")
                finishStart(app, distroId, false, "Host not ready", onResult)
                return@prepareHost
            }
            // Redeploy host scripts so start_gui.sh matches this APK (termux-lib deployScripts).
            HostScriptDeployer.deployScripts(app)

            val method = methodFor(distroId)
            if (method == "chroot" && !TerminalLauncher.isDebianChrootInstalled()) {
                toast(app, "Chroot not installed. Install Debian (Rooted) first.")
                finishStart(app, distroId, false, "Chroot not installed", onResult)
                return@prepareHost
            }
            if (method != "chroot" && !TerminalLauncher.isDebianProotInstalled(app)) {
                toast(app, "Debian not installed. Complete onboarding first.")
                finishStart(app, distroId, false, "Debian not installed", onResult)
                return@prepareHost
            }

            val scriptName = if (method == "chroot") "start_gui_chroot.sh" else "start_gui.sh"
            val script = File(TermuxHostPaths.HOME, scriptName)
            if (!script.isFile) {
                GuiDesktopLog.header(app, "START", scriptName, method)
                GuiDesktopLog.append(app, "ERROR: missing host script ${script.absolutePath}")
                pushLog(app, forceShow = true)
                toast(app, "Desktop scripts missing — re-run host initialize")
                finishStart(app, distroId, false, "Missing $scriptName", onResult)
                return@prepareHost
            }
            if (method == "chroot") {
                val guest = File(TermuxHostPaths.HOME, "start_debian13_gui.sh")
                if (!guest.isFile) {
                    toast(app, "Chroot desktop launcher missing — re-run host initialize")
                    finishStart(app, distroId, false, "Missing start_debian13_gui.sh", onResult)
                    return@prepareHost
                }
            }

            guiShellJob?.cancel()
            guiUserStopping = false
            guiX11Launched = false
            healthyLineSeen.set(false)

            GuiDesktopLog.clear(app)
            GuiDesktopLog.header(app, "START", scriptName, method)
            val supportLib = File(TermuxHostPaths.LIB, "libandroid-support.so")
            if (!supportLib.isFile) {
                GuiDesktopLog.append(
                    app,
                    "WARN: missing ${supportLib.absolutePath} — libbash may fail to link"
                )
            }

            _ui.update {
                it.copy(
                    phase = Phase.Starting,
                    distroId = distroId,
                    logText = GuiDesktopLog.read(app),
                    logsAvailable = true,
                    displayReady = false,
                    lastError = null,
                    autoShowLogsTick = it.autoShowLogsTick + 1
                )
            }

            startFgs(app)
            toast(app, "Starting desktop…")
            Log.i(TAG, "startGui method=$method script=${script.absolutePath}")

            val bash = TermuxHostPaths.libBash(app).absolutePath
            val args = arrayOf(bash, script.absolutePath, "debian")

            guiShellJob = ShellCommandRunner.runStreamedCancelable(
                app,
                args,
                onLine = { line ->
                    GuiDesktopLog.append(app, line)
                    appendLive(line)
                    if (line.isNotBlank()) {
                        onHealthyLine(app, distroId, onResult)
                    }
                },
                onDone = { code ->
                    GuiDesktopLog.append(app, "[exit $code]")
                    appendLive("[exit $code]")
                    when {
                        code == -1 || guiUserStopping -> {
                            // User stop owns flip to idle
                        }
                        code != 0 -> {
                            Log.w(TAG, "Desktop start failed exit=$code")
                            toast(app, "Desktop start failed (exit $code) — see logs")
                            revertToIdle(app, distroId, "exit $code", showLogs = true)
                            onResult?.invoke(false)
                        }
                        else -> {
                            // Clean desktop exit (session ended)
                            Log.i(TAG, "Desktop session ended cleanly")
                            revertToIdle(app, distroId, null, showLogs = false)
                        }
                    }
                }
            )
        }
    }

    fun stop(ctx: Context, distroId: String, onDone: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        val method = methodFor(distroId)
        guiUserStopping = true

        try {
            val stopBroadcast = Intent("com.termux.x11.ACTION_STOP")
            stopBroadcast.setPackage(app.packageName)
            app.sendBroadcast(stopBroadcast)
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_STOP failed", e)
        }
        stopFgs(app)
        guiShellJob?.cancel()

        StateManager.setGuiRunning(app, distroId, false)
        StateManager.setGuiRunningType(app, distroId, "")

        val scriptName = if (method == "chroot") "stop_gui_chroot.sh" else "stop_gui.sh"
        HostScriptDeployer.deployScripts(app)
        val script = File(TermuxHostPaths.HOME, scriptName)
        Log.i(TAG, "stopGui method=$method script=${script.absolutePath}")
        GuiDesktopLog.header(app, "STOP", scriptName, method)
        appendLive("=== STOP method=$method script=$scriptName ===")

        if (!script.isFile) {
            GuiDesktopLog.append(app, "WARN: stop script missing ${script.absolutePath}")
            appendLive("WARN: stop script missing")
            _ui.update {
                it.copy(
                    phase = Phase.Idle,
                    displayReady = false,
                    logsAvailable = GuiDesktopLog.hasContent(app),
                    logText = GuiDesktopLog.read(app)
                )
            }
            toast(app, "Stopping desktop…")
            main.post { onDone?.invoke() }
            return
        }

        val bash = TermuxHostPaths.libBash(app).absolutePath
        toast(app, "Stopping desktop…")
        guiShellJob = ShellCommandRunner.runStreamedCancelable(
            app,
            arrayOf(bash, script.absolutePath, "debian"),
            onLine = { line ->
                GuiDesktopLog.append(app, line)
                appendLive(line)
            },
            onDone = { code ->
                GuiDesktopLog.append(app, "[exit $code]")
                appendLive("[exit $code]")
                _ui.update {
                    it.copy(
                        phase = Phase.Idle,
                        displayReady = false,
                        logsAvailable = GuiDesktopLog.hasContent(app),
                        logText = GuiDesktopLog.read(app)
                    )
                }
                onDone?.invoke()
            }
        )
        // Immediate idle for Open X11 button (stop stream may still print)
        _ui.update {
            it.copy(
                phase = Phase.Idle,
                displayReady = false,
                logsAvailable = true
            )
        }
    }

    /** Re-open the X11 surface without restarting the DE. */
    fun reopenDisplay(ctx: Context) {
        // Prefer Activity context so NEW_TASK is not required (BACK returns to Home).
        openX11(ctx)
    }

    fun readLog(ctx: Context): String = GuiDesktopLog.read(ctx.applicationContext)

    // ── private ────────────────────────────────────────────────────────────

    private fun onHealthyLine(app: Context, distroId: String, onResult: ((Boolean) -> Unit)?) {
        if (guiUserStopping) return
        // Only first healthy line flips RUNNING / prefs / X11 (match nativecode once-per-start).
        // Subsequent lines only append logs — avoid StateManager + TermuxX11 prefs thrash.
        val first = healthyLineSeen.compareAndSet(false, true)
        if (!first) return

        StateManager.setGuiRunning(app, distroId, true)
        StateManager.setGuiRunningType(app, distroId, "xfce4")
        runCatching { TermuxX11Preferences.applyToTermux(app) }

        _ui.update {
            it.copy(
                phase = Phase.Running,
                distroId = distroId,
                displayReady = true,
                logsAvailable = true
            )
        }

        onResult?.invoke(true)
        if (!guiX11Launched) {
            guiX11Launched = true
            // Match nativecode: short delay so X server process can bind
            main.postDelayed({
                if (guiUserStopping) return@postDelayed
                if (_ui.value.phase == Phase.Idle) return@postDelayed
                openX11(app)
            }, 400)
        }
    }

    private fun appendLive(line: String) {
        _ui.update { st ->
            val next = (st.logText + line + "\n").takeLast(24_000)
            st.copy(logText = next, logsAvailable = true)
        }
    }

    private fun pushLog(app: Context, forceShow: Boolean) {
        _ui.update {
            it.copy(
                logText = GuiDesktopLog.read(app),
                logsAvailable = GuiDesktopLog.hasContent(app),
                autoShowLogsTick = if (forceShow) it.autoShowLogsTick + 1 else it.autoShowLogsTick
            )
        }
    }

    private fun finishStart(
        app: Context,
        distroId: String,
        ok: Boolean,
        error: String?,
        onResult: ((Boolean) -> Unit)?
    ) {
        if (!ok) {
            _ui.update {
                it.copy(
                    phase = Phase.Idle,
                    distroId = distroId,
                    lastError = error,
                    displayReady = false
                )
            }
        }
        onResult?.invoke(ok)
    }

    private fun revertToIdle(
        app: Context,
        distroId: String,
        error: String?,
        showLogs: Boolean
    ) {
        stopFgs(app)
        StateManager.setGuiRunning(app, distroId, false)
        StateManager.setGuiRunningType(app, distroId, "")
        guiX11Launched = false
        healthyLineSeen.set(false)
        _ui.update {
            it.copy(
                phase = Phase.Idle,
                distroId = distroId,
                lastError = error,
                displayReady = false,
                logsAvailable = GuiDesktopLog.hasContent(app),
                logText = GuiDesktopLog.read(app),
                autoShowLogsTick = if (showLogs) it.autoShowLogsTick + 1 else it.autoShowLogsTick
            )
        }
    }

    private fun methodFor(distroId: String): String = try {
        terminalComponentFor(distroId).method
    } catch (_: Exception) {
        "proot"
    }

    private fun startFgs(app: Context): Boolean {
        return try {
            val fgs = Intent(app, DesktopSessionService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                app.startForegroundService(fgs)
            } else {
                app.startService(fgs)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Desktop FGS start failed", e)
            toast(app, "Desktop keep-alive service failed — display may sleep")
            false
        }
    }

    private fun stopFgs(app: Context) {
        try {
            app.stopService(Intent(app, DesktopSessionService::class.java))
        } catch (_: Exception) {
        }
    }

    private fun openX11(ctx: Context) {
        try {
            val x11 = Intent(ctx, com.termux.x11.MainActivity::class.java)
            // Match nativecode when started from an Activity (no NEW_TASK).
            // Application context (auto-open after healthy line) still needs NEW_TASK.
            if (ctx is android.app.Activity) {
                x11.addFlags(
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            } else {
                x11.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            ctx.startActivity(x11)
        } catch (e: Exception) {
            Log.e(TAG, "Open X11 activity failed", e)
            toast(ctx.applicationContext, "Failed to open display: ${e.message}")
        }
    }

    private fun toast(app: Context, msg: String) {
        main.post {
            Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
        }
    }
}
