package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.terminalComponentFor
import com.termux.terminal.TerminalSession

/**
 * Builds interactive guest sessions (shell / shell-root / component payloads).
 * Method is ALWAYS passed explicitly by card paths (plan §2.6) — no ambient
 * `LinuxCommandBuilder.currentMethod` for product actions.
 */
object GuestSessionFactory {

    /**
     * Create + open an interactive guest session.
     * [shellCmd] "exec zsh" (default) or blank → interactive login; else guest payload.
     */
    fun openSession(
        ctx: Context,
        type: String,
        title: String = type,
        shellCmd: String = "exec zsh",
        method: String
    ): Boolean {
        if (!SessionRegistry.hasFreeTab()) return false
        val user = LinuxCommandBuilder.sessionUserForType(type)
        val (args, envMap) = LinuxCommandBuilder.build(ctx, shellCmd, user = user, method = method)

        val isChroot = method == "chroot"
        val shell = TermuxHostPaths.libBash(ctx).absolutePath
        val cwd = if (isChroot) "/" else TermuxHostPaths.homeDir(ctx).absolutePath
        val sessionExec = if (isChroot) com.ivarna.fluxlinux.core.root.ChrootPaths.SESSION_EXEC else shell

        val env = envMap.map { "${it.key}=${it.value}" }.toTypedArray()
        val session = TerminalSession(sessionExec, cwd, args, env, 10000, SessionRegistry.sessionClient())
        return SessionRegistry.add(
            ctx,
            SessionRegistry.ManagedSession(session, type, title, method)
        )
    }

    /**
     * Interactive host (embedded Termux prefix) shell under libbash — no guest login.
     * Used by the HOST card in the terminal tool selector (plan §5.1).
     * Host env carries PREFIX/HOME/TMPDIR/package identity via [HostCommandBuilder].
     */
    fun openHostShell(ctx: Context, title: String = "Host Shell"): Boolean {
        if (!SessionRegistry.hasFreeTab()) return false
        val shell = TermuxHostPaths.libBash(ctx).absolutePath
        val (_, envMap) = HostCommandBuilder.build(ctx, shell, forceHostSetup = false)
        val env = envMap.map { "${it.key}=${it.value}" }.toTypedArray()
        val session = TerminalSession(
            shell,
            TermuxHostPaths.homeDir(ctx).absolutePath,
            arrayOf(shell, "-l"),
            env,
            10000,
            SessionRegistry.sessionClient()
        )
        return SessionRegistry.add(
            ctx,
            SessionRegistry.ManagedSession(session, "host", title, "host")
        )
    }

    /**
     * Component install/uninstall session for a distro. The component script is
     * base64-injected and runs INSIDE the guest (proot: `zsh -c '…'`; chroot: SSOT
     * helper `b64` as flux). Same component as the parent distro — never Termux intent.
     * [onFinished] fires when the session exits.
     */
    fun openComponentSession(
        ctx: Context,
        distro: Distro,
        scriptContent: String,
        title: String,
        extraEnv: Map<String, String> = emptyMap(),
        isUninstall: Boolean = false,
        onFinished: (() -> Unit)? = null
    ): Boolean {
        val method = terminalComponentFor(distro.id).method
        val b64 = android.util.Base64.encodeToString(
            scriptContent.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        val arg = if (isUninstall) " uninstall" else ""
        val envPrefix = extraEnv.entries.joinToString("") { (k, v) ->
            "export ${k}='${v.replace("'", "'\\''")}'; "
        }
        val guestPayload = buildString {
            append("export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; ")
            append(envPrefix)
            append("echo '$b64' | base64 -d > /tmp/flux_feature.sh; ")
            append("chmod +x /tmp/flux_feature.sh; ")
            append("bash /tmp/flux_feature.sh$arg; RC=\$?; ")
            append("rm -f /tmp/flux_feature.sh; ")
            append("exit \$RC")
        }
        if (!SessionRegistry.hasFreeTab()) return false
        val user = "flux"
        val (args, envMap) = LinuxCommandBuilder.build(ctx, guestPayload, user = user, method = method)
        val isChroot = method == "chroot"
        val shell = TermuxHostPaths.libBash(ctx).absolutePath
        val sessionExec = if (isChroot) com.ivarna.fluxlinux.core.root.ChrootPaths.SESSION_EXEC else shell
        val cwd = if (isChroot) "/" else TermuxHostPaths.homeDir(ctx).absolutePath
        val env = envMap.map { "${it.key}=${it.value}" }.toTypedArray()
        val session = TerminalSession(sessionExec, cwd, args, env, 10000, SessionRegistry.sessionClient())
        return SessionRegistry.add(
            ctx,
            SessionRegistry.ManagedSession(session, "component", title, method, onFinished)
        )
    }
}
