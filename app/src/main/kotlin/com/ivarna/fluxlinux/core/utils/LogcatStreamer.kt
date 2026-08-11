package com.ivarna.fluxlinux.core.utils

import android.util.Log
import java.io.BufferedReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Streams `logcat` output for the app's own UID into a bounded buffer so
 * onboarding / setup screens can show live logs without shell access.
 * Apps can only read logcat lines for their own UID, which is exactly what
 * host-setup produces (TerminalLauncher, BootstrapInstaller, libbash …).
 */
object LogcatStreamer {

    private const val TAG = "LogcatStreamer"
    private const val MAX_LINES = 800
    private val interestingTags = arrayOf(
        "TerminalLauncher",
        "BootstrapInstaller",
        "TermuxHostPaths",
        "ShellCommandRunner",
        "libbash.so",
        "FluxLinux",
        "LogcatStreamer"
    )

    private val buffer = ArrayDeque<String>()
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    @Volatile
    private var job: Job? = null
    private var process: Process? = null

    /** Start tailing logcat (idempotent; replaces any running stream). */
    fun start(scope: CoroutineScope) {
        stop()
        job = scope.launch(Dispatchers.IO) {
            val proc = try {
                ProcessBuilder("logcat", "-v", "time")
                    .redirectErrorStream(true)
                    .start()
            } catch (e: Exception) {
                Log.w(TAG, "logcat start failed", e)
                return@launch
            }
            process = proc
            try {
                val reader: BufferedReader = proc.inputStream.bufferedReader(Charsets.UTF_8)
                while (coroutineContext.isActive) {
                    val line = reader.readLine() ?: break
                    if (interestingTags.any { line.contains(it) }) append(line)
                }
            } catch (e: Exception) {
                if (coroutineContext.isActive) Log.w(TAG, "logcat stream ended", e)
            } finally {
                runCatching { proc.destroy() }
            }
        }
        job?.invokeOnCompletion {
            runCatching { process?.destroy() }
            process = null
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
        notifyListeners("")
    }

    fun snapshot(): List<String> = synchronized(buffer) { buffer.toList() }

    /** @return unsubscribe lambda */
    fun subscribe(listener: (String) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    private fun append(line: String) {
        synchronized(buffer) {
            buffer.addLast(line)
            while (buffer.size > MAX_LINES) buffer.removeFirst()
        }
        notifyListeners(line)
    }

    private fun notifyListeners(line: String) {
        listeners.forEach { runCatching { it(line) } }
    }
}
