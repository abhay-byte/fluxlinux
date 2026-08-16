package com.ivarna.fluxlinux.core.chroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChrootSettingsModelTest {

    @Test
    fun sumSizes_emptyList_returnsNullAndNoRootfsMessage() {
        val (sum, note) = ChrootSettingsModel.sumSizes(emptyList())
        assertNull(sum)
        assertEquals("No chroot rootfs on host", note)
    }

    @Test
    fun sumSizes_allSuccess_returnsSumAndCount() {
        val (sum, note) = ChrootSettingsModel.sumSizes(listOf(1000L, 2000L, 3000L))
        assertEquals(6000L, sum)
        assertEquals("3 roots · binds excluded", note)
    }

    @Test
    fun sumSizes_partialSuccess_returnsSumAndPartialNoteWithoutZeroing() {
        val (sum, note) = ChrootSettingsModel.sumSizes(listOf(1000L, null, 2000L))
        assertEquals(3000L, sum)
        assertEquals("Partial · 2 of 3 measured", note)
    }

    @Test
    fun sumSizes_allFailure_returnsNullAndFailedNote() {
        val (sum, note) = ChrootSettingsModel.sumSizes(listOf(null, null))
        assertNull(sum)
        assertEquals("Size probe failed", note)
    }

    @Test
    fun confirmKillCopy_containsAll12KnownPaths() {
        val paths = GuestStorageCatalog.KNOWN_CHROOT_PATHS.toList()
        val copy = ChrootSettingsModel.confirmKillCopy(paths)

        for (p in paths) {
            assertTrue("Kill confirmation copy must contain path $p", copy.contains(p))
        }
    }

    @Test
    fun resolveStatus_leftoverDirectory_returnsPresent() {
        // Case A: Leftover directory (installed = false, dirExists = true)
        assertEquals("PRESENT", ChrootSettingsModel.resolveStatus(installed = false, dirExists = true))
    }

    @Test
    fun resolveStatus_shellOnlyValidInstallation_returnsInstalled() {
        // Case B: Shell-only valid installation (installed = true, dirExists = true)
        assertEquals("INSTALLED", ChrootSettingsModel.resolveStatus(installed = true, dirExists = true))
    }

    @Test
    fun resolveStatus_markerBasedInstallation_returnsInstalled() {
        // Case C: Marker-based installation (installed = true, dirExists = true)
        assertEquals("INSTALLED", ChrootSettingsModel.resolveStatus(installed = true, dirExists = true))
    }

    @Test
    fun resolveStatus_missing_returnsNotInstalled() {
        // Case D: Missing (installed = false, dirExists = false)
        assertEquals("NOT INSTALLED", ChrootSettingsModel.resolveStatus(installed = false, dirExists = false))
    }

    @Test
    fun loadCached_leftoverDir_doesNotElevateInstalledState() {
        val ctx = com.ivarna.fluxlinux.core.utils.FakePrefsContext()
        // Save leftover state: dirExists = true, installed = false
        ChrootInfoStore.saveInstallInfo(
            ctx,
            distroId = "alpine_chroot",
            installed = false,
            dirExists = true,
            bytes = 500_000_000L,
            rootOk = true,
            viaRoot = true
        )

        val snap = ChrootSettingsModel.loadCached(ctx, distroId = "alpine_chroot", path = "/data/local/tmp/chrootAlpine")
        // Must remain installed = false, dirExists = true
        org.junit.Assert.assertFalse("Leftover dir must NOT have installed=true", snap.size.installed)
        assertTrue("dirExists must be true", snap.size.dirExists)
        assertEquals("PRESENT", ChrootSettingsModel.resolveStatus(snap.size.installed, snap.size.dirExists))
    }

    @Test
    fun runMultiPathKill_cancellationStopsSchedulingSubsequentPaths() {
        val paths = listOf(
            "/data/local/tmp/chrootDebian13",
            "/data/local/tmp/chrootAlpine",
            "/data/local/tmp/chrootFedora",
            "/data/local/tmp/chrootArch"
        )
        val executedPaths = mutableListOf<String>()
        var cancelRequested = false

        val (results, wasCancelled) = ChrootSettingsModel.runMultiPathKill(
            validPaths = paths,
            isCancelled = { cancelRequested },
            onProgress = { current, total ->
                // Trigger cancel after first path finishes
                if (current == 1) {
                    cancelRequested = true
                }
            },
            killSingle = { path ->
                executedPaths.add(path)
                ChrootProcessManager.KillResult(
                    killed = 1,
                    failed = 0,
                    remaining = emptyList(),
                    verifiedClean = true,
                    raw = "",
                    rootOk = true
                )
            }
        )

        // Path 1 executed, paths 2, 3, 4 were never scheduled!
        assertEquals(1, executedPaths.size)
        assertEquals("/data/local/tmp/chrootDebian13", executedPaths[0])
        assertEquals(1, results.size)
        assertTrue("wasCancelled must be true", wasCancelled)
    }

    @Test
    fun isKillEnabled_enabledWhenRootOkAndNotBusy_regardlessOfProcessCount() {
        // rootOk=true, busy=false (even if procCount is unmeasured -1 or 0) -> enabled=true
        assertTrue(ChrootSettingsModel.isKillEnabled(rootOk = true, busy = false))

        // rootOk=false, busy=false (even if procCount > 0) -> enabled=false
        org.junit.Assert.assertFalse(ChrootSettingsModel.isKillEnabled(rootOk = false, busy = false))

        // rootOk=true, busy=true (operation in progress) -> enabled=false
        org.junit.Assert.assertFalse(ChrootSettingsModel.isKillEnabled(rootOk = true, busy = true))

        // rootOk=false, busy=true -> enabled=false
        org.junit.Assert.assertFalse(ChrootSettingsModel.isKillEnabled(rootOk = false, busy = true))
    }
}
