package com.ivarna.fluxlinux.core.proot

import android.content.Context
import android.util.Log
import com.ivarna.fluxlinux.core.chroot.GuestStorageCatalog
import com.ivarna.fluxlinux.core.data.DistroRepository
import com.ivarna.fluxlinux.core.terminal.SessionRegistry
import java.io.IOException

/**
 * Measures PRoot container storage using [File.walkTopDown] on the container directory.
 * No root shell is used. Guarded against active uninstall sessions.
 */
object ProotSizeManager {

    private const val TAG = "ProotSize"
    const val TIMEOUT_MS = 90_000L

    /** Traversal hook for deterministic race testing (e.g., simulating container disappearance mid-walk). */
    @Volatile
    var onFileVisitForTest: ((java.io.File) -> Unit)? = null

    data class Result(
        val path: String,
        val bytes: Long?,
        val dirExists: Boolean,
        val error: String? = null
    )

    fun isProotUninstallRunning(distroName: String): Boolean =
        SessionRegistry.sessions().any { it.title == "Uninstall $distroName" }

    fun measure(context: Context, distroId: String): Result {
        val distro = DistroRepository.supportedDistros.firstOrNull { it.id == distroId }
        val distroName = distro?.name ?: distroId

        val containerDir = GuestStorageCatalog.prootContainerDir(context, distroId)
        val initialPath = containerDir?.absolutePath ?: GuestStorageCatalog.prootContainerPath(context, distroId) ?: ""

        if (isProotUninstallRunning(distroName)) {
            Log.d(TAG, "measure skipped: uninstall in progress for $distroName")
            return Result(
                path = initialPath,
                bytes = null,
                dirExists = containerDir?.isDirectory == true,
                error = "uninstalling"
            )
        }

        if (containerDir == null || !containerDir.exists() || !containerDir.isDirectory) {
            return Result(
                path = initialPath,
                bytes = null,
                dirExists = false,
                error = "no_dir"
            )
        }

        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        var timedOut = false
        var totalBytes = 0L

        return try {
            val sequence = containerDir.walkTopDown()
            for (file in sequence) {
                onFileVisitForTest?.invoke(file)
                if (System.currentTimeMillis() > deadline) {
                    timedOut = true
                    break
                }
                if (file.isFile) {
                    val len = file.length()
                    if (len > 0L) {
                        totalBytes += len
                    }
                }
            }

            // Post-walk validation: ensure container directory didn't vanish mid-walk
            val stillExists = containerDir.exists() && containerDir.isDirectory
            when {
                timedOut -> {
                    Log.w(TAG, "measure timed out after ${TIMEOUT_MS}ms on $initialPath")
                    Result(
                        path = containerDir.absolutePath,
                        bytes = null,
                        dirExists = stillExists,
                        error = "timeout"
                    )
                }
                !stillExists -> {
                    Log.w(TAG, "container disappeared mid-walk on $initialPath")
                    Result(
                        path = containerDir.absolutePath,
                        bytes = null,
                        dirExists = false,
                        error = "gone"
                    )
                }
                else -> {
                    Result(
                        path = containerDir.absolutePath,
                        bytes = totalBytes,
                        dirExists = true,
                        error = null
                    )
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "measure failed/file disappeared: ${e.message}")
            Result(
                path = containerDir.absolutePath,
                bytes = null,
                dirExists = containerDir.isDirectory,
                error = "gone"
            )
        } catch (e: Exception) {
            Log.w(TAG, "unexpected measure exception: ${e.message}")
            Result(
                path = containerDir.absolutePath,
                bytes = null,
                dirExists = containerDir.isDirectory,
                error = "measure_failed"
            )
        }
    }
}
