package com.ivarna.fluxlinux.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import com.ivarna.fluxlinux.ui.theme.FluxBackgroundEnd
import com.ivarna.fluxlinux.ui.theme.FluxBackgroundMid
import com.ivarna.fluxlinux.ui.theme.FluxBackgroundStart
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
fun GlassScaffold(
    hazeState: HazeState,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    // val hazeState = androidx.compose.runtime.remember { HazeState() } // Lifted up
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
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
            topBar = topBar,
            bottomBar = bottomBar,
            content = { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    content()
                }
            }
        )
    }
}
