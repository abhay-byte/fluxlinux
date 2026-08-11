package com.ivarna.fluxlinux.core.chroot

import android.content.Context
import android.util.Log
import com.ivarna.fluxlinux.core.root.RootShell

/**
 * Background orchestration for Chroot Settings page (size + processes).
 * Decoupled from Compose — UI observes [Snapshot] results.
 */
object ChrootSettingsModel {

    private const val TAG = "ChrootSettingsModel"

    data class SizeUi(
        val rootOk: Boolean,
        val installed: Boolean,
        val markerOk: Boolean,
        val dirExists: Boolean,
        val bytes: Long?,
        val dimmedCache: Boolean,
        val hint: String,
        val measuring: Boolean = false
    )

    data class ProcUi(
        val rootOk: Boolean,
        val count: Int,
        val processes: List<ChrootProcessManager.Proc>,
        val hint: String,
        val statusLine: String? = null,
        val scanning: Boolean = false,
        val killing: Boolean = false,
        val error: String? = null
    )

    data class PageSnapshot(
        val size: SizeUi,
        val proc: ProcUi
    )

    fun loadCached(ctx: Context): PageSnapshot {
        val size = if (ChrootInfoStore.hasCache(ctx) && ChrootInfoStore.cachedRootOk(ctx)) {
            val bytes = ChrootInfoStore.cachedBytes(ctx)
            val last = ChrootInfoStore.cachedLastMs(ctx)
            val via = ChrootInfoStore.cachedViaRoot(ctx)
            val installed = ChrootInfoStore.cachedInstalled(ctx) || ChrootInfoStore.cachedDir(ctx)
            SizeUi(
                rootOk = true,
                installed = installed,
                markerOk = ChrootInfoStore.cachedInstalled(ctx),
                dirExists = ChrootInfoStore.cachedDir(ctx),
                bytes = bytes,
                dimmedCache = bytes != null,
                hint = "Cached · ${if (via) "root du" else "root"} · ${ChrootInfoStore.formatCacheAge(last)}"
            )
        } else if (ChrootInfoStore.hasCache(ctx) && !ChrootInfoStore.cachedRootOk(ctx)) {
            SizeUi(
                rootOk = false,
                installed = false,
                markerOk = false,
                dirExists = false,
                bytes = null,
                dimmedCache = false,
                hint = "Root required · grant su then refresh"
            )
        } else {
            val snap = ChrootDetection.probe(forceRoot = false)
            SizeUi(
                rootOk = false,
                installed = snap.installed,
                markerOk = snap.markerOk,
                dirExists = snap.dirExists,
                bytes = null,
                dimmedCache = false,
                hint = "Tap refresh to measure"
            )
        }

        val proc = if (ChrootInfoStore.hasProcCache(ctx)) {
            val n = ChrootInfoStore.cachedProcCount(ctx).coerceAtLeast(0)
            val age = ChrootInfoStore.formatCacheAge(ChrootInfoStore.cachedProcLastMs(ctx))
            ProcUi(
                rootOk = true,
                count = n,
                processes = emptyList(),
                hint = "Cached · $age"
            )
        } else {
            ProcUi(
                rootOk = true,
                count = -1,
                processes = emptyList(),
                hint = "Tap scan for chroot processes"
            )
        }
        return PageSnapshot(size, proc)
    }

    /** BG: root + size measure. Sequential with [refreshProcesses]. */
    fun refreshSize(ctx: Context, forceClearSu: Boolean = false): SizeUi {
        if (forceClearSu) RootShell.clearSuCache()
        val rootOk = RootShell.isRootAvailable()
        if (!rootOk) {
            ChrootInfoStore.saveInstallInfo(
                ctx, installed = false, dirExists = false,
                bytes = null, rootOk = false, viaRoot = false
            )
            ChrootDetection.invalidate()
            return SizeUi(
                rootOk = false,
                installed = false,
                markerOk = false,
                dirExists = false,
                bytes = null,
                dimmedCache = false,
                hint = "Root required · install via onboarding after granting su"
            )
        }

        val detect = ChrootDetection.probe(forceRoot = true)
        val measure = ChrootSizeManager.measure(ctx)
        var dirExists = measure.dirExists
        when {
            measure.error == "no_dir" -> dirExists = false
            measure.bytes != null || measure.error == null -> dirExists = true
            else -> dirExists = detect.dirExists || detect.markerOk || dirExists
        }

        val liveBytes = measure.bytes
        val priorBytes = ChrootInfoStore.cachedBytes(ctx)
        val priorMs = ChrootInfoStore.cachedLastMs(ctx)

        val displayBytes: Long?
        val dimmedCache: Boolean
        val measureNote: String
        when {
            liveBytes != null -> {
                displayBytes = liveBytes
                dimmedCache = false
                measureNote = "Debian rootfs · binds excluded (sdcard/mnt/dev)"
            }
            !dirExists -> {
                displayBytes = null
                dimmedCache = false
                measureNote = "No chroot rootfs on host"
            }
            priorBytes != null -> {
                displayBytes = priorBytes
                dimmedCache = true
                val why = when (measure.error) {
                    "timeout" -> "timeout"
                    "empty_output" -> "empty"
                    else -> "probe failed"
                }
                measureNote = "Size $why · showing cache · ${ChrootInfoStore.formatCacheAge(priorMs)}"
                Log.w(TAG, "probe fail keep cache err=${measure.error}")
            }
            else -> {
                displayBytes = null
                dimmedCache = false
                measureNote = "Root OK · size probe failed"
            }
        }

        val installed = detect.markerOk || dirExists
        ChrootInfoStore.saveInstallInfo(
            ctx,
            installed = installed,
            dirExists = dirExists,
            bytes = liveBytes,
            rootOk = true,
            viaRoot = measure.viaRoot
        )

        return SizeUi(
            rootOk = true,
            installed = installed,
            markerOk = detect.markerOk,
            dirExists = dirExists,
            bytes = displayBytes,
            dimmedCache = dimmedCache,
            hint = measureNote
        )
    }

    fun refreshProcesses(ctx: Context, forceClearSu: Boolean = false): ProcUi {
        if (forceClearSu) RootShell.clearSuCache()
        val result = ChrootProcessManager.list(ctx)
        if (!result.rootOk || result.error == "root_required") {
            return ProcUi(
                rootOk = false,
                count = 0,
                processes = emptyList(),
                hint = "Root required",
                error = result.error
            )
        }
        if (result.error == "stage_failed") {
            return ProcUi(
                rootOk = true,
                count = 0,
                processes = emptyList(),
                hint = "Failed to stage helper script",
                error = result.error
            )
        }
        if (result.error == "timeout") {
            return ProcUi(
                rootOk = true,
                count = -1,
                processes = emptyList(),
                hint = "Scan timed out — retry",
                error = result.error
            )
        }
        val n = result.processes.size
        ChrootInfoStore.saveProcCount(ctx, n)
        return ProcUi(
            rootOk = true,
            count = n,
            processes = result.processes,
            hint = when {
                n == 0 -> "No chroot processes"
                n == 1 -> "1 process uses chroot root"
                else -> "$n processes use chroot root"
            }
        )
    }

    fun killAllProcesses(ctx: Context): ProcUi {
        val result = ChrootProcessManager.killAll(ctx)
        if (!result.rootOk || result.error == "root_required") {
            return ProcUi(
                rootOk = false,
                count = 0,
                processes = emptyList(),
                hint = "Root required",
                error = result.error
            )
        }
        if (result.error == "stage_failed") {
            return ProcUi(
                rootOk = true,
                count = 0,
                processes = emptyList(),
                hint = "Failed to stage helper script",
                error = result.error
            )
        }
        if (result.error == "timeout") {
            return ProcUi(
                rootOk = true,
                count = -1,
                processes = emptyList(),
                hint = "Kill timed out — retry",
                error = result.error,
                statusLine = "last: timeout"
            )
        }
        val rem = result.remaining.size
        ChrootInfoStore.saveProcCount(ctx, rem)
        val status = if (result.verifiedClean) {
            "last: killed ${result.killed} · verified 0 remaining"
        } else {
            "last: killed ${result.killed} · $rem still alive — retry"
        }
        return ProcUi(
            rootOk = true,
            count = rem,
            processes = result.remaining,
            hint = if (result.verifiedClean) {
                "No chroot processes"
            } else {
                "$rem process(es) still use chroot root"
            },
            statusLine = status
        )
    }

    /** Full page refresh: size then processes (no concurrent su). */
    fun refreshPage(ctx: Context, force: Boolean = true): PageSnapshot {
        val size = refreshSize(ctx, forceClearSu = force)
        val proc = if (size.rootOk) {
            refreshProcesses(ctx, forceClearSu = false)
        } else {
            ProcUi(
                rootOk = false,
                count = 0,
                processes = emptyList(),
                hint = "Root required"
            )
        }
        return PageSnapshot(size, proc)
    }
}
