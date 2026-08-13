package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.terminalComponentFor
import com.ivarna.fluxlinux.core.install.DistroInstallProfile
import com.ivarna.fluxlinux.core.root.RootShell
import com.termux.terminal.TerminalSession

/**
 * Builds host + install sessions.
 *  - proot: `$HOME/flux_install.sh <prootName> [setup_b64]` under libbash
 *  - chroot: staged setup_*_chroot.sh run as root on the host
 */
object InstallSessionFactory {

    fun openInstallSession(
        ctx: Context,
        distro: Distro,
        setupB64: String? = null,
        onFinished: (() -> Unit)? = null
    ): Boolean {
        val method = terminalComponentFor(distro.id).method
        return when (method) {
            "chroot" -> openChrootInstall(ctx, distro.id, onFinished)
            else -> openProotInstall(ctx, distro.id, setupB64, onFinished)
        }
    }

    fun openHostScriptSession(
        ctx: Context,
        scriptName: String,
        title: String = scriptName,
        args: Array<String> = emptyArray(),
        forceHostSetup: Boolean = false,
        onFinished: (() -> Unit)? = null,
        extraEnv: Map<String, String> = emptyMap()
    ): Boolean {
        val script = TermuxHostPaths.hostScript(ctx, scriptName)
        val (_, envMap) = HostCommandBuilder.build(
            ctx,
            script.absolutePath,
            forceHostSetup = forceHostSetup || HostCommandBuilder.shouldForceHostSetup(scriptName)
        )
        extraEnv.forEach { (k, v) -> envMap[k] = v }
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

    fun openRootScriptSession(
        ctx: Context,
        scriptPath: String,
        title: String = "Root Shell",
        onFinished: (() -> Unit)? = null,
        rootfsFileName: String = DistroInstallProfile.DEBIAN_ROOTFS_NAME,
        chrootPath: String? = null
    ): Boolean {
        val rootInner = RootShell.shellRootCommand("sh '$scriptPath'")
        val winchCmd =
            "trap 'kill -WINCH -\$\$ 2>/dev/null; kill -WINCH 0 2>/dev/null' WINCH; $rootInner"
        val env = HostCommandBuilder.envMap(ctx, includeTerm = false)
        env["PATH"] = "/system/bin:/system/xbin:/sbin:" + (env["PATH"] ?: "")
        env["TERM"] = "xterm-256color"
        env["FLUX_ROOTFS_PATH"] = "${TermuxHostPaths.HOME}/$rootfsFileName"
        if (chrootPath != null) {
            env["FLUX_CHROOT"] = chrootPath
        }
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
        distroId: String,
        setupB64: String?,
        onFinished: (() -> Unit)?
    ): Boolean {
        val profile = DistroInstallProfile.require(distroId)
        val args = if (setupB64.isNullOrEmpty() || setupB64 == "null") {
            arrayOf(profile.prootName)
        } else {
            arrayOf(profile.prootName, setupB64)
        }
        return openHostScriptSession(
            ctx,
            "flux_install.sh",
            title = "${profile.displayName} Install (Flux Terminal)",
            args = args,
            onFinished = onFinished,
            extraEnv = mapOf(
                "FLUX_ROOTFS_PATH" to "${TermuxHostPaths.HOME}/${profile.rootfsFileName}",
                "FLUX_ROOTFS_NAME" to profile.rootfsFileName,
                "FLUX_ROOTFS_SHA256" to profile.rootfsSha256
            )
        )
    }

    private fun openChrootInstall(
        ctx: Context,
        distroId: String,
        onFinished: (() -> Unit)?
    ): Boolean {
        val profile = DistroInstallProfile.require(distroId)
        val asset = profile.chrootSetupAsset
            ?: return false
        val staged = RootShell.stageAsset(ctx, asset)
            ?: TermuxHostPaths.hostScript(ctx, FileName(asset)).absolutePath
        return openRootScriptSession(
            ctx,
            staged,
            title = "${profile.displayName} Install",
            onFinished = onFinished,
            rootfsFileName = profile.rootfsFileName,
            chrootPath = profile.chrootPath
        )
    }

    private fun FileName(assetPath: String): String =
        assetPath.substringAfterLast('/')
}
