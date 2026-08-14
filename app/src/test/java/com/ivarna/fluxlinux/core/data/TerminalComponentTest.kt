package com.ivarna.fluxlinux.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class TerminalComponentTest {

    @Test
    fun prootCards() {
        assertEquals(TerminalComponent.TERMUX_FLUX_TERMINAL, terminalComponentFor("debian"))
        assertEquals(TerminalComponent.TERMUX_FLUX_TERMINAL, terminalComponentFor("alpine"))
        assertEquals(TerminalComponent.TERMUX_FLUX_TERMINAL, terminalComponentFor("fedora"))
        assertEquals(TerminalComponent.TERMUX_FLUX_TERMINAL, terminalComponentFor("void"))
        assertEquals(TerminalComponent.TERMUX_FLUX_TERMINAL, terminalComponentFor("opensuse"))
        assertEquals(TerminalComponent.TERMUX_FLUX_TERMINAL, terminalComponentFor("deepin"))
        assertEquals(TerminalComponent.TERMUX_FLUX_TERMINAL, terminalComponentFor("chimera"))
        assertEquals(TerminalComponent.TERMUX_FLUX_TERMINAL, terminalComponentFor("manjaro"))
        assertEquals(TerminalComponent.TERMUX_FLUX_TERMINAL, terminalComponentFor("ubuntu"))
        assertEquals(TerminalComponent.TERMUX_FLUX_TERMINAL, terminalComponentFor("kali"))
        assertEquals(TerminalComponent.TERMUX_FLUX_TERMINAL, terminalComponentFor("parrot"))
        assertEquals(TerminalComponent.TERMUX_FLUX_TERMINAL, terminalComponentFor("archlinux"))
    }

    @Test
    fun chrootCards() {
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("debian13_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("debian_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("alpine_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("fedora_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("void_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("opensuse_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("deepin_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("chimera_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("manjaro_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("ubuntu_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("kali_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("parrot_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("archlinux_chroot"))
    }

    @Test
    fun unknownThrows() {
        try {
            terminalComponentFor("openbsd")
            fail("expected")
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message!!.contains("unsupported"))
        }
    }
}
