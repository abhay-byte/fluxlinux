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

                            // Initial Setup (Restored)
                            Button(
                                onClick = {
                                    if (permissionState.status.isGranted) {
                                        android.util.Log.d("FluxLinux", "Running Initial Setup...")
                                        val scriptManager = com.fluxlinux.app.core.data.ScriptManager(this@MainActivity)
                                        val script = scriptManager.getScriptContent("setup_termux.sh")
                                        val intent = com.fluxlinux.app.core.data.TermuxIntentFactory.buildRunCommandIntent(script)
                                        try {
                                            startService(intent)
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
                            
                            // Download States
                            val termuxProgress = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
                            val x11Progress = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }

                            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Button(
                                        onClick = {
                                            android.widget.Toast.makeText(context, "Downloading Termux...", android.widget.Toast.LENGTH_SHORT).show()
                                            coroutineScope.launch {
                                                val url = "https://github.com/termux/termux-app/releases/download/v0.118.3/termux-app_v0.118.3+github-debug_universal.apk"
                                                installer.downloadAndInstall(url, "termux.apk") { progress, status ->
                                                    termuxProgress.value = progress
                                                    android.util.Log.d("FluxLinux", status)
                                                }
                                                termuxProgress.value = 0f // Reset after done
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
                                }
                                
                                Spacer(modifier = Modifier.size(8.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Button(
                                        onClick = {
                                            android.widget.Toast.makeText(context, "Downloading Termux:X11...", android.widget.Toast.LENGTH_SHORT).show()
                                            coroutineScope.launch {
                                                val url = "https://github.com/termux/termux-x11/releases/download/nightly/app-arm64-v8a-debug.apk"
                                                installer.downloadAndInstall(url, "termux-x11.apk") { progress, status ->
                                                    x11Progress.value = progress
                                                    android.util.Log.d("FluxLinux", status)
                                                }
                                                x11Progress.value = 0f // Reset after done
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
                                            android.util.Log.d("FluxLinux", "Installing ${distro.name}")
                                            val intent = com.fluxlinux.app.core.data.TermuxIntentFactory.buildInstallIntent(distro.id)
                                            try {
                                                startService(intent)
                                                android.widget.Toast.makeText(this@MainActivity, "Installing ${distro.name}...", android.widget.Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                android.util.Log.e("FluxLinux", "Install failed", e)
                                                android.widget.Toast.makeText(this@MainActivity, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            permissionState.launchPermissionRequest()
                                        }
                                    },
                                    onLaunch = {
                                        if (permissionState.status.isGranted) {
                                            android.util.Log.d("FluxLinux", "Launching ${distro.name}")
                                            val intent = com.fluxlinux.app.core.data.TermuxIntentFactory.buildLaunchIntent(distro.id)
                                            try {
                                                startService(intent)
                                                android.widget.Toast.makeText(this@MainActivity, "Launching ${distro.name}...", android.widget.Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                android.util.Log.e("FluxLinux", "Launch failed", e)
                                                android.widget.Toast.makeText(this@MainActivity, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
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

                            Text(
                                text = "Prerequisites",
                                color = Color.White,
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))

                            Row {
                                Button(
                                    onClick = {
                                        val url = "https://github.com/termux/termux-app/releases"
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = com.fluxlinux.app.ui.theme.GlassBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Get Termux", color = Color.White)
                                }
                                
                                Spacer(modifier = Modifier.size(8.dp))
                                
                                Button(
                                    onClick = {
                                        val url = "https://github.com/termux/termux-x11/releases"
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = com.fluxlinux.app.ui.theme.GlassBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Get Termux:X11", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
