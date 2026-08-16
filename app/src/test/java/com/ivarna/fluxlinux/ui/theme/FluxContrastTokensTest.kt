package com.ivarna.fluxlinux.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class FluxContrastTokensTest {

    private fun channelToLinear(c: Float): Double {
        return if (c <= 0.04045f) {
            (c / 12.92).toDouble()
        } else {
            ((c + 0.055) / 1.055).toDouble().pow(2.4)
        }
    }

    private fun relativeLuminance(color: Color): Double {
        val r = channelToLinear(color.red)
        val g = channelToLinear(color.green)
        val b = channelToLinear(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(c1: Color, c2: Color): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun fluxBodyMuted_hasHighContrastAgainstDarkSurface() {
        val darkSurface = FluxDarkSurface // #121212
        val contrast = contrastRatio(FluxBodyMuted, darkSurface)
        // WCAG AA requires 4.5:1, WCAG AAA requires 7.0:1
        assertTrue("FluxBodyMuted contrast ratio ($contrast) must be >= 7.0:1 on #121212", contrast >= 7.0)
    }

    @Test
    fun fluxSwitchUncheckedTrack_isDistinctFromDarkSurface() {
        assertNotEquals(FluxDarkSurface, FluxSwitchUncheckedTrack)
        val contrast = contrastRatio(FluxSwitchUncheckedTrack, FluxDarkSurface)
        assertTrue("Unchecked track must have visible separation on background", contrast > 1.3)
    }

    @Test
    fun fluxSwitchTokens_haveAdequateContrast() {
        // Checked thumb on checked track
        val checkedContrast = contrastRatio(FluxSwitchCheckedThumb, FluxSwitchCheckedTrack)
        assertTrue("Checked switch thumb/track contrast must be >= 4.5:1", checkedContrast >= 4.5)

        // Unchecked thumb on unchecked track
        val uncheckedContrast = contrastRatio(FluxSwitchUncheckedThumb, FluxSwitchUncheckedTrack)
        assertTrue("Unchecked switch thumb/track contrast must be >= 3.0:1", uncheckedContrast >= 3.0)
    }
}
