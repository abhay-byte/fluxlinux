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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.data.DistroRepository
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
    onNavigateToSettings: () -> Unit,
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
        
    // Header with Settings Icon
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.ivarna.fluxlinux.R.drawable.ic_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "FluxLinux",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Setup State Tracking
        val setupCompleted = remember {
            mutableStateOf(StateManager.isTermuxInitialized(context))
        }
        val setupExpanded = remember {
            mutableStateOf(!setupCompleted.value)
        }
        
        // Refresh setup state
        LaunchedEffect(Unit) {
            setupCompleted.value = StateManager.isTermuxInitialized(context)
            if (setupCompleted.value) {
                setupExpanded.value = false
            }
        }
        
        // Setup Section (Collapsible)
        if (!setupCompleted.value || setupExpanded.value) {
            Button(
                onClick = {
                    if (permissionState.status.isGranted) {
                        val scriptManager = ScriptManager(context)
                        val setupScript = scriptManager.getScriptContent("setup_termux.sh")
                        val intent = TermuxIntentFactory.buildRunCommandIntent(setupScript)
                        try {
                            onStartService(intent)
                            // Mark as initialized
                            StateManager.setTermuxInitialized(context, true)
                            setupCompleted.value = true
                            setupExpanded.value = false
                            android.widget.Toast.makeText(context, "Initializing Environment...", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.util.Log.e("FluxLinux", "Setup failed", e)
                        }
                    } else {
                        permissionState.launchPermissionRequest()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = FluxAccentCyan
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Initialize Environment (Setup)", color = FluxBackgroundStart)
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Apply Tweaks Button (only show if initialized)
            if (setupCompleted.value) {
                Button(
                    onClick = {
                        if (permissionState.status.isGranted) {
                            val scriptManager = ScriptManager(context)
                            val tweaksScript = scriptManager.getScriptContent("termux_tweaks.sh")
                            
                            val copyCmd = "cat > \$HOME/termux_tweaks.sh << 'TWEAKS_EOF'\n$tweaksScript\nTWEAKS_EOF\nchmod +x \$HOME/termux_tweaks.sh && bash \$HOME/termux_tweaks.sh"
                            val intent = TermuxIntentFactory.buildRunCommandIntent(copyCmd)
                            
                            try {
                                onStartService(intent)
                                StateManager.setTweaksApplied(context, true)
                                android.widget.Toast.makeText(context, "Applying Termux Tweaks...", android.widget.Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                android.util.Log.e("FluxLinux", "Tweaks failed", e)
                            }
                        } else {
                            permissionState.launchPermissionRequest()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FluxAccentMagenta),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("🎨 Apply Termux Tweaks", color = Color.White)
                }
            }
        }
        
        // Show/Hide Setup Toggle
        if (setupCompleted.value) {
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = { setupExpanded.value = !setupExpanded.value }
            ) {
                Text(
                    if (setupExpanded.value) "▲ Hide Setup" else "▼ Show Setup",
                    color = GlassWhiteMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Prerequisites (Downloads)
        Text(
            text = "Prerequisites",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // Scope for installs
        val installer = remember { ApkInstaller(context) }
        

        
        // Trigger initial refresh on mount
        LaunchedEffect(Unit) {
            android.util.Log.d("FluxLinux", "Triggering initial refresh, refreshKey=${refreshKey.value}")
            refreshKey.value++
        }
        
        // Package Detection
        val termuxInstalled = remember(refreshKey.value) { 
            val installed = StateManager.isTermuxInstalled(context)
            android.util.Log.d("FluxLinux", "Creating termuxInstalled state: $installed (refreshKey=${refreshKey.value})")
            mutableStateOf(installed)
        }
        val x11Installed = remember(refreshKey.value) { 
            val installed = StateManager.isTermuxX11Installed(context)
            android.util.Log.d("FluxLinux", "Creating x11Installed state: $installed (refreshKey=${refreshKey.value})")
            mutableStateOf(installed)
        }
        
        // Installed Distros Detection
        val installedDistros = remember(refreshKey.value) {
            val installedIds = StateManager.getInstalledDistros(context)
            DistroRepository.supportedDistros.filter { installedIds.contains(it.id) }
        }
        
        // Download States
        val termuxProgress = remember { mutableStateOf(0f) }
        val x11Progress = remember { mutableStateOf(0f) }

        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                if (!termuxInstalled.value) {
                    Button(
                        onClick = {
                            android.widget.Toast.makeText(context, "Downloading Termux...", android.widget.Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                val url = "https://github.com/termux/termux-app/releases/download/v0.118.3/termux-app_v0.118.3+github-debug_universal.apk"
                                installer.downloadAndInstall(url, "termux.apk") { progress, status ->
                                    termuxProgress.value = progress
                                    android.util.Log.d("FluxLinux", status)
                                }
                                termuxProgress.value = 0f
                                // Trigger refresh
                                refreshKey.value++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GlassBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Install Termux", color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color.Black)
                    }
                    
                    if (termuxProgress.value > 0f) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { termuxProgress.value },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = FluxAccentCyan,
                            trackColor = GlassWhiteLow,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Installed",
                            tint = Color(0xFF50fa7b),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Termux ✓",
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                StateManager.getTermuxVersion(context),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                if (!x11Installed.value) {
                    Button(
                        onClick = {
                            android.widget.Toast.makeText(context, "Downloading Termux:X11...", android.widget.Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                val url = "https://github.com/termux/termux-x11/releases/download/nightly/app-arm64-v8a-debug.apk"
                                installer.downloadAndInstall(url, "termux-x11.apk") { progress, status ->
                                    x11Progress.value = progress
                                    android.util.Log.d("FluxLinux", status)
                                }
                                x11Progress.value = 0f
                                // Trigger refresh
                                refreshKey.value++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GlassBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Install X11", color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color.Black)
                    }
                    
                    if (x11Progress.value > 0f) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { x11Progress.value },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = FluxAccentCyan,
                            trackColor = GlassWhiteLow,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Installed",
                            tint = Color(0xFF50fa7b),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Termux:X11 ✓",
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                StateManager.getTermuxX11Version(context),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))


        Spacer(modifier = Modifier.height(24.dp))
        
        // Installed Distros Section
        if (installedDistros.isNotEmpty()) {
            Text(
                text = "Installed Distros",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            installedDistros.forEach { distro ->
                com.ivarna.fluxlinux.ui.components.DistroCard(
                    distro = distro,
                    hazeState = hazeState,
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
                    onAlreadyInstalled = {} // No-op
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        
        // Footer: Connection Issues
        ConnectionIssuesSection(context = context)
        
        Spacer(modifier = Modifier.height(32.dp))
    }
    
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

@Composable
fun ConnectionIssuesSection(context: Context) {
    val connectionFixed = remember { mutableStateOf(StateManager.isConnectionFixed(context)) }
    val expanded = remember { mutableStateOf(!connectionFixed.value) }
    val fixCommand = "mkdir -p ~/.termux && echo \"allow-external-apps = true\" >> ~/.termux/termux.properties && termux-reload-settings"
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Toggle Button
        TextButton(
            onClick = { expanded.value = !expanded.value }
        ) {
            Text(
                if (expanded.value) "▲ Hide Connection Issues" else "▼ Connection Issues",
                color = if (connectionFixed.value) GlassWhiteMedium else FluxAccentMagenta
            )
        }
        
        // Content (when expanded)
        if (expanded.value) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Fix Termux Connection",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Command Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF1E1E1E),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = fixCommand,
                    color = Color(0xFF50fa7b),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Copy & Open Button
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Termux Fix", fixCommand)
                        clipboard.setPrimaryClip(clip)
                        
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.termux")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                            android.widget.Toast.makeText(context, "Command copied! Paste in Termux", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FluxAccentMagenta),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copy & Open", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                
                // Mark as Fixed Button
                Button(
                    onClick = {
                        StateManager.setConnectionFixed(context, true)
                        connectionFixed.value = true
                        expanded.value = false
                        android.widget.Toast.makeText(context, "Marked as fixed", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlassBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mark as Fixed", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
