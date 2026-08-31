package com.ivarna.fluxlinux.core.install

import android.content.Context
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths
import java.io.File

/**
 * Play placeholder until Worker 03 supplies the approved delivery path.
 * It accepts only a locally verified rootfs and never creates a network source.
 */
object PlayFeatureRootfsProvider : RootfsPayloadProvider {
    override val id: String = "zenithblue-local-only"

    override fun ensurePresent(
        destDir: File,
        profile: DistroInstallProfile,
        isCancelled: () -> Boolean,
        onProgress: (PayloadProgress) -> Unit
    ): PayloadAcquireResult {
        val name = profile.rootfsFileName
        val filesRoot = destDir.parentFile ?: destDir
        val file = VerifiedPayloadStore.materialize(
            destDir = destDir,
            spec = VerifiedPayloadStore.spec(profile),
            candidates = listOf(
                File(destDir, name),
                File(destDir, "rootfs/$name"),
                File(filesRoot, "usr/var/lib/proot-distro/cache/rootfs/$name"),
                File("/sdcard/Download/$name"),
                File("/storage/emulated/0/Download/$name")
            ),
            isCancelled = isCancelled
        )
        return if (file != null) {
            onProgress(PayloadProgress(0L, file.length(), "Using local verified rootfs"))
            PayloadAcquireResult.Available(VerifiedPayload(file, PayloadSource.LOCAL_VERIFIED))
        } else {
            PayloadAcquireResult.Unavailable(
                message = "Play rootfs delivery is not available yet; place a verified " +
                    "$name in the app home directory",
                cancelled = isCancelled()
            )
        }
    }
}

/**
 * Play host source: embedded bootstrap asset, or a verified local repair copy.
 * Worker 03 may replace the local repair branch with the approved delivery API.
 */
object PlayFeatureHostRuntimeProvider : HostRuntimePayloadProvider {
    private const val ASSET_NAME = "bootstrap.tar"

    override val id: String = "zenithblue-packaged-or-local"
    override val expectedFileName: String = HostBootstrap.ZENITHBLUE.fileName

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
            // A repair is permitted only from a verified local file.
        }

        val pin = HostBootstrap.ZENITHBLUE
        val destDir = TermuxHostPaths.homeDir(ctx)
        val file = VerifiedPayloadStore.materialize(
            destDir = destDir,
            spec = pin,
            candidates = listOf(
                File(destDir, pin.fileName),
                File("/sdcard/Download/${pin.fileName}"),
                File("/storage/emulated/0/Download/${pin.fileName}")
            )
        ) ?: return null
        onProgress(PayloadProgress(0L, file.length(), "Using local verified host bootstrap"))
        return HostRuntimePayload(file.inputStream(), file.length(), PayloadSource.LOCAL_VERIFIED)
    }
}

internal object StorePayloadProviders {
    val rootfs: RootfsPayloadProvider = PlayFeatureRootfsProvider
    val hostRuntime: HostRuntimePayloadProvider = PlayFeatureHostRuntimeProvider
}
