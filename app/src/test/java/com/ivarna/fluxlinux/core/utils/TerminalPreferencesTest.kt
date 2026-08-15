package com.ivarna.fluxlinux.core.utils

import com.ivarna.fluxlinux.core.terminal.GuestLoginShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalPreferencesTest {

    private val ctx = FakePrefsContext()

    @Test
    fun unset_defaultsToZsh() {
        assertEquals(GuestLoginShell.ZSH, TerminalPreferences.getGuestLoginShell(ctx))
        assertTrue(TerminalPreferences.preferZsh(ctx))
    }

    @Test
    fun setBash_roundTrips() {
        TerminalPreferences.setGuestLoginShell(ctx, GuestLoginShell.BASH)
        assertEquals(GuestLoginShell.BASH, TerminalPreferences.getGuestLoginShell(ctx))
        assertFalse(TerminalPreferences.preferZsh(ctx))
    }

    @Test
    fun setZsh_roundTrips() {
        TerminalPreferences.setGuestLoginShell(ctx, GuestLoginShell.BASH)
        TerminalPreferences.setGuestLoginShell(ctx, GuestLoginShell.ZSH)
        assertEquals(GuestLoginShell.ZSH, TerminalPreferences.getGuestLoginShell(ctx))
        assertTrue(TerminalPreferences.preferZsh(ctx))
    }

    @Test
    fun garbageStoredString_fallsBackToZsh() {
        ctx.prefs.edit().putString("guest_login_shell", "fish").apply()
        assertEquals(GuestLoginShell.ZSH, TerminalPreferences.getGuestLoginShell(ctx))
    }
}
