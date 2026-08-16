package com.ivarna.fluxlinux.core.chroot

import android.content.Context
import android.util.Log
import com.ivarna.fluxlinux.core.data.DistroRepository
import com.ivarna.fluxlinux.core.root.ChrootPaths
import com.ivarna.fluxlinux.core.root.RootShell

/**
 * Background orchestration for Chroot Settings pages (size + processes).
 * Decoupled from Compose — UI observes [PageSnapshot] / [SizeUi] / [ProcUi] results.
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

    private fun distroLabel(distroId: String): String {
        return DistroRepository.supportedDistros
            .firstOrNull { it.id == distroId }
            ?.name ?: "Linux"
    }

    fun loadCached(
        ctx: Context,
        distroId: String = ChrootInfoStore.DEFAULT_DEBIAN_ID,
        path: String = ChrootPaths.CHROOT_PATH
    ): PageSnapshot {
        val size = if (ChrootInfoStore.hasCache(ctx, distroId) && ChrootInfoStore.cachedRootOk(ctx, distroId)) {
            val bytes = ChrootInfoStore.cachedBytes(ctx, distroId)
            val last = ChrootInfoStore.cachedLastMs(ctx, distroId)
            val via = ChrootInfoStore.cachedViaRoot(ctx, distroId)
            val installed = ChrootInfoStore.cachedInstalled(ctx, distroId)
            SizeUi(
                rootOk = true,
                installed = installed,
                markerOk = installed,
                dirExists = ChrootInfoStore.cachedDir(ctx, distroId),
                bytes = bytes,
                dimmedCache = bytes != null,
                hint = "Cached · ${if (via) "root du" else "root"} · ${ChrootInfoStore.formatCacheAge(last)}"
            )
        } else if (ChrootInfoStore.hasCache(ctx, distroId) && !ChrootInfoStore.cachedRootOk(ctx, distroId)) {
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
            val snap = ChrootDetection.probe(forceRoot = false, path = path)
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

        val proc = if (ChrootInfoStore.hasProcCache(ctx, distroId)) {
            val n = ChrootInfoStore.cachedProcCount(ctx, distroId).coerceAtLeast(0)
            val age = ChrootInfoStore.formatCacheAge(ChrootInfoStore.cachedProcLastMs(ctx, distroId))
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
    fun refreshSize(
        ctx: Context,
        distroId: String = ChrootInfoStore.DEFAULT_DEBIAN_ID,
        path: String = ChrootPaths.CHROOT_PATH,
        forceClearSu: Boolean = false
    ): SizeUi {
        if (forceClearSu) RootShell.clearSuCache()
        val rootOk = RootShell.isRootAvailable()
        if (!rootOk) {
            ChrootInfoStore.saveInstallInfo(
                ctx,
                distroId = distroId,
                installed = false,
                dirExists = false,
                bytes = null,
                rootOk = false,
                viaRoot = false
            )
            ChrootDetection.invalidate(path)
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

        val detect = ChrootDetection.probe(forceRoot = true, path = path)
        val measure = ChrootSizeManager.measure(ctx, path = path)
        var dirExists = measure.dirExists
        when {
            measure.error == "no_dir" -> dirExists = false
            measure.bytes != null || measure.error == null -> dirExists = true
            else -> dirExists = detect.dirExists || detect.markerOk || dirExists
        }

        val liveBytes = measure.bytes
        val priorBytes = ChrootInfoStore.cachedBytes(ctx, distroId)
        val priorMs = ChrootInfoStore.cachedLastMs(ctx, distroId)

        val displayBytes: Long?
        val dimmedCache: Boolean
        val measureNote: String
        when {
            liveBytes != null -> {
                displayBytes = liveBytes
                dimmedCache = false
                measureNote = if (distroId == "debian13_chroot") {
                    "Debian rootfs · binds excluded (sdcard/mnt/dev)"
                } else {
                    "${distroLabel(distroId)} rootfs · binds excluded (sdcard/mnt/dev)"
                }
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

        val installed = detect.installed
        ChrootInfoStore.saveInstallInfo(
            ctx,
            distroId = distroId,
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

    fun refreshProcesses(
        ctx: Context,
        distroId: String = ChrootInfoStore.DEFAULT_DEBIAN_ID,
        path: String = ChrootPaths.CHROOT_PATH,
        forceClearSu: Boolean = false
    ): ProcUi {
        if (forceClearSu) RootShell.clearSuCache()
        val result = ChrootProcessManager.list(ctx, path = path)
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
        ChrootInfoStore.saveProcCount(ctx, distroId, n)
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

    fun killAllProcesses(
        ctx: Context,
        path: String = ChrootPaths.CHROOT_PATH
    ): ProcUi {
        if (!GuestStorageCatalog.allowedKillPath(path)) {
            return ProcUi(
                rootOk = true,
                count = 0,
                processes = emptyList(),
                hint = "Invalid or refused path",
                error = "refused_path"
            )
        }
        val result = ChrootProcessManager.killAll(ctx, path = path)
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
        val distroId = GuestStorageCatalog.installableChroots().firstOrNull {
            GuestStorageCatalog.chrootPathOrNull(it.id) == path
        }?.id ?: ChrootInfoStore.DEFAULT_DEBIAN_ID
        ChrootInfoStore.saveProcCount(ctx, distroId, rem)
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

    /**
     * Pure status mapping according to authoritative plan:
     * - INSTALLED when installed
     * - PRESENT when dirExists && !installed (leftover directory)
     * - NOT INSTALLED otherwise
     */
    fun resolveStatus(installed: Boolean, dirExists: Boolean): String = when {
        installed -> "INSTALLED"
        dirExists -> "PRESENT"
        else -> "NOT INSTALLED"
    }

    /**
     * Kill button enablement invariant:
     * Enabled when root is available and no operation is busy.
     * Process count is NOT the enablement gate.
     */
    fun isKillEnabled(rootOk: Boolean, busy: Boolean): Boolean = rootOk && !busy

    /**
     * Testable multi-path runner that executes [killSingle] for each path sequentially,
     * checking [isCancelled] before and after each invocation to stop scheduling remaining paths.
     */
    fun runMultiPathKill(
        validPaths: List<String>,
        isCancelled: () -> Boolean = { false },
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        killSingle: (path: String) -> ChrootProcessManager.KillResult
    ): Pair<List<ChrootProcessManager.KillResult>, Boolean> {
        val killResults = mutableListOf<ChrootProcessManager.KillResult>()
        var wasCancelled = false
        for ((index, path) in validPaths.withIndex()) {
            if (isCancelled()) {
                wasCancelled = true
                break
            }
            onProgress?.invoke(index + 1, validPaths.size)
            val res = killSingle(path)
            killResults.add(res)
            if (isCancelled()) {
                wasCancelled = true
                break
            }
        }
        return killResults to wasCancelled
    }

    /**
     * Universal kill over multiple paths.
     * Iterates sequentially over [paths], filtering with [GuestStorageCatalog.allowedKillPath].
     * Checks [isCancelled] before and between paths to abort scheduling remaining paths immediately.
     */
    fun killAllProcesses(
        ctx: Context,
        paths: List<String>,
        isCancelled: () -> Boolean = { false },
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): ProcUi {
        val validPaths = paths.filter(GuestStorageCatalog::allowedKillPath)
        if (validPaths.isEmpty()) {
            return ProcUi(
                rootOk = true,
                count = 0,
                processes = emptyList(),
                hint = "No valid paths to kill",
                error = "no_paths"
            )
        }
        if (!RootShell.isRootAvailable()) {
            return ProcUi(
                rootOk = false,
                count = 0,
                processes = emptyList(),
                hint = "Root required",
                error = "root_required"
            )
        }

        var totalKilled = 0
        var totalFailed = 0
        val timeouts = mutableListOf<String>()

        val (killResults, wasCancelled) = runMultiPathKill(
            validPaths = validPaths,
            isCancelled = isCancelled,
            onProgress = onProgress,
            killSingle = { path ->
                val res = ChrootProcessManager.killAll(ctx, path = path)
                totalKilled += res.killed
                totalFailed += res.failed
                if (res.error == "timeout") {
                    timeouts.add(path)
                }
                val distroId = GuestStorageCatalog.installableChroots().firstOrNull {
                    GuestStorageCatalog.chrootPathOrNull(it.id) == path
                }?.id
                if (distroId != null) {
                    ChrootInfoStore.saveProcCount(ctx, distroId, res.remaining.size)
                }
                res
            }
        )

        val remainingList = ChrootProcessManager.mergeRemaining(killResults)
        val rem = remainingList.size
        ChrootInfoStore.saveProcCount(ctx, GuestStorageCatalog.ALL_CHROOT_ID, rem)
        val processedCount = killResults.size
        val verifiedClean = !wasCancelled && rem == 0 && timeouts.isEmpty()
        val status = when {
            wasCancelled -> "last: cancelled after $processedCount/${validPaths.size} roots · killed $totalKilled · $rem remaining"
            verifiedClean -> "last: killed $totalKilled · verified 0 remaining"
            timeouts.isNotEmpty() -> "last: killed $totalKilled · $rem remaining (timeout on ${timeouts.size} path(s))"
            else -> "last: killed $totalKilled · $rem still alive — retry"
        }

        return ProcUi(
            rootOk = true,
            count = rem,
            processes = remainingList,
            hint = when {
                wasCancelled -> "Kill cancelled · $rem process(es) remaining"
                verifiedClean -> "No chroot processes across roots"
                else -> "$rem process(es) still use chroot roots"
            },
            statusLine = status
        )
    }

    /**
     * Universal process scan over multiple paths.
     * Iterates sequentially over [paths], filtering with [GuestStorageCatalog.allowedKillPath].
     */
    fun scanAllProcesses(
        ctx: Context,
        paths: List<String>,
        isCancelled: () -> Boolean = { false },
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): ProcUi {
        val validPaths = paths.filter(GuestStorageCatalog::allowedKillPath)
        if (validPaths.isEmpty()) {
            return ProcUi(
                rootOk = true,
                count = 0,
                processes = emptyList(),
                hint = "No valid paths to scan"
            )
        }
        if (!RootShell.isRootAvailable()) {
            return ProcUi(
                rootOk = false,
                count = 0,
                processes = emptyList(),
                hint = "Root required",
                error = "root_required"
            )
        }

        val listResults = mutableListOf<List<ChrootProcessManager.Proc>>()
        var wasCancelled = false

        for ((index, path) in validPaths.withIndex()) {
            if (isCancelled()) {
                wasCancelled = true
                break
            }
            onProgress?.invoke(index + 1, validPaths.size)
            val res = ChrootProcessManager.list(ctx, path = path)
            listResults.add(res.processes)
            val distroId = GuestStorageCatalog.installableChroots().firstOrNull {
                GuestStorageCatalog.chrootPathOrNull(it.id) == path
            }?.id
            if (distroId != null) {
                ChrootInfoStore.saveProcCount(ctx, distroId, res.processes.size)
            }
            if (isCancelled()) {
                wasCancelled = true
                break
            }
        }

        val mergedList = ChrootProcessManager.mergeProcs(listResults)
        ChrootInfoStore.saveProcCount(ctx, GuestStorageCatalog.ALL_CHROOT_ID, mergedList.size)
        return ProcUi(
            rootOk = true,
            count = mergedList.size,
            processes = mergedList,
            hint = when {
                wasCancelled -> "Scan cancelled · ${mergedList.size} process(es) found"
                mergedList.isEmpty() -> "No chroot processes across roots"
                else -> "${mergedList.size} process(es) use chroot roots"
            }
        )
    }

    /** Full page refresh: size then processes (no concurrent su). */
    fun refreshPage(
        ctx: Context,
        distroId: String = ChrootInfoStore.DEFAULT_DEBIAN_ID,
        path: String = ChrootPaths.CHROOT_PATH,
        force: Boolean = true
    ): PageSnapshot {
        val size = refreshSize(ctx, distroId = distroId, path = path, forceClearSu = force)
        val proc = if (size.rootOk) {
            refreshProcesses(ctx, distroId = distroId, path = path, forceClearSu = false)
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

    /**
     * List IO Job: sequentially probe installable chroots and refresh sizes for installed ones.
     */
    fun refreshInstalledSizes(
        ctx: Context,
        ids: List<String>,
        forceClearSuOnce: Boolean = true
    ): List<Pair<String, SizeUi>> {
        if (forceClearSuOnce) RootShell.clearSuCache()
        val results = mutableListOf<Pair<String, SizeUi>>()
        for (id in ids) {
            val path = GuestStorageCatalog.chrootPathOrNull(id) ?: continue
            val snap = ChrootDetection.probe(forceRoot = true, path = path)
            if (snap.installed) {
                val sizeUi = refreshSize(ctx, distroId = id, path = path, forceClearSu = false)
                results.add(id to sizeUi)
            } else {
                ChrootInfoStore.saveInstallInfo(
                    ctx,
                    distroId = id,
                    installed = false,
                    dirExists = snap.dirExists,
                    bytes = null,
                    rootOk = snap.rootOk,
                    viaRoot = snap.viaRoot
                )
            }
        }
        return results
    }

    /**
     * Universal detail refresh: measures all installed roots and sums sizes.
     */
    fun refreshUniversal(ctx: Context, ids: List<String>): PageSnapshot {
        val measured = refreshInstalledSizes(ctx, ids, forceClearSuOnce = true)
        val (totalBytes, note) = sumSizes(measured.map { it.second.bytes })
        val installedCount = measured.size
        val rootOk = RootShell.isRootAvailable()

        ChrootInfoStore.saveInstallInfo(
            ctx,
            distroId = GuestStorageCatalog.ALL_CHROOT_ID,
            installed = installedCount > 0,
            dirExists = installedCount > 0,
            bytes = totalBytes,
            rootOk = rootOk,
            viaRoot = true
        )

        val sizeUi = SizeUi(
            rootOk = rootOk,
            installed = installedCount > 0,
            markerOk = installedCount > 0,
            dirExists = installedCount > 0,
            bytes = totalBytes,
            dimmedCache = false,
            hint = note
        )

        val cachedProc = ChrootInfoStore.cachedProcCount(ctx, GuestStorageCatalog.ALL_CHROOT_ID)
        val procUi = ProcUi(
            rootOk = rootOk,
            count = cachedProc,
            processes = emptyList(),
            hint = if (cachedProc >= 0) {
                "Cached · $cachedProc running across roots"
            } else {
                "Tap scan for all chroot processes"
            }
        )

        return PageSnapshot(sizeUi, procUi)
    }

    fun sumSizes(results: List<Long?>): Pair<Long?, String> {
        val ok = results.filterNotNull()
        val missing = results.size - ok.size
        val sum = if (ok.isEmpty()) null else ok.sum()
        val note = when {
            results.isEmpty() -> "No chroot rootfs on host"
            missing == 0 -> "${ok.size} roots · binds excluded"
            ok.isEmpty() -> "Size probe failed"
            else -> "Partial · ${ok.size} of ${results.size} measured"
        }
        return sum to note
    }

    fun confirmKillCopy(paths: List<String>): String {
        val pathList = paths.joinToString("\n") { "• $it" }
        return "Sends SIGKILL to every process whose root is one of:\n\n" +
            "$pathList\n\n" +
            "Open chroot shells and guest daemons will die. Host Android processes " +
            "are not targeted.\n\nRootfs and mounts stay."
    }
}
