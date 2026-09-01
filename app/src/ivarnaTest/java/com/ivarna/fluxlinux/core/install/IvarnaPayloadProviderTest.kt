package com.ivarna.fluxlinux.core.install

import java.io.File
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IvarnaPayloadProviderTest {

    private fun repoFile(rel: String): File {
        val cwd = File("").absoluteFile
        val candidates = listOf(File(cwd, rel), File(cwd, "app/$rel"))
        return candidates.firstOrNull { it.isFile }
            ?: error("missing $rel (cwd=$cwd)")
    }

    @Test
    fun flavorWiring_retainsReleaseProviders() {
        assertSame(IvarnaRootfsPayloadProvider, PayloadProviders.rootfs)
        assertSame(IvarnaHostRuntimePayloadProvider, PayloadProviders.hostRuntime)
        assertTrue(PayloadProviders.androidRoot.enabled)
        assertTrue(PayloadProviders.rootfs.id.contains("remote"))
        assertTrue(PayloadProviders.hostRuntime.id.contains("remote"))

        val source = repoFile(
            "src/ivarna/kotlin/com/ivarna/fluxlinux/core/install/IvarnaPayloadProviders.kt"
        ).readText()
        assertTrue(source.contains("RootfsDownloader"))
        assertTrue(source.contains("RELEASE_BASE"))
    }

    @Test
    fun providerUsesRawReleasePin() {
        val profile = DistroInstallProfile.require("debian")
        assertEquals(
            "13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803",
            IvarnaRootfsPayloadProvider.verifiedSpec(profile).sha256
        )
        assertTrue(IvarnaRootfsPayloadProvider.supports(profile))
    }
}
