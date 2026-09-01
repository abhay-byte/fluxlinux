package com.ivarna.fluxlinux.core.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths
import java.io.File

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
    enum class State { STOPPED, STARTING, RUNNING, STOPPING }

    private val lifecycleLock = Any()
    @Volatile private var lifecycleState = State.STOPPED
    @Volatile private var serverThread: Thread? = null

    fun state(): State = lifecycleState

    /** Start the native server on its owning thread. */
    fun startServer(context: Context): Boolean {
        synchronized(lifecycleLock) {
            if (lifecycleState == State.RUNNING || lifecycleState == State.STARTING)
                return true
            if (lifecycleState == State.STOPPING)
                return false
            lifecycleState = State.STARTING
            val app = context.applicationContext
            val thread = Thread({
                try {
                    android.os.Looper.prepare()
                    com.termux.x11.CmdEntryPoint.ctx = app
                    com.termux.x11.CmdEntryPoint.setTmpDir(TermuxHostPaths.TMPDIR)
                    val xkbRoot = File(app.filesDir, "usr/share/X11/xkb")
                    if (xkbRoot.exists()) {
                        com.termux.x11.CmdEntryPoint.setXkbConfigRoot(xkbRoot.absolutePath)
                    }
                    val status = com.termux.x11.CmdEntryPoint.main(
                        arrayOf(":0", "-legacy-drawing")
                    ) {
                        synchronized(lifecycleLock) { lifecycleState = State.RUNNING }
                        Log.i(TAG, "Embedded X11 native server started")
                    }
                    Log.i(TAG, "Embedded X11 native server returned status=$status")
                } catch (e: Throwable) {
                    Log.e(TAG, "Embedded X11 server failed without killing the app", e)
                } finally {
                    synchronized(lifecycleLock) {
                        lifecycleState = State.STOPPED
                        serverThread = null
                    }
                }
            }, "fluxlinux-x11-server")
            thread.isDaemon = false
            serverThread = thread
            return try {
                thread.start()
                true
            } catch (e: Exception) {
                lifecycleState = State.STOPPED
                serverThread = null
                Log.e(TAG, "Could not create embedded X11 thread", e)
                false
            }
        }
    }

    /** Open the X11 preferences activity (in-app). */
    fun launchPreferences(context: Context): Boolean =
        launch(context, com.termux.x11.LoriePreferences::class.java)

    /** Close only the in-app display Activity; this does not stop the server. */
    fun stopDisplay(context: Context) {
        runCatching {
            context.sendBroadcast(Intent("com.termux.x11.ACTION_STOP").setPackage(context.packageName))
        }
    }

    /** Request the bundled Xorg loop to stop and join its owning thread. */
    fun stopServer(context: Context, timeoutMs: Long = 10_000L): Boolean {
        val thread = synchronized(lifecycleLock) {
            val current = serverThread
            if (current == null || !current.isAlive) {
                lifecycleState = State.STOPPED
                return true
            }
            lifecycleState = State.STOPPING
            current
        }
        runCatching { com.termux.x11.CmdEntryPoint.stop() }
            .onFailure { Log.e(TAG, "Could not request embedded X11 stop", it) }
        if (thread !== Thread.currentThread()) {
            runCatching { thread.join(timeoutMs) }
        }
        val stopped = !thread.isAlive
        if (stopped) {
            synchronized(lifecycleLock) { lifecycleState = State.STOPPED }
        } else {
            Log.w(TAG, "Embedded X11 did not stop within ${timeoutMs}ms")
        }
        return stopped
    }

    /** Stop a previous server, then start a fresh native Xorg instance. */
    fun restartServer(context: Context): Boolean {
        if (!stopServer(context)) return false
        return startServer(context)
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
