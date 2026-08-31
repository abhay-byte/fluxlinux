package com.ivarna.fluxlinux.core.install

import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * JVM-only tests for [RootfsDownloader] (no Robolectric, no Context):
 * 200 happy path, SHA gate, resume (Range/206/416), cancellation, local-first,
 * free-space predicate.
 */
class RootfsDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()

    private val payload = "FluxLinux test rootfs payload".repeat(400).toByteArray()
    private val payloadSha = sha256Bytes(payload)
    private val half = payload.size / 2

    private fun profile(name: String, url: String): DistroInstallProfile =
        DistroInstallProfile(
            distroId = "test",
            prootName = "test",
            method = "proot",
            rootfsAsset = url,
            rootfsFileName = name,
            rootfsSha256 = payloadSha,
            rootfsMinBytes = 1L,
            familyScript = "scripts/test/setup.sh",
            customizationScript = "scripts/test/custom.sh",
            displayName = "Test"
        )

    private fun client(): OkHttpClient = OkHttpClient.Builder().build()

    private fun sha256Bytes(bytes: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun writeFile(f: File, bytes: ByteArray) {
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
    }

    @Test
    fun happyPath_200_deploysVerifiedFile() {
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))
        server.start()
        val dest = tmp.newFolder("home")
        val p = profile("test_rootfs.tar.xz", server.url("/rootfs/test_rootfs.tar.xz").toString())

        assertTrue(RootfsDownloader.ensurePresent(dest, p, client()))
        val out = File(dest, p.rootfsFileName)
        assertTrue(out.isFile)
        assertEquals(payload.size.toLong(), out.length())
        assertTrue(RootfsDownloader.isDeployed(dest, p))
        assertEquals(1, server.requestCount)
        // Second call: verified destination → zero extra requests.
        assertTrue(RootfsDownloader.ensurePresent(dest, p, client()))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun truncatedDownload_keepsPartial_thenRange206_resumes() {
        server.enqueue(MockResponse().setBody(Buffer().write(payload.copyOfRange(0, half))))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $half-${payload.size - 1}/${payload.size}")
                .setBody(Buffer().write(payload.copyOfRange(half, payload.size)))
        )
        server.start()
        val dest = tmp.newFolder("home")
        val p = profile("test_rootfs.tar.xz", server.url("/rootfs/test_rootfs.tar.xz").toString())

        // First call: 200 with a truncated body → SHA gate fails → partial kept.
        val lastProgress = AtomicReference<RootfsDownloader.Progress>()
        assertFalse(
            RootfsDownloader.ensurePresent(dest, p, client(), onProgress = { lastProgress.set(it) })
        )
        val partial = File(dest, p.rootfsFileName + ".partial")
        assertTrue("partial must be retained", partial.isFile)
        assertEquals(half.toLong(), partial.length())
        assertFalse(File(dest, p.rootfsFileName).exists())

        // Second call: Range header on the existing partial → 206 append → success.
        val resumeProgress = AtomicReference<RootfsDownloader.Progress>()
        assertTrue(
            RootfsDownloader.ensurePresent(dest, p, client(), onProgress = { resumeProgress.set(it) })
        )
        assertEquals(2, server.requestCount)
        server.takeRequest() // first (truncated 200) request
        val range = server.takeRequest().getHeader("Range")
        assertEquals("bytes=$half-", range)
        val out = File(dest, p.rootfsFileName)
        assertTrue(out.isFile)
        assertTrue(RootfsDownloader.isDeployed(dest, p))
        assertFalse(partial.exists())
        val progress = resumeProgress.get()
        assertNotNull(progress)
        // 206 progress total must be existing + remaining (no 200-append).
        assertTrue(progress!!.totalBytes >= payload.size.toLong())
        assertTrue(progress.downloadedBytes >= payload.size.toLong())
    }

    @Test
    fun range416_deletesPartial_andRestartsFullGet() {
        server.enqueue(MockResponse().setResponseCode(416))
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))
        server.start()
        val dest = tmp.newFolder("home")
        val p = profile("test_rootfs.tar.xz", server.url("/rootfs/test_rootfs.tar.xz").toString())
        writeFile(File(dest, p.rootfsFileName + ".partial"), "stale-bytes".toByteArray())

        assertTrue(RootfsDownloader.ensurePresent(dest, p, client()))
        assertEquals(2, server.requestCount)
        assertEquals("bytes=11-", server.takeRequest().getHeader("Range"))
        assertTrue(RootfsDownloader.isDeployed(dest, p))
        assertFalse(File(dest, p.rootfsFileName + ".partial").exists())
    }

    @Test
    fun shaMismatch_deletesOutputAndPartial() {
        server.enqueue(
            MockResponse().setBody(Buffer().write("corrupt-rootfs-bytes".repeat(10).toByteArray()))
        )
        server.start()
        val dest = tmp.newFolder("home")
        val p = profile("test_rootfs.tar.xz", server.url("/rootfs/test_rootfs.tar.xz").toString())

        assertFalse(RootfsDownloader.ensurePresent(dest, p, client()))
        assertFalse("mismatched final must be deleted", File(dest, p.rootfsFileName).exists())
        assertTrue(
            "partial is retained for resume (416 self-heals corrupt restarts)",
            File(dest, p.rootfsFileName + ".partial").isFile
        )
    }

    @Test
    fun cancelledMidStream_keepsPartial_andReturnsFalse() {
        val body = MockResponse()
            .setChunkedBody(Buffer().write(payload), 256)
            .throttleBody(4096, 50, TimeUnit.MILLISECONDS)
        server.enqueue(body)
        server.start()
        val dest = tmp.newFolder("home")
        val p = profile("test_rootfs.tar.xz", server.url("/rootfs/test_rootfs.tar.xz").toString())
        val cancelled = AtomicBoolean(false)

        val ok = RootfsDownloader.ensurePresent(
            dest, p, client(),
            isCancelled = { cancelled.get() },
            onProgress = { cancelled.set(true) }
        )
        assertFalse(ok)
        val partial = File(dest, p.rootfsFileName + ".partial")
        assertTrue("partial must be retained on cancel", partial.isFile)
        assertTrue(partial.length() < payload.size)
        assertFalse(File(dest, p.rootfsFileName).exists())
    }

    @Test
    fun alreadyValidDestination_causesZeroRequests() {
        server.start()
        val dest = tmp.newFolder("home")
        val p = profile("test_rootfs.tar.xz", server.url("/rootfs/test_rootfs.tar.xz").toString())
        writeFile(File(dest, p.rootfsFileName), payload)

        assertTrue(RootfsDownloader.isDeployed(dest, p))
        assertTrue(RootfsDownloader.ensurePresent(dest, p, client()))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun validLocalCandidate_copiedWithZeroNetwork() {
        server.start()
        val dest = tmp.newFolder("home")
        val p = profile("test_rootfs.tar.xz", server.url("/rootfs/test_rootfs.tar.xz").toString())
        // $HOME/rootfs/<name> — the documented offline placement directory.
        writeFile(File(dest, "rootfs/${p.rootfsFileName}"), payload)

        assertTrue(RootfsDownloader.ensurePresent(dest, p, client()))
        assertEquals(0, server.requestCount)
        assertTrue(RootfsDownloader.isDeployed(dest, p))
    }

    @Test
    fun invalidLocalCandidate_isSkipped_andNetworkIsUsed() {
        val bad = server.enqueue(MockResponse().setBody(Buffer().write(payload)))
        server.start()
        val dest = tmp.newFolder("home")
        val p = profile("test_rootfs.tar.xz", server.url("/rootfs/test_rootfs.tar.xz").toString())
        // Undersized/garbage candidate must not be copied — network fallback wins.
        writeFile(File(dest, "rootfs/${p.rootfsFileName}"), "tiny".toByteArray())

        assertTrue(RootfsDownloader.ensurePresent(dest, p, client()))
        assertEquals(1, server.requestCount)
        assertTrue(RootfsDownloader.isDeployed(dest, p))
    }

    @Test
    fun partialPlus200_truncatesInsteadOfAppending() {
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))
        server.start()
        val dest = tmp.newFolder("home")
        val p = profile("test_rootfs.tar.xz", server.url("/rootfs/test_rootfs.tar.xz").toString())
        writeFile(File(dest, p.rootfsFileName + ".partial"), "junk-prefix-do-not-keep".toByteArray())

        assertTrue(RootfsDownloader.ensurePresent(dest, p, client()))
        val out = File(dest, p.rootfsFileName)
        assertEquals(payload.size.toLong(), out.length())
        assertTrue(RootfsDownloader.isDeployed(dest, p))
    }

    @Test
    fun freeSpacePredicate_usesContentLengthThenMinBytes() {
        // Known length: need remaining + 8 MiB slack.
        assertTrue(
            RootfsDownloader.hasEnoughFreeSpace(
                100L * 1024 * 1024, 50L * 1024 * 1024, 1L * 1024 * 1024
            )
        )
        assertFalse(
            RootfsDownloader.hasEnoughFreeSpace(
                50L * 1024 * 1024 + RootfsDownloader.FREE_SPACE_SLACK_BYTES - 1,
                50L * 1024 * 1024,
                1L * 1024 * 1024
            )
        )
        // Unknown length: fall back to rootfsMinBytes + 8 MiB.
        assertTrue(
            RootfsDownloader.hasEnoughFreeSpace(
                40L * 1024 * 1024 + RootfsDownloader.FREE_SPACE_SLACK_BYTES,
                -1L,
                40L * 1024 * 1024
            )
        )
        assertFalse(
            RootfsDownloader.hasEnoughFreeSpace(
                40L * 1024 * 1024 + RootfsDownloader.FREE_SPACE_SLACK_BYTES - 1,
                -1L,
                40L * 1024 * 1024
            )
        )
    }

    @Test
    fun isDeployed_rejectsUndersizedAndMismatched() {
        val dest = tmp.newFolder("home")
        val p = profile("test_rootfs.tar.xz", "https://example.invalid/rootfs")
        writeFile(File(dest, p.rootfsFileName), "tiny".toByteArray())
        assertFalse(RootfsDownloader.isDeployed(dest, p))
        writeFile(File(dest, p.rootfsFileName), "wrong-sha-but-big-enough".repeat(100).toByteArray())
        assertFalse(RootfsDownloader.isDeployed(dest, p))
        writeFile(File(dest, p.rootfsFileName), payload)
        assertTrue(RootfsDownloader.isDeployed(dest, p))
    }

    @Test
    fun httpError_returnsFalseWithoutFiles() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()
        val dest = tmp.newFolder("home")
        val p = profile("test_rootfs.tar.xz", server.url("/rootfs/missing.tar.xz").toString())
        assertFalse(RootfsDownloader.ensurePresent(dest, p, client()))
        assertFalse(File(dest, p.rootfsFileName).exists())
    }
}
