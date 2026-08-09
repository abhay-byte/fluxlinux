package com.ivarna.fluxlinux.ui.install

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.ui.theme.FluxAccentMagenta

/**
 * Shared install progress UI for onboarding + Distros [InstallConfigScreen].
 */
@Composable
fun InstallProgressPanel(
    percent: Int,
    phaseLabel: String,
    detail: String,
    logText: String,
    failed: Boolean,
    errorMessage: String?,
    showLog: Boolean,
    onToggleLog: () -> Unit,
    onRetry: () -> Unit,
    onBack: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    isDone: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            if (failed) "Setup failed" else if (isDone) "Install complete" else "Setting up environment",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))

        Text(
            "$percent%",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = if (failed) MaterialTheme.colorScheme.error else FluxAccentMagenta
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (failed) MaterialTheme.colorScheme.error else FluxAccentMagenta,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(phaseLabel, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        if (detail.isNotBlank()) {
            Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (failed && errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onToggleLog) {
            Text(if (showLog) "Hide log" else "Show log")
        }

        if (showLog) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0A0A0C))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    logText.ifBlank { "Waiting for output…" },
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        when {
            failed -> {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Retry", fontWeight = FontWeight.Bold)
                }
                if (onBack != null) {
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Back")
                    }
                }
            }
            isDone && onDone != null -> {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Shared dark/light theme radio row for install wizards. */
@Composable
fun InstallThemePickRow(
    id: String,
    title: String,
    desc: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (selected) FluxAccentMagenta else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = FluxAccentMagenta)
        )
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
