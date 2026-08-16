package com.ivarna.fluxlinux.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import com.ivarna.fluxlinux.ui.theme.FluxSwitchCheckedThumb
import com.ivarna.fluxlinux.ui.theme.FluxSwitchCheckedTrack
import com.ivarna.fluxlinux.ui.theme.FluxSwitchUncheckedBorder
import com.ivarna.fluxlinux.ui.theme.FluxSwitchUncheckedThumb
import com.ivarna.fluxlinux.ui.theme.FluxSwitchUncheckedTrack

@Composable
fun FluxSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = if (isDark) {
        SwitchDefaults.colors(
            checkedThumbColor = FluxSwitchCheckedThumb,
            checkedTrackColor = FluxSwitchCheckedTrack,
            checkedBorderColor = FluxSwitchCheckedTrack,
            uncheckedThumbColor = FluxSwitchUncheckedThumb,
            uncheckedTrackColor = FluxSwitchUncheckedTrack,
            uncheckedBorderColor = FluxSwitchUncheckedBorder,
        )
    } else {
        SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.secondary,
            checkedThumbColor = MaterialTheme.colorScheme.onSecondary
        )
    }

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = colors,
        enabled = enabled,
        modifier = modifier
    )
}
