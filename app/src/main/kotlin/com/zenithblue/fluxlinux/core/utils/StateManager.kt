package com.zenithblue.fluxlinux.core.utils

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages application state and package detection
 */
object StateManager {
    
    // UI Refresh Trigger
    private val _refreshTrigger = kotlinx.coroutines.flow.MutableStateFlow(0)
    val refreshTrigger: kotlinx.coroutines.flow.StateFlow<Int> = _refreshTrigger.asStateFlow()
    
    fun triggerRefresh() {
        _refreshTrigger.value += 1
    }
    
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
     * Clear all state associated with a distro
     */
    fun clearDistroState(context: Context, distroId: String) {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Remove installation status
        editor.remove("distro_${distroId}_installed")
        
        // Remove GUI running status
        editor.remove("distro_${distroId}_gui_running")
        
        // Remove all component statuses for this distro
        // Since we don't track a list of installed components separately, 
        // we iterate through all keys to find those matching the pattern.
        // Pattern: distro_${distroId}_component_${componentId}
        val allKeys = prefs.all.keys
        val componentPrefix = "distro_${distroId}_component_"
        
        for (key in allKeys) {
            if (key.startsWith(componentPrefix)) {
                editor.remove(key)
            }
        }
        
        editor.apply()
        android.util.Log.d("StateManager", "Cleared all state for distro: $distroId")
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

    /**
     * Check if app has PACKAGE_USAGE_STATS permission
     */


    /**
     * Get total package size including app, data, and cache
     */
    fun getPackageSize(context: Context, packageName: String): String {
        return try {
            
            // Fallback: Calculate via directory sizes (less accurate but works without permission)
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            var totalSize = 0L
            
            // Add APK size
            val apkFile = java.io.File(appInfo.publicSourceDir)
            totalSize += apkFile.length()
            
            // Try to estimate data directory size
            try {
                val dataDir = java.io.File(appInfo.dataDir)
                totalSize += getDirectorySize(dataDir)
            } catch (e: Exception) {
                android.util.Log.d("StateManager", "Could not access data dir for $packageName: ${e.message}")
            }
            
            val totalGb = totalSize / (1024.0 * 1024.0 * 1024.0)
            val totalMb = totalSize / (1024.0 * 1024.0)
            
            if (totalGb >= 1.0) {
                "%.2f GB".format(totalGb)
            } else {
                "%.0f MB".format(totalMb)
            }
        } catch (e: Exception) {
            android.util.Log.e("StateManager", "Error getting package size for $packageName", e)
            "Unknown"
        }
    }
    
    /**
     * Calculate directory size recursively
     */
    private fun getDirectorySize(directory: java.io.File): Long {
        var size = 0L
        try {
            if (directory.exists()) {
                val files = directory.listFiles()
                if (files != null) {
                    for (file in files) {
                        size += if (file.isDirectory) {
                            getDirectorySize(file)
                        } else {
                            file.length()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("StateManager", "Error calculating directory size: ${e.message}")
        }
        return size
    }

    /**
     * Check if a script has been successfully executed
     */
    fun getScriptStatus(context: Context, scriptName: String): Boolean {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("script_${scriptName}_success", false)
    }

    /**
     * Set script execution status
     */
    fun setScriptStatus(context: Context, scriptName: String, success: Boolean) {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("script_${scriptName}_success", success).apply()
        android.util.Log.d("StateManager", "Script $scriptName status set to: $success")
    }
    
    /**
     * Check if Unexpected Keyboard is installed
     */
    fun isUnexpectedKeyboardInstalled(context: Context): Boolean {
        return isPackageInstalled(context, "juloo.keyboard2")
    }
    
    /**
     * Get Unexpected Keyboard version
     */
    fun getUnexpectedKeyboardVersion(context: Context): String {
        return getPackageVersion(context, "juloo.keyboard2") ?: "Not Installed"
    }
    
    /**
     * Check if a distro component is installed
     */
    fun isComponentInstalled(context: Context, distroId: String, componentId: String): Boolean {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("distro_${distroId}_component_${componentId}", false)
    }

    /**
     * Mark a distro component as installed
     */
    fun setComponentInstalled(context: Context, distroId: String, componentId: String, installed: Boolean) {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("distro_${distroId}_component_${componentId}", installed).apply()
        android.util.Log.d("StateManager", "Distro $distroId component $componentId status set to: $installed")
    }
    
    /**
     * Check if GUI is running for a distro
     */
    fun isGuiRunning(context: Context, distroId: String): Boolean {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("distro_${distroId}_gui_running", false)
    }
    
    /**
     * Set GUI running state for a distro
     */
    fun setGuiRunning(context: Context, distroId: String, running: Boolean) {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("distro_${distroId}_gui_running", running).apply()
        android.util.Log.d("StateManager", "Distro $distroId GUI running status set to: $running")
        // Trigger UI refresh
        triggerRefresh()
    }
    
    /**
     * Get all distros with GUI running
     */
    fun getDistrosWithGuiRunning(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        return prefs.all.keys
            .filter { it.startsWith("distro_") && it.endsWith("_gui_running") }
            .filter { prefs.getBoolean(it, false) }
            .map { it.removePrefix("distro_").removeSuffix("_gui_running") }
            .toSet()
    }

    /**
     * Get which GUI type is running for a distro ("xfce4", "kde", or "")
     */
    fun getGuiRunningType(context: Context, distroId: String): String {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        return prefs.getString("distro_${distroId}_gui_type", "") ?: ""
    }

    /**
     * Set which GUI type is running for a distro ("xfce4", "kde", or "")
     */
    fun setGuiRunningType(context: Context, distroId: String, type: String) {
        val prefs = context.getSharedPreferences("fluxlinux_state", Context.MODE_PRIVATE)
        prefs.edit().putString("distro_${distroId}_gui_type", type).apply()
        android.util.Log.d("StateManager", "Distro $distroId GUI type set to: $type")
    }

}
