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
        deepinProot: Boolean = false,
        chimeraProot: Boolean = false,
        manjaroProot: Boolean = false,
        ubuntuProot: Boolean = false,
        kaliProot: Boolean = false,
        parrotProot: Boolean = false,
        archlinuxProot: Boolean = false,
        debianChroot: Boolean = false,
        alpineChroot: Boolean = false,
        fedoraChroot: Boolean = false,
        voidChroot: Boolean = false,
        opensuseChroot: Boolean = false,
        deepinChroot: Boolean = false,
        chimeraChroot: Boolean = false,
        manjaroChroot: Boolean = false,
        ubuntuChroot: Boolean = false,
        kaliChroot: Boolean = false,
        parrotChroot: Boolean = false,
        archlinuxChroot: Boolean = false,
        rootAvailable: Boolean = false
    ) = TerminalShellAvailability(
        debianProot = debianProot,
        alpineProot = alpineProot,
        fedoraProot = fedoraProot,
        voidProot = voidProot,
        opensuseProot = opensuseProot,
        deepinProot = deepinProot,
        chimeraProot = chimeraProot,
        manjaroProot = manjaroProot,
        ubuntuProot = ubuntuProot,
        kaliProot = kaliProot,
        parrotProot = parrotProot,
        archlinuxProot = archlinuxProot,
        debianChroot = debianChroot,
        alpineChroot = alpineChroot,
        fedoraChroot = fedoraChroot,
        voidChroot = voidChroot,
        opensuseChroot = opensuseChroot,
        deepinChroot = deepinChroot,
        chimeraChroot = chimeraChroot,
        manjaroChroot = manjaroChroot,
        ubuntuChroot = ubuntuChroot,
        kaliChroot = kaliChroot,
        parrotChroot = parrotChroot,
        archlinuxChroot = archlinuxChroot,
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
            assertEquals("Installed · no root given to app", card.disabledReason)
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
                "PROOT", "PROOT", "PROOT", "PROOT", "PROOT", "PROOT", "PROOT", "PROOT",
                "PROOT", "PROOT", "PROOT", "PROOT",
                "CHROOT", "CHROOT", "CHROOT", "CHROOT", "CHROOT", "CHROOT", "CHROOT", "CHROOT",
                "CHROOT", "CHROOT", "CHROOT", "CHROOT",
                "OPTIONAL"
            ),
            sections.map { it.subtitle }
        )
        assertEquals(
            listOf(
                "DEBIAN SHELL", "ALPINE SHELL", "FEDORA SHELL", "VOID SHELL", "OPENSUSE SHELL",
                "DEEPIN SHELL", "CHIMERA SHELL", "MANJARO SHELL",
                "UBUNTU SHELL", "KALI SHELL", "PARROT SHELL", "ARCHLINUX SHELL",
                "DEBIAN SHELL", "ALPINE SHELL", "FEDORA SHELL", "VOID SHELL", "OPENSUSE SHELL",
                "DEEPIN SHELL", "CHIMERA SHELL", "MANJARO SHELL",
                "UBUNTU SHELL", "KALI SHELL", "PARROT SHELL", "ARCHLINUX SHELL",
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

    @Test
    fun deepinProotInstalled_cardsEnabled() {
        val cards = TerminalShellCatalog.sections(ctx(), avail(deepinProot = true))
            .first { it.title == "DEEPIN SHELL" && it.subtitle == "PROOT" }.cards
        assertTrue(cards.all { it.enabled })
        assertEquals("deepin", cards[0].def.distroId)
    }

    @Test
    fun chimeraProotInstalled_cardsEnabled() {
        val cards = TerminalShellCatalog.sections(ctx(), avail(chimeraProot = true))
            .first { it.title == "CHIMERA SHELL" && it.subtitle == "PROOT" }.cards
        assertTrue(cards.all { it.enabled })
        assertEquals("chimera", cards[0].def.distroId)
    }

    @Test
    fun manjaroProotInstalled_cardsEnabled() {
        val cards = TerminalShellCatalog.sections(ctx(), avail(manjaroProot = true))
            .first { it.title == "MANJARO SHELL" && it.subtitle == "PROOT" }.cards
        assertTrue(cards.all { it.enabled })
        assertEquals("manjaro", cards[0].def.distroId)
    }

    @Test
    fun deepinChrootInstalledAndRoot_enabled() {
        val chroot = TerminalShellCatalog.sections(
            ctx(),
            avail(deepinChroot = true, rootAvailable = true)
        ).first { it.title == "DEEPIN SHELL" && it.subtitle == "CHROOT" }.cards
        chroot.forEach { card ->
            assertTrue(card.enabled)
            assertNull(card.disabledReason)
            assertEquals("deepin_chroot", card.def.distroId)
        }
    }

    @Test
    fun chimeraChrootMissing_disabledChrootNotInstalled() {
        val chroot = TerminalShellCatalog.sections(
            ctx(),
            avail(chimeraProot = true, rootAvailable = true)
        ).first { it.title == "CHIMERA SHELL" && it.subtitle == "CHROOT" }.cards
        chroot.forEach { card ->
            assertFalse(card.enabled)
            assertEquals("Chroot not installed", card.disabledReason)
        }
    }

    @Test
    fun manjaroChroot_noRoot_disabledRootRequired() {
        val chroot = TerminalShellCatalog.sections(
            ctx(),
            avail(manjaroChroot = true, rootAvailable = false)
        ).first { it.title == "MANJARO SHELL" && it.subtitle == "CHROOT" }.cards
        chroot.forEach { card ->
            assertFalse(card.enabled)
            assertEquals("Installed · no root given to app", card.disabledReason)
        }
    }

    @Test
    fun prootDefs_deepin_chimera_manjaro() {
        assertEquals("deepin", TerminalShellCatalog.prootDefs("deepin")[0].distroId)
        assertEquals("chimera", TerminalShellCatalog.prootDefs("chimera")[0].distroId)
        assertEquals("manjaro", TerminalShellCatalog.prootDefs("manjaro")[0].distroId)
    }

    @Test
    fun chrootDefs_deepin_chimera_manjaro() {
        val ids = listOf("deepin_chroot", "chimera_chroot", "manjaro_chroot")
        ids.forEach { id ->
            val defs = TerminalShellCatalog.chrootDefs(id)
            assertEquals(listOf("shell", "shell-root"), defs.map { it.type })
            assertTrue(defs.all { it.method == "chroot" && it.distroId == id })
        }
    }

    @Test
    fun ubuntuProotInstalled_cardsEnabled() {
        val cards = TerminalShellCatalog.sections(ctx(), avail(ubuntuProot = true))
            .first { it.title == "UBUNTU SHELL" && it.subtitle == "PROOT" }.cards
        assertTrue(cards.all { it.enabled })
        assertEquals("ubuntu", cards[0].def.distroId)
    }

    @Test
    fun kaliProotNotInstalled_cardsDisabled() {
        val cards = TerminalShellCatalog.sections(ctx(), avail())
            .first { it.title == "KALI SHELL" && it.subtitle == "PROOT" }.cards
        cards.forEach { card ->
            assertFalse(card.enabled)
            assertEquals("Install KALI in Distros", card.disabledReason)
        }
    }

    @Test
    fun parrotChrootInstalledAndRoot_enabled() {
        val chroot = TerminalShellCatalog.sections(
            ctx(),
            avail(parrotChroot = true, rootAvailable = true)
        ).first { it.title == "PARROT SHELL" && it.subtitle == "CHROOT" }.cards
        chroot.forEach { card ->
            assertTrue(card.enabled)
            assertNull(card.disabledReason)
            assertEquals("parrot_chroot", card.def.distroId)
        }
    }

    @Test
    fun archlinuxChrootMissing_disabledChrootNotInstalled() {
        val chroot = TerminalShellCatalog.sections(
            ctx(),
            avail(archlinuxProot = true, rootAvailable = true)
        ).first { it.title == "ARCHLINUX SHELL" && it.subtitle == "CHROOT" }.cards
        chroot.forEach { card ->
            assertFalse(card.enabled)
            assertEquals("Chroot not installed", card.disabledReason)
        }
    }

    @Test
    fun prootDefs_ukpa() {
        assertEquals("ubuntu", TerminalShellCatalog.prootDefs("ubuntu")[0].distroId)
        assertEquals("kali", TerminalShellCatalog.prootDefs("kali")[0].distroId)
        assertEquals("parrot", TerminalShellCatalog.prootDefs("parrot")[0].distroId)
        assertEquals("archlinux", TerminalShellCatalog.prootDefs("archlinux")[0].distroId)
    }

    @Test
    fun chrootDefs_ukpa() {
        val ids = listOf("ubuntu_chroot", "kali_chroot", "parrot_chroot", "archlinux_chroot")
        ids.forEach { id ->
            val defs = TerminalShellCatalog.chrootDefs(id)
            assertEquals(listOf("shell", "shell-root"), defs.map { it.type })
            assertTrue(defs.all { it.method == "chroot" && it.distroId == id })
        }
    }
}
