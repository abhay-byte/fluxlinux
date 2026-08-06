package com.ivarna.fluxlinux.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.DistroComponent
import com.ivarna.fluxlinux.ui.components.GlassScaffold
import com.ivarna.fluxlinux.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

// Data Models for UI are now in ComponentData.kt


@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun InstallConfigScreen(
    distro: Distro,
    onBack: () -> Unit,
    onInstallStart: (List<DistroComponent>, String, String, String) -> Unit,
    hazeState: HazeState
) {
    var desktopEnv by remember { mutableStateOf("XFCE4") }
    var selectedTheme by remember { mutableStateOf("dark") } // dark, light, cyber
    var selectedGpu by remember { mutableStateOf("auto") } // auto, virgl, turnip, manual
    val selectedComponents = remember { mutableStateListOf<String>() }

    // Constants for Base System
    val baseSystemSize = 0.6 // ~600MB for Debian Base + XFCE

    // Calculate Total Size
    val totalSizeGB = remember(selectedComponents.size, selectedComponents.toList()) {
        var size = baseSystemSize
        selectedComponents.forEach { id ->
            size += componentDetailsMap[id]?.totalSizeValues ?: 0.0
        }
        size
    }

    // Pre-select mandatory components
    LaunchedEffect(Unit) {
        distro.components.forEach {
            if (it.isMandatory) {
                if (!selectedComponents.contains(it.id)) {
                    selectedComponents.add(it.id)
                }
            }
        }
    }

    // State Observation
    val installState by com.ivarna.fluxlinux.core.utils.InstallationQueueManager.installState.collectAsState()

    // Navigation on Completion
    LaunchedEffect(installState.isInstalling, installState.cancelledByUser) {
        if (!installState.isInstalling && installState.progressTotal > 0) {
            if (installState.cancelledByUser) {
                com.ivarna.fluxlinux.core.utils.InstallationQueueManager.clear()
            } else {
                onBack()
            }
        }
    }

    if (installState.isInstalling) {
        InstallationProgressScreen(
            state = installState,
            hazeState = hazeState,
            onCancel = {
                com.ivarna.fluxlinux.core.utils.InstallationQueueManager.cancelByUser()
            }
        )
    } else {
        GlassScaffold(
            hazeState = hazeState,
            topBar = {
                TopAppBar(
                    title = { Text("Configure ${distro.name}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier.hazeChild(state = hazeState, shape = androidx.compose.ui.graphics.RectangleShape, style = HazeMaterials.thin())
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .hazeChild(state = hazeState, shape = RoundedCornerShape(24.dp), style = HazeMaterials.thin())
                ) {
                     Button(
                        onClick = {
                            val componentsToInstall = distro.components.filter { selectedComponents.contains(it.id) }
                            onInstallStart(componentsToInstall, selectedTheme, selectedGpu, desktopEnv)
                        },
                        enabled = !installState.isInstalling,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        // Primary CTA routes to the in-app terminal component for this distro
                        // (termux-flux-terminal for proot, chroot-root-shell for chroot).
                        val ctaLabel = when (distro.id) {
                            "debian13_chroot", "debian_chroot" -> "Install in Root Shell"
                            else -> "Install in Flux Terminal"
                        }
                        Text("$ctaLabel (~${String.format("%.1f", totalSizeGB)} GB)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
            ) {
                // Section 1: Desktop Environment
                item {
                    Text(
                        text = "1. Desktop Environment",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Adaptive Card for DE Selection
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = desktopEnv == "XFCE4",
                                    onClick = { desktopEnv = "XFCE4" },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.secondary, unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                                Column {
                                    Text("XFCE4", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text("Lightweight, fast, and stable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            
                            // KDE Plasma — fully enabled
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { desktopEnv = "KDE" }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = desktopEnv == "KDE",
                                    onClick = { desktopEnv = "KDE" },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.secondary,
                                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Column {
                                    Text(
                                        "KDE Plasma",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Modern, customizable & feature-rich. (~800 MB extra)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Section 2: Theme Selection (New)
                item {
                    Text(
                        text = "2. Appearance Theme",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeOption(name = "Dark Mode (Default)", desc = "Sleek and easy on eyes.", id = "dark", selected = selectedTheme == "dark", onSelect = { selectedTheme = "dark" })
                            ThemeOption(name = "Light Mode", desc = "Clean and bright.", id = "light", selected = selectedTheme == "light", onSelect = { selectedTheme = "light" })
                        }
                    }
                }

                // Section 3: Graphics Acceleration (New)
                item {
                    Text(
                        text = "3. Graphics Acceleration",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeOption(name = "Auto Detect (Recommended)", desc = "Detects Snapdragon (Turnip) or uses VirGL.", id = "auto", selected = selectedGpu == "auto", onSelect = { selectedGpu = "auto" })
                            ThemeOption(name = "VirGL (Universal)", desc = "Compatible with most devices.", id = "virgl", selected = selectedGpu == "virgl", onSelect = { selectedGpu = "virgl" })
                            ThemeOption(name = "Turnip/Zink (Snapdragon)", desc = "High performance for Adreno.", id = "turnip", selected = selectedGpu == "turnip", onSelect = { selectedGpu = "turnip" })
                            ThemeOption(name = "Ask during Customization", desc = "Prompts you later.", id = "ask", selected = selectedGpu == "ask", onSelect = { selectedGpu = "ask" })
                        }
                    }
                }

                // Section 4: Features
                item {
                    Text(
                        text = "4. Select Features",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose add-ons to install. You can manage these later in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(distro.components) { component ->
                    ComponentSelectionCard(
                        component = component,
                        isSelected = selectedComponents.contains(component.id),
                        onToggle = { isSelected ->
                            if (component.isMandatory) return@ComponentSelectionCard
                            if (isSelected) {
                                selectedComponents.add(component.id)
                            } else {
                                selectedComponents.remove(component.id)
                            }
                        },
                        details = componentDetailsMap[component.id]
                    )
                }
            }
        }
    }
    }


@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InstallationProgressScreen(
    state: com.ivarna.fluxlinux.core.utils.InstallationState,
    hazeState: HazeState,
    onCancel: () -> Unit
) {
    var showCancelConfirm by remember { mutableStateOf(false) }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel Installation?", fontWeight = FontWeight.Bold) },
            text = { Text("The current step may continue in Termux, but remaining steps will be skipped.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    onCancel()
                }) {
                    Text("Yes, Cancel", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("Continue Waiting")
                }
            }
        )
    }

    GlassScaffold(
        hazeState = hazeState,
        topBar = {
             CenterAlignedTopAppBar(
                title = { Text("Installing...", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                OutlinedButton(
                    onClick = { showCancelConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel Installation", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 100.dp) // Space for bottomBar overlay
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated Icon
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(2.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                     Icon(
                         imageVector = Icons.Default.Speed,
                         contentDescription = null,
                         modifier = Modifier.size(64.dp),
                         tint = MaterialTheme.colorScheme.onPrimaryContainer
                     )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "Setting up your environment",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Progress Bar
                LinearProgressIndicator(
                    progress = { 
                        if (state.progressTotal > 0) state.progressCurrent.toFloat() / state.progressTotal.toFloat() else 0f 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = state.currentTaskName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Text(
                    text = "Step ${state.progressCurrent} of ${state.progressTotal}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Info Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Please Wait", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Termux is installing packages in the background. The app will notify you when ready.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ComponentSelectionCard(
    component: DistroComponent,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    details: ComponentDetail?
) {
    var expanded by remember { mutableStateOf(false) }
    val isDisabled = component.isMandatory || component.comingSoon

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    component.comingSoon -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    isSelected -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                }
            )
            .border(1.dp, if (isSelected && !component.comingSoon) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(enabled = !isDisabled) { onToggle(!isSelected) }
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(
                    checked = isSelected && !component.comingSoon,
                    onCheckedChange = null, // Handled by parent container click
                    enabled = !isDisabled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.secondary, 
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledUncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        checkmarkColor = MaterialTheme.colorScheme.onSecondary
                    )
                )
                
                Column(modifier = Modifier.padding(start = 8.dp, top = 2.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Icon if available
                        if (details != null) {
                            Icon(
                                imageVector = details.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (component.comingSoon) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                            text = component.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (component.comingSoon) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    if (component.comingSoon) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Coming Soon", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary)
                        }
                    } else if (component.isMandatory) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Required", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    } else {
                        Text(
                            text = component.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 2
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Est. Size: ${component.sizeEstimate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (component.comingSoon) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.tertiary
                    )
                }

                if (details != null) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (details != null) {
                    Column(modifier = Modifier.padding(start = 52.dp, top = 8.dp, bottom = 8.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Includes:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        details.packages.forEach { (pkg, size) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• $pkg",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = size,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeOption(name: String, desc: String, id: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.secondary)
        )
        Column {
            Text(name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
