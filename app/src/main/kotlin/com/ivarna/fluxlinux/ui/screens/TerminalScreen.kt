package com.ivarna.fluxlinux.ui.screens

import android.content.Context
import android.graphics.Typeface
import android.system.Os
import android.system.OsConstants
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ivarna.fluxlinux.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ivarna.fluxlinux.core.terminal.FluxTerminalSessionManager
import com.ivarna.fluxlinux.core.terminal.FluxTerminalSessionManager.SessionOpenResult
import com.ivarna.fluxlinux.core.utils.TerminalPreferences
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
 * Empty state = compact proot/chroot session rows (`TerminalToolSelector`); active state =
 * interactive `TerminalView` (focus + IME + clipboard) with a compact ExtraKeys
 * toolbar. Plan: docs/plans/terminal-grid-extrakeys-interactive.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onBack: (() -> Unit)? = null,
    embeddedInBottomNav: Boolean = false
) {
    val context = LocalContext.current
    val activeIndex by FluxTerminalSessionManager.activeIndex.collectAsState()
    val sessionRevision by FluxTerminalSessionManager.revision.collectAsState()
    val titles = remember(sessionRevision) { FluxTerminalSessionManager.titles() }
    val sessions = remember(sessionRevision) { FluxTerminalSessionManager.sessions() }

    // Modifier state shared between TerminalViewClient (read side) and ExtraKeys (UI).
    val modState = remember { TerminalModifierState() }
    var fontSize by remember {
        mutableIntStateOf(TerminalPreferences.getFontSize(context))
    }
    var showExtraKeys by remember {
        mutableStateOf(TerminalPreferences.isExtraKeysEnabled(context))
    }
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    var showNewSessionSheet by remember { mutableStateOf(false) }
    // Avoid SIGWINCH spam on every Compose recomposition — only when the emulator's
    // cols/rows or the attached pid actually change (R2: pinch zoom changes cols/rows
    // without a view-size change, so pixel width/height is NOT a valid guard key).
    val lastWinchKey = remember { intArrayOf(-1, -1, -1) } // cols, rows, pid

    // Reload prefs when returning from Settings (Terminal / X11 detail pages).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fontSize = TerminalPreferences.getFontSize(context)
                showExtraKeys = TerminalPreferences.isExtraKeysEnabled(context)
                terminalViewRef?.setTextSize(fontSize)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun openCard(type: String, title: String, method: String, distroId: String? = null) {
        if (method == "host") {
            // R4: gate on host ready — prepare when missing, toast "Host not ready".
            FluxTerminalSessionManager.openHostShellAfterReady(context, title) { result ->
                when (result) {
                    SessionOpenResult.OPENED -> {}
                    SessionOpenResult.MAX_TABS -> toast("Tab limit reached (${FluxTerminalSessionManager.MAX_TABS})")
                    SessionOpenResult.HOST_NOT_READY -> toast("Host not ready — check Settings")
                    else -> toast("Host shell failed to open")
                }
            }
            return
        }
        FluxTerminalSessionManager.openSessionAfterHost(
            context,
            type = type,
            title = title,
            method = method,
            distroId = distroId,
            onResult = { result ->
                when (result) {
                    SessionOpenResult.OPENED -> {}
                    SessionOpenResult.MAX_TABS -> toast("Tab limit reached (${FluxTerminalSessionManager.MAX_TABS})")
                    SessionOpenResult.HOST_PREPARE_FAILED -> toast("Host bootstrap not ready — check Settings")
                    SessionOpenResult.OPEN_FAILED ->
                        toast(if (method == "chroot") "Chroot session failed to open" else "Session failed to open")
                    SessionOpenResult.HOST_NOT_READY -> toast("Host not ready — check Settings")
                }
            }
        )
    }

    /** Nativecode `forceTerminalResize` parity — post to view; SIGWINCH on cols/rows change. */
    fun forceTerminalResize(tv: TerminalView) {
        tv.post {
            try {
                if (tv.width <= 0 || tv.height <= 0) return@post
                tv.updateSize()
                tv.onScreenUpdated()
                val session = tv.currentSession ?: return@post
                val emulator = session.emulator ?: return@post
                if (!session.isRunning) return@post
                val cols = emulator.mColumns
                val rows = emulator.mRows
                val pid = session.pid
                if (pid > 0 &&
                    (cols != lastWinchKey[0] || rows != lastWinchKey[1] || pid != lastWinchKey[2])
                ) {
                    lastWinchKey[0] = cols
                    lastWinchKey[1] = rows
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
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(
                WindowInsets.ime.union(WindowInsets.navigationBars)
                    .only(WindowInsetsSides.Bottom)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = "Terminal",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = if (onBack == null) 8.dp else 8.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    val next = !showExtraKeys
                    showExtraKeys = next
                    TerminalPreferences.setExtraKeysEnabled(context, next)
                    terminalViewRef?.let { forceTerminalResize(it) }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (showExtraKeys) Icons.Default.Keyboard else Icons.Default.KeyboardHide,
                    contentDescription = if (showExtraKeys) "Hide extra keys" else "Show extra keys",
                    tint = if (showExtraKeys) FluxAccentMagenta
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { showNewSessionSheet = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New session",
                    tint = FluxAccentMagenta,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (sessions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sessions.forEachIndexed { i, managed ->
                    val selected = i == activeIndex
                    val icon = managed.iconRes ?: R.drawable.ic_terminal
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) FluxAccentMagenta.copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    BorderStroke(
                                        if (selected) 1.5.dp else 1.dp,
                                        if (selected) FluxAccentMagenta
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { FluxTerminalSessionManager.switchSession(i) },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = icon),
                                contentDescription = managed.title.ifBlank { titles.getOrElse(i) { "session" } },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        CircleShape
                                    )
                                    .clickable {
                                        FluxTerminalSessionManager.closeSession(context, i)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
            }
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
                                        val next = (fontSize * scale).toInt().coerceIn(
                                            TerminalPreferences.FONT_MIN,
                                            TerminalPreferences.FONT_MAX
                                        )
                                        fontSize = next
                                        TerminalPreferences.setFontSize(ctx, next)
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
                        // Keep text size in sync with global prefs (settings page / pinch).
                        view.setTextSize(fontSize)
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
            if (showExtraKeys) {
                TerminalExtraKeys(
                    modState = modState,
                    terminalView = { terminalViewRef },
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
                onOpen = { type, title, method, distroId ->
                    showNewSessionSheet = false
                    openCard(type, title, method, distroId)
                },
                modifier = Modifier.heightIn(max = 460.dp)
            )
        }
    }
}
