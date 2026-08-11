package com.ivarna.fluxlinux.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.data.Distro
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow

@Composable
fun DistroCard(
    distro: Distro,
    isInstalled: Boolean = false,
    isGuiRunning: Boolean = false,
    /** True while start_gui is streaming (termux-lib Starting phase). */
    isGuiStarting: Boolean = false,
    isGlobalInstalling: Boolean = false,
    isCurrentlyInstalling: Boolean = false,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStart: () -> Unit,
    onStop: () -> Unit = {},
    onOpenDisplay: () -> Unit = {},
    onViewLogs: () -> Unit = {},
    logsAvailable: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Distro Icon
                if (distro.iconRes != null) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = distro.iconRes),
                        contentDescription = "${distro.name} logo",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    // Fallback gradient placeholder
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                        androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Placeholder for distro icon
                    }
                }

                Spacer(modifier = Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = distro.name,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // RUNNING / STARTING badge (desktop session)
                        if (isInstalled && (isGuiRunning || isGuiStarting)) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isGuiStarting) Color(0xFFFFA000) else Color(0xFF4CAF50),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isGuiStarting) "STARTING" else "RUNNING",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        if (distro.comingSoon) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "COMING SOON",
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = distro.id,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp
                    )
                }

                // Settings Icon (Only if installed)
                if (isInstalled) {
                    androidx.compose.material3.IconButton(onClick = onNavigateToSettings) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                text = distro.description,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            
            // Compatibility Badges (for coming soon distros)
            if (distro.comingSoon) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // PRoot Badge
                    Box(
                        modifier = Modifier
                            .background(
                                if (distro.prootSupported) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "PRoot",
                            color = if (distro.prootSupported) androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Chroot Badge
                    Box(
                        modifier = Modifier
                            .background(
                                if (distro.chrootSupported) androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Chroot",
                            color = if (distro.chrootSupported) androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons - Conditional based on installation status
            if (!isInstalled) {
                // Show Install button when not installed
                Button(
                    onClick = if (distro.comingSoon || (isGlobalInstalling && !isCurrentlyInstalling)) { {} } else onInstall,
                    enabled = !distro.comingSoon && (!isGlobalInstalling || isCurrentlyInstalling),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        disabledContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                        disabledContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            isCurrentlyInstalling -> "View Progress"
                            isGlobalInstalling -> "Installation Busy..."
                            distro.comingSoon -> "Coming Soon"
                            else -> "Install"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // RUNNING / STARTING — Open X11 + View Logs + Stop (termux-lib parity)
                if (isGuiRunning || isGuiStarting) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Open X11 display (nativecode displayBtn)
                            Button(
                                onClick = onOpenDisplay,
                                enabled = isGuiRunning || isGuiStarting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E5FF),
                                    contentColor = Color.Black,
                                    disabledContainerColor = Color(0xFF00E5FF).copy(alpha = 0.4f),
                                    disabledContentColor = Color.Black.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Open X11",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Button(
                                onClick = onStop,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF5252),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(0.55f)
                            ) {
                                Text(
                                    text = "Stop",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        // VIEW LOGS — always available once start streamed any output
                        if (logsAvailable || isGuiStarting || isGuiRunning) {
                            Button(
                                onClick = onViewLogs,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "View Logs",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else {
                    // STOPPED State - Start + optional prior logs
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onNavigateToStart,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        if (logsAvailable) {
                            Button(
                                onClick = onViewLogs,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "View Logs",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlassSettingCard(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                // Use surface variant or surface with opacity, adaptive to theme
                androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            ) 
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .border(
                1.dp,
                androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                shape
            )
    ) {
        content()
    }
}
