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
                // Check if onboarding has been completed (persisted state)
                val onboardingComplete = com.fluxlinux.app.core.utils.StateManager.isOnboardingComplete(this@MainActivity)
                
                // Show appropriate screen based on state
                when {
                    !onboardingComplete -> {
                        // Show Onboarding → Prerequisites flow
                        val showPrerequisites = remember { mutableStateOf(false) }
                        
                        if (!showPrerequisites.value) {
                            // Show Onboarding
                            com.fluxlinux.app.ui.screens.OnboardingScreen(
                                onGetStarted = {
                                    showPrerequisites.value = true
                                }
                            )
                        } else {
                            // Show Prerequisites
                            com.fluxlinux.app.ui.screens.PrerequisitesScreen(
                                onComplete = {
                                    // Mark onboarding as complete and recreate to show home
                                    com.fluxlinux.app.core.utils.StateManager.setOnboardingComplete(this@MainActivity, true)
                                    recreate()
                                }
                            )
                        }
                    }
                    else -> {
                        // Show Home Screen
                        GlassScaffold { hazeState ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                val permissionState = rememberPermissionState(
                                    permission = "com.termux.permission.RUN_COMMAND"
                                )
                                
                                com.fluxlinux.app.ui.screens.HomeScreen(
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
                    }
                }
            }
        }
    }
}
