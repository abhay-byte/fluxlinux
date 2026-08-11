package com.ivarna.fluxlinux.ui.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ModifierState parity with termux-lib / nativecode:
 * tap = one-shot toggle; long-press = lock; consume clears unlocked only.
 */
class TerminalModifierStateTest {

    @Test
    fun default_allInactiveUnlocked() {
        val s = TerminalModifierState()
        assertFalse(s.ctrlActive)
        assertFalse(s.ctrlLocked)
        assertFalse(s.altActive)
        assertFalse(s.altLocked)
        assertFalse(s.shiftActive)
        assertFalse(s.shiftLocked)
    }

    // ── CTRL ──────────────────────────────────────────────────────────────────

    @Test
    fun toggleCtrl_activatesThenDeactivates() {
        val s = TerminalModifierState()
        s.toggleCtrl()
        assertTrue(s.ctrlActive)
        assertFalse(s.ctrlLocked)
        s.toggleCtrl()
        assertFalse(s.ctrlActive)
        assertFalse(s.ctrlLocked)
    }

    @Test
    fun lockCtrl_activeAndLocked_survivesConsume() {
        val s = TerminalModifierState()
        s.lockCtrl()
        assertTrue(s.ctrlActive)
        assertTrue(s.ctrlLocked)
        s.consumeModifiers()
        assertTrue(s.ctrlActive)
        assertTrue(s.ctrlLocked)
    }

    @Test
    fun consumeModifiers_clearsUnlockedOnly() {
        val s = TerminalModifierState()
        s.toggleCtrl()
        s.lockAlt()
        s.toggleShift()
        s.consumeModifiers()
        assertFalse(s.ctrlActive)
        assertTrue(s.altActive)
        assertTrue(s.altLocked)
        assertFalse(s.shiftActive)
    }

    @Test
    fun readCtrl_oneShot_returnsTrueOnceAndClears() {
        val s = TerminalModifierState()
        s.toggleCtrl()
        assertTrue(s.readCtrl(true))
        assertFalse(s.ctrlActive)
        assertFalse(s.readCtrl(true))
    }

    @Test
    fun readCtrl_locked_returnsTrueAndStaysActive() {
        val s = TerminalModifierState()
        s.lockCtrl()
        assertTrue(s.readCtrl(true))
        assertTrue(s.ctrlActive)
        assertTrue(s.ctrlLocked)
        assertTrue(s.readCtrl(true))
    }

    @Test
    fun readCtrl_autoReadFalse_doesNotClear() {
        val s = TerminalModifierState()
        s.toggleCtrl()
        assertTrue(s.readCtrl(false))
        assertTrue(s.ctrlActive)
    }

    @Test
    fun toggleCtrl_whenLocked_clearsLock() {
        val s = TerminalModifierState()
        s.lockCtrl()
        s.toggleCtrl()
        assertFalse(s.ctrlActive)
        assertFalse(s.ctrlLocked)
    }

    // ── ALT ───────────────────────────────────────────────────────────────────

    @Test
    fun toggleAlt_activatesThenDeactivates() {
        val s = TerminalModifierState()
        s.toggleAlt()
        assertTrue(s.altActive)
        s.toggleAlt()
        assertFalse(s.altActive)
    }

    @Test
    fun lockAlt_survivesConsume() {
        val s = TerminalModifierState()
        s.lockAlt()
        s.consumeModifiers()
        assertTrue(s.altActive)
        assertTrue(s.altLocked)
    }

    @Test
    fun readAlt_oneShot_clears() {
        val s = TerminalModifierState()
        s.toggleAlt()
        assertTrue(s.readAlt(true))
        assertFalse(s.altActive)
    }

    @Test
    fun readAlt_locked_stays() {
        val s = TerminalModifierState()
        s.lockAlt()
        assertTrue(s.readAlt(true))
        assertTrue(s.altActive)
    }

    // ── SHIFT ─────────────────────────────────────────────────────────────────

    @Test
    fun toggleShift_activatesThenDeactivates() {
        val s = TerminalModifierState()
        s.toggleShift()
        assertTrue(s.shiftActive)
        s.toggleShift()
        assertFalse(s.shiftActive)
    }

    @Test
    fun lockShift_survivesConsume() {
        val s = TerminalModifierState()
        s.lockShift()
        s.consumeModifiers()
        assertTrue(s.shiftActive)
        assertTrue(s.shiftLocked)
    }

    @Test
    fun readShift_oneShot_clears() {
        val s = TerminalModifierState()
        s.toggleShift()
        assertTrue(s.readShift(true))
        assertFalse(s.shiftActive)
    }

    @Test
    fun readShift_locked_stays() {
        val s = TerminalModifierState()
        s.lockShift()
        assertTrue(s.readShift(true))
        assertTrue(s.shiftActive)
    }

    // ── Fn ────────────────────────────────────────────────────────────────────

    @Test
    fun readFn_alwaysFalse() {
        val s = TerminalModifierState()
        assertFalse(s.readFn(true))
        assertFalse(s.readFn(false))
    }

    @Test
    fun consumeModifiers_noopWhenAllInactive() {
        val s = TerminalModifierState()
        s.consumeModifiers()
        assertFalse(s.ctrlActive)
        assertFalse(s.altActive)
        assertFalse(s.shiftActive)
    }
}
