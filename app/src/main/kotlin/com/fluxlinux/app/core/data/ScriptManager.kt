package com.fluxlinux.app.core.data

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class ScriptManager(private val context: Context) {

    /**
     * Reads a script file from the assets folder and returns it as a single String.
     * Useful for passing small scripts directly to 'bash -c'.
     */
    fun getScriptContent(fileName: String): String {
        return try {
            val inputStream = context.assets.open("scripts/$fileName")
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            "echo 'Error executing script: ${e.message}'"
        }
    }
}
