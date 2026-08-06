package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import com.ivarna.fluxlinux.core.root.ChrootPaths
import com.ivarna.fluxlinux.core.root.RootShell

/** Builds shell arguments and environment map for chroot Debian sessions (SSOT helper).
 *  Ported from termux-lib `ChrootCommandBuilder` (fluxlinux_chroot.sh v2.2).
 *  Depends on RootShell (one-way) — RootShell never imports this (plan §2.5). */
object ChrootCommandBuilder {

    val CHROOT_PATH: String get() = ChrootPaths.CHROOT_PATH
    val CHROOT_HELPER: String get() = ChrootPaths.CHROOT_HELPER
    val CHROOT_HELPER_ASSET: String get() = ChrootPaths.CHROOT_HELPER_ASSET
    val CHROOT_HELPER_VERSION: String get() = ChrootPaths.CHROOT_HELPER_VERSION
    val SESSION_EXEC: String get() = ChrootPaths.SESSION_EXEC

    fun build(
        ctx: Context,
        shellCmd: String,
        user: String = "flux"
    ): Pair<Array<String>, HashMap<String, String>> {
        ensureHelperScript(ctx)
        val rootInner = buildRootInner(shellCmd, user)
        val cmd = RootShell.shellRootCommand(rootInner)
        // Inline WINCH trap on outer sh (mShellPid). Keep this shell as parent (no leading exec).
        val winchCmd = winchWrap(cmd)
        return arrayOf(SESSION_EXEC, "-c", winchCmd) to buildEnv(user)
    }

    /** Inline WINCH trap prefix for the outer `/system/bin/sh` session. */
    fun winchWrap(cmd: String): String =
        "trap 'kill -WINCH -\$\$ 2>/dev/null; kill -WINCH 0 2>/dev/null' WINCH; $cmd"

    /** Pure guest-inner argv builder (unit-testable without Android). */
    fun buildRootInner(
        shellCmd: String,
        user: String = "flux",
        helper: String = ChrootPaths.CHROOT_HELPER
    ): String {
        val u = if (user == "root") "root" else "flux"
        val workdir = parseInteractiveWorkdir(shellCmd)
        val isInteractive = workdir != null ||
            shellCmd == "exec zsh" ||
            shellCmd == "/bin/bash --login" ||
            shellCmd.isBlank()

        // Always `sh $HELPER` (not bare exec of script) — SELinux often blocks exec of
        // /data/local/tmp/*.sh; /system/bin/sh interpreting the script is reliable.
        return when {
            isInteractive && u == "root" -> {
                val wd = workdir?.let { " --workdir ${shellSingleQuote(it)}" } ?: ""
                "exec sh $helper login --user root --shell bash$wd"
            }
            isInteractive -> {
                val wd = workdir?.let { " --workdir ${shellSingleQuote(it)}" } ?: ""
                "exec sh $helper login --user flux --shell zsh$wd"
            }
            isSimpleGuestCmd(shellCmd) -> {
                val esc = shellCmd.replace("'", "'\\''")
                "exec sh $helper sh --user $u -- '$esc'"
            }
            else -> {
                val b64 = java.util.Base64.getEncoder().encodeToString(
                    shellCmd.toByteArray(Charsets.UTF_8)
                )
                "exec sh $helper b64 --user $u -- $b64"
            }
        }
    }

    /** Session env for the outer sh (Android PATH + TERM + guest HOME). */
    fun buildEnv(user: String = "flux"): HashMap<String, String> {
        val u = if (user == "root") "root" else "flux"
        val envMap = HashMap(System.getenv())
        envMap["PATH"] = "/system/bin:/system/xbin:/sbin:" + (envMap["PATH"] ?: "")
        envMap["TERM"] = "xterm-256color"
        envMap["HOME"] = if (u == "root") "/root" else "/home/flux"
        envMap["LANG"] = "en_US.UTF-8"
        envMap["LC_ALL"] = "en_US.UTF-8"
        envMap["XDG_RUNTIME_DIR"] = "/tmp"
        envMap["TMPDIR"] = "/tmp"
        return envMap
    }

    /**
     * Stage the SSOT helper when missing/stale. Delegates to
     * [RootShell.ensureChrootHelper] (RootShell = su only; one-way dependency).
     */
    fun ensureHelperScript(ctx: Context): Boolean = RootShell.ensureChrootHelper(ctx)

    /**
     * Guest payload safe to embed in `sh --user U -- '…'`.
     * Reject `$` / backticks / quotes / newlines / backslash — those need b64.
     */
    private fun isSimpleGuestCmd(shellCmd: String): Boolean {
        if (shellCmd.isEmpty()) return false
        for (c in shellCmd) {
            when (c) {
                '$', '`', '"', '\'', '\n', '\r', '\\' -> return false
            }
        }
        return true
    }

    private fun shellSingleQuote(s: String): String =
        "'" + s.replace("'", "'\\''") + "'"

    /**
     * Workspace shell: `mkdir -p DIR && cd DIR && exec zsh` → interactive login + --workdir.
     * Returns workdir path, or null if not that form.
     */
    private fun parseInteractiveWorkdir(shellCmd: String): String? {
        val t = shellCmd.trim()
        val m = Regex("""^mkdir -p (.+) && cd \1 && exec zsh$""").matchEntire(t)
            ?: return null
        val dir = m.groupValues[1].trim()
        return dir.takeIf { it.isNotEmpty() && !it.contains('\'') }
    }
}
