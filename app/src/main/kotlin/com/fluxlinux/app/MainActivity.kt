package com.fluxlinux.app


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.fluxlinux.app.ui.components.GlassScaffold
import com.fluxlinux.app.ui.theme.FluxLinuxTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FluxLinuxTheme {
                // Navigation State
                val onboardingComplete = remember {
                    mutableStateOf(
                        com.fluxlinux.app.core.utils.StateManager.isOnboardingComplete(this@MainActivity)
                    )
                }
                
                val prerequisitesComplete = remember {
                    mutableStateOf(
                        com.fluxlinux.app.core.utils.StateManager.isTermuxInitialized(this@MainActivity) ||
                        com.fluxlinux.app.core.utils.StateManager.isOnboardingComplete(this@MainActivity)
                    )
                }
                
                // Show appropriate screen based on state
                when {
                    !onboardingComplete.value -> {
                        // Show Onboarding
                        com.fluxlinux.app.ui.screens.OnboardingScreen(
                            onGetStarted = {
                                onboardingComplete.value = true
                            }
                        )
                    }
                    onboardingComplete.value && !prerequisitesComplete.value -> {
                        // Show Prerequisites
                        com.fluxlinux.app.ui.screens.PrerequisitesScreen(
                            onComplete = {
                                com.fluxlinux.app.core.utils.StateManager.setOnboardingComplete(this@MainActivity, true)
                                prerequisitesComplete.value = true
                            }
                        )
                    }
                    else -> {
                        // Show Home Screen
                        GlassScaffold {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val permissionState = rememberPermissionState(
                                    permission = "com.termux.permission.RUN_COMMAND"
                                )
                                
                                com.fluxlinux.app.ui.screens.HomeScreen(
                                    permissionState = permissionState,
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
                    }
                }
            }
        }
    }
}
