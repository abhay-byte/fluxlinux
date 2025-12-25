package com.ivarna.fluxlinux.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.utils.ApkInstaller
import com.ivarna.fluxlinux.core.utils.StateManager
import com.ivarna.fluxlinux.ui.theme.*
import com.ivarna.fluxlinux.ui.theme.TextWhite
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PrerequisitesScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Step tracking
    var currentStep by remember { mutableStateOf(1) }
    val totalSteps = 3
    
    // Package states
    val termuxInstalled = remember { mutableStateOf(StateManager.isTermuxInstalled(context)) }
    val x11Installed = remember { mutableStateOf(StateManager.isTermuxX11Installed(context)) }
    
    // Download progress
    val termuxProgress = remember { mutableStateOf(0f) }
    val x11Progress = remember { mutableStateOf(0f) }
    
    // Configuration state
    var configDone by remember { mutableStateOf(false) }
    
    // Permission state
    val permissionState = rememberPermissionState(
        permission = "com.termux.permission.RUN_COMMAND"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Title
            Text(
                text = "Prerequisites",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle
            Text(
                text = "FluxLinux requires these to function",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f),
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Step Indicator
            StepIndicator(currentStep = currentStep, totalSteps = totalSteps)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Content based on current step
            when (currentStep) {
                1 -> PackageInstallationStep(
                    termuxInstalled = termuxInstalled,
                    x11Installed = x11Installed,
                    termuxProgress = termuxProgress,
                    x11Progress = x11Progress,
                    onContinue = {
                        if (termuxInstalled.value && x11Installed.value) {
                            currentStep = 2
                        }
                    }
                )
                
                2 -> TermuxConfigurationStep(
                    configDone = configDone,
                    onConfigDone = { configDone = it },
                    onContinue = {
                        if (configDone) {
                            StateManager.setConnectionFixed(context, true)
                            currentStep = 3
                        }
                    }
                )
                
                3 -> PermissionRequestStep(
                    permissionState = permissionState,
                    onComplete = onComplete
                )
            }
        }
    }
}

@Composable
fun PackageInstallationStep(
    termuxInstalled: MutableState<Boolean>,
    x11Installed: MutableState<Boolean>,
    termuxProgress: MutableState<Float>,
    x11Progress: MutableState<Float>,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val installer = remember { ApkInstaller(context) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Step 1: Install Required Apps",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Termux
        PrerequisiteItem(
            name = "Termux",
            isInstalled = termuxInstalled.value,
            version = if (termuxInstalled.value) StateManager.getTermuxVersion(context) else null,
            progress = termuxProgress.value,
            onInstall = {
                android.widget.Toast.makeText(context, "Downloading Termux...", android.widget.Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    val url = "https://github.com/termux/termux-app/releases/download/v0.118.3/termux-app_v0.118.3+github-debug_universal.apk"
                    installer.downloadAndInstall(url, "termux.apk") { progress, status ->
                        termuxProgress.value = progress
                        android.util.Log.d("Prerequisites", status)
                    }
                    termuxProgress.value = 0f
                    termuxInstalled.value = StateManager.isTermuxInstalled(context)
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Termux:X11
        PrerequisiteItem(
            name = "Termux:X11",
            isInstalled = x11Installed.value,
            version = if (x11Installed.value) StateManager.getTermuxX11Version(context) else null,
            progress = x11Progress.value,
            onInstall = {
                android.widget.Toast.makeText(context, "Downloading Termux:X11...", android.widget.Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    val url = "https://github.com/termux/termux-x11/releases/download/nightly/app-arm64-v8a-debug.apk"
                    installer.downloadAndInstall(url, "termux-x11.apk") { progress, status ->
                        x11Progress.value = progress
                        android.util.Log.d("Prerequisites", status)
                    }
                    x11Progress.value = 0f
                    x11Installed.value = StateManager.isTermuxX11Installed(context)
                }
            }
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Continue Button
        Button(
            onClick = onContinue,
            enabled = termuxInstalled.value && x11Installed.value,
            colors = ButtonDefaults.buttonColors(containerColor = FluxAccentCyan),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Continue",
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TermuxConfigurationStep(
    configDone: Boolean,
    onConfigDone: (Boolean) -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val fixCommand = "mkdir -p ~/.termux && echo \"allow-external-apps = true\" >> ~/.termux/termux.properties && termux-reload-settings"
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Step 2: Configure Termux",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Allow FluxLinux to communicate with Termux",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f),
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Command Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E1E))
                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = fixCommand,
                color = Color(0xFF50fa7b),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Copy & Open Button
        Button(
            onClick = {
                // Copy to clipboard
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Termux Fix", fixCommand)
                clipboard.setPrimaryClip(clip)
                
                // Open Termux
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.termux")
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                    android.widget.Toast.makeText(context, "Command copied! Paste in Termux", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(context, "Termux not found!", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = FluxAccentMagenta),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Copy & Open Termux",
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = configDone,
                onCheckedChange = onConfigDone,
                colors = CheckboxDefaults.colors(
                    checkedColor = FluxAccentCyan,
                    uncheckedColor = GlassBorder
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "I've pasted and run the command in Termux",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Continue Button
        Button(
            onClick = onContinue,
            enabled = configDone,
            colors = ButtonDefaults.buttonColors(containerColor = FluxAccentCyan),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Continue",
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestStep(
    permissionState: com.google.accompanist.permissions.PermissionState,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Step 3: Grant Permission",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "🔐",
            fontSize = 64.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "FluxLinux needs permission to communicate with Termux",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f),
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Permission status
        if (permissionState.status.isGranted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF50fa7b).copy(alpha = 0.2f))
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = Color(0xFF50fa7b),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Permission Granted ✓",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            // Request Permission Button
            Button(
                onClick = {
                    permissionState.launchPermissionRequest()
                },
                colors = ButtonDefaults.buttonColors(containerColor = FluxAccentMagenta),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Grant Permission",
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Complete Button
        Button(
            onClick = onComplete,
            enabled = permissionState.status.isGranted,
            colors = ButtonDefaults.buttonColors(containerColor = FluxAccentCyan),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Complete Setup",
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PrerequisiteItem(
    name: String,
    isInstalled: Boolean,
    version: String?,
    progress: Float,
    onInstall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassWhiteLow)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isInstalled) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Installed",
                    tint = Color(0xFF50fa7b),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "$name ✓",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (version != null) {
                        Text(
                            text = version,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Text(
                    text = name,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = GlassBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Install", color = TextWhite)
                }
            }
        }
        
        if (progress > 0f) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = FluxAccentCyan,
                trackColor = GlassWhiteLow,
            )
        }
    }
}

@Composable
fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val step = index + 1
            Box(
                modifier = Modifier
                    .size(if (step == currentStep) 12.dp else 8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (step <= currentStep) FluxAccentCyan else GlassWhiteLow
                    )
            )
            if (step < totalSteps) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}
