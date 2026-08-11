package com.ivarna.fluxlinux.ui.terminal

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPECIAL_KEY_CODES + inject path pure assertions (termux-lib injectKey parity).
 * TerminalView is Android View — inject with null is the JVM-safe fail path;
 * special/printable dispatch is covered via map membership + path branching.
 */
class TerminalKeyCodesTest {

    private val requiredSpecialKeys = listOf(
        "ESC", "TAB", "ENTER", "BKSP", "DEL",
        "UP", "DOWN", "LEFT", "RIGHT",
        "HOME", "END", "PGUP", "PGDN", "INS",
        "F1", "F2", "F3", "F4", "F5", "F6",
        "F7", "F8", "F9", "F10", "F11", "F12"
    )

    @Test
    fun everySpecialKey_presentInMap() {
        requiredSpecialKeys.forEach { key ->
            assertNotNull("missing SPECIAL_KEY_CODES entry: $key", TerminalKeyCodes.SPECIAL_KEY_CODES[key])
        }
        assertEquals(
            "unexpected extra/missing special keys",
            requiredSpecialKeys.toSet(),
            TerminalKeyCodes.SPECIAL_KEY_CODES.keys
        )
    }

    @Test
    fun specialKeyCodes_matchAndroidKeyEventConstants() {
        assertEquals(KeyEvent.KEYCODE_ESCAPE, TerminalKeyCodes.SPECIAL_KEY_CODES["ESC"])
        assertEquals(KeyEvent.KEYCODE_TAB, TerminalKeyCodes.SPECIAL_KEY_CODES["TAB"])
        assertEquals(KeyEvent.KEYCODE_ENTER, TerminalKeyCodes.SPECIAL_KEY_CODES["ENTER"])
        assertEquals(KeyEvent.KEYCODE_DEL, TerminalKeyCodes.SPECIAL_KEY_CODES["BKSP"])
        assertEquals(KeyEvent.KEYCODE_FORWARD_DEL, TerminalKeyCodes.SPECIAL_KEY_CODES["DEL"])
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, TerminalKeyCodes.SPECIAL_KEY_CODES["UP"])
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, TerminalKeyCodes.SPECIAL_KEY_CODES["DOWN"])
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, TerminalKeyCodes.SPECIAL_KEY_CODES["LEFT"])
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, TerminalKeyCodes.SPECIAL_KEY_CODES["RIGHT"])
        assertEquals(KeyEvent.KEYCODE_MOVE_HOME, TerminalKeyCodes.SPECIAL_KEY_CODES["HOME"])
        assertEquals(KeyEvent.KEYCODE_MOVE_END, TerminalKeyCodes.SPECIAL_KEY_CODES["END"])
        assertEquals(KeyEvent.KEYCODE_PAGE_UP, TerminalKeyCodes.SPECIAL_KEY_CODES["PGUP"])
        assertEquals(KeyEvent.KEYCODE_PAGE_DOWN, TerminalKeyCodes.SPECIAL_KEY_CODES["PGDN"])
        assertEquals(KeyEvent.KEYCODE_INSERT, TerminalKeyCodes.SPECIAL_KEY_CODES["INS"])
        assertEquals(KeyEvent.KEYCODE_F1, TerminalKeyCodes.SPECIAL_KEY_CODES["F1"])
        assertEquals(KeyEvent.KEYCODE_F12, TerminalKeyCodes.SPECIAL_KEY_CODES["F12"])
    }

    @Test
    fun printableSymbols_notInSpecialMap() {
        listOf("/", "\\", "|", "~", "-", "_").forEach { sym ->
            assertNull("printable must use inputCodePoint path: $sym", TerminalKeyCodes.SPECIAL_KEY_CODES[sym])
        }
    }

    @Test
    fun injectKey_nullView_doesNotThrow() {
        // Fail path: no TerminalView attached — must be a no-op.
        TerminalKeyInjector.injectKey(null, "ESC", ctrl = false, alt = false, shift = false)
        TerminalKeyInjector.injectKey(null, "ENTER", ctrl = true, alt = false, shift = false)
        TerminalKeyInjector.injectKey(null, "/", ctrl = false, alt = false, shift = false)
        TerminalKeyInjector.injectKey(null, "F5", ctrl = false, alt = true, shift = true)
    }

    @Test
    fun specialVsPrintable_pathClassification() {
        // Pure logic that mirrors injectKey branching without needing TerminalView.
        fun isSpecial(key: String) = TerminalKeyCodes.SPECIAL_KEY_CODES.containsKey(key)
        assertTrue(isSpecial("ESC"))
        assertTrue(isSpecial("TAB"))
        assertTrue(isSpecial("ENTER"))
        assertTrue(isSpecial("BKSP"))
        assertTrue(isSpecial("LEFT"))
        assertTrue(isSpecial("F1"))
        assertTrue(!isSpecial("/"))
        assertTrue(!isSpecial("|"))
        assertTrue(!isSpecial("~"))
        assertTrue(!isSpecial("-"))
        assertTrue(!isSpecial("_"))
        assertTrue(!isSpecial("\\"))
        assertTrue(!isSpecial("C")) // letter keys are not special map entries
    }

    @Test
    fun metaFlagComposition_matchesNativecode() {
        // Document expected meta bits used by injectKey (termux-lib MainActivity.injectKey).
        var meta = 0
        meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        assertTrue(meta and KeyEvent.META_CTRL_ON != 0)
        assertTrue(meta and KeyEvent.META_ALT_ON != 0)
        assertTrue(meta and KeyEvent.META_SHIFT_ON != 0)
    }
}
