package com.ivarna.fluxlinux.ui.screens

import android.content.Context
import android.graphics.Typeface
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ivarna.fluxlinux.core.terminal.FluxTerminalSessionManager
import com.ivarna.fluxlinux.core.terminal.LinuxCommandBuilder
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths
import com.ivarna.fluxlinux.ui.theme.FluxAccentMagenta
import com.termux.view.TerminalView

/**
 * In-app terminal (termux-flux-terminal + chroot-root-shell share this screen).
 * Hosts an AndroidView(TerminalView); session tabs + tool cards open new shells.
 *
 * Used both as a **bottom-nav page** ([onBack] null — like termux-lib Terminal tab)
 * and as a full-screen route (FGS notification / legacy [onBack] non-null).
 */
@Composable
fun TerminalScreen(
    onBack: (() -> Unit)? = null,
    /** When embedded under GlassScaffold bottom nav, skip status-bar inset (scaffold owns chrome). */
    embeddedInBottomNav: Boolean = false
) {
    val context = LocalContext.current
    val activeIndex by FluxTerminalSessionManager.activeIndex.collectAsState()
    FluxTerminalSessionManager.revision.collectAsState()
    val titles = FluxTerminalSessionManager.titles()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Floating glass bottom nav (~72 + 48 padding) — keep last lines visible.
            .then(if (embeddedInBottomNav) Modifier.padding(bottom = 120.dp) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (embeddedInBottomNav) Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    else Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Text(
                text = "Terminal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = if (onBack == null) 12.dp else 0.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                FluxTerminalSessionManager.openSessionAfterHost(
                    context, type = "shell", title = "Debian Shell (PRoot)", method = "proot"
                )
            }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Debian shell",
                    tint = FluxAccentMagenta
                )
            }
        }

        // Session tabs
        if (titles.isNotEmpty() || activeIndex >= 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val count = FluxTerminalSessionManager.sessionCount
                for (i in 0 until count) {
                    val title = titles.getOrElse(i) { "session" }
                    val selected = i == activeIndex
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) FluxAccentMagenta.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { FluxTerminalSessionManager.switchSession(i) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { FluxTerminalSessionManager.closeSession(context, i) },
                                modifier = Modifier.width(28.dp).height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(14.dp).height(14.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (activeIndex >= 0) {
            AndroidView(
                factory = { ctx ->
                    TerminalView(ctx, null).apply {
                        setTextSize(24)
                        try {
                            ctx.assets.open("fonts/font.ttf").use {
                                val tf = Typeface.createFromAsset(ctx.assets, "fonts/font.ttf")
                                setTypeface(tf)
                            }
                        } catch (_: Exception) {
                        }
                        setTerminalViewClient(object : com.termux.view.TerminalViewClient {
                            override fun onScale(scale: Float): Float = scale
                            override fun onSingleTapUp(e: android.view.MotionEvent) {
                                requestFocus()
                                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.showSoftInput(this@apply, InputMethodManager.SHOW_IMPLICIT)
                            }
                            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
                            override fun shouldEnforceCharBasedInput(): Boolean = false
                            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
                            override fun isTerminalViewSelected(): Boolean = true
                            override fun copyModeChanged(active: Boolean) {}
                            override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent, session: com.termux.terminal.TerminalSession): Boolean = false
                            override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean = false
                            override fun onLongPress(e: android.view.MotionEvent): Boolean = false
                            override fun readControlKey(): Boolean = false
                            override fun readAltKey(): Boolean = false
                            override fun readShiftKey(): Boolean = false
                            override fun readFnKey(): Boolean = false
                            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: com.termux.terminal.TerminalSession): Boolean = false
                            override fun onEmulatorSet() {}
                            override fun logError(tag: String, message: String) {}
                            override fun logWarn(tag: String, message: String) {}
                            override fun logInfo(tag: String, message: String) {}
                            override fun logDebug(tag: String, message: String) {}
                            override fun logVerbose(tag: String, message: String) {}
                            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
                            override fun logStackTrace(tag: String, e: Exception) {}
                        })
                    }
                },
                update = { view ->
                    FluxTerminalSessionManager.attachView(view)
                },
                modifier = Modifier.fillMaxSize()
            )
            DisposableEffect(Unit) {
                onDispose {
                    FluxTerminalSessionManager.detachView()
                }
            }
        } else {
            // Empty state: tool selector cards
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp).height(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No open sessions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Open a Debian shell (PRoot, no root) or start one from a Distro card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        FluxTerminalSessionManager.openSessionAfterHost(
                            context, type = "shell", title = "Debian Shell (PRoot)", method = "proot"
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FluxAccentMagenta)
                ) {
                    Text("Debian Shell (PRoot)")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        FluxTerminalSessionManager.openSessionAfterHost(
                            context, type = "shell-root", title = "Debian Shell Rooted (PRoot)", method = "proot"
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FluxAccentMagenta)
                ) {
                    Text("Debian Shell Rooted (PRoot)")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        FluxTerminalSessionManager.openSessionAfterHost(
                            context, type = "shell", title = "Root Shell (Chroot)", method = "chroot"
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("Root Shell (Chroot)")
                }
            }
        }
    }
}
