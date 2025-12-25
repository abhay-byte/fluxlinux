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

@Composable
fun DistroCard(
    distro: Distro,
    onInstall: () -> Unit,
    onLaunch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GlassWhiteLow)
            .border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Placeholder Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(distro.color)
                )
                
                Spacer(modifier = Modifier.size(12.dp))
                
                Text(
                    text = distro.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = distro.description,
                color = Color.LightGray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Actions
            Row {
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = GlassWhiteLow),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Install", color = Color.White)
                }
                
                Spacer(modifier = Modifier.size(8.dp))
                
                Button(
                    onClick = onLaunch,
                    colors = ButtonDefaults.buttonColors(containerColor = distro.color),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Launch", color = Color.White)
                }
            }
        }
    }
}
