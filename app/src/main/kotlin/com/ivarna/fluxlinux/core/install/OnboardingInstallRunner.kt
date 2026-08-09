package com.ivarna.fluxlinux.core.install

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ivarna.fluxlinux.core.root.RootShell
import com.ivarna.fluxlinux.core.terminal.HostCommandBuilder
import com.ivarna.fluxlinux.core.terminal.ShellCommandRunner
import com.ivarna.fluxlinux.core.terminal.TerminalLauncher
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths
import com.ivarna.fluxlinux.core.utils.StateManager
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs the simplified onboarding / base install with phase progress + live log.
 * Proot: prepareHost → flux_install (family) → customization via proot-distro.
 * Chroot: root check → prepareHost → setup_debian13_chroot → family → customization in chroot.
 *
 * Cancel: generation token + destroy active process; never mutates StateManager after cancel.
 * Overlapping [start] cancels the previous run first.
 */
class OnboardingInstallRunner(private val ctx: Context) {

    data class Progress(
        val phaseId: String,
        val phaseLabel: String,
        val phaseIndex: Int,
        val phaseCount: Int,
        val overallPercent: Int,
        val detail: String,
        val logLine: String? = null,
        val failed: Boolean = false,
        val finished: Boolean = false,
        val errorMessage: String? = null
    )

    private val appCtx = ctx.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cancelled = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val activeProcess = AtomicReference<Process?>(null)
    private val busy = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
        activeProcess.getAndSet(null)?.destroyForcibly()
    }

    fun isBusy(): Boolean = busy.get()

    /**
     * Start (or restart) install. If a previous job is still running, it is cancelled
     * and the new job is queued on the single-thread executor.
     */
    fun start(
        distroId: String,
        theme: String = "dark",
        onProgress: (Progress) -> Unit
    ) {
        // Invalidate any in-flight job before resetting cancel flag
        val gen = generation.incrementAndGet()
        cancelled.set(true)
        activeProcess.getAndSet(null)?.destroyForcibly()
        cancelled.set(false)

        val method = BaseDesktopInstallPlan.methodFor(distroId)
        val phases = BaseDesktopInstallPlan.phasesFor(method)
        busy.set(true)
        executor.execute {
            try {
                if (isStale(gen)) {
                    postCancelled(onProgress, phases)
                    return@execute
                }
                if (method == "chroot") {
                    runChroot(distroId, theme, phases, onProgress, gen)
                } else {
                    runProot(distroId, theme, phases, onProgress, gen)
                }
            } catch (e: Exception) {
                if (!isStale(gen)) {
                    Log.e(TAG, "install failed", e)
                    postFail(onProgress, phases, e.message ?: "Unknown error")
                } else {
                    postCancelled(onProgress, phases)
                }
            } finally {
                if (generation.get() == gen) {
                    busy.set(false)
                }
            }
        }
    }

    private fun isStale(gen: Int): Boolean =
        cancelled.get() || generation.get() != gen

    private fun abortIfCancelled(
        gen: Int,
        phases: List<BaseDesktopInstallPlan.Phase>,
        onProgress: (Progress) -> Unit
    ): Boolean {
        if (!isStale(gen)) return false
        postCancelled(onProgress, phases)
        return true
    }

    private fun runProot(
        distroId: String,
        theme: String,
        phases: List<BaseDesktopInstallPlan.Phase>,
        onProgress: (Progress) -> Unit,
        gen: Int
    ) {
        enter(phases, 0, onProgress, "Extracting bootstrap + deploying scripts…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        val hostOk = TerminalLauncher.prepareHostBlocking(appCtx, forceHostSetup = false) { done, total, phase ->
            if (isStale(gen)) return@prepareHostBlocking
            val frac = if (total > 0) done.toFloat() / total else 0f
            updateFraction(phases, 0, frac, onProgress, phase)
        }
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (!hostOk) {
            postFail(onProgress, phases, "Host bootstrap failed")
            return
        }
        completePhase(phases, 0, onProgress, "Host ready")

        enter(phases, 1, onProgress, "Installing Debian via proot-distro…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        val bash = TermuxHostPaths.libBash(appCtx).absolutePath
        val installScript = TermuxHostPaths.hostScript(appCtx, "flux_install.sh").absolutePath
        val setupB64 = BaseDesktopInstallPlan.familySetupB64(appCtx, theme)
        val env = HostCommandBuilder.envMap(appCtx, forceHostSetup = false, includeTerm = false)
        val (exitInstall, outInstall) = ShellCommandRunner.runCaptureExit(
            appCtx,
            arrayOf(bash, installScript, "debian", setupB64),
            env,
            processHolder = activeProcess
        )
        if (abortIfCancelled(gen, phases, onProgress)) return
        outInstall.lineSequence().forEach { line ->
            if (line.isNotBlank()) log(phases, 1, onProgress, line)
        }
        if (exitInstall != 0) {
            postFail(onProgress, phases, "Debian install failed (exit $exitInstall)")
            return
        }
        if (!TerminalLauncher.isDebianProotInstalled(appCtx)) {
            postFail(onProgress, phases, "Debian rootfs missing after install")
            return
        }
        if (!isProotXfceInstalled(appCtx)) {
            postFail(
                onProgress, phases,
                "XFCE not found after install (startxfce4 missing). Retry setup."
            )
            return
        }
        completePhase(phases, 1, onProgress, "Debian + XFCE installed")

        enter(phases, 2, onProgress, "Themes, wallpapers, fonts…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        val customOk = runProotGuestScript(
            phases, 2, onProgress, gen,
            scriptAssetPath = BaseDesktopInstallPlan.CUSTOMIZATION_SCRIPT,
            envPrefix = "FLUX_THEME=$theme"
        )
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (!customOk) {
            log(phases, 2, onProgress, "Customization failed or partial — you can retry from Distro Settings")
        }
        completePhase(phases, 2, onProgress, "Customization done")

        if (abortIfCancelled(gen, phases, onProgress)) return
        StateManager.setDistroInstalled(appCtx, distroId, true)
        StateManager.setComponentInstalled(appCtx, distroId, "xfce4_desktop", true)
        if (customOk) {
            StateManager.setComponentInstalled(appCtx, distroId, "customization", true)
        }
        StateManager.triggerRefresh()
        postSuccess(onProgress, phases)
    }

    private fun runChroot(
        distroId: String,
        theme: String,
        phases: List<BaseDesktopInstallPlan.Phase>,
        onProgress: (Progress) -> Unit,
        gen: Int
    ) {
        enter(phases, 0, onProgress, "Probing KernelSU / Magisk…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (!RootShell.isRootAvailable()) {
            postFail(
                onProgress, phases,
                "Root not available. Grant superuser to FluxLinux, install BusyBox if needed, then retry."
            )
            return
        }
        completePhase(phases, 0, onProgress, "Root OK")

        enter(phases, 1, onProgress, "Extracting bootstrap + deploying scripts…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        val hostOk = TerminalLauncher.prepareHostBlocking(appCtx, forceHostSetup = false) { done, total, phase ->
            if (isStale(gen)) return@prepareHostBlocking
            val frac = if (total > 0) done.toFloat() / total else 0f
            updateFraction(phases, 1, frac, onProgress, phase)
        }
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (!hostOk) {
            postFail(onProgress, phases, "Host bootstrap failed")
            return
        }
        completePhase(phases, 1, onProgress, "Host ready")

        enter(phases, 2, onProgress, "Extracting chroot rootfs (may take several minutes)…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        val staged = RootShell.stageAsset(appCtx, "scripts/chroot/setup_debian13_chroot.sh")
            ?: TermuxHostPaths.hostScript(appCtx, "setup_debian13_chroot.sh").absolutePath
        val envHome = TermuxHostPaths.HOME
        val rootCmd =
            "export FLUX_ROOTFS_PATH='$envHome/debian_13_rootfs.tar.xz'; " +
                "export TERMUX_APP__PACKAGE_NAME='${TermuxHostPaths.PACKAGE}'; " +
                "export TERMUX__HOME='$envHome'; " +
                "sh '$staged'"
        val rootResult = RootShell.captureResult(rootCmd, timeoutMs = 0L)
        if (abortIfCancelled(gen, phases, onProgress)) return
        rootResult.stdout.lineSequence().forEach { line ->
            if (line.isNotBlank()) log(phases, 2, onProgress, line)
        }
        val installed = TerminalLauncher.isDebianChrootInstalled()
        if (rootResult.exitCode != 0) {
            postFail(onProgress, phases, "Chroot install failed (exit ${rootResult.exitCode})")
            return
        }
        if (!installed) {
            postFail(onProgress, phases, "Chroot rootfs missing after install")
            return
        }
        completePhase(phases, 2, onProgress, "Chroot rootfs ready")

        enter(phases, 3, onProgress, "Installing XFCE packages…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        val familyPayload = BaseDesktopInstallPlan.familySetupPayload(appCtx, theme)
        val familyExit = runChrootGuestBlocking(familyPayload, user = "root", phases, 3, onProgress)
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (familyExit != 0) {
            postFail(onProgress, phases, "XFCE setup failed (exit $familyExit)")
            return
        }
        if (!isChrootXfceInstalled()) {
            postFail(
                onProgress, phases,
                "XFCE not found after family setup (startxfce4 missing). Retry."
            )
            return
        }
        completePhase(phases, 3, onProgress, "XFCE installed")

        enter(phases, 4, onProgress, "Themes, wallpapers, fonts…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        val customPayload = BaseDesktopInstallPlan.customizationPayload(appCtx, theme)
        val customExit = runChrootGuestBlocking(customPayload, user = "root", phases, 4, onProgress)
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (customExit != 0) {
            log(phases, 4, onProgress, "Customization failed or partial (exit $customExit)")
        }
        completePhase(phases, 4, onProgress, "Customization done")

        if (abortIfCancelled(gen, phases, onProgress)) return
        StateManager.setDistroInstalled(appCtx, distroId, true)
        StateManager.setComponentInstalled(appCtx, distroId, "xfce4_desktop", true)
        if (customExit == 0) {
            StateManager.setComponentInstalled(appCtx, distroId, "customization", true)
        }
        StateManager.triggerRefresh()
        postSuccess(onProgress, phases)
    }

    private fun runProotGuestScript(
        phases: List<BaseDesktopInstallPlan.Phase>,
        phaseIndex: Int,
        onProgress: (Progress) -> Unit,
        gen: Int,
        scriptAssetPath: String,
        envPrefix: String
    ): Boolean {
        return try {
            val tmp = File(TermuxHostPaths.TMPDIR)
            if (!tmp.exists()) tmp.mkdirs()
            val name = File(scriptAssetPath).name
            val dest = File(tmp, name)
            appCtx.assets.open("scripts/$scriptAssetPath").use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            dest.setExecutable(true)
            val bash = TermuxHostPaths.libBash(appCtx).absolutePath
            val cmd = arrayOf(
                bash, "-c",
                "exec python ${TermuxHostPaths.PROOT_DISTRO} login debian --shared-tmp -- " +
                    "env $envPrefix bash /tmp/$name"
            )
            val env = HostCommandBuilder.envMap(appCtx, includeTerm = false)
            val (exit, out) = ShellCommandRunner.runCaptureExit(
                appCtx, cmd, env, processHolder = activeProcess
            )
            if (isStale(gen)) return false
            out.lineSequence().forEach { line ->
                if (line.isNotBlank()) log(phases, phaseIndex, onProgress, line)
            }
            exit == 0
        } catch (e: Exception) {
            if (!isStale(gen)) {
                log(phases, phaseIndex, onProgress, "Guest script error: ${e.message}")
            }
            false
        }
    }

    private fun runChrootGuestBlocking(
        scriptBody: String,
        user: String,
        phases: List<BaseDesktopInstallPlan.Phase>,
        phaseIndex: Int,
        onProgress: (Progress) -> Unit
    ): Int {
        val b64 = android.util.Base64.encodeToString(
            scriptBody.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        val guest =
            "echo '$b64' | base64 -d > /tmp/flux_onboard.sh && chmod +x /tmp/flux_onboard.sh && " +
                "bash /tmp/flux_onboard.sh; RC=\$?; rm -f /tmp/flux_onboard.sh; exit \$RC"
        val result = RootShell.captureInChroot(guest, user = user, context = appCtx, timeoutMs = 0L)
        result.stdout.lineSequence().forEach { line ->
            if (line.isNotBlank()) log(phases, phaseIndex, onProgress, line)
        }
        return result.exitCode
    }

    // ── progress helpers ────────────────────────────────────────────────────

    private fun enter(
        phases: List<BaseDesktopInstallPlan.Phase>,
        index: Int,
        onProgress: (Progress) -> Unit,
        detail: String
    ) {
        val p = phases[index]
        post(
            onProgress,
            Progress(
                phaseId = p.id,
                phaseLabel = p.label,
                phaseIndex = index,
                phaseCount = phases.size,
                overallPercent = weightedPercent(phases, index, 0f),
                detail = detail
            )
        )
    }

    private fun completePhase(
        phases: List<BaseDesktopInstallPlan.Phase>,
        index: Int,
        onProgress: (Progress) -> Unit,
        detail: String
    ) {
        val p = phases[index]
        post(
            onProgress,
            Progress(
                phaseId = p.id,
                phaseLabel = p.label,
                phaseIndex = index,
                phaseCount = phases.size,
                overallPercent = weightedPercent(phases, index, 1f),
                detail = detail
            )
        )
    }

    private fun updateFraction(
        phases: List<BaseDesktopInstallPlan.Phase>,
        index: Int,
        frac: Float,
        onProgress: (Progress) -> Unit,
        detail: String
    ) {
        val p = phases[index]
        post(
            onProgress,
            Progress(
                phaseId = p.id,
                phaseLabel = p.label,
                phaseIndex = index,
                phaseCount = phases.size,
                overallPercent = weightedPercent(phases, index, frac.coerceIn(0f, 1f)),
                detail = detail
            )
        )
    }

    private fun log(
        phases: List<BaseDesktopInstallPlan.Phase>,
        index: Int,
        onProgress: (Progress) -> Unit,
        line: String
    ) {
        val p = phases.getOrNull(index) ?: return
        post(
            onProgress,
            Progress(
                phaseId = p.id,
                phaseLabel = p.label,
                phaseIndex = index,
                phaseCount = phases.size,
                overallPercent = weightedPercent(phases, index, 0.5f),
                detail = p.label,
                logLine = line
            )
        )
    }

    private fun postSuccess(
        onProgress: (Progress) -> Unit,
        phases: List<BaseDesktopInstallPlan.Phase>
    ) {
        val last = phases.last()
        post(
            onProgress,
            Progress(
                phaseId = last.id,
                phaseLabel = "Complete",
                phaseIndex = phases.size - 1,
                phaseCount = phases.size,
                overallPercent = 100,
                detail = "Environment ready",
                finished = true
            )
        )
    }

    private fun postFail(
        onProgress: (Progress) -> Unit,
        phases: List<BaseDesktopInstallPlan.Phase>,
        message: String
    ) {
        val p = phases.firstOrNull()
        post(
            onProgress,
            Progress(
                phaseId = p?.id ?: "ERR",
                phaseLabel = "Failed",
                phaseIndex = 0,
                phaseCount = phases.size.coerceAtLeast(1),
                overallPercent = 0,
                detail = message,
                failed = true,
                finished = true,
                errorMessage = message,
                logLine = "ERROR: $message"
            )
        )
    }

    private fun postCancelled(
        onProgress: (Progress) -> Unit,
        phases: List<BaseDesktopInstallPlan.Phase>
    ) {
        postFail(onProgress, phases, "Install cancelled")
    }

    private fun weightedPercent(
        phases: List<BaseDesktopInstallPlan.Phase>,
        currentIndex: Int,
        fractionInPhase: Float
    ): Int {
        val total = phases.sumOf { it.weight }.coerceAtLeast(1)
        var done = 0
        for (i in phases.indices) {
            if (i < currentIndex) done += phases[i].weight
            else if (i == currentIndex) done += (phases[i].weight * fractionInPhase).toInt()
        }
        return ((done * 100) / total).coerceIn(0, 100)
    }

    private fun post(onProgress: (Progress) -> Unit, progress: Progress) {
        main.post { onProgress(progress) }
    }

    private fun isProotXfceInstalled(ctx: Context): Boolean =
        File(
            ctx.filesDir,
            "usr/var/lib/proot-distro/containers/debian/rootfs/usr/bin/startxfce4"
        ).exists()

    private fun isChrootXfceInstalled(): Boolean =
        File("/data/local/tmp/chrootDebian13/usr/bin/startxfce4").exists()

    companion object {
        private const val TAG = "OnboardingInstall"
    }
}
