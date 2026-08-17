package com.ivarna.fluxlinux.core.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChrootPathsTest {

    @Test
    fun helper_version_is_v29() {
        assertEquals("fluxlinux-chroot v2.9", ChrootPaths.CHROOT_HELPER_VERSION)
    }

    @Test
    fun helper_fluxLoginPrefersRunuserThenSetuidgid() {
        val cwd = java.io.File("").absoluteFile
        val candidates = listOf(
            java.io.File(cwd, "src/main/assets/scripts/chroot/fluxlinux_chroot.sh"),
            java.io.File(cwd, "app/src/main/assets/scripts/chroot/fluxlinux_chroot.sh")
        )
        val text = candidates.first { it.isFile }.readText()
        assertTrue("guest_login_user must exist", text.contains("guest_login_user()"))
        assertTrue("runuser first (start_guest_gui parity)", text.contains("guest_bin_path runuser"))
        assertTrue("numeric setuidgid fallback", text.contains("setuidgid"))
        assertFalse("GNU chroot --userspec is not supported on Android busybox", text.contains("chroot --userspec"))
        assertFalse(
            "flux login must not hardcode /bin/su only",
            text.contains("guest_chroot_env /bin/su - \"\$USER_NAME\"")
        )
    }

    @Test
    fun debian_and_alpine_paths_differ() {
        assertNotEquals(ChrootPaths.DEBIAN_CHROOT_PATH, ChrootPaths.ALPINE_CHROOT_PATH)
        assertEquals(ChrootPaths.DEBIAN_CHROOT_PATH, ChrootPaths.CHROOT_PATH)
    }

    @Test
    fun pathForDistro() {
        assertEquals(
            ChrootPaths.ALPINE_CHROOT_PATH,
            ChrootPaths.pathForDistro("alpine_chroot")
        )
        assertEquals(
            ChrootPaths.DEBIAN_CHROOT_PATH,
            ChrootPaths.pathForDistro("debian13_chroot")
        )
        assertEquals(
            ChrootPaths.FEDORA_CHROOT_PATH,
            ChrootPaths.pathForDistro("fedora_chroot")
        )
        assertEquals(
            ChrootPaths.VOID_CHROOT_PATH,
            ChrootPaths.pathForDistro("void_chroot")
        )
        assertEquals(
            ChrootPaths.OPENSUSE_CHROOT_PATH,
            ChrootPaths.pathForDistro("opensuse_chroot")
        )
        assertEquals(
            ChrootPaths.DEEPIN_CHROOT_PATH,
            ChrootPaths.pathForDistro("deepin_chroot")
        )
        assertEquals(
            ChrootPaths.CHIMERA_CHROOT_PATH,
            ChrootPaths.pathForDistro("chimera_chroot")
        )
        assertEquals(
            ChrootPaths.MANJARO_CHROOT_PATH,
            ChrootPaths.pathForDistro("manjaro_chroot")
        )
        assertEquals(
            ChrootPaths.UBUNTU_CHROOT_PATH,
            ChrootPaths.pathForDistro("ubuntu_chroot")
        )
        assertEquals(
            ChrootPaths.KALI_CHROOT_PATH,
            ChrootPaths.pathForDistro("kali_chroot")
        )
        assertEquals(
            ChrootPaths.PARROT_CHROOT_PATH,
            ChrootPaths.pathForDistro("parrot_chroot")
        )
        assertEquals(
            ChrootPaths.ARCH_CHROOT_PATH,
            ChrootPaths.pathForDistro("archlinux_chroot")
        )
    }

    @Test
    fun new_chroot_paths_are_distinct() {
        val paths = setOf(
            ChrootPaths.DEBIAN_CHROOT_PATH,
            ChrootPaths.ALPINE_CHROOT_PATH,
            ChrootPaths.FEDORA_CHROOT_PATH,
            ChrootPaths.VOID_CHROOT_PATH,
            ChrootPaths.OPENSUSE_CHROOT_PATH,
            ChrootPaths.DEEPIN_CHROOT_PATH,
            ChrootPaths.CHIMERA_CHROOT_PATH,
            ChrootPaths.MANJARO_CHROOT_PATH,
            ChrootPaths.UBUNTU_CHROOT_PATH,
            ChrootPaths.KALI_CHROOT_PATH,
            ChrootPaths.PARROT_CHROOT_PATH,
            ChrootPaths.ARCH_CHROOT_PATH
        )
        assertEquals(12, paths.size)
    }

    @Test
    fun new_paths_match_plan_contract() {
        assertEquals("/data/local/tmp/chrootDeepin", ChrootPaths.DEEPIN_CHROOT_PATH)
        assertEquals("/data/local/tmp/chrootChimera", ChrootPaths.CHIMERA_CHROOT_PATH)
        assertEquals("/data/local/tmp/chrootManjaro", ChrootPaths.MANJARO_CHROOT_PATH)
        assertEquals("/data/local/tmp/chrootUbuntu", ChrootPaths.UBUNTU_CHROOT_PATH)
        assertEquals("/data/local/tmp/chrootKali", ChrootPaths.KALI_CHROOT_PATH)
        assertEquals("/data/local/tmp/chrootParrot", ChrootPaths.PARROT_CHROOT_PATH)
        assertEquals("/data/local/tmp/chrootArch", ChrootPaths.ARCH_CHROOT_PATH)
    }
}
