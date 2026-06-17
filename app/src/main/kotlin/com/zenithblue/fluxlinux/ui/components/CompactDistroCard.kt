package com.zenithblue.fluxlinux.ui.components

import com.zenithblue.fluxlinux.core.data.Distro
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenithblue.fluxlinux.ui.theme.GlassBorder

// Compact Distro Card for Coming Soon distros
@Composable
fun CompactDistroCard(
    distro: Distro
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .border(BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Distro Icon
            if (distro.iconRes != null) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = distro.iconRes),
                    contentDescription = "${distro.name} logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Distro Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = distro.name,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    // Coming Soon badge
                    Box(
                        modifier = Modifier
                            .background(
                                androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SOON",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Compatibility badges
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // PRoot Badge
                Box(
                    modifier = Modifier
                        .background(
                            if (distro.prootSupported) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "P",
                        color = if (distro.prootSupported) androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Chroot Badge
                Box(
                    modifier = Modifier
                        .background(
                            if (distro.chrootSupported) androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "C",
                        color = if (distro.chrootSupported) androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
