package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Deploys host + distro shell scripts from assets into `$HOME` and copies the
 * pinned Debian rootfs archive into `$HOME` for proot/chroot installs.
 *
 * Pass 2: all deploy methods return success so [TerminalLauncher.prepareHost]
 * can fail closed when the rootfs is missing/corrupt.
 */
object HostScriptDeployer {

    private const val TAG = "HostScriptDeployer"

    /** Pinned Debian 13 rootfs identity (shared proot + chroot path). */
    const val ROOTFS_SHA256 = "13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803"
    const val ROOTFS_MIN_BYTES = 50L * 1024L * 1024L

    /** Scripts copied to `$HOME`; asset path derived per script. */
    private val HOST_SCRIPTS = listOf(
        "setup_termux.sh",
        "flux_install.sh",
        "setup_debian_family.sh",
        "setup_customization_debian.sh",
        "setup_hw_accel_debian.sh",
        "setup_debian13_chroot.sh",
        "uninstall_debian13_chroot.sh",
        "fluxlinux_chroot.sh"
    )

    /** @return false when any required deploy step fails (fail-closed contract). */
    fun deployScripts(ctx: Context): Boolean {
        return try {
            TermuxHostPaths.applyPackageToExtractedPrefix(ctx.filesDir, ctx)
            val homeDir = File(ctx.filesDir, "home").also { it.mkdirs() }
            var ok = true
            for (script in HOST_SCRIPTS) {
                val assetPath = when {
                    script == "setup_termux.sh" -> "scripts/host/$script"
                    script == "flux_install.sh" -> "scripts/debian/proot/setup/$script"
                    script.contains("chroot") -> "scripts/chroot/$script"
                    else -> "scripts/debian/common/setup/$script"
                }
                val out = File(homeDir, script)
                try {
                    ctx.assets.open(assetPath).use { input ->
                        FileOutputStream(out).use { input.copyTo(it) }
                    }
                    out.setExecutable(true, false)
                } catch (e: Exception) {
                    Log.w(TAG, "Script $assetPath not found in assets", e)
                    ok = false
                }
            }
            // Optional terminal font (best-effort; terminal falls back to default)
            try {
                val termuxDir = File(homeDir, ".termux").also { it.mkdirs() }
                val fontOut = File(termuxDir, "font.ttf")
                if (!fontOut.isFile) {
                    ctx.assets.open("fonts/font.ttf").use { input ->
                        FileOutputStream(fontOut).use { input.copyTo(it) }
                    }
                }
            } catch (_: Exception) {
            }
            val rootfsOk = deployRootfsFromAssets(ctx)
            if (!rootfsOk) Log.w(TAG, "rootfs deploy failed — host not ready")
            // M2: loader is required by setup_termux.sh — include it in fail-closed.
            val loaderOk = deployLoaderApk(ctx)
            if (!loaderOk) Log.w(TAG, "loader.apk deploy failed — host not ready")
            ok && rootfsOk && loaderOk
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deploy scripts", e)
            false
        }
    }

    /** Stage Termux:X11 loader.apk into the prefix (required by setup_termux gate). */
    fun deployLoaderApk(ctx: Context): Boolean {
        return try {
            val dest = File(ctx.filesDir, "usr/libexec/termux-x11/loader.apk")
            if (dest.isFile && dest.length() > 0L) return true
            dest.parentFile?.mkdirs()
            ctx.assets.open("loader.apk").use { input ->
                FileOutputStream(dest).use { input.copyTo(it) }
            }
            dest.setReadOnly()
            Log.i(TAG, "Deployed loader.apk: ${dest.absolutePath}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "loader.apk deploy failed (GUI helpers will warn)", e)
            false
        }
    }

    /**
     * Copy `assets/rootfs/debian_13_rootfs.tar.xz` → `$HOME`.
     * Size (> 50 MiB) AND SHA256 gates; skip re-copy only when both pass.
     *
     * @return true when the on-disk archive satisfies size + SHA
     */
    fun deployRootfsFromAssets(ctx: Context): Boolean {
        val homeDir = TermuxHostPaths.homeDir(ctx).also { it.mkdirs() }
        val out = File(homeDir, "debian_13_rootfs.tar.xz")
        if (out.isFile && out.length() > ROOTFS_MIN_BYTES && sha256(out) == ROOTFS_SHA256) {
            Log.i(TAG, "Rootfs already deployed and verified: ${out.absolutePath}")
            return true
        }
        return try {
            ctx.assets.open("rootfs/debian_13_rootfs.tar.xz").use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            }
            val sizeOk = out.length() > ROOTFS_MIN_BYTES
            val shaOk = sha256(out) == ROOTFS_SHA256
            if (sizeOk && shaOk) {
                Log.i(TAG, "Deployed rootfs: ${out.absolutePath} (${out.length()} bytes, SHA OK)")
                true
            } else {
                Log.e(TAG, "Rootfs deploy failed gate: size=${out.length()} sha=${sha256(out)}")
                out.delete()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deploy rootfs archive from assets", e)
            out.delete()
            false
        }
    }

    private fun sha256(file: File): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r == -1) break
                md.update(buf, 0, r)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        Log.w(TAG, "sha256 failed: ${e.message}")
        ""
    }
}
