package com.zenithblue.fluxlinux.core.utils

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
    }
    
    fun getFullscreen(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FULLSCREEN, true)
    }
    
    fun setFullscreen(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FULLSCREEN, enabled).apply()
    }
    
    fun getHideCutout(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HIDE_CUTOUT, true)
    }
    
    fun setHideCutout(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HIDE_CUTOUT, enabled).apply()
    }
    
    fun getKeepScreenOn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
    }
    
    fun setKeepScreenOn(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
    }
    
    // Input Settings
    fun getCapturePointer(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CAPTURE_POINTER, true)
    }
    
    fun setCapturePointer(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CAPTURE_POINTER, enabled).apply()
    }
    
    fun getShowAdditionalKeyboard(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_ADDITIONAL_KBD, false)
    }
    
    fun setShowAdditionalKeyboard(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOW_ADDITIONAL_KBD, enabled).apply()
    }
    
    fun getShowIME(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_IME, true)
    }
    
    fun setShowIME(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOW_IME, enabled).apply()
    }
    
    fun getPreferScancodes(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PREFER_SCANCODES, true)
    }
    
    fun setPreferScancodes(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PREFER_SCANCODES, enabled).apply()
    }
    
    fun getScancodeWorkaround(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SCANCODE_WORKAROUND, true)
    }
    
    fun setScancodeWorkaround(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SCANCODE_WORKAROUND, enabled).apply()
    }
    
    /**
     * Apply preferences by writing them to a script that start_gui.sh will execute.
     */
    /**
     * Apply preferences by running the `termux-x11-preference` command in Termux.
     */
    fun applyToTermux(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Build the command script
            // Consolidate all preferences into a single command to avoid race conditions/hangs
            val commandArgs = StringBuilder()
            
            fun addPref(key: String, value: String) {
                // Formatting based on tool usage help: {key:value}
                // Previous attempts with key=value failed. Switching to colon separator.
                // We'll treat values as strings but minimize quoting to avoid shell issues unless spaces exist.
                commandArgs.append(" $key:$value") 
            }
            
            // Display
            addPref("displayScale", prefs.getInt(KEY_DISPLAY_SCALE, 100).toString())
            addPref("fullscreen", prefs.getBoolean(KEY_FULLSCREEN, true).toString())
            addPref("hideCutout", prefs.getBoolean(KEY_HIDE_CUTOUT, true).toString())
            addPref("keepScreenOn", prefs.getBoolean(KEY_KEEP_SCREEN_ON, true).toString())
            addPref("displayResolutionMode", "scaled")
            
            // Input
            addPref("pointerCapture", prefs.getBoolean(KEY_CAPTURE_POINTER, true).toString())
            addPref("showAdditionalKbd", prefs.getBoolean(KEY_SHOW_ADDITIONAL_KBD, false).toString())
            addPref("showIMEWhileExternalConnected", prefs.getBoolean(KEY_SHOW_IME, true).toString())
            addPref("preferScancodes", prefs.getBoolean(KEY_PREFER_SCANCODES, true).toString())
            addPref("hardwareKbdScancodesWorkaround", prefs.getBoolean(KEY_SCANCODE_WORKAROUND, true).toString())
            
            // Other useful defaults we want to enforce
            addPref("clipboardEnable", "true")
            addPref("touchMode", "Trackpad")
            addPref("scaleTouchpad", "true")

            val allArgs = commandArgs.toString()
            
            // Build a complete, robust bash script
            // Note: Removed 'read -p' as we are running in background now
            val fullScript = """
                #!/data/data/com.termux/files/usr/bin/bash
                LOG_FILE="${'$'}HOME/.fluxlinux/x11_prefs.log"
                exec > >(tee "${'$'}LOG_FILE") 2>&1
                
                echo "[$(date)] Applying X11 Preferences..."
                
                if ! command -v termux-x11-preference &> /dev/null; then
                    echo "Error: termux-x11-preference tool not found!"
                    echo "Please ensure Termux:X11 is installed correctly."
                    exit 1
                fi
                
                # Debug: List valid preferences to log
                echo "--- Valid Preferences List ---"
                termux-x11-preference list
                echo "------------------------------"
                
                # Execute ALL preferences in one go
                echo "Running: termux-x11-preference$allArgs"
                termux-x11-preference$allArgs
                
                if [ ${'$'}? -eq 0 ]; then
                    echo "✅ Preferences applied successfully."
                    # Try toast, but don't fail if missing
                    termux-toast "Termux:X11 Settings Applied" 2>/dev/null || true
                    exit 0
                else
                    echo "❌ Failed to apply some preferences."
                    exit 1
                fi
            """.trimIndent()
            
            // Encode to Base64 to avoid all here-doc/quoting issues
            val scriptB64 = android.util.Base64.encodeToString(fullScript.toByteArray(), android.util.Base64.NO_WRAP)
            
            // Command to decode and run
            val cmd = "mkdir -p \$HOME/.fluxlinux && echo \"$scriptB64\" | base64 -d > \$HOME/.fluxlinux/apply_x11_prefs.sh && chmod +x \$HOME/.fluxlinux/apply_x11_prefs.sh && \$HOME/.fluxlinux/apply_x11_prefs.sh"
            
            // Send command Intent (Background execution restored)
            val intent = com.zenithblue.fluxlinux.core.data.TermuxIntentFactory.buildRunCommandIntent(cmd, runInBackground = false)
            context.startService(intent)
            
            android.util.Log.d("TermuxX11Prefs", "Applied preferences via termux-x11-preference")
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
