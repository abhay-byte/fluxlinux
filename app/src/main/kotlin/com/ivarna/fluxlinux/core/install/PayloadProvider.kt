package com.ivarna.fluxlinux.core.install

import android.content.Context
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Source-neutral progress reported while a provider materializes a payload.
 * The installer deliberately knows nothing about HTTP, Play APIs, or release
 * URLs; it only consumes a verified local file.
 */
data class PayloadProgress(
    val completedBytes: Long,
    val totalBytes: Long,
    val phase: String = ""
)

enum class PayloadSource {
    PACKAGED_ASSET,
    LOCAL_VERIFIED,
    REMOTE_RELEASE
}

data class VerifiedPayload(
    val file: File,
    val source: PayloadSource
)

sealed class PayloadAcquireResult {
    data class Available(val payload: VerifiedPayload) : PayloadAcquireResult()

    data class Unavailable(
        val message: String,
        val cancelled: Boolean = false
    ) : PayloadAcquireResult()
}

/** Rootfs acquisition seam shared by onboarding and all terminal backends. */
interface RootfsPayloadProvider {
    val id: String

    fun ensurePresent(
        destDir: File,
        profile: DistroInstallProfile,
        isCancelled: () -> Boolean = { false },
        onProgress: (PayloadProgress) -> Unit = {}
    ): PayloadAcquireResult
}

/** Host-prefix source seam; extraction and configuration remain common. */
interface HostRuntimePayloadProvider {
    val id: String
    val expectedFileName: String

    /** Caller owns and must close the returned stream. */
    fun open(
        ctx: Context,
        onProgress: (PayloadProgress) -> Unit = {}
    ): HostRuntimePayload?
}

data class HostRuntimePayload(
    val stream: InputStream,
    val totalBytes: Long,
    val source: PayloadSource
)

/** Metadata visible to common code without carrying a transport URL. */
data class VerifiedPayloadSpec(
    val fileName: String,
    val sha256: String,
    val minBytes: Long
)

/** Local-only verification/materialization used by the Play placeholder. */
object VerifiedPayloadStore {

    private const val COPY_BUFFER_BYTES = 64 * 1024

    fun spec(profile: DistroInstallProfile): VerifiedPayloadSpec =
        VerifiedPayloadSpec(profile.rootfsFileName, profile.rootfsSha256, profile.rootfsMinBytes)

    fun isVerified(file: File, spec: VerifiedPayloadSpec): Boolean =
        file.isFile && file.length() > spec.minBytes && sha256(file) == spec.sha256

    /**
     * Finds a verified local candidate and materializes it at destDir/fileName.
     * No network-capable type is referenced by this helper.
     */
    fun materialize(
        destDir: File,
        spec: VerifiedPayloadSpec,
        candidates: List<File>,
        isCancelled: () -> Boolean = { false }
    ): File? {
        destDir.mkdirs()
        val destination = File(destDir, spec.fileName)
        if (isVerified(destination, spec)) return destination

        for (candidate in candidates) {
            if (isCancelled()) return null
            if (!candidate.isFile || candidate.absoluteFile == destination.absoluteFile) continue
            if (!isVerified(candidate, spec)) continue
            try {
                candidate.inputStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(COPY_BUFFER_BYTES)
                        while (true) {
                            if (isCancelled()) {
                                destination.delete()
                                return null
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } catch (_: Exception) {
                destination.delete()
                continue
            }
            if (isVerified(destination, spec)) return destination
            destination.delete()
        }
        return null
    }

    fun sha256(file: File): String = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        ""
    }
}

/** Compile-time supplied by exactly one store flavor. */
object PayloadProviders {
    // StorePayloadProviders is supplied by exactly one flavor source set.
    // Keeping this common call site stable prevents policy-sensitive providers
    // from leaking into onboarding/session business logic.
    val rootfs: RootfsPayloadProvider
        get() = StorePayloadProviders.rootfs

    val hostRuntime: HostRuntimePayloadProvider
        get() = StorePayloadProviders.hostRuntime
}
