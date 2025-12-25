package com.fluxlinux.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxlinux.app.core.utils.StateManager
import com.fluxlinux.app.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val hazeState = remember { HazeState() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0C29),
                        Color(0xFF302B63),
                        Color(0xFF24243E)
                    )
                )
            )
    ) {
        // Background layer for blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState)
        )
        
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Settings",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // General Settings
                GeneralSettingsSection(hazeState)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Advanced Settings
                AdvancedSettingsSection(hazeState)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Storage Management
                StorageManagementSection(hazeState)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // About
                AboutSection(hazeState)
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GeneralSettingsSection(hazeState: HazeState) {
    val context = LocalContext.current
    var defaultLaunchMode by remember { mutableStateOf("CLI") }
    var autoUpdateCheck by remember { mutableStateOf(true) }
    
    SettingsCard(hazeState, "General Settings") {
        // Default Launch Mode
        SettingItem(
            title = "Default Launch Mode",
            description = "Choose CLI or GUI for quick launch"
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = defaultLaunchMode == "CLI",
                    onClick = { defaultLaunchMode = "CLI" },
                    label = { Text("CLI") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FluxAccentCyan,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = defaultLaunchMode == "GUI",
                    onClick = { defaultLaunchMode = "GUI" },
                    label = { Text("GUI") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FluxAccentMagenta,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Auto-update Check
        SettingItem(
            title = "Auto-update Check",
            description = "Check for app updates on launch"
        ) {
            Switch(
                checked = autoUpdateCheck,
                onCheckedChange = { autoUpdateCheck = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = FluxAccentCyan,
                    checkedTrackColor = FluxAccentCyan.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AdvancedSettingsSection(hazeState: HazeState) {
    var installMode by remember { mutableStateOf("PRoot") }
    var gpuAcceleration by remember { mutableStateOf(false) }
    
    SettingsCard(hazeState, "Advanced Settings") {
        // Installation Mode
        SettingItem(
            title = "Installation Mode",
            description = "PRoot (No Root) or Chroot (Root Required)"
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = installMode == "PRoot",
                        onClick = { installMode = "PRoot" },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = FluxAccentCyan
                        )
                    )
                    Text("🔓 PRoot (No Root)", color = Color.White, fontSize = 14.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = installMode == "Chroot",
                        onClick = { installMode = "Chroot" },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = FluxAccentCyan
                        )
                    )
                    Text("🔐 Chroot ", color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Root Required",
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Color(0xFFFF6B6B).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // GPU Acceleration
        SettingItem(
            title = "GPU Acceleration",
            description = "VirGL/Zink for better graphics (Experimental)"
        ) {
            Switch(
                checked = gpuAcceleration,
                onCheckedChange = { gpuAcceleration = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = FluxAccentMagenta,
                    checkedTrackColor = FluxAccentMagenta.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun StorageManagementSection(hazeState: HazeState) {
    val context = LocalContext.current
    
    SettingsCard(hazeState, "Storage Management") {
        // Installed Distros
        Text(
            "Installed Distros",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Example distro (would be dynamic in real implementation)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Debian", color = Color(0xFFDDDDDD), fontSize = 14.sp)
            Text("2.3 GB", color = Color(0xFFDDDDDD), fontSize = 14.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Clear Cache Button
        Button(
            onClick = {
                android.widget.Toast.makeText(context, "Cache cleared", android.widget.Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = GlassBorder),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Clear Cache", color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Reset All Button
        Button(
            onClick = {
                // Would show confirmation dialog
                android.widget.Toast.makeText(context, "Reset All (requires confirmation)", android.widget.Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Reset All", color = Color.White)
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AboutSection(hazeState: HazeState) {
    val context = LocalContext.current
    
    SettingsCard(hazeState, "About") {
        // App Version
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Version", color = Color.White, fontSize = 14.sp)
            Text("1.0.0", color = Color(0xFFDDDDDD), fontSize = 14.sp)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // GitHub Link
        TextButton(
            onClick = {
                // Would open browser
                android.widget.Toast.makeText(context, "Opening GitHub...", android.widget.Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🔗 View on GitHub", color = FluxAccentCyan)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // License
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("License", color = Color.White, fontSize = 14.sp)
            Text("MIT", color = Color(0xFFDDDDDD), fontSize = 14.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Check for Updates
        Button(
            onClick = {
                android.widget.Toast.makeText(context, "Checking for updates...", android.widget.Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = FluxAccentCyan),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Check for Updates", color = Color.Black)
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun SettingsCard(
    hazeState: HazeState,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .hazeChild(
                state = hazeState,
                style = HazeMaterials.regular(
                    containerColor = GlassWhiteLow
                )
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        content()
    }
}

@Composable
fun SettingItem(
    title: String,
    description: String,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = Color(0xFFDDDDDD),
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        control()
    }
}
