package com.ivarna.fluxlinux.core.legacy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

object LegacyTermuxBridge {
    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_X11_PACKAGE = "com.termux.x11"
    const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
    const val TERMUX_HOME = "/data/data/com.termux/files/home"
    const val MIN_TERMUX_VERSION = "0.118.3"
    private const val TAG = "LegacyTermux"
    val ID_RE = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")

    fun isSafeProotId(id: String): Boolean = ID_RE.matches(id)

    fun isTermuxInstalled(ctx: Context): Boolean {
        return try {
            ctx.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Checks if external Termux:X11 package is installed. (Never uses embedded X11 state). */
    fun isTermuxX11Installed(ctx: Context): Boolean {
        return try {
            ctx.packageManager.getPackageInfo(TERMUX_X11_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun termuxVersionName(ctx: Context): String? {
        return try {
            ctx.packageManager.getPackageInfo(TERMUX_PACKAGE, 0).versionName
        } catch (_: Exception) {
            null
        }
    }

    fun isVersionOlderThan(current: String, minimum: String): Boolean {
        if (current == "Not Installed") return false
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        val m = minimum.split(".").mapNotNull { it.toIntOrNull() }
        if (c.isEmpty() || m.isEmpty()) return false
        for (i in 0 until maxOf(c.size, m.size)) {
            val cv = c.getOrElse(i) { 0 }
            val mv = m.getOrElse(i) { 0 }
            if (cv < mv) return true
            if (cv > mv) return false
        }
        return false
    }

    fun isTermuxVersionOk(ctx: Context): Boolean {
        val ver = termuxVersionName(ctx) ?: return false
        return !isVersionOlderThan(ver, MIN_TERMUX_VERSION)
    }

    fun hasRunCommandPermission(ctx: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            ctx,
            "com.termux.permission.RUN_COMMAND"
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Pure extras — unit-testable without constructing android.content.Intent. */
    data class RunCommandSpec(
        val packageName: String = TERMUX_PACKAGE,
        val className: String = RUN_COMMAND_SERVICE,
        val action: String = ACTION_RUN_COMMAND,
        val commandPath: String = TERMUX_BASH,
        val arguments: List<String>,
        val workdir: String = TERMUX_HOME,
        val background: Boolean,
    )

    fun wrapDeploy(b64: String, destName: String, args: List<String>): String? {
        if (!destName.matches(Regex("^flux_legacy_[A-Za-z0-9_]+\\.sh$"))) return null
        if (args.any { !isSafeProotId(it) }) return null
        val tail = if (args.isEmpty()) "" else " " + args.joinToString(" ")
        return """
            echo '$b64' | base64 -d > ${'$'}HOME/$destName
            chmod +x ${'$'}HOME/$destName
            bash ${'$'}HOME/$destName$tail
        """.trimIndent()
    }

    fun specFor(script: String, background: Boolean): RunCommandSpec {
        return RunCommandSpec(
            arguments = listOf("-c", script),
            background = background
        )
    }

    fun toIntent(spec: RunCommandSpec): Intent {
        return Intent(spec.action).apply {
            setClassName(spec.packageName, spec.className)
            putExtra(EXTRA_COMMAND_PATH, spec.commandPath)
            putExtra(EXTRA_ARGUMENTS, spec.arguments.toTypedArray())
            putExtra(EXTRA_WORKDIR, spec.workdir)
            putExtra(EXTRA_BACKGROUND, spec.background)
        }
    }

    fun hostPath(id: String, layout: String?): String? {
        if (!isSafeProotId(id)) return null
        return when (layout) {
            "installed-rootfs" -> "/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/$id"
            "containers" -> "/data/data/com.termux/files/usr/var/lib/proot-distro/containers/$id"
            else -> null
        }
    }

    fun buildListSpec(scriptB64: String): RunCommandSpec {
        val script = wrapDeploy(scriptB64, "flux_legacy_list_proot.sh", emptyList())
            ?: error("Invalid list script deploy args")
        return specFor(script, background = true)
    }

    fun buildPingSpec(): RunCommandSpec {
        val cmd = "am start -a android.intent.action.VIEW -d \"fluxlinux://callback?result=success&name=legacy_termux_ping\""
        return specFor(cmd, background = true)
    }

    fun buildLoginSpec(scriptB64: String, id: String): RunCommandSpec? {
        if (!isSafeProotId(id) || id == "termux") return null
        val script = wrapDeploy(scriptB64, "flux_legacy_login_proot.sh", listOf(id)) ?: return null
        return specFor(script, background = false)
    }

    fun buildStartDisplaySpec(scriptB64: String, id: String): RunCommandSpec? {
        if (!isSafeProotId(id) || id == "termux") return null
        val script = wrapDeploy(scriptB64, "flux_legacy_start_display.sh", listOf(id)) ?: return null
        return specFor(script, background = false)
    }

    fun buildStopDisplaySpec(scriptB64: String, id: String): RunCommandSpec? {
        if (!isSafeProotId(id) || id == "termux") return null
        val script = wrapDeploy(scriptB64, "flux_legacy_stop_display.sh", listOf(id)) ?: return null
        return specFor(script, background = false)
    }

    fun buildUninstallSpec(scriptB64: String, id: String): RunCommandSpec? {
        if (!isSafeProotId(id) || id == "termux") return null
        val script = wrapDeploy(scriptB64, "flux_legacy_uninstall_proot.sh", listOf(id)) ?: return null
        return specFor(script, background = false)
    }

    fun startSafely(ctx: Context, intent: Intent): Boolean {
        if (!isTermuxInstalled(ctx)) {
            Log.e(TAG, "startSafely: Termux not installed")
            return false
        }
        return try {
            val component = ctx.startService(intent)
            if (component == null) {
                Log.e(TAG, "startSafely: startService returned null (no RunCommandService)")
                false
            } else true
        } catch (e: SecurityException) {
            Log.e(TAG, "RUN_COMMAND denied", e)
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "startService not allowed", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "startService failed", e)
            false
        }
    }
}
