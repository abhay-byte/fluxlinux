package com.ivarna.fluxlinux.ui.install

import android.content.Context
import com.ivarna.fluxlinux.core.install.OnboardingInstallRunner
import com.ivarna.fluxlinux.core.install.ZenithbluePayloadProviders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object InstallFlowHelper {

    /**
     * Common install starter for InstallConfigScreen and OnboardingFlowScreen.
     * On Zenithblue with a Play-supported distro, requests and materializes the module first via
     * [ZenithbluePayloadProviders.ensurePresent], showing "Downloading distro…".
     * On Ivarna or non-Play distros, immediately delegates to [runner.start].
     */
    fun startInstall(
        context: Context,
        scope: CoroutineScope,
        distroId: String,
        theme: String,
        runner: OnboardingInstallRunner,
        playFeatureDelivery: com.ivarna.fluxlinux.core.install.PlayFeatureDelivery? = null,
        onPhaseChange: (String) -> Unit,
        onDetailChange: (String) -> Unit,
        onPercentChange: (Int) -> Unit,
        onLogLine: (String) -> Unit,
        onFailed: (String) -> Unit,
        onSuccess: () -> Unit,
    ) {
        val isZenithblue = ZenithbluePayloadProviders.isZenithblue(context)
        val isPlaySupported = isZenithblue && ZenithbluePayloadProviders.supports(context, distroId)

        if (!isPlaySupported) {
            runner.start(distroId, theme) { progress ->
                handleRunnerProgress(
                    progress = progress,
                    onPhaseChange = onPhaseChange,
                    onDetailChange = onDetailChange,
                    onPercentChange = onPercentChange,
                    onLogLine = onLogLine,
                    onFailed = onFailed,
                    onSuccess = onSuccess
                )
            }
            return
        }

        // Zenithblue Play path: request + materialize via PlayFeatureDelivery first
        scope.launch {
            onPhaseChange("Downloading distro…")
            onPercentChange(0)
            onDetailChange("")

            val pfd = playFeatureDelivery ?: com.ivarna.fluxlinux.core.install.PlayFeatureDelivery.create(context)
            val ok = ZenithbluePayloadProviders.ensurePresent(
                ctx = context,
                distroId = distroId,
                playFeatureDelivery = pfd,
                onProgress = { p ->
                    onPhaseChange(p.phase)
                    val pct = (p.fraction * 100).toInt().coerceIn(0, 100)
                    onPercentChange(pct)
                    if (p.totalBytesToDownload > 0) {
                        val dlMiB = p.bytesDownloaded / 1_048_576
                        val totalMiB = p.totalBytesToDownload / 1_048_576
                        onDetailChange("Downloaded $dlMiB / $totalMiB MiB")
                    } else {
                        onDetailChange("")
                    }
                }
            )

            if (!ok) {
                onPhaseChange("Install failed")
                onFailed("Distro download failed. Retry.")
                return@launch
            }

            // Materialized & verified: proceed to runner (runner will verify file and proceed)
            runner.start(distroId, theme) { progress ->
                handleRunnerProgress(
                    progress = progress,
                    onPhaseChange = onPhaseChange,
                    onDetailChange = onDetailChange,
                    onPercentChange = onPercentChange,
                    onLogLine = onLogLine,
                    onFailed = onFailed,
                    onSuccess = onSuccess
                )
            }
        }
    }

    private fun handleRunnerProgress(
        progress: OnboardingInstallRunner.Progress,
        onPhaseChange: (String) -> Unit,
        onDetailChange: (String) -> Unit,
        onPercentChange: (Int) -> Unit,
        onLogLine: (String) -> Unit,
        onFailed: (String) -> Unit,
        onSuccess: () -> Unit,
    ) {
        onPercentChange(progress.overallPercent)
        onPhaseChange(progress.phaseLabel)
        onDetailChange(progress.detail)
        progress.logLine?.let { line: String ->
            onLogLine(line)
        }
        if (progress.failed) {
            onFailed(progress.errorMessage ?: progress.detail)
        }
        if (progress.finished && !progress.failed) {
            onSuccess()
        }
    }
}
