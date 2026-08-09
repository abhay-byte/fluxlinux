@file:OptIn(ExperimentalFoundationApi::class)

package com.ivarna.fluxlinux.ui.terminal

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.R
import com.ivarna.fluxlinux.ui.theme.FluxAccentMagenta
import com.termux.view.TerminalView

/**
 * Toggle + long-press-lock modifier state (nativecode `ModifierState` parity).
 *
 * A modifier stays active until consumed by a key press unless it is locked
 * (long-press); locked modifiers survive consumption and clear on the next tap.
 * Compose `mutableStateOf` fields drive toolbar UI refresh automatically.
 */
class TerminalModifierState {
    var ctrlActive by mutableStateOf(false)
    var ctrlLocked by mutableStateOf(false)
    var altActive by mutableStateOf(false)
    var altLocked by mutableStateOf(false)
    var shiftActive by mutableStateOf(false)
    var shiftLocked by mutableStateOf(false)

    fun readCtrl(autoReadSetFalse: Boolean = true): Boolean {
        if (!ctrlActive) return false
        if (autoReadSetFalse && !ctrlLocked) ctrlActive = false
        return true
    }

    fun readAlt(autoReadSetFalse: Boolean = true): Boolean {
        if (!altActive) return false
        if (autoReadSetFalse && !altLocked) altActive = false
        return true
    }

    fun readShift(autoReadSetFalse: Boolean = true): Boolean {
        if (!shiftActive) return false
        if (autoReadSetFalse && !shiftLocked) shiftActive = false
        return true
    }

    /** Fn is a UI-only row toggle (nativecode returns false here). */
    fun readFn(autoReadSetFalse: Boolean = true): Boolean = false

    fun toggleCtrl() {
        if (ctrlLocked) {
            ctrlActive = false
            ctrlLocked = false
        } else {
            ctrlActive = !ctrlActive
            if (!ctrlActive) ctrlLocked = false
        }
    }

    fun lockCtrl() {
        ctrlActive = true
        ctrlLocked = true
    }

    fun toggleAlt() {
        if (altLocked) {
            altActive = false
            altLocked = false
        } else {
            altActive = !altActive
            if (!altActive) altLocked = false
        }
    }

    fun lockAlt() {
        altActive = true
        altLocked = true
    }

    fun toggleShift() {
        if (shiftLocked) {
            shiftActive = false
            shiftLocked = false
        } else {
            shiftActive = !shiftActive
            if (!shiftActive) shiftLocked = false
        }
    }

    fun lockShift() {
        shiftActive = true
        shiftLocked = true
    }

    /** Clear one-shot modifiers after a key is injected (locked ones survive). */
    fun consumeModifiers() {
        if (!ctrlLocked) ctrlActive = false
        if (!altLocked) altActive = false
        if (!shiftLocked) shiftActive = false
    }
}

/** Nativecode `SPECIAL_KEY_CODES` parity — toolbar key name → Android keycode. */
object TerminalKeyCodes {
    val SPECIAL_KEY_CODES: Map<String, Int> = mapOf(
        "ESC" to KeyEvent.KEYCODE_ESCAPE,
        "TAB" to KeyEvent.KEYCODE_TAB,
        "ENTER" to KeyEvent.KEYCODE_ENTER,
        "BKSP" to KeyEvent.KEYCODE_DEL,
        "DEL" to KeyEvent.KEYCODE_FORWARD_DEL,
        "UP" to KeyEvent.KEYCODE_DPAD_UP,
        "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
        "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT,
        "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "HOME" to KeyEvent.KEYCODE_MOVE_HOME,
        "END" to KeyEvent.KEYCODE_MOVE_END,
        "PGUP" to KeyEvent.KEYCODE_PAGE_UP,
        "PGDN" to KeyEvent.KEYCODE_PAGE_DOWN,
        "INS" to KeyEvent.KEYCODE_INSERT,
        "F1" to KeyEvent.KEYCODE_F1,
        "F2" to KeyEvent.KEYCODE_F2,
        "F3" to KeyEvent.KEYCODE_F3,
        "F4" to KeyEvent.KEYCODE_F4,
        "F5" to KeyEvent.KEYCODE_F5,
        "F6" to KeyEvent.KEYCODE_F6,
        "F7" to KeyEvent.KEYCODE_F7,
        "F8" to KeyEvent.KEYCODE_F8,
        "F9" to KeyEvent.KEYCODE_F9,
        "F10" to KeyEvent.KEYCODE_F10,
        "F11" to KeyEvent.KEYCODE_F11,
        "F12" to KeyEvent.KEYCODE_F12
    )
}

/**
 * Nativecode `injectKey` parity — special keys go through `TerminalView.onKeyDown /
 * onKeyUp` with meta flags; printable symbols go through `inputCodePoint`. This is
 * the ONLY toolbar inject path (the old `session.write("\u001b…")` / dispatch path is
 * gone — plan §2.3 / §6 PR-T4).
 */
object TerminalKeyInjector {
    fun injectKey(tv: TerminalView?, key: String, ctrl: Boolean, alt: Boolean, shift: Boolean) {
        tv ?: return
        val keyCode = TerminalKeyCodes.SPECIAL_KEY_CODES[key]
        if (keyCode != null) {
            var meta = 0
            if (ctrl) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            if (alt) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            if (shift) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            val evDown = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta)
            tv.onKeyDown(keyCode, evDown)
            val evUp = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, meta)
            tv.onKeyUp(keyCode, evUp)
        } else {
            key.codePoints().forEach { cp -> tv.inputCodePoint(cp, ctrl, alt) }
        }
    }
}

/**
 * Nativecode-style special-keys toolbar (2 rows + optional F-keys row).
 *
 * Row 1: CTRL · ALT · SHFT · ESC · TAB · ENT · BKSP — equal weight, 44dp, edge-to-edge.
 * Row 2: horizontal scroll — arrow icons, DEL / Ins, symbols `/\|~-_\`, Fn toggle.
 * Row 3: F1–F12 (toggled via Fn). Image attach is out of scope for v1 (plan §4).
 */
@Composable
fun TerminalExtraKeys(
    modState: TerminalModifierState,
    terminalView: () -> TerminalView?,
    modifier: Modifier = Modifier
) {
    val row2Scroll = rememberScrollState()
    val fRowScroll = rememberScrollState()
    var fnVisible by remember { mutableStateOf(false) }
    // Nativecode keyWidth = screenWidthPx / 8 → screenWidthDp / 8.
    val keyWidth: Dp = with(LocalDensity.current) {
        (LocalConfiguration.current.screenWidthDp / 8).dp
    }

    fun press(key: String) {
        TerminalKeyInjector.injectKey(
            terminalView(),
            key,
            modState.ctrlActive,
            modState.altActive,
            modState.shiftActive
        )
        modState.consumeModifiers()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
    ) {
        // ── Row 1: modifiers + core keys (equal weight, 44dp) ───────────────
        Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            ModifierKey("CTRL", modState.ctrlActive, modState.ctrlLocked, { modState.toggleCtrl() }, { modState.lockCtrl() }, Modifier.weight(1f))
            ModifierKey("ALT", modState.altActive, modState.altLocked, { modState.toggleAlt() }, { modState.lockAlt() }, Modifier.weight(1f))
            ModifierKey("SHFT", modState.shiftActive, modState.shiftLocked, { modState.toggleShift() }, { modState.lockShift() }, Modifier.weight(1f))
            CoreKey("ESC", { press("ESC") }, Modifier.weight(1f))
            CoreKey("TAB", { press("TAB") }, Modifier.weight(1f))
            CoreKey("ENT", { press("ENTER") }, Modifier.weight(1f))
            CoreKey("BKSP", { press("BKSP") }, Modifier.weight(1f))
        }

        // ── Row 2: scrollable special keys ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(row2Scroll)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconKey(painterResource(R.drawable.ic_arrow_left), "Arrow left", { press("LEFT") }, keyWidth)
            IconKey(painterResource(R.drawable.ic_arrow_up), "Arrow up", { press("UP") }, keyWidth)
            IconKey(painterResource(R.drawable.ic_arrow_down), "Arrow down", { press("DOWN") }, keyWidth)
            IconKey(painterResource(R.drawable.ic_arrow_right), "Arrow right", { press("RIGHT") }, keyWidth)
            IconKey(painterResource(R.drawable.ic_backspace), "Delete", { press("DEL") }, keyWidth)
            TextKey("Ins", { press("INS") }, keyWidth)
            listOf("/", "|", "~", "-", "_", "\\").forEach { sym ->
                TextKey(sym, { press(sym) }, keyWidth)
            }
            TextKey("Fn", { fnVisible = !fnVisible }, keyWidth, active = fnVisible)
        }

        // ── Row 3: F-keys (toggle via Fn) ───────────────────────────────────
        if (fnVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(fRowScroll)
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                (1..12).forEach { n ->
                    TextKey("F$n", { press("F$n") }, keyWidth)
                }
            }
        }
    }
}

@Composable
private fun ModifierKey(
    label: String,
    active: Boolean,
    locked: Boolean,
    onPress: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .background(
                if (active) FluxAccentMagenta.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.surface
            )
            // R5: LOCKED (long-press) gets an outline ring + badge dot so it is
            // visually distinct from a one-shot active modifier.
            .then(
                if (locked) Modifier.border(2.dp, MaterialTheme.colorScheme.onPrimary)
                else Modifier
            )
            .combinedClickable(onClick = onPress, onLongClick = onLongPress)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
            )
        }
    }
}

@Composable
private fun CoreKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun IconKey(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    width: Dp
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width)
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick)
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp).height(22.dp)
        )
    }
}

@Composable
private fun TextKey(label: String, onClick: () -> Unit, width: Dp, active: Boolean = false) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width)
            .height(44.dp)
            .background(
                if (active) FluxAccentMagenta.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
