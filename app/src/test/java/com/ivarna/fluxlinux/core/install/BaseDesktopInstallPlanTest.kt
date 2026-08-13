package com.ivarna.fluxlinux.core.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pure onboarding plan logic (no rootfs / no assets).
 * Payload builders need ScriptManager + assets — covered on device only.
 */
class BaseDesktopInstallPlanTest {

    @Test
    fun distroById_debian_found() {
        val d = BaseDesktopInstallPlan.distroById("debian")
        assertNotNull(d)
        assertEquals("debian", d!!.id)
        assertTrue(d.prootSupported)
    }

    @Test
    fun distroById_debianChroot_found() {
        val d = BaseDesktopInstallPlan.distroById("debian13_chroot")
        assertNotNull(d)
        assertEquals("debian13_chroot", d!!.id)
        assertTrue(d.chrootSupported)
    }

    @Test
    fun distroById_unknown_returnsNull() {
        assertNull(BaseDesktopInstallPlan.distroById("not-a-distro"))
        assertNull(BaseDesktopInstallPlan.distroById(""))
    }

    @Test
    fun methodFor_debian_isProot() {
        assertEquals("proot", BaseDesktopInstallPlan.methodFor("debian"))
    }

    @Test
    fun methodFor_debianChroot_isChroot() {
        assertEquals("chroot", BaseDesktopInstallPlan.methodFor("debian13_chroot"))
        assertEquals("chroot", BaseDesktopInstallPlan.methodFor("debian_chroot"))
    }

    @Test
    fun methodFor_unknown_fallsBackToProot() {
        // terminalComponentFor throws → methodFor catches and returns "proot"
        assertEquals("proot", BaseDesktopInstallPlan.methodFor("totally_unknown_distro"))
    }

    @Test
    fun phasesFor_proot_hasHostRootfsCustom() {
        val phases = BaseDesktopInstallPlan.phasesFor("proot")
        assertEquals(listOf("HOST", "ROOTFS", "CUSTOM"), phases.map { it.id })
        assertTrue(phases.all { it.weight > 0 })
        assertEquals(100, phases.sumOf { it.weight })
    }

    @Test
    fun phasesFor_chroot_hasRootCheckFirst() {
        val phases = BaseDesktopInstallPlan.phasesFor("chroot")
        assertEquals(listOf("R0", "HOST", "ROOTFS", "XFCE", "CUSTOM"), phases.map { it.id })
        assertEquals(100, phases.sumOf { it.weight })
    }

    @Test
    fun phasesFor_unknownMethod_treatedAsProotFamily() {
        // Non-chroot methods use proot phase list (install runner only branches on "chroot").
        val phases = BaseDesktopInstallPlan.phasesFor("something-else")
        assertEquals(listOf("HOST", "ROOTFS", "CUSTOM"), phases.map { it.id })
    }

    @Test
    fun scriptConstants_pointAtDebianFamilyPaths() {
        assertTrue(BaseDesktopInstallPlan.FAMILY_SCRIPT.contains("setup_debian_family"))
        assertTrue(BaseDesktopInstallPlan.CUSTOMIZATION_SCRIPT.contains("setup_customization_debian"))
    }

    @Test
    fun distroById_alpine_found() {
        val d = BaseDesktopInstallPlan.distroById("alpine")
        assertNotNull(d)
        assertEquals("alpine", d!!.id)
        assertTrue(d.prootSupported)
        assertEquals(false, d.comingSoon)
    }

    @Test
    fun distroById_alpineChroot_found() {
        val d = BaseDesktopInstallPlan.distroById("alpine_chroot")
        assertNotNull(d)
        assertEquals("alpine_chroot", d!!.id)
        assertTrue(d.chrootSupported)
    }

    @Test
    fun methodFor_alpine() {
        assertEquals("proot", BaseDesktopInstallPlan.methodFor("alpine"))
        assertEquals("chroot", BaseDesktopInstallPlan.methodFor("alpine_chroot"))
    }

    @Test
    fun phasesFor_alpineDisplayName() {
        val phases = BaseDesktopInstallPlan.phasesFor("proot", "Alpine")
        assertTrue(phases.any { it.label.contains("Alpine") })
        assertEquals(100, phases.sumOf { it.weight })
    }

    @Test
    fun profileFor_alpine() {
        val p = BaseDesktopInstallPlan.profileFor("alpine")
        assertNotNull(p)
        assertEquals("alpine", p!!.prootName)
    }

    @Test
    fun distroById_fedora_void_opensuse() {
        assertEquals("fedora", BaseDesktopInstallPlan.distroById("fedora")!!.id)
        assertEquals("void_chroot", BaseDesktopInstallPlan.distroById("void_chroot")!!.id)
        assertEquals("opensuse", BaseDesktopInstallPlan.distroById("opensuse")!!.id)
        assertEquals("proot", BaseDesktopInstallPlan.methodFor("fedora"))
        assertEquals("chroot", BaseDesktopInstallPlan.methodFor("opensuse_chroot"))
        assertEquals("void", BaseDesktopInstallPlan.profileFor("void")!!.prootName)
    }

    @Test
    fun terminalComponentFor_rejectsUnknownDistro() {
        try {
            com.ivarna.fluxlinux.core.data.terminalComponentFor("nope")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("unsupported"))
        }
    }
}
