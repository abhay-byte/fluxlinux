package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Grid availability / fail-closed gating (proot missing, chroot needs root, host always shown).
 * Uses [TerminalShellAvailability] directly so tests stay pure (no real rootfs probe).
 */
class TerminalShellCatalogTest {

    private fun ctx(): FakeContext {
        val dir = File(System.getProperty("java.io.tmpdir"), "flux_tsc_${System.nanoTime()}")
        dir.mkdirs()
        return FakeContext(dir, "$dir/jni")
    }

    @Test
    fun prootNotInstalled_bothCardsDisabledWithInstallReason() {
        val avail = TerminalShellAvailability(
            prootInstalled = false,
            chrootInstalled = false,
            rootAvailable = false
        )
        val sections = TerminalShellCatalog.sections(ctx(), avail)
        val proot = sections.first { it.subtitle == "PROOT" }.cards
        assertEquals(2, proot.size)
        proot.forEach { card ->
            assertFalse(card.enabled)
            assertEquals("Install Debian in Distros", card.disabledReason)
        }
    }

    @Test
    fun prootInstalled_bothCardsEnabled() {
        val avail = TerminalShellAvailability(
            prootInstalled = true,
            chrootInstalled = false,
            rootAvailable = false
        )
        val proot = TerminalShellCatalog.sections(ctx(), avail)
            .first { it.subtitle == "PROOT" }.cards
        assertTrue(proot.all { it.enabled })
        assertTrue(proot.all { it.disabledReason == null })
        assertEquals("shell", proot[0].def.type)
        assertEquals("proot", proot[0].def.method)
        assertEquals("shell-root", proot[1].def.type)
        assertEquals("proot", proot[1].def.method)
    }

    @Test
    fun chrootInstalled_noRoot_allDisabledRootRequired() {
        val avail = TerminalShellAvailability(
            prootInstalled = false,
            chrootInstalled = true,
            rootAvailable = false
        )
        val chroot = TerminalShellCatalog.sections(ctx(), avail)
            .first { it.subtitle == "CHROOT" }.cards
        assertEquals(2, chroot.size)
        chroot.forEach { card ->
            assertFalse(card.enabled)
            assertEquals("Root required", card.disabledReason)
            assertEquals("chroot", card.def.method)
        }
    }

    @Test
    fun chrootMissing_disabledChrootNotInstalled() {
        val avail = TerminalShellAvailability(
            prootInstalled = true,
            chrootInstalled = false,
            rootAvailable = true
        )
        val chroot = TerminalShellCatalog.sections(ctx(), avail)
            .first { it.subtitle == "CHROOT" }.cards
        chroot.forEach { card ->
            assertFalse(card.enabled)
            assertEquals("Chroot not installed", card.disabledReason)
        }
    }

    @Test
    fun chrootInstalledAndRoot_allEnabledReasonNull() {
        val avail = TerminalShellAvailability(
            prootInstalled = false,
            chrootInstalled = true,
            rootAvailable = true
        )
        val chroot = TerminalShellCatalog.sections(ctx(), avail)
            .first { it.subtitle == "CHROOT" }.cards
        chroot.forEach { card ->
            assertTrue(card.enabled)
            assertNull(card.disabledReason)
        }
    }

    @Test
    fun hostCard_alwaysPresentAndEnabled() {
        val avail = TerminalShellAvailability(
            prootInstalled = false,
            chrootInstalled = false,
            rootAvailable = false
        )
        val host = TerminalShellCatalog.sections(ctx(), avail)
            .first { it.subtitle == "OPTIONAL" }.cards
        assertEquals(1, host.size)
        assertEquals("host", host[0].def.type)
        assertEquals("host", host[0].def.method)
        assertTrue(host[0].enabled)
        assertNull(host[0].disabledReason)
    }

    @Test
    fun sectionsOrder_prootThenChrootThenHost() {
        val sections = TerminalShellCatalog.sections(
            ctx(),
            TerminalShellAvailability(true, true, true)
        )
        assertEquals(listOf("PROOT", "CHROOT", "OPTIONAL"), sections.map { it.subtitle })
        assertEquals("DEBIAN SHELL", sections[0].title)
        assertEquals("DEBIAN SHELL", sections[1].title)
        assertEquals("HOST", sections[2].title)
    }

    @Test
    fun prootDefs_shellAndShellRoot() {
        val defs = TerminalShellCatalog.prootDefs()
        assertEquals(listOf("shell", "shell-root"), defs.map { it.type })
        assertTrue(defs.all { it.method == "proot" })
    }

    @Test
    fun chrootDefs_shellAndShellRoot() {
        val defs = TerminalShellCatalog.chrootDefs()
        assertEquals(listOf("shell", "shell-root"), defs.map { it.type })
        assertTrue(defs.all { it.method == "chroot" })
    }
}
