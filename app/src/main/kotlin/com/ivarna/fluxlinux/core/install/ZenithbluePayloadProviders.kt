package com.ivarna.fluxlinux.core.install

import android.content.Context
import android.util.Log
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class PlayInstallProgress(
    val phase: String,
    val bytesDownloaded: Long = 0,
    val totalBytesToDownload: Long = 0,
    val fraction: Float = 0f,
    val confirmationState: SplitInstallProgress.RequiresUserConfirmation? = null
)

object ZenithbluePayloadProviders {
    private const val TAG = "ZenithbluePayload"

    fun isZenithblue(ctx: Context): Boolean =
        (ctx.packageName == HostBootstrap.ZENITHBLUE_PACKAGE)

    /**
     * Determines whether [distroId] is supported by the current flavor / package.
     * On Ivarna (F-Droid/GitHub), every card is supported (filter skipped).
     * On Zenithblue (Play Store), only cards present in [PlayPayloadRegistry] are supported.
     */
    fun supports(ctx: Context, distroId: String): Boolean {
        return if (isZenithblue(ctx)) {
            PlayPayloadRegistry.contains(distroId)
        } else {
            true
        }
    }

    /**
     * Ensures the rootfs payload is present and verified in [TermuxHostPaths.homeDir].
     *
     * Flow:
     * 1. Check if already valid in destDir -> done.
     * 2. Request module via PlayFeatureDelivery.
     * 3. Open asset via SplitCompat application context (or package context fallback) using single path:
     *    `payloads/<moduleName>/<archiveFileName>`.
     * 4. Stream to app-private homeDir/<archiveFileName>.partial then atomic rename.
     * 5. Verify SHA256 and size via RootfsDownloader.isValid.
     *
     * Never calls RootfsDownloader.ensurePresent.
     * Never checks /sdcard or external storage.
     */
    suspend fun ensurePresent(
        ctx: Context,
        distroId: String,
        playFeatureDelivery: PlayFeatureDelivery = PlayFeatureDelivery.create(ctx),
        assetOpener: ((String) -> InputStream)? = null,
        onProgress: (PlayInstallProgress) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val payloadInfo = PlayPayloadRegistry.find(distroId)
            ?: error("Distro '$distroId' not in PlayPayloadRegistry")

        val homeDir = TermuxHostPaths.homeDir(ctx)
        val destFile = File(homeDir, payloadInfo.archiveFileName)
        val profile = DistroInstallProfile.forId(distroId)
            ?: DistroInstallProfile.require(distroId)

        // 1. Check if already valid
        if (RootfsDownloader.isValid(destFile, profile)) {
            Log.i(TAG, "Payload already present and valid at ${destFile.absolutePath}")
            onProgress(PlayInstallProgress(phase = "Distro verified", fraction = 1f))
            return@withContext true
        }

        // 2. Request Dynamic Feature Module
        onProgress(PlayInstallProgress(phase = "Downloading distro…", fraction = 0f))
        var downloadSuccess = false

        try {
            playFeatureDelivery.requestModule(payloadInfo.moduleName).collect { progress ->
                when (progress) {
                    is SplitInstallProgress.Downloading -> {
                        val frac = if (progress.totalBytesToDownload > 0) {
                            progress.bytesDownloaded.toFloat() / progress.totalBytesToDownload
                        } else 0f
                        onProgress(
                            PlayInstallProgress(
                                phase = "Downloading distro…",
                                bytesDownloaded = progress.bytesDownloaded,
                                totalBytesToDownload = progress.totalBytesToDownload,
                                fraction = frac
                            )
                        )
                    }
                    is SplitInstallProgress.Installing -> {
                        onProgress(PlayInstallProgress(phase = "Installing feature…", fraction = 1f))
                    }
                    is SplitInstallProgress.Installed -> {
                        downloadSuccess = true
                    }
                    is SplitInstallProgress.RequiresUserConfirmation -> {
                        onProgress(
                            PlayInstallProgress(
                                phase = "Confirmation required…",
                                fraction = 0f,
                                confirmationState = progress
                            )
                        )
                    }
                    is SplitInstallProgress.Failed -> {
                        Log.e(TAG, "Module install failed: ${progress.errorCode}", progress.exception)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during module request", e)
            return@withContext false
        }

        if (!downloadSuccess && !playFeatureDelivery.isInstalled(payloadInfo.moduleName)) {
            Log.e(TAG, "Module ${payloadInfo.moduleName} is not installed")
            return@withContext false
        }

        // 3. Open asset using binding single path: payloads/<moduleName>/<archiveFileName>
        val assetPath = "payloads/${payloadInfo.moduleName}/${payloadInfo.archiveFileName}"
        var inputStream: InputStream? = null

        if (assetOpener != null) {
            try {
                inputStream = assetOpener(assetPath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open asset via assetOpener: $assetPath", e)
                return@withContext false
            }
        } else {
            val appCtx = ctx.applicationContext
            try {
                inputStream = appCtx.assets.open(assetPath)
                Log.i(TAG, "Opened asset via appCtx.assets.open: $assetPath")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open asset via appCtx, attempting createPackageContext fallback: $assetPath", e)
                try {
                    val pkgCtx = ctx.createPackageContext(ctx.packageName, 0)
                    inputStream = pkgCtx.assets.open(assetPath)
                    Log.i(TAG, "Opened asset via pkgCtx.assets.open: $assetPath")
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Failed to open asset via pkgCtx fallback: $assetPath", fallbackEx)
                    return@withContext false
                }
            }
        }

        // 4. Stream to app-private target homeDir
        homeDir.mkdirs()
        val partialFile = File(homeDir, "${payloadInfo.archiveFileName}.partial")
        if (partialFile.exists()) {
            partialFile.delete()
        }

        onProgress(PlayInstallProgress(phase = "Extracting payload…", fraction = 0.5f))
        try {
            inputStream.use { input ->
                FileOutputStream(partialFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset to $partialFile", e)
            if (partialFile.exists()) partialFile.delete()
            return@withContext false
        }

        // Atomic rename
        if (destFile.exists()) {
            destFile.delete()
        }
        if (!partialFile.renameTo(destFile)) {
            Log.e(TAG, "Failed to rename $partialFile to $destFile")
            if (partialFile.exists()) partialFile.delete()
            return@withContext false
        }

        // 5. Verify SHA256 and size
        if (!RootfsDownloader.isValid(destFile, profile)) {
            Log.e(TAG, "Materialized file failed verification gate, rejecting: ${destFile.absolutePath}")
            destFile.delete()
            return@withContext false
        }

        Log.i(TAG, "Materialized and verified rootfs for ${payloadInfo.distroId} at ${destFile.absolutePath}")
        onProgress(PlayInstallProgress(phase = "Distro verified", fraction = 1f))
        true
    }
}
