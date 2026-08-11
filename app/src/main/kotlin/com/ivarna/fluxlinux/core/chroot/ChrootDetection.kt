package com.ivarna.fluxlinux.core.chroot

import android.os.SystemClock
import android.util.Log
import com.ivarna.fluxlinux.core.root.ChrootPaths
import com.ivarna.fluxlinux.core.root.RootShell
import java.io.File

/**
 * Auto-detection for Debian chroot at [ChrootPaths.CHROOT_PATH].
 *
 * App SELinux often cannot read `/data/local/tmp` (`shell_data_file`), so bare
 * [File.exists] is a false negative. Detection order:
 * 1. Direct file probe (when policy allows)
 * 2. Root `test -e` probe (SSOT for install/settings)
 * 3. Short TTL cache for UI
 *
 * Ported from termux-lib [ProjectPathResolver] + FluxLinux install probe.
 */
object ChrootDetection {

    private const val TAG = "ChrootDetection"
    private const val CACHE_TTL_MS = 30_000L

    @Volatile private var installedCache: Boolean? = null
    @Volatile private var cacheAtMs: Long = 0L

    data class Snapshot(
        val installed: Boolean,
        val markerOk: Boolean,
        val dirExists: Boolean,
        val viaRoot: Boolean,
        val rootOk: Boolean
    )

    fun chrootPath(): String = ChrootPaths.CHROOT_PATH

    fun rootfsDir(): File = File(ChrootPaths.CHROOT_PATH)

    /** Marker file written by setup_debian13_chroot. */
    fun markerFile(): File = File(ChrootPaths.CHROOT_PATH, ".flux_configured")

    /** Fast app-visible check (may be false under SELinux even when installed). */
    fun isVisibleToApp(): Boolean {
        val root = ChrootPaths.CHROOT_PATH
        return File("$root/.flux_configured").exists() ||
            File("$root/bin/sh").exists() ||
            File("$root/usr/bin/bash").exists() ||
            File("$root/usr/bin/sh").exists() ||
            File(root).isDirectory
    }

    fun isMarkerVisibleToApp(): Boolean = markerFile().exists()

    fun isDirVisibleToApp(): Boolean = rootfsDir().isDirectory

    /**
     * True when chroot looks installed. Uses cache; call [invalidate] after
     * install/uninstall. Safe on any thread; root probe blocks briefly.
     */
    fun isInstalled(): Boolean {
        if (isVisibleToApp() && (isMarkerVisibleToApp() || shellPresentToApp())) {
            installedCache = true
            cacheAtMs = SystemClock.elapsedRealtime()
            return true
        }
        val now = SystemClock.elapsedRealtime()
        val cached = installedCache
        if (cached != null && now - cacheAtMs < CACHE_TTL_MS) return cached

        val snap = probe(forceRoot = true)
        installedCache = snap.installed
        cacheAtMs = now
        return snap.installed
    }

    fun invalidate() {
        installedCache = null
        cacheAtMs = 0L
    }

    /**
     * Full probe for settings UI. Background thread preferred when forceRoot.
     */
    fun probe(forceRoot: Boolean = true): Snapshot {
        val markerApp = isMarkerVisibleToApp()
        val dirApp = isDirVisibleToApp()
        val shellApp = shellPresentToApp()

        if (!forceRoot && (markerApp || shellApp)) {
            return Snapshot(
                installed = true,
                markerOk = markerApp,
                dirExists = dirApp || shellApp,
                viaRoot = false,
                rootOk = false
            )
        }

        if (!RootShell.isRootAvailable()) {
            val installed = markerApp || shellApp || dirApp
            return Snapshot(
                installed = installed,
                markerOk = markerApp,
                dirExists = dirApp,
                viaRoot = false,
                rootOk = false
            )
        }

        val root = ChrootPaths.CHROOT_PATH
        val out = try {
            RootShell.capture(
                "M=0; D=0; S=0; " +
                    "[ -e '$root/.flux_configured' ] && M=1; " +
                    "[ -d '$root' ] && D=1; " +
                    "{ [ -e '$root/bin/sh' ] || [ -e '$root/usr/bin/bash' ] || [ -e '$root/usr/bin/sh' ]; } && S=1; " +
                    "echo MARKER=\$M DIR=\$D SHELL=\$S",
                timeoutMs = 8_000L
            )
        } catch (e: Exception) {
            Log.w(TAG, "probe failed: ${e.message}")
            return Snapshot(
                installed = markerApp || shellApp || dirApp,
                markerOk = markerApp,
                dirExists = dirApp,
                viaRoot = false,
                rootOk = true
            )
        }

        var marker = markerApp
        var dir = dirApp
        var shell = shellApp
        for (line in out.lineSequence()) {
            val t = line.trim()
            if (!t.contains("MARKER=")) continue
            // MARKER=1 DIR=1 SHELL=1
            t.split(Regex("\\s+")).forEach { tok ->
                when {
                    tok.startsWith("MARKER=") ->
                        marker = tok.removePrefix("MARKER=") == "1"
                    tok.startsWith("DIR=") ->
                        dir = tok.removePrefix("DIR=") == "1"
                    tok.startsWith("SHELL=") ->
                        shell = tok.removePrefix("SHELL=") == "1"
                }
            }
        }

        val installed = marker || shell || dir
        installedCache = installed
        cacheAtMs = SystemClock.elapsedRealtime()
        return Snapshot(
            installed = installed,
            markerOk = marker,
            dirExists = dir || shell,
            viaRoot = true,
            rootOk = true
        )
    }

    fun isXfceInstalled(): Boolean {
        val path = "${ChrootPaths.CHROOT_PATH}/usr/bin/startxfce4"
        if (File(path).exists()) return true
        if (!RootShell.isRootAvailable()) return false
        return try {
            RootShell.capture(
                "if [ -e '$path' ]; then echo YES; else echo NO; fi",
                timeoutMs = 8_000L
            ).contains("YES")
        } catch (_: Exception) {
            false
        }
    }

    private fun shellPresentToApp(): Boolean {
        val root = ChrootPaths.CHROOT_PATH
        return File("$root/bin/sh").exists() ||
            File("$root/usr/bin/bash").exists() ||
            File("$root/usr/bin/sh").exists()
    }
}
