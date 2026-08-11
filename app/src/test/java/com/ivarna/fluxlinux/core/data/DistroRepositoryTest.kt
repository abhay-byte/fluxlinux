package com.ivarna.fluxlinux.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catalog tests: Termux Native card dropped; only Debian + Debian Rooted are
 * installable (comingSoon = false) cards.
 */
class DistroRepositoryTest {

    @Test
    fun noTermuxNativeCard() {
        assertFalse(DistroRepository.supportedDistros.any { it.id == "termux" })
    }

    @Test
    fun onlyDebianCardsAvailable() {
        val available = DistroRepository.supportedDistros.filter { !it.comingSoon }.map { it.id }
        assertTrue(available.contains("debian"))
        assertTrue(available.contains("debian13_chroot"))
        assertTrue(available.size == 2)
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
