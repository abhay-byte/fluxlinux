package com.ivarna.fluxlinux.core.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class BaseArtifactRootfsGateTest {

    @Test
    fun `verify built APKs or build outputs contain no payloads or assets rootfs in base`() {
        val buildOutputsDir = File("build/outputs")
        val appOutputsDir = File("app/build/outputs")

        val apks = mutableListOf<File>()
        listOf(buildOutputsDir, appOutputsDir).forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown().filter { it.isFile && (it.name.endsWith(".apk") || it.name.endsWith(".aab")) }.forEach {
                    // Only inspect base APKs/bundles, not dynamic feature apks
                    if (!it.name.contains("distro_") && (it.name.contains("base") || it.name.contains("app-"))) {
                        apks.add(it)
                    }
                }
            }
        }

        apks.forEach { archiveFile ->
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toList()
                val rootfsEntries = entries.filter { it.startsWith("assets/rootfs/") }
                val payloadEntries = entries.filter { it.startsWith("assets/payloads/") || it.startsWith("payloads/") }

                assertTrue("Base artifact ${archiveFile.name} must contain zero assets/rootfs/ entries, found: $rootfsEntries", rootfsEntries.isEmpty())
                assertTrue("Base artifact ${archiveFile.name} must contain zero payloads/ entries, found: $payloadEntries", payloadEntries.isEmpty())
            }
        }
    }

    @Test
    fun `verify verify_apk_host_assets script enforces zero payloads and zero rootfs`() {
        val script = File("../scripts/verify_apk_host_assets.sh").let { if (it.exists()) it else File("scripts/verify_apk_host_assets.sh") }
        assertTrue("Script must exist", script.exists())
        val scriptContent = script.readText()

        assertTrue(scriptContent.contains("assets/rootfs/"))
        assertTrue(scriptContent.contains("payloads/"))
        assertTrue(scriptContent.contains("zero assets/rootfs/* and zero payloads/* entries in base APK"))
    }
}
