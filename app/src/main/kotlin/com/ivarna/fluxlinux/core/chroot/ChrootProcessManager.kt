package com.ivarna.fluxlinux.core.chroot

import android.content.Context
import android.util.Log
import com.ivarna.fluxlinux.core.root.ChrootPaths
import com.ivarna.fluxlinux.core.root.RootShell

/**
 * List / kill host processes whose `/proc/PID/root` is the Debian chroot path.
 *
 * SSOT shell: assets `scripts/chroot/chroot_processes.sh`.
 * Copied from termux-lib [com.zenithblue.nativecode.terminal.ChrootProcessManager].
 * Blocking — background executor only.
 */
object ChrootProcessManager {

    private const val TAG = "ChrootProcessManager"
    private const val ASSET = "scripts/chroot/chroot_processes.sh"
    private const val LIST_TIMEOUT_MS = 25_000L
    private const val REAP_TIMEOUT_MS = 55_000L

    data class Proc(val pid: Int, val comm: String, val cmdline: String)

    data class ListResult(
        val path: String,
        val processes: List<Proc>,
        val raw: String,
        val rootOk: Boolean,
        val error: String? = null
    )

    data class KillResult(
        val killed: Int,
        val failed: Int,
        val remaining: List<Proc>,
        val verifiedClean: Boolean,
        val raw: String,
        val rootOk: Boolean,
        val error: String? = null
    )

    fun list(
        context: Context,
        path: String = ChrootPaths.CHROOT_PATH
    ): ListResult {
        if (!RootShell.isRootAvailable()) {
            return ListResult(
                path = path,
                processes = emptyList(),
                raw = "",
                rootOk = false,
                error = "root_required"
            )
        }
        val staged = RootShell.stageAsset(context, ASSET)
        if (staged == null) {
            return ListResult(
                path = path,
                processes = emptyList(),
                raw = "",
                rootOk = true,
                error = "stage_failed"
            )
        }
        val raw = try {
            RootShell.capture("sh \"$staged\" list '$path'", timeoutMs = LIST_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "list failed: ${e.message}")
            return ListResult(
                path = path,
                processes = emptyList(),
                raw = "",
                rootOk = true,
                error = "timeout"
            )
        }
        Log.d(TAG, "list raw:\n${raw.take(800)}")
        val parsed = parseList(raw, path)
        return parsed.copy(rootOk = true, error = parsed.error)
    }

    fun killAll(
        context: Context,
        path: String = ChrootPaths.CHROOT_PATH
    ): KillResult {
        if (!RootShell.isRootAvailable()) {
            return KillResult(
                killed = 0,
                failed = 0,
                remaining = emptyList(),
                verifiedClean = false,
                raw = "",
                rootOk = false,
                error = "root_required"
            )
        }
        val staged = RootShell.stageAsset(context, ASSET)
        if (staged == null) {
            return KillResult(
                killed = 0,
                failed = 0,
                remaining = emptyList(),
                verifiedClean = false,
                raw = "",
                rootOk = true,
                error = "stage_failed"
            )
        }
        val raw = try {
            RootShell.capture("sh \"$staged\" reap '$path'", timeoutMs = REAP_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "reap failed: ${e.message}")
            return KillResult(
                killed = 0,
                failed = 0,
                remaining = emptyList(),
                verifiedClean = false,
                raw = "",
                rootOk = true,
                error = "timeout"
            )
        }
        Log.d(TAG, "reap raw:\n${raw.take(1200)}")
        return parseKill(raw, path, rootOk = true)
    }

    internal fun parseList(raw: String, defaultPath: String): ListResult {
        var path = defaultPath
        var error: String? = null
        val procs = ArrayList<Proc>()
        var footerCount: Int? = null

        for (line in raw.lineSequence()) {
            val t = line.trimEnd()
            when {
                t.startsWith("# path=") -> path = t.removePrefix("# path=").trim()
                t.startsWith("# error=") -> error = t.removePrefix("# error=").trim()
                t.startsWith("# count=") ->
                    footerCount = t.removePrefix("# count=").trim().toIntOrNull()
                t.startsWith("#") -> Unit
                t.isBlank() -> Unit
                else -> {
                    val parts = t.split('\t', limit = 3)
                    if (parts.isNotEmpty()) {
                        val pid = parts[0].trim().toIntOrNull()
                        if (pid != null && pid > 0) {
                            val comm = parts.getOrNull(1)?.trim().orEmpty().ifEmpty { "?" }
                            val cmd = parts.getOrNull(2)?.trim().orEmpty()
                            procs.add(Proc(pid, comm, cmd))
                        }
                    }
                }
            }
        }

        if (footerCount != null && footerCount != procs.size) {
            Log.w(TAG, "count footer=$footerCount but parsed=${procs.size}")
        }

        return ListResult(
            path = path,
            processes = procs.sortedBy { it.pid },
            raw = raw,
            rootOk = error != "root_required",
            error = error
        )
    }

    internal fun parseKill(raw: String, defaultPath: String, rootOk: Boolean): KillResult {
        var killed = 0
        var failed = 0
        var error: String? = null
        val remaining = ArrayList<Proc>()
        var sawKillSummary = false
        var sawListHeader = false
        var inListSection = false

        for (line in raw.lineSequence()) {
            val t = line.trimEnd()
            when {
                t.startsWith("# error=") -> error = t.removePrefix("# error=").trim()
                t.startsWith("# killed=") -> {
                    sawKillSummary = true
                    val body = t.removePrefix("# ")
                    val parts = body.split(Regex("\\s+"))
                    for (p in parts) {
                        when {
                            p.startsWith("killed=") ->
                                killed = p.removePrefix("killed=").toIntOrNull() ?: killed
                            p.startsWith("failed=") ->
                                failed = p.removePrefix("failed=").toIntOrNull() ?: failed
                        }
                    }
                    inListSection = false
                }
                t.startsWith("# chroot_processes v1") -> {
                    if (sawKillSummary) {
                        sawListHeader = true
                        inListSection = true
                    }
                }
                t.startsWith("# count=") -> Unit
                t.startsWith("#") -> Unit
                t.isBlank() -> Unit
                else -> {
                    if (sawListHeader || inListSection || !sawKillSummary) {
                        val parts = t.split('\t', limit = 3)
                        val pid = parts.getOrNull(0)?.trim()?.toIntOrNull()
                        if (pid != null && pid > 0) {
                            remaining.add(
                                Proc(
                                    pid = pid,
                                    comm = parts.getOrNull(1)?.trim().orEmpty().ifEmpty { "?" },
                                    cmdline = parts.getOrNull(2)?.trim().orEmpty()
                                )
                            )
                        }
                    }
                }
            }
        }

        if (!sawKillSummary) {
            val list = parseList(raw, defaultPath)
            return KillResult(
                killed = 0,
                failed = 0,
                remaining = list.processes,
                verifiedClean = list.processes.isEmpty() && list.error == null,
                raw = raw,
                rootOk = rootOk && list.rootOk,
                error = list.error ?: error
            )
        }

        val rem = remaining.sortedBy { it.pid }
        return KillResult(
            killed = killed,
            failed = failed,
            remaining = rem,
            verifiedClean = rem.isEmpty() && error == null,
            raw = raw,
            rootOk = rootOk,
            error = error
        )
    }
}
