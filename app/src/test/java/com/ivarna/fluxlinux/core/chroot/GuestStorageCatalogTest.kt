package com.ivarna.fluxlinux.core.chroot

import com.ivarna.fluxlinux.core.root.ChrootPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestStorageCatalogTest {

    @Test
    fun installableChroots_containsExactly12InstallableIds() {
        val distros = GuestStorageCatalog.installableChroots()
        assertEquals(12, distros.size)
        val ids = distros.map { it.id }.toSet()
        assertTrue(ids.contains("debian13_chroot"))
        assertTrue(ids.contains("alpine_chroot"))
        assertTrue(ids.contains("fedora_chroot"))
        assertTrue(ids.contains("void_chroot"))
        assertTrue(ids.contains("opensuse_chroot"))
        assertTrue(ids.contains("deepin_chroot"))
        assertTrue(ids.contains("chimera_chroot"))
        assertTrue(ids.contains("manjaro_chroot"))
        assertTrue(ids.contains("ubuntu_chroot"))
        assertTrue(ids.contains("kali_chroot"))
        assertTrue(ids.contains("parrot_chroot"))
        assertTrue(ids.contains("archlinux_chroot"))
    }

    @Test
    fun installableChroots_doesNotContainAliasOrComingSoon() {
        val distros = GuestStorageCatalog.installableChroots()
        val ids = distros.map { it.id }.toSet()
        assertFalse(ids.contains("debian_chroot"))
        assertFalse(ids.contains("adelie"))
        assertFalse(ids.contains("gentoo"))
        assertFalse(ids.contains("artix"))
    }

    @Test
    fun installableProots_containsExactly12InstallableIds() {
        val distros = GuestStorageCatalog.installableProots()
        assertEquals(12, distros.size)
        val ids = distros.map { it.id }.toSet()
        assertTrue(ids.contains("debian"))
        assertTrue(ids.contains("alpine"))
        assertTrue(ids.contains("fedora"))
        assertFalse(ids.contains("debian13_chroot"))
    }

    @Test
    fun installedRows_predicateFiltersRowsCorrectly() {
        val catalog = GuestStorageCatalog.installableChroots()

        // Empty predicate -> no rows
        val emptyRows = GuestStorageCatalog.installedRows(
            catalog,
            installed = { false },
            hostPath = GuestStorageCatalog::chrootPathOrNull
        )
        assertTrue(emptyRows.isEmpty())

        // Single installed -> one row
        val singleRows = GuestStorageCatalog.installedRows(
            catalog,
            installed = { it == "debian13_chroot" },
            hostPath = GuestStorageCatalog::chrootPathOrNull
        )
        assertEquals(1, singleRows.size)
        assertEquals("debian13_chroot", singleRows[0].distroId)
        assertEquals(ChrootPaths.DEBIAN_CHROOT_PATH, singleRows[0].hostPath)
        assertEquals("chroot", singleRows[0].method)
    }

    @Test
    fun chrootPathOrNull_resolvesOnlyValidInstallableChroots() {
        val catalog = GuestStorageCatalog.installableChroots()
        val resolvedPaths = mutableSetOf<String>()

        for (d in catalog) {
            val path = GuestStorageCatalog.chrootPathOrNull(d.id)
            assertNotNull("Path must not be null for ${d.id}", path)
            assertTrue("Path $path must be in KNOWN_CHROOT_PATHS", path in GuestStorageCatalog.KNOWN_CHROOT_PATHS)
            resolvedPaths.add(path!!)
        }
        assertEquals(12, resolvedPaths.size)

        // Invalid / proot / aggregate IDs return null
        assertNull(GuestStorageCatalog.chrootPathOrNull(GuestStorageCatalog.ALL_CHROOT_ID))
        assertNull(GuestStorageCatalog.chrootPathOrNull(GuestStorageCatalog.ALL_PROOT_ID))
        assertNull(GuestStorageCatalog.chrootPathOrNull("debian")) // proot id
        assertNull(GuestStorageCatalog.chrootPathOrNull("adelie")) // coming soon
        assertNull(GuestStorageCatalog.chrootPathOrNull(""))
        assertNull(GuestStorageCatalog.chrootPathOrNull("invalid_id"))
    }

    @Test
    fun criticalPathSafety_legacyFallbackNeverBecomesKillPath() {
        // Legacy ChrootPaths.pathForDistro falls back to Debian for unknown/coming-soon IDs
        assertEquals(ChrootPaths.DEBIAN_CHROOT_PATH, ChrootPaths.pathForDistro("adelie"))

        // GuestStorageCatalog MUST reject it (returns null) so adelie never resolves to Debian kill target
        assertNull(GuestStorageCatalog.chrootPathOrNull("adelie"))
        assertNull(GuestStorageCatalog.chrootPathOrNull("centos_stream"))
    }

    @Test
    fun allowedKillPath_rejectsRefusedPathsAndTraversals() {
        // Refused root/system paths
        assertFalse(GuestStorageCatalog.allowedKillPath(""))
        assertFalse(GuestStorageCatalog.allowedKillPath("/"))
        assertFalse(GuestStorageCatalog.allowedKillPath("/data"))
        assertFalse(GuestStorageCatalog.allowedKillPath("/data/"))
        assertFalse(GuestStorageCatalog.allowedKillPath("/data/local"))
        assertFalse(GuestStorageCatalog.allowedKillPath("/data/local/"))
        assertFalse(GuestStorageCatalog.allowedKillPath("/data/local/tmp"))
        assertFalse(GuestStorageCatalog.allowedKillPath("/data/local/tmp/"))

        // Traversals
        assertFalse(GuestStorageCatalog.allowedKillPath("${ChrootPaths.DEBIAN_CHROOT_PATH}/.."))
        assertFalse(GuestStorageCatalog.allowedKillPath("/data/local/tmp/chrootUnknown"))

        // Known 12 paths are all allowed
        for (p in GuestStorageCatalog.KNOWN_CHROOT_PATHS) {
            assertTrue("Path $p should be allowed", GuestStorageCatalog.allowedKillPath(p))
        }
    }
}
