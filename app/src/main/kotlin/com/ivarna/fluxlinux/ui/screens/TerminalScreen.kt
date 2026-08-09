package com.ivarna.fluxlinux.ui.screens

import android.content.Context
import android.graphics.Typeface
import android.system.Os
import android.system.OsConstants
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.viewinterop.AndroidView
import com.ivarna.fluxlinux.core.terminal.FluxTerminalSessionManager
import com.ivarna.fluxlinux.ui.terminal.TerminalExtraKeys
import com.ivarna.fluxlinux.ui.terminal.TerminalModifierState
import com.ivarna.fluxlinux.ui.terminal.TerminalToolSelector
import com.ivarna.fluxlinux.ui.theme.FluxAccentMagenta
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * In-app terminal with multi-session tabs + nativecode-style tool selector grid
 * + full ExtraKeys toolbar (injectKey / ModifierState lock).
 *
 * Empty state = 2-column card grid (`TerminalToolSelector`); active state =
 * interactive `TerminalView` (focus + IME + clipboard) with the ExtraKeys toolbar
 * above the bottom nav. Plan: docs/plans/terminal-grid-extrakeys-interactive.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onBack: (() -> Unit)? = null,
    embeddedInBottomNav: Boolean = false
) {
    val context = LocalContext.current
    val activeIndex by FluxTerminalSessionManager.activeIndex.collectAsState()
    FluxTerminalSessionManager.revision.collectAsState()
    val titles = FluxTerminalSessionManager.titles()

    // Modifier state shared between TerminalViewClient (read side) and ExtraKeys (UI).
    val modState = remember { TerminalModifierState() }
    var fontSize by remember { mutableStateOf(24) }
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    var showNewSessionSheet by remember { mutableStateOf(false) }
    // Avoid SIGWINCH spam on every Compose recomposition — only on real cols/rows/pid change.
    val lastWinchKey = remember { intArrayOf(-1, -1, -1) } // cols, rows, pid

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun openCard(type: String, title: String, method: String) {
        if (method == "host") {
            val ok = FluxTerminalSessionManager.openHostShell(context, title)
            if (!ok) toast("Host shell failed to open")
            return
        }
        FluxTerminalSessionManager.openSessionAfterHost(
            context, type = type, title = title, method = method,
            onDone = { ok ->
                if (!ok) {
                    toast(if (method == "chroot") "Chroot session failed" else "Host bootstrap failed")
                }
            }
        )
    }

    /** Nativecode `forceTerminalResize` parity — post to view; SIGWINCH only when running. */
    fun forceTerminalResize(tv: TerminalView) {
        tv.post {
            try {
                if (tv.width <= 0 || tv.height <= 0) return@post
                tv.updateSize()
                tv.onScreenUpdated()
                val session = tv.currentSession ?: return@post
                if (!session.isRunning) return@post
                val pid = session.pid
                if (pid > 0 &&
                    (tv.width != lastWinchKey[0] || tv.height != lastWinchKey[1] || pid != lastWinchKey[2])
                ) {
                    lastWinchKey[0] = tv.width
                    lastWinchKey[1] = tv.height
                    lastWinchKey[2] = pid
                    runCatching { Os.kill(pid, OsConstants.SIGWINCH) }
                }
            } catch (_: Exception) {
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
            IconButton(onClick = { showNewSessionSheet = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New session",
                    tint = FluxAccentMagenta
                )
            }
        }

        if (titles.isNotEmpty()) {
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

                                // T3: tap → focus + IME (SHOW_FORCED fallback for OEMs).
                                override fun onSingleTapUp(e: android.view.MotionEvent) {
                                    requestFocus()
                                    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                    if (!imm.showSoftInput(this@apply, InputMethodManager.SHOW_IMPLICIT)) {
                                        imm.showSoftInput(this@apply, InputMethodManager.SHOW_FORCED)
                                    }
                                }

                                override fun shouldBackButtonBeMappedToEscape(): Boolean = false
                                override fun shouldEnforceCharBasedInput(): Boolean = false
                                override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
                                override fun isTerminalViewSelected(): Boolean = true
                                override fun copyModeChanged(active: Boolean) {}
                                // Let the view handle keys — never swallow (nativecode parity).
                                override fun onKeyDown(keyCode: Int, event: KeyEvent, session: com.termux.terminal.TerminalSession): Boolean = false
                                override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = false
                                override fun onLongPress(e: android.view.MotionEvent): Boolean = false
                                // Modifiers read from shared lockable state (ModifierState parity).
                                override fun readControlKey(): Boolean = modState.readCtrl(true)
                                override fun readAltKey(): Boolean = modState.readAlt(true)
                                override fun readShiftKey(): Boolean = modState.readShift(true)
                                override fun readFnKey(): Boolean = modState.readFn(true)
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
                            terminalViewRef = this
                        }
                    },
                    update = { view ->
                        terminalViewRef = view
                        // T3: focus parity — re-attach + focus on every recompose/switch so a
                        // freshly opened or switched session never shows a blank/dead view.
                        view.isFocusable = true
                        view.isFocusableInTouchMode = true
                        FluxTerminalSessionManager.attachView(view)
                        view.requestFocus()
                        // T5: resize + SIGWINCH only on real size/pid change.
                        forceTerminalResize(view)
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

            // ExtraKeys toolbar: nativecode inject path (TerminalView.onKeyDown / inputCodePoint).
            TerminalExtraKeys(
                modState = modState,
                terminalView = { terminalViewRef },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // Empty state: nativecode-style 2-column card grid with original distro icons.
            TerminalToolSelector(onOpen = ::openCard)
        }
    }

    // "+" — same selector as a bottom sheet so users can add sessions from anywhere.
    if (showNewSessionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNewSessionSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            TerminalToolSelector(
                onOpen = { type, title, method ->
                    showNewSessionSheet = false
                    openCard(type, title, method)
                },
                modifier = Modifier.heightIn(max = 460.dp)
            )
        }
    }
}
