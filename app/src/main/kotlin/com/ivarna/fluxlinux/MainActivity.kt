package com.ivarna.fluxlinux


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.ivarna.fluxlinux.ui.components.BottomTab
import com.ivarna.fluxlinux.ui.components.GlassBottomNavigation
import androidx.compose.ui.platform.LocalContext
import com.ivarna.fluxlinux.ui.components.GlassScaffold
import com.ivarna.fluxlinux.ui.theme.FluxLinuxTheme

// Screen navigation enum
enum class Screen {
    ONBOARDING,
    PREREQUISITES,
    HOME,
    SETTINGS,
    TROUBLESHOOTING,
    ROOT_ACCESS
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            	 // Watch Theme Preference
                val context = LocalContext.current
                val themePrefs = remember { com.ivarna.fluxlinux.core.utils.ThemePreferences(context) }
                // We need to observe changes, but SharedPreferences doesn't support State out of the box easily without a listener.
                // For simplicity in this step, we'll assume SettingsScreen updates a shared mutable state or we poll/reload.
                // Ideally we'd use a Flow or LiveData. Let's use a simple State for now that we can pass to Settings to update.
                
                // Better approach: Lift state up
                var currentThemeMode by remember { mutableStateOf(themePrefs.getThemeMode()) }

                FluxLinuxTheme(themeMode = currentThemeMode) {
                    // Check if onboarding has been completed (persisted state)
                    val onboardingComplete = com.ivarna.fluxlinux.core.utils.StateManager.isOnboardingComplete(this@MainActivity)
                
                // Navigation state
                var currentScreen by remember { 
                    mutableStateOf(if (onboardingComplete) Screen.HOME else Screen.ONBOARDING) 
                }
                
                var currentTab by remember { mutableStateOf(BottomTab.HOME) }
                
                // Helper to render content with Bottom Nav
                @Composable
                fun MainScreenContent(
                    tab: BottomTab,
                    hazeState: dev.chrisbanes.haze.HazeState,
                    permissionState: com.google.accompanist.permissions.PermissionState,
                    onNavigateToSettings: () -> Unit
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (tab) {
                            BottomTab.HOME -> {
                                com.ivarna.fluxlinux.ui.screens.HomeScreen(
                                    permissionState = permissionState,
                                    hazeState = hazeState,
                                    onNavigateToSettings = onNavigateToSettings,
                                    onStartService = { intent ->
                                        try {
                                            startService(intent)
                                        } catch (e: Exception) {
                                            android.util.Log.e("FluxLinux", "Service start failed", e)
                                        }
                                    },
                                    onStartActivity = { intent ->
                                        try {
                                            startActivity(intent)
                                        } catch (e: Exception) {
                                            android.util.Log.e("FluxLinux", "Activity start failed", e)
                                        }
                                    }
                                )
                            }
                            BottomTab.DISTROS -> {
                                com.ivarna.fluxlinux.ui.screens.DistroScreen(
                                    permissionState = permissionState,
                                    hazeState = hazeState,
                                    onStartService = { intent ->
                                        try {
                                            startService(intent)
                                        } catch (e: Exception) {
                                            android.util.Log.e("FluxLinux", "Service start failed", e)
                                        }
                                    },
                                    onStartActivity = { intent ->
                                        try {
                                            startActivity(intent)
                                        } catch (e: Exception) {
                                            android.util.Log.e("FluxLinux", "Activity start failed", e)
                                        }
                                    }
                                )
                            }
                        }
                        
                        GlassBottomNavigation(
                            selectedTab = currentTab,
                            onTabSelected = { currentTab = it },
                            hazeState = hazeState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
                
                // Show appropriate screen based on state
                when (currentScreen) {
                    Screen.ONBOARDING -> {
                        // Show Onboarding → Prerequisites flow
                        val showPrerequisites = remember { mutableStateOf(false) }
                        
                        if (!showPrerequisites.value) {
                            // Show Onboarding
                            com.ivarna.fluxlinux.ui.screens.OnboardingScreen(
                                onGetStarted = {
                                    showPrerequisites.value = true
                                }
                            )
                        } else {
                            // Show Prerequisites
                            com.ivarna.fluxlinux.ui.screens.PrerequisitesScreen(
                                onComplete = {
                                    // Mark onboarding as complete and show home
                                    com.ivarna.fluxlinux.core.utils.StateManager.setOnboardingComplete(this@MainActivity, true)
                                    currentScreen = Screen.HOME
                                }
                            )
                        }
                    }
                    Screen.HOME -> {
                        // Show Home Screen
                        GlassScaffold { hazeState ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                val permissionState = rememberPermissionState(
                                    permission = "com.termux.permission.RUN_COMMAND"
                                )
                                
                                MainScreenContent(
                                    tab = currentTab,
                                    hazeState = hazeState,
                                    permissionState = permissionState,
                                    onNavigateToSettings = { currentScreen = Screen.SETTINGS }
                                )
                            }
                        }
                    }
                    Screen.SETTINGS -> {
                        // Show Settings Screen
                        com.ivarna.fluxlinux.ui.screens.SettingsScreen(
                            onBack = { currentScreen = Screen.HOME },
                            onNavigateToOnboarding = {
                                com.ivarna.fluxlinux.core.utils.StateManager.setOnboardingComplete(this@MainActivity, false)
                                currentScreen = Screen.ONBOARDING
                            },
                            onNavigateToTroubleshooting = { currentScreen = Screen.TROUBLESHOOTING },
                            onNavigateToRootCheck = { currentScreen = Screen.ROOT_ACCESS },
                            onThemeChanged = { newMode -> 
                                themePrefs.setThemeMode(newMode)
                                currentThemeMode = newMode 
                            },
                            currentTheme = currentThemeMode
                        )
                    }
                    Screen.TROUBLESHOOTING -> {
                        com.ivarna.fluxlinux.ui.screens.TroubleshootingScreen(
                            onBack = { currentScreen = Screen.SETTINGS }
                        )
                    }
                    Screen.PREREQUISITES -> {
                        // Should not reach here in normal flow
                        currentScreen = Screen.HOME
                    }
                    Screen.ROOT_ACCESS -> {
                        com.ivarna.fluxlinux.ui.screens.RootAccessScreen(
                            onBack = { currentScreen = Screen.SETTINGS },
                            onEnableChroot = {
                                android.widget.Toast.makeText(this@MainActivity, "Chroot Mode Enabled", android.widget.Toast.LENGTH_SHORT).show()
                                currentScreen = Screen.SETTINGS
                            }
                        )
                    }
                }
            }
        }
    }
}
