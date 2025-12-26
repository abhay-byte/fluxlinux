package com.ivarna.fluxlinux.core.utils

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootUtils {

    /**
     * Checks if the device is rooted and if we can obtain root access.
     */
    fun isRootAvailable(): Boolean {
        return runRootCommand("id").isSuccess
    }

    /**
     * Runs a command as root (su).
     * Returns a Result object containing exit code and output.
     */
    fun runRootCommand(command: String): ShellResult {
        var process: Process? = null
        var os: DataOutputStream? = null
        
        try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            
            os.writeBytes(command + "\n")
            os.writeBytes("exit\n")
            os.flush()
            
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            
            return ShellResult(
                isSuccess = exitCode == 0,
                output = output.trim(),
                error = error.trim()
            )
            
        } catch (e: Exception) {
            return ShellResult(false, "", e.message ?: "Unknown error")
        } finally {
            try { os?.close() } catch (e: Exception) {}
            try { process?.destroy() } catch (e: Exception) {}
        }
    }

    data class ShellResult(
        val isSuccess: Boolean,
        val output: String,
        val error: String
    )
}
