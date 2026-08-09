package com.ivarna.fluxlinux.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.data.DistroRepository
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.ScriptManager
import com.ivarna.fluxlinux.core.data.TermuxIntentFactory

import com.ivarna.fluxlinux.core.utils.StateManager
import com.ivarna.fluxlinux.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun HomeScreen(
    permissionState: PermissionState,
    hazeState: HazeState,
    scriptRefreshTrigger: Int = 0,
    onStartService: (android.content.Intent) -> Unit,
    onStartActivity: (android.content.Intent) -> Unit,
    onNavigateToInstall: (com.ivarna.fluxlinux.core.data.Distro) -> Unit,
    onNavigateToSettings: (com.ivarna.fluxlinux.core.data.Distro) -> Unit,
    onOpenTerminal: (distroId: String, root: Boolean) -> Unit = { _, _ -> },
    /** Switch bottom-nav to Terminal after an in-app session is opened (e.g. Qwen). */
    onShowTerminal: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // State for Launch Popup
    val distroToLaunch = remember { mutableStateOf<com.ivarna.fluxlinux.core.data.Distro?>(null) }
    // State for KDE GPU mode picker (sub-dialog)
    val showKdeGpuPicker = remember { mutableStateOf<com.ivarna.fluxlinux.core.data.Distro?>(null) }
    
    // Refresh key to trigger recomposition
    val refreshKey = remember { mutableStateOf(0) }

    // React to external refresh trigger (from MainActivity)
    LaunchedEffect(scriptRefreshTrigger) {
        if (scriptRefreshTrigger > 0) {
            refreshKey.value++
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Trigger initial refresh on mount
        LaunchedEffect(Unit) {
            refreshKey.value++
        }
        
        // Installed Distros Detection — filesystem truth (plan P4-T13): a stale
        // "installed" pref without a rootfs on disk must show Install, not a broken shell.
        val installedDistros = remember(refreshKey.value) {
            DistroRepository.supportedDistros.filter {
                com.ivarna.fluxlinux.core.terminal.TerminalLauncher.isDistroInstalledOnFs(context, it.id)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        

        
        // Installed Distros Section
        Text(
            text = "Installed Distros",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Show empty state or distro list
        if (installedDistros.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No distros installed yet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Install a distribution from the Distros tab",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Distro list
            installedDistros.forEach { distro ->
                com.ivarna.fluxlinux.ui.components.DistroCard(
                    distro = distro,
                    isInstalled = true,
                    isGuiRunning = StateManager.isGuiRunning(context, distro.id),
                    onInstall = { onNavigateToInstall(distro) },
                    onUninstall = { /* Handled in Settings */ }, 
                    onNavigateToSettings = { onNavigateToSettings(distro) },
                    onNavigateToStart = { distroToLaunch.value = distro },
                    onOpenDisplay = {
                        if (!com.ivarna.fluxlinux.core.utils.EmbeddedX11.launchDisplay(context)) {
                            android.widget.Toast.makeText(context, "Termux:X11 not available", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onStop = {
                        val runningType = StateManager.getGuiRunningType(context, distro.id)
                        try {
                            if (runningType == "kde") {
                                // KDE still uses legacy intent until fully ported
                                if (StateManager.canRunCommands(context)) {
                                    val intent = TermuxIntentFactory.buildStopKdeGuiIntent(context, distro.id)
                                    onStartService(intent)
                                    StateManager.setGuiRunning(context, distro.id, false)
                                    StateManager.setGuiRunningType(context, distro.id, "")
                                    android.widget.Toast.makeText(context, "Stopping KDE Plasma...", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    permissionState.launchPermissionRequest()
                                }
                            } else {
                                // Embedded XFCE stop — no Termux-era canRunCommands gate
                                com.ivarna.fluxlinux.core.desktop.DesktopLauncher.stop(context, distro.id)
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Stop failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    
    Spacer(modifier = Modifier.height(100.dp))
    
    // Launch Popup
    if (distroToLaunch.value != null) {
        val distro = distroToLaunch.value!!
        AlertDialog(
            onDismissRequest = { distroToLaunch.value = null },
            title = { 
                Text(
                    "Start ${distro.name}", 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            },
            text = { Text("Choose how you want to launch the distribution.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { distroToLaunch.value = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface) 
                }
            },
            icon = {
                 Icon(
                     imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                     contentDescription = null,
                     tint = FluxAccentCyan
                 )
            },
            // Custom Layout for Buttons
            // Using a Row with two big buttons? LIMITATION: AlertDialog has specific slots.
            // We can put the buttons in the "text" part or just use confirm/dismiss as actions?
            // Better to use the text part to house the buttons for vertical stacking or a Row.
        )
        // AlertDialog is a bit restrictive for 2 "positive" actions.
        // Let's use a custom Dialog or just use the Buttons in the text area?
        // Actually, we can just put a Column in the 'text' slot.
    }
    
    // Custom Launch Dialog
    if (distroToLaunch.value != null) {
        val distro = distroToLaunch.value!!
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { distroToLaunch.value = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false // Allow full width customization
            ) 
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1E1E1E).copy(alpha = 0.95f),
                                Color(0xFF121212).copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(1.dp, Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )),
                        RoundedCornerShape(28.dp)
                    )
            ) {
                // Glow effect behind the top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Icon
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (distro.iconRes != null) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = distro.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                        } else {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Start ${distro.name}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = "Choose launch mode",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // CLI Button — opens in-app terminal session (termux-flux-terminal /
                    // chroot-root-shell), no external Termux required.
                    Button(
                        onClick = {
                            onOpenTerminal(distro.id, false)
                            distroToLaunch.value = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Open Shell", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Root Terminal Button (chroot → chroot-root-shell; proot → shell-root card)
                    Button(
                        onClick = {
                            onOpenTerminal(distro.id, true)
                            distroToLaunch.value = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔓 Open Root Shell", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // LLM Model Button (Qwen3.5 interactive chat)
                    val llmInstalled = StateManager.isComponentInstalled(context, distro.id, "vulkan_llamacpp")
                    val modelInstalled = StateManager.isComponentInstalled(context, distro.id, "qwen25_model")
                    
                    if (llmInstalled && modelInstalled) {
                        Button(
                            onClick = {
                                // Run Qwen inside the in-app proot terminal (no Termux intent)
                                val scriptManager = ScriptManager(context)
                                val scriptContent = scriptManager.getScriptContent("debian/common/addon/launch_qwen25.sh")
                                val scriptB64 = android.util.Base64.encodeToString(
                                    scriptContent.toByteArray(), android.util.Base64.NO_WRAP
                                )
                                val payload = "echo '$scriptB64' | base64 -d | bash"
                                com.ivarna.fluxlinux.core.terminal.FluxTerminalSessionManager.openSessionAfterHost(
                                    context,
                                    type = "shell",
                                    title = "Qwen2.5-1.5B",
                                    shellCmd = payload,
                                    method = com.ivarna.fluxlinux.core.data.terminalComponentFor(distro.id).method,
                                    onResult = { result ->
                                        if (result == com.ivarna.fluxlinux.core.terminal.FluxTerminalSessionManager.SessionOpenResult.OPENED) {
                                            onShowTerminal()
                                        }
                                    }
                                )
                                distroToLaunch.value = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7C4DFF).copy(alpha = 0.85f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🤖 Run Qwen2.5-1.5B", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // GUI Buttons — separate for XFCE4 and KDE
                    val kdeInstalled = StateManager.isComponentInstalled(context, distro.id, "kde_plasma")

                    // XFCE4 — embedded host start_gui + in-app X11 (no external Termux)
                    Button(
                        onClick = {
                            try {
                                // Flags + X11 prefs owned by DesktopLauncher on success
                                com.ivarna.fluxlinux.core.desktop.DesktopLauncher.start(context, distro.id)
                                distroToLaunch.value = null
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Launch failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4A148C).copy(alpha = 0.85f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("🖥 Launch XFCE4", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // KDE Plasma — opens GPU picker if installed
                    Button(
                        onClick = {
                            if (kdeInstalled && com.ivarna.fluxlinux.core.utils.StateManager.canRunCommands(context)) {
                                // Show GPU mode picker sub-dialog
                                showKdeGpuPicker.value = distro
                            } else if (!kdeInstalled) {
                                android.widget.Toast.makeText(context, "Install KDE Plasma Desktop first from Settings.", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                permissionState.launchPermissionRequest()
                            }
                        },
                        enabled = true, // Always tappable — shows toast if not installed
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (kdeInstalled) Color(0xFF1A237E).copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (kdeInstalled) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(
                            if (kdeInstalled) "🌊 Launch KDE Plasma" else "🌊 Launch KDE (Not Installed)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }


                    val isGuiRunning = StateManager.isGuiRunning(context, distro.id)
                    val runningType = StateManager.getGuiRunningType(context, distro.id)
                    if (isGuiRunning) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (runningType == "kde") {
                                    // KDE still uses legacy intent until fully ported
                                    val intent = TermuxIntentFactory.buildStopKdeGuiIntent(context, distro.id)
                                    onStartService(intent)
                                    StateManager.setGuiRunning(context, distro.id, false)
                                    StateManager.setGuiRunningType(context, distro.id, "")
                                } else {
                                    // Flags cleared inside DesktopLauncher.stop
                                    com.ivarna.fluxlinux.core.desktop.DesktopLauncher.stop(context, distro.id)
                                }
                                distroToLaunch.value = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text(
                                if (runningType == "kde") "⏹ Stop KDE Plasma" else "⏹ Stop XFCE4",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    androidx.compose.material3.TextButton(
                        onClick = { distroToLaunch.value = null },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Cancel", 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // ─── KDE GPU Mode Picker Sub-Dialog ───────────────────────────────────────
    if (showKdeGpuPicker.value != null) {
        val distro = showKdeGpuPicker.value!!
        KdeGpuPickerDialog(
            distro = distro,
            onDismiss = { showKdeGpuPicker.value = null },
            onSelectVirGL = {
                val intent = TermuxIntentFactory.buildLaunchKdeGuiIntent(context, distro.id)
                try {
                    onStartService(intent)
                    StateManager.setGuiRunning(context, distro.id, true)
                    StateManager.setGuiRunningType(context, distro.id, "kde")
                    com.ivarna.fluxlinux.core.utils.EmbeddedX11.launchDisplay(context)
                    com.ivarna.fluxlinux.core.utils.TermuxX11Preferences.applyToTermux(context)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Launch failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
                showKdeGpuPicker.value = null
                distroToLaunch.value = null
            },
            onSelectTurnip = {
                val intent = TermuxIntentFactory.buildLaunchKdeGuiTurnipIntent(context, distro.id)
                try {
                    onStartService(intent)
                    StateManager.setGuiRunning(context, distro.id, true)
                    StateManager.setGuiRunningType(context, distro.id, "kde")
                    com.ivarna.fluxlinux.core.utils.EmbeddedX11.launchDisplay(context)
                    com.ivarna.fluxlinux.core.utils.TermuxX11Preferences.applyToTermux(context)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Launch failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
                showKdeGpuPicker.value = null
                distroToLaunch.value = null
            },
            onSelectSoftware = {
                val intent = TermuxIntentFactory.buildLaunchKdeGuiSoftwareIntent(context, distro.id)
                try {
                    onStartService(intent)
                    StateManager.setGuiRunning(context, distro.id, true)
                    StateManager.setGuiRunningType(context, distro.id, "kde")
                    com.ivarna.fluxlinux.core.utils.EmbeddedX11.launchDisplay(context)
                    com.ivarna.fluxlinux.core.utils.TermuxX11Preferences.applyToTermux(context)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Launch failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
                showKdeGpuPicker.value = null
                distroToLaunch.value = null
            }
        )
    }
}

// ─── KDE GPU Picker Dialog ─────────────────────────────────────────────────────
@Composable
private fun KdeGpuPickerDialog(
    distro: com.ivarna.fluxlinux.core.data.Distro,
    onDismiss: () -> Unit,
    onSelectVirGL: () -> Unit,
    onSelectTurnip: () -> Unit,
    onSelectSoftware: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E1E1E).copy(alpha = 0.95f),
                            Color(0xFF121212).copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    BorderStroke(1.dp, Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )),
                    RoundedCornerShape(28.dp)
                )
        ) {
            // Top glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "🌊 KDE Plasma",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose GPU Renderer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.55f)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // GPU Option Cards — Vertical List of Horizontal Cards
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Turnip Card ─────────────────────────
                    GpuOptionCard(
                        modifier = Modifier.fillMaxWidth(),
                        emoji = "🔥",
                        title = "Turnip",
                        subtitle = "Vulkan",
                        description = "Hardware Vulkan via Adreno GPU. Best performance.",
                        accentColor = Color(0xFFFF6F00),
                        badgeText = "Adreno Only",
                        onClick = onSelectTurnip
                    )

                    // ── Software Card ───────────────────────
                    GpuOptionCard(
                        modifier = Modifier.fillMaxWidth(),
                        emoji = "🖥️",
                        title = "Software",
                        subtitle = "LLVMpipe",
                        description = "CPU-only. No GPU required. Works on all devices.",
                        accentColor = Color(0xFF4CAF50),
                        badgeText = "Safe Fallback",
                        onClick = onSelectSoftware
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Info note
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "ℹ️  Turnip: Requires Adreno GPU with Vulkan • Software: Works on any device (slower)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }


                Spacer(modifier = Modifier.height(16.dp))

                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Cancel",
                        color = Color.White.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ─── Reusable GPU Option Card ──────────────────────────────────────────────────
@Composable
private fun GpuOptionCard(
    modifier: Modifier = Modifier,
    emoji: String,
    title: String,
    subtitle: String,
    description: String,
    accentColor: Color,
    badgeText: String?,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        accentColor.copy(alpha = 0.15f),
                        accentColor.copy(alpha = 0.05f)
                    )
                )
            )
            .border(
                BorderStroke(1.dp, Brush.verticalGradient(
                    listOf(
                        accentColor.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                )),
                RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji icon in glowing circle
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 26.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    
                    // Experimental/Fallback badge
                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.25f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 9.sp,
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = accentColor.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Start,
                    lineHeight = 15.sp
                )
            }
        }
    }
}



