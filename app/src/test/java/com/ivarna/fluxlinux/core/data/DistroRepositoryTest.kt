package com.ivarna.fluxlinux.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catalog tests: Termux Native card dropped; installable cards are
 * Debian + Alpine (proot + rooted each).
 */
class DistroRepositoryTest {

    @Test
    fun noTermuxNativeCard() {
        assertFalse(DistroRepository.supportedDistros.any { it.id == "termux" })
    }

    @Test
    fun installableCards_includeDebianAndAlpine() {
        val available = DistroRepository.supportedDistros.filter { !it.comingSoon }.map { it.id }
        assertTrue(available.contains("debian"))
        assertTrue(available.contains("debian13_chroot"))
        assertTrue(available.contains("alpine"))
        assertTrue(available.contains("alpine_chroot"))
        assertTrue(available.contains("fedora"))
        assertTrue(available.contains("fedora_chroot"))
        assertTrue(available.contains("void"))
        assertTrue(available.contains("void_chroot"))
        assertTrue(available.contains("opensuse"))
        assertTrue(available.contains("opensuse_chroot"))
        assertEquals(10, available.size)
    }

    @Test
    fun alpineIsProotOnly() {
        val alpine = DistroRepository.supportedDistros.first { it.id == "alpine" }
        assertTrue(alpine.prootSupported)
        assertFalse(alpine.chrootSupported)
    }

    @Test
    fun alpineChrootIsChrootOnly() {
        val chroot = DistroRepository.supportedDistros.first { it.id == "alpine_chroot" }
        assertFalse(chroot.prootSupported)
        assertTrue(chroot.chrootSupported)
    }

    @Test
    fun noComponentReferencesTermuxScripts() {
        val scripts = DistroRepository.supportedDistros
            .flatMap { it.components }
            .map { it.scriptName }
        assertFalse(scripts.any { it.startsWith("termux/") })
    }

    @Test
    fun debianIsProotOnly() {
        val debian = DistroRepository.supportedDistros.first { it.id == "debian" }
        assertTrue(debian.prootSupported)
        assertFalse(debian.chrootSupported)
    }

    @Test
    fun debian13ChrootIsChrootOnly() {
        val chroot = DistroRepository.supportedDistros.first { it.id == "debian13_chroot" }
        assertFalse(chroot.prootSupported)
        assertTrue(chroot.chrootSupported)
    }
}
