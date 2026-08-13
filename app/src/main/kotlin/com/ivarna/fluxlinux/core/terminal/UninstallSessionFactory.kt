package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.terminalComponentFor
import com.ivarna.fluxlinux.core.install.DistroInstallProfile
import com.ivarna.fluxlinux.core.root.RootShell

/**
 * Distro uninstall sessions.
 *  - proot: `proot-distro remove <name>` + marker cleanup + callback
 *  - chroot: root session running staged uninstall_*_chroot.sh
 */
object UninstallSessionFactory {

    fun openUninstallSession(
        ctx: Context,
        distro: Distro
    ): Boolean {
        val method = terminalComponentFor(distro.id).method
        val profile = DistroInstallProfile.forId(distro.id)
        return when (method) {
            "chroot" -> {
                val asset = profile?.chrootUninstallAsset
                    ?: "scripts/chroot/uninstall_debian13_chroot.sh"
                val name = asset.substringAfterLast('/')
                val staged = RootShell.stageAsset(ctx, asset)
                    ?: return InstallSessionFactory.openRootScriptSession(
                        ctx,
                        TermuxHostPaths.hostScript(ctx, name).absolutePath,
                        title = "Uninstall ${distro.name}",
                        rootfsFileName = profile?.rootfsFileName
                            ?: DistroInstallProfile.DEBIAN_ROOTFS_NAME,
                        chrootPath = profile?.chrootPath
                    )
                InstallSessionFactory.openRootScriptSession(
                    ctx,
                    staged,
                    title = "Uninstall ${distro.name}",
                    rootfsFileName = profile?.rootfsFileName
                        ?: DistroInstallProfile.DEBIAN_ROOTFS_NAME,
                    chrootPath = profile?.chrootPath
                )
            }
            else -> {
                val removeName = profile?.prootName ?: distro.id
                val callback = "am start -a android.intent.action.VIEW -d " +
                    "\"fluxlinux://callback?result=success&name=distro_uninstall_${distro.id}\""
                val cmd = buildString {
                    append("proot-distro remove $removeName; RC=\$?; ")
                    append("rm -f \"\$HOME/.fluxlinux_distro_${distro.id}_installed\" 2>/dev/null; ")
                    append("if [ \$RC -eq 0 ]; then $callback; fi; exit \$RC")
                }
                InstallSessionFactory.openHostCommandSession(
                    ctx, cmd, title = "Uninstall ${distro.name}"
                )
            }
        }
    }
}
