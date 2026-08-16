package com.ivarna.fluxlinux.core.chroot

import com.ivarna.fluxlinux.core.utils.FakePrefsContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChrootInfoStoreTest {

    @Test
    fun formatStorageBytes_formattingContract() {
        assertEquals("—" to "", ChrootInfoStore.formatStorageBytes(null))
        assertEquals("—" to "", ChrootInfoStore.formatStorageBytes(-1L))
        assertEquals("512" to "B", ChrootInfoStore.formatStorageBytes(512L))
        assertEquals("100" to "KB", ChrootInfoStore.formatStorageBytes(100L * 1024L))
        assertEquals("100" to "MB", ChrootInfoStore.formatStorageBytes(100L * 1024L * 1024L))
        assertEquals("2.8" to "GB", ChrootInfoStore.formatStorageBytes((2.8 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun migration_unscopedKeysReadAsDebianWhenScopedAbsent() {
        val ctx = FakePrefsContext()
        val legacyBytes = 3_000_000_000L
        val legacyMs = 123456789L
        val legacyProcCount = 4
        val legacyProcMs = 987654321L

        // Populate legacy unscoped keys
        ctx.prefs.edit()
            .putLong("chroot_size_bytes", legacyBytes)
            .putLong("chroot_last_ms", legacyMs)
            .putBoolean("chroot_root_ok", true)
            .putBoolean("chroot_installed", true)
            .putInt("chroot_proc_count", legacyProcCount)
            .putLong("chroot_proc_last_ms", legacyProcMs)
            .apply()

        // Read via Debian ID -> should fall back to legacy keys
        assertTrue(ChrootInfoStore.hasCache(ctx, "debian13_chroot"))
        assertEquals(legacyBytes, ChrootInfoStore.cachedBytes(ctx, "debian13_chroot"))
        assertEquals(legacyMs, ChrootInfoStore.cachedLastMs(ctx, "debian13_chroot"))
        assertTrue(ChrootInfoStore.cachedRootOk(ctx, "debian13_chroot"))
        assertTrue(ChrootInfoStore.cachedInstalled(ctx, "debian13_chroot"))
        assertTrue(ChrootInfoStore.hasProcCache(ctx, "debian13_chroot"))
        assertEquals(legacyProcCount, ChrootInfoStore.cachedProcCount(ctx, "debian13_chroot"))
        assertEquals(legacyProcMs, ChrootInfoStore.cachedProcLastMs(ctx, "debian13_chroot"))

        // Read via non-Debian ID (e.g. alpine_chroot) -> must NOT fall back to legacy keys
        assertFalse(ChrootInfoStore.hasCache(ctx, "alpine_chroot"))
        assertNull(ChrootInfoStore.cachedBytes(ctx, "alpine_chroot"))
        assertFalse(ChrootInfoStore.cachedInstalled(ctx, "alpine_chroot"))
        assertFalse(ChrootInfoStore.hasProcCache(ctx, "alpine_chroot"))
        assertEquals(-1, ChrootInfoStore.cachedProcCount(ctx, "alpine_chroot"))
    }

    @Test
    fun scopedWrites_doNotDeleteUnscopedKeys() {
        val ctx = FakePrefsContext()
        val legacyBytes = 2_000_000_000L

        ctx.prefs.edit()
            .putLong("chroot_size_bytes", legacyBytes)
            .putLong("chroot_last_ms", 1000L)
            .apply()

        // Write scoped Debian info
        val newBytes = 4_000_000_000L
        ChrootInfoStore.saveInstallInfo(
            ctx,
            distroId = "debian13_chroot",
            installed = true,
            dirExists = true,
            bytes = newBytes,
            rootOk = true,
            viaRoot = true
        )

        // Scoped key has new value
        assertEquals(newBytes, ChrootInfoStore.cachedBytes(ctx, "debian13_chroot"))

        // Legacy key is preserved
        assertEquals(legacyBytes, ctx.prefs.getLong("chroot_size_bytes", -1L))
    }
}
