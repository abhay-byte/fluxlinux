package com.ivarna.fluxlinux.core.chroot

import android.content.Context

/**
 * Cached chroot settings state (size + process count).
 * Mirrors termux-lib `nativecode_prefs` chroot_* keys under flux prefs.
 */
object ChrootInfoStore {

    private const val PREFS = "flux_chroot_prefs"

    private const val KEY_INSTALLED = "chroot_installed"
    private const val KEY_DIR = "chroot_dir"
    private const val KEY_BYTES = "chroot_size_bytes"
    private const val KEY_ROOT_OK = "chroot_root_ok"
    private const val KEY_SIZE_VIA_ROOT = "chroot_size_via_root"
    private const val KEY_LAST_MS = "chroot_last_ms"
    private const val KEY_PROC_COUNT = "chroot_proc_count"
    private const val KEY_PROC_LAST_MS = "chroot_proc_last_ms"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveInstallInfo(
        ctx: Context,
        installed: Boolean,
        dirExists: Boolean,
        bytes: Long?,
        rootOk: Boolean,
        viaRoot: Boolean
    ) {
        val p = prefs(ctx)
        val edit = p.edit()
            .putBoolean(KEY_INSTALLED, installed)
            .putBoolean(KEY_DIR, dirExists)
            .putBoolean(KEY_ROOT_OK, rootOk)

        when {
            bytes != null && bytes >= 0L -> {
                edit.putLong(KEY_BYTES, bytes)
                edit.putBoolean(KEY_SIZE_VIA_ROOT, viaRoot)
                edit.putLong(KEY_LAST_MS, System.currentTimeMillis())
            }
            !rootOk || !dirExists -> {
                edit.putLong(KEY_BYTES, -1L)
                edit.putBoolean(KEY_SIZE_VIA_ROOT, false)
                edit.putLong(KEY_LAST_MS, System.currentTimeMillis())
            }
            else -> {
                // probe failed but dir present — keep prior good size
                if (!p.contains(KEY_LAST_MS)) {
                    edit.putLong(KEY_LAST_MS, System.currentTimeMillis())
                }
            }
        }
        edit.apply()
    }

    fun cachedBytes(ctx: Context): Long? =
        prefs(ctx).getLong(KEY_BYTES, -1L).takeIf { it >= 0L }

    fun cachedLastMs(ctx: Context): Long =
        prefs(ctx).getLong(KEY_LAST_MS, 0L)

    fun cachedRootOk(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ROOT_OK, false)

    fun cachedInstalled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_INSTALLED, false)

    fun cachedDir(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DIR, false)

    fun cachedViaRoot(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SIZE_VIA_ROOT, false)

    fun hasCache(ctx: Context): Boolean =
        prefs(ctx).contains(KEY_LAST_MS)

    fun saveProcCount(ctx: Context, count: Int) {
        prefs(ctx).edit()
            .putInt(KEY_PROC_COUNT, count)
            .putLong(KEY_PROC_LAST_MS, System.currentTimeMillis())
            .apply()
    }

    fun cachedProcCount(ctx: Context): Int =
        prefs(ctx).getInt(KEY_PROC_COUNT, -1)

    fun cachedProcLastMs(ctx: Context): Long =
        prefs(ctx).getLong(KEY_PROC_LAST_MS, 0L)

    fun hasProcCache(ctx: Context): Boolean =
        prefs(ctx).contains(KEY_PROC_LAST_MS)

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
            bytes >= gb -> String.format("%.1f", bytes / gb) to "GB"
            bytes >= mb -> String.format("%.0f", bytes / mb) to "MB"
            bytes >= kb -> String.format("%.0f", bytes / kb) to "KB"
            else -> bytes.toString() to "B"
        }
    }
}
