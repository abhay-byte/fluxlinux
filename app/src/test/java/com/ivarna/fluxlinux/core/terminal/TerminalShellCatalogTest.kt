package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Grid availability / fail-closed gating (Debian + Alpine proot/chroot + host).
 * Uses [TerminalShellAvailability] directly so tests stay pure (no real rootfs probe).
 */
class TerminalShellCatalogTest {

    private fun ctx(): FakeContext {
        val dir = File(System.getProperty("java.io.tmpdir"), "flux_tsc_${System.nanoTime()}")
        dir.mkdirs()
        return FakeContext(dir, "$dir/jni")
    }

    private fun avail(
        debianProot: Boolean = false,
        alpineProot: Boolean = false,
        fedoraProot: Boolean = false,
        voidProot: Boolean = false,
        opensuseProot: Boolean = false,
        debianChroot: Boolean = false,
        alpineChroot: Boolean = false,
        fedoraChroot: Boolean = false,
        voidChroot: Boolean = false,
        opensuseChroot: Boolean = false,
        rootAvailable: Boolean = false
    ) = TerminalShellAvailability(
        debianProot = debianProot,
        alpineProot = alpineProot,
        fedoraProot = fedoraProot,
        voidProot = voidProot,
        opensuseProot = opensuseProot,
        debianChroot = debianChroot,
        alpineChroot = alpineChroot,
        fedoraChroot = fedoraChroot,
        voidChroot = voidChroot,
        opensuseChroot = opensuseChroot,
        rootAvailable = rootAvailable
    )

    @Test
    fun debianProotNotInstalled_cardsDisabled() {
        val sections = TerminalShellCatalog.sections(ctx(), avail())
        val proot = sections.first { it.title == "DEBIAN SHELL" && it.subtitle == "PROOT" }.cards
        assertEquals(2, proot.size)
        proot.forEach { card ->
            assertFalse(card.enabled)
            assertEquals("Install DEBIAN in Distros", card.disabledReason)
            assertEquals("debian", card.def.distroId)
        }
    }

    @Test
    fun alpineProotInstalled_cardsEnabled() {
        val alpine = TerminalShellCatalog.sections(ctx(), avail(alpineProot = true))
            .first { it.title == "ALPINE SHELL" && it.subtitle == "PROOT" }.cards
        assertTrue(alpine.all { it.enabled })
        assertTrue(alpine.all { it.disabledReason == null })
        assertEquals("alpine", alpine[0].def.distroId)
        assertEquals("shell", alpine[0].def.type)
        assertEquals("shell-root", alpine[1].def.type)
    }

    @Test
    fun debianProotInstalled_bothCardsEnabled() {
        val proot = TerminalShellCatalog.sections(ctx(), avail(debianProot = true))
            .first { it.title == "DEBIAN SHELL" && it.subtitle == "PROOT" }.cards
        assertTrue(proot.all { it.enabled })
        assertTrue(proot.all { it.disabledReason == null })
        assertEquals("proot", proot[0].def.method)
    }

    @Test
    fun chrootInstalled_noRoot_allDisabledRootRequired() {
        val chroot = TerminalShellCatalog.sections(
            ctx(),
            avail(debianChroot = true, rootAvailable = false)
        ).first { it.title == "DEBIAN SHELL" && it.subtitle == "CHROOT" }.cards
        assertEquals(2, chroot.size)
        chroot.forEach { card ->
            assertFalse(card.enabled)
            assertEquals("Root required", card.disabledReason)
            assertEquals("chroot", card.def.method)
        }
    }

    @Test
    fun alpineChrootInstalledAndRoot_enabled() {
        val chroot = TerminalShellCatalog.sections(
            ctx(),
            avail(alpineChroot = true, rootAvailable = true)
        ).first { it.title == "ALPINE SHELL" && it.subtitle == "CHROOT" }.cards
        chroot.forEach { card ->
            assertTrue(card.enabled)
            assertNull(card.disabledReason)
            assertEquals("alpine_chroot", card.def.distroId)
        }
    }

    @Test
    fun chrootMissing_disabledChrootNotInstalled() {
        val chroot = TerminalShellCatalog.sections(
            ctx(),
            avail(debianProot = true, rootAvailable = true)
        ).first { it.title == "DEBIAN SHELL" && it.subtitle == "CHROOT" }.cards
        chroot.forEach { card ->
            assertFalse(card.enabled)
            assertEquals("Chroot not installed", card.disabledReason)
        }
    }

    @Test
    fun hostCard_alwaysPresentAndEnabled() {
        val host = TerminalShellCatalog.sections(ctx(), avail())
            .first { it.subtitle == "OPTIONAL" }.cards
        assertEquals(1, host.size)
        assertEquals("host", host[0].def.type)
        assertTrue(host[0].enabled)
        assertNull(host[0].disabledReason)
    }

    @Test
    fun sectionsOrder_debianAlpineProotChrootHost() {
        val sections = TerminalShellCatalog.sections(
            ctx(),
            avail(
                debianProot = true,
                alpineProot = true,
                debianChroot = true,
                alpineChroot = true,
                rootAvailable = true
            )
        )
        assertEquals(
            listOf(
                "PROOT", "PROOT", "PROOT", "PROOT", "PROOT",
                "CHROOT", "CHROOT", "CHROOT", "CHROOT", "CHROOT",
                "OPTIONAL"
            ),
            sections.map { it.subtitle }
        )
        assertEquals(
            listOf(
                "DEBIAN SHELL", "ALPINE SHELL", "FEDORA SHELL", "VOID SHELL", "OPENSUSE SHELL",
                "DEBIAN SHELL", "ALPINE SHELL", "FEDORA SHELL", "VOID SHELL", "OPENSUSE SHELL",
                "HOST"
            ),
            sections.map { it.title }
        )
    }

    @Test
    fun fedoraProotInstalled_cardsEnabled() {
        val cards = TerminalShellCatalog.sections(ctx(), avail(fedoraProot = true))
            .first { it.title == "FEDORA SHELL" && it.subtitle == "PROOT" }.cards
        assertTrue(cards.all { it.enabled })
        assertEquals("fedora", cards[0].def.distroId)
    }

    @Test
    fun prootDefs_shellAndShellRoot() {
        val defs = TerminalShellCatalog.prootDefs("alpine")
        assertEquals(listOf("shell", "shell-root"), defs.map { it.type })
        assertTrue(defs.all { it.method == "proot" && it.distroId == "alpine" })
    }

    @Test
    fun chrootDefs_shellAndShellRoot() {
        val defs = TerminalShellCatalog.chrootDefs("alpine_chroot")
        assertEquals(listOf("shell", "shell-root"), defs.map { it.type })
        assertTrue(defs.all { it.method == "chroot" && it.distroId == "alpine_chroot" })
    }

    @Test
    fun availability_aliases_match_debian_fields() {
        val a = avail(debianProot = true, debianChroot = true)
        assertTrue(a.prootInstalled)
        assertTrue(a.chrootInstalled)
    }
}
