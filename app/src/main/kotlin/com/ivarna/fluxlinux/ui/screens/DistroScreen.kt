package com.ivarna.fluxlinux.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivarna.fluxlinux.core.data.DistroRepository
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.ScriptManager
import com.ivarna.fluxlinux.core.data.TermuxIntentFactory
import com.ivarna.fluxlinux.core.utils.StateManager
import com.ivarna.fluxlinux.ui.components.DistroCard
import com.ivarna.fluxlinux.ui.theme.FluxAccentMagenta
import com.ivarna.fluxlinux.ui.theme.GlassWhiteMedium
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DistroScreen(
    permissionState: PermissionState,
    hazeState: HazeState,
    onStartService: (android.content.Intent) -> Unit,
    onStartActivity: (android.content.Intent) -> Unit
) {
    val context = LocalContext.current
    
    // State for Uninstall Dialog
    val distroToUninstall = remember { mutableStateOf<com.ivarna.fluxlinux.core.data.Distro?>(null) }
    // State for Install Dialog
    val distroToInstall = remember { mutableStateOf<com.ivarna.fluxlinux.core.data.Distro?>(null) }
    
    // Refresh mechanism to check install status
    val refreshKey = remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshKey.value++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "Available Distros",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.Start)
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Distro List
        val installedDistroIds = remember(refreshKey.value) {
            StateManager.getInstalledDistros(context)
        }
        
        val availableDistros = DistroRepository.supportedDistros.filter { 
            !installedDistroIds.contains(it.id)
        }
        
        if (availableDistros.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "All available distros are installed!",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            availableDistros.forEach { distro ->
                if (distro.comingSoon) {
                    // Use compact card for coming soon distros
                    com.ivarna.fluxlinux.ui.components.CompactDistroCard(
                        distro = distro
                    )
                } else {
                    // Use full card for available distros
                    com.ivarna.fluxlinux.ui.components.DistroCard(
                        distro = distro,
                        isInstalled = false,
                        onInstall = {
                            if (permissionState.status.isGranted) {
                                distroToInstall.value = distro
                            } else {
                                permissionState.launchPermissionRequest()
                            }
                        },
                        onUninstall = {}, // Not used
                        onLaunchCli = {}, // Not used
                        onLaunchGui = {} // Not used
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(100.dp)) // Spacing for Bottom Nav
    }
    
    // Install Confirmation Dialog
    if (distroToInstall.value != null) {
        val distro = distroToInstall.value!!
        AlertDialog(
            onDismissRequest = { distroToInstall.value = null },
            title = { Text("Install ${distro.name}?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = { Text("This will install ${distro.name} using PRoot. It may take some time depending on your internet connection.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                Button(
                    onClick = {
                        // 1. Get Setup Script
                        val scriptManager = ScriptManager(context)
                        val setupScript = if (distro.configuration?.family == com.ivarna.fluxlinux.core.model.DistroFamily.TERMUX) {
                            // Bundle all Termux scripts
                            val baseInstall = scriptManager.getScriptContent("termux/install.sh")
                            val appsInstall = scriptManager.getScriptContent("termux/install_apps.sh")
                            val themeInstall = scriptManager.getScriptContent("termux/setup_theme.sh")
                            "$baseInstall\n$appsInstall\n$themeInstall"
                        } else {
                            val scriptName = when (distro.configuration?.family) {
                                com.ivarna.fluxlinux.core.model.DistroFamily.DEBIAN -> "debian/setup.sh"
                                else -> "debian/setup.sh"
                            }
                            scriptManager.getScriptContent(scriptName)
                        }
                        
                        // 2. Generate Command
                        val command = TermuxIntentFactory.getInstallCommand(distro.id, setupScript)
                        
                        // 3. Copy to Clipboard
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("FluxLinux Install", command)
                        clipboard.setPrimaryClip(clip)
                        
                        // 4. Open Termux
                        val launchIntent = TermuxIntentFactory.buildOpenTermuxIntent(context)
                        if (launchIntent != null) {
                            try {
                                onStartActivity(launchIntent)
                                // Optimistic update removed - relying on script callback
                                // StateManager.setDistroInstalled(context, distro.id, true)
                                // refreshKey.value++ 
                                android.widget.Toast.makeText(context, "Command Copied! Paste in Termux.", android.widget.Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                android.util.Log.e("FluxLinux", "Failed to open Termux", e)
                                android.widget.Toast.makeText(context, "Failed to open Termux", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            android.widget.Toast.makeText(context, "Termux app not found!", android.widget.Toast.LENGTH_LONG).show()
                        }
                        
                        distroToInstall.value = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Install", color = MaterialTheme.colorScheme.onPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { distroToInstall.value = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
            }
        )
    }

    // Uninstall Dialog
    if (distroToUninstall.value != null) {
        val distro = distroToUninstall.value!!
        AlertDialog(
            onDismissRequest = { distroToUninstall.value = null },
            title = { Text("Uninstall ${distro.name}?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = { Text("This will delete all data associated with this distribution. This action cannot be undone.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = TermuxIntentFactory.buildUninstallIntent(distro.id)
                        try {
                            onStartService(intent)
                            StateManager.setDistroInstalled(context, distro.id, false)
                            android.widget.Toast.makeText(context, "Uninstalling ${distro.name}...", android.widget.Toast.LENGTH_SHORT).show()
                            refreshKey.value++
                        } catch (e: Exception) {
                            android.util.Log.e("FluxLinux", "Uninstall failed", e)
                        }
                        distroToUninstall.value = null
                    }
                ) { Text("Uninstall", color = FluxAccentMagenta) }
            },
            dismissButton = {
                TextButton(onClick = { distroToUninstall.value = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
            }
        )
    }
}
