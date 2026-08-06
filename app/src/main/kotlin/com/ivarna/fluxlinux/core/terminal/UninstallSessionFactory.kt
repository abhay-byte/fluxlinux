package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.terminalComponentFor
import com.ivarna.fluxlinux.core.root.RootShell

/**
 * Distro uninstall sessions.
 *  - proot (`debian`): host `proot-distro remove debian` + marker cleanup + callback.
 *  - chroot (`debian13_chroot`): root session running staged `uninstall_debian13_chroot.sh`.
 */
object UninstallSessionFactory {

    fun openUninstallSession(
        ctx: Context,
        distro: Distro
    ): Boolean {
        val method = terminalComponentFor(distro.id).method
        return when (method) {
            "chroot" -> {
                val staged = RootShell.stageAsset(ctx, "scripts/chroot/uninstall_debian13_chroot.sh")
                    ?: return InstallSessionFactory.openRootScriptSession(
                        ctx,
                        TermuxHostPaths.hostScript(ctx, "uninstall_debian13_chroot.sh").absolutePath,
                        title = "Uninstall Debian (Rooted)"
                    )
                InstallSessionFactory.openRootScriptSession(
                    ctx,
                    staged,
                    title = "Uninstall Debian (Rooted)"
                )
            }
            else -> {
                val callback = "am start -a android.intent.action.VIEW -d " +
                    "\"fluxlinux://callback?result=success&name=distro_uninstall_${distro.id}\""
                val cmd = buildString {
                    append("proot-distro remove ${distro.id}; RC=\$?; ")
                    append("rm -f \"\$HOME/.fluxlinux_distro_${distro.id}_installed\" 2>/dev/null; ")
                    append("if [ \$RC -eq 0 ]; then $callback; fi; exit \$RC")
                }
                InstallSessionFactory.openHostCommandSession(ctx, cmd, title = "Uninstall ${distro.name}")
            }
        }
    }
}
