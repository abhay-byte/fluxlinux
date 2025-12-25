package com.fluxlinux.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxlinux.app.core.data.Distro
import com.fluxlinux.app.ui.theme.GlassBorder
import com.fluxlinux.app.ui.theme.GlassWhiteLow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun DistroCard(
    distro: Distro,
    hazeState: HazeState,
    isInstalled: Boolean = false,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onLaunchCli: () -> Unit,
    onLaunchGui: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .hazeChild(
                state = hazeState,
                style = HazeMaterials.regular()
            )
            .border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Placeholder
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

                Spacer(modifier = Modifier.size(12.dp))

                Column {
                    Text(
                        text = distro.name,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = distro.id,
                        color = Color(0xFFDDDDDD),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                text = distro.description,
                color = Color(0xFFDDDDDD),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons - Conditional based on installation status
            if (!isInstalled) {
                // Show Install button when not installed
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Install", color = Color.White)
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
                        Text("CLI", color = Color.Black)
                    }

                    Spacer(modifier = Modifier.size(8.dp))

                    Button(
                        onClick = onLaunchGui,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF00E6)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("GUI", color = Color.Black)
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
