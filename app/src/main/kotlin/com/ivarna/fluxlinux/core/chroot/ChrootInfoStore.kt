package com.ivarna.fluxlinux.core.chroot

import android.content.Context
import java.util.Locale

/**
 * Cached chroot settings state (size + process count).
 * Scoped by distroId, with backward compatibility for legacy unscoped Debian keys.
 */
object ChrootInfoStore {

    private const val PREFS = "flux_chroot_prefs"
    const val DEFAULT_DEBIAN_ID = "debian13_chroot"

    // Legacy unscoped keys
    private const val LEGACY_KEY_INSTALLED = "chroot_installed"
    private const val LEGACY_KEY_DIR = "chroot_dir"
    private const val LEGACY_KEY_BYTES = "chroot_size_bytes"
    private const val LEGACY_KEY_ROOT_OK = "chroot_root_ok"
    private const val LEGACY_KEY_SIZE_VIA_ROOT = "chroot_size_via_root"
    private const val LEGACY_KEY_LAST_MS = "chroot_last_ms"
    private const val LEGACY_KEY_PROC_COUNT = "chroot_proc_count"
    private const val LEGACY_KEY_PROC_LAST_MS = "chroot_proc_last_ms"

    private fun keyInstalled(distroId: String) = "chroot_installed_$distroId"
    private fun keyDir(distroId: String) = "chroot_dir_$distroId"
    private fun keyBytes(distroId: String) = "chroot_size_bytes_$distroId"
    private fun keyRootOk(distroId: String) = "chroot_root_ok_$distroId"
    private fun keySizeViaRoot(distroId: String) = "chroot_size_via_root_$distroId"
    private fun keyLastMs(distroId: String) = "chroot_last_ms_$distroId"
    private fun keyProcCount(distroId: String) = "chroot_proc_count_$distroId"
    private fun keyProcLastMs(distroId: String) = "chroot_proc_last_ms_$distroId"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveInstallInfo(
        ctx: Context,
        distroId: String = DEFAULT_DEBIAN_ID,
        installed: Boolean,
        dirExists: Boolean,
        bytes: Long?,
        rootOk: Boolean,
        viaRoot: Boolean
    ) {
        val p = prefs(ctx)
        val kInstalled = keyInstalled(distroId)
        val kDir = keyDir(distroId)
        val kRootOk = keyRootOk(distroId)
        val kBytes = keyBytes(distroId)
        val kViaRoot = keySizeViaRoot(distroId)
        val kLastMs = keyLastMs(distroId)

        val edit = p.edit()
            .putBoolean(kInstalled, installed)
            .putBoolean(kDir, dirExists)
            .putBoolean(kRootOk, rootOk)

        when {
            bytes != null && bytes >= 0L -> {
                edit.putLong(kBytes, bytes)
                edit.putBoolean(kViaRoot, viaRoot)
                edit.putLong(kLastMs, System.currentTimeMillis())
            }
            !rootOk || !dirExists -> {
                edit.putLong(kBytes, -1L)
                edit.putBoolean(kViaRoot, false)
                edit.putLong(kLastMs, System.currentTimeMillis())
            }
            else -> {
                // probe failed but dir present — keep prior good size
                if (!p.contains(kLastMs)) {
                    edit.putLong(kLastMs, System.currentTimeMillis())
                }
            }
        }
        edit.apply()
    }

    fun cachedBytes(ctx: Context, distroId: String = DEFAULT_DEBIAN_ID): Long? {
        val p = prefs(ctx)
        val kLastMs = keyLastMs(distroId)
        if (distroId == DEFAULT_DEBIAN_ID && !p.contains(kLastMs) && p.contains(LEGACY_KEY_LAST_MS)) {
            return p.getLong(LEGACY_KEY_BYTES, -1L).takeIf { it >= 0L }
        }
        return p.getLong(keyBytes(distroId), -1L).takeIf { it >= 0L }
    }

    fun cachedLastMs(ctx: Context, distroId: String = DEFAULT_DEBIAN_ID): Long {
        val p = prefs(ctx)
        val kLastMs = keyLastMs(distroId)
        if (distroId == DEFAULT_DEBIAN_ID && !p.contains(kLastMs) && p.contains(LEGACY_KEY_LAST_MS)) {
            return p.getLong(LEGACY_KEY_LAST_MS, 0L)
        }
        return p.getLong(kLastMs, 0L)
    }

    fun cachedRootOk(ctx: Context, distroId: String = DEFAULT_DEBIAN_ID): Boolean {
        val p = prefs(ctx)
        val kLastMs = keyLastMs(distroId)
        if (distroId == DEFAULT_DEBIAN_ID && !p.contains(kLastMs) && p.contains(LEGACY_KEY_LAST_MS)) {
            return p.getBoolean(LEGACY_KEY_ROOT_OK, false)
        }
        return p.getBoolean(keyRootOk(distroId), false)
    }

    fun cachedInstalled(ctx: Context, distroId: String = DEFAULT_DEBIAN_ID): Boolean {
        val p = prefs(ctx)
        val kLastMs = keyLastMs(distroId)
        if (distroId == DEFAULT_DEBIAN_ID && !p.contains(kLastMs) && p.contains(LEGACY_KEY_LAST_MS)) {
            return p.getBoolean(LEGACY_KEY_INSTALLED, false)
        }
        return p.getBoolean(keyInstalled(distroId), false)
    }

    fun cachedDir(ctx: Context, distroId: String = DEFAULT_DEBIAN_ID): Boolean {
        val p = prefs(ctx)
        val kLastMs = keyLastMs(distroId)
        if (distroId == DEFAULT_DEBIAN_ID && !p.contains(kLastMs) && p.contains(LEGACY_KEY_LAST_MS)) {
            return p.getBoolean(LEGACY_KEY_DIR, false)
        }
        return p.getBoolean(keyDir(distroId), false)
    }

    fun cachedViaRoot(ctx: Context, distroId: String = DEFAULT_DEBIAN_ID): Boolean {
        val p = prefs(ctx)
        val kLastMs = keyLastMs(distroId)
        if (distroId == DEFAULT_DEBIAN_ID && !p.contains(kLastMs) && p.contains(LEGACY_KEY_LAST_MS)) {
            return p.getBoolean(LEGACY_KEY_SIZE_VIA_ROOT, false)
        }
        return p.getBoolean(keySizeViaRoot(distroId), false)
    }

    fun hasCache(ctx: Context, distroId: String = DEFAULT_DEBIAN_ID): Boolean {
        val p = prefs(ctx)
        val kLastMs = keyLastMs(distroId)
        if (distroId == DEFAULT_DEBIAN_ID && !p.contains(kLastMs) && p.contains(LEGACY_KEY_LAST_MS)) {
            return true
        }
        return p.contains(kLastMs)
    }

    fun saveProcCount(ctx: Context, distroId: String = DEFAULT_DEBIAN_ID, count: Int) {
        prefs(ctx).edit()
            .putInt(keyProcCount(distroId), count)
            .putLong(keyProcLastMs(distroId), System.currentTimeMillis())
            .apply()
    }

    fun cachedProcCount(ctx: Context, distroId: String = DEFAULT_DEBIAN_ID): Int {
        val p = prefs(ctx)
        val kProcLastMs = keyProcLastMs(distroId)
        if (distroId == DEFAULT_DEBIAN_ID && !p.contains(kProcLastMs) && p.contains(LEGACY_KEY_PROC_LAST_MS)) {
            return p.getInt(LEGACY_KEY_PROC_COUNT, -1)
        }
        return p.getInt(keyProcCount(distroId), -1)
    }

    fun cachedProcLastMs(ctx: Context, distroId: String = DEFAULT_DEBIAN_ID): Long {
        val p = prefs(ctx)
        val kProcLastMs = keyProcLastMs(distroId)
        if (distroId == DEFAULT_DEBIAN_ID && !p.contains(kProcLastMs) && p.contains(LEGACY_KEY_PROC_LAST_MS)) {
            return p.getLong(LEGACY_KEY_PROC_LAST_MS, 0L)
        }
        return p.getLong(kProcLastMs, 0L)
    }

    fun hasProcCache(ctx: Context, distroId: String = DEFAULT_DEBIAN_ID): Boolean {
        val p = prefs(ctx)
        val kProcLastMs = keyProcLastMs(distroId)
        if (distroId == DEFAULT_DEBIAN_ID && !p.contains(kProcLastMs) && p.contains(LEGACY_KEY_PROC_LAST_MS)) {
            return true
        }
        return p.contains(kProcLastMs)
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    fun formatCacheAge(lastMs: Long): String {
        if (lastMs <= 0L) return "unknown"
        val mins = ((System.currentTimeMillis() - lastMs) / 60_000L).coerceAtLeast(0L)
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 1440 -> "${mins / 60}h ago"
            else -> "${mins / 1440}d ago"
        }
    }

    fun formatStorageBytes(bytes: Long?): Pair<String, String> {
        if (bytes == null || bytes < 0L) return "—" to ""
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format(Locale.US, "%.1f", bytes / gb) to "GB"
            bytes >= mb -> String.format(Locale.US, "%.0f", bytes / mb) to "MB"
            bytes >= kb -> String.format(Locale.US, "%.0f", bytes / kb) to "KB"
            else -> bytes.toString() to "B"
        }
    }
}
