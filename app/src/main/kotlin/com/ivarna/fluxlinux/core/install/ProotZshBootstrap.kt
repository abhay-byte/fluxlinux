package com.ivarna.fluxlinux.core.install

import android.content.Context
import android.util.Log
import com.ivarna.fluxlinux.core.terminal.HostCommandBuilder
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Host-side Oh My Zsh + essential plugins into the proot rootfs.
 *
 * Guest-side `rm -rf ~/.oh-my-zsh` and `curl | sh` install under proot often
 * hang for a long time (slow unlink of partial clones; unbounded network).
 * Java [File.deleteRecursively] + host `git` (PREFIX) is much more reliable.
 */
object ProotZshBootstrap {

    private const val TAG = "ProotZshBootstrap"
    private const val OMZ_URL = "https://github.com/ohmyzsh/ohmyzsh.git"
    private const val AUTOSUGGEST =
        "https://github.com/zsh-users/zsh-autosuggestions.git"
    private const val SYNTAX =
        "https://github.com/zsh-users/zsh-syntax-highlighting.git"
    private const val THEME_URL =
        "https://raw.githubusercontent.com/zakaziko99/agnosterzak-ohmyzsh-theme/master/agnosterzak.zsh-theme"

    fun omzDir(rootfs: File): File = File(rootfs, "home/flux/.oh-my-zsh")

    fun isOmzInstalled(rootfs: File): Boolean =
        File(omzDir(rootfs), "oh-my-zsh.sh").isFile

    /**
     * @return true when [oh-my-zsh.sh] is present after this call.
     */
    fun install(ctx: Context, onLog: (String) -> Unit = {}): Boolean {
        val app = ctx.applicationContext
        val rootfs = ProotXfceAssetInstaller.prootRootfs(app)
        if (!rootfs.isDirectory) {
            onLog("Proot rootfs missing — skip host Oh My Zsh")
            return false
        }
        val omz = omzDir(rootfs)
        if (isOmzInstalled(rootfs)) {
            onLog("Oh My Zsh already installed — skip")
            ensurePlugins(app, omz, onLog)
            return true
        }

        // Corrupt / partial: remove natively (do NOT rm under proot — that hangs)
        if (omz.exists()) {
            onLog("Removing incomplete Oh My Zsh (native delete)…")
            val ok = omz.deleteRecursively()
            if (!ok && omz.exists()) {
                onLog("WARN: could not fully delete ${omz.absolutePath}")
            }
        }

        val git = File(app.filesDir, "usr/bin/git")
        if (!git.isFile) {
            onLog("Host git not found — guest will try short-timeout install")
            return false
        }

        onLog("Host git clone Oh My Zsh (shallow)…")
        val cloneOk = hostGitClone(app, OMZ_URL, omz, timeoutSec = 180, onLog)
        if (!cloneOk || !isOmzInstalled(rootfs)) {
            onLog("Oh My Zsh host clone failed or incomplete")
            // Leave no half tree for guest rm to thrash on
            if (omz.exists() && !isOmzInstalled(rootfs)) {
                omz.deleteRecursively()
            }
            return false
        }
        onLog("Oh My Zsh ready")
        ensurePlugins(app, omz, onLog)
        return true
    }

    private fun ensurePlugins(app: Context, omz: File, onLog: (String) -> Unit) {
        val custom = File(omz, "custom")
        val plugins = File(custom, "plugins")
        val themes = File(custom, "themes")
        plugins.mkdirs()
        themes.mkdirs()

        clonePluginIfMissing(
            app,
            File(plugins, "zsh-autosuggestions"),
            AUTOSUGGEST,
            onLog
        )
        clonePluginIfMissing(
            app,
            File(plugins, "zsh-syntax-highlighting"),
            SYNTAX,
            onLog
        )
        // Do NOT install zsh-autocomplete (known 30s+ startup under proot)

        val themeFile = File(themes, "agnosterzak.zsh-theme")
        if (!themeFile.isFile || themeFile.length() < 50) {
            onLog("Fetching agnosterzak theme…")
            httpGetToFile(THEME_URL, themeFile, onLog)
        }
    }

    private fun clonePluginIfMissing(
        app: Context,
        dest: File,
        url: String,
        onLog: (String) -> Unit
    ) {
        if (dest.isDirectory && dest.list()?.isNotEmpty() == true) {
            onLog("Plugin ${dest.name} already present — skip")
            return
        }
        if (dest.exists()) dest.deleteRecursively()
        onLog("Host clone plugin ${dest.name}…")
        if (!hostGitClone(app, url, dest, timeoutSec = 90, onLog)) {
            onLog("Plugin ${dest.name} clone failed — skip")
            dest.deleteRecursively()
        }
    }

    private fun hostGitClone(
        app: Context,
        url: String,
        dest: File,
        timeoutSec: Long,
        onLog: (String) -> Unit
    ): Boolean {
        dest.parentFile?.mkdirs()
        val bash = TermuxHostPaths.libBash(app).absolutePath
        // Quote paths for shell
        val destQ = dest.absolutePath.replace("'", "'\\''")
        val script =
            "set -e; " +
                "export GIT_TERMINAL_PROMPT=0; " +
                "export GIT_HTTP_LOW_SPEED_LIMIT=1000; " +
                "export GIT_HTTP_LOW_SPEED_TIME=30; " +
                "rm -rf '$destQ'; " +
                "git clone --depth 1 --single-branch --quiet '$url' '$destQ'"
        return try {
            val pb = ProcessBuilder(bash, "-c", script)
            HostCommandBuilder.applyTo(app, pb, forceHostSetup = false)
            pb.redirectErrorStream(true)
            runCatching { pb.directory(File(TermuxHostPaths.BIN)) }
            val proc = pb.start()
            val reader = Thread {
                try {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        if (line.isNotBlank()) Log.d(TAG, line)
                    }
                } catch (_: Exception) {
                }
            }.also { it.isDaemon = true; it.start() }
            val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                onLog("git clone timed out after ${timeoutSec}s: $url")
                dest.deleteRecursively()
                return false
            }
            val code = proc.exitValue()
            if (code != 0) {
                onLog("git clone exit $code: $url")
                dest.deleteRecursively()
                false
            } else {
                dest.isDirectory
            }
        } catch (e: Exception) {
            onLog("git clone error: ${e.message}")
            dest.deleteRecursively()
            false
        }
    }

    private fun httpGetToFile(url: String, dest: File, onLog: (String) -> Unit) {
        try {
            dest.parentFile?.mkdirs()
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            conn.inputStream.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            conn.disconnect()
        } catch (e: Exception) {
            onLog("theme download failed: ${e.message}")
            dest.delete()
        }
    }
}
