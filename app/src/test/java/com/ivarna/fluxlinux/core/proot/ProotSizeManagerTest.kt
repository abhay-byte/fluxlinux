package com.ivarna.fluxlinux.core.proot

import com.ivarna.fluxlinux.core.terminal.FakeContext
import com.ivarna.fluxlinux.core.terminal.SessionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProotSizeManagerTest {

    @Test
    fun measure_containerWithFiles_calculatesSizeCorrectly() {
        val tempDir = Files.createTempDirectory("proot_test_files").toFile()
        try {
            val containerDir = File(tempDir, "usr/var/lib/proot-distro/containers/debian")
            containerDir.mkdirs()

            val testFile = File(containerDir, "test.bin")
            testFile.writeBytes(ByteArray(150) { 0x42 })

            val fakeCtx = FakeContext(tempDir, "/fake/jni")
            val res = ProotSizeManager.measure(fakeCtx, "debian")

            assertTrue("Directory must exist", res.dirExists)
            assertNull("Error must be null", res.error)
            assertNotNull("Bytes must not be null", res.bytes)
            assertTrue("Bytes must be >= 150", (res.bytes ?: 0L) >= 150L)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun measure_missingContainer_returnsNoDir() {
        val tempDir = Files.createTempDirectory("proot_empty_test").toFile()
        try {
            val fakeCtx = FakeContext(tempDir, "/fake/jni")
            val res = ProotSizeManager.measure(fakeCtx, "alpine")

            assertFalse("Directory must not exist", res.dirExists)
            assertEquals("no_dir", res.error)
            assertNull(res.bytes)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun isProotUninstallRunning_matchesExactSessionTitle() {
        SessionRegistry.clearForTest()
        try {
            // No sessions running
            assertFalse(ProotSizeManager.isProotUninstallRunning("Debian"))

            // Add uninstall session for "Debian"
            SessionRegistry.addForTest(
                SessionRegistry.ManagedSession(
                    session = null,
                    type = "install",
                    title = "Uninstall Debian",
                    method = "proot",
                    distroId = null // uninstall leaves distroId null
                )
            )

            // Exact match for "Debian"
            assertTrue(ProotSizeManager.isProotUninstallRunning("Debian"))

            // Does NOT match "Debian (Rooted)" or "Fedora"
            assertFalse(ProotSizeManager.isProotUninstallRunning("Debian (Rooted)"))
            assertFalse(ProotSizeManager.isProotUninstallRunning("Fedora"))

            // When uninstall session is running, measure() returns "uninstalling"
            val tempDir = Files.createTempDirectory("proot_uninstall_test").toFile()
            try {
                val fakeCtx = FakeContext(tempDir, "/fake/jni")
                val res = ProotSizeManager.measure(fakeCtx, "debian")
                assertEquals("uninstalling", res.error)
                assertNull(res.bytes)
            } finally {
                tempDir.deleteRecursively()
            }
        } finally {
            SessionRegistry.clearForTest()
        }
    }

    @Test
    fun measure_midWalkDisappearance_doesNotReturnZeroAndReportsGone() {
        val tempDir = Files.createTempDirectory("proot_gone_test").toFile()
        try {
            val containerDir = File(tempDir, "usr/var/lib/proot-distro/containers/debian")
            containerDir.mkdirs()
            val subDir = File(containerDir, "root")
            subDir.mkdirs()
            val f1 = File(containerDir, "test1.bin")
            f1.writeBytes(ByteArray(100))
            val f2 = File(subDir, "test2.bin")
            f2.writeBytes(ByteArray(200))

            val fakeCtx = FakeContext(tempDir, "/fake/jni")

            // Simulate race: mid-walk after visiting the first item, container tree disappears
            var visitedCount = 0
            ProotSizeManager.onFileVisitForTest = { _ ->
                visitedCount++
                if (visitedCount == 1) {
                    containerDir.deleteRecursively()
                }
            }

            val res = ProotSizeManager.measure(fakeCtx, "debian")

            // Invariant: disappearance must NEVER report 0 bytes as a success
            assertTrue("Must have visited at least one file before deletion", visitedCount >= 1)
            assertEquals("gone", res.error)
            assertNull("Bytes must be null, not 0L", res.bytes)
            assertFalse("dirExists must be false", res.dirExists)
        } finally {
            ProotSizeManager.onFileVisitForTest = null
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun refreshSize_transientErrors_preservePriorCacheInSharedPreferences() {
        SessionRegistry.clearForTest()
        val tempDir = Files.createTempDirectory("proot_cache_preserve_test").toFile()
        try {
            val prefsCtx = com.ivarna.fluxlinux.core.utils.FakePrefsContext()
            val priorBytes = 1_500_000_000L

            // Populate prior good cache
            ProotInfoStore.saveInstallInfo(
                prefsCtx,
                distroId = "debian",
                installed = true,
                dirExists = true,
                bytes = priorBytes
            )

            // Inject active uninstall session
            SessionRegistry.addForTest(
                SessionRegistry.ManagedSession(
                    session = null,
                    type = "install",
                    title = "Uninstall Debian",
                    method = "proot",
                    distroId = null
                )
            )

            // Refresh size while uninstalling
            val sizeUi = ProotSettingsModel.refreshSize(prefsCtx, "debian")

            // UI shows prior cache dimmed
            assertEquals(priorBytes, sizeUi.bytes)
            assertTrue("dimmedCache must be true", sizeUi.dimmedCache)
            assertTrue("Hint must explain uninstall", sizeUi.hint.contains("uninstalling"))

            // SharedPreferences MUST still hold 1.5 GB — NOT overwritten with -1 or 0!
            assertEquals(priorBytes, ProotInfoStore.cachedBytes(prefsCtx, "debian"))
        } finally {
            SessionRegistry.clearForTest()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun refreshSize_midWalkDisappearance_preservesPriorCacheInSharedPreferences() {
        val tempDir = Files.createTempDirectory("proot_midwalk_cache_test").toFile()
        try {
            val containerDir = File(tempDir, "usr/var/lib/proot-distro/containers/debian")
            containerDir.mkdirs()
            val subDir = File(containerDir, "root")
            subDir.mkdirs()
            File(containerDir, "a.bin").writeBytes(ByteArray(50))
            File(subDir, "b.bin").writeBytes(ByteArray(80))

            val fakeCtx = com.ivarna.fluxlinux.core.utils.FakePrefsContext(tempDir, "/fake/jni")
            val priorBytes = 1_500_000_000L

            // Populate prior good cache in SharedPreferences (1.5 GB)
            ProotInfoStore.saveInstallInfo(
                fakeCtx,
                distroId = "debian",
                installed = true,
                dirExists = true,
                bytes = priorBytes
            )

            // Inject mid-walk disappearance on first visited file
            var visitedCount = 0
            ProotSizeManager.onFileVisitForTest = { _ ->
                visitedCount++
                if (visitedCount == 1) {
                    containerDir.deleteRecursively()
                }
            }

            val sizeUi = ProotSettingsModel.refreshSize(fakeCtx, "debian")

            // Invariants:
            assertTrue("Must have visited at least one file before deletion", visitedCount >= 1)
            assertEquals("Must display prior cached 1.5 GB", priorBytes, sizeUi.bytes)
            assertTrue("dimmedCache must be true", sizeUi.dimmedCache)
            assertTrue("Hint must explain file gone", sizeUi.hint.contains("file gone"))

            // SharedPreferences MUST still hold 1.5 GB — NOT overwritten with 0 or -1 or null!
            assertEquals("SharedPreferences must retain 1.5 GB cache", priorBytes, ProotInfoStore.cachedBytes(fakeCtx, "debian"))
        } finally {
            ProotSizeManager.onFileVisitForTest = null
            tempDir.deleteRecursively()
        }
    }
}
