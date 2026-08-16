package com.ivarna.fluxlinux.core.legacy

import com.ivarna.fluxlinux.core.utils.FakePrefsContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegacyTermuxCallbackTest {

    @Test
    fun store_saveLoadRemove_inMemoryPrefs() {
        val ctx = FakePrefsContext(File("."))
        var scan = LegacyTermuxStore.load(ctx)
        assertEquals(0, scan.rows.size)
        assertFalse(LegacyTermuxStore.isPingOk(ctx))

        val rows = listOf(
            LegacyTermuxStore.Row(
                id = "debian",
                bytes = 2147483648L,
                layout = "installed-rootfs",
                hostPath = "/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/debian"
            ),
            LegacyTermuxStore.Row(
                id = "ubuntu",
                bytes = 1048576L,
                layout = "containers",
                hostPath = "/data/data/com.termux/files/usr/var/lib/proot-distro/containers/ubuntu"
            )
        )

        LegacyTermuxStore.saveScan(ctx, rows, 12345L)
        scan = LegacyTermuxStore.load(ctx)
        assertEquals(2, scan.rows.size)
        assertEquals("debian", scan.rows[0].id)
        assertEquals(2147483648L, scan.rows[0].bytes)
        assertEquals("ubuntu", scan.rows[1].id)
        assertEquals(1048576L, scan.rows[1].bytes)
        assertEquals(12345L, scan.scannedAtMs)

        // remove unsafe id
        val unsafeRemoved = LegacyTermuxStore.remove(ctx, "debian;rm")
        assertFalse(unsafeRemoved)
        assertEquals(2, LegacyTermuxStore.load(ctx).rows.size)

        // remove valid id
        val removed = LegacyTermuxStore.remove(ctx, "debian")
        assertTrue(removed)
        val afterRemove = LegacyTermuxStore.load(ctx)
        assertEquals(1, afterRemove.rows.size)
        assertEquals("ubuntu", afterRemove.rows[0].id)

        // pingOk
        LegacyTermuxStore.setPingOk(ctx)
        assertTrue(LegacyTermuxStore.isPingOk(ctx))
    }

    @Test
    fun handleCallback_listSuccess_twoRows() {
        val ctx = FakePrefsContext(File("."))
        val params = mapOf(
            "ids" to "debian,ubuntu",
            "bytes" to "2147483648,1048576",
            "layouts" to "installed-rootfs,containers"
        )

        LegacyTermuxCallbacks.handle(ctx, "success", "legacy_termux_list") { params[it] }

        val scan = LegacyTermuxStore.load(ctx)
        assertEquals(2, scan.rows.size)
        assertEquals("debian", scan.rows[0].id)
        assertEquals("installed-rootfs", scan.rows[0].layout)
        assertEquals("/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/debian", scan.rows[0].hostPath)
        assertEquals("ubuntu", scan.rows[1].id)
        assertEquals("containers", scan.rows[1].layout)
        assertEquals("/data/data/com.termux/files/usr/var/lib/proot-distro/containers/ubuntu", scan.rows[1].hostPath)
        assertTrue(LegacyTermuxStore.isPingOk(ctx))
    }

    @Test
    fun handleCallback_listSuccess_missingIdsEmptySuccess() {
        val ctx = FakePrefsContext(File("."))
        val params = mapOf(
            "ids" to "",
            "bytes" to "",
            "layouts" to ""
        )

        LegacyTermuxCallbacks.handle(ctx, "success", "legacy_termux_list") { params[it] }

        val scan = LegacyTermuxStore.load(ctx)
        assertEquals(0, scan.rows.size)
        assertNull(scan.error)
        assertTrue(LegacyTermuxStore.isPingOk(ctx))
    }

    @Test
    fun handleCallback_listSuccess_nullIdsKeyReturnsEmpty() {
        val ctx = FakePrefsContext(File("."))
        // Missing key entirely returns null
        LegacyTermuxCallbacks.handle(ctx, "success", "legacy_termux_list") { null }

        val scan = LegacyTermuxStore.load(ctx)
        assertEquals(0, scan.rows.size)
        assertNull(scan.error)
        assertTrue(LegacyTermuxStore.isPingOk(ctx))
    }

    @Test
    fun handleCallback_listSuccess_unknownLayoutNullHostPath() {
        val ctx = FakePrefsContext(File("."))
        val params = mapOf(
            "ids" to "alpine",
            "bytes" to "1000",
            "layouts" to "unknown_layout"
        )

        LegacyTermuxCallbacks.handle(ctx, "success", "legacy_termux_list") { params[it] }

        val scan = LegacyTermuxStore.load(ctx)
        assertEquals(1, scan.rows.size)
        assertEquals("alpine", scan.rows[0].id)
        assertNull(scan.rows[0].hostPath)
    }

    @Test
    fun handleCallback_listSuccess_badTokenRejected() {
        val ctx = FakePrefsContext(File("."))
        val params = mapOf(
            "ids" to "debian,bad;id",
            "bytes" to "100,200",
            "layouts" to "installed-rootfs,containers"
        )

        LegacyTermuxCallbacks.handle(ctx, "success", "legacy_termux_list") { params[it] }

        val scan = LegacyTermuxStore.load(ctx)
        assertEquals(0, scan.rows.size)
        assertNotNull(scan.error)
    }

    @Test
    fun handleCallback_uninstallSuccess_removesAndRecordsActionFinished() {
        val ctx = FakePrefsContext(File("."))
        val rows = listOf(
            LegacyTermuxStore.Row(id = "debian", bytes = 1000L, layout = "installed-rootfs"),
            LegacyTermuxStore.Row(id = "alpine", bytes = 500L, layout = "containers")
        )
        LegacyTermuxStore.saveScan(ctx, rows, 1000L)

        LegacyTermuxCallbacks.handle(ctx, "success", "legacy_termux_uninstall_debian") { null }

        val scan = LegacyTermuxStore.load(ctx)
        assertEquals(1, scan.rows.size)
        assertEquals("alpine", scan.rows[0].id)
        assertTrue(scan.lastActionMs > 0L)
    }

    @Test
    fun handleCallback_uninstallError_recordsErrorAndActionFinished() {
        val ctx = FakePrefsContext(File("."))
        val rows = listOf(
            LegacyTermuxStore.Row(id = "debian", bytes = 1000L, layout = "installed-rootfs")
        )
        LegacyTermuxStore.saveScan(ctx, rows, 1000L)

        val params = mapOf("reason" to "container_busy")
        LegacyTermuxCallbacks.handle(ctx, "error", "legacy_termux_uninstall_debian") { params[it] }

        val scan = LegacyTermuxStore.load(ctx)
        // Row is still preserved on error
        assertEquals(1, scan.rows.size)
        assertEquals("debian", scan.rows[0].id)
        assertNotNull(scan.error)
        assertTrue(scan.error!!.contains("container_busy"))
        assertTrue(scan.lastActionMs > 0L)
    }

    @Test
    fun handleCallback_distroUninstall_ignored() {
        val ctx = FakePrefsContext(File("."))

        // Should be completely ignored by LegacyTermuxCallbacks because prefix is not legacy_termux_
        LegacyTermuxCallbacks.handle(ctx, "success", "distro_uninstall_debian") { null }

        val scan = LegacyTermuxStore.load(ctx)
        assertEquals(0, scan.rows.size)
        assertFalse(LegacyTermuxStore.isPingOk(ctx))
    }
}
