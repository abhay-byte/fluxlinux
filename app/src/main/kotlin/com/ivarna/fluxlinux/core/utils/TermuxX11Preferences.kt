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
        return prefs.getInt(KEY_DISPLAY_SCALE, 200)
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
     * Apply preferences by writing them to a script that start_gui.sh will execute.
     */
    fun applyPreferences(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Build preference command
            // We use 'termux-x11-preference' tool which comes with the package
            val prefCommand = buildString {
                append("termux-x11-preference ")
                append("-s ") // Silent mode if available, or just key values
                append("\"fullscreen\"=\"${prefs.getBoolean(KEY_FULLSCREEN, true)}\" ")
                append("\"showAdditionalKbd\"=\"${prefs.getBoolean(KEY_SHOW_ADDITIONAL_KBD, false)}\" ")
                append("\"hideCutout\"=\"${prefs.getBoolean(KEY_HIDE_CUTOUT, true)}\" ")
                append("\"keepScreenOn\"=\"${prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)}\" ")
                append("\"capturePointer\"=\"${prefs.getBoolean(KEY_CAPTURE_POINTER, true)}\" ")
                append("\"showIME\"=\"${prefs.getBoolean(KEY_SHOW_IME, true)}\" ")
                append("\"preferScancodes\"=\"${prefs.getBoolean(KEY_PREFER_SCANCODES, true)}\" ")
                append("\"scancodeWorkaround\"=\"${prefs.getBoolean(KEY_SCANCODE_WORKAROUND, true)}\" ")
            }
            
            // Create the script content
            val scriptContent = """
                #!/bin/bash
                # Auto-generated Termux:X11 preferences
                # Applied by FluxLinux
                
                # Check if Termux:X11 is running before applying
                if pgrep -f "termux.x11" > /dev/null; then
                    $prefCommand
                fi
            """.trimIndent()
            
            // Write to file using cat
            // We escape the content properly for bash
            val escapedContent = scriptContent.replace("$", "\\$")
            val writeCmd = "mkdir -p \$HOME/.fluxlinux && cat << 'EOF_PREFS' > \$HOME/.fluxlinux/x11_preferences.sh\n$scriptContent\nEOF_PREFS\nchmod +x \$HOME/.fluxlinux/x11_preferences.sh"
            
            // Send command Intent
            // Force background execution to prevent Termux from stealing focus
            val intent = com.ivarna.fluxlinux.core.data.TermuxIntentFactory.buildRunCommandIntent(writeCmd, runInBackground = true)
            context.startService(intent)
            
            android.util.Log.d("TermuxX11Prefs", "Updated preference script")
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
