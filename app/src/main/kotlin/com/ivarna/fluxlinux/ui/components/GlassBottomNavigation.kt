package com.ivarna.fluxlinux.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.HazeStyle

enum class BottomTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    DISTROS("Distros", Icons.Default.List)
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GlassBottomNavigation(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(bottom = 20.dp, start = 40.dp, end = 40.dp)
            .height(64.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    blurRadius = 24.dp,
                    noiseFactor = 0f,
                    tint = null
                )
            )
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)) // Fallback/Tint
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable { onTabSelected(tab) }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
