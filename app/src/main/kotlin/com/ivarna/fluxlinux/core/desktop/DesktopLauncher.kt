package com.ivarna.fluxlinux.core.desktop

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.ivarna.fluxlinux.core.data.terminalComponentFor
import com.ivarna.fluxlinux.core.service.DesktopSessionService
import com.ivarna.fluxlinux.core.terminal.HostCommandBuilder
import com.ivarna.fluxlinux.core.terminal.ShellCommandRunner
import com.ivarna.fluxlinux.core.terminal.TerminalLauncher
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths
import com.ivarna.fluxlinux.core.utils.StateManager
import com.ivarna.fluxlinux.core.utils.TermuxX11Preferences
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Start/stop XFCE via embedded host scripts + in-process Termux:X11
 * (nativecode-ai parity — no external Termux / Termux:X11).
 *
 * Fail-closed: missing script or early exit (any code) does not open X11 or
 * mark GUI running. A real XFCE session keeps the start script alive after
 * preflight; short-lived exit is always treated as failure.
 *
 * GUI running flags are owned here so call sites do not reimplement them.
 */
object DesktopLauncher {

    private const val TAG = "DesktopLauncher"
    /**
     * Preflight wait: host X11 + guest start usually finish in ~4s;
     * missing startxfce4 exits earlier. Only "still alive" counts as success.
     */
    private const val PREFLIGHT_SECONDS = 6L

    private val executor = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private val runningStartProcess = AtomicReference<Process?>(null)

    /**
     * @param onResult invoked on main thread after flags are updated.
     *   true = desktop accepted (script still running after preflight).
     */
    fun start(ctx: Context, distroId: String, onResult: ((Boolean) -> Unit)? = null) {
        val app = ctx.applicationContext
        TerminalLauncher.prepareHost(app) { ok ->
            if (!ok) {
                Toast.makeText(app, "Host not ready — open Settings to initialize", Toast.LENGTH_LONG).show()
                finishStart(app, distroId, false, onResult)
                return@prepareHost
            }
            val method = try {
                terminalComponentFor(distroId).method
            } catch (_: Exception) {
                "proot"
            }
            if (method == "chroot" && !TerminalLauncher.isDebianChrootInstalled()) {
                Toast.makeText(app, "Chroot not installed. Install Debian (Rooted) first.", Toast.LENGTH_LONG).show()
                finishStart(app, distroId, false, onResult)
                return@prepareHost
            }
            if (method != "chroot" && !TerminalLauncher.isDebianProotInstalled(app)) {
                Toast.makeText(app, "Debian not installed. Complete onboarding first.", Toast.LENGTH_LONG).show()
                finishStart(app, distroId, false, onResult)
                return@prepareHost
            }

            val scriptName = if (method == "chroot") "start_gui_chroot.sh" else "start_gui.sh"
            val script = File(TermuxHostPaths.HOME, scriptName)
            if (!script.isFile) {
                Log.e(TAG, "Missing $scriptName at ${script.absolutePath}")
                Toast.makeText(
                    app,
                    "Desktop scripts missing — re-run host initialize in Settings",
                    Toast.LENGTH_LONG
                ).show()
                finishStart(app, distroId, false, onResult)
                return@prepareHost
            }
            if (method == "chroot") {
                val guest = File(TermuxHostPaths.HOME, "start_debian13_gui.sh")
                if (!guest.isFile) {
                    Log.e(TAG, "Missing start_debian13_gui.sh")
                    Toast.makeText(
                        app,
                        "Chroot desktop launcher missing — re-run host initialize",
                        Toast.LENGTH_LONG
                    ).show()
                    finishStart(app, distroId, false, onResult)
                    return@prepareHost
                }
            }

            Toast.makeText(app, "Starting desktop…", Toast.LENGTH_SHORT).show()

            executor.execute {
                val bash = TermuxHostPaths.libBash(app).absolutePath
                Log.i(TAG, "startGui method=$method script=${script.absolutePath}")
                val pb = ProcessBuilder(bash, script.absolutePath, "debian")
                pb.redirectErrorStream(true)
                runCatching { pb.directory(File(TermuxHostPaths.BIN)) }
                HostCommandBuilder.applyTo(app, pb, forceHostSetup = false)

                val proc = try {
                    pb.start()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start desktop script", e)
                    main.post {
                        Toast.makeText(app, "Desktop start failed: ${e.message}", Toast.LENGTH_LONG).show()
                        finishStart(app, distroId, false, onResult)
                    }
                    return@execute
                }
                runningStartProcess.getAndSet(proc)?.destroyForcibly()

                executor.execute {
                    try {
                        proc.inputStream.bufferedReader().use { reader ->
                            reader.lineSequence().forEach { line ->
                                if (line.isNotBlank()) Log.d(TAG, "gui: $line")
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                val exitedEarly = try {
                    proc.waitFor(PREFLIGHT_SECONDS, TimeUnit.SECONDS)
                } catch (_: Exception) {
                    true
                }

                if (exitedEarly) {
                    val code = try {
                        proc.exitValue()
                    } catch (_: Exception) {
                        -1
                    }
                    runningStartProcess.compareAndSet(proc, null)
                    Log.w(TAG, "Desktop script exited during preflight exit=$code (fail-closed)")
                    main.post {
                        stopFgs(app)
                        Toast.makeText(
                            app,
                            if (code != 0) {
                                "Desktop failed to start (exit $code). Check logs."
                            } else {
                                "Desktop exited immediately — XFCE did not stay running."
                            },
                            Toast.LENGTH_LONG
                        ).show()
                        finishStart(app, distroId, false, onResult)
                    }
                    return@execute
                }

                // Still alive after preflight → DE session running
                Log.i(TAG, "Desktop script still running after ${PREFLIGHT_SECONDS}s — opening display")
                main.post {
                    val fgsOk = startFgs(app)
                    if (!fgsOk) {
                        Toast.makeText(
                            app,
                            "Desktop keep-alive service failed — display may sleep",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    // Script also am-starts X11; reopen for reliability (singleTop)
                    openX11(app)
                    finishStart(app, distroId, true, onResult)
                }
            }
        }
    }

    fun stop(ctx: Context, distroId: String, onDone: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        val method = try {
            terminalComponentFor(distroId).method
        } catch (_: Exception) {
            "proot"
        }
        try {
            val stopBroadcast = Intent("com.termux.x11.ACTION_STOP")
            stopBroadcast.setPackage(app.packageName)
            app.sendBroadcast(stopBroadcast)
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_STOP failed", e)
        }
        stopFgs(app)
        runningStartProcess.getAndSet(null)?.destroyForcibly()

        // Clear flags immediately so UI does not offer Stop while dying
        StateManager.setGuiRunning(app, distroId, false)
        StateManager.setGuiRunningType(app, distroId, "")

        executor.execute {
            val bash = TermuxHostPaths.libBash(app).absolutePath
            val scriptName = if (method == "chroot") "stop_gui_chroot.sh" else "stop_gui.sh"
            val script = File(TermuxHostPaths.HOME, scriptName)
            Log.i(TAG, "stopGui method=$method script=${script.absolutePath}")
            try {
                if (script.isFile) {
                    ShellCommandRunner.run(app, arrayOf(bash, script.absolutePath, "debian"))
                } else {
                    Log.w(TAG, "Stop script missing: ${script.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "stop_gui failed", e)
            }
            main.post { onDone?.invoke() }
        }
        Toast.makeText(app, "Stopping desktop…", Toast.LENGTH_SHORT).show()
    }

    /** Re-open the X11 surface without restarting the DE. */
    fun reopenDisplay(ctx: Context) {
        openX11(ctx.applicationContext)
    }

    private fun finishStart(
        app: Context,
        distroId: String,
        ok: Boolean,
        onResult: ((Boolean) -> Unit)?
    ) {
        if (ok) {
            StateManager.setGuiRunning(app, distroId, true)
            StateManager.setGuiRunningType(app, distroId, "xfce4")
            runCatching { TermuxX11Preferences.applyToTermux(app) }
        }
        onResult?.invoke(ok)
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
            false
        }
    }

    private fun openX11(app: Context) {
        try {
            val x11 = Intent(app, com.termux.x11.MainActivity::class.java)
            x11.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            app.startActivity(x11)
        } catch (e: Exception) {
            Log.e(TAG, "Open X11 activity failed", e)
            Toast.makeText(app, "Failed to open display: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopFgs(app: Context) {
        try {
            app.stopService(Intent(app, DesktopSessionService::class.java))
        } catch (_: Exception) {
        }
    }
}
