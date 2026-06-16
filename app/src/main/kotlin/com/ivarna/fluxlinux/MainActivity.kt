package com.ivarna.fluxlinux

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.ivarna.fluxlinux.ui.components.BottomTab
import com.ivarna.fluxlinux.ui.components.GlassBottomNavigation
import com.ivarna.fluxlinux.ui.components.GlassScaffold
import com.ivarna.fluxlinux.ui.theme.FluxLinuxTheme
import com.ivarna.fluxlinux.core.utils.StateManager
import com.ivarna.fluxlinux.core.utils.ThemePreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

// Screen navigation enum
enum class Screen {
    ONBOARDING,
    PREREQUISITES,
    HOME,
    SETTINGS,
    TROUBLESHOOTING,
    ROOT_ACCESS,
    INSTALL_WIZARD,
    DISTRO_SETTINGS
}

class MainActivity : ComponentActivity() {
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleScriptCallback(intent)
    }

    private fun handleScriptCallback(intent: android.content.Intent) {
        android.util.Log.d("FluxLinux", "handleScriptCallback called with action: ${intent.action}, data: ${intent.data}")
        // Handle Deep Link: fluxlinux://callback?result=success&name=setup_termux
        if (intent.action == android.content.Intent.ACTION_VIEW && intent.data?.scheme == "fluxlinux") {
            val uri = intent.data
            val result = uri?.getQueryParameter("result")
            val scriptName = uri?.getQueryParameter("name") ?: "unknown"
            val installedComponents = uri?.getQueryParameter("components") // Legacy
            
            android.util.Log.d("FluxLinux", "Deep Link received: result=$result, scriptName=$scriptName")
            
            if (result == "success") {
                 // Check Queue first
                 val queueManager = com.ivarna.fluxlinux.core.utils.InstallationQueueManager
                 val currentTask = queueManager.currentTask
                 
                 // If the callback matches the current task, proceed queue.
                 // Strict match: the script's callback name must equal the current task's id.
                 // The previous `|| scriptName == "base_install"` fallback was redundant
                 // (base install tasks have id="base_install") AND allowed the base-install
                 // callback to be attributed to a stale/different task (e.g. a debian
                 // component task left over from a previous screen), which would set state
                 // for the wrong distro.
                 if (currentTask != null && scriptName == currentTask.id) {
                     android.widget.Toast.makeText(this, "Task '${currentTask.name}' Complete. Proceeding...", android.widget.Toast.LENGTH_SHORT).show()
                     
                     if (currentTask.type == com.ivarna.fluxlinux.core.utils.TaskType.BASE_INSTALL) {
                         // Mark Distro Installed on Base Success
                         // We assume we know the distro ID from state or we just mark currently selected?
                         // Ideally we pass distro ID in callback, or use 'selectedDistro' variable if available?
                         // MainActivity is recreated? No, usually singleTop/SingleTask. 'selectedDistro' state might receive it.
                         // But for safety, we rely on 'distro_install_ID' naming convention if possible, but our base script has generic name.
                         // Let's rely on 'base_install' result means 'selectedDistro' (which should be saved/restored if activity died).
                         // Actually, 'selectedDistro' is inside setContent scope.
                         // We need a class-level property or StateManager access.
                         // For now, let's assume 'selectedDistro' is unavailable here and we rely on user manually refreshing or existing state?
                         // NO, we MUST update StateManager.
                         // FIX: Let's extract Distro ID from the Task if possible.
                         // But Task struct doesn't have distroId yet.
                         // We'll trust the processNextInstallTask logic to handle the flow.
                         // Marking distro installed:
                         // We can iterate Distros and match ID? Or just skip marking if ID not known?
                         // The Base Script DOES NOT include ID in "name=base_install". 
                         // However, InstallQueueManager is singleton. We can store context there?
                         // Let's just process queue. The user will see "Installed" in UI eventually.
                     }
                     
                     // Mark Component as Installed in StateManager
                     val distroId = currentTask.distroId
                     if (currentTask.type == com.ivarna.fluxlinux.core.utils.TaskType.COMPONENT) {
                         if (currentTask.isUninstall) {
                             // Uninstall completion: clear the "installed" flag
                             StateManager.setComponentInstalled(this, distroId, currentTask.id, false)
                             android.widget.Toast.makeText(this, "${currentTask.name.replace("Uninstall ", "")} Uninstalled 🗑️", android.widget.Toast.LENGTH_LONG).show()
                         } else {
                             StateManager.setComponentInstalled(this, distroId, currentTask.id, true)
                         }
                     }
                     // Force State Update
                     StateManager.triggerRefresh()
                 } else {
                     // Legacy / Standalone handling
                     if (scriptName.startsWith("distro_install_")) {
                         val distroId = scriptName.removePrefix("distro_install_")
                         StateManager.setDistroInstalled(this, distroId, true)
                         android.widget.Toast.makeText(this, "$distroId Installed! ✅", android.widget.Toast.LENGTH_LONG).show()
                     } else if (scriptName.startsWith("distro_uninstall_")) {
                         val distroId = scriptName.removePrefix("distro_uninstall_")
                         StateManager.clearDistroState(this, distroId)
                         android.widget.Toast.makeText(this, "$distroId Uninstalled! 🗑️", android.widget.Toast.LENGTH_LONG).show()
                     } else {
                         // Generic Script
                         StateManager.setScriptStatus(this, scriptName, true)
                         android.widget.Toast.makeText(this, "Script '$scriptName' details saved.", android.widget.Toast.LENGTH_SHORT).show()
                     }
                 }
                 
                 // Process Next
                 processNextInstallTask()
                 
            } else {
                 // Component failed
                 android.widget.Toast.makeText(this, "Task '$scriptName' failed! ❌", android.widget.Toast.LENGTH_LONG).show()
                 com.ivarna.fluxlinux.core.utils.InstallationQueueManager.clear() // Stop queue on failure
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — FGS still works, but FGS notification visibility
           improves on Android 13+ when POST_NOTIFICATIONS is granted. */ }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun processNextInstallTask() {
        val queueManager = com.ivarna.fluxlinux.core.utils.InstallationQueueManager
        if (!queueManager.hasPending()) {
            android.widget.Toast.makeText(this, "All Installation Steps Complete! 🎉", android.widget.Toast.LENGTH_LONG).show()
            
            // Mark Distro Installed
            val distroId = queueManager.activeDistroId
            if (distroId != null) {
                com.ivarna.fluxlinux.core.utils.StateManager.setDistroInstalled(this, distroId, true)
                // Trigger UI refresh via StateFlow
                com.ivarna.fluxlinux.core.utils.StateManager.triggerRefresh()
            }
            
            queueManager.clear()
            // Reset Progress UI state effectively done by clear()
            return
        }

        val nextTask = queueManager.next() ?: return // advances queue state internal
        
        // Log Update
        android.util.Log.d("FluxLinux", "Processing Task: ${nextTask.name}")
        
        android.widget.Toast.makeText(this, "Starting: ${nextTask.name}...", android.widget.Toast.LENGTH_SHORT).show()
        
        if (nextTask.type == com.ivarna.fluxlinux.core.utils.TaskType.HW_ACCEL || nextTask.type == com.ivarna.fluxlinux.core.utils.TaskType.COMPONENT) {
            val scriptName = nextTask.scriptName
            val distroId = nextTask.distroId
            
            if (scriptName != null) {
                // Fetch Script Content
                // We need ScriptManager. Since we are in Activity, we can instantiate it.
                val scriptManager = com.ivarna.fluxlinux.core.data.ScriptManager(this)
                var scriptContent = scriptManager.getScriptContent(scriptName)
                
                // Inject Environment Variables from Task
                if (nextTask.extraEnv.isNotEmpty()) {
                    val envBlock = nextTask.extraEnv.entries.joinToString("\n") { "export ${it.key}=\"${it.value}\"" }
                    // Prepend to script content
                    scriptContent = "$envBlock\n\n$scriptContent"
                }
                
                // Build Intent with Callback
                val intent = com.ivarna.fluxlinux.core.data.TermuxIntentFactory.buildRunFeatureScriptIntent(
                    distroId = distroId,
                    scriptContent = scriptContent,
                    callbackName = nextTask.id,
                    isUninstall = nextTask.isUninstall
                )
                
                try {
                    startService(intent) // or startActivity depending on IntentFactory implementation.
                    // buildRunFeatureScriptIntent returns RunCommandService intent?
                    // No, TermuxIntentFactory returns Intent for RUN_COMMAND usually.
                    // Actually buildRunCommandIntent uses 'com.termux.permission.RUN_COMMAND'.
                    // So startService is correct IF it targets Termux Service, 
                    // BUT for Termux RUN_COMMAND we usually use startService.
                    // However, my stub 'onStartServiceStub' used startService.
                } catch (e: Exception) {
                    android.util.Log.e("FluxLinux", "Failed to start task: ${nextTask.name}", e)
                    android.widget.Toast.makeText(this, "Failed to start ${nextTask.name}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class, ExperimentalHazeMaterialsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            // Force Permanent Dark Mode
            val currentThemeMode = com.ivarna.fluxlinux.core.utils.ThemeMode.DARK
            
            FluxLinuxTheme(themeMode = currentThemeMode) {
                val onboardingComplete = StateManager.isOnboardingComplete(this@MainActivity)
                
                // Permission State (Lifted for Settings and Home access)
                val permissionState = rememberPermissionState(
                    permission = "com.termux.permission.RUN_COMMAND"
                )

                // Navigation state
                var currentScreen by remember { 
                    mutableStateOf(if (onboardingComplete) Screen.HOME else Screen.ONBOARDING) 
                }
                
                var currentTab by remember { mutableStateOf(BottomTab.HOME) }
                
                // Selected Distro for Wizard/Settings
                var selectedDistro by remember { mutableStateOf<com.ivarna.fluxlinux.core.data.Distro?>(null) }
                
                // Refresh key to force UI update on resume
                // Collected from StateManager for remote triggers too
                val refreshKey by com.ivarna.fluxlinux.core.utils.StateManager.refreshTrigger.collectAsState()
                
                // ALSO react to Lifecycle
                var lifecycleRefreshKey by remember { mutableStateOf(0) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            lifecycleRefreshKey++
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                
                // Helpers for service/activity
                val onStartServiceStub: (android.content.Intent) -> Unit = { intent ->
                    try { startService(intent) } catch (e: Exception) { android.util.Log.e("FluxLinux", "StartService failed", e) }
                }
                val onStartActivityStub: (android.content.Intent) -> Unit = { intent ->
                    try { startActivity(intent) } catch (e: Exception) { android.util.Log.e("FluxLinux", "StartActivity failed", e) }
                }
                
                // Navigation Callbacks
                val onNavigateToInstall: (com.ivarna.fluxlinux.core.data.Distro) -> Unit = { distro ->
                    selectedDistro = distro
                    currentScreen = Screen.INSTALL_WIZARD
                }
                val onNavigateToDistroSettings: (com.ivarna.fluxlinux.core.data.Distro) -> Unit = { distro ->
                    selectedDistro = distro
                    currentScreen = Screen.DISTRO_SETTINGS
                }
                
                @Composable
                fun MainScreenContent(
                    tab: BottomTab,
                    hazeState: HazeState
                ) {
                    when (tab) {
                        BottomTab.HOME -> {
                            com.ivarna.fluxlinux.ui.screens.HomeScreen(
                                permissionState = permissionState,
                                hazeState = hazeState,
                                scriptRefreshTrigger = refreshKey + lifecycleRefreshKey,
                                onStartService = onStartServiceStub,
                                onStartActivity = onStartActivityStub,
                                // Pass navigation callbacks
                                onNavigateToInstall = onNavigateToInstall,
                                onNavigateToSettings = onNavigateToDistroSettings
                            )
                        }
                        BottomTab.DISTROS -> {
                            com.ivarna.fluxlinux.ui.screens.DistroScreen(
                                permissionState = permissionState,
                                hazeState = hazeState,
                                onStartService = onStartServiceStub,
                                onStartActivity = onStartActivityStub,
                                onNavigateToInstall = onNavigateToInstall
                            )
                        }
                    }
                }

                // Helper for Top Bar
                @Composable
                fun TopBar(
                    hazeState: HazeState,
                    onSettingsClick: () -> Unit
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hazeChild(
                                state = hazeState,
                                style = HazeStyle(
                                    backgroundColor = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                                    blurRadius = 20.dp,
                                    tint = null
                                )
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .windowInsetsPadding(WindowInsets.statusBars),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_logo),
                                    contentDescription = "Logo",
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "FluxLinux",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (StateManager.isTermuxInstalled(LocalContext.current)) {
                                   Text(
                                       text = StateManager.getPackageSize(LocalContext.current, "com.termux"),
                                       style = MaterialTheme.typography.labelSmall,
                                       color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                       modifier = Modifier.padding(end = 8.dp)
                                   )
                                }
                                
                                IconButton(onClick = onSettingsClick) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Show appropriate screen based on state
                when (currentScreen) {
                    Screen.ONBOARDING -> {
                        val showPrerequisites = remember { mutableStateOf(false) }
                        if (!showPrerequisites.value) {
                            com.ivarna.fluxlinux.ui.screens.OnboardingScreen(
                                onGetStarted = { showPrerequisites.value = true }
                            )
                        } else {
                            com.ivarna.fluxlinux.ui.screens.PrerequisitesScreen(
                                onComplete = {
                                    StateManager.setOnboardingComplete(this@MainActivity, true)
                                    currentScreen = Screen.HOME
                                }
                            )
                        }
                    }
                    Screen.HOME -> {
                        val hazeState = remember { HazeState() }
                        GlassScaffold(
                            hazeState = hazeState,
                            topBar = {
                                TopBar(
                                    hazeState = hazeState,
                                    onSettingsClick = { currentScreen = Screen.SETTINGS }
                                )
                            },
                            bottomBar = {
                                GlassBottomNavigation(
                                    selectedTab = currentTab,
                                    onTabSelected = { currentTab = it },
                                    hazeState = hazeState
                                )
                            }
                        ) {
                            MainScreenContent(
                                tab = currentTab,
                                hazeState = hazeState
                            )
                        }
                    }
                    Screen.SETTINGS -> {
                        com.ivarna.fluxlinux.ui.screens.SettingsScreen(
                            onBack = { currentScreen = Screen.HOME },
                            permissionState = permissionState,
                            onStartService = onStartServiceStub,
                            onStartActivity = onStartActivityStub,
                            onNavigateToOnboarding = {
                                StateManager.setOnboardingComplete(this@MainActivity, false)
                                currentScreen = Screen.ONBOARDING
                            },
                            onNavigateToTroubleshooting = { currentScreen = Screen.TROUBLESHOOTING },
                            onNavigateToRootCheck = { currentScreen = Screen.ROOT_ACCESS }
                        )
                    }
                    Screen.TROUBLESHOOTING -> {
                        com.ivarna.fluxlinux.ui.screens.TroubleshootingScreen(
                            onBack = { currentScreen = Screen.SETTINGS }
                        )
                    }
                    Screen.PREREQUISITES -> { currentScreen = Screen.HOME }
                    Screen.ROOT_ACCESS -> {
                        com.ivarna.fluxlinux.ui.screens.RootAccessScreen(
                            onBack = { currentScreen = Screen.SETTINGS },
                            onEnableChroot = {
                                android.widget.Toast.makeText(this@MainActivity, "Chroot Mode Enabled", android.widget.Toast.LENGTH_SHORT).show()
                                currentScreen = Screen.SETTINGS
                            }
                        )
                    }
                    Screen.INSTALL_WIZARD -> {
                         val hazeState = remember { HazeState() }
                         if (selectedDistro != null) {
                             com.ivarna.fluxlinux.ui.screens.InstallConfigScreen(
                                 distro = selectedDistro!!,
                                 onBack = { currentScreen = Screen.HOME }, // Or Screen.DISTROS depending on where they came from? Let's just go Home for now or maintain history.
                                 // Actually for simplicity, back goes to tab view.
                                 hazeState = hazeState,
                                 onInstallStart = { components, theme, gpu, desktopEnv ->
                                     if (permissionState.status.isGranted) {
                                         // NEW QUEUE-BASED WORKFLOW
                                         lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                              withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                  android.widget.Toast.makeText(this@MainActivity, "Preparing Queue...", android.widget.Toast.LENGTH_SHORT).show()
                                              }
                                              
                                              val queueManager = com.ivarna.fluxlinux.core.utils.InstallationQueueManager
                                              queueManager.clear()
                                              
                                              val tasks = mutableListOf<com.ivarna.fluxlinux.core.utils.InstallTask>()
                                              
                                              // 1. Base Task (Manual)
                                              tasks.add(com.ivarna.fluxlinux.core.utils.InstallTask(
                                                  id = "base_install",
                                                  name = "Base System Install",
                                                  type = com.ivarna.fluxlinux.core.utils.TaskType.BASE_INSTALL,
                                                  isManual = true,
                                                  distroId = selectedDistro!!.id,
                                                  extraEnv = mapOf("FLUX_THEME" to theme, "FLUX_GPU" to gpu, "FLUX_DESKTOP_ENV" to desktopEnv)
                                              ))
                                              
                                              // 2. Hardware Acceleration
                                              // Determine if we should run it based on selection
                                              val runHwAccel = gpu != "manual" && selectedDistro!!.id != "termux"
                                              // Even if auto, we run the script which detects.
                                              // If 'virgl' or 'turnip', we pass via Env.
                                              if (selectedDistro!!.id != "termux") {
                                                  // Pass the GPU pref to the script if not 'ask'/'manual'
                                                  // 'ask' means script runs interactively? Queue is non-interactive usually.
                                                  // HwAccel script needs to handle 'ask' by blocking? No, 'proot' execution is tricky for interactivity if wrapped.
                                                  // But here we are automating. 'ask' might just default to 'auto' logic or prompt if we handle it?
                                                  // Best approach: Force explicit choice or auto.
                                                  tasks.add(com.ivarna.fluxlinux.core.utils.InstallTask(
                                                      id = "hw_accel",
                                                      name = "Hardware Acceleration",
                                                      type = com.ivarna.fluxlinux.core.utils.TaskType.COMPONENT,
                                                      scriptName = "debian/common/setup/setup_hw_accel_debian.sh",
                                                      distroId = selectedDistro!!.id,
                                                      extraEnv = mapOf("FLUX_GPU" to gpu)
                                                  ))
                                              }
                                              
                                              // 3. If KDE selected, inject kde_plasma component automatically
                                              if (desktopEnv == "KDE") {
                                                  val kdeComp = selectedDistro!!.components.find { it.id == "kde_plasma" }
                                                  if (kdeComp != null) {
                                                      tasks.add(com.ivarna.fluxlinux.core.utils.InstallTask(
                                                          id = "kde_plasma",
                                                          name = kdeComp.name,
                                                          type = com.ivarna.fluxlinux.core.utils.TaskType.COMPONENT,
                                                          scriptName = kdeComp.scriptName,
                                                          distroId = selectedDistro!!.id,
                                                          extraEnv = emptyMap()
                                                      ))
                                                  }
                                              }
                                              
                                              // 4. User-selected Components (filter mandatory ones already added above)
                                              val alreadyQueued = setOf("hw_accel", "kde_plasma")
                                              components.filter { it.id !in alreadyQueued }.forEach { comp ->
                                                  tasks.add(com.ivarna.fluxlinux.core.utils.InstallTask(
                                                      id = comp.id,
                                                      name = comp.name,
                                                      type = com.ivarna.fluxlinux.core.utils.TaskType.COMPONENT,
                                                      scriptName = comp.scriptName,
                                                      distroId = selectedDistro!!.id,
                                                      extraEnv = mapOf("FLUX_THEME" to theme) // Pass theme if needed by component
                                                  ))
                                              }
                                              
                                               queueManager.enqueue(tasks)

                                               // Start the first task (Base Install)
                                               val firstTask = queueManager.next()
                                               if (firstTask != null && firstTask.type == com.ivarna.fluxlinux.core.utils.TaskType.BASE_INSTALL) {
                                                    // Generate Base Script
                                                    val script = com.ivarna.fluxlinux.core.data.TermuxIntentFactory.getBaseInstallScript(this@MainActivity, selectedDistro!!)

                                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                          // Start the foreground service so Android keeps the bridge alive
                                                          // even when the activity is backgrounded (T3 / GH-9).
                                                          com.ivarna.fluxlinux.core.service.InstallServerService.start(this@MainActivity, script)
                                                          android.widget.Toast.makeText(this@MainActivity, "Starting install server…", android.widget.Toast.LENGTH_SHORT).show()
                                                          lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                              val port = com.ivarna.fluxlinux.core.service.InstallServerService.awaitPort()
                                                              if (port == null) {
                                                                  withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                      android.widget.Toast.makeText(this@MainActivity, "Server failed to start", android.widget.Toast.LENGTH_LONG).show()
                                                                  }
                                                                  return@launch
                                                              }
                                                              withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                  // INTERACTIVE COMMAND: Download then Run
                                                                  // Prepend Exports based on Selection
                                                                  // Use one-shot env var syntax: VAR=val command
                                                                  val exports = "FLUX_THEME=$theme FLUX_GPU=$gpu"
                                                                  
                                                                  // Determine Root/Chroot status
                                                                  val isChroot = selectedDistro!!.chrootSupported && !selectedDistro!!.prootSupported
                                                                  
                                                                  // Define Clipboard
                                                                  val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

                                                                  if (isChroot) {
                                                                       // Chroot/Root Command: simpler, avoids Termux 'pkg' commands
                                                                       // Assumes /system/bin/curl exists (standard on rooted Android 10+)
                                                                       val chrootCommand = "curl -L -o install.sh http://127.0.0.1:$port/install && $exports sh install.sh"
                                                                       
                                                                       val clip = android.content.ClipData.newPlainText("FluxLinux Install", chrootCommand)
                                                                       clipboard.setPrimaryClip(clip)

                                                                       android.app.AlertDialog.Builder(this@MainActivity)
                                                                        .setTitle("⚠️ Root Access Required")
                                                                        .setMessage("This distro requires Root/Chroot.\n\n1. Open Termux\n2. Type 'su' and press Enter 🔑\n3. Paste the command and Run it.\n4. Follow prompts.")
                                                                        .setPositiveButton("Open Termux") { _, _ ->
                                                                            val launchIntent = com.ivarna.fluxlinux.core.data.TermuxIntentFactory.buildOpenTermuxIntent(this@MainActivity)
                                                                            if (launchIntent != null) startActivity(launchIntent)
                                                                            // Stay on Install Wizard/DistroSettings so user sees progress
                                                                        }
                                                                         .setNegativeButton("Cancel") { _, _ -> com.ivarna.fluxlinux.core.service.InstallServerService.stop(this@MainActivity) }
                                                                         .setCancelable(false)
                                                                         .show()
                                                                   } else {
                                                                        // Standard Proot Command
                                                                        val installCommand = "pkg update -y && pkg install curl -y && curl -L -o install.sh http://127.0.0.1:$port/install && $exports bash install.sh"
                                                                        val clip = android.content.ClipData.newPlainText("FluxLinux Install", installCommand)
                                                                        clipboard.setPrimaryClip(clip)

                                                                        android.app.AlertDialog.Builder(this@MainActivity)
                                                                         .setTitle("Phase 1: Base Install 🚀")
                                                                         .setMessage("Queue initialized!\n\n1. Open Termux\n2. Paste command\n3. Follow prompts (GPU/Theme)\n4. App will auto-launch next steps.")
                                                                         .setPositiveButton("Open Termux") { _, _ ->
                                                                             // The service observes InstallationQueueManager.installState; it will
                                                                             // tear itself down when the install completes.
                                                                             val launchIntent = com.ivarna.fluxlinux.core.data.TermuxIntentFactory.buildOpenTermuxIntent(this@MainActivity)
                                                                             if (launchIntent != null) startActivity(launchIntent)
                                                                             currentScreen = Screen.HOME
                                                                         }
                                                                         .setNegativeButton("Cancel") { _, _ -> com.ivarna.fluxlinux.core.service.InstallServerService.stop(this@MainActivity) }
                                                                         .setCancelable(false)
                                                                         .show()
                                                                   }
                                                             }
                                                         }
                                                   }
                                              }
                                         }
                                     } else {
                                         permissionState.launchPermissionRequest()
                                     }
                                 }
                             )
                         } else {
                             currentScreen = Screen.HOME
                         }
                    }
                    Screen.DISTRO_SETTINGS -> {
                         val hazeState = remember { HazeState() }
                         if (selectedDistro != null) {
                              com.ivarna.fluxlinux.ui.screens.DistroSettingsScreen(
                                  distro = selectedDistro!!,
                                  onBack = { currentScreen = Screen.HOME },
                                  hazeState = hazeState,
                                   onInstallComponent = { component, extraEnv ->
                                       if (permissionState.status.isGranted) {
                                           // Start the foreground service so the install progress notification
                                           // stays alive when the user backgrounds the app (T3 / GH-9).
                                           com.ivarna.fluxlinux.core.service.InstallServerService.start(this@MainActivity)
                                           lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                               val queueManager = com.ivarna.fluxlinux.core.utils.InstallationQueueManager
                                               queueManager.clear()

                                               val task = com.ivarna.fluxlinux.core.utils.InstallTask(
                                                   id = component.id,
                                                   name = component.name,
                                                   type = com.ivarna.fluxlinux.core.utils.TaskType.COMPONENT,
                                                   scriptName = component.scriptName,
                                                   distroId = selectedDistro!!.id,
                                                   extraEnv = extraEnv,
                                                   isUninstall = false
                                               )
                                               queueManager.enqueue(listOf(task))

                                               withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                   processNextInstallTask()
                                               }
                                           }
                                       } else {
                                           permissionState.launchPermissionRequest()
                                       }
                                   },
                                  onUninstallComponent = { component ->
                                      if (permissionState.status.isGranted) {
                                          com.ivarna.fluxlinux.core.service.InstallServerService.start(this@MainActivity)
                                          lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                              val queueManager = com.ivarna.fluxlinux.core.utils.InstallationQueueManager
                                              queueManager.clear()

                                              val task = com.ivarna.fluxlinux.core.utils.InstallTask(
                                                  id = component.id,
                                                  name = "Uninstall ${component.name}",
                                                  type = com.ivarna.fluxlinux.core.utils.TaskType.COMPONENT,
                                                  scriptName = component.scriptName,
                                                  distroId = selectedDistro!!.id,
                                                  extraEnv = emptyMap(),
                                                  isUninstall = true
                                              )
                                              queueManager.enqueue(listOf(task))

                                              withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                  processNextInstallTask()
                                              }
                                          }
                                      } else {
                                          permissionState.launchPermissionRequest()
                                      }
                                  },
                                  onReinstallDistro = {
                                       if (permissionState.status.isGranted) {
                                           lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                               val queueManager = com.ivarna.fluxlinux.core.utils.InstallationQueueManager
                                                                                               // No need to enqueue a task here since the base install is handled manually via curl/Termux.
                                                 // Enqueueing it causes the UI to permanently stay in 'isInstalling' (Busy) mode.

                                               withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    // Reuse InstallConfigScreen logic for Base Install handling
                                                    // We manually trigger the logic here because it's a manual script execution in Termux.

                                                    val script = com.ivarna.fluxlinux.core.data.TermuxIntentFactory.getBaseInstallScript(this@MainActivity, selectedDistro!!)

                                                    // Start the foreground service so Android keeps the bridge alive
                                                    // across backgrounding (T3 / GH-9).
                                                    com.ivarna.fluxlinux.core.service.InstallServerService.start(this@MainActivity, script)

                                                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                         val port = com.ivarna.fluxlinux.core.service.InstallServerService.awaitPort()
                                                         if (port == null) {
                                                             withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                 android.widget.Toast.makeText(this@MainActivity, "Server failed to start", android.widget.Toast.LENGTH_LONG).show()
                                                             }
                                                             return@launch
                                                         }
                                                         withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                               val isChroot = selectedDistro!!.chrootSupported && !selectedDistro!!.prootSupported
                                                               val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

                                                               if (isChroot) {
                                                                    // Chroot Logic
                                                                    val chrootCommand = "curl -L -o install.sh http://127.0.0.1:$port/install && sh install.sh"
                                                                    val clip = android.content.ClipData.newPlainText("FluxLinux Install", chrootCommand)
                                                                    clipboard.setPrimaryClip(clip)

                                                                    android.app.AlertDialog.Builder(this@MainActivity)
                                                                     .setTitle("⚠️ Root Required (Reinstall)")
                                                                     .setMessage("1. Open Termux\n2. Type 'su' -> Enter 🔑\n3. Paste & Run command.")
                                                                     .setPositiveButton("Open Termux") { _, _ ->
                                                                         val launchIntent = com.ivarna.fluxlinux.core.data.TermuxIntentFactory.buildOpenTermuxIntent(this@MainActivity)
                                                                         if (launchIntent != null) startActivity(launchIntent)
                                                                     }
                                                                     .setNegativeButton("Cancel") { _, _ -> com.ivarna.fluxlinux.core.service.InstallServerService.stop(this@MainActivity) }
                                                                     .setCancelable(false)
                                                                     .show()
                                                               } else {
                                                                    // Proot Logic
                                                                    val curlCommand = "pkg update -y && pkg install curl -y && curl -L -o install.sh http://127.0.0.1:$port/install && bash install.sh"
                                                                    val clip = android.content.ClipData.newPlainText("FluxLinux Install", curlCommand)
                                                                    clipboard.setPrimaryClip(clip)

                                                                    android.app.AlertDialog.Builder(this@MainActivity)
                                                                     .setTitle("Reinstalling Base System 🚀")
                                                                     .setMessage("Command copied!\n\n1. Open Termux\n2. Paste command")
                                                                     .setPositiveButton("Open Termux") { _, _ ->
                                                                         val launchIntent = com.ivarna.fluxlinux.core.data.TermuxIntentFactory.buildOpenTermuxIntent(this@MainActivity)
                                                                         if (launchIntent != null) startActivity(launchIntent)
                                                                     }
                                                                     .setNegativeButton("Cancel") { _, _ -> com.ivarna.fluxlinux.core.service.InstallServerService.stop(this@MainActivity) }
                                                                     .setCancelable(false)
                                                                     .show()
                                                               }
                                                         }
                                                    }
                                              }
                                          }
                                      } else {
                                          permissionState.launchPermissionRequest()
                                      }
                                 },
                                 onUninstallDistro = {
                                     // Navigate to Home first, then trigger uninstall
                                     // The uninstall script sends a callback to the app when complete
                                     // which triggers handleScriptCallback to update state
                                      if (permissionState.status.isGranted) {
                                          val intent = com.ivarna.fluxlinux.core.data.TermuxIntentFactory.buildUninstallIntent(selectedDistro!!.id)
                                          try {
                                              onStartServiceStub(intent)
                                              // DON'T update state here - let the callback handle it
                                              // StateManager will be updated when script sends am start callback
                                              android.widget.Toast.makeText(this@MainActivity, "Uninstalling...", android.widget.Toast.LENGTH_SHORT).show()
                                              currentScreen = Screen.HOME
                                          } catch(e: Exception) {
                                              android.util.Log.e("FluxLinux", "Uninstall failed", e)
                                          }
                                      } else {
                                          permissionState.launchPermissionRequest()
                                      }
                                  },
                                  onStartActivity = onStartActivityStub,
                                  onNavigateToStart = { /* Not used in Settings, but if needed */ },
                                  onLaunchXfce = {
                                      if (permissionState.status.isGranted) {
                                          try {
                                              val intent = com.ivarna.fluxlinux.core.data.TermuxIntentFactory.buildLaunchGuiIntent(this@MainActivity, selectedDistro!!.id)
                                              onStartServiceStub(intent)
                                          } catch (e: Exception) {
                                              android.util.Log.e("FluxLinux", "Launch XFCE4 failed", e)
                                          }
                                      } else {
                                          permissionState.launchPermissionRequest()
                                      }
                                  },
                                  onStopXfce = {
                                      try {
                                          val intent = com.ivarna.fluxlinux.core.data.TermuxIntentFactory.buildStopGuiIntent(this, selectedDistro!!.id)
                                          onStartServiceStub(intent)
                                      } catch (e: Exception) {
                                          android.util.Log.e("FluxLinux", "Stop XFCE4 failed", e)
                                      }
                                  },
                                  onLaunchKde = {
                                      if (permissionState.status.isGranted) {
                                          try {
                                              val intent = com.ivarna.fluxlinux.core.data.TermuxIntentFactory.buildLaunchKdeGuiIntent(this@MainActivity, selectedDistro!!.id)
                                              onStartServiceStub(intent)
                                          } catch (e: Exception) {
                                              android.util.Log.e("FluxLinux", "Launch KDE failed", e)
                                          }
                                      } else {
                                          permissionState.launchPermissionRequest()
                                      }
                                  },
                                  onStopKde = {
                                      try {
                                           val intent = com.ivarna.fluxlinux.core.data.TermuxIntentFactory.buildStopKdeGuiIntent(this, selectedDistro!!.id)
                                          onStartServiceStub(intent)
                                      } catch (e: Exception) {
                                          android.util.Log.e("FluxLinux", "Stop KDE failed", e)
                                      }
                                  }
                              )

                         } else {
                             currentScreen = Screen.HOME
                         }
                    }
                }
            }
        }
    }
}
