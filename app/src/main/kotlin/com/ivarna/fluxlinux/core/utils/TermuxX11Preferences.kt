package com.ivarna.fluxlinux.core.utils

import android.content.Context

/**
 * Manages Termux:X11 preferences
 */
object TermuxX11Preferences {
    
    private const val PREFS_NAME = "termux_x11_prefs"
    
    // Display Settings
    private const val KEY_DISPLAY_SCALE = "display_scale"
    private const val KEY_FULLSCREEN = "fullscreen"
    private const val KEY_HIDE_CUTOUT = "hide_cutout"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    
    // Input Settings
    private const val KEY_CAPTURE_POINTER = "capture_pointer"
    private const val KEY_SHOW_ADDITIONAL_KBD = "show_additional_kbd"
    private const val KEY_SHOW_IME = "show_ime"
    private const val KEY_PREFER_SCANCODES = "prefer_scancodes"
    private const val KEY_SCANCODE_WORKAROUND = "scancode_workaround"
    
    // Display Settings
    fun getDisplayScale(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DISPLAY_SCALE, 100)
    }
    
    fun setDisplayScale(context: Context, scale: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DISPLAY_SCALE, scale).apply()
        applyPreferences(context)
    }
    
    fun getFullscreen(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FULLSCREEN, true)
    }
    
    fun setFullscreen(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FULLSCREEN, enabled).apply()
        applyPreferences(context)
    }
    
    fun getHideCutout(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HIDE_CUTOUT, true)
    }
    
    fun setHideCutout(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HIDE_CUTOUT, enabled).apply()
        applyPreferences(context)
    }
    
    fun getKeepScreenOn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
    }
    
    fun setKeepScreenOn(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
        applyPreferences(context)
    }
    
    // Input Settings
    fun getCapturePointer(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CAPTURE_POINTER, true)
    }
    
    fun setCapturePointer(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CAPTURE_POINTER, enabled).apply()
        applyPreferences(context)
    }
    
    fun getShowAdditionalKeyboard(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_ADDITIONAL_KBD, false)
    }
    
    fun setShowAdditionalKeyboard(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOW_ADDITIONAL_KBD, enabled).apply()
        applyPreferences(context)
    }
    
    fun getShowIME(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_IME, true)
    }
    
    fun setShowIME(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOW_IME, enabled).apply()
        applyPreferences(context)
    }
    
    fun getPreferScancodes(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PREFER_SCANCODES, true)
    }
    
    fun setPreferScancodes(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PREFER_SCANCODES, enabled).apply()
        applyPreferences(context)
    }
    
    fun getScancodeWorkaround(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SCANCODE_WORKAROUND, true)
    }
    
    fun setScancodeWorkaround(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SCANCODE_WORKAROUND, enabled).apply()
        applyPreferences(context)
    }
    
    /**
     * Apply preferences to Termux:X11 via termux-x11-preference command
     */
    private fun applyPreferences(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Build preference command
            val prefCommand = buildString {
                append("termux-x11-preference ")
                append("\"fullscreen\"=\"${prefs.getBoolean(KEY_FULLSCREEN, true)}\" ")
                append("\"showAdditionalKbd\"=\"${prefs.getBoolean(KEY_SHOW_ADDITIONAL_KBD, false)}\" ")
                // Add more preferences as needed
            }
            
            android.util.Log.d("TermuxX11Prefs", "Applying preferences: $prefCommand")
            // Note: This would need to be executed via TermuxIntentFactory
        } catch (e: Exception) {
            android.util.Log.e("TermuxX11Prefs", "Failed to apply preferences", e)
        }
    }
    
    /**
     * Open Termux:X11 preferences activity
     */
    fun openTermuxX11Preferences(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.termux.x11")
            intent?.let {
                it.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(it)
            }
        } catch (e: Exception) {
            android.util.Log.e("TermuxX11Prefs", "Failed to open Termux:X11", e)
        }
    }
}
