package com.ivarna.fluxlinux.ui.screens

import android.content.Context
import android.graphics.Typeface
import android.system.Os
import android.view.KeyEvent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ivarna.fluxlinux.core.terminal.FluxTerminalSessionManager
import com.ivarna.fluxlinux.core.terminal.TerminalLauncher
import com.ivarna.fluxlinux.ui.theme.FluxAccentMagenta
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * In-app terminal with multi-session tabs + special-keys toolbar
 * (nativecode-ai interactive terminal parity).
 */
@Composable
fun TerminalScreen(
    onBack: (() -> Unit)? = null,
    embeddedInBottomNav: Boolean = false
) {
    val context = LocalContext.current
    val activeIndex by FluxTerminalSessionManager.activeIndex.collectAsState()
    FluxTerminalSessionManager.revision.collectAsState()
    val titles = FluxTerminalSessionManager.titles()

    // Atomic so TerminalViewClient can read from its thread without Compose races
    val ctrlLatch = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val altLatch = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val shiftLatch = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val fnLatch = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var ctrlUi by remember { mutableStateOf(false) }
    var altUi by remember { mutableStateOf(false) }
    var shiftUi by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(24) }
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    // Avoid SIGWINCH spam on every Compose recomposition
    val lastWinchKey = remember { intArrayOf(-1, -1, -1) } // cols, rows, pid

    fun setCtrl(v: Boolean) {
        ctrlLatch.set(v)
        ctrlUi = v
    }
    fun setAlt(v: Boolean) {
        altLatch.set(v)
        altUi = v
    }
    fun setShift(v: Boolean) {
        shiftLatch.set(v)
        shiftUi = v
    }
    fun postMain(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }

    fun writeKey(code: Int) {
        val session = FluxTerminalSessionManager.activeSession?.session ?: return
        // ESC sequences / control via write
        when (code) {
            KeyEvent.KEYCODE_ESCAPE -> session.write("\u001b")
            KeyEvent.KEYCODE_TAB -> session.write("\t")
            KeyEvent.KEYCODE_DEL -> session.write("\u007f")
            KeyEvent.KEYCODE_DPAD_UP -> session.write("\u001b[A")
            KeyEvent.KEYCODE_DPAD_DOWN -> session.write("\u001b[B")
            KeyEvent.KEYCODE_DPAD_RIGHT -> session.write("\u001b[C")
            KeyEvent.KEYCODE_DPAD_LEFT -> session.write("\u001b[D")
            KeyEvent.KEYCODE_MOVE_HOME -> session.write("\u001b[H")
            KeyEvent.KEYCODE_MOVE_END -> session.write("\u001b[F")
            KeyEvent.KEYCODE_PAGE_UP -> session.write("\u001b[5~")
            KeyEvent.KEYCODE_PAGE_DOWN -> session.write("\u001b[6~")
            else -> {
                val event = KeyEvent(KeyEvent.ACTION_DOWN, code)
                terminalViewRef?.dispatchKeyEvent(event)
                terminalViewRef?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        TerminalView(ctx, null).apply {
                            setTextSize(fontSize)
                            try {
                                val tf = Typeface.createFromAsset(ctx.assets, "fonts/font.ttf")
                                setTypeface(tf)
                            } catch (_: Exception) {
                            }
                            setTerminalViewClient(object : TerminalViewClient {
                                override fun onScale(scale: Float): Float {
                                    if (scale < 0.9f || scale > 1.1f) {
                                        val next = (fontSize * scale).toInt().coerceIn(10, 48)
                                        fontSize = next
                                        setTextSize(next)
                                        return 1.0f
                                    }
                                    return scale
                                }

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
                                override fun onKeyDown(keyCode: Int, event: KeyEvent, session: TerminalSession): Boolean = false
                                override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = false
                                override fun onLongPress(e: android.view.MotionEvent): Boolean = false
                                override fun readControlKey(): Boolean {
                                    val v = ctrlLatch.getAndSet(false)
                                    if (v) postMain { ctrlUi = false }
                                    return v
                                }
                                override fun readAltKey(): Boolean {
                                    val v = altLatch.getAndSet(false)
                                    if (v) postMain { altUi = false }
                                    return v
                                }
                                override fun readShiftKey(): Boolean {
                                    val v = shiftLatch.getAndSet(false)
                                    if (v) postMain { shiftUi = false }
                                    return v
                                }
                                override fun readFnKey(): Boolean = fnLatch.getAndSet(false)
                                override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
                                override fun onEmulatorSet() {}
                                override fun logError(tag: String, message: String) {}
                                override fun logWarn(tag: String, message: String) {}
                                override fun logInfo(tag: String, message: String) {}
                                override fun logDebug(tag: String, message: String) {}
                                override fun logVerbose(tag: String, message: String) {}
                                override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
                                override fun logStackTrace(tag: String, e: Exception) {}
                            })
                            terminalViewRef = this
                        }
                    },
                    update = { view ->
                        terminalViewRef = view
                        FluxTerminalSessionManager.attachView(view)
                        // Resize + SIGWINCH only when view size / session pid change (not every recompose)
                        try {
                            view.updateSize()
                            val w = view.width
                            val h = view.height
                            val pid = FluxTerminalSessionManager.activeSession?.session?.getPid() ?: -1
                            if (pid > 0 &&
                                (w != lastWinchKey[0] || h != lastWinchKey[1] || pid != lastWinchKey[2])
                            ) {
                                lastWinchKey[0] = w
                                lastWinchKey[1] = h
                                lastWinchKey[2] = pid
                                runCatching { Os.kill(pid, 28 /* SIGWINCH */) }
                            }
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            DisposableEffect(Unit) {
                onDispose {
                    FluxTerminalSessionManager.detachView()
                    terminalViewRef = null
                }
            }

            // Special keys toolbar (nativecode-style)
            ExtraKeysBar(
                ctrl = ctrlUi,
                alt = altUi,
                shift = shiftUi,
                onCtrl = { setCtrl(!ctrlUi) },
                onAlt = { setAlt(!altUi) },
                onShift = { setShift(!shiftUi) },
                onKey = { writeKey(it) }
            )
        } else {
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
                    text = "Open a Debian shell (PRoot) or Rooted shell (Chroot).",
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
                val chrootReady = TerminalLauncher.isDebianChrootInstalled()
                Button(
                    onClick = {
                        FluxTerminalSessionManager.openSessionAfterHost(
                            context, type = "shell", title = "Debian (Chroot)", method = "chroot"
                        )
                    },
                    enabled = chrootReady,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(if (chrootReady) "Debian Shell (Chroot)" else "Chroot not installed")
                }
            }
        }
    }
}

@Composable
private fun ExtraKeysBar(
    ctrl: Boolean,
    alt: Boolean,
    shift: Boolean,
    onCtrl: () -> Unit,
    onAlt: () -> Unit,
    onShift: () -> Unit,
    onKey: (Int) -> Unit
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            .horizontalScroll(scroll)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModKey("Ctrl", ctrl, onCtrl)
        ModKey("Alt", alt, onAlt)
        ModKey("Shift", shift, onShift)
        KeyChip("Esc") { onKey(KeyEvent.KEYCODE_ESCAPE) }
        KeyChip("Tab") { onKey(KeyEvent.KEYCODE_TAB) }
        KeyChip("←") { onKey(KeyEvent.KEYCODE_DPAD_LEFT) }
        KeyChip("↑") { onKey(KeyEvent.KEYCODE_DPAD_UP) }
        KeyChip("↓") { onKey(KeyEvent.KEYCODE_DPAD_DOWN) }
        KeyChip("→") { onKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
        KeyChip("Home") { onKey(KeyEvent.KEYCODE_MOVE_HOME) }
        KeyChip("End") { onKey(KeyEvent.KEYCODE_MOVE_END) }
        KeyChip("PgUp") { onKey(KeyEvent.KEYCODE_PAGE_UP) }
        KeyChip("PgDn") { onKey(KeyEvent.KEYCODE_PAGE_DOWN) }
    }
}

@Composable
private fun ModKey(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (active) FluxAccentMagenta else MaterialTheme.colorScheme.surface,
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun KeyChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
