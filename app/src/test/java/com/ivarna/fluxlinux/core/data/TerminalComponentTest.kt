package com.ivarna.fluxlinux.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Routing helper tests — the `termux` card must never resolve to a component. */
class TerminalComponentTest {

    @Test
    fun debian_routesToFluxTerminal() {
        val c = terminalComponentFor("debian")
        assertEquals(TerminalComponent.TERMUX_FLUX_TERMINAL, c)
        assertEquals("proot", c.method)
    }

    @Test
    fun debian13Chroot_routesToRootShell() {
        val c = terminalComponentFor("debian13_chroot")
        assertEquals(TerminalComponent.CHROOT_ROOT_SHELL, c)
        assertEquals("chroot", c.method)
    }

    @Test
    fun removedTermuxCard_throws() {
        // Legacy "termux" install card was removed; any path reaching it is a bug.
        assertThrows(IllegalArgumentException::class.java) {
            terminalComponentFor("termux")
        }
    }
}
