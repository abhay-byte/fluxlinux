package com.ivarna.fluxlinux.core.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ivarna.fluxlinux.core.service.AppTerminalService
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * SessionRegistry — owns open terminal tabs, view attachment, session client,
 * and the FGS count. Factories (Install/Guest/Uninstall) build [TerminalSession]
 * instances and hand them to [add]; UI only talks to this registry.
 *
 * Split from the Pass 1 god-object `FluxTerminalSessionManager` (plan §2.5).
 */
object SessionRegistry {

    private const val TAG = "SessionRegistry"
    const val MAX_TABS = 10

    data class ManagedSession(
        val session: TerminalSession,
        val type: String,      // "shell" | "shell-root" | "host" | "install" | "component"
        val title: String,
        val method: String,    // "proot" | "chroot" | "host"
        val onFinished: (() -> Unit)? = null,
        /** Always fired when the process exits (any status, including signal). */
        val onClosed: ((exitStatus: Int) -> Unit)? = null,
        val distroId: String? = null,
        val iconRes: Int? = null,
    )

    private val sessionsList = ArrayList<ManagedSession>()

    private val _activeIndex = MutableStateFlow(-1)
    val activeIndex: StateFlow<Int> = _activeIndex

    /** Bumped on every add/remove so UI tab lists recompose. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    val sessionCount: Int get() = sessionsList.size

    /** Last terminal view attached (null when TerminalScreen is not shown). */
    @Volatile
    private var attachedView: TerminalView? = null

    @Volatile
    private var attachedClient: TerminalSessionClient? = null

    /** App context cached on first session add — needed by clipboard session client. */
    @Volatile
    private var appContext: Context? = null

    /** Background executor for paste (clipboard → emulator writes). */
    private val pasteExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    val activeSession: ManagedSession?
        get() = sessionsList.getOrNull(_activeIndex.value)

    fun isOpen(): Boolean = sessionsList.isNotEmpty()

    fun titles(): List<String> = sessionsList.map { it.title }

    fun sessions(): List<ManagedSession> = sessionsList.toList()

    fun hasFreeTab(): Boolean = sessionsList.size < MAX_TABS

    /** Add a session (factory-created) and focus it. */
    fun add(ctx: Context, managed: ManagedSession): Boolean {
        if (!hasFreeTab()) return false
        if (appContext == null) appContext = ctx.applicationContext
        sessionsList.add(managed)
        _revision.value++
        switchSession(sessionsList.size - 1)
        updateService(ctx)
        return true
    }

    fun switchSession(index: Int) {
        if (index < 0 || index >= sessionsList.size) {
            _activeIndex.value = -1
            return
        }
        _activeIndex.value = index
        attachedView?.let { view ->
            val session = sessionsList[index].session
            try {
                view.attachSession(session)
                view.onScreenUpdated()
            } catch (e: Exception) {
                Log.w(TAG, "attachSession failed on switch: ${e.message}")
            }
            // T3: focus parity — TerminalView must take touch + IME focus, never stay blank.
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.requestFocus()
        }
    }

    fun closeSession(ctx: Context, index: Int) {
        if (index < 0 || index >= sessionsList.size) return
        val managed = sessionsList[index]
        try {
            managed.session.finishIfRunning()
        } catch (_: Exception) {
        }
        sessionsList.removeAt(index)
        _revision.value++
        if (_activeIndex.value == index) {
            val next = if (sessionsList.isEmpty()) -1 else minOf(index, sessionsList.size - 1)
            switchSession(next)
        }
        updateService(ctx)
    }

    fun closeAll(ctx: Context) {
        for (m in sessionsList) {
            try {
                m.session.finishIfRunning()
            } catch (_: Exception) {
            }
        }
        sessionsList.clear()
        _revision.value++
        _activeIndex.value = -1
        updateService(ctx)
    }

    /** Attach the active session to a TerminalView (called when TerminalScreen shows). */
    fun attachView(view: TerminalView) {
        attachedView = view
        val idx = _activeIndex.value
        if (idx in sessionsList.indices) {
            try {
                view.attachSession(sessionsList[idx].session)
                view.onScreenUpdated()
            } catch (e: Exception) {
                Log.w(TAG, "attachSession failed on attach: ${e.message}")
            }
        }
        // T3: focus parity — TerminalView must take touch + IME focus, never stay blank.
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
    }

    fun detachView() {
        attachedView = null
    }

    /** Shared session client; fires per-session [ManagedSession.onFinished] callbacks. */
    fun sessionClient(): TerminalSessionClient {
        attachedClient?.let { return it }
        val client = object : TerminalSessionClient {
            override fun onTextChanged(session: TerminalSession) {
                if (session == activeSession?.session) {
                    attachedView?.onScreenUpdated()
                }
            }
            override fun onTitleChanged(session: TerminalSession) {}
            override fun onSessionFinished(session: TerminalSession) {
                Log.d(TAG, "Session finished: ${session.exitStatus}")
                val managed = sessionsList.find { it.session === session }
                // B3: success callbacks fire ONLY on clean exit — a failed
                // flux_install.sh / component script must never mark state installed.
                if (session.exitStatus == 0) {
                    managed?.onFinished?.invoke()
                }
                // Uninstall must refresh cards even when toybox/su dies with
                // SIGSEGV after the script already removed the rootfs.
                try {
                    managed?.onClosed?.invoke(session.exitStatus)
                } catch (e: Exception) {
                    Log.w(TAG, "onClosed failed: ${e.message}")
                }
            }
            // T3: clipboard parity (nativecode) — copy puts text on the system
            // clipboard; paste reads it and feeds the terminal emulator.
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                val ctx = appContext ?: return
                try {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("terminal", text))
                } catch (e: Exception) {
                    Log.w(TAG, "copy to clipboard failed: ${e.message}")
                }
            }
            override fun onPasteTextFromClipboard(session: TerminalSession) {
                val ctx = appContext ?: return
                val text = try {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                } catch (e: Exception) {
                    Log.w(TAG, "read clipboard failed: ${e.message}")
                    null
                } ?: return
                pasteExecutor.execute {
                    try {
                        session.emulator?.paste(text) ?: session.write(text)
                    } catch (e: Exception) {
                        Log.w(TAG, "paste failed: ${e.message}")
                    }
                }
            }
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun getTerminalCursorStyle(): Int? = 1
            override fun logError(tag: String, message: String) { Log.e(tag, message) }
            override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
            override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
            override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
            override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
            override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "Stacktrace", e) }
        }
        attachedClient = client
        return client
    }

    private fun updateService(ctx: Context) {
        val count = sessionsList.size
        val intent = Intent(ctx, AppTerminalService::class.java).apply {
            putExtra("SESSION_COUNT", count)
        }
        try {
            if (count > 0) {
                ctx.startForegroundService(intent)
            } else {
                ctx.stopService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateService failed", e)
        }
    }
}
