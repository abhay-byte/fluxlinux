package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.terminalComponentFor
import com.ivarna.fluxlinux.core.root.RootShell
import com.termux.terminal.TerminalSession

/**
 * Builds host + install sessions.
 *  - proot install: `$HOME/flux_install.sh debian [setup_b64]` under libbash —
 *    setupB64 is passed as a RAW argv element (execve contract, never shell-quoted).
 *  - chroot install: staged `setup_debian13_chroot.sh` run as root on the host.
 *  - host scripts/commands: `$HOME/<script>` or `bash -c '<cmd>'`.
 */
object InstallSessionFactory {

    /**
     * Distro install session.
     *  - proot (`debian`): host bash runs `$HOME/flux_install.sh debian [setup_b64]`
     *    (local rootfs archive; no registry pull; no external Termux).
     *  - chroot (`debian13_chroot`): root TerminalSession runs staged
     *    `setup_debian13_chroot.sh` on the host (same rootfs asset).
     */
    fun openInstallSession(
        ctx: Context,
        distro: Distro,
        setupB64: String? = null,
        onFinished: (() -> Unit)? = null
    ): Boolean {
        val method = terminalComponentFor(distro.id).method
        return when (method) {
            "chroot" -> openChrootInstall(ctx, onFinished)
            else -> openProotInstall(ctx, setupB64, onFinished)
        }
    }

    /**
     * Host session running a deployed script with libbash.so
     * (e.g. `flux_install.sh debian` from $HOME). [args] are raw argv.
     */
    fun openHostScriptSession(
        ctx: Context,
        scriptName: String,
        title: String = scriptName,
        args: Array<String> = emptyArray(),
        forceHostSetup: Boolean = false,
        onFinished: (() -> Unit)? = null
    ): Boolean {
        val script = TermuxHostPaths.hostScript(ctx, scriptName)
        val (_, envMap) = HostCommandBuilder.build(
            ctx,
            script.absolutePath,
            forceHostSetup = forceHostSetup || HostCommandBuilder.shouldForceHostSetup(scriptName)
        )
        val shell = TermuxHostPaths.libBash(ctx).absolutePath
        val argv = arrayOf(shell, script.absolutePath) + args
        val cwd = TermuxHostPaths.homeDir(ctx).absolutePath
        val env = envMap.map { "${it.key}=${it.value}" }.toTypedArray()
        val session = TerminalSession(shell, cwd, argv, env, 10000, SessionRegistry.sessionClient())
        return SessionRegistry.add(
            ctx,
            SessionRegistry.ManagedSession(session, "host", title, "host", onFinished)
        )
    }

    /**
     * Host session running an arbitrary host command under libbash.so
     * (e.g. `proot-distro remove debian` for uninstall). No guest login.
     */
    fun openHostCommandSession(
        ctx: Context,
        command: String,
        title: String = "Host Shell"
    ): Boolean {
        val shell = TermuxHostPaths.libBash(ctx).absolutePath
        val (_, envMap) = HostCommandBuilder.build(ctx, shell, forceHostSetup = false)
        val env = envMap.map { "${it.key}=${it.value}" }.toTypedArray()
        val session = TerminalSession(
            shell,
            TermuxHostPaths.homeDir(ctx).absolutePath,
            arrayOf(shell, "-c", command),
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
     * Root shell session running [scriptPath] on the HOST via discovered su
     * (mounts + rootfs extraction; guest entry is not required).
     *
     * Env is seeded from [HostCommandBuilder.envMap] so host scripts see the
     * correct package identity under BOTH flavors (B1): `setup_debian13_chroot.sh`
     * resolves PKG/APP_HOME from `TERMUX_APP__PACKAGE_NAME`/`TERMUX__HOME` —
     * without this, a zenithblue root install falls back to ivarna paths.
     * [FLUX_ROOTFS_PATH] pins the deployed rootfs archive.
     */
    fun openRootScriptSession(
        ctx: Context,
        scriptPath: String,
        title: String = "Root Shell",
        onFinished: (() -> Unit)? = null
    ): Boolean {
        val rootInner = RootShell.shellRootCommand("sh '$scriptPath'")
        val winchCmd =
            "trap 'kill -WINCH -\$\$ 2>/dev/null; kill -WINCH 0 2>/dev/null' WINCH; $rootInner"
        // Host SSOT env (package identity, PREFIX/HOME/TMPDIR, LD_LIBRARY_PATH) —
        // the outer /system/bin/sh still needs system tools, so PATH keeps them first.
        val env = HostCommandBuilder.envMap(ctx, includeTerm = false)
        env["PATH"] = "/system/bin:/system/xbin:/sbin:" + (env["PATH"] ?: "")
        env["TERM"] = "xterm-256color"
        env["FLUX_ROOTFS_PATH"] = "${TermuxHostPaths.HOME}/debian_13_rootfs.tar.xz"
        val session = TerminalSession(
            com.ivarna.fluxlinux.core.root.ChrootPaths.SESSION_EXEC,
            "/",
            arrayOf("-c", winchCmd),
            env.map { "${it.key}=${it.value}" }.toTypedArray(),
            10000,
            SessionRegistry.sessionClient()
        )
        return SessionRegistry.add(
            ctx,
            SessionRegistry.ManagedSession(session, "install", title, "chroot", onFinished)
        )
    }

    private fun openProotInstall(
        ctx: Context,
        setupB64: String?,
        onFinished: (() -> Unit)?
    ): Boolean {
        val args = if (setupB64.isNullOrEmpty() || setupB64 == "null") {
            arrayOf("debian")
        } else {
            // RAW argv — TerminalSession is execve-style; quoting would become part of $2.
            arrayOf("debian", setupB64)
        }
        return openHostScriptSession(
            ctx,
            "flux_install.sh",
            title = "Debian Install (Flux Terminal)",
            args = args,
            onFinished = onFinished
        )
    }

    private fun openChrootInstall(
        ctx: Context,
        onFinished: (() -> Unit)?
    ): Boolean {
        val staged = RootShell.stageAsset(ctx, "scripts/chroot/setup_debian13_chroot.sh")
            ?: return openRootScriptSession(
                ctx,
                TermuxHostPaths.hostScript(ctx, "setup_debian13_chroot.sh").absolutePath,
                title = "Debian Rooted Install (Root Shell)",
                onFinished = onFinished
            )
        return openRootScriptSession(
            ctx,
            staged,
            title = "Debian Rooted Install (Root Shell)",
            onFinished = onFinished
        )
    }
}
