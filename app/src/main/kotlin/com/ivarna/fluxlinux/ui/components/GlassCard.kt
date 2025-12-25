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
import com.ivarna.fluxlinux.ui.theme.GlassBorder
import com.ivarna.fluxlinux.ui.theme.GlassWhiteLow
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember

@Composable
fun DistroCard(
    distro: Distro,
    isInstalled: Boolean = false,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onLaunchCli: () -> Unit,
    onLaunchGui: () -> Unit,
    onAlreadyInstalled: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(16.dp))
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
                                        Color(0xFFFF00E6),
                                        Color(0xFF00E5FF)
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
                        
                        if (distro.comingSoon) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color(0xFFFFB74D),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "COMING SOON",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = distro.id,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
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
                                if (distro.prootSupported) Color(0xFF4CAF50) else Color(0xFF757575),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "PRoot",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Chroot Badge
                    Box(
                        modifier = Modifier
                            .background(
                                if (distro.chrootSupported) Color(0xFF2196F3) else Color(0xFF757575),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Chroot",
                            color = Color.White,
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
                    onClick = if (distro.comingSoon) { {} } else onInstall,
                    enabled = !distro.comingSoon,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        disabledContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (distro.comingSoon) "Coming Soon" else "Install",
                        color = if (distro.comingSoon) 
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) 
                        else 
                            androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                    )
                }
                
                // Manual Sync Option - only show for available distros
                if (!distro.comingSoon) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    androidx.compose.material3.TextButton(
                        onClick = onAlreadyInstalled,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            "Already Installed?", 
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                // Show CLI/GUI/Uninstall when installed
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onLaunchCli,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CLI", color = Color.Black) // Cyan bg, black text is fine
                    }

                    Spacer(modifier = Modifier.size(8.dp))

                    Button(
                        onClick = onLaunchGui,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF00E6)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("GUI", color = Color.Black) // Magenta bg, black text is fine
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Uninstall Button
                androidx.compose.material3.TextButton(
                    onClick = onUninstall,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Uninstall", color = Color(0xFFFF6B6B))
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
