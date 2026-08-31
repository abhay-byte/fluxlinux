package com.ivarna.fluxlinux.core.install

import android.content.Context
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths
import java.io.File

/** Non-Play rootfs provider retaining the existing pinned release behavior. */
object IvarnaRootfsPayloadProvider : RootfsPayloadProvider {
    override val id: String = "ivarna-remote-release"

    override fun ensurePresent(
        destDir: File,
        profile: DistroInstallProfile,
        isCancelled: () -> Boolean,
        onProgress: (PayloadProgress) -> Unit
    ): PayloadAcquireResult {
        val existed = RootfsDownloader.isDeployed(destDir, profile)
        val ok = RootfsDownloader.ensurePresent(
            destDir,
            profile,
            RootfsDownloader.defaultClient,
            isCancelled = isCancelled,
            onProgress = { progress ->
                onProgress(
                    PayloadProgress(
                        completedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes,
                        phase = "Downloaded ${progress.downloadedBytes / 1_048_576} / " +
                            "${progress.totalBytes.coerceAtLeast(0) / 1_048_576} MiB"
                    )
                )
            }
        )
        if (!ok) {
            return PayloadAcquireResult.Unavailable(
                message = "Rootfs unavailable — place ${profile.rootfsFileName} in the app " +
                    "home directory or retry online",
                cancelled = isCancelled()
            )
        }
        return PayloadAcquireResult.Available(
            VerifiedPayload(
                file = File(destDir, profile.rootfsFileName),
                source = if (existed) PayloadSource.LOCAL_VERIFIED else PayloadSource.REMOTE_RELEASE
            )
        )
    }
}

/** Non-Play host provider: packaged input when present, then the pinned release. */
object IvarnaHostRuntimePayloadProvider : HostRuntimePayloadProvider {
    private const val ASSET_NAME = "bootstrap.tar"
    private const val RELEASE_BASE =
        "https://github.com/abhay-byte/fluxlinux/releases/download/rootfs"

    override val id: String = "ivarna-remote-release"
    override val expectedFileName: String = HostBootstrap.IVARNA.fileName

    override fun open(
        ctx: Context,
        onProgress: (PayloadProgress) -> Unit
    ): HostRuntimePayload? {
        try {
            val total = try {
                ctx.assets.openFd(ASSET_NAME).use { it.length }
            } catch (_: Exception) {
                0L
            }
            return HostRuntimePayload(
                stream = ctx.assets.open(ASSET_NAME),
                totalBytes = total,
                source = PayloadSource.PACKAGED_ASSET
            )
        } catch (_: Exception) {
            // Ivarna intentionally does not package the host archive.
        }

        val pin = HostBootstrap.IVARNA
        val archive = PinnedReleaseArchive(
            fileName = pin.fileName,
            sha256 = pin.sha256,
            url = "$RELEASE_BASE/${pin.fileName}",
            minBytes = pin.minBytes
        )
        val destDir = TermuxHostPaths.homeDir(ctx)
        val ok = RootfsDownloader.ensurePresent(
            destDir,
            archive,
            isCancelled = { false },
            onProgress = { progress ->
                onProgress(
                    PayloadProgress(
                        completedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes,
                        phase = "Downloading host bootstrap"
                    )
                )
            }
        )
        if (!ok) return null
        val file = File(destDir, pin.fileName)
        return if (file.isFile) {
            HostRuntimePayload(file.inputStream(), file.length(), PayloadSource.REMOTE_RELEASE)
        } else {
            null
        }
    }
}

internal object StorePayloadProviders {
    val rootfs: RootfsPayloadProvider = IvarnaRootfsPayloadProvider
    val hostRuntime: HostRuntimePayloadProvider = IvarnaHostRuntimePayloadProvider
    val androidRoot: AndroidRootCapability = object : AndroidRootCapability {
        override val enabled: Boolean = true
        override val unavailableMessage: String = "Android-root integration is unavailable"
    }
}
