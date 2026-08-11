package com.ivarna.fluxlinux.core.desktop

import android.content.Context
import java.io.File

/**
 * Host-only desktop start/stop log (termux-lib `gui_desktop.log` parity).
 * Ring-capped file under [Context.getCacheDir]; never enters guest/isolation runners.
 */
object GuiDesktopLog {

    private const val FILE_NAME = "gui_desktop.log"
    private const val MAX_BYTES = 512L * 1024L

    fun logFile(ctx: Context): File = File(ctx.cacheDir, FILE_NAME)

    fun clear(ctx: Context) {
        runCatching { logFile(ctx).writeText("") }
    }

    fun header(ctx: Context, action: String, script: String, method: String) {
        append(ctx, "")
        append(ctx, "=== $action method=$method script=$script ===")
    }

    fun append(ctx: Context, line: String) {
        try {
            val f = logFile(ctx)
            val text = "$line\n"
            if (f.exists() && f.length() + text.length > MAX_BYTES) {
                val tail = f.readText().takeLast((MAX_BYTES / 2).toInt())
                f.writeText(tail + text)
            } else {
                f.appendText(text)
            }
        } catch (_: Exception) {
        }
    }

    fun read(ctx: Context): String = try {
        val f = logFile(ctx)
        if (f.isFile) f.readText() else ""
    } catch (_: Exception) {
        ""
    }

    fun hasContent(ctx: Context): Boolean = try {
        val f = logFile(ctx)
        f.isFile && f.length() > 0L
    } catch (_: Exception) {
        false
    }
}
