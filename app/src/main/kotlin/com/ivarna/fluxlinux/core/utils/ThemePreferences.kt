package com.ivarna.fluxlinux.core.utils

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode {
    LIGHT,
    DARK,
    GRUVBOX,
    NORD,
    DRACULA,
    SOLARIZED,
    MONOKAI,
    SKY_BREEZE,
    LAVENDER_DREAM,
    MINT_FRESH,
    AMOLED_BLACK,
    SYSTEM
}

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    fun getThemeMode(): ThemeMode {
        val modeName = prefs.getString("theme_mode", ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(modeName ?: ThemeMode.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }
}
