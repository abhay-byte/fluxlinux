package com.ivarna.fluxlinux.core.install

import android.content.Context
import android.util.Log
import com.google.android.play.core.splitcompat.SplitCompat
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths
import java.io.File

/**
 * Play-only rootfs provider. The only executable-bearing source it can select
 * is the matching on-demand PFD feature; there is no HTTP, release, or shared
 * storage fallback in this source set.
 */
object PlayFeatureRootfsProvider : RootfsPayloadProvider {
    override val id: String = "zenithblue-play-feature-delivery"

    override fun verifiedSpec(profile: DistroInstallProfile): VerifiedPayloadSpec =
        PlayPayloadRegistry.forProfile(profile)?.verifiedSpec
            ?: VerifiedPayloadStore.spec(profile)

    override fun ensurePresent(
        ctx: Context,
        destDir: File,
        profile: DistroInstallProfile,
        isCancelled: () -> Boolean,
        onProgress: (PayloadProgress) -> Unit
    ): PayloadAcquireResult {
        val payload = PlayPayloadRegistry.forProfile(profile)
            ?: return unavailable("No Play payload is registered for ${profile.distroId}", isCancelled)
        val destination = File(destDir, payload.archiveFileName)
        if (VerifiedPayloadStore.isVerified(destination, payload.verifiedSpec)) {
            onProgress(PayloadProgress(destination.length(), destination.length(), "Using verified Play rootfs"))
            return PayloadAcquireResult.Available(VerifiedPayload(destination, PayloadSource.PLAY_FEATURE))
        }

        val deliveryError = PlayFeatureDelivery.ensureInstalled(
            ctx = ctx,
            moduleName = payload.moduleName,
            isCancelled = isCancelled,
            onProgress = onProgress
        )
        if (deliveryError != null) return unavailable(deliveryError, isCancelled)

        val splitContext = PlayFeatureAssets.contextForSplit(ctx, payload.moduleName)
            ?: return unavailable("Installed Play feature ${payload.moduleName} is not accessible", isCancelled)
        val provenance = PlayFeatureAssets.readProvenance(splitContext, payload)
            ?: return unavailable("Play feature ${payload.moduleName} has no provenance manifest", isCancelled)
        provenance.validationError(payload)?.let {
            return unavailable("Play feature ${payload.moduleName} provenance rejected: $it", isCancelled)
        }

        val materialized = try {
            splitContext.assets.open(payload.assetPath).use { input ->
                VerifiedPayloadStore.materializeStream(
                    destDir = destDir,
                    spec = payload.verifiedSpec,
                    input = input,
                    expectedBytes = provenance.compressedSize,
                    isCancelled = isCancelled,
                    onProgress = onProgress
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to read ${payload.assetPath} from ${payload.moduleName}", e)
            null
        }
        return if (materialized != null) {
            onProgress(PayloadProgress(materialized.length(), materialized.length(), "Verified Play rootfs"))
            PayloadAcquireResult.Available(VerifiedPayload(materialized, PayloadSource.PLAY_FEATURE))
        } else {
            unavailable("Play rootfs materialization failed verification for ${payload.archiveFileName}", isCancelled)
        }
    }

    override fun ensurePresent(
        destDir: File,
        profile: DistroInstallProfile,
        isCancelled: () -> Boolean,
        onProgress: (PayloadProgress) -> Unit
    ): PayloadAcquireResult = unavailable(
        "Play Feature Delivery requires an Android Context; no local file fallback is supported",
        isCancelled
    )

    private fun unavailable(message: String, isCancelled: () -> Boolean) =
        PayloadAcquireResult.Unavailable(message, cancelled = isCancelled())

    private const val TAG = "PlayFeatureRootfs"
}

/** Play host source: on-demand runtime_host feature, then verified app-private staging. */
object PlayFeatureHostRuntimeProvider : HostRuntimePayloadProvider {
    override val id: String = "zenithblue-play-feature-delivery"
    override val expectedFileName: String = HostBootstrap.ZENITHBLUE.fileName

    override fun open(
        ctx: Context,
        onProgress: (PayloadProgress) -> Unit
    ): HostRuntimePayload? {
        val payload = PlayPayloadRegistry.runtimeHost
        val destDir = TermuxHostPaths.homeDir(ctx)
        val destination = File(destDir, payload.archiveFileName)
        if (VerifiedPayloadStore.isVerified(destination, payload.verifiedSpec)) {
            onProgress(PayloadProgress(destination.length(), destination.length(), "Using verified Play host runtime"))
            return HostRuntimePayload(destination.inputStream(), destination.length(), PayloadSource.PLAY_FEATURE)
        }

        val deliveryError = PlayFeatureDelivery.ensureInstalled(
            ctx = ctx,
            moduleName = payload.moduleName,
            isCancelled = { false },
            onProgress = onProgress
        )
        if (deliveryError != null) {
            Log.w(TAG, deliveryError)
            return null
        }

        val splitContext = PlayFeatureAssets.contextForSplit(ctx, payload.moduleName) ?: return null
        val provenance = PlayFeatureAssets.readProvenance(splitContext, payload) ?: return null
        provenance.validationError(payload)?.let {
            Log.e(TAG, "Host provenance rejected: $it")
            return null
        }
        val materialized = try {
            splitContext.assets.open(payload.assetPath).use { input ->
                VerifiedPayloadStore.materializeStream(
                    destDir = destDir,
                    spec = payload.verifiedSpec,
                    input = input,
                    expectedBytes = provenance.compressedSize,
                    onProgress = onProgress
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to materialize Play host runtime", e)
            null
        } ?: return null
        onProgress(PayloadProgress(materialized.length(), materialized.length(), "Verified Play host runtime"))
        return HostRuntimePayload(materialized.inputStream(), materialized.length(), PayloadSource.PLAY_FEATURE)
    }

    private const val TAG = "PlayFeatureHost"
}

internal object PlayFeatureAssets {
    fun contextForSplit(ctx: Context, moduleName: String): Context? = try {
        SplitCompat.install(ctx)
        ctx.createContextForSplit(moduleName)
    } catch (e: Exception) {
        Log.e("PlayFeatureAssets", "Cannot create context for $moduleName", e)
        null
    }

    fun readProvenance(ctx: Context, payload: PlayFeaturePayloadSpec): PlayPayloadProvenance? = try {
        ctx.assets.open(payload.provenanceAssetPath).bufferedReader().use {
            PlayPayloadProvenance.parse(it.readText())
        }
    } catch (e: Exception) {
        Log.e("PlayFeatureAssets", "Cannot read Play provenance", e)
        null
    }
}

internal object StorePayloadProviders {
    val rootfs: RootfsPayloadProvider = PlayFeatureRootfsProvider
    val hostRuntime: HostRuntimePayloadProvider = PlayFeatureHostRuntimeProvider
    val androidRoot: AndroidRootCapability = object : AndroidRootCapability {
        override val enabled: Boolean = false
        override val unavailableMessage: String =
            "Chroot and Android-root integration are not available in this flavor"
    }
}
