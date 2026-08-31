package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import com.ivarna.fluxlinux.BuildConfig

/**
 * Builds argv + environment for **host** (embedded Termux-class prefix) scripts/commands.
 * Package is always [TermuxHostPaths.PACKAGE] — not com.termux.
 * Ported from termux-lib `HostCommandBuilder`.
 */
object HostCommandBuilder {

    /**
     * Full host environment map (starts from [System.getenv] or empty).
     *
     * @param forceHostSetup when true, sets FLUX_SETUP_FORCE=1 for setup_termux.sh
     * @param includeTerm when true, sets TERM=xterm-256color (interactive terminal)
     */
    fun envMap(
        ctx: Context,
        forceHostSetup: Boolean = false,
        includeTerm: Boolean = true,
        base: Map<String, String>? = System.getenv()
    ): HashMap<String, String> {
        val nld = ctx.applicationInfo.nativeLibraryDir
        val env = HashMap<String, String>()
        if (base != null) env.putAll(base)

        env["PATH"] = "$nld:${TermuxHostPaths.BIN}:/system/bin"
        // W^X (targetSdk 36): proot/bash/loader must run from nativeLibraryDir, not $PREFIX/bin.
        // proot-distro must also pass PROOT_LOADER* through (see TermuxHostPaths.patchProotDistroLoaderPassThrough).
        env["PD_PROOT_BIN"] = TermuxHostPaths.libProot(ctx).absolutePath
        env["PROOT_LOADER"] = TermuxHostPaths.libLoader(ctx).absolutePath
        env["PROOT_LOADER_32"] = TermuxHostPaths.libLoader32(ctx).absolutePath
        env["PD_PULSEAUDIO_BIN"] = TermuxHostPaths.libPulseaudio(ctx).absolutePath
        env["PD_PACTL_BIN"] = TermuxHostPaths.libPactl(ctx).absolutePath
        env["LD_LIBRARY_PATH"] =
            "${TermuxHostPaths.LIB}:${TermuxHostPaths.PREFIX}/opt/virglrenderer-android/lib"
        env["PREFIX"] = TermuxHostPaths.PREFIX
        env["HOME"] = TermuxHostPaths.HOME
        env["TMPDIR"] = TermuxHostPaths.TMPDIR
        // Private glue dir — never the --shared-tmp bind (guest chmod breaks proot).
        env["PROOT_TMP_DIR"] = TermuxHostPaths.PROOT_TMP
        TermuxHostPaths.ensureHostTmpDirs()
        env["TERMUX_APP__PACKAGE_NAME"] = TermuxHostPaths.PACKAGE
        env["TERMUX_VERSION"] = TermuxHostPaths.TERMUX_VERSION
        // X11 is compiled into the app module and started by EmbeddedX11 from
        // this process; there is no APK-on-disk loader or app_process fallback.
        env["FLUX_EMBEDDED_X11"] = "1"
        if (BuildConfig.FLAVOR == "zenithblue") {
            env["FLUX_PLAY_BASELINE"] = "1"
        } else {
            env.remove("FLUX_PLAY_BASELINE")
        }
        env["TERMUX_X11_OVERRIDE_PACKAGE"] = TermuxHostPaths.PACKAGE
        env["TERMUX__PREFIX"] = TermuxHostPaths.PREFIX
        env["TERMUX__HOME"] = TermuxHostPaths.HOME
        env["SSL_CERT_FILE"] = TermuxHostPaths.SSL_CERT
        env["CURL_CA_BUNDLE"] = TermuxHostPaths.SSL_CERT
        // Android's active LinkProperties DNS is the only resolver the PRoot
        // guest can reliably inherit on host-prefix networks. The shell-side
        // helper uses its public list only when this final fallback is needed.
        env[GuestDnsConfigurator.ENV_NAME] = GuestDnsConfigurator.environmentValue(ctx)
        // Do not set PULSE_SERVER here: pulseaudio refuses to spawn a
        // daemon when it is set. Native clients use the unix socket via
        // PULSE_RUNTIME_PATH. Guests get tcp:127.0.0.1 from the builders.
        env["PULSE_RUNTIME_PATH"] = "${TermuxHostPaths.HOME}/.pulse"

        val termuxExec = TermuxHostPaths.termuxExec(ctx)
        // On x86_64 hosts (NDK translation), the binfmt_misc runner is an x86_64
        // binary and rejects the arm64 preload at startup; the preload is also
        // unneeded there since shebang rewriting is handled by the translation layer.
        val nativeHostArch = System.getProperty("os.arch").orEmpty().lowercase()
        val x86_64Host = nativeHostArch.contains("x86_64") || nativeHostArch == "x86"
        if (termuxExec.exists() && !x86_64Host) {
            env["LD_PRELOAD"] = termuxExec.absolutePath
        } else {
            env.remove("LD_PRELOAD")
        }

        if (includeTerm) {
            env["TERM"] = env["TERM"] ?: "xterm-256color"
        }

        if (forceHostSetup) {
            env["FLUX_SETUP_FORCE"] = "1"
        } else {
            env.remove("FLUX_SETUP_FORCE")
        }

        return env
    }

    /** Apply host env onto a [ProcessBuilder] (mutates process environment). */
    fun applyTo(
        ctx: Context,
        pb: ProcessBuilder,
        forceHostSetup: Boolean = false
    ) {
        val env = pb.environment()
        // base=null: only our keys; ProcessBuilder already has system env under it
        val built = envMap(ctx, forceHostSetup = forceHostSetup, includeTerm = false, base = null)
        built.forEach { (k, v) -> env[k] = v }
        if (!built.containsKey("LD_PRELOAD")) env.remove("LD_PRELOAD")
        if (!forceHostSetup) env.remove("FLUX_SETUP_FORCE")
    }

    /**
     * Run a host script with libbash.so.
     * @return argv + env for TerminalSession / ProcessBuilder
     */
    fun build(
        ctx: Context,
        scriptPath: String,
        forceHostSetup: Boolean = false
    ): Pair<Array<String>, HashMap<String, String>> {
        val shell = TermuxHostPaths.libBash(ctx).absolutePath
        val args = arrayOf(shell, scriptPath)
        val env = envMap(ctx, forceHostSetup = forceHostSetup, includeTerm = true)
        return args to env
    }

    /** True when [scriptName] should force re-validation of host setup. */
    fun shouldForceHostSetup(scriptName: String): Boolean =
        scriptName == "setup_termux.sh" || scriptName.endsWith("/setup_termux.sh")

    fun clearSetupMarker(ctx: Context) {
        TermuxHostPaths.clearSetupTermuxMarker(ctx)
    }
}
