package com.ivarna.fluxlinux.core.utils

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

    /**
     * Check if app has PACKAGE_USAGE_STATS permission
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Open Settings to grant Usage Access permission
     */
    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("StateManager", "Failed to open Usage Access Settings", e)
        }
    }

    /**
     * Get total package size including app, data, and cache
     */
    fun getPackageSize(context: Context, packageName: String): String {
        return try {
            // Try StorageStatsManager if we have permission (Android 8.0+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && hasUsageStatsPermission(context)) {
                try {
                    val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as android.app.usage.StorageStatsManager
                    val userHandle = android.os.Process.myUserHandle()
                    
                    val stats = storageStatsManager.queryStatsForPackage(
                        android.os.storage.StorageManager.UUID_DEFAULT,
                        packageName,
                        userHandle
                    )
                    
                    val totalBytes = stats.appBytes + stats.dataBytes + stats.cacheBytes
                    val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
                    val totalMb = totalBytes / (1024.0 * 1024.0)
                    
                    return if (totalGb >= 1.0) {
                        "%.2f GB".format(totalGb)
                    } else {
                        "%.0f MB".format(totalMb)
                    }
                } catch (e: Exception) {
                    android.util.Log.d("StateManager", "StorageStatsManager failed, falling back: ${e.message}")
                }
            }
            
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
}
