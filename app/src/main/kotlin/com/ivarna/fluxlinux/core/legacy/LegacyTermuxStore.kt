package com.ivarna.fluxlinux.core.legacy

import android.content.Context

object LegacyTermuxStore {
    const val PREFS_NAME = "flux_legacy_termux_prefs"

    data class Row(
        val id: String,
        val bytes: Long?,
        val layout: String?,
        val hostPath: String? = null
    )

    data class Scan(
        val rows: List<Row>,
        val scannedAtMs: Long,
        val error: String?,
        val lastActionMs: Long = 0L
    )

    fun load(ctx: Context): Scan {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawIds = prefs.getString("scan_ids", null)
        val rawBytes = prefs.getString("scan_bytes", null)
        val rawLayouts = prefs.getString("scan_layouts", null)
        val scanMs = prefs.getLong("scan_ms", 0L)
        val error = prefs.getString("scan_error", null)
        val actionMs = prefs.getLong("last_action_ms", 0L)

        if (rawIds.isNullOrBlank()) {
            return Scan(emptyList(), scanMs, error, actionMs)
        }

        val ids = rawIds.split(",").filter { it.isNotBlank() }
        val bytes = rawBytes?.split(",")?.map { it.toLongOrNull() } ?: emptyList()
        val layouts = rawLayouts?.split(",") ?: emptyList()

        val rows = ids.mapIndexed { idx, id ->
            val b = bytes.getOrNull(idx)
            val layout = layouts.getOrNull(idx)
            val path = LegacyTermuxBridge.hostPath(id, layout)
            Row(id = id, bytes = b, layout = layout, hostPath = path)
        }
        return Scan(rows, scanMs, error, actionMs)
    }

    fun saveScan(ctx: Context, rows: List<Row>, scannedAtMs: Long) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ids = rows.joinToString(",") { it.id }
        val bytes = rows.joinToString(",") { it.bytes?.toString() ?: "" }
        val layouts = rows.joinToString(",") { it.layout ?: "" }
        prefs.edit()
            .putString("scan_ids", ids)
            .putString("scan_bytes", bytes)
            .putString("scan_layouts", layouts)
            .putLong("scan_ms", scannedAtMs)
            .putLong("last_action_ms", System.currentTimeMillis())
            .remove("scan_error")
            .apply()
    }

    fun saveError(ctx: Context, message: String) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("scan_error", message)
            .putLong("last_action_ms", System.currentTimeMillis())
            .apply()
    }

    fun recordActionFinished(ctx: Context, error: String? = null) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().putLong("last_action_ms", System.currentTimeMillis())
        if (error != null) {
            editor.putString("scan_error", error)
        }
        editor.apply()
    }

    fun remove(ctx: Context, id: String): Boolean {
        if (!LegacyTermuxBridge.isSafeProotId(id)) return false
        val scan = load(ctx)
        val remaining = scan.rows.filter { it.id != id }
        if (remaining.size == scan.rows.size) return false
        saveScan(ctx, remaining, scan.scannedAtMs)
        return true
    }

    fun setPingOk(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("ping_ok", true)
            .putLong("ping_ms", System.currentTimeMillis())
            .apply()
    }

    fun isPingOk(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("ping_ok", false)
    }

    fun clear(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
