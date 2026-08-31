package com.ivarna.fluxlinux.core.install

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
    PLAY_FEATURE,
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

    /**
     * Context-aware entry point used by providers whose source is Android's
     * split/module delivery API. The legacy overload remains available to the
     * non-Play provider and to older callers.
     */
    fun ensurePresent(
        ctx: Context,
        destDir: File,
        profile: DistroInstallProfile,
        isCancelled: () -> Boolean = { false },
        onProgress: (PayloadProgress) -> Unit = {}
    ): PayloadAcquireResult = ensurePresent(destDir, profile, isCancelled, onProgress)

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

/**
 * Build-time capability for policy-sensitive Android-root/chroot execution.
 * The common runtime can keep its session plumbing while refusing to select
 * rooted paths in a flavor that does not ship that integration.
 */
interface AndroidRootCapability {
    val enabled: Boolean
    val unavailableMessage: String
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

/** Local verification/materialization shared by all transport providers. */
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
                    val copied = materializeStream(
                        destDir = destDir,
                        spec = spec,
                        input = input,
                        expectedBytes = candidate.length(),
                        isCancelled = isCancelled
                    )
                    if (copied != null) return copied
                }
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    /**
     * Copy a payload stream to a deterministic temporary sibling, verify it,
     * then atomically promote it to [destDir]/[spec.fileName]. A partial file is
     * never exposed as a usable payload and no caller needs to know where a
     * Play split stores its own files.
     */
    fun materializeStream(
        destDir: File,
        spec: VerifiedPayloadSpec,
        input: InputStream,
        expectedBytes: Long? = null,
        isCancelled: () -> Boolean = { false },
        onProgress: (PayloadProgress) -> Unit = {}
    ): File? {
        destDir.mkdirs()
        val destination = File(destDir, spec.fileName)
        if (isVerified(destination, spec) &&
            (expectedBytes == null || destination.length() == expectedBytes)
        ) return destination

        val temporary = File(destDir, ".${spec.fileName}.part")
        temporary.delete()
        var copiedBytes = 0L
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    if (isCancelled()) {
                        temporary.delete()
                        return null
                    }
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    copiedBytes += read
                    onProgress(PayloadProgress(copiedBytes, expectedBytes ?: 0L, "Materializing payload"))
                }
                output.fd.sync()
            }

            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (copiedBytes <= spec.minBytes || hash != spec.sha256 ||
                (expectedBytes != null && copiedBytes != expectedBytes)
            ) {
                temporary.delete()
                return null
            }

            // The temp file is a sibling of the destination, so an atomic
            // rename is available on the app-private filesystem. Fail closed
            // if the platform cannot provide that guarantee.
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            return if (isVerified(destination, spec) &&
                (expectedBytes == null || destination.length() == expectedBytes)
            ) destination else null
        } catch (_: Exception) {
            temporary.delete()
            return null
        }
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

    val androidRoot: AndroidRootCapability
        get() = StorePayloadProviders.androidRoot
}
