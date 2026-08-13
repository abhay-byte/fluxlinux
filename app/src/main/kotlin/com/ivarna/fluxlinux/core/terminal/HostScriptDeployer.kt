package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import android.util.Log
import com.ivarna.fluxlinux.core.install.DistroInstallProfile
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Deploys host + distro shell scripts from assets into `$HOME` and copies
 * pinned rootfs archives into `$HOME` for proot/chroot installs.
 *
 * Fail-closed: missing/corrupt **any** required rootfs or [HostScript.required]
 * asset makes [deployScripts] return false so [TerminalLauncher.prepareHost] fails.
 */
object HostScriptDeployer {

    private const val TAG = "HostScriptDeployer"

    /** @deprecated Use [DistroInstallProfile.DEBIAN_ROOTFS_SHA256]. */
    const val ROOTFS_SHA256 = DistroInstallProfile.DEBIAN_ROOTFS_SHA256
    /** @deprecated Use [DistroInstallProfile.DEBIAN_ROOTFS_MIN_BYTES]. */
    const val ROOTFS_MIN_BYTES = DistroInstallProfile.DEBIAN_ROOTFS_MIN_BYTES

    private data class HostScript(
        val name: String,
        val assetPath: String,
        val required: Boolean = true
    )

    private val HOST_SCRIPTS: List<HostScript> = listOf(
        HostScript("setup_termux.sh", "scripts/host/setup_termux.sh"),
        HostScript("flux_install.sh", "scripts/debian/proot/setup/flux_install.sh"),
        // Debian guest
        HostScript("setup_debian_family.sh", "scripts/debian/common/setup/setup_debian_family.sh"),
        HostScript(
            "setup_customization_debian.sh",
            "scripts/debian/common/setup/setup_customization_debian.sh"
        ),
        HostScript(
            "setup_hw_accel_debian.sh",
            "scripts/debian/common/setup/setup_hw_accel_debian.sh",
            required = false
        ),
        // Alpine guest
        HostScript("setup_alpine_family.sh", "scripts/alpine/common/setup/setup_alpine_family.sh"),
        HostScript(
            "setup_customization_alpine.sh",
            "scripts/alpine/common/setup/setup_customization_alpine.sh"
        ),
        // Shared glibc-guest helpers (Fedora / Void / openSUSE)
        HostScript("flux_guest_common.sh", "scripts/common/setup/flux_guest_common.sh"),
        HostScript(
            "setup_customization_xfce.sh",
            "scripts/common/setup/setup_customization_xfce.sh"
        ),
        HostScript("setup_hw_accel_guest.sh", "scripts/common/setup/setup_hw_accel_guest.sh"),
        HostScript(
            "bwrap-proot-shim.sh",
            "scripts/common/setup/bwrap-proot-shim.sh",
            required = false
        ),
        HostScript(
            "bwrap-proot-shim",
            "scripts/common/setup/bwrap-proot-shim",
            required = false
        ),
        HostScript("setup_fedora_family.sh", "scripts/fedora/common/setup/setup_fedora_family.sh"),
        HostScript("setup_void_family.sh", "scripts/void/common/setup/setup_void_family.sh"),
        HostScript(
            "setup_opensuse_family.sh",
            "scripts/opensuse/common/setup/setup_opensuse_family.sh"
        ),
        // EVP_md2 stub for TW libldap vs OpenSSL 3.5 (sudo/zypper)
        HostScript(
            "libevp_md2.so",
            "scripts/opensuse/common/libevp_md2.so",
            required = false
        ),
        // Chroot setup/uninstall
        HostScript("setup_debian13_chroot.sh", "scripts/chroot/setup_debian13_chroot.sh"),
        HostScript("uninstall_debian13_chroot.sh", "scripts/chroot/uninstall_debian13_chroot.sh"),
        HostScript("setup_alpine_chroot.sh", "scripts/chroot/setup_alpine_chroot.sh"),
        HostScript("uninstall_alpine_chroot.sh", "scripts/chroot/uninstall_alpine_chroot.sh"),
        HostScript("fluxlinux_chroot.sh", "scripts/chroot/fluxlinux_chroot.sh"),
        HostScript("chroot_processes.sh", "scripts/chroot/chroot_processes.sh", required = false),
        HostScript("chroot_size.sh", "scripts/chroot/chroot_size.sh", required = false),
        // Desktop (proot)
        HostScript("start_gui.sh", "scripts/debian/proot/start/start_gui.sh"),
        HostScript("stop_gui.sh", "scripts/debian/proot/stop/stop_gui.sh"),
        // Desktop (chroot host wrappers + root guest)
        HostScript("start_gui_chroot.sh", "scripts/chroot/start_gui_chroot.sh"),
        HostScript("stop_gui_chroot.sh", "scripts/chroot/stop_gui_chroot.sh"),
        HostScript("start_debian13_gui.sh", "scripts/chroot/start_debian13_gui.sh"),
        HostScript("stop_debian13_gui.sh", "scripts/chroot/stop_debian13_gui.sh"),
        HostScript("start_alpine_gui.sh", "scripts/chroot/start_alpine_gui.sh"),
        HostScript("stop_alpine_gui.sh", "scripts/chroot/stop_alpine_gui.sh"),
        HostScript("setup_guest_chroot.sh", "scripts/chroot/setup_guest_chroot.sh"),
        HostScript("uninstall_guest_chroot.sh", "scripts/chroot/uninstall_guest_chroot.sh"),
        HostScript("start_guest_gui.sh", "scripts/chroot/start_guest_gui.sh"),
        HostScript("stop_guest_gui.sh", "scripts/chroot/stop_guest_gui.sh"),
    )

    /** @return false when any required deploy step fails (fail-closed contract). */
    fun deployScripts(ctx: Context): Boolean {
        return try {
            TermuxHostPaths.applyPackageToExtractedPrefix(ctx.filesDir, ctx)
            val homeDir = File(ctx.filesDir, "home").also { it.mkdirs() }
            var ok = true
            for (script in HOST_SCRIPTS) {
                val out = File(homeDir, script.name)
                try {
                    ctx.assets.open(script.assetPath).use { input ->
                        FileOutputStream(out).use { input.copyTo(it) }
                    }
                    out.setExecutable(true, false)
                } catch (e: Exception) {
                    Log.w(TAG, "Script ${script.assetPath} not found in assets", e)
                    if (script.required) ok = false
                }
            }
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
            val rootfsOk = deployAllRootfsFromAssets(ctx)
            if (!rootfsOk) Log.w(TAG, "rootfs deploy failed — host not ready")
            val loaderOk = deployLoaderApk(ctx)
            if (!loaderOk) Log.w(TAG, "loader.apk deploy failed — host not ready")
            ok && rootfsOk && loaderOk
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deploy scripts", e)
            false
        }
    }

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
     * Deploy every pinned rootfs (Debian + Alpine). Fail-closed if any fails.
     */
    fun deployAllRootfsFromAssets(ctx: Context): Boolean {
        var allOk = true
        for (profile in DistroInstallProfile.allRootfsProfiles()) {
            if (!deployRootfsProfile(ctx, profile)) {
                allOk = false
            }
        }
        return allOk
    }

    /**
     * Copy `assets/rootfs/debian_13_rootfs.tar.xz` → `$HOME` (legacy single-path API).
     */
    fun deployRootfsFromAssets(ctx: Context): Boolean =
        deployRootfsProfile(ctx, DistroInstallProfile.require("debian"))

    fun deployRootfsProfile(ctx: Context, profile: DistroInstallProfile): Boolean {
        val homeDir = TermuxHostPaths.homeDir(ctx).also { it.mkdirs() }
        val out = File(homeDir, profile.rootfsFileName)
        if (
            out.isFile &&
            out.length() > profile.rootfsMinBytes &&
            sha256(out) == profile.rootfsSha256
        ) {
            Log.i(TAG, "Rootfs already deployed and verified: ${out.absolutePath}")
            return true
        }
        return try {
            ctx.assets.open(profile.rootfsAsset).use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            }
            val sizeOk = out.length() > profile.rootfsMinBytes
            val shaOk = sha256(out) == profile.rootfsSha256
            if (sizeOk && shaOk) {
                Log.i(
                    TAG,
                    "Deployed rootfs: ${out.absolutePath} (${out.length()} bytes, SHA OK)"
                )
                true
            } else {
                Log.e(
                    TAG,
                    "Rootfs deploy failed gate for ${profile.rootfsFileName}: " +
                        "size=${out.length()} sha=${sha256(out)}"
                )
                out.delete()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deploy ${profile.rootfsAsset}", e)
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
