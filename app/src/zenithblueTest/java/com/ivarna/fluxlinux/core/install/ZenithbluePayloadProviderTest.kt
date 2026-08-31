package com.ivarna.fluxlinux.core.install

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZenithbluePayloadProviderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val payload = "verified local rootfs payload".repeat(80).toByteArray()

    private fun profile(): DistroInstallProfile {
        val name = "test_rootfs.tar.xz"
        return DistroInstallProfile(
            distroId = "test",
            prootName = "test",
            method = "proot",
            rootfsAsset = "rootfs/$name",
            rootfsFileName = name,
            rootfsSha256 = sha256(payload),
            rootfsMinBytes = 1L,
            familyScript = "scripts/test/setup.sh",
            customizationScript = "scripts/test/custom.sh",
            displayName = "Test"
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun repoFile(rel: String): File {
        val cwd = File("").absoluteFile
        val candidates = listOf(File(cwd, rel), File(cwd, "app/$rel"))
        return candidates.firstOrNull { it.isFile }
            ?: error("missing $rel (cwd=$cwd)")
    }

    @Test
    fun flavorWiring_isPlayLocalOnly() {
        assertSame(PlayFeatureRootfsProvider, PayloadProviders.rootfs)
        assertSame(PlayFeatureHostRuntimeProvider, PayloadProviders.hostRuntime)
        assertEquals("zenithblue-local-only", PayloadProviders.rootfs.id)
        assertEquals("zenithblue-packaged-or-local", PayloadProviders.hostRuntime.id)

        val source = repoFile(
            "src/zenithblue/kotlin/com/ivarna/fluxlinux/core/install/ZenithbluePayloadProviders.kt"
        ).readText()
        assertFalse(source.contains("RootfsDownloader"))
        assertFalse(source.contains("https://"))
        assertTrue(source.contains("VerifiedPayloadStore"))
    }

    @Test
    fun localVerifiedRootfs_isAcceptedWithoutTransport() {
        val home = tmp.newFolder("home")
        val p = profile()
        val candidate = File(home, "rootfs/${p.rootfsFileName}").also {
            it.parentFile.mkdirs()
            it.writeBytes(payload)
        }
        val progress = mutableListOf<PayloadProgress>()

        val result = PlayFeatureRootfsProvider.ensurePresent(home, p, onProgress = progress::add)

        assertTrue(candidate.isFile)
        assertTrue(result is PayloadAcquireResult.Available)
        assertEquals(
            File(home, p.rootfsFileName).absolutePath,
            (result as PayloadAcquireResult.Available).payload.file.absolutePath
        )
        assertTrue(VerifiedPayloadStore.isVerified(File(home, p.rootfsFileName), VerifiedPayloadStore.spec(p)))
        assertTrue(progress.any { it.phase.contains("local verified") })
    }

    @Test
    fun missingOrCancelledRootfs_failsClosed() {
        val p = profile()
        val missing = PlayFeatureRootfsProvider.ensurePresent(tmp.newFolder("missing"), p)
        assertTrue(missing is PayloadAcquireResult.Unavailable)
        assertFalse((missing as PayloadAcquireResult.Unavailable).message.contains("http"))

        val cancelled = PlayFeatureRootfsProvider.ensurePresent(
            tmp.newFolder("cancelled"),
            p,
            isCancelled = { true }
        )
        assertTrue(cancelled is PayloadAcquireResult.Unavailable)
        assertTrue((cancelled as PayloadAcquireResult.Unavailable).cancelled)
    }
}
