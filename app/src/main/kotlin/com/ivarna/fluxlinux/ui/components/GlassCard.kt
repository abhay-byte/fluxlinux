package com.ivarna.fluxlinux.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.ui.theme.FluxAccentCyan

private val CardShape = RoundedCornerShape(14.dp)
private val MethodProot = Color(0xFF00B8D4)
private val MethodChroot = Color(0xFFE040FB)
private val StatusRunning = Color(0xFF4CAF50)
private val StatusStarting = Color(0xFFFFA000)

fun Distro.isChrootCard(): Boolean =
    id.contains("chroot", ignoreCase = true) || (chrootSupported && !prootSupported)

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
    val isChroot = distro.isChrootCard()
    val methodColor = if (isChroot) MethodChroot else MethodProot
    val colors = MaterialTheme.colorScheme
    val showLogs = logsAvailable || isGuiStarting || isGuiRunning

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(CardShape)
            .background(colors.surface.copy(alpha = 0.72f))
            .border(BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)), CardShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(methodColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (distro.iconRes != null) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = distro.iconRes),
                            contentDescription = "${distro.name} logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(colors.primary, colors.tertiary)
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = distro.name.removeSuffix(" (Rooted)"),
                                color = colors.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            MethodChip(
                                label = if (isChroot) "CHROOT" else "PROOT",
                                color = methodColor
                            )
                            if (isInstalled && (isGuiRunning || isGuiStarting)) {
                                Spacer(modifier = Modifier.width(4.dp))
                                MethodChip(
                                    label = if (isGuiStarting) "STARTING" else "RUNNING",
                                    color = if (isGuiStarting) StatusStarting else StatusRunning
                                )
                            }
                            if (distro.comingSoon) {
                                Spacer(modifier = Modifier.width(4.dp))
                                MethodChip(
                                    label = "SOON",
                                    color = colors.secondary
                                )
                            }
                        }
                        Text(
                            text = distro.id,
                            color = colors.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isInstalled) {
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = colors.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (!isInstalled && distro.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = distro.description,
                        color = colors.onSurface.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!isInstalled) {
                    CompactAction(
                        label = when {
                            isCurrentlyInstalling -> "Progress"
                            isGlobalInstalling -> "Busy"
                            distro.comingSoon -> "Soon"
                            else -> "Install"
                        },
                        onClick = if (distro.comingSoon || (isGlobalInstalling && !isCurrentlyInstalling)) {
                            {}
                        } else {
                            onInstall
                        },
                        enabled = !distro.comingSoon && (!isGlobalInstalling || isCurrentlyInstalling),
                        containerColor = colors.secondary,
                        contentColor = colors.onSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (isGuiRunning || isGuiStarting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactAction(
                            label = "Open",
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            onClick = onOpenDisplay,
                            enabled = isGuiRunning || isGuiStarting,
                            containerColor = FluxAccentCyan,
                            contentColor = Color.Black,
                            modifier = Modifier.weight(1f)
                        )
                        CompactAction(
                            label = "Stop",
                            icon = Icons.Default.Stop,
                            onClick = onStop,
                            containerColor = Color(0xFFFF5252),
                            contentColor = Color.White,
                            modifier = Modifier.weight(0.85f)
                        )
                        if (showLogs) {
                            CompactAction(
                                label = "Logs",
                                icon = Icons.AutoMirrored.Filled.Article,
                                onClick = onViewLogs,
                                containerColor = colors.secondaryContainer,
                                contentColor = colors.onSecondaryContainer,
                                modifier = Modifier.weight(0.85f)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactAction(
                            label = "Start",
                            icon = Icons.Default.PlayArrow,
                            onClick = onNavigateToStart,
                            containerColor = colors.secondary,
                            contentColor = colors.onSecondary,
                            modifier = Modifier.weight(1.2f)
                        )
                        if (showLogs) {
                            CompactAction(
                                label = "Logs",
                                icon = Icons.AutoMirrored.Filled.Article,
                                onClick = onViewLogs,
                                containerColor = colors.secondaryContainer,
                                contentColor = colors.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class MethodTab { PROOT, CHROOT }

@Composable
fun MethodTabs(
    selected: MethodTab,
    onSelected: (MethodTab) -> Unit,
    modifier: Modifier = Modifier,
    prootCount: Int? = null,
    chrootCount: Int? = null
) {
    val shape = RoundedCornerShape(12.dp)
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(40.dp)
            .clip(shape)
            .background(colors.surface.copy(alpha = 0.72f))
            .border(BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)), shape)
    ) {
        MethodTabSegment(
            label = if (prootCount != null) "PRoot  $prootCount" else "PRoot",
            selected = selected == MethodTab.PROOT,
            accent = MethodProot,
            onClick = { onSelected(MethodTab.PROOT) },
            modifier = Modifier.weight(1f)
        )
        MethodTabSegment(
            label = if (chrootCount != null) "Chroot  $chrootCount" else "Chroot",
            selected = selected == MethodTab.CHROOT,
            accent = MethodChroot,
            onClick = { onSelected(MethodTab.CHROOT) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MethodTabSegment(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(3.dp)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
fun MethodChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.45f)), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun CompactAction(
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.35f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = modifier.height(36.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
