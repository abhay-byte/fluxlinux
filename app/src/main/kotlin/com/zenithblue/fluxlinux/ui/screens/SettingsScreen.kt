package com.zenithblue.fluxlinux.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenithblue.fluxlinux.R
import com.zenithblue.fluxlinux.ui.components.GlassSettingCard
import com.zenithblue.fluxlinux.core.utils.ThemePreferences
import com.zenithblue.fluxlinux.core.utils.ThemeMode
import com.zenithblue.fluxlinux.core.utils.TermuxX11Preferences
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import com.zenithblue.fluxlinux.ui.theme.FluxLinuxTheme
import com.zenithblue.fluxlinux.ui.theme.GlassBorder
import com.zenithblue.fluxlinux.core.data.ScriptManager
import com.zenithblue.fluxlinux.core.data.TermuxIntentFactory

import com.zenithblue.fluxlinux.core.utils.StateManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zenithblue.fluxlinux.ui.theme.FluxAccentCyan
import com.zenithblue.fluxlinux.ui.theme.FluxAccentMagenta
import com.zenithblue.fluxlinux.ui.theme.FluxBackgroundStart
import com.zenithblue.fluxlinux.ui.theme.GlassWhiteLow
import com.zenithblue.fluxlinux.ui.theme.GlassWhiteMedium
import android.content.ClipboardManager
import android.content.ClipData


@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    permissionState: PermissionState,
    onStartService: (Intent) -> Unit,
    onStartActivity: (Intent) -> Unit,
    onNavigateToOnboarding: (() -> Unit)? = null,
    onNavigateToTroubleshooting: (() -> Unit)? = null,
    onNavigateToRootCheck: (() -> Unit)? = null,
    onThemeChanged: ((ThemeMode) -> Unit)? = null,
    currentTheme: ThemeMode = ThemeMode.SYSTEM
) {
    val context = LocalContext.current
    // No need for local ThemePreferences if we pass it down, but keeping for consistency if other things need it
    
    // Theme Dropdown State
    var themeExpanded by remember { mutableStateOf(false) }
    
    // Haze State
    val hazeState = remember { HazeState() }

    // State for Settings
    var autoUpdate by remember { mutableStateOf(true) }


    // Entrance Animation


    // Background and Scaffold
    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .haze(state = hazeState)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack, // Standard icon
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier.background(Color.Transparent)
                )
            }
        ) { innerPadding ->
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    
                    // =====================================================================
                    // 0. ENVIRONMENT SETUP & PRE-REQUISITES (MOVED FROM HOME)
                    // =====================================================================
                    val context = LocalContext.current
                    val coroutineScope = rememberCoroutineScope()
                    val refreshKey = remember { mutableStateOf(0) } // For refreshing status
                    
                    // Refresh when app resumes to catch installation changes
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                refreshKey.value++
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    
                    GlassSettingCard {
                        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                            Text(
                                "Environment Setup",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Setup State Tracking
                            val setupCompleted = remember(refreshKey.value) { mutableStateOf(StateManager.isTermuxInitialized(context) || StateManager.getScriptStatus(context, "setup_termux")) }
                            val tweaksApplied = remember(refreshKey.value) { mutableStateOf(StateManager.getScriptStatus(context, "termux_tweaks")) }
                            

                            
                            // Setup Environment Button
                            Button(
                                onClick = {
                                    if (permissionState.status.isGranted) {
                                        val scriptManager = ScriptManager(context)
                                        val setupScript = scriptManager.getScriptContent("termux/setup_termux.sh")
                                        val fluxInstallScript = scriptManager.getScriptContent("debian/proot/setup/flux_install.sh")
                                        val startGuiScript = scriptManager.getScriptContent("debian/proot/start/start_gui.sh")
                                        
                                        val compositeCommand = buildString {
                                            append("cat << 'EOF_FLUX' > \$HOME/flux_install.sh\n")
                                            append(fluxInstallScript)
                                            append("\nEOF_FLUX\n")
                                            append("chmod +x \$HOME/flux_install.sh\n\n")
                                            
                                            append("cat << 'EOF_GUI' > \$HOME/start_gui.sh\n")
                                            append(startGuiScript)
                                            append("\nEOF_GUI\n")
                                            append("chmod +x \$HOME/start_gui.sh\n\n")
                                            
                                            append("rm -f \$HOME/.fluxlinux/setup_termux.done\n")
                                            append(setupScript)
                                        }
                                        
                                        val intent = TermuxIntentFactory.buildRunCommandIntent(compositeCommand)
                                        try {
                                            onStartService(intent)
                                            android.widget.Toast.makeText(context, "Initializing Environment...", android.widget.Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            android.util.Log.e("FluxLinux", "Setup failed", e)
                                        }
                                    } else {
                                        permissionState.launchPermissionRequest()
                                    }
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (setupCompleted.value) Color.Transparent else MaterialTheme.colorScheme.primary,
                                    contentColor = if (setupCompleted.value) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimary
                                ),
                                border = if (setupCompleted.value) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (setupCompleted.value) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Environment Initialized", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                    } else {
                                        Text("Initialize Environment (Setup)", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Tweaks Button
                             Button(
                                onClick = {
                                    if (permissionState.status.isGranted) {
                                        val scriptManager = ScriptManager(context)
                                        val tweaksScript = scriptManager.getScriptContent("termux/termux_tweaks.sh")
                                        val forceTweaksScript = "rm -f \$HOME/.fluxlinux/termux_tweaks.done\n" + tweaksScript
                                        val copyCmd = "cat > \$HOME/termux_tweaks.sh << 'TWEAKS_EOF'\n$forceTweaksScript\nTWEAKS_EOF\nchmod +x \$HOME/termux_tweaks.sh && bash \$HOME/termux_tweaks.sh"
                                        val intent = TermuxIntentFactory.buildRunCommandIntent(copyCmd)
                                        try {
                                            onStartService(intent)
                                            android.widget.Toast.makeText(context, "Applying Termux Tweaks...", android.widget.Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            android.util.Log.e("FluxLinux", "Tweaks failed", e)
                                        }
                                    } else {
                                        permissionState.launchPermissionRequest()
                                    }
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (tweaksApplied.value) Color.Transparent else MaterialTheme.colorScheme.secondary,
                                    contentColor = if (tweaksApplied.value) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSecondary
                                ),
                                border = if (tweaksApplied.value) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (tweaksApplied.value) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Tweaks Applied", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                    } else {
                                        Text("Apply Termux Tweaks", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // PREREQUISITES CARD
                    GlassSettingCard {
                         Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                            Text(
                                "Prerequisites",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            val termuxInstalled = remember(refreshKey.value) { mutableStateOf(StateManager.isTermuxInstalled(context)) }
                            val x11Installed = remember(refreshKey.value) { mutableStateOf(StateManager.isTermuxX11Installed(context)) }

                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                // Termux
                                Column(modifier = Modifier.weight(1f)) {
                                    if (!termuxInstalled.value) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Not Installed", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("Termux ✓", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                                Text(StateManager.getTermuxVersion(context), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                                            }
                                        }
                                    }
                                }
                                
                                // X11
                                Column(modifier = Modifier.weight(1f)) {
                                    if (!x11Installed.value) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Not Installed", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("Termux:X11 ✓", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                                Text(StateManager.getTermuxX11Version(context), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // CONNECTION FIX CARD
                    GlassSettingCard {
                        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                            Text(
                                "Troubleshooting",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            Text("Fix Termux Connection", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Run this if distros fail to install or launch.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val fixCommand = "mkdir -p ~/.termux && echo \"allow-external-apps = true\" >> ~/.termux/termux.properties && termux-reload-settings"
                            
                             Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = fixCommand,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Termux Fix", fixCommand)
                                        clipboard.setPrimaryClip(clip)
                                        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.termux")
                                        if (launchIntent != null) {
                                            onStartActivity(launchIntent)
                                            android.widget.Toast.makeText(context, "Copied! Opening Termux...", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Copy & Open Termux", fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiary)
                                }
                                
                                Button(
                                    onClick = {
                                        StateManager.setConnectionFixed(context, true)
                                        android.widget.Toast.makeText(context, "Marked as fixed", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Mark Resolved", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }



                    // =====================================================================
                    // Support Actions (Separated)
                    // =====================================================================
                    GlassSettingCard {
                        Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = { onNavigateToOnboarding?.invoke() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Text("Show Onboarding Screen", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onNavigateToTroubleshooting?.invoke() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Text("Troubleshoot", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Divider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), 
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )



                    // =====================================================================
                    // TERMUX:X11 CONFIGURATION
                    // =====================================================================
                    GlassSettingCard {
                        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Termux:X11 Configuration",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                IconButton(
                                    onClick = { TermuxX11Preferences.openTermuxX11Preferences(context) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Open Termux:X11 Settings",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))

                            // Apply Settings Button
                            Button(
                                onClick = { 
                                    TermuxX11Preferences.applyToTermux(context)
                                    android.widget.Toast.makeText(context, "Applying Settings to Termux...", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth().height(45.dp)
                            ) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Apply Settings", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Display Settings
                            Text(
                                "Display",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            // Display Scale Slider
                            var displayScale by remember { mutableStateOf(TermuxX11Preferences.getDisplayScale(context).toFloat()) }
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Display Scale", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                    Text("${displayScale.toInt()}%", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = displayScale,
                                    onValueChange = { displayScale = it },
                                    onValueChangeFinished = {
                                        TermuxX11Preferences.setDisplayScale(context, displayScale.toInt())
                                    },
                                    valueRange = 50f..300f,
                                    steps = 24,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
                            
                            // Fullscreen
                            var fullscreen by remember { mutableStateOf(TermuxX11Preferences.getFullscreen(context)) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Fullscreen", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                    Text("Toggle immersive mode", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                                }
                                Switch(
                                    checked = fullscreen,
                                    onCheckedChange = {
                                        fullscreen = it
                                        TermuxX11Preferences.setFullscreen(context, it)
                                    }
                                )
                            }
                            
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
                            
                            // Hide Display Cutout
                            var hideCutout by remember { mutableStateOf(TermuxX11Preferences.getHideCutout(context)) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Hide Display Cutout", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                    Text("Hide notch/cutout area", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                                }
                                Switch(
                                    checked = hideCutout,
                                    onCheckedChange = {
                                        hideCutout = it
                                        TermuxX11Preferences.setHideCutout(context, it)
                                    }
                                )
                            }
                            
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
                            
                            // Keep Screen On
                            var keepScreenOn by remember { mutableStateOf(TermuxX11Preferences.getKeepScreenOn(context)) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Keep Screen On", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                    Text("Prevent screen timeout", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                                }
                                Switch(
                                    checked = keepScreenOn,
                                    onCheckedChange = {
                                        keepScreenOn = it
                                        TermuxX11Preferences.setKeepScreenOn(context, it)
                                    }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Input Settings
                            Text(
                                "Input",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            // Capture Pointer
                            var capturePointer by remember { mutableStateOf(TermuxX11Preferences.getCapturePointer(context)) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Capture External Pointer", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                    Text("Intercept hardware pointer events", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                                }
                                Switch(
                                    checked = capturePointer,
                                    onCheckedChange = {
                                        capturePointer = it
                                        TermuxX11Preferences.setCapturePointer(context, it)
                                    }
                                )
                            }
                            
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
                            
                            // Show Additional Keyboard
                            var showAdditionalKbd by remember { mutableStateOf(TermuxX11Preferences.getShowAdditionalKeyboard(context)) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Show Additional Keyboard", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                    Text("Extra keyboard with special keys", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                                }
                                Switch(
                                    checked = showAdditionalKbd,
                                    onCheckedChange = {
                                        showAdditionalKbd = it
                                        TermuxX11Preferences.setShowAdditionalKeyboard(context, it)
                                    }
                                )
                            }
                            
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
                            
                            // Show IME with External Keyboard
                            var showIME by remember { mutableStateOf(TermuxX11Preferences.getShowIME(context)) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Show IME with External Keyboard", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                    Text("Software keyboard with hardware keyboard", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                                }
                                Switch(
                                    checked = showIME,
                                    onCheckedChange = {
                                        showIME = it
                                        TermuxX11Preferences.setShowIME(context, it)
                                    }
                                )
                            }
                            
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
                            
                            // Prefer Scancodes
                            var preferScancodes by remember { mutableStateOf(TermuxX11Preferences.getPreferScancodes(context)) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Prefer Scancodes", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                    Text("Let X server handle keyboard layout", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                                }
                                Switch(
                                    checked = preferScancodes,
                                    onCheckedChange = {
                                        preferScancodes = it
                                        TermuxX11Preferences.setPreferScancodes(context, it)
                                    }
                                )
                            }
                            
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
                            
                            // Scancode Workaround
                            var scancodeWorkaround by remember { mutableStateOf(TermuxX11Preferences.getScancodeWorkaround(context)) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Hardware Keyboard Scancodes Workaround", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                    Text("Fix scancodes on some devices", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                                }
                                Switch(
                                    checked = scancodeWorkaround,
                                    onCheckedChange = {
                                        scancodeWorkaround = it
                                        TermuxX11Preferences.setScancodeWorkaround(context, it)
                                    }
                                )
                            }
                        }
                    }
                    
                    Divider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), 
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )

                    // =====================================================================
                    // 2. APP VERSION CARD
                    // =====================================================================
                    GlassSettingCard {
                         Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                             Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1A1A1A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_logo),
                                    contentDescription = "FluxLinux Logo",
                                    modifier = Modifier.size(48.dp), // Slightly larger for visibility
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("FluxLinux", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Text("v1.8p • Jun 17, 2026", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Made with ❤️ in Kotlin", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }

                    // =====================================================================
                    // TUTORIALS
                    // =====================================================================
                    GlassSettingCard(
                        onClick = { openUrl(context, "https://github.com/abhay-byte/fluxlinux#readme") }
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("📖", fontSize = 24.sp)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Read Documentation",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    "Setup guides and full tutorials",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // =====================================================================
                    // JOIN DISCORD
                    // =====================================================================
                    GlassSettingCard(
                        onClick = { openUrl(context, "https://discord.gg/tag9kXAs2x") }
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF5865F2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_discord),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Join our Discord",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    "Get help, share setups, and discuss features",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // =====================================================================
                    // 3. SPECIAL THANKS
                    // =====================================================================
                    GlassSettingCard {
                        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                            Text(
                                "Special Thanks",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            val credits = listOf(
                                Credit("Termux Team", "Termux", "https://github.com/termux"),
                                Credit("PRoot Distro", "Termux", "https://github.com/termux/proot-distro"),
                                Credit("Termux:X11", "Termux", "https://github.com/termux/termux-x11")
                            )
                            
                            credits.forEach { credit ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { openUrl(context, credit.url) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(credit.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    // =====================================================================
                    // 4. MY CARDS (Profile + Socials)
                    // =====================================================================
                    GlassSettingCard(onClick = { openUrl(context, "https://github.com/abhay-byte") }) {
                        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(70.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.me),
                                        contentDescription = "Profile",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Abhay Raj", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                    Text("@abhay-byte", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Passionate about building software, exploring hardware, and all things Linux.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.7f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    GlassSettingCard {
                        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                            Text("Connect With Me", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(bottom = 12.dp))
                            
                            val links = listOf(
                                SocialLink("GitHub", "https://github.com/abhay-byte", R.drawable.ic_github),
                                SocialLink("LinkedIn", "https://www.linkedin.com/in/abhay-byte/", R.drawable.ic_linkedin),
                                SocialLink("Portfolio", "https://abhayraj-porfolio.web.app/", R.drawable.ic_portfolio),
                                SocialLink("Instagram", "https://www.instagram.com/abhayrajx/", R.drawable.ic_instagram),
                                SocialLink("X (Twitter)", "https://x.com/arch_deve", R.drawable.ic_twitter_x)
                            )
                            
                            links.forEach { link ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { openUrl(context, link.url) }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(painter=painterResource(link.iconRes), null, tint=MaterialTheme.colorScheme.secondary, modifier=Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(link.name, color=MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }

                    // =====================================================================
                    // 5. STAR THIS REPO
                    // =====================================================================
                    GlassSettingCard(
                        onClick = { openUrl(context, "https://github.com/abhay-byte/FluxLinux") }
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_star),
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Star this Repository",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }



data class SocialLink(val name: String, val url: String, val iconRes: Int)
data class Credit(val name: String, val author: String, val url: String)

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("Settings", "Error opening URL", e)
    }
}
