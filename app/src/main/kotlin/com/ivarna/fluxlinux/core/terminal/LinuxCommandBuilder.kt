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
        method: String = currentMethod
    ): Pair<Array<String>, HashMap<String, String>> {
        return when (method) {
            "chroot" -> ChrootCommandBuilder.build(ctx, shellCmd, user)
            else -> ProotCommandBuilder.build(ctx, shellCmd, user, useSharedTmp)
        }
    }
}
