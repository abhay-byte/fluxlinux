package com.ivarna.fluxlinux.core.utils

import android.content.Context

/**
 * Global terminal UI prefs (font zoom + ExtraKeys toolbar).
 * Mirrors nativecode-ai `pref_show_extra_keys` / font size behaviour.
 */
object TerminalPreferences {

    private const val PREFS_NAME = "flux_terminal_prefs"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_SHOW_EXTRA_KEYS = "show_extra_keys"

    const val FONT_MIN = 10
    const val FONT_MAX = 48
    const val FONT_DEFAULT = 24

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFontSize(context: Context): Int =
        prefs(context).getInt(KEY_FONT_SIZE, FONT_DEFAULT).coerceIn(FONT_MIN, FONT_MAX)

    fun setFontSize(context: Context, size: Int) {
        prefs(context).edit()
            .putInt(KEY_FONT_SIZE, size.coerceIn(FONT_MIN, FONT_MAX))
            .apply()
    }

    fun isExtraKeysEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_EXTRA_KEYS, true)

    fun setExtraKeysEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_EXTRA_KEYS, enabled).apply()
    }
}
