package com.ivarna.fluxlinux.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivarna.fluxlinux.core.data.DistroRepository
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.ScriptManager
import com.ivarna.fluxlinux.core.utils.InstallationQueueManager
import com.ivarna.fluxlinux.core.utils.StateManager
import com.ivarna.fluxlinux.ui.components.DistroCard
import com.ivarna.fluxlinux.ui.components.MethodTab
import com.ivarna.fluxlinux.ui.components.MethodTabs
import com.ivarna.fluxlinux.ui.theme.FluxAccentMagenta
import com.ivarna.fluxlinux.ui.theme.GlassWhiteMedium
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import dev.chrisbanes.haze.HazeState

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DistroScreen(
    permissionState: PermissionState,
    hazeState: HazeState,
    onStartService: (android.content.Intent) -> Unit,
    onStartActivity: (android.content.Intent) -> Unit,
    onNavigateToInstall: (com.ivarna.fluxlinux.core.data.Distro) -> Unit
) {
    val context = LocalContext.current
    
    val installState by InstallationQueueManager.installState.collectAsState()
    val stateRefresh by StateManager.refreshTrigger.collectAsState()
    
    // Refresh mechanism to check install status
    val refreshKey = remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshKey.value++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // Distro List — installed state = filesystem truth (plan P4-T13); stale
    // prefs without a rootfs on disk keep the card available for Install.
    val installedDistroIds = remember(refreshKey.value, stateRefresh) {
        DistroRepository.supportedDistros
            .filter { com.ivarna.fluxlinux.core.terminal.TerminalLauncher.isDistroInstalledOnFs(context, it.id) }
            .map { it.id }
            .toSet()
    }
    
    val availableDistros = DistroRepository.supportedDistros.filter { 
        !installedDistroIds.contains(it.id)
    }

    var methodTab by remember { mutableStateOf(MethodTab.PROOT) }
    val visibleDistros = DistroRepository.sortForDistroPage(
        availableDistros.filter { distro ->
            if (methodTab == MethodTab.CHROOT) distro.chrootSupported else distro.prootSupported
        }
    )
    val prootCount = availableDistros.count { it.prootSupported }
    val chrootCount = availableDistros.count { it.chrootSupported }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 128.dp)
    ) {
        item(key = "title") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Available Distros",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        item(key = "tabs") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MethodTabs(
                    selected = methodTab,
                    onSelected = { methodTab = it },
                    prootCount = prootCount,
                    chrootCount = chrootCount
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        if (visibleDistros.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (availableDistros.isEmpty()) {
                            "All available distros are installed!"
                        } else if (methodTab == MethodTab.CHROOT) {
                            "No chroot distros left to install"
                        } else {
                            "No PRoot distros left to install"
                        },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            items(
                items = visibleDistros,
                key = { it.id },
                contentType = { if (it.comingSoon) "soon" else "full" }
            ) { distro ->
                if (distro.comingSoon) {
                    com.ivarna.fluxlinux.ui.components.CompactDistroCard(
                        distro = distro
                    )
                } else {
                    com.ivarna.fluxlinux.ui.components.DistroCard(
                        distro = distro,
                        isInstalled = false,
                        isGlobalInstalling = installState.isInstalling,
                        isCurrentlyInstalling = installState.isInstalling && installState.currentDistroId == distro.id,
                        onInstall = {
                            if (installState.isInstalling && installState.currentDistroId != distro.id) {
                                Toast.makeText(context, "Installation already in progress for another distro", Toast.LENGTH_LONG).show()
                            } else {
                                onNavigateToInstall(distro)
                            }
                        },
                        onUninstall = {},
                        onNavigateToSettings = {},
                        onNavigateToStart = {}
                    )
                }
            }
        }
    }
}
