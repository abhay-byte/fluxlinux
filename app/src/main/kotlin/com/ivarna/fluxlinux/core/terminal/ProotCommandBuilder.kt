package com.ivarna.fluxlinux.core.terminal

import android.content.Context

/** Builds shell arguments and environment map for proot Debian sessions.
 *  Ported from termux-lib `ProotCommandBuilder`. */
object ProotCommandBuilder {

    /**
     * Pure argv builder (unit-testable without Android).
     * Interactive login when [shellCmd] is a login sentinel; otherwise a
     * single-quoted guest payload (host bash never expands `$HOME`/`$PATH`).
     */
    fun buildArgs(
        shell: String,
        prootDistro: String,
        shellCmd: String,
        user: String = "flux",
        useSharedTmp: Boolean = true
    ): Array<String> {
        val sharedTmpFlag = if (useSharedTmp) "--shared-tmp" else ""
        return if (shellCmd == "exec zsh" || shellCmd == "/bin/bash --login" || shellCmd.isBlank()) {
            arrayOf(
                shell, "-c",
                "exec python $prootDistro login debian $sharedTmpFlag --user $user"
            )
        } else {
            // Single-quote guest payload so host bash never expands $HOME/$PATH/etc.
            // Escape embedded single quotes: ' → '\''
            val escaped = shellCmd.replace("'", "'\\''")
            arrayOf(
                shell, "-c",
                "exec python $prootDistro login debian $sharedTmpFlag --user $user -- zsh -c '$escaped'"
            )
        }
    }

    fun build(
        ctx: Context,
        shellCmd: String,
        user: String = "flux",
        useSharedTmp: Boolean = true
    ): Pair<Array<String>, HashMap<String, String>> {
        val shell = TermuxHostPaths.libBash(ctx).absolutePath
        val prootDistro = TermuxHostPaths.PROOT_DISTRO
        val args = buildArgs(shell, prootDistro, shellCmd, user, useSharedTmp)
        // Host package env + interactive TERM (guest login inherits package identity)
        val envMap = HostCommandBuilder.envMap(ctx, forceHostSetup = false, includeTerm = true)
        return args to envMap
    }
}
