package com.fluxlinux.app.core.utils

import android.content.Context
import android.content.pm.PackageManager

/**
 * Manages application state and package detection
 */
object StateManager {
    
    /**
     * Check if a package is installed
     */
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * Get installed package version
     */
    fun getPackageVersion(context: Context, packageName: String): String? {
        return try {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    /**
     * Check if Termux is installed with minimum version
     */
    fun isTermuxInstalled(context: Context): Boolean {
        return isPackageInstalled(context, "com.termux")
    }
    
    /**
     * Check if Termux:X11 is installed
     */
    fun isTermuxX11Installed(context: Context): Boolean {
        return isPackageInstalled(context, "com.termux.x11")
    }
    
    /**
     * Get Termux version
     */
    fun getTermuxVersion(context: Context): String {
        return getPackageVersion(context, "com.termux") ?: "Not Installed"
    }
    
    /**
     * Get Termux:X11 version
     */
    fun getTermuxX11Version(context: Context): String {
        return getPackageVersion(context, "com.termux.x11") ?: "Not Installed"
    }
    
    /**
     * Check if Termux environment has been initialized
     */
    fun isTermuxInitialized(context: Context): Boolean {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("termux_initialized", false)
    }
    
    /**
     * Mark Termux environment as initialized
     */
    fun setTermuxInitialized(context: Context, initialized: Boolean) {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("termux_initialized", initialized).apply()
    }
    
    /**
     * Check if Termux tweaks have been applied
     */
    fun isTweaksApplied(context: Context): Boolean {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("tweaks_applied", false)
    }
    
    /**
     * Mark Termux tweaks as applied
     */
    fun setTweaksApplied(context: Context, applied: Boolean) {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("tweaks_applied", applied).apply()
    }
}
