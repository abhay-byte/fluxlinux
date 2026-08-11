package com.ivarna.fluxlinux.ui.terminal

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.root.RootShell
import com.ivarna.fluxlinux.core.terminal.TerminalShellCardUi
import com.ivarna.fluxlinux.core.terminal.TerminalShellCatalog
import com.ivarna.fluxlinux.core.terminal.TerminalShellSection
import com.ivarna.fluxlinux.ui.theme.FluxAccentMagenta

/**
 * Nativecode-style tool selector grid for the Terminal tab empty state.
 *
 * 2-column card grid with section headers (DEBIAN SHELL // PROOT · // CHROOT ·
 * HOST), 64dp full-color icons (no tint), labels + short descriptions. Cards that
 * cannot open (guest not installed / chroot-root without su) stay visible but are
 * grayed out with a reason — plan §5.1 / goal 6.
 *
 * @param onOpen (type, title, method) — opens the session (host prepare handled by caller)
 */
@Composable
fun TerminalToolSelector(
    onOpen: (type: String, title: String, method: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Async su probe (KernelSU / Magisk) — gates the chroot-root card only.
    var rootAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        RootShell.probeRootAvailable { ok -> rootAvailable = ok }
    }

    val sections = remember(context, rootAvailable) {
        TerminalShellCatalog.sections(
            context,
            TerminalShellCatalog.availability(context, rootAvailable)
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        sections.forEach { section ->
            item(key = "header-${section.title}-${section.subtitle}", span = { GridItemSpan(maxLineSpan) }) {
                TerminalSectionHeader(section)
            }
            items(section.cards, key = { "card-${it.def.method}-${it.def.type}" }) { card ->
                TerminalToolCard(
                    card = card,
                    onClick = {
                        if (card.enabled) onOpen(card.def.type, card.def.label, card.def.method)
                    }
                )
            }
        }
    }
}

@Composable
private fun TerminalSectionHeader(section: TerminalShellSection, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = FluxAccentMagenta,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "// ${section.subtitle}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TerminalToolCard(
    card: TerminalShellCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val def = card.def
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (card.enabled) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (card.enabled) FluxAccentMagenta.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = card.enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Full-color distro icon — never tinted (nativecode cli PNG parity).
            Image(
                painter = painterResource(id = def.iconRes),
                contentDescription = def.label,
                modifier = Modifier
                    .size(64.dp)
                    .alpha(if (card.enabled) 1f else 0.45f)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = def.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (card.enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(if (card.enabled) 1f else 0.55f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = def.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!card.enabled && card.disabledReason != null) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = card.disabledReason,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
