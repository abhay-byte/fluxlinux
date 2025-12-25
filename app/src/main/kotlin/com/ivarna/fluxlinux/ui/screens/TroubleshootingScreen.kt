package com.ivarna.fluxlinux.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.ui.components.GlassSettingCard
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

data class TroubleshootingItem(
    val title: String,
    val content: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshootingScreen(
    onBack: () -> Unit
) {
    val hazeState = remember { HazeState() }
    
    val issues = listOf(
        TroubleshootingItem("Termux Connection Failed", "Run the fix command from the Home screen connection panel. Ensure 'Allow External Apps' is true in ~/.termux/termux.properties."),
        TroubleshootingItem("GUI Not Starting", "Check if Termux:X11 is installed. Ensure you selected the correct display ID (usually 0). Try 'export DISPLAY=:0' in Termux."),
        TroubleshootingItem("Audio Issues", "Audio forwarding requires Pulseaudio. Install it in Termux: 'pkg install pulseaudio' and start it with 'pulseaudio --start'."),
        TroubleshootingItem("Performance is Low", "Enable hardware acceleration in Settings if supported. Ensure you are not running heavy generic kernels without GPU drivers."),
        TroubleshootingItem("Touch Input Offset", "Check screen resolution settings in Termux:X11 preferences. Matches device resolution?")
    )

    Box(
        modifier = Modifier.fillMaxSize()
            .background(
                 Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F0C29), Color(0xFF24243E))
                )
            )
            .haze(state = hazeState)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Troubleshooting", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            LazyColumn(
                contentPadding = innerPadding,
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(issues) { issue ->
                    ExpandableIssueCard(issue)
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun ExpandableIssueCard(issue: TroubleshootingItem) {
    var expanded by remember { mutableStateOf(false) }

    GlassSettingCard(
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = issue.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = issue.content,
                        color = Color(0xFFDDDDDD),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
