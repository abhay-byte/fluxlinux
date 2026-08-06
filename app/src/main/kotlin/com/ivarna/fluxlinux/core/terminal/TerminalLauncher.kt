package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Entry point for card actions that need the embedded host before a terminal
 * session opens: extract bootstrap → deploy scripts/rootfs → setup_termux
 * validation (host gate). All heavy work runs on a background executor;
 * [onDone] is dispatched on the main thread.
 *
 * Pass 2: **fail-closed** — false unless extract AND rootfs deploy AND
 * setup_termux all succeed. Recovery: a failed setup_termux clears the extract
 * marker and force re-extracts once before giving up.
 */
object TerminalLauncher {

    private const val TAG = "TerminalLauncher"
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Marker written by setup_termux.sh; checked by host status cards. */
    fun isHostSetupDone(ctx: Context): Boolean =
        TermuxHostPaths.setupTermuxMarker(ctx).exists()

    fun isBootstrapExtracted(ctx: Context): Boolean =
        BootstrapInstaller.isExtracted(ctx)

    /** Filesystem truth for "Debian (proot) installed" (P4-T13 migration SSOT). */
    fun isDebianProotInstalled(ctx: Context): Boolean =
        File(ctx.filesDir, "usr/var/lib/proot-distro/containers/debian/rootfs/bin/sh").exists()

    /** Filesystem truth for "Debian (chroot) installed". */
    fun isDebianChrootInstalled(): Boolean =
        File("/data/local/tmp/chrootDebian13/bin/sh").exists()

    /** @return true when the distro rootfs exists on disk for [distroId]. */
    fun isDistroInstalledOnFs(ctx: Context, distroId: String): Boolean = when (distroId) {
        "debian" -> isDebianProotInstalled(ctx)
        "debian13_chroot", "debian_chroot" -> isDebianChrootInstalled()
        else -> false
    }

    /**
     * Ensure bootstrap extracted + scripts deployed + setup validated (async).
     * Fail-closed: false unless extract AND rootfs/loader deploy AND setup_termux
     * all succeed. Recovery (B2): a setup_termux failure NEVER wipes the prefix —
     * installed proot containers are preserved by [BootstrapInstaller]; a corrupt
     * tree (missing marker) re-extracts on the next call with containers preserved.
     *
     * @param forceHostSetup re-run setup_termux validation even when the marker exists
     *   (used by Settings "Initialize Host Environment" / Prerequisites).
     */
    fun prepareHost(
        ctx: Context,
        forceHostSetup: Boolean = false,
        progress: (done: Long, total: Long, phase: String) -> Unit = { _, _, _ -> },
        onDone: (Boolean) -> Unit = {}
    ) {
        executor.execute {
            val ok = prepareHostBlocking(ctx, forceHostSetup, progress)
            Log.i(TAG, "prepareHost ok=$ok")
            mainHandler.post { onDone(ok) }
        }
    }

    /** Blocking variant (background thread only) used by tests / RootShell flows. */
    fun prepareHostBlocking(
        ctx: Context,
        forceHostSetup: Boolean = false,
        progress: (done: Long, total: Long, phase: String) -> Unit = { _, _, _ -> }
    ): Boolean {
        // Corrupt/partial tree (no valid marker) → clean re-extract (containers preserved).
        if (!BootstrapInstaller.ensureExtracted(ctx, onProgress = progress)) {
            return false
        }
        if (!HostScriptDeployer.deployScripts(ctx)) {
            return false
        }
        if (!forceHostSetup && isHostSetupDone(ctx)) return true

        val marker = TermuxHostPaths.setupTermuxMarker(ctx)
        try {
            val (exit, out) = ShellCommandRunner.runCaptureExit(
                ctx,
                HostCommandBuilder.build(
                    ctx,
                    TermuxHostPaths.hostScript(ctx, "setup_termux.sh").absolutePath,
                    forceHostSetup = true
                ).first
            )
            Log.i(TAG, "setup_termux exit=$exit\n$out")
            if (exit == 0) return true
            marker.delete()
            Log.w(TAG, "setup_termux failed — host NOT ready (no destructive recovery)")
        } catch (e: Exception) {
            Log.w(TAG, "setup_termux run failed", e)
            marker.delete()
        }
        return false
    }
}
