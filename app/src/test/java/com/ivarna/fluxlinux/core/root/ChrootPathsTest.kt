package com.ivarna.fluxlinux.core.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChrootPathsTest {

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
            ChrootPaths.MANJARO_CHROOT_PATH
        )
        assertEquals(8, paths.size)
    }

    @Test
    fun new_paths_match_plan_contract() {
        assertEquals("/data/local/tmp/chrootDeepin", ChrootPaths.DEEPIN_CHROOT_PATH)
        assertEquals("/data/local/tmp/chrootChimera", ChrootPaths.CHIMERA_CHROOT_PATH)
        assertEquals("/data/local/tmp/chrootManjaro", ChrootPaths.MANJARO_CHROOT_PATH)
    }
}
