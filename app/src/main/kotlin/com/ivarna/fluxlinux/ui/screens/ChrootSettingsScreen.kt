package com.ivarna.fluxlinux.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.chroot.ChrootDetection
import com.ivarna.fluxlinux.core.chroot.ChrootInfoStore
import com.ivarna.fluxlinux.core.chroot.ChrootSettingsModel
import com.ivarna.fluxlinux.core.root.ChrootPaths
import com.ivarna.fluxlinux.ui.components.GlassSettingCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Chroot Settings page — auto-detect, rootfs size, kill orphan processes.
 * Logic in [ChrootSettingsModel]; scripts match termux-lib nativecode-ai.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChrootSettingsScreen(
    onBack: () -> Unit,
    onNavigateToInstall: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sizeUi by remember {
        mutableStateOf(ChrootSettingsModel.loadCached(context).size)
    }
    var procUi by remember {
        mutableStateOf(ChrootSettingsModel.loadCached(context).proc)
    }
    var busy by remember { mutableStateOf(false) }
    var showKillConfirm by remember { mutableStateOf(false) }

    fun refreshAll(force: Boolean = true) {
        if (busy) return
        busy = true
        sizeUi = sizeUi.copy(measuring = true, hint = "Checking root · measuring rootfs…")
        procUi = procUi.copy(scanning = true, hint = "Waiting for size probe…")
        scope.launch {
            val snap = withContext(Dispatchers.IO) {
                ChrootSettingsModel.refreshPage(context, force = force)
            }
            sizeUi = snap.size
            procUi = snap.proc
            busy = false
        }
    }

    fun refreshSizeOnly() {
        if (busy) return
        busy = true
        sizeUi = sizeUi.copy(measuring = true, hint = "Checking root · measuring rootfs…")
        scope.launch {
            val s = withContext(Dispatchers.IO) {
                ChrootSettingsModel.refreshSize(context, forceClearSu = true)
            }
            sizeUi = s
            busy = false
        }
    }

    fun scanProcs() {
        if (busy) return
        busy = true
        procUi = procUi.copy(scanning = true, hint = "Scanning /proc for chroot root…")
        scope.launch {
            val p = withContext(Dispatchers.IO) {
                ChrootSettingsModel.refreshProcesses(context, forceClearSu = true)
            }
            procUi = p
            busy = false
        }
    }

    fun killAll() {
        if (busy) return
        busy = true
        procUi = procUi.copy(killing = true, scanning = true, hint = "Sending SIGKILL…")
        scope.launch {
            val p = withContext(Dispatchers.IO) {
                ChrootSettingsModel.killAllProcesses(context)
            }
            procUi = p
            busy = false
            Toast.makeText(
                context,
                p.statusLine ?: p.hint,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        refreshAll(force = false)
    }

    if (showKillConfirm) {
        AlertDialog(
            onDismissRequest = { showKillConfirm = false },
            title = { Text("Kill chroot processes?") },
            text = {
                Text(
                    "Sends SIGKILL to every process whose root is ${ChrootPaths.CHROOT_PATH}.\n\n" +
                        "Open chroot shells and guest daemons will die. Host Android processes " +
                        "are not targeted.\n\nRootfs and mounts stay." +
                        if (procUi.count > 0) "\n\nDetected: ${procUi.count} process(es)." else ""
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showKillConfirm = false
                        killAll()
                    }
                ) { Text("Kill all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showKillConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Chroot",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dark primary is near-black; use secondary (cream) + onSurface for readable text.
            val accent = MaterialTheme.colorScheme.secondary
            val body = MaterialTheme.colorScheme.onBackground
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            val label = MaterialTheme.colorScheme.onSurfaceVariant

            Text(
                "// Root-level Debian — outside app storage",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = muted
            )

            // ── Storage & auto-detect ─────────────────────────────────────
            GlassSettingCard {
                Column(Modifier.padding(18.dp).fillMaxWidth()) {
                    Text(
                        "Storage & manage",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0x33FF9800),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            "Rootfs is not removed when you uninstall the app. Free space here.",
                            fontSize = 12.sp,
                            color = Color(0xFFFFCC80)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    MetaRow("Status") {
                        StatusBadge(
                            text = when {
                                sizeUi.installed && sizeUi.markerOk -> "INSTALLED"
                                sizeUi.installed -> "PRESENT"
                                else -> "NOT INSTALLED"
                            },
                            ok = sizeUi.installed
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (sizeUi.rootOk || sizeUi.measuring) {
                        MetaRow("Root access") {
                            StatusBadge(
                                text = when {
                                    sizeUi.measuring -> "CHECKING"
                                    sizeUi.rootOk -> "GRANTED"
                                    else -> "DENIED"
                                },
                                ok = sizeUi.rootOk && !sizeUi.measuring
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // Size panel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "LINUX STORAGE",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = label,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { refreshSizeOnly() },
                                enabled = !busy
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh size",
                                    tint = accent
                                )
                            }
                        }
                        if (sizeUi.measuring || busy && sizeUi.bytes == null) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = accent,
                                trackColor = accent.copy(alpha = 0.2f)
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        val (v, u) = ChrootInfoStore.formatStorageBytes(sizeUi.bytes)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                v,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = accent,
                                modifier = Modifier.alpha(if (sizeUi.dimmedCache) 0.85f else 1f)
                            )
                            if (u.isNotEmpty()) {
                                Text(
                                    " $u",
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = body,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                        Text(
                            sizeUi.hint,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = muted
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            "HOST PATH",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = label
                        )
                        Text(
                            ChrootDetection.chrootPath(),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = body
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    if (!sizeUi.installed && onNavigateToInstall != null) {
                        Button(
                            onClick = { onNavigateToInstall() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Install chroot", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Processes / kill services ─────────────────────────────────
            GlassSettingCard {
                Column(Modifier.padding(18.dp).fillMaxWidth()) {
                    Text(
                        "Processes",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Orphans survive app close (unlike proot). Kill before uninstall if stuck.",
                        fontSize = 12.sp,
                        color = muted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "CHROOT PROCESSES",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = label,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { scanProcs() }, enabled = !busy) {
                                Text("Scan", color = accent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (procUi.scanning || procUi.killing) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.error,
                                trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                if (procUi.count < 0) "—" else procUi.count.toString(),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (procUi.count > 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    accent
                                }
                            )
                            Text(
                                " running",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                color = body,
                                modifier = Modifier.padding(bottom = 4.dp, start = 6.dp)
                            )
                        }
                        Text(
                            "root=${ChrootPaths.CHROOT_PATH}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = muted
                        )
                        Text(
                            procUi.hint,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = muted,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        if (procUi.processes.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "SAMPLE",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = label
                            )
                            procUi.processes.take(5).forEach { p ->
                                Text(
                                    "${p.pid}  ${p.comm}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = body
                                )
                            }
                            val more = procUi.processes.size - 5
                            if (more > 0) {
                                Text(
                                    "+$more more",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = muted
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    val killEnabled = !busy && procUi.rootOk && procUi.count > 0
                    Button(
                        onClick = { showKillConfirm = true },
                        enabled = killEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            disabledContentColor = muted
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (procUi.killing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Kill all chroot processes", fontWeight = FontWeight.Bold)
                    }
                    procUi.statusLine?.let {
                        Text(
                            it,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = muted,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Button(
                onClick = { refreshAll(force = true) },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    disabledContainerColor = accent.copy(alpha = 0.35f),
                    disabledContentColor = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.7f)
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Refresh all", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun MetaRow(label: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label.uppercase(),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
private fun StatusBadge(text: String, ok: Boolean) {
    // primary is near-black in dark theme — use secondary (cream) for OK badges
    val accent = MaterialTheme.colorScheme.secondary
    val fg = if (ok) accent else MaterialTheme.colorScheme.error
    Text(
        text,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = Modifier
            .background(
                fg.copy(alpha = 0.18f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
