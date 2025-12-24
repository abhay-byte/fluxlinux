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
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
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
                            Text(text = "FluxLinux Initialized", color = Color.White)
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Permission State
                            val permissionState = com.google.accompanist.permissions.rememberPermissionState(
                                permission = "com.termux.permission.RUN_COMMAND"
                            )

                            Button(
                                onClick = {
                                    if (permissionState.status.isGranted) {
                                        // Permission Granted: Run Script
                                        android.util.Log.d("FluxLinux", "Permission granted, running script...")
                                        val scriptManager = com.fluxlinux.app.core.data.ScriptManager(this@MainActivity)
                                        val script = scriptManager.getScriptContent("setup_termux.sh")
                                        val intent = com.fluxlinux.app.core.data.TermuxIntentFactory.buildRunCommandIntent(script)
                                        try {
                                            startService(intent)
                                            android.widget.Toast.makeText(this@MainActivity, "Sent command to Termux...", android.widget.Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            android.util.Log.e("FluxLinux", "Failed to send intent", e)
                                            android.widget.Toast.makeText(this@MainActivity, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        // Permission Denied: Request it
                                        android.util.Log.d("FluxLinux", "Requesting permission...")
                                        permissionState.launchPermissionRequest()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = com.fluxlinux.app.ui.theme.FluxAccentCyan
                                )
                            ) {
                                Text(if (permissionState.status.isGranted) "Connect to Termux" else "Grant Permission")
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Troubleshooting Button
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    val command = "mkdir -p ~/.termux && echo \"allow-external-apps = true\" >> ~/.termux/termux.properties && termux-reload-settings"
                                    val clipboard = androidx.core.content.ContextCompat.getSystemService(this@MainActivity, android.content.ClipboardManager::class.java)
                                    val clip = android.content.ClipData.newPlainText("Termux Fix", command)
                                    clipboard?.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(this@MainActivity, "Command copied! Paste it in Termux.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            ) {
                                Text("Fix Connection Issues (Copy Command)", color = com.fluxlinux.app.ui.theme.FluxAccentMagenta)
                            }
                        }
                    }
                }
            }
        }
    }
}
