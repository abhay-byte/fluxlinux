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
                val isAab = archiveFile.name.endsWith(".aab")
                val rootfsEntries = entries.filter {
                    it.startsWith("assets/rootfs/") ||
                        (isAab && it.startsWith("base/assets/rootfs/"))
                }
                val payloadEntries = entries.filter {
                    it.startsWith("assets/payloads/") ||
                        it.startsWith("payloads/") ||
                        (isAab && it.startsWith("base/assets/payloads/"))
                }

                assertTrue("Base artifact ${archiveFile.name} must contain zero assets/rootfs/ entries, found: $rootfsEntries", rootfsEntries.isEmpty())
                assertTrue("Base artifact ${archiveFile.name} must contain zero payloads/ entries, found: $payloadEntries", payloadEntries.isEmpty())
            }
        }
    }

    @Test
    fun `verify synthetic AAB rejects rootfs or payloads in base module but allows in feature modules`() {
        val tempDir = java.nio.file.Files.createTempDirectory("aab_gate_test").toFile()
        try {
            // Helper to inspect entries
            fun inspectEntries(entries: List<String>): Pair<List<String>, List<String>> {
                val rootfs = entries.filter {
                    it.startsWith("assets/rootfs/") || it.startsWith("base/assets/rootfs/")
                }
                val payloads = entries.filter {
                    it.startsWith("assets/payloads/") || it.startsWith("payloads/") || it.startsWith("base/assets/payloads/")
                }
                return rootfs to payloads
            }

            // 1. Synthetic AAB with base/assets/payloads/foo -> FAIL
            val badBaseEntries = listOf(
                "base/manifest/AndroidManifest.xml",
                "base/assets/payloads/distro_debian/debian.tar.xz",
                "distro_alpine/assets/payloads/distro_alpine/alpine.tar.gz"
            )
            val (badRootfs, badPayloads) = inspectEntries(badBaseEntries)
            assertTrue("Must reject base/assets/payloads/ entries", badPayloads.isNotEmpty())

            // 2. Synthetic AAB with only feature module payloads -> PASS
            val goodEntries = listOf(
                "base/manifest/AndroidManifest.xml",
                "base/assets/bootstrap.tar",
                "distro_alpine/assets/payloads/distro_alpine/alpine_3.24_rootfs.tar.gz",
                "distro_debian/assets/payloads/distro_debian/debian_13_rootfs.tar.xz"
            )
            val (goodRootfs, goodPayloads) = inspectEntries(goodEntries)
            assertTrue("Good AAB must have 0 base rootfs", goodRootfs.isEmpty())
            assertTrue("Good AAB must have 0 base payloads", goodPayloads.isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
