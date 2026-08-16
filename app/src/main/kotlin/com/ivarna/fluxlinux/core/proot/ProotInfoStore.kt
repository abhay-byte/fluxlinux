package com.ivarna.fluxlinux.core.proot

import android.content.Context
import java.util.Locale

/**
 * Cached PRoot settings state (container sizes).
 * Stored in `flux_proot_prefs` and scoped by distroId.
 */
object ProotInfoStore {

    private const val PREFS = "flux_proot_prefs"

    private fun keyInstalled(distroId: String) = "proot_installed_$distroId"
    private fun keyDir(distroId: String) = "proot_dir_$distroId"
    private fun keyBytes(distroId: String) = "proot_size_bytes_$distroId"
    private fun keyLastMs(distroId: String) = "proot_last_ms_$distroId"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveInstallInfo(
        ctx: Context,
        distroId: String,
        installed: Boolean,
        dirExists: Boolean,
        bytes: Long?
    ) {
        val p = prefs(ctx)
        val kInstalled = keyInstalled(distroId)
        val kDir = keyDir(distroId)
        val kBytes = keyBytes(distroId)
        val kLastMs = keyLastMs(distroId)

        val edit = p.edit()
            .putBoolean(kInstalled, installed)
            .putBoolean(kDir, dirExists)

        when {
            bytes != null && bytes >= 0L -> {
                edit.putLong(kBytes, bytes)
                edit.putLong(kLastMs, System.currentTimeMillis())
            }
            !dirExists -> {
                edit.putLong(kBytes, -1L)
                edit.putLong(kLastMs, System.currentTimeMillis())
            }
            else -> {
                // keep prior good size if probe failed but dir present
                if (!p.contains(kLastMs)) {
                    edit.putLong(kLastMs, System.currentTimeMillis())
                }
            }
        }
        edit.apply()
    }

    fun cachedBytes(ctx: Context, distroId: String): Long? =
        prefs(ctx).getLong(keyBytes(distroId), -1L).takeIf { it >= 0L }

    fun cachedLastMs(ctx: Context, distroId: String): Long =
        prefs(ctx).getLong(keyLastMs(distroId), 0L)

    fun cachedInstalled(ctx: Context, distroId: String): Boolean =
        prefs(ctx).getBoolean(keyInstalled(distroId), false)

    fun cachedDir(ctx: Context, distroId: String): Boolean =
        prefs(ctx).getBoolean(keyDir(distroId), false)

    fun hasCache(ctx: Context, distroId: String): Boolean =
        prefs(ctx).contains(keyLastMs(distroId))

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
