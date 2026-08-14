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
        assertTrue(names.contains(DistroInstallProfile.DEEPIN_ROOTFS_NAME))
        assertTrue(names.contains(DistroInstallProfile.CHIMERA_ROOTFS_NAME))
        assertTrue(names.contains(DistroInstallProfile.MANJARO_ROOTFS_NAME))
        assertTrue(names.contains(DistroInstallProfile.UBUNTU_ROOTFS_NAME))
        assertTrue(names.contains(DistroInstallProfile.KALI_ROOTFS_NAME))
        assertTrue(names.contains(DistroInstallProfile.PARROT_ROOTFS_NAME))
        assertTrue(names.contains(DistroInstallProfile.ARCH_ROOTFS_NAME))
        assertEquals(12, names.size)
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
        assertTrue(DistroInstallProfile.isInstallable("deepin"))
        assertTrue(DistroInstallProfile.isInstallable("deepin_chroot"))
        assertTrue(DistroInstallProfile.isInstallable("chimera"))
        assertTrue(DistroInstallProfile.isInstallable("chimera_chroot"))
        assertTrue(DistroInstallProfile.isInstallable("manjaro"))
        assertTrue(DistroInstallProfile.isInstallable("manjaro_chroot"))
        assertTrue(DistroInstallProfile.isInstallable("ubuntu"))
        assertTrue(DistroInstallProfile.isInstallable("ubuntu_chroot"))
        assertTrue(DistroInstallProfile.isInstallable("kali"))
        assertTrue(DistroInstallProfile.isInstallable("kali_chroot"))
        assertTrue(DistroInstallProfile.isInstallable("parrot"))
        assertTrue(DistroInstallProfile.isInstallable("parrot_chroot"))
        assertTrue(DistroInstallProfile.isInstallable("archlinux"))
        assertTrue(DistroInstallProfile.isInstallable("archlinux_chroot"))
        assertFalse(DistroInstallProfile.isInstallable("openbsd"))
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
            DistroInstallProfile.OPENSUSE_ROOTFS_SHA256,
            DistroInstallProfile.DEEPIN_ROOTFS_SHA256,
            DistroInstallProfile.CHIMERA_ROOTFS_SHA256,
            DistroInstallProfile.MANJARO_ROOTFS_SHA256,
            DistroInstallProfile.UBUNTU_ROOTFS_SHA256,
            DistroInstallProfile.KALI_ROOTFS_SHA256,
            DistroInstallProfile.PARROT_ROOTFS_SHA256,
            DistroInstallProfile.ARCH_ROOTFS_SHA256
        ).forEach { sha ->
            assertEquals(64, sha.length)
            assertTrue(sha.matches(Regex("[0-9a-f]{64}")))
        }
    }

    @Test
    fun deepin_proot_chroot_profiles() {
        val proot = DistroInstallProfile.require("deepin")
        assertEquals("proot", proot.method)
        assertEquals("deepin", proot.prootName)
        assertEquals(DistroInstallProfile.DEEPIN_ROOTFS_NAME, proot.rootfsFileName)
        assertFalse(proot.rootfsAsset.endsWith(".gz"))
        assertTrue(proot.rootfsMinBytes >= 40L * 1024L * 1024L)
        assertTrue(proot.familyScript.contains("deepin"))
        assertEquals("common/setup/setup_customization_xfce.sh", proot.customizationScript)
        assertNotNull(proot.hwAccelScript)

        val chroot = DistroInstallProfile.require("deepin_chroot")
        assertEquals("chroot", chroot.method)
        assertEquals(ChrootPaths.DEEPIN_CHROOT_PATH, chroot.chrootPath)
        assertEquals("start_guest_gui.sh", chroot.chrootStartGuiScript)
        assertTrue(chroot.chrootSetupAsset!!.contains("setup_guest_chroot"))
        assertEquals(chroot.rootfsSha256, proot.rootfsSha256)
    }

    @Test
    fun chimera_proot_chroot_profiles() {
        val proot = DistroInstallProfile.require("chimera")
        assertEquals("proot", proot.method)
        assertEquals("chimera", proot.prootName)
        assertEquals(DistroInstallProfile.CHIMERA_ROOTFS_NAME, proot.rootfsFileName)
        assertFalse(proot.rootfsAsset.endsWith(".gz"))
        assertTrue(proot.rootfsMinBytes >= 4L * 1024L * 1024L)
        assertTrue(proot.familyScript.contains("chimera"))
        assertEquals("common/setup/setup_customization_xfce.sh", proot.customizationScript)
        assertNotNull(proot.hwAccelScript)

        val chroot = DistroInstallProfile.require("chimera_chroot")
        assertEquals("chroot", chroot.method)
        assertEquals(ChrootPaths.CHIMERA_CHROOT_PATH, chroot.chrootPath)
        assertEquals("start_guest_gui.sh", chroot.chrootStartGuiScript)
        assertTrue(chroot.chrootSetupAsset!!.contains("setup_guest_chroot"))
    }

    @Test
    fun manjaro_proot_chroot_profiles() {
        val proot = DistroInstallProfile.require("manjaro")
        assertEquals("proot", proot.method)
        assertEquals("manjaro", proot.prootName)
        assertEquals(DistroInstallProfile.MANJARO_ROOTFS_NAME, proot.rootfsFileName)
        assertFalse(proot.rootfsAsset.endsWith(".gz"))
        assertTrue(proot.rootfsMinBytes >= 80L * 1024L * 1024L)
        assertTrue(proot.familyScript.contains("manjaro"))
        assertEquals("common/setup/setup_customization_xfce.sh", proot.customizationScript)
        assertNotNull(proot.hwAccelScript)

        val chroot = DistroInstallProfile.require("manjaro_chroot")
        assertEquals("chroot", chroot.method)
        assertEquals(ChrootPaths.MANJARO_CHROOT_PATH, chroot.chrootPath)
        assertEquals("start_guest_gui.sh", chroot.chrootStartGuiScript)
        assertTrue(chroot.chrootSetupAsset!!.contains("setup_guest_chroot"))
    }

    @Test
    fun allInstallable_have_hwAccelScript() {
        DistroInstallProfile.allInstallable().forEach { p ->
            assertNotNull("${p.distroId} missing hwAccelScript", p.hwAccelScript)
            assertTrue(
                "${p.distroId} hwAccelScript should be guest SSOT",
                p.hwAccelScript!!.contains("setup_hw_accel_guest")
            )
        }
    }

    @Test
    fun debian_and_alpine_have_hwAccelScript() {
        assertNotNull(DistroInstallProfile.require("debian").hwAccelScript)
        assertNotNull(DistroInstallProfile.require("debian13_chroot").hwAccelScript)
        assertNotNull(DistroInstallProfile.require("alpine").hwAccelScript)
        assertNotNull(DistroInstallProfile.require("alpine_chroot").hwAccelScript)
    }

    @Test
    fun allInstallable_includes_six_new_cards() {
        val ids = DistroInstallProfile.allInstallable().map { it.distroId }.toSet()
        listOf(
            "deepin", "deepin_chroot",
            "chimera", "chimera_chroot",
            "manjaro", "manjaro_chroot"
        ).forEach { assertTrue(ids.contains(it)) }
        assertEquals(24, ids.size)
    }

    @Test
    fun ubuntu_kali_parrot_arch_proot_chroot_profiles() {
        val pairs = listOf(
            Triple("ubuntu", DistroInstallProfile.UBUNTU_ROOTFS_NAME, ChrootPaths.UBUNTU_CHROOT_PATH),
            Triple("kali", DistroInstallProfile.KALI_ROOTFS_NAME, ChrootPaths.KALI_CHROOT_PATH),
            Triple("parrot", DistroInstallProfile.PARROT_ROOTFS_NAME, ChrootPaths.PARROT_CHROOT_PATH),
            Triple("archlinux", DistroInstallProfile.ARCH_ROOTFS_NAME, ChrootPaths.ARCH_CHROOT_PATH)
        )
        pairs.forEach { (id, rootfs, chrootPath) ->
            val proot = DistroInstallProfile.require(id)
            assertEquals("proot", proot.method)
            assertEquals(id, proot.prootName)
            assertEquals(rootfs, proot.rootfsFileName)
            assertFalse("$id rootfs must not be .gz (aapt2)", proot.rootfsAsset.endsWith(".gz"))
            assertTrue(proot.familyScript.contains(if (id == "archlinux") "arch" else id))
            assertEquals("common/setup/setup_customization_xfce.sh", proot.customizationScript)
            assertNotNull(proot.hwAccelScript)

            val chroot = DistroInstallProfile.require("${id}_chroot")
            assertEquals("chroot", chroot.method)
            assertEquals(chrootPath, chroot.chrootPath)
            assertEquals("start_guest_gui.sh", chroot.chrootStartGuiScript)
            assertTrue(chroot.chrootSetupAsset!!.contains("setup_guest_chroot"))
            assertEquals(chroot.rootfsSha256, proot.rootfsSha256)
        }
        assertTrue(DistroInstallProfile.require("ubuntu").rootfsMinBytes >= 15L * 1024L * 1024L)
        assertTrue(DistroInstallProfile.require("kali").rootfsMinBytes >= 40L * 1024L * 1024L)
        assertTrue(DistroInstallProfile.require("parrot").rootfsMinBytes >= 30L * 1024L * 1024L)
        assertTrue(DistroInstallProfile.require("archlinux").rootfsMinBytes >= 40L * 1024L * 1024L)
        assertTrue(
            DistroInstallProfile.require("ubuntu").rootfsFileName.endsWith(".tar.xz")
        )
        assertFalse(DistroInstallProfile.UBUNTU_ROOTFS_NAME.endsWith(".gz"))
        assertFalse(DistroInstallProfile.KALI_ROOTFS_NAME.contains("kali-arm64"))
        assertFalse(DistroInstallProfile.PARROT_ROOTFS_NAME.contains("parrot-arm64"))
    }

    @Test
    fun allInstallable_includes_ukpa_cards() {
        val ids = DistroInstallProfile.allInstallable().map { it.distroId }.toSet()
        listOf(
            "ubuntu", "ubuntu_chroot",
            "kali", "kali_chroot",
            "parrot", "parrot_chroot",
            "archlinux", "archlinux_chroot"
        ).forEach { assertTrue(ids.contains(it)) }
        assertEquals(24, ids.size)
    }
}
