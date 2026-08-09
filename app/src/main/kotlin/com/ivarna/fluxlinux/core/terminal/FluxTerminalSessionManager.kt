package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import com.ivarna.fluxlinux.core.data.Distro
import kotlinx.coroutines.flow.StateFlow
import com.termux.view.TerminalView

/**
 * Thin facade over [SessionRegistry] + [InstallSessionFactory] +
 * [GuestSessionFactory] + [UninstallSessionFactory] (Pass 2 split, plan §2.5).
 *
 * Call sites (TerminalScreen / MainActivity / HomeScreen) keep using this object;
 * product behavior is unchanged. All session-open paths require an explicit
 * `method` for card actions — see each factory.
 */
object FluxTerminalSessionManager {

    const val MAX_TABS = SessionRegistry.MAX_TABS

    /**
     * Outcome of a session-open request — lets UI show distinct toasts
     * (R3: tab limit vs host prepare vs open failure).
     */
    enum class SessionOpenResult {
        /** Session opened and attached to a tab. */
        OPENED,
        /** Tab limit reached (MAX_TABS). */
        MAX_TABS,
        /** Host bootstrap extract / setup_termux failed (guest paths). */
        HOST_PREPARE_FAILED,
        /** Embedded host not ready and prepare failed (host shell). */
        HOST_NOT_READY,
        /** Unexpected registry add failure. */
        OPEN_FAILED
    }

    val activeIndex: StateFlow<Int> get() = SessionRegistry.activeIndex
    val revision: StateFlow<Int> get() = SessionRegistry.revision
    val sessionCount: Int get() = SessionRegistry.sessionCount
    val activeSession: SessionRegistry.ManagedSession? get() = SessionRegistry.activeSession

    fun isOpen(): Boolean = SessionRegistry.isOpen()
    fun titles(): List<String> = SessionRegistry.titles()

    fun switchSession(index: Int) = SessionRegistry.switchSession(index)
    fun closeSession(ctx: Context, index: Int) = SessionRegistry.closeSession(ctx, index)
    fun closeAll(ctx: Context) = SessionRegistry.closeAll(ctx)
    fun attachView(view: TerminalView) = SessionRegistry.attachView(view)
    fun detachView() = SessionRegistry.detachView()

    /**
     * Interactive guest session after ensuring the host is prepared. Use from UI threads.
     * [method] MUST come from `terminalComponentFor(distroId).method` for card actions (plan §2.6).
     * Reports a distinct [SessionOpenResult] so the UI can toast the right failure (R3).
     */
    fun openSessionAfterHost(
        ctx: Context,
        type: String,
        title: String = type,
        shellCmd: String = "exec zsh",
        method: String,
        onResult: (SessionOpenResult) -> Unit = {}
    ) {
        if (!SessionRegistry.hasFreeTab()) {
            onResult(SessionOpenResult.MAX_TABS)
            return
        }
        TerminalLauncher.prepareHost(ctx) { ok ->
            if (!ok) {
                onResult(SessionOpenResult.HOST_PREPARE_FAILED)
                return@prepareHost
            }
            onResult(
                if (GuestSessionFactory.openSession(ctx, type, title, shellCmd, method)) {
                    SessionOpenResult.OPENED
                } else {
                    SessionOpenResult.OPEN_FAILED
                }
            )
        }
    }

    /** Interactive guest session (host already prepared). */
    fun openSession(
        ctx: Context,
        type: String,
        title: String = type,
        shellCmd: String = "exec zsh",
        method: String
    ): Boolean = GuestSessionFactory.openSession(ctx, type, title, shellCmd, method)

    /**
     * Interactive host (embedded Termux prefix) shell — HOST selector card.
     * Synchronous fast path; callers that need the R4 host-ready gate should use
     * [openHostShellAfterReady] instead.
     */
    fun openHostShell(ctx: Context, title: String = "Host Shell"): Boolean =
        GuestSessionFactory.openHostShell(ctx, title)

    /**
     * Interactive host shell after ensuring the embedded host is ready (async).
     * Probes libbash + bootstrap extraction; prepares the host when missing (R4).
     */
    fun openHostShellAfterReady(
        ctx: Context,
        title: String = "Host Shell",
        onResult: (SessionOpenResult) -> Unit = {}
    ) {
        if (!SessionRegistry.hasFreeTab()) {
            onResult(SessionOpenResult.MAX_TABS)
            return
        }
        if (GuestSessionFactory.hostShellReady(ctx)) {
            onResult(
                if (GuestSessionFactory.openHostShell(ctx, title)) {
                    SessionOpenResult.OPENED
                } else {
                    SessionOpenResult.OPEN_FAILED
                }
            )
            return
        }
        TerminalLauncher.prepareHost(ctx) { ok ->
            if (!ok) {
                onResult(SessionOpenResult.HOST_NOT_READY)
                return@prepareHost
            }
            onResult(
                if (GuestSessionFactory.openHostShell(ctx, title)) {
                    SessionOpenResult.OPENED
                } else {
                    SessionOpenResult.OPEN_FAILED
                }
            )
        }
    }

    /** Host script session (e.g. `flux_install.sh debian`). */
    fun openHostScriptSession(
        ctx: Context,
        scriptName: String,
        title: String = scriptName,
        args: Array<String> = emptyArray(),
        forceHostSetup: Boolean = false,
        onFinished: (() -> Unit)? = null
    ): Boolean =
        InstallSessionFactory.openHostScriptSession(
            ctx, scriptName, title, args, forceHostSetup, onFinished
        )

    /** Host command session (e.g. `proot-distro remove debian`). */
    fun openHostCommandSession(
        ctx: Context,
        command: String,
        title: String = "Host Shell"
    ): Boolean = InstallSessionFactory.openHostCommandSession(ctx, command, title)

    /** Root shell session running [scriptPath] on the host via su. */
    fun openRootScriptSession(
        ctx: Context,
        scriptPath: String,
        title: String = "Root Shell",
        onFinished: (() -> Unit)? = null
    ): Boolean = InstallSessionFactory.openRootScriptSession(ctx, scriptPath, title, onFinished)

    /** Distro install session (proot → flux_install.sh; chroot → setup script as root). */
    fun openInstallSession(
        ctx: Context,
        distro: Distro,
        setupB64: String? = null,
        onFinished: (() -> Unit)? = null
    ): Boolean = InstallSessionFactory.openInstallSession(ctx, distro, setupB64, onFinished)

    /** Distro uninstall session. */
    fun openUninstallSession(ctx: Context, distro: Distro): Boolean =
        UninstallSessionFactory.openUninstallSession(ctx, distro)

    /** Component install/uninstall session inside the guest. */
    fun openComponentSession(
        ctx: Context,
        distro: Distro,
        scriptContent: String,
        title: String,
        extraEnv: Map<String, String> = emptyMap(),
        isUninstall: Boolean = false,
        onFinished: (() -> Unit)? = null
    ): Boolean = GuestSessionFactory.openComponentSession(
        ctx, distro, scriptContent, title, extraEnv, isUninstall, onFinished
    )
}
