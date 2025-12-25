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
            android.util.Log.d("StateManager", "Package $packageName is installed")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            android.util.Log.d("StateManager", "Package $packageName not found: ${e.message}")
            false
        } catch (e: Exception) {
            android.util.Log.e("StateManager", "Error checking package $packageName", e)
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
        val result = isPackageInstalled(context, "com.termux.x11")
        android.util.Log.d("StateManager", "isTermuxX11Installed: $result")
        return result
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
    
    /**
     * Check if a distro is installed
     */
    fun isDistroInstalled(context: Context, distroId: String): Boolean {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("distro_${distroId}_installed", false)
    }
    
    /**
     * Mark a distro as installed
     */
    fun setDistroInstalled(context: Context, distroId: String, installed: Boolean) {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("distro_${distroId}_installed", installed).apply()
        android.util.Log.d("StateManager", "Distro $distroId installation status set to: $installed")
    }
    
    /**
     * Get all installed distros
     */
    fun getInstalledDistros(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        return prefs.all.keys
            .filter { it.startsWith("distro_") && it.endsWith("_installed") }
            .filter { prefs.getBoolean(it, false) }
            .map { it.removePrefix("distro_").removeSuffix("_installed") }
            .toSet()
    }
    
    /**
     * Check if onboarding has been completed
     */
    fun isOnboardingComplete(context: Context): Boolean {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("onboarding_complete", false)
    }
    
    /**
     * Mark onboarding as complete
     */
    fun setOnboardingComplete(context: Context, complete: Boolean) {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_complete", complete).apply()
        android.util.Log.d("StateManager", "Onboarding completion set to: $complete")
    }
    
    /**
     * Check if Termux connection fix has been applied
     */
    fun isConnectionFixed(context: Context): Boolean {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("connection_fixed", false)
    }
    
    /**
     * Mark connection fix as applied
     */
    fun setConnectionFixed(context: Context, fixed: Boolean) {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("connection_fixed", fixed).apply()
        android.util.Log.d("StateManager", "Connection fix status set to: $fixed")
    }
}
