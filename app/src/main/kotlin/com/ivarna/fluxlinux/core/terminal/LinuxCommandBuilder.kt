package com.ivarna.fluxlinux.core.terminal

import android.content.Context

/** Entry point for building terminal commands.
 *  Delegates to ProotCommandBuilder or ChrootCommandBuilder based on method.
 *  Ported from termux-lib `LinuxCommandBuilder`. */
object LinuxCommandBuilder {

    /**
     * UI-default isolation method. DEPRECATED for product/card actions —
     * card paths must pass `method` explicitly from
     * `terminalComponentFor(distroId).method` (plan §2.6). Kept only as a
     * convenience default for the shared Terminal page tool selector.
     */
    @Deprecated("Pass method explicitly from terminalComponentFor(distroId).method for card actions")
    var currentMethod = "proot"

    /**
     * Guest user for session type.
     * - shell-root / component → root (apt/dpkg & setup scripts)
     * - interactive shell / default → flux
     */
    fun sessionUserForType(type: String): String =
        when (type) {
            "shell-root", "component" -> "root"
            else -> "flux"
        }

    fun build(
        ctx: Context,
        shellCmd: String,
        user: String = "flux",
        useSharedTmp: Boolean = true,
        method: String = currentMethod,
        distroId: String? = null,
        loginShell: GuestLoginShell? = null
    ): Pair<Array<String>, HashMap<String, String>> {
        val profile = distroId?.let {
            com.ivarna.fluxlinux.core.install.DistroInstallProfile.forId(it)
        }
        return when (method) {
            "chroot" -> ChrootCommandBuilder.build(
                ctx,
                shellCmd,
                user,
                chrootPath = profile?.chrootPath
                    ?: com.ivarna.fluxlinux.core.root.ChrootPaths.CHROOT_PATH,
                loginShell = loginShell
            )
            else -> ProotCommandBuilder.build(
                ctx,
                shellCmd,
                user,
                useSharedTmp,
                distro = profile?.prootName ?: "debian",
                loginShell = loginShell ?: GuestLoginShell.DEFAULT
            )
        }
    }
}
