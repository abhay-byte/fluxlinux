package com.ivarna.fluxlinux.core.install

import com.ivarna.fluxlinux.core.data.terminalComponentFor
import com.ivarna.fluxlinux.core.root.ChrootPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DistroInstallProfileTest {

    @Test
    fun debian_proot_profile() {
        val p = DistroInstallProfile.require("debian")
        assertEquals("proot", p.method)
        assertEquals("debian", p.prootName)
        assertEquals(DistroInstallProfile.DEBIAN_ROOTFS_NAME, p.rootfsFileName)
        assertEquals(DistroInstallProfile.DEBIAN_ROOTFS_SHA256, p.rootfsSha256)
        assertTrue(p.rootfsMinBytes >= 50L * 1024L * 1024L)
        assertTrue(p.familyScript.contains("debian"))
        assertNull(p.chrootPath)
    }

    @Test
    fun alpine_proot_profile() {
        val p = DistroInstallProfile.require("alpine")
        assertEquals("proot", p.method)
        assertEquals("alpine", p.prootName)
        assertEquals(DistroInstallProfile.ALPINE_ROOTFS_NAME, p.rootfsFileName)
        assertEquals(DistroInstallProfile.ALPINE_ROOTFS_ASSET, p.rootfsAsset)
        // Asset must not end in .gz (aapt2 auto-decompress / rename).
        assertFalse(p.rootfsAsset.endsWith(".gz"))
        assertEquals(DistroInstallProfile.ALPINE_ROOTFS_SHA256, p.rootfsSha256)
        assertTrue(p.rootfsMinBytes < DistroInstallProfile.DEBIAN_ROOTFS_MIN_BYTES)
        assertTrue(p.familyScript.contains("alpine"))
        assertTrue(p.customizationScript.contains("alpine"))
    }

    @Test
    fun alpine_chroot_profile() {
        val p = DistroInstallProfile.require("alpine_chroot")
        assertEquals("chroot", p.method)
        assertEquals(ChrootPaths.ALPINE_CHROOT_PATH, p.chrootPath)
        assertEquals("start_alpine_gui.sh", p.chrootStartGuiScript)
        assertNotNull(p.chrootSetupAsset)
        assertTrue(p.chrootSetupAsset!!.contains("setup_alpine_chroot"))
    }

    @Test
    fun debian_chroot_alias_maps() {
        val a = DistroInstallProfile.require("debian13_chroot")
        val b = DistroInstallProfile.require("debian_chroot")
        assertEquals(a.chrootPath, b.chrootPath)
        assertEquals(ChrootPaths.DEBIAN_CHROOT_PATH, a.chrootPath)
    }

    @Test
    fun allRootfsProfiles_deduped_by_file() {
        val names = DistroInstallProfile.allRootfsProfiles().map { it.rootfsFileName }.toSet()
        assertTrue(names.contains(DistroInstallProfile.DEBIAN_ROOTFS_NAME))
        assertTrue(names.contains(DistroInstallProfile.ALPINE_ROOTFS_NAME))
        assertTrue(names.contains(DistroInstallProfile.FEDORA_ROOTFS_NAME))
        assertTrue(names.contains(DistroInstallProfile.VOID_ROOTFS_NAME))
        assertTrue(names.contains(DistroInstallProfile.OPENSUSE_ROOTFS_NAME))
        assertEquals(5, names.size)
    }

    @Test
    fun methodFor_matches_terminalComponent() {
        assertEquals("proot", DistroInstallProfile.methodFor("debian"))
        assertEquals("proot", DistroInstallProfile.methodFor("alpine"))
        assertEquals("chroot", DistroInstallProfile.methodFor("debian13_chroot"))
        assertEquals("chroot", DistroInstallProfile.methodFor("alpine_chroot"))
        assertEquals(
            terminalComponentFor("alpine").method,
            DistroInstallProfile.methodFor("alpine")
        )
    }

    @Test
    fun require_unknown_throws() {
        try {
            DistroInstallProfile.require("nope")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun alpine_sha256_is_64_hex() {
        val sha = DistroInstallProfile.ALPINE_ROOTFS_SHA256
        assertEquals(64, sha.length)
        assertTrue(sha.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun isInstallable() {
        assertTrue(DistroInstallProfile.isInstallable("alpine"))
        assertTrue(DistroInstallProfile.isInstallable("alpine_chroot"))
        assertTrue(DistroInstallProfile.isInstallable("void"))
        assertTrue(DistroInstallProfile.isInstallable("fedora"))
        assertTrue(DistroInstallProfile.isInstallable("opensuse_chroot"))
        assertFalse(DistroInstallProfile.isInstallable("archlinux"))
    }

    @Test
    fun fedora_void_opensuse_proot_profiles() {
        val fedora = DistroInstallProfile.require("fedora")
        assertEquals("proot", fedora.method)
        assertEquals("fedora", fedora.prootName)
        assertEquals(DistroInstallProfile.FEDORA_ROOTFS_NAME, fedora.rootfsFileName)
        assertFalse(fedora.rootfsAsset.endsWith(".gz"))
        assertTrue(fedora.familyScript.contains("fedora"))
        assertEquals("common/setup/setup_customization_xfce.sh", fedora.customizationScript)
        assertNotNull(fedora.hwAccelScript)

        val voidp = DistroInstallProfile.require("void")
        assertEquals("void", voidp.prootName)
        assertEquals(DistroInstallProfile.VOID_ROOTFS_SHA256, voidp.rootfsSha256)

        val suse = DistroInstallProfile.require("opensuse")
        assertEquals("opensuse", suse.prootName)
        assertTrue(suse.familyScript.contains("opensuse"))
    }

    @Test
    fun fedora_void_opensuse_chroot_profiles() {
        val fedora = DistroInstallProfile.require("fedora_chroot")
        assertEquals("chroot", fedora.method)
        assertEquals(ChrootPaths.FEDORA_CHROOT_PATH, fedora.chrootPath)
        assertEquals("start_guest_gui.sh", fedora.chrootStartGuiScript)
        assertTrue(fedora.chrootSetupAsset!!.contains("setup_guest_chroot"))

        assertEquals(
            ChrootPaths.VOID_CHROOT_PATH,
            DistroInstallProfile.require("void_chroot").chrootPath
        )
        assertEquals(
            ChrootPaths.OPENSUSE_CHROOT_PATH,
            DistroInstallProfile.require("opensuse_chroot").chrootPath
        )
    }

    @Test
    fun new_rootfs_sha256_are_64_hex() {
        listOf(
            DistroInstallProfile.FEDORA_ROOTFS_SHA256,
            DistroInstallProfile.VOID_ROOTFS_SHA256,
            DistroInstallProfile.OPENSUSE_ROOTFS_SHA256
        ).forEach { sha ->
            assertEquals(64, sha.length)
            assertTrue(sha.matches(Regex("[0-9a-f]{64}")))
        }
    }
}
