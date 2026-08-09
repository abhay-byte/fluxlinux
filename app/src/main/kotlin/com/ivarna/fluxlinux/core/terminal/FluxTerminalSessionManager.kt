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
     */
    fun openSessionAfterHost(
        ctx: Context,
        type: String,
        title: String = type,
        shellCmd: String = "exec zsh",
        method: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        TerminalLauncher.prepareHost(ctx) { ok ->
            if (!ok) {
                onDone(false)
                return@prepareHost
            }
            onDone(GuestSessionFactory.openSession(ctx, type, title, shellCmd, method))
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

    /** Interactive host (embedded Termux prefix) shell — HOST selector card. */
    fun openHostShell(ctx: Context, title: String = "Host Shell"): Boolean =
        GuestSessionFactory.openHostShell(ctx, title)

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
