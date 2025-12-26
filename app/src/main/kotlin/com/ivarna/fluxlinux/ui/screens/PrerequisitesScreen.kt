package com.ivarna.fluxlinux.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.ivarna.fluxlinux.core.utils.RootUtils
import com.ivarna.fluxlinux.core.data.TermuxIntentFactory
import com.ivarna.fluxlinux.core.data.ScriptManager
import com.ivarna.fluxlinux.ui.theme.*
import com.ivarna.fluxlinux.ui.theme.TextWhite
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material.icons.filled.Refresh
import android.widget.Toast
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PrerequisitesScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Step tracking
    var currentStep by remember { mutableStateOf(1) }
    val totalSteps = 7
    
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

    // Function to re-check status
    val checkStatus = {
        termuxInstalled.value = StateManager.isTermuxInstalled(context)
        x11Installed.value = StateManager.isTermuxX11Installed(context)
    }

    // Refresh when app resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
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
                    onRefresh = checkStatus,
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
                    onContinue = { currentStep = 4 }
                )
                
                4 -> OverlayPermissionStep(
                    onContinue = { currentStep = 5 }
                )

                5 -> PhantomProcessStep(
                    onContinue = { currentStep = 6 }
                )
                
                6 -> EnvironmentSetupStep(
                    onContinue = { currentStep = 7 }
                )
                
                7 -> FinalInstructionsStep(
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
    onRefresh: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val installer = remember { ApkInstaller(context) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Step 1: Install Required Apps",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = FluxAccentCyan
                )
            }
        }
        
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
    onContinue: () -> Unit
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
        
        // Continue Button
        Button(
            onClick = onContinue,
            enabled = permissionState.status.isGranted,
            colors = ButtonDefaults.buttonColors(containerColor = FluxAccentCyan),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Next",
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

@Composable
fun OverlayPermissionStep(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    
    // Check overlay permission
    var hasOverlayPermission by remember { 
        mutableStateOf(android.provider.Settings.canDrawOverlays(context)) 
    }
    
    // Manual override
    var manualOverride by remember { mutableStateOf(false) }
    
    // Refresh permission status on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Step 4: Display Overlay",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))

         // Permission Info Card
         androidx.compose.material3.Card(
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
         ) {
             Column(modifier = Modifier.padding(20.dp)) {
                 Text(
                    "⚠️ Critical Permission",
                    color = Color(0xFFFFB74D), // Warning Orange
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                 )
                 Spacer(modifier = Modifier.height(8.dp))
                 Text(
                    "To display the Linux desktop (X11) on your screen, Termux needs the 'Display over other apps' permission.",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                 )
             }
         }
         
         Spacer(modifier = Modifier.height(24.dp))
         
         if (hasOverlayPermission) {
             // Success State
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
             // Action Buttons
             Row(
                 modifier = Modifier.fillMaxWidth(),
                 horizontalArrangement = Arrangement.spacedBy(12.dp)
             ) {
                 // Overlay Settings Button
                 Button(
                     onClick = { 
                         try {
                             val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                             intent.data = android.net.Uri.parse("package:com.termux")
                             context.startActivity(intent)
                         } catch (e: Exception) {
                              try {
                                 val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                 context.startActivity(intent)
                             } catch (e2: Exception) {
                                 Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
                             }
                         }
                     },
                     colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary),
                     modifier = Modifier.weight(1f),
                     shape = RoundedCornerShape(12.dp)
                 ) {
                     Text("Enable Overlay", fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary, textAlign = TextAlign.Center, lineHeight = 16.sp)
                 }
                 
                 // App Info Button
                 Button(
                     onClick = { 
                         try {
                             val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                             intent.data = android.net.Uri.parse("package:com.termux")
                             context.startActivity(intent)
                         } catch (e: Exception) {
                             Toast.makeText(context, "Could not open App Info", Toast.LENGTH_SHORT).show()
                         }
                     },
                     colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary),
                     modifier = Modifier.weight(1f),
                     shape = RoundedCornerShape(12.dp)
                 ) {
                     Text("App Info", fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondary, textAlign = TextAlign.Center, lineHeight = 16.sp)
                 }
             }
             
             Spacer(modifier = Modifier.height(16.dp))

             // Help Link
             androidx.compose.material3.TextButton(
                 onClick = { 
                     val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://support.google.com/android/answer/12623953?hl=en"))
                     context.startActivity(browserIntent)
                 }
             ) {
                 Text(
                     "How to allow restricted settings on Android devices",
                     color = FluxAccentCyan,
                     fontSize = 13.sp,
                     textAlign = TextAlign.Center,
                     textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                 )
             }
             
             Spacer(modifier = Modifier.height(16.dp))
             
             // Manual Override Checkbox
             Row(
                 verticalAlignment = Alignment.CenterVertically,
                 modifier = Modifier
                     .fillMaxWidth()
                     .clickable { manualOverride = !manualOverride }
                     .padding(8.dp)
             ) {
                 Checkbox(
                     checked = manualOverride,
                     onCheckedChange = { manualOverride = it },
                     colors = CheckboxDefaults.colors(
                         checkedColor = FluxAccentCyan,
                         uncheckedColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)
                     )
                 )
                 Text(
                     "I have enabled this manually",
                     fontSize = 14.sp,
                     color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                     modifier = Modifier.padding(start = 8.dp)
                 )
             }
         }
         
         Spacer(modifier = Modifier.weight(1f))
         
         // Continue Button
         Button(
            onClick = onContinue,
            enabled = hasOverlayPermission || manualOverride,
            colors = ButtonDefaults.buttonColors(
                containerColor = FluxAccentCyan,
                disabledContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Next",
                color = if (hasOverlayPermission || manualOverride) Color.Black else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PhantomProcessStep(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
    var fixApplied by remember { mutableStateOf(false) }
    var checkingRoot by remember { mutableStateOf(false) }
    
    // Check root on init
    LaunchedEffect(Unit) {
        checkingRoot = true
        kotlinx.coroutines.delay(500) // fake delay for UX
        rootAvailable = RootUtils.isRootAvailable()
        checkingRoot = false
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Step 5: Process Killer Fix",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Info Card
        androidx.compose.material3.Card(
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "⚠️ Android 12+ Stability Issue",
                    color = Color(0xFFFFB74D), // Orange
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Android 12 and higher kill background processes aggressively (Phantom Process Killer). This causes Termux to crash unexpectedly.",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (checkingRoot) {
            CircularProgressIndicator(color = FluxAccentCyan)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Checking for Root access...", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
        } else if (fixApplied) {
            // Success State
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
                    contentDescription = "Applied",
                    tint = Color(0xFF50fa7b),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Fix Applied Successfully ✓",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (rootAvailable == true) {
            // Root Available Action
            Text(
                "Root Access Detected ✅",
                color = Color(0xFF50fa7b),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    coroutineScope.launch {
                        val r1 = RootUtils.runRootCommand("/system/bin/device_config set_sync_disabled_for_tests persistent")
                        val r2 = RootUtils.runRootCommand("/system/bin/device_config put activity_manager max_phantom_processes 2147483647")
                        val r3 = RootUtils.runRootCommand("settings put global settings_enable_monitor_phantom_procs false")
                        
                        if (r1.isSuccess && r2.isSuccess && r3.isSuccess) {
                           fixApplied = true 
                        } else {
                           Toast.makeText(context, "Failed to apply fix: ${r1.error}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = FluxAccentMagenta),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                 shape = RoundedCornerShape(12.dp)
            ) {
                Text("Apply Fix (Grant Root)", color = TextWhite)
            }
        } else {
            // No Root
             Text(
                "Root Access Not Detected ❌",
                color = Color(0xFFFF6B6B),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "We cannot apply the fix automatically. Please run these commands from your PC via ADB:",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha=0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // ADB Commands Box
            val commands = """
                adb shell "/system/bin/device_config set_sync_disabled_for_tests persistent"
                adb shell "/system/bin/device_config put activity_manager max_phantom_processes 2147483647"
                adb shell "settings put global settings_enable_monitor_phantom_procs false"
            """.trimIndent()
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("ADB Commands", commands)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Commands copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Text(
                    text = commands,
                    color = Color(0xFF50fa7b),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
             // Re-check Button
             androidx.compose.material3.OutlinedButton(
                 onClick = { 
                     checkingRoot = true
                     coroutineScope.launch {
                         kotlinx.coroutines.delay(500)
                         rootAvailable = RootUtils.isRootAvailable()
                         checkingRoot = false
                     }
                 },
                 modifier = Modifier.fillMaxWidth()
             ) {
                 Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                 Spacer(modifier = Modifier.width(8.dp))
                 Text("Check Root Again")
             }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Continue / Skip
        Button(
            onClick = onContinue,
            // Always enabled, user can skip if they want/have to
            enabled = true,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (fixApplied) FluxAccentCyan else androidx.compose.material3.MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (fixApplied) "Next" else "Skip (Use ADB instead)",
                color = if (fixApplied) Color.Black else TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EnvironmentSetupStep(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val scriptManager = remember { ScriptManager(context) }
    
    // State to track if scripts have been run
    // Initialize from StateManager to handle screen rotation/re-entry
    var setupInitiated by remember { mutableStateOf(StateManager.getScriptStatus(context, "setup_termux")) }
    var isSetupLoading by remember { mutableStateOf(false) }
    var tweaksInitiated by remember { mutableStateOf(false) }
    var tweaksCompleted by remember { mutableStateOf(StateManager.getScriptStatus(context, "termux_tweaks")) }
    
    // Poll for setup completion
    LaunchedEffect(isSetupLoading) {
        if (isSetupLoading) {
            while (isSetupLoading) {
                delay(2000) // Check every 2 seconds
                if (StateManager.getScriptStatus(context, "setup_termux")) {
                    isSetupLoading = false
                    setupInitiated = true
                    Toast.makeText(context, "Environment Initialized Successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Poll for tweaks completion
    LaunchedEffect(tweaksInitiated) {
        if (tweaksInitiated && !tweaksCompleted) {
            while (!tweaksCompleted) {
                delay(2000) // Check every 2 seconds
                if (StateManager.getScriptStatus(context, "termux_tweaks")) {
                     tweaksCompleted = true
                     break
                }
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Step 5: Environment Setup",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Setup Button (Background)
        Button(
            onClick = {
                val script = scriptManager.getScriptContent("common/setup_termux.sh")
                // Reset status first
                StateManager.setScriptStatus(context, "setup_termux", false)
                
                val intent = TermuxIntentFactory.buildRunCommandIntent(script, runInBackground = false)
                try {
                    context.startService(intent)
                    isSetupLoading = true
                    Toast.makeText(context, "Opening Termux...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to start service", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (setupInitiated) Color(0xFF50fa7b).copy(alpha=0.2f) else FluxAccentCyan,
                contentColor = if (setupInitiated) Color(0xFF50fa7b) else Color.Black
            ),
            enabled = !isSetupLoading && !setupInitiated,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isSetupLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Initializing... (Takes a few mins)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else if (setupInitiated) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Environment Initialized",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        "Initialize Environment (Setup)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Tweaks Button (Foreground)
        Button(
            onClick = {
                val script = scriptManager.getScriptContent("common/termux_tweaks.sh")
                val intent = TermuxIntentFactory.buildRunCommandIntent(script, runInBackground = false)
                try {
                    context.startService(intent)
                    tweaksInitiated = true
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to start service", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = setupInitiated && !tweaksCompleted,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (tweaksCompleted) Color(0xFF50fa7b).copy(alpha=0.2f) else FluxAccentMagenta,
                disabledContainerColor = if (tweaksCompleted) Color(0xFF50fa7b).copy(alpha=0.2f) else FluxAccentMagenta.copy(alpha=0.5f),
                disabledContentColor = if (tweaksCompleted) Color(0xFF50fa7b) else TextWhite.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                 if (tweaksCompleted) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Tweaks Applied",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Apply Termux Tweaks",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (tweaksInitiated) {
                             Text(
                                "(Opened in Termux)",
                                color = TextWhite.copy(alpha=0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Continue Button
        Button(
            onClick = onContinue,
            // Logic: Only enable if both are initiated? Or just allow user to skip? 
            // User request usually implies they want to run it. Let's allow skip but maybe warn? 
            // For now, let's enable it always to not block if they already did it.
            enabled = true, 
            colors = ButtonDefaults.buttonColors(containerColor = GlassBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Next",
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FinalInstructionsStep(
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Step 6: Almost Done!",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Warning Card
        androidx.compose.material3.Card(
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = Color(0xFFff5555).copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFff5555), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Important Note",
                        color = Color(0xFFff5555),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "FluxLinux runs on top of Termux.",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "You must keep Termux running in the background. Do not swipe close Termux from your recent apps!",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Complete Button
        Button(
            onClick = onComplete,
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
