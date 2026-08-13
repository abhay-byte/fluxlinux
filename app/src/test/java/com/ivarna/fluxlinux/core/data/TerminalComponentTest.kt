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
    }

    @Test
    fun chrootCards() {
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("debian13_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("debian_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("alpine_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("fedora_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("void_chroot"))
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, terminalComponentFor("opensuse_chroot"))
    }

    @Test
    fun unknownThrows() {
        try {
            terminalComponentFor("archlinux")
            fail("expected")
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message!!.contains("unsupported"))
        }
    }
}
