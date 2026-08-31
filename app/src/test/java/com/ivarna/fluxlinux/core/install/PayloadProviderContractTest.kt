package com.ivarna.fluxlinux.core.install

import java.io.File
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PayloadProviderContractTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun repoFile(rel: String): File {
        val cwd = File("").absoluteFile
        val candidates = listOf(File(cwd, rel), File(cwd, "app/$rel"))
        return candidates.firstOrNull { it.isFile }
            ?: error("missing $rel (cwd=$cwd)")
    }

    @Test
    fun commonInstallContract_hasNoRemoteTransportSelection() {
        val onboarding = repoFile(
            "src/main/kotlin/com/ivarna/fluxlinux/core/install/OnboardingInstallRunner.kt"
        ).readText()
        val sessions = repoFile(
            "src/main/kotlin/com/ivarna/fluxlinux/core/terminal/InstallSessionFactory.kt"
        ).readText()
        val bootstrap = repoFile(
            "src/main/kotlin/com/ivarna/fluxlinux/core/terminal/BootstrapInstaller.kt"
        ).readText()

        assertTrue(onboarding.contains("PayloadProviders.rootfs"))
        assertFalse(onboarding.contains("RootfsDownloader"))
        assertFalse(onboarding.contains("FLUX_ROOTFS_URL"))
        assertTrue(onboarding.contains("PayloadProviders.androidRoot"))
        assertFalse(sessions.contains("FLUX_ROOTFS_URL"))
        assertTrue(sessions.contains("PayloadProviders.androidRoot"))
        assertTrue(bootstrap.contains("PayloadProviders.hostRuntime"))
        assertFalse(bootstrap.contains("RootfsDownloader"))
    }

    @Test
    fun materializeStream_promotesOnlyVerifiedCompletePayload() {
        val payload = ByteArray(32 * 1024) { index -> (index % 251).toByte() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { "%02x".format(it) }
        val destinationDir = tmp.newFolder("payload")
        val spec = VerifiedPayloadSpec("rootfs.tar.xz", hash, 1L)

        val destination = VerifiedPayloadStore.materializeStream(
            destinationDir,
            spec,
            ByteArrayInputStream(payload),
            expectedBytes = payload.size.toLong()
        )

        assertEquals(File(destinationDir, spec.fileName), destination)
        assertEquals(payload.toList(), destination!!.readBytes().toList())
        assertFalse(File(destinationDir, ".${spec.fileName}.part").exists())
    }

    @Test
    fun materializeStream_rejectsByteCountMismatchAndLeavesNoPartial() {
        val payload = "complete payload".repeat(32).toByteArray()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { "%02x".format(it) }
        val destinationDir = tmp.newFolder("mismatch")
        val spec = VerifiedPayloadSpec("rootfs.tar.xz", hash, 1L)

        val result = VerifiedPayloadStore.materializeStream(
            destinationDir,
            spec,
            ByteArrayInputStream(payload),
            expectedBytes = payload.size.toLong() + 1L
        )

        assertEquals(null, result)
        assertFalse(File(destinationDir, spec.fileName).exists())
        assertFalse(File(destinationDir, ".${spec.fileName}.part").exists())
    }
}
