package com.ivarna.fluxlinux.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.terminal.GuestLoginShell
import com.ivarna.fluxlinux.core.utils.TerminalPreferences
import com.ivarna.fluxlinux.ui.components.GlassSettingCard

/**
 * Terminal font zoom + ExtraKeys toolbar + guest login shell (nativecode settings parity).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var fontSize by remember {
        mutableIntStateOf(TerminalPreferences.getFontSize(context))
    }
    var showExtraKeys by remember {
        mutableStateOf(TerminalPreferences.isExtraKeysEnabled(context))
    }
    var guestShellZsh by remember {
        mutableStateOf(TerminalPreferences.preferZsh(context))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Terminal",
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
            GlassSettingCard {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Text(
                        "Global terminal zoom",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Font size for the embedded terminal (pinch zoom also updates this).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                val next = (fontSize - 2).coerceAtLeast(TerminalPreferences.FONT_MIN)
                                fontSize = next
                                TerminalPreferences.setFontSize(context, next)
                            }
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Smaller")
                        }
                        Text(
                            "${fontSize} pt",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                val next = (fontSize + 2).coerceAtMost(TerminalPreferences.FONT_MAX)
                                fontSize = next
                                TerminalPreferences.setFontSize(context, next)
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Larger")
                        }
                    }
                    Text(
                        "Range ${TerminalPreferences.FONT_MIN}–${TerminalPreferences.FONT_MAX}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            GlassSettingCard {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Extra keyboard rows",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Show CTRL, ALT, ESC, arrows and symbols under the terminal.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = showExtraKeys,
                        onCheckedChange = {
                            showExtraKeys = it
                            TerminalPreferences.setExtraKeysEnabled(context, it)
                        }
                    )
                }
            }

            GlassSettingCard {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Guest login shell",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "On = zsh, off = bash. New proot/chroot Terminal sessions only; live tabs keep their shell.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (guestShellZsh) "zsh" else "bash",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Switch(
                        checked = guestShellZsh,
                        onCheckedChange = { on ->
                            guestShellZsh = on
                            TerminalPreferences.setGuestLoginShell(
                                context,
                                if (on) GuestLoginShell.ZSH else GuestLoginShell.BASH
                            )
                        },
                        modifier = Modifier.semantics {
                            contentDescription =
                                "Guest login shell: ${if (guestShellZsh) "zsh" else "bash"}"
                        }
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
