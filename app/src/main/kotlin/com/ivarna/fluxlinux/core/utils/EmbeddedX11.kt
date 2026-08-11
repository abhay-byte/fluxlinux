package com.ivarna.fluxlinux.core.utils

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Launcher for the Termux:X11 clone embedded directly in this app.
 * X11 renders in-process (same package) — no external Termux:X11 APK needed.
 */
object EmbeddedX11 {

    const val X11_VERSION = "1.03.01"
    private const val TAG = "EmbeddedX11"

    /** Open the X11 display activity (in-app). */
    fun launchDisplay(context: Context): Boolean {
        startServer(context)
        return launch(context, com.termux.x11.MainActivity::class.java)
    }

    /**
     * Start the X server in-process (the renderer native lib is already loaded
     * into this app). Equivalent of the standalone termux-x11 binary: it binds
     * the X socket and broadcasts ACTION_START which the X11 activity receives.
     */
    @Volatile
    private var serverThread: Thread? = null

    fun startServer(context: Context) {
        if (serverThread?.isAlive == true) return
        serverThread = Thread({
            try {
                android.os.Looper.prepare()
                com.termux.x11.CmdEntryPoint.main(arrayOf(":0", "-legacy-drawing"))
                android.os.Looper.loop()
            } catch (e: Exception) {
                Log.e(TAG, "Embedded X11 server exited", e)
            }
        }, "fluxlinux-x11-server").apply {
            isDaemon = false
            start()
        }
    }

    /** Open the X11 preferences activity (in-app). */
    fun launchPreferences(context: Context): Boolean =
        launch(context, com.termux.x11.LoriePreferences::class.java)

    /** Ask the in-app X11 session to stop. */
    fun stopDisplay(context: Context) {
        runCatching {
            context.sendBroadcast(Intent("com.termux.x11.ACTION_STOP"))
        }
    }

    private fun launch(context: Context, cls: Class<*>): Boolean = try {
        val intent = Intent(context, cls).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to launch ${cls.name}", e)
        false
    }
}
