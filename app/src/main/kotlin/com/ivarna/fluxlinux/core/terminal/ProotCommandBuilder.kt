package com.ivarna.fluxlinux.core.terminal

import android.content.Context

/** Builds shell arguments and environment map for proot sessions.
 *  Ported from termux-lib `ProotCommandBuilder`. */
object ProotCommandBuilder {

    /** Guest PATH only — host `$PREFIX/bin` must not leak (nested proot glue errors). */
    const val GUEST_PATH =
        "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    /**
     * Clean guest env for `proot-distro login … -- env -i …`.
     * Drops host `TMPDIR`/`PROOT_TMP_DIR`/`PATH` so guest uid 1000 never tries
     * to write the host glue dir or exec host `proot`.
     */
    fun guestLoginEnv(user: String): String {
        val u = if (user == "root") "root" else "flux"
        val home = if (u == "root") "/root" else "/home/flux"
        // Do not force LANG/LC_ALL here: missing en_US.UTF-8 prints
        // "cannot change locale" from /bin/sh before profile.d can pick.
        // Guest flux-locale.sh / .zshrc select a locale that exists.
        return "env -i HOME=$home USER=$u LOGNAME=$u " +
            "TERM=\"\${TERM:-xterm-256color}\" LANG=C " +
            "TMPDIR=/tmp XDG_RUNTIME_DIR=/tmp PATH=$GUEST_PATH"
    }

    /** zsh-first cascade (default pref). */
    val GUEST_LOGIN_SHELL: String get() = GuestLoginShell.prootLoginCascade(GuestLoginShell.DEFAULT)

    /**
     * Pure argv builder (unit-testable without Android).
     * Interactive login when [shellCmd] is a login sentinel
     * ([GuestLoginShell.isLoginSentinel]); otherwise a single-quoted guest
     * payload (host bash never expands `$HOME`/`$PATH`).
     *
     * The interactive guest binary comes from [loginShell] — the sentinel
     * string itself never picks the shell.
     *
     * @param distro proot-distro container name (`debian`, `alpine`, …)
     */
    fun buildArgs(
        shell: String,
        prootDistro: String,
        shellCmd: String,
        user: String = "flux",
        useSharedTmp: Boolean = true,
        distro: String = "debian",
        loginShell: GuestLoginShell = GuestLoginShell.DEFAULT
    ): Array<String> {
        val sharedTmpFlag = if (useSharedTmp) "--shared-tmp" else ""
        val env = guestLoginEnv(user)
        return if (GuestLoginShell.isLoginSentinel(shellCmd)) {
            arrayOf(
                shell, "-c",
                "exec python $prootDistro login $distro $sharedTmpFlag --user $user -- " +
                    "$env ${GuestLoginShell.prootLoginCascade(loginShell)}"
            )
        } else {
            // Single-quote guest payload so host bash never expands $HOME/$PATH/etc.
            // Escape embedded single quotes: ' → '\''
            // Use /bin/sh so Alpine (pre-zsh) and Debian both work.
            val escaped = shellCmd.replace("'", "'\\''")
            arrayOf(
                shell, "-c",
                "exec python $prootDistro login $distro $sharedTmpFlag --user $user -- " +
                    "$env /bin/sh -c '$escaped'"
            )
        }
    }

    fun build(
        ctx: Context,
        shellCmd: String,
        user: String = "flux",
        useSharedTmp: Boolean = true,
        distro: String = "debian",
        loginShell: GuestLoginShell = GuestLoginShell.DEFAULT
    ): Pair<Array<String>, HashMap<String, String>> {
        val shell = TermuxHostPaths.libBash(ctx).absolutePath
        val prootDistro = TermuxHostPaths.PROOT_DISTRO
        val args = buildArgs(shell, prootDistro, shellCmd, user, useSharedTmp, distro, loginShell)
        // Host package env + interactive TERM (guest login inherits package identity)
        val envMap = HostCommandBuilder.envMap(ctx, forceHostSetup = false, includeTerm = true)
        return args to envMap
    }
}
