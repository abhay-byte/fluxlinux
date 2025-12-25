package com.fluxlinux.app


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fluxlinux.app.ui.components.GlassScaffold
import com.fluxlinux.app.ui.theme.FluxLinuxTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FluxLinuxTheme {
                GlassScaffold {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            
                            // Permission State (Declared at top)
                            val permissionState = com.google.accompanist.permissions.rememberPermissionState(
                                permission = "com.termux.permission.RUN_COMMAND"
                            )

                            // Setup State Tracking
                            val setupCompleted = androidx.compose.runtime.remember {
                                androidx.compose.runtime.mutableStateOf(com.fluxlinux.app.core.utils.StateManager.isTermuxInitialized(this@MainActivity))
                            }
                            val setupExpanded = androidx.compose.runtime.remember {
                                androidx.compose.runtime.mutableStateOf(!setupCompleted.value)
                            }
                            
                            // Refresh setup state
                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                setupCompleted.value = com.fluxlinux.app.core.utils.StateManager.isTermuxInitialized(this@MainActivity)
                                if (setupCompleted.value) {
                                    setupExpanded.value = false
                                }
                            }
                            
                            // Setup Section (Collapsible)
                            if (!setupCompleted.value || setupExpanded.value) {
                                Button(
                                    onClick = {
                                        if (permissionState.status.isGranted) {
                                            val scriptManager = com.fluxlinux.app.core.data.ScriptManager(this@MainActivity)
                                            val setupScript = scriptManager.getScriptContent("setup_termux.sh")
                                            val intent = com.fluxlinux.app.core.data.TermuxIntentFactory.buildRunCommandIntent(setupScript)
                                            try {
                                                startService(intent)
                                                // Mark as initialized
                                                com.fluxlinux.app.core.utils.StateManager.setTermuxInitialized(this@MainActivity, true)
                                                setupCompleted.value = true
                                                setupExpanded.value = false
                                                android.widget.Toast.makeText(this@MainActivity, "Initializing Environment...", android.widget.Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                android.util.Log.e("FluxLinux", "Setup failed", e)
                                            }
                                        } else {
                                            permissionState.launchPermissionRequest()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = com.fluxlinux.app.ui.theme.FluxAccentCyan
                                    ),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                ) {
                                    Text("Initialize Environment (Setup)", color = com.fluxlinux.app.ui.theme.FluxBackgroundStart)
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                // Apply Tweaks Button (only show if initialized)
                                if (setupCompleted.value) {
                                    Button(
                                        onClick = {
                                            if (permissionState.status.isGranted) {
                                                val scriptManager = com.fluxlinux.app.core.data.ScriptManager(this@MainActivity)
                                                val tweaksScript = scriptManager.getScriptContent("termux_tweaks.sh")
                                                
                                                val copyCmd = "cat > \$HOME/termux_tweaks.sh << 'TWEAKS_EOF'\n$tweaksScript\nTWEAKS_EOF\nchmod +x \$HOME/termux_tweaks.sh && bash \$HOME/termux_tweaks.sh"
                                                val intent = com.fluxlinux.app.core.data.TermuxIntentFactory.buildRunCommandIntent(copyCmd)
                                                
                                                try {
                                                    startService(intent)
                                                    com.fluxlinux.app.core.utils.StateManager.setTweaksApplied(this@MainActivity, true)
                                                    android.widget.Toast.makeText(this@MainActivity, "Applying Termux Tweaks...", android.widget.Toast.LENGTH_LONG).show()
                                                } catch (e: Exception) {
                                                    android.util.Log.e("FluxLinux", "Tweaks failed", e)
                                                }
                                            } else {
                                                permissionState.launchPermissionRequest()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = com.fluxlinux.app.ui.theme.FluxAccentMagenta),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                    ) {
                                        Text("🎨 Apply Termux Tweaks", color = Color.White)
                                    }
                                }
                            }
                            
                            // Show/Hide Setup Toggle
                            if (setupCompleted.value) {
                                Spacer(modifier = Modifier.height(10.dp))
                                androidx.compose.material3.TextButton(
                                    onClick = { setupExpanded.value = !setupExpanded.value }
                                ) {
                                    Text(
                                        if (setupExpanded.value) "▲ Hide Setup" else "▼ Show Setup",
                                        color = com.fluxlinux.app.ui.theme.GlassWhiteMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Prerequisites (Downloads)
                            Text(
                                text = "Prerequisites",
                                color = Color.White,
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            // Scope for installs
                            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val installer = androidx.compose.runtime.remember { com.fluxlinux.app.core.utils.ApkInstaller(context) }
                            
                            // Package Detection
                            val termuxInstalled = androidx.compose.runtime.remember { 
                                androidx.compose.runtime.mutableStateOf(com.fluxlinux.app.core.utils.StateManager.isTermuxInstalled(context))
                            }
                            val x11Installed = androidx.compose.runtime.remember { 
                                androidx.compose.runtime.mutableStateOf(com.fluxlinux.app.core.utils.StateManager.isTermuxX11Installed(context))
                            }
                            
                            // Refresh package detection on composition
                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                termuxInstalled.value = com.fluxlinux.app.core.utils.StateManager.isTermuxInstalled(context)
                                x11Installed.value = com.fluxlinux.app.core.utils.StateManager.isTermuxX11Installed(context)
                            }
                            
                            // Download States
                            val termuxProgress = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
                            val x11Progress = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }

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
                                                    // Recheck installation status
                                                    termuxInstalled.value = com.fluxlinux.app.core.utils.StateManager.isTermuxInstalled(context)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = com.fluxlinux.app.ui.theme.GlassBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Install Termux", color = Color.White)
                                        }
                                        
                                        if (termuxProgress.value > 0f) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            androidx.compose.material3.LinearProgressIndicator(
                                                progress = { termuxProgress.value },
                                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                                color = com.fluxlinux.app.ui.theme.FluxAccentCyan,
                                                trackColor = com.fluxlinux.app.ui.theme.GlassWhiteLow,
                                            )
                                        }
                                    } else {
                                        // Show status indicator
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            androidx.compose.material3.Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                                contentDescription = "Installed",
                                                tint = Color(0xFF50fa7b),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("Termux ✓", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    com.fluxlinux.app.core.utils.StateManager.getTermuxVersion(context),
                                                    color = com.fluxlinux.app.ui.theme.GlassWhiteMedium,
                                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.size(8.dp))
                                
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
                                                    // Recheck installation status
                                                    x11Installed.value = com.fluxlinux.app.core.utils.StateManager.isTermuxX11Installed(context)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = com.fluxlinux.app.ui.theme.GlassBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Install X11", color = Color.White)
                                        }
                                        
                                        if (x11Progress.value > 0f) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            androidx.compose.material3.LinearProgressIndicator(
                                                progress = { x11Progress.value },
                                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                                color = com.fluxlinux.app.ui.theme.FluxAccentMagenta,
                                                trackColor = com.fluxlinux.app.ui.theme.GlassWhiteLow,
                                            )
                                        }
                                    } else {
                                        // Show status indicator
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            androidx.compose.material3.Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                                contentDescription = "Installed",
                                                tint = Color(0xFF50fa7b),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("Termux:X11 ✓", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    com.fluxlinux.app.core.utils.StateManager.getTermuxX11Version(context),
                                                    color = com.fluxlinux.app.ui.theme.GlassWhiteMedium,
                                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))

                            // Title
                            Text(
                                text = "Available Distros",
                                color = Color.White,
                                style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Distro List
                            com.fluxlinux.app.core.data.DistroRepository.supportedDistros.forEach { distro ->
                                com.fluxlinux.app.ui.components.DistroCard(
                                    distro = distro,
                                    onInstall = {
                                        if (permissionState.status.isGranted) {
                                            android.util.Log.d("FluxLinux", "Preparing Install Command for ${distro.name}")
                                            
                                            // Load setup script
                                            val scriptManager = com.fluxlinux.app.core.data.ScriptManager(this@MainActivity)
                                            val setupScript = scriptManager.getScriptContent("debian_setup.sh")
                                            
                                            // Generate Command
                                            val command = com.fluxlinux.app.core.data.TermuxIntentFactory.getInstallCommand(distro.id, setupScript)
                                            
                                            // 1. Copy to Clipboard
                                            val clipboard = android.content.Context.CLIPBOARD_SERVICE
                                            val clipboardManager = getSystemService(clipboard) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("FluxLinux Install", command)
                                            clipboardManager.setPrimaryClip(clip)
                                            
                                            // 2. Notify User
                                            android.widget.Toast.makeText(this@MainActivity, "Command Copied! Paste in Termux to Install.", android.widget.Toast.LENGTH_LONG).show()
                                            
                                            // 3. Open Termux
                                            val launchIntent = com.fluxlinux.app.core.data.TermuxIntentFactory.buildOpenTermuxIntent(this@MainActivity)
                                            if (launchIntent != null) {
                                                startActivity(launchIntent)
                                            } else {
                                                android.widget.Toast.makeText(this@MainActivity, "Termux not found!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            
                                        } else {
                                            permissionState.launchPermissionRequest()
                                        }
                                    },
                                    onUninstall = {
                                        if (permissionState.status.isGranted) {
                                            android.util.Log.d("FluxLinux", "Uninstalling ${distro.name}")
                                            val intent = com.fluxlinux.app.core.data.TermuxIntentFactory.buildUninstallIntent(distro.id)
                                            try {
                                                startService(intent)
                                                android.widget.Toast.makeText(this@MainActivity, "Uninstalling ${distro.name}...", android.widget.Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                android.util.Log.e("FluxLinux", "Uninstall failed", e)
                                            }
                                        } else {
                                            permissionState.launchPermissionRequest()
                                        }
                                    },
                                    onLaunchCli = {
                                        if (permissionState.status.isGranted) {
                                            android.util.Log.d("FluxLinux", "Launching CLI ${distro.name}")
                                            val intent = com.fluxlinux.app.core.data.TermuxIntentFactory.buildLaunchCliIntent(distro.id)
                                            try {
                                                startService(intent)
                                            } catch (e: Exception) {
                                                android.util.Log.e("FluxLinux", "Launch CLI failed", e)
                                            }
                                        } else {
                                            permissionState.launchPermissionRequest()
                                        }
                                    },
                                    onLaunchGui = {
                                        if (permissionState.status.isGranted) {
                                            android.util.Log.d("FluxLinux", "Launching GUI ${distro.name}")
                                            val intent = com.fluxlinux.app.core.data.TermuxIntentFactory.buildLaunchGuiIntent(distro.id)
                                            try {
                                                startService(intent)
                                                android.widget.Toast.makeText(this@MainActivity, "Launching XFCE...", android.widget.Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                android.util.Log.e("FluxLinux", "Launch GUI failed", e)
                                            }
                                        } else {
                                            permissionState.launchPermissionRequest()
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Troubleshooting (Footer)
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    val command = "mkdir -p ~/.termux && echo \"allow-external-apps = true\" >> ~/.termux/termux.properties && termux-reload-settings"
                                    val clipboard = androidx.core.content.ContextCompat.getSystemService(this@MainActivity, android.content.ClipboardManager::class.java)
                                    val clip = android.content.ClipData.newPlainText("Termux Fix", command)
                                    clipboard?.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(this@MainActivity, "Command copied! Paste it in Termux.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            ) {
                                Text("Fix Connection Issues", color = com.fluxlinux.app.ui.theme.FluxAccentMagenta)
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}
