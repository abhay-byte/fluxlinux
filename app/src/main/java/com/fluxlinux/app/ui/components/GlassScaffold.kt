package com.fluxlinux.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.fluxlinux.app.ui.theme.FluxBackgroundEnd
import com.fluxlinux.app.ui.theme.FluxBackgroundMid
import com.fluxlinux.app.ui.theme.FluxBackgroundStart

@Composable
fun GlassScaffold(
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
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
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent, // Critical for gradient visibility
            bottomBar = bottomBar,
            content = { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    content()
                }
            }
        )
    }
}
