package com.ivarna.fluxlinux.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.utils.TermuxX11Preferences
import com.ivarna.fluxlinux.ui.components.FluxSwitch
import com.ivarna.fluxlinux.ui.components.GlassSettingCard
import com.ivarna.fluxlinux.ui.theme.FluxCardFill
import com.ivarna.fluxlinux.ui.theme.FluxHairline
import com.ivarna.fluxlinux.ui.theme.FluxSwitchUncheckedTrack
import com.ivarna.fluxlinux.ui.theme.fluxMutedText

/**
 * Embedded X11 display prefs — writes Lorie keys and broadcasts live updates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun X11SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var displayScale by remember {
        mutableFloatStateOf(TermuxX11Preferences.getDisplayScale(context).toFloat())
    }
    var fullscreen by remember { mutableStateOf(TermuxX11Preferences.getFullscreen(context)) }
    var hideCutout by remember { mutableStateOf(TermuxX11Preferences.getHideCutout(context)) }
    var keepScreenOn by remember { mutableStateOf(TermuxX11Preferences.getKeepScreenOn(context)) }
    var capturePointer by remember { mutableStateOf(TermuxX11Preferences.getCapturePointer(context)) }
    var showAdditionalKbd by remember {
        mutableStateOf(TermuxX11Preferences.getShowAdditionalKeyboard(context))
    }
    var showIME by remember { mutableStateOf(TermuxX11Preferences.getShowIME(context)) }
    var preferScancodes by remember {
        mutableStateOf(TermuxX11Preferences.getPreferScancodes(context))
    }
    var scancodeWorkaround by remember {
        mutableStateOf(TermuxX11Preferences.getScancodeWorkaround(context))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "X11 Display",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Changes apply to the embedded X11 display immediately (no external Termux:X11).",
                fontSize = 13.sp,
                color = fluxMutedText()
            )

            GlassSettingCard {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Text(
                        "Display",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Display scale", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(
                            "${displayScale.toInt()}%",
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Slider(
                        value = displayScale,
                        onValueChange = { displayScale = it },
                        onValueChangeFinished = {
                            TermuxX11Preferences.setDisplayScale(context, displayScale.toInt())
                        },
                        valueRange = 30f..300f,
                        steps = 26,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                            inactiveTrackColor = FluxSwitchUncheckedTrack
                        )
                    )

                    PrefDivider()
                    PrefSwitch(
                        title = "Fullscreen",
                        subtitle = "Immersive mode on the device display",
                        checked = fullscreen,
                        onChange = {
                            fullscreen = it
                            TermuxX11Preferences.setFullscreen(context, it)
                        }
                    )
                    PrefDivider()
                    PrefSwitch(
                        title = "Hide display cutout",
                        subtitle = "Use notch / cutout area",
                        checked = hideCutout,
                        onChange = {
                            hideCutout = it
                            TermuxX11Preferences.setHideCutout(context, it)
                        }
                    )
                    PrefDivider()
                    PrefSwitch(
                        title = "Keep screen on",
                        subtitle = "Prevent timeout while X11 is open",
                        checked = keepScreenOn,
                        onChange = {
                            keepScreenOn = it
                            TermuxX11Preferences.setKeepScreenOn(context, it)
                        }
                    )
                }
            }

            GlassSettingCard {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Text(
                        "Input",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(12.dp))
                    PrefSwitch(
                        title = "Capture external pointer",
                        subtitle = "Intercept hardware pointer events",
                        checked = capturePointer,
                        onChange = {
                            capturePointer = it
                            TermuxX11Preferences.setCapturePointer(context, it)
                        }
                    )
                    PrefDivider()
                    PrefSwitch(
                        title = "Show additional keyboard",
                        subtitle = "Extra keys bar inside the X11 surface",
                        checked = showAdditionalKbd,
                        onChange = {
                            showAdditionalKbd = it
                            TermuxX11Preferences.setShowAdditionalKeyboard(context, it)
                        }
                    )
                    PrefDivider()
                    PrefSwitch(
                        title = "Show IME with external keyboard",
                        subtitle = "Software keyboard while a hardware keyboard is connected",
                        checked = showIME,
                        onChange = {
                            showIME = it
                            TermuxX11Preferences.setShowIME(context, it)
                        }
                    )
                    PrefDivider()
                    PrefSwitch(
                        title = "Prefer scancodes",
                        subtitle = "Let the X server handle keyboard layout",
                        checked = preferScancodes,
                        onChange = {
                            preferScancodes = it
                            TermuxX11Preferences.setPreferScancodes(context, it)
                        }
                    )
                    PrefDivider()
                    PrefSwitch(
                        title = "Hardware keyboard scancodes workaround",
                        subtitle = "Fix scancodes on some devices",
                        checked = scancodeWorkaround,
                        onChange = {
                            scancodeWorkaround = it
                            TermuxX11Preferences.setScancodeWorkaround(context, it)
                        }
                    )
                }
            }

            Button(
                onClick = {
                    TermuxX11Preferences.applyToTermux(context)
                    TermuxX11Preferences.openTermuxX11Preferences(context)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = FluxCardFill,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, FluxHairline),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(
                    Icons.Default.OpenInNew,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Open full X11 preferences",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    TermuxX11Preferences.applyToTermux(context)
                    Toast.makeText(context, "Applied to embedded X11", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    "Re-apply to running display",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PrefDivider() {
    HorizontalDivider(
        color = FluxHairline,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun PrefSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                subtitle,
                color = fluxMutedText(),
                fontSize = 13.sp
            )
        }
        FluxSwitch(checked = checked, onCheckedChange = onChange)
    }
}
