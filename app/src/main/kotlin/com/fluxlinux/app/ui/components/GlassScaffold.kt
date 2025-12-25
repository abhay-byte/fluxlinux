package com.fluxlinux.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.fluxlinux.app.ui.theme.FluxBackgroundEnd
import com.fluxlinux.app.ui.theme.FluxBackgroundMid
import com.fluxlinux.app.ui.theme.FluxBackgroundStart
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
fun GlassScaffold(
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (HazeState) -> Unit
) {
    val hazeState = androidx.compose.runtime.remember { HazeState() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        FluxBackgroundStart,
                        FluxBackgroundMid,
                        FluxBackgroundEnd
                    )
                )
            )
    ) {
        // Background layer - source for blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState)
        )
        
        // Content layer - no blur here, components handle their own
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = bottomBar,
            content = { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    content(hazeState)
                }
            }
        )
    }
}
