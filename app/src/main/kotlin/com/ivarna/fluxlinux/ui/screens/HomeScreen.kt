package com.ivarna.fluxlinux.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
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
import com.ivarna.fluxlinux.core.utils.ApkInstaller
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
    onStartService: (android.content.Intent) -> Unit,
    onStartActivity: (android.content.Intent) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // State for Uninstall Dialog
    val distroToUninstall = remember { mutableStateOf<com.ivarna.fluxlinux.core.data.Distro?>(null) }
    
    // Refresh key to trigger recomposition
    val refreshKey = remember { mutableStateOf(0) }
    

    
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
        
        // Installed Distros Detection
        val installedDistros = remember(refreshKey.value) {
            val installedIds = StateManager.getInstalledDistros(context)
            DistroRepository.supportedDistros.filter { installedIds.contains(it.id) }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Installed Distros Section
        Text(
            text = "Installed Distros",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
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
                    onInstall = {}, // Won't be clicked
                    onUninstall = {
                        if (permissionState.status.isGranted) {
                            distroToUninstall.value = distro
                        } else {
                            permissionState.launchPermissionRequest()
                        }
                    },
                    onLaunchCli = {
                        if (permissionState.status.isGranted) {
                            val intent = TermuxIntentFactory.buildLaunchCliIntent(distro.id)
                            try {
                                onStartService(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("FluxLinux", "Launch CLI failed", e)
                            }
                        } else {
                            permissionState.launchPermissionRequest()
                        }
                    },
                    onLaunchGui = {
                        if (permissionState.status.isGranted) {
                            val intent = TermuxIntentFactory.buildLaunchGuiIntent(distro.id)
                            try {
                                onStartService(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("FluxLinux", "Launch GUI failed", e)
                            }
                        } else {
                            permissionState.launchPermissionRequest()
                        }
                    },
                )
            }
        }
    }


        
        Spacer(modifier = Modifier.height(100.dp))
    
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


