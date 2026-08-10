package com.ivarna.fluxlinux.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.R
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.DistroRepository
import com.ivarna.fluxlinux.core.install.OnboardingInstallRunner
import com.ivarna.fluxlinux.ui.components.CompactDistroCard
import com.ivarna.fluxlinux.ui.install.InstallProgressPanel
import com.ivarna.fluxlinux.ui.install.InstallThemePickRow
import com.ivarna.fluxlinux.ui.theme.FluxAccentMagenta

private enum class OnboardStep { Welcome, DistroPick, Options, Running, Done }

/**
 * Full first-run onboarding: welcome → distro catalog → base install options →
 * progress → complete. Installs rootfs + XFCE + customization only (no modules).
 */
@Composable
fun OnboardingFlowScreen(
    onFinished: () -> Unit,
    onOpenTerminal: () -> Unit = {},
    onStartDesktop: (distroId: String) -> Unit = {}
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(OnboardStep.Welcome) }
    var selectedDistroId by remember { mutableStateOf("debian") }
    var theme by remember { mutableStateOf("dark") }

    var percent by remember { mutableIntStateOf(0) }
    var phaseLabel by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var logText by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showLog by remember { mutableStateOf(true) }

    val runner = remember { OnboardingInstallRunner(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { runner.cancel() }
    }

    fun startInstall() {
        step = OnboardStep.Running
        percent = 0
        phaseLabel = "Starting…"
        detail = ""
        logText = ""
        failed = false
        errorMessage = null
        runner.start(selectedDistroId, theme) { p ->
            percent = p.overallPercent
            phaseLabel = p.phaseLabel
            detail = p.detail
            p.logLine?.let { line ->
                logText = (logText + line + "\n").takeLast(12_000)
            }
            if (p.finished) {
                if (p.failed) {
                    failed = true
                    errorMessage = p.errorMessage
                } else {
                    step = OnboardStep.Done
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        when (step) {
            OnboardStep.Welcome -> WelcomePage(onNext = { step = OnboardStep.DistroPick })
            OnboardStep.DistroPick -> DistroPickPage(
                selectedId = selectedDistroId,
                onSelect = { selectedDistroId = it },
                onBack = { step = OnboardStep.Welcome },
                onNext = { step = OnboardStep.Options }
            )
            OnboardStep.Options -> OptionsPage(
                distroId = selectedDistroId,
                theme = theme,
                onTheme = { theme = it },
                onBack = { step = OnboardStep.DistroPick },
                onInstall = { startInstall() }
            )
            OnboardStep.Running -> InstallProgressPanel(
                percent = percent,
                phaseLabel = phaseLabel,
                detail = detail,
                logText = logText,
                failed = failed,
                errorMessage = errorMessage,
                showLog = showLog,
                onToggleLog = { showLog = !showLog },
                onRetry = { startInstall() },
                onBack = {
                    runner.cancel()
                    step = OnboardStep.Options
                }
            )
            OnboardStep.Done -> DonePage(
                distroId = selectedDistroId,
                onHome = onFinished,
                // Single path each — MainActivity handlers mark complete + navigate
                onTerminal = onOpenTerminal,
                onDesktop = { onStartDesktop(selectedDistroId) }
            )
        }
    }
}

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            "FluxLinux",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Full Linux desktop on Android — no Termux app required",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(32.dp))
        Image(
            painter = painterResource(id = R.drawable.onboarding_bg_1),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(16.dp))
        FeatureLine("🐧", "Debian PRoot or rooted Chroot")
        Spacer(Modifier.height(10.dp))
        FeatureLine("🖥️", "XFCE desktop + Flux themes")
        Spacer(Modifier.height(10.dp))
        FeatureLine("⚡", "Embedded terminal & X11 display")
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Get Started", onNext)
    }
}

@Composable
private fun FeatureLine(icon: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
    }
}

@Composable
private fun DistroPickPage(
    selectedId: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val installable = DistroRepository.supportedDistros.filter { !it.comingSoon }
    val comingSoon = DistroRepository.supportedDistros.filter { it.comingSoon }
        .sortedBy { it.name }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            "Choose a distribution",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Installable now, or coming soon",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Available",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            installable.forEach { distro ->
                SelectableDistroRow(
                    distro = distro,
                    selected = selectedId == distro.id,
                    onClick = { onSelect(distro.id) }
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Coming soon",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            comingSoon.forEach { distro ->
                CompactDistroCard(distro = distro)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Back", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SelectableDistroRow(
    distro: Distro,
    selected: Boolean,
    onClick: () -> Unit
) {
    val border = if (selected) FluxAccentMagenta else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .background(
                if (selected) FluxAccentMagenta.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = FluxAccentMagenta)
        )
        if (distro.iconRes != null) {
            Image(
                painter = painterResource(id = distro.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                distro.name,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
            Text(
                distro.description,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
            val methodLabel = when {
                distro.chrootSupported -> "Requires root · Chroot"
                else -> "No root · PRoot"
            }
            Text(
                methodLabel,
                color = FluxAccentMagenta,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun OptionsPage(
    distroId: String,
    theme: String,
    onTheme: (String) -> Unit,
    onBack: () -> Unit,
    onInstall: () -> Unit
) {
    val distro = DistroRepository.supportedDistros.find { it.id == distroId }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Base desktop install",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${distro?.name ?: distroId}: Debian rootfs, XFCE desktop, and Flux themes. Feature modules can be added later in Distro Settings.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))

        Text("Appearance", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))
        InstallThemePickRow(
            id = "dark",
            title = "Dark",
            desc = "Sleek default",
            selected = theme == "dark",
            onSelect = { onTheme("dark") }
        )
        Spacer(Modifier.height(8.dp))
        InstallThemePickRow(
            id = "light",
            title = "Light",
            desc = "Bright desktop",
            selected = theme == "light",
            onSelect = { onTheme("light") }
        )

        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(14.dp)
        ) {
            Column {
                Text("Includes", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text("• Debian 13 rootfs", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("• XFCE4 desktop", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("• Flux theme, wallpapers, fonts", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("• Embedded terminal + X11 display", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }

        if (distroId.contains("chroot")) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Rooted path: grant superuser to FluxLinux. BusyBox may be required on some devices.",
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Dark primary is near-black — TextButton default contentColor is unreadable.
            TextButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Back", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onInstall,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Install", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DonePage(
    distroId: String,
    onHome: () -> Unit,
    onTerminal: () -> Unit,
    onDesktop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✅", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "You're ready",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Debian base desktop is installed. Open a shell or start XFCE.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(32.dp))
        PrimaryButton("Go to Home", onHome)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onTerminal,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FluxAccentMagenta),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Open Terminal", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onDesktop,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Start Desktop", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        // Cream secondary on dark = high contrast (primary is near-black in dark theme)
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}
