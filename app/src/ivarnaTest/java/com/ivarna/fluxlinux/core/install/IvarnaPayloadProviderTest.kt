package com.ivarna.fluxlinux.core.install

import java.io.File
import org.junit.Assert.assertSame
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
        assertTrue(PayloadProviders.rootfs.id.contains("remote"))
        assertTrue(PayloadProviders.hostRuntime.id.contains("remote"))

        val source = repoFile(
            "src/ivarna/kotlin/com/ivarna/fluxlinux/core/install/IvarnaPayloadProviders.kt"
        ).readText()
        assertTrue(source.contains("RootfsDownloader"))
        assertTrue(source.contains("RELEASE_BASE"))
    }
}
