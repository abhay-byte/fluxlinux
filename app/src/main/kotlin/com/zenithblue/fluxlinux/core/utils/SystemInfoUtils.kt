package com.zenithblue.fluxlinux.core.utils

import android.app.ActivityManager
import android.content.Context
import java.io.File

object SystemInfoUtils {
    
    data class MemoryInfo(
        val totalRamGB: Float,
        val totalSwapGB: Float
    )
    
    /**
     * Get system RAM and SWAP information
     * @return MemoryInfo containing RAM and SWAP in GB
     */
    fun getMemoryInfo(context: Context): MemoryInfo {
        val ramGB = getTotalRamGB(context)
        val swapGB = getTotalSwapGB()
        return MemoryInfo(ramGB, swapGB)
    }
    
    /**
     * Get total system RAM in GB
     */
    private fun getTotalRamGB(context: Context): Float {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            
            // Convert bytes to GB
            val totalRamBytes = memInfo.totalMem
            (totalRamBytes / (1024f * 1024f * 1024f))
        } catch (e: Exception) {
            android.util.Log.e("SystemInfoUtils", "Error getting RAM info", e)
            0f
        }
    }
    
    /**
     * Get total SWAP space in GB by parsing /proc/meminfo
     */
    private fun getTotalSwapGB(): Float {
        return try {
            val memInfoFile = File("/proc/meminfo")
            if (!memInfoFile.exists()) {
                return 0f
            }
            
            var swapTotalKB = 0L
            memInfoFile.forEachLine { line ->
                if (line.startsWith("SwapTotal:")) {
                    // Format: "SwapTotal:       8388604 kB"
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        swapTotalKB = parts[1].toLongOrNull() ?: 0L
                    }
                    return@forEachLine
                }
            }
            
            // Convert KB to GB
            (swapTotalKB / (1024f * 1024f))
        } catch (e: Exception) {
            android.util.Log.e("SystemInfoUtils", "Error getting SWAP info", e)
            0f
        }
    }
    
    /**
     * Check if RAM meets minimum requirement
     */
    fun isRamSufficient(ramGB: Float, minimumGB: Float = 8f): Boolean {
        return ramGB >= minimumGB
    }
    
    /**
     * Check if SWAP meets minimum requirement
     */
    fun isSwapSufficient(swapGB: Float, minimumGB: Float = 8f): Boolean {
        return swapGB >= minimumGB
    }
}
