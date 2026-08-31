package com.ivarna.fluxlinux.core.install

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Kotlin-side rootfs downloader (D2): makes the selected distro's pinned rootfs
 * archive available under [destDir]/[DistroInstallProfile.rootfsFileName].
 *
 * Local-first (D7): a verified destination file or a verified local candidate
 * (same locations `flux_install.sh` searches) short-circuits before any network
 * access. Only then does it GET the matching release asset from the ivarna
 * transport boundary, streaming into `<name>.partial` with HTTP Range
 * resume (D5), SHA256 + minimum-size gates (D4), cancellation, progress and a
 * free-space precheck (D11).
 *
 * JVM-testable: dest directory + [OkHttpClient] are injected; no Android
 * [android.content.Context] is required in the core API.
 */
object RootfsDownloader {

    private const val TAG = "RootfsDownloader"
    private const val RELEASE_BASE =
        "https://github.com/abhay-byte/fluxlinux/releases/download/rootfs"

    /** Free-space slack beyond the expected download size (D11). */
    const val FREE_SPACE_SLACK_BYTES = 8L * 1024L * 1024L

    /** Streaming buffer — a 127 MiB Manjaro archive is never loaded into memory. */
    private const val CHUNK_BYTES = 64 * 1024

    data class Progress(
        val downloadedBytes: Long,
        /** Total expected bytes; -1 when unknown. */
        val totalBytes: Long
    )

    /**
     * OkHttp with redirects (GitHub 302 → objects.githubusercontent.com), 30 s
     * connect, 60 s read/write timeouts and **no overall call timeout**
     * (Manjaro is ~127 MiB on potentially slow mobile links).
     */
    val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun DistroInstallProfile.toPinnedArchive(): PinnedReleaseArchive =
        PinnedReleaseArchive(
            fileName = rootfsFileName,
            sha256 = rootfsSha256,
            // rootfsAsset is a legacy path in production profiles. Accepting
            // an absolute URL here keeps the JVM transport tests injectable
            // without putting a release URL in common profile metadata.
            url = if (rootfsAsset.startsWith("http://") || rootfsAsset.startsWith("https://")) {
                rootfsAsset
            } else {
                "$RELEASE_BASE/$rootfsFileName"
            },
            minBytes = rootfsMinBytes
        )

    /**
     * True when `destDir/rootfsFileName` exists, is larger than
     * [DistroInstallProfile.rootfsMinBytes], and its SHA256 matches
     * [DistroInstallProfile.rootfsSha256].
     */
    fun isDeployed(destDir: File, profile: DistroInstallProfile): Boolean =
        isDeployed(destDir, profile.toPinnedArchive())

    fun isDeployed(destDir: File, archive: PinnedReleaseArchive): Boolean =
        isValid(File(destDir, archive.fileName), archive)

    /**
     * Guarantee a verified rootfs archive in [destDir].
     *
     * 1. verified destination → true (zero HTTP requests)
     * 2. first verified local candidate (same concept as `flux_install.sh`)
     *    copied into the destination → true (zero HTTP requests)
     * 3. otherwise OkHttp GET with Range resume into `<name>.partial`,
     *    verify SHA + size, then atomic rename to the final filename.
     *
     * Cancellation and gate failures keep the `.partial` file (D5) so a later
     * call can resume. A downloaded file failing the SHA/min-size gate is
     * deleted (D4).
     */
    fun ensurePresent(
        destDir: File,
        profile: DistroInstallProfile,
        client: OkHttpClient = defaultClient,
        isCancelled: () -> Boolean = { false },
        onProgress: (Progress) -> Unit = {}
    ): Boolean = ensurePresent(
        destDir,
        profile.toPinnedArchive(),
        client,
        isCancelled,
        onProgress,
        extraLocalCandidates = localCandidates(destDir, profile)
    )

    fun ensurePresent(
        destDir: File,
        archive: PinnedReleaseArchive,
        client: OkHttpClient = defaultClient,
        isCancelled: () -> Boolean = { false },
        onProgress: (Progress) -> Unit = {},
        extraLocalCandidates: List<File> = emptyList()
    ): Boolean {
        destDir.mkdirs()
        val dest = File(destDir, archive.fileName)

        // 1. Verified destination — done, no network.
        if (isValid(dest, archive)) {
            Log.i(TAG, "Archive already deployed and verified: ${dest.absolutePath}")
            return true
        }

        // 2. Local candidates (D7). $HOME/rootfs/, proot cache, Download dirs.
        val candidates = extraLocalCandidates.ifEmpty {
            listOf(
                File(destDir, archive.fileName),
                File(destDir, "rootfs/${archive.fileName}"),
                File("/sdcard/Download/${archive.fileName}"),
                File("/storage/emulated/0/Download/${archive.fileName}")
            )
        }
        for (candidate in candidates) {
            if (!candidate.isFile) continue
            if (candidate.absolutePath == dest.absolutePath) continue
            if (isCancelled()) return false
            if (!isValid(candidate, archive)) {
                Log.w(TAG, "Candidate fails gate, skipped: ${candidate.absolutePath}")
                continue
            }
            Log.i(TAG, "Copying verified local candidate ${candidate.absolutePath} → ${dest.absolutePath}")
            if (!copyWithCancel(candidate, dest, isCancelled)) return false
            if (isValid(dest, archive)) {
                Log.i(TAG, "Archive from local candidate: ${dest.absolutePath}")
                return true
            }
            Log.w(TAG, "Copied candidate failed gate — deleting ${dest.absolutePath}")
            dest.delete()
        }

        if (isCancelled()) return false

        // 3. Network download with Range resume.
        return download(destDir, dest, archive, client, isCancelled, onProgress)
    }

    // ── network ─────────────────────────────────────────────────────────────

    private fun download(
        destDir: File,
        dest: File,
        archive: PinnedReleaseArchive,
        client: OkHttpClient,
        isCancelled: () -> Boolean,
        onProgress: (Progress) -> Unit
    ): Boolean {
        val partial = File(destDir, archive.fileName + ".partial")

        // Second iteration only runs after a 416 deleted the partial.
        repeat(2) { attempt ->
            val existing = if (partial.isFile) partial.length() else 0L
            val request = Request.Builder()
                .url(archive.url)
                .apply {
                    if (existing > 0L) {
                        header("Range", "bytes=$existing-")
                    }
                }
                .build()

            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                Log.e(TAG, "Rootfs GET failed: ${e.message}")
                return false
            }

            response.use { resp ->
                when {
                    resp.code == 416 -> {
                        // Server says the partial is past EOF — delete and
                        // restart with a clean full GET.
                        Log.w(TAG, "416 Range Not Satisfiable — restarting download")
                        partial.delete()
                        if (attempt >= 1) {
                            Log.e(TAG, "Second 416 — giving up")
                            return false
                        }
                        return@use
                    }
                    resp.code == 206 -> {
                        // Append to existing partial. If the server actually
                        // restarted the payload (200 behavior under 206), the
                        // SHA gate below deletes it — no corrupt append (D5).
                        val body = resp.body ?: return false
                        val contentLength = body.contentLength()
                        val total = if (contentLength > 0L) existing + contentLength else -1L
                        val remaining = if (contentLength > 0L) contentLength else -1L
                        if (!checkFreeSpace(destDir, remaining, archive.minBytes)) {
                            return false
                        }
                        val ok = streamBody(body, partial, append = true, total, isCancelled, onProgress)
                        if (!ok || isCancelled()) return false
                        return finishVerified(dest, partial, archive)
                    }
                    resp.code == 200 -> {
                        // Full response — truncate/restart, never append a
                        // stale partial (D5).
                        val body = resp.body ?: return false
                        val contentLength = body.contentLength()
                        if (!checkFreeSpace(destDir, contentLength, archive.minBytes)) {
                            return false
                        }
                        val ok = streamBody(body, partial, append = false, contentLength, isCancelled, onProgress)
                        if (!ok || isCancelled()) return false
                        return finishVerified(dest, partial, archive)
                    }
                    else -> {
                        Log.e(TAG, "Rootfs download HTTP ${resp.code}")
                        return false
                    }
                }
            }
        }
        return false
    }

    /**
     * Stream the response body into [partial], reporting progress and honoring
     * cancellation. Returns false on IO failure or cancellation (partial kept).
     */
    private fun streamBody(
        body: okhttp3.ResponseBody,
        partial: File,
        append: Boolean,
        totalBytes: Long,
        isCancelled: () -> Boolean,
        onProgress: (Progress) -> Unit
    ): Boolean {
        val started = if (append) partial.length() else 0L
        var downloaded = started
        try {
            FileOutputStream(partial, append).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(CHUNK_BYTES)
                    while (true) {
                        if (isCancelled()) {
                            Log.i(TAG, "Download cancelled — keeping partial ${partial.absolutePath}")
                            return false
                        }
                        val read = input.read(buf)
                        if (read == -1) break
                        out.write(buf, 0, read)
                        downloaded += read
                        onProgress(Progress(downloaded, totalBytes))
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Download stream failed — partial kept: ${e.message}")
            return false
        }
        return true
    }

    /**
     * Gate the finished `.partial`: size > [PinnedReleaseArchive.minBytes]
     * and SHA256 == [PinnedReleaseArchive.sha256]. On success, atomically
     * rename to the final filename (D4/D5).
     */
    private fun finishVerified(
        dest: File,
        partial: File,
        archive: PinnedReleaseArchive
    ): Boolean {
        if (!isValid(partial, archive)) {
            // Delete the final output (D4). The partial is kept: a truncated
            // stream resumes via Range; a corrupt-complete partial self-heals
            // through the 416 restart path on the next attempt.
            Log.e(
                TAG,
                "Downloaded archive failed gate: size=${partial.length()} " +
                    "sha=${sha256(partial)} — keeping partial for resume"
            )
            dest.delete()
            return false
        }
        if (dest.exists()) dest.delete()
        if (!partial.renameTo(dest)) {
            // renameTo can fail across mount quirks; same-dir rename is atomic on
            // Android — fall back to copy+delete only when necessary.
            if (!partial.copyTo(dest, overwrite = true).isFile) {
                Log.e(TAG, "Failed to finalize ${dest.absolutePath}")
                partial.delete()
                return false
            }
            partial.delete()
        }
        Log.i(TAG, "Archive deployed: ${dest.absolutePath} (${dest.length()} bytes, SHA OK)")
        return true
    }

    // ── gates ───────────────────────────────────────────────────────────────

    internal fun isValid(file: File, profile: DistroInstallProfile): Boolean =
        isValid(file, profile.toPinnedArchive())

    internal fun isValid(file: File, archive: PinnedReleaseArchive): Boolean =
        file.isFile &&
            file.length() > archive.minBytes &&
            sha256(file) == archive.sha256

    /**
     * D11: free-space precheck after response headers.
     * [remaining] >= 0 (Content-Length known) → need `remaining + slack`;
     * unknown length → fall back to `rootfsMinBytes + slack`.
     */
    internal fun hasEnoughFreeSpace(
        usableSpace: Long,
        remaining: Long,
        rootfsMinBytes: Long
    ): Boolean =
        if (remaining >= 0L) {
            usableSpace >= remaining + FREE_SPACE_SLACK_BYTES
        } else {
            usableSpace >= rootfsMinBytes + FREE_SPACE_SLACK_BYTES
        }

    private fun checkFreeSpace(destDir: File, remaining: Long, rootfsMinBytes: Long): Boolean {
        val usable = destDir.usableSpace
        if (usable <= 0L) return true // cannot determine — let the write fail
        if (!hasEnoughFreeSpace(usable, remaining, rootfsMinBytes)) {
            Log.e(
                TAG,
                "Not enough free space: usable=$usable remaining=${if (remaining >= 0) remaining else "?"}"
            )
            return false
        }
        return true
    }

    // ── candidates ──────────────────────────────────────────────────────────

    /**
     * Local candidates in the same order `flux_install.sh` searches (D7):
     * `$HOME/<name>`, `$HOME/rootfs/<name>`, proot cache, `/sdcard/Download/`,
     * emulated Download. Only the first *verified* candidate is used.
     */
    internal fun localCandidates(destDir: File, profile: DistroInstallProfile): List<File> {
        val name = profile.rootfsFileName
        val filesRoot = destDir.parentFile ?: destDir
        return listOf(
            File(destDir, name),
            File(destDir, "rootfs/$name"),
            File(filesRoot, "usr/var/lib/proot-distro/cache/rootfs/$name"),
            File("/sdcard/Download/$name"),
            File("/sdcard/Download/rootfs.tar.xz"),
            File("/sdcard/Download/rootfs.tar.gz"),
            File("/storage/emulated/0/Download/$name"),
            File("/storage/emulated/0/Download/rootfs.tar.xz"),
            File("/storage/emulated/0/Download/rootfs.tar.gz")
        )
    }

    private fun copyWithCancel(
        src: File,
        dest: File,
        isCancelled: () -> Boolean
    ): Boolean {
        try {
            if (dest.exists()) dest.delete()
            src.inputStream().use { input ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(CHUNK_BYTES)
                    while (true) {
                        if (isCancelled()) return false
                        val read = input.read(buf)
                        if (read == -1) break
                        out.write(buf, 0, read)
                    }
                }
            }
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Candidate copy failed: ${e.message}")
            dest.delete()
            return false
        }
    }

    /** SHA256 hex of [file] (empty string on failure). */
    fun sha256(file: File): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(CHUNK_BYTES)
            while (true) {
                val r = input.read(buf)
                if (r == -1) break
                md.update(buf, 0, r)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        Log.w(TAG, "sha256 failed: ${e.message}")
        ""
    }
}
