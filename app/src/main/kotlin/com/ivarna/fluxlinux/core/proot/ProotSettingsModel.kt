package com.ivarna.fluxlinux.core.proot

import android.content.Context
import android.util.Log
import com.ivarna.fluxlinux.core.chroot.GuestStorageCatalog
import com.ivarna.fluxlinux.core.data.DistroRepository
import com.ivarna.fluxlinux.core.terminal.TerminalLauncher

/**
 * Model orchestration for PRoot Settings (size measurement and caching).
 */
object ProotSettingsModel {

    private const val TAG = "ProotSettingsModel"

    data class SizeUi(
        val installed: Boolean,
        val dirExists: Boolean,
        val bytes: Long?,
        val dimmedCache: Boolean,
        val hint: String,
        val measuring: Boolean = false
    )

    private fun distroLabel(distroId: String): String {
        return DistroRepository.supportedDistros
            .firstOrNull { it.id == distroId }
            ?.name ?: "Linux"
    }

    fun loadCached(ctx: Context, distroId: String): SizeUi {
        if (ProotInfoStore.hasCache(ctx, distroId)) {
            val bytes = ProotInfoStore.cachedBytes(ctx, distroId)
            val last = ProotInfoStore.cachedLastMs(ctx, distroId)
            val installed = ProotInfoStore.cachedInstalled(ctx, distroId) || ProotInfoStore.cachedDir(ctx, distroId)
            return SizeUi(
                installed = installed,
                dirExists = ProotInfoStore.cachedDir(ctx, distroId),
                bytes = bytes,
                dimmedCache = bytes != null,
                hint = "Cached · ${ProotInfoStore.formatCacheAge(last)}"
            )
        }
        val isInstalled = TerminalLauncher.isDistroInstalledOnFs(ctx, distroId)
        val containerDir = GuestStorageCatalog.prootContainerDir(ctx, distroId)
        val dirExists = containerDir?.isDirectory == true
        return SizeUi(
            installed = isInstalled,
            dirExists = dirExists,
            bytes = null,
            dimmedCache = false,
            hint = "Tap refresh to measure"
        )
    }

    fun refreshSize(ctx: Context, distroId: String): SizeUi {
        val isInstalled = TerminalLauncher.isDistroInstalledOnFs(ctx, distroId)
        val measure = ProotSizeManager.measure(ctx, distroId)
        val liveBytes = measure.bytes
        val priorBytes = ProotInfoStore.cachedBytes(ctx, distroId)
        val priorMs = ProotInfoStore.cachedLastMs(ctx, distroId)

        val displayBytes: Long?
        val dimmedCache: Boolean
        val measureNote: String

        if (measure.error in listOf("uninstalling", "gone", "timeout", "measure_failed")) {
            // Transient error / active uninstall / race / timeout: preserve last-good cache
            if (priorBytes != null && priorBytes >= 0L) {
                displayBytes = priorBytes
                dimmedCache = true
                val why = when (measure.error) {
                    "uninstalling" -> "uninstalling"
                    "gone" -> "file gone"
                    "timeout" -> "timeout"
                    else -> "measure failed"
                }
                measureNote = "Size $why · showing cache · ${ProotInfoStore.formatCacheAge(priorMs)}"
                Log.w(TAG, "proot measure transient error, keeping prior cache: ${measure.error}")
            } else {
                displayBytes = null
                dimmedCache = false
                measureNote = when (measure.error) {
                    "uninstalling" -> "Uninstall in progress…"
                    "timeout" -> "Measurement timed out"
                    else -> "Measurement failed"
                }
            }
            // DO NOT overwrite SharedPreferences with -1/null on transient errors.
        } else if (!measure.dirExists || measure.error == "no_dir") {
            displayBytes = null
            dimmedCache = false
            measureNote = "No container on disk"
            ProotInfoStore.saveInstallInfo(
                ctx,
                distroId = distroId,
                installed = false,
                dirExists = false,
                bytes = null
            )
        } else {
            // Successful measurement
            displayBytes = liveBytes ?: 0L
            dimmedCache = false
            measureNote = "${distroLabel(distroId)} container · app storage"
            ProotInfoStore.saveInstallInfo(
                ctx,
                distroId = distroId,
                installed = isInstalled,
                dirExists = true,
                bytes = liveBytes
            )
        }

        return SizeUi(
            installed = isInstalled,
            dirExists = measure.dirExists,
            bytes = displayBytes,
            dimmedCache = dimmedCache,
            hint = measureNote
        )
    }

    fun refreshInstalledSizes(ctx: Context, ids: List<String>): List<Pair<String, SizeUi>> {
        val results = mutableListOf<Pair<String, SizeUi>>()
        for (id in ids) {
            val installed = TerminalLauncher.isDistroInstalledOnFs(ctx, id)
            if (installed) {
                val sizeUi = refreshSize(ctx, id)
                results.add(id to sizeUi)
            } else {
                val container = GuestStorageCatalog.prootContainerDir(ctx, id)
                ProotInfoStore.saveInstallInfo(
                    ctx,
                    distroId = id,
                    installed = false,
                    dirExists = container?.isDirectory == true,
                    bytes = null
                )
            }
        }
        return results
    }

    fun refreshUniversal(ctx: Context, ids: List<String>): SizeUi {
        val measured = refreshInstalledSizes(ctx, ids)
        val (totalBytes, note) = sumSizes(measured.map { it.second.bytes })
        val installedCount = measured.size

        ProotInfoStore.saveInstallInfo(
            ctx,
            distroId = GuestStorageCatalog.ALL_PROOT_ID,
            installed = installedCount > 0,
            dirExists = installedCount > 0,
            bytes = totalBytes
        )

        return SizeUi(
            installed = installedCount > 0,
            dirExists = installedCount > 0,
            bytes = totalBytes,
            dimmedCache = false,
            hint = note
        )
    }

    fun sumSizes(results: List<Long?>): Pair<Long?, String> {
        val ok = results.filterNotNull()
        val missing = results.size - ok.size
        val sum = if (ok.isEmpty()) null else ok.sum()
        val note = when {
            results.isEmpty() -> "No container on disk"
            missing == 0 -> "${ok.size} containers · inside app storage"
            ok.isEmpty() -> "Size probe failed"
            else -> "Partial · ${ok.size} of ${results.size} measured"
        }
        return sum to note
    }
}
