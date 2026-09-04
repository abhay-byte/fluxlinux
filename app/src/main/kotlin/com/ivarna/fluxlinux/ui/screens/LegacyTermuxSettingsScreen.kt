package com.ivarna.fluxlinux.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.ivarna.fluxlinux.core.data.DistroRepository
import com.ivarna.fluxlinux.core.data.ScriptManager
import com.ivarna.fluxlinux.core.desktop.DesktopLauncher
import com.ivarna.fluxlinux.core.legacy.LegacyTermuxBridge
import com.ivarna.fluxlinux.core.legacy.LegacyTermuxStore
import com.ivarna.fluxlinux.core.utils.StateManager
import com.ivarna.fluxlinux.ui.components.GlassSettingCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val TAG = "LegacyTermux"
private const val FIX_COMMAND =
    "mkdir -p ~/.termux && echo \"allow-external-apps = true\" >> ~/.termux/termux.properties && termux-reload-settings"

enum class LegacyPageState {
    IDLE,
    SCANNING,
    UNINSTALLING,
    TIMED_OUT
}

enum class LegacyTimeoutType {
    NONE,
    LIST,
    UNINSTALL
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LegacyTermuxSettingsScreen(
    onBack: () -> Unit,
    permissionState: PermissionState
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scriptManager = remember { ScriptManager(context) }

    var pageState by remember { mutableStateOf(LegacyPageState.IDLE) }
    var activeJobId by remember { mutableLongStateOf(0L) }
    var lastTimeoutType by remember { mutableStateOf(LegacyTimeoutType.NONE) }
    var uninstallTargetId by remember { mutableStateOf<String?>(null) }
    var confirmUninstallRow by remember { mutableStateOf<LegacyTermuxStore.Row?>(null) }
    var showPermissionHelpDialog by remember { mutableStateOf(false) }

    // Lifecycle observer to re-read package state on resume
    var lifecycleKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lifecycleKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val termuxInstalled = remember(lifecycleKey) { LegacyTermuxBridge.isTermuxInstalled(context) }
    val termuxVersion = remember(lifecycleKey) { LegacyTermuxBridge.termuxVersionName(context) }
    val termuxVersionOk = remember(lifecycleKey) { LegacyTermuxBridge.isTermuxVersionOk(context) }
    val termuxX11Installed = remember(lifecycleKey) { LegacyTermuxBridge.isTermuxX11Installed(context) }
    val runCommandGranted = remember(lifecycleKey, permissionState.status) {
        LegacyTermuxBridge.hasRunCommandPermission(context) || permissionState.status.isGranted
    }

    val refreshTrigger by StateManager.refreshTrigger.collectAsState()
    val scan = remember(refreshTrigger, lifecycleKey) { LegacyTermuxStore.load(context) }
    val isPingOk = remember(refreshTrigger, lifecycleKey) { LegacyTermuxStore.isPingOk(context) }

    val embeddedDesktopActive = remember(refreshTrigger, lifecycleKey) {
        isEmbeddedDesktopActive(context)
    }

    val isBusy = pageState == LegacyPageState.SCANNING || pageState == LegacyPageState.UNINSTALLING
    val canInteract = termuxInstalled && termuxVersionOk && runCommandGranted && !isBusy

    fun triggerScan() {
        if (!termuxInstalled || !termuxVersionOk || !runCommandGranted || isBusy) return

        try {
            val script = scriptManager.getScriptContent("legacy-termux/list_proot.sh")
            val b64 = Base64.encodeToString(script.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val spec = LegacyTermuxBridge.buildListSpec(b64)
            val intent = LegacyTermuxBridge.toIntent(spec)
            val launched = LegacyTermuxBridge.startSafely(context, intent)
            if (launched) {
                val jobId = System.currentTimeMillis()
                activeJobId = jobId
                pageState = LegacyPageState.SCANNING
                lastTimeoutType = LegacyTimeoutType.NONE
                coroutineScope.launch {
                    delay(15_000L)
                    if (pageState == LegacyPageState.SCANNING && activeJobId == jobId) {
                        pageState = LegacyPageState.TIMED_OUT
                        lastTimeoutType = LegacyTimeoutType.LIST
                    }
                }
            } else {
                Toast.makeText(context, "Failed to start Termux scan", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scan trigger failed", e)
            Toast.makeText(context, "Scan error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-scan on enter / resume whenever gates are satisfied
    LaunchedEffect(lifecycleKey, termuxInstalled, termuxVersionOk, runCommandGranted) {
        if (termuxInstalled && termuxVersionOk && runCommandGranted && pageState == LegacyPageState.IDLE) {
            triggerScan()
        }
    }

    // Track handled scan updates so initial composition does not clear SCANNING
    var lastHandledScanMs by remember { mutableLongStateOf(scan.scannedAtMs) }
    var lastHandledError by remember { mutableStateOf(scan.error) }
    var lastHandledRows by remember { mutableStateOf(scan.rows) }
    var lastHandledActionMs by remember { mutableLongStateOf(scan.lastActionMs) }

    LaunchedEffect(scan.scannedAtMs, scan.error, scan.rows, scan.lastActionMs) {
        val scanChanged = scan.scannedAtMs != lastHandledScanMs || scan.error != lastHandledError
        val rowsChanged = scan.rows != lastHandledRows
        val actionChanged = scan.lastActionMs != lastHandledActionMs

        if (scanChanged || rowsChanged || actionChanged) {
            lastHandledScanMs = scan.scannedAtMs
            lastHandledError = scan.error
            lastHandledRows = scan.rows
            lastHandledActionMs = scan.lastActionMs

            // Any applied callback response (list/uninstall success or error) returns page to IDLE
            if (pageState == LegacyPageState.SCANNING || pageState == LegacyPageState.TIMED_OUT || pageState == LegacyPageState.UNINSTALLING) {
                pageState = LegacyPageState.IDLE
                uninstallTargetId = null
                lastTimeoutType = LegacyTimeoutType.NONE
                activeJobId = 0L
            }
        }
    }

    fun triggerOpenTerminal(row: LegacyTermuxStore.Row) {
        if (!termuxInstalled || !termuxVersionOk || !runCommandGranted || isBusy) return
        if (row.id == "termux") return

        try {
            val script = scriptManager.getScriptContent("legacy-termux/login_proot.sh")
            val b64 = Base64.encodeToString(script.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val spec = LegacyTermuxBridge.buildLoginSpec(b64, row.id) ?: return
            val intent = LegacyTermuxBridge.toIntent(spec)
            val launched = LegacyTermuxBridge.startSafely(context, intent)
            if (launched) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.termux")
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            } else {
                Toast.makeText(context, "Failed to launch Termux session", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            Toast.makeText(context, "Login error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerStartDisplay(row: LegacyTermuxStore.Row) {
        if (!termuxInstalled || !termuxVersionOk || !runCommandGranted || isBusy) return
        if (row.id == "termux") return

        if (embeddedDesktopActive) {
            Toast.makeText(context, "Stop the FluxLinux desktop first.", Toast.LENGTH_LONG).show()
            return
        }
        if (!termuxX11Installed) {
            Toast.makeText(context, "Termux:X11 is not installed", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val script = scriptManager.getScriptContent("legacy-termux/start_display.sh")
            val b64 = Base64.encodeToString(script.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val spec = LegacyTermuxBridge.buildStartDisplaySpec(b64, row.id) ?: return
            val intent = LegacyTermuxBridge.toIntent(spec)
            val launched = LegacyTermuxBridge.startSafely(context, intent)
            if (!launched) {
                Toast.makeText(context, "Failed to start leftover display", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Start display failed", e)
            Toast.makeText(context, "Start display error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerStopDisplay(row: LegacyTermuxStore.Row) {
        if (!termuxInstalled || !termuxVersionOk || !runCommandGranted || isBusy) return
        if (row.id == "termux") return

        try {
            val script = scriptManager.getScriptContent("legacy-termux/stop_display.sh")
            val b64 = Base64.encodeToString(script.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val spec = LegacyTermuxBridge.buildStopDisplaySpec(b64, row.id) ?: return
            val intent = LegacyTermuxBridge.toIntent(spec)
            val launched = LegacyTermuxBridge.startSafely(context, intent)
            if (!launched) {
                Toast.makeText(context, "Failed to stop leftover display", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stop display failed", e)
            Toast.makeText(context, "Stop display error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerUninstall(row: LegacyTermuxStore.Row) {
        if (!termuxInstalled || !termuxVersionOk || !runCommandGranted || isBusy) return
        if (row.id == "termux") return

        val targetId = row.id
        try {
            val script = scriptManager.getScriptContent("legacy-termux/uninstall_proot.sh")
            val b64 = Base64.encodeToString(script.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val spec = LegacyTermuxBridge.buildUninstallSpec(b64, targetId) ?: return
            val intent = LegacyTermuxBridge.toIntent(spec)
            val launched = LegacyTermuxBridge.startSafely(context, intent)
            if (launched) {
                val jobId = System.currentTimeMillis()
                activeJobId = jobId
                pageState = LegacyPageState.UNINSTALLING
                uninstallTargetId = targetId
                lastTimeoutType = LegacyTimeoutType.NONE
                coroutineScope.launch {
                    delay(20_000L)
                    if (pageState == LegacyPageState.UNINSTALLING && activeJobId == jobId) {
                        pageState = LegacyPageState.TIMED_OUT
                        lastTimeoutType = LegacyTimeoutType.UNINSTALL
                        Toast.makeText(context, "Termux did not confirm removal; tap Scan.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(context, "Failed to request Termux uninstall", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall trigger failed", e)
            Toast.makeText(context, "Uninstall error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyAndOpenTermux() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Termux Fix", FIX_COMMAND))
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.termux")
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            Toast.makeText(context, "Command copied! Paste in Termux", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Termux not found!", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerPing() {
        if (!termuxInstalled || !termuxVersionOk || !runCommandGranted || isBusy) return

        try {
            val spec = LegacyTermuxBridge.buildPingSpec()
            val intent = LegacyTermuxBridge.toIntent(spec)
            val launched = LegacyTermuxBridge.startSafely(context, intent)
            if (launched) {
                Toast.makeText(context, "Ping sent to Termux", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Ping failed to start", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ping failed", e)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Legacy Termux",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { triggerScan() },
                        enabled = !isBusy && termuxInstalled && termuxVersionOk && runCommandGranted
                    ) {
                        if (pageState == LegacyPageState.SCANNING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Scan",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Warning
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    "Leftovers live in com.termux — not app storage",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "These are containers inside the Termux app, not Settings → PRoot.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Prerequisites Card
            GlassSettingCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Prerequisites",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    // Row 1: Termux App + Version
                    PrereqRow(
                        title = "Termux App",
                        statusText = when {
                            !termuxInstalled -> "Not Installed"
                            !termuxVersionOk -> "v${termuxVersion ?: "unknown"} (< v${LegacyTermuxBridge.MIN_TERMUX_VERSION})"
                            else -> "v${termuxVersion} (Ready) ✓"
                        },
                        isOk = termuxInstalled && termuxVersionOk,
                        ctaText = when {
                            !termuxInstalled -> "Download"
                            !termuxVersionOk -> "Update"
                            else -> null
                        },
                        onCta = {
                            openUrl(context, "https://f-droid.org/packages/com.termux/")
                        }
                    )
                    if (termuxInstalled && !termuxVersionOk) {
                        Text(
                            "Play Store version will not work. Install v0.118.3 or newer from the official releases.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            lineHeight = 14.sp
                        )
                    }

                    // Row 2: Termux:X11
                    PrereqRow(
                        title = "Termux:X11",
                        statusText = if (termuxX11Installed) "Installed (Ready) ✓" else "Not Installed",
                        isOk = termuxX11Installed,
                        ctaText = if (!termuxX11Installed) "Get X11" else null,
                        onCta = {
                            openUrl(context, "https://github.com/termux/termux-x11/releases")
                        }
                    )

                    // Row 3: RUN_COMMAND
                    PrereqRow(
                        title = "RUN_COMMAND",
                        statusText = if (runCommandGranted) "Granted ✓" else if (!termuxInstalled) "Termux Not Installed" else "Denied",
                        isOk = runCommandGranted,
                        ctaText = if (!runCommandGranted) "Grant" else null,
                        onCta = {
                            if (!termuxInstalled) {
                                Toast.makeText(context, "Install Termux first to grant RUN_COMMAND permission", Toast.LENGTH_LONG).show()
                                openUrl(context, "https://f-droid.org/packages/com.termux/")
                            } else {
                                try {
                                    permissionState.launchPermissionRequest()
                                } catch (e: Exception) {
                                    Log.e(TAG, "launchPermissionRequest failed", e)
                                }
                                showPermissionHelpDialog = true
                            }
                        }
                    )

                    // Row 4: allow-external-apps
                    val isConfigured = isPingOk || (scan.scannedAtMs > 0 && scan.error == null)
                    PrereqRow(
                        title = "allow-external-apps",
                        statusText = if (isConfigured) "Configured ✓" else "Unknown / Needs Fix",
                        isOk = isConfigured,
                        ctaText = null,
                        onCta = null
                    )

                    // Fix box if not configured or if timed out
                    if (!isConfigured || pageState == LegacyPageState.TIMED_OUT) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF141414))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Fix: allow-external-apps = true",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                FIX_COMMAND,
                                color = Color(0xFF50FA7B),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { copyAndOpenTermux() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ),
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Copy & Open Termux", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { triggerPing() },
                                    enabled = canInteract,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)),
                                    modifier = Modifier.height(40.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Ping", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Timed out banner
            if (pageState == LegacyPageState.TIMED_OUT) {
                GlassSettingCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (lastTimeoutType == LegacyTimeoutType.UNINSTALL) {
                                "Termux did not confirm removal of '${uninstallTargetId ?: "container"}'; tap Scan to check if it was removed."
                            } else {
                                "Termux did not respond. Enable allow-external-apps = true and grant RUN_COMMAND."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Status / Error message
            if (scan.error != null) {
                Text(
                    scan.error,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Discovered Leftovers Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Leftover Containers",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (scan.scannedAtMs > 0) {
                    val ageMinutes = (System.currentTimeMillis() - scan.scannedAtMs) / 60000
                    Text(
                        if (ageMinutes <= 0) "Just scanned" else "Scanned ${ageMinutes}m ago",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Rows list or Empty states
            if (!termuxInstalled) {
                GlassSettingCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            "Termux Not Installed",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "The Termux app is not installed. Leftover FluxLinux ≤ v1.8.0 containers lived there.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else if (scan.rows.isEmpty() && scan.scannedAtMs > 0 && scan.error == null) {
                GlassSettingCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF50FA7B),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            "No Leftovers Found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "No leftover PRoot containers found in Termux.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                val isScanFreshForUninstall = (System.currentTimeMillis() - scan.scannedAtMs) < 30 * 60 * 1000L

                scan.rows.forEach { row ->
                    LeftoverRowCard(
                        row = row,
                        isBusy = isBusy,
                        isUninstallActive = pageState == LegacyPageState.UNINSTALLING && uninstallTargetId == row.id,
                        termuxX11Installed = termuxX11Installed,
                        embeddedDesktopActive = embeddedDesktopActive,
                        canInteract = canInteract,
                        canUninstall = isScanFreshForUninstall && canInteract,
                        onLoginTerminal = { triggerOpenTerminal(row) },
                        onStartDisplay = { triggerStartDisplay(row) },
                        onStopDisplay = { triggerStopDisplay(row) },
                        onUninstall = {
                            if (!isBusy) {
                                confirmUninstallRow = row
                            }
                        }
                    )
                }
            }

            // Bottom Scan Action Button
            if (termuxInstalled && termuxVersionOk && runCommandGranted) {
                Button(
                    onClick = { triggerScan() },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (pageState == LegacyPageState.SCANNING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onSecondary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Scanning Termux…", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Scan leftovers", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // Uninstall Confirmation Dialog
    val targetToConfirm = confirmUninstallRow
    if (targetToConfirm != null) {
        val pathText = if (!targetToConfirm.hostPath.isNullOrBlank()) {
            "\n\nPath: ${targetToConfirm.hostPath}"
        } else ""

        AlertDialog(
            onDismissRequest = {
                if (!isBusy) confirmUninstallRow = null
            },
            title = {
                Text(
                    if (targetToConfirm.id == "debian") "Delete Legacy Debian Container?" else "Delete Termux Container?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                val promptText = if (targetToConfirm.id == "debian") {
                    "Delete the legacy FluxLinux ≤ v1.8 Termux container 'debian'?"
                } else {
                    "Delete the Termux proot-distro container '${targetToConfirm.id}' (not installed by FluxLinux ≤ v1.8)?"
                }
                Text(
                    "$promptText$pathText\n\nThis removes the container from Termux and does not touch FluxLinux app-storage (Settings → PRoot).",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRemove = targetToConfirm
                        confirmUninstallRow = null
                        if (!isBusy) {
                            triggerUninstall(toRemove)
                        }
                    },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Uninstall", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmUninstallRow = null },
                    enabled = !isBusy
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.secondary)
                }
            }
        )
    }

    // Permission Help Dialog (Android 11+ / MIUI custom dangerous permission guidance)
    if (showPermissionHelpDialog && !runCommandGranted) {
        AlertDialog(
            onDismissRequest = { showPermissionHelpDialog = false },
            title = {
                Text(
                    "Grant RUN_COMMAND",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "On modern Android, custom permissions like RUN_COMMAND must be enabled manually in App Info → Permissions → Additional Permissions / All Permissions.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Or grant via ADB:\npm grant ${context.packageName} com.termux.permission.RUN_COMMAND",
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionHelpDialog = false
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to open app settings", e)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text("Open App Info", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionHelpDialog = false }) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun PrereqRow(
    title: String,
    statusText: String,
    isOk: Boolean,
    ctaText: String?,
    onCta: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                statusText,
                fontSize = 12.sp,
                color = if (isOk) Color(0xFF50FA7B) else MaterialTheme.colorScheme.error
            )
        }
        if (ctaText != null && onCta != null) {
            Button(
                onClick = onCta,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(ctaText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LeftoverRowCard(
    row: LegacyTermuxStore.Row,
    isBusy: Boolean,
    isUninstallActive: Boolean,
    termuxX11Installed: Boolean,
    embeddedDesktopActive: Boolean,
    canInteract: Boolean,
    canUninstall: Boolean,
    onLoginTerminal: () -> Unit,
    onStartDisplay: () -> Unit,
    onStopDisplay: () -> Unit,
    onUninstall: () -> Unit
) {
    val isDebianLeftover = row.id == "debian"
    val isNativeTermux = row.id == "termux"

    GlassSettingCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            row.id,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            formatBytes(row.bytes),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isDebianLeftover) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when {
                            isDebianLeftover -> "v1.8 LEFTOVER"
                            isNativeTermux -> "NATIVE"
                            else -> "OTHER TERMUX CONTAINER"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDebianLeftover) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Path line
            if (!row.hostPath.isNullOrBlank()) {
                Text(
                    row.hostPath,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            // Start disclaimer / conflict banner
            if (embeddedDesktopActive) {
                Text(
                    "Stop the FluxLinux desktop first. Leftover Termux:X11 and embedded XFCE should not run together (Pulse TCP 127.0.0.1:4713).",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (isNativeTermux) {
                Text(
                    "Termux native container management is not supported.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (isDebianLeftover) {
                Text(
                    "Start: XFCE in Termux:X11 (v1.8 protocol). KDE leftovers are not supported.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Termux container (not installed by FluxLinux ≤ v1.8). Start: XFCE in Termux:X11 if installed inside container.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onLoginTerminal,
                        enabled = canInteract && !isNativeTermux,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Open terminal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onStartDisplay,
                        enabled = canInteract && termuxX11Installed && !embeddedDesktopActive && !isNativeTermux,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Start display", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onStopDisplay,
                        enabled = canInteract && !isNativeTermux,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)),
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Stop display", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onUninstall,
                        enabled = canUninstall && !isNativeTermux,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isUninstallActive) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.error,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Uninstall", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun isEmbeddedDesktopActive(ctx: Context): Boolean {
    return DesktopLauncher.isSessionActive() ||
        DistroRepository.supportedDistros.any {
            StateManager.isGuiRunning(ctx, it.id)
        }
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return "Size unknown"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return String.format(Locale.US, "%.1f GB", gb)
    val mb = bytes / (1024.0 * 1024.0)
    if (mb >= 1.0) return String.format(Locale.US, "%.1f MB", mb)
    val kb = bytes / 1024.0
    return String.format(Locale.US, "%.1f KB", kb)
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Log.e(TAG, "Error opening URL: $url", e)
    }
}
