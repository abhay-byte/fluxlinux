package com.ivarna.fluxlinux.core.data

import android.content.Intent
import android.content.Context
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.ScriptManager

object TermuxIntentFactory {

    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"

    private const val TERMUX_BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
    private const val TERMUX_HOME_DIR = "/data/data/com.termux/files/home"

    /**
     * Creates an intent to execute a bash script string in Termux.
     */
    fun buildRunCommandIntent(
        scriptContent: String,
        runInBackground: Boolean = false
    ): Intent {
        return Intent(ACTION_RUN_COMMAND).apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH_PATH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-c", scriptContent))
            putExtra(EXTRA_WORKDIR, TERMUX_HOME_DIR)
            putExtra(EXTRA_BACKGROUND, runInBackground)
            // 0 = ACTION_FAIL_ON_SESSION_EXIT (keep session open if it fails?)
            // let's default to just running.
        }
    }

    /**
     * A simple "Ping" command to check if connection works.
     */
    fun buildTestConnectionIntent(): Intent {
        return buildRunCommandIntent("echo 'FluxLinux: Connection Established!' && sleep 2")
    }

    /**
     * Generates the install command string for manual execution.
     */
    fun getInstallCommand(distroId: String, setupScript: String? = null, installScriptContent: String, guiScriptContent: String): String {
        // Enforce newline termination for safety
        val safeInstallScript = if (!installScriptContent.endsWith("\n")) "$installScriptContent\n" else installScriptContent
        val safeGuiScript = if (!guiScriptContent.endsWith("\n")) "$guiScriptContent\n" else guiScriptContent
        
        val installScriptB64 = android.util.Base64.encodeToString(safeInstallScript.toByteArray(), android.util.Base64.NO_WRAP)
        val guiScriptB64 = android.util.Base64.encodeToString(safeGuiScript.toByteArray(), android.util.Base64.NO_WRAP)
        
        val setupB64 = if (!setupScript.isNullOrEmpty()) {
            android.util.Base64.encodeToString(setupScript.toByteArray(), android.util.Base64.NO_WRAP)
        } else {
            "null"
        }
        
        // Use Base64 decoding to write files. This avoids fragile 'cat << EOF' constructs in terminals
        // and handles special characters safely.
        return """
            echo "$installScriptB64" | base64 -d > ${'$'}HOME/flux_install.sh
            chmod +x ${'$'}HOME/flux_install.sh
            
            echo "$guiScriptB64" | base64 -d > ${'$'}HOME/start_gui.sh
            chmod +x ${'$'}HOME/start_gui.sh
            
            bash ${'$'}HOME/flux_install.sh $distroId "$setupB64"
        """.trimIndent()
    }

    /**
     * Just opens Termux (launcher intent).
     */
    fun buildOpenTermuxIntent(context: android.content.Context): Intent? {
        return context.packageManager.getLaunchIntentForPackage("com.termux")
    }

    /**
     * Installs a specific distro... (Deprecated: User Manual Fallback Preferred)
     */
    fun buildInstallIntent(distroId: String, setupScript: String? = null): Intent {
        // Use the native helper script we created in setup_termux.sh
        // Usage: bash ~/flux_install.sh <distro> <base64_setup>
        
        val setupB64 = if (!setupScript.isNullOrEmpty()) {
            android.util.Base64.encodeToString(setupScript.toByteArray(), android.util.Base64.NO_WRAP)
        } else {
            "null"
        }
        
        val command = "bash $TERMUX_HOME_DIR/flux_install.sh $distroId \"$setupB64\""
        return buildRunCommandIntent(command)
    }

    /**
     * Uninstalls/Removes a specific distro.
     */
    fun buildUninstallIntent(distroId: String): Intent {
        val callbackUrl = "fluxlinux://callback?result=success&name=distro_uninstall_$distroId"
        
        val command = when {
            distroId == "termux" -> {
                "pkg uninstall -y xfce4 xfce4-terminal tigervnc && echo 'FluxLinux: Termux Native Desktop Removed.' && sleep 1 && am start -a android.intent.action.VIEW -d \"$callbackUrl\""
            }
            distroId == "debian13_chroot" -> {
                // Chroot: Inline uninstall logic (unmount, remove, callback)
                // This avoids dependency on pre-deployed script files
                """
                su -c '
                DEBIANPATH="/data/local/tmp/chrootDebian13"
                echo "Unmounting filesystems..."
                for mnt in $(grep "${'$'}DEBIANPATH" /proc/mounts | awk "{print \${'$'}2}" | sort -r); do
                    umount -l "${'$'}mnt" 2>/dev/null
                done
                echo "Removing chroot directory..."
                rm -rf "${'$'}DEBIANPATH"
                rm -f /data/local/tmp/start_debian13*.sh /data/local/tmp/enter_debian13.sh /data/local/tmp/run_debian13_root.sh /data/local/tmp/stop_debian13*.sh /data/local/tmp/uninstall_debian13.sh
                echo "Chroot removed successfully!"
                am start -a android.intent.action.VIEW -d "$callbackUrl"
                '
                """.trimIndent()
            }
            distroId == "debian_chroot" -> {
                // Chroot: Inline uninstall logic
                """
                su -c '
                DEBIANPATH="/data/local/tmp/chrootDebian"
                echo "Unmounting filesystems..."
                for mnt in $(grep "${'$'}DEBIANPATH" /proc/mounts | awk "{print \${'$'}2}" | sort -r); do
                    umount -l "${'$'}mnt" 2>/dev/null
                done
                echo "Removing chroot directory..."
                rm -rf "${'$'}DEBIANPATH"
                rm -f /data/local/tmp/start_debian*.sh /data/local/tmp/enter_debian.sh /data/local/tmp/stop_debian*.sh /data/local/tmp/uninstall_debian*.sh
                echo "Chroot removed successfully!"
                am start -a android.intent.action.VIEW -d "$callbackUrl"
                '
                """.trimIndent()
            }
            distroId.contains("chroot") -> {
                // Generic chroot fallback
                "su -c \"rm -rf /data/local/tmp/chroot*\" && echo 'Chroot removed.' && am start -a android.intent.action.VIEW -d \"$callbackUrl\""
            }
            else -> {
                // PRoot: Try proot-distro remove, retry once if it fails, then fallback to manual removal
                """
                echo "Attempting to remove $distroId..."
                if proot-distro remove $distroId 2>/dev/null; then
                    echo "FluxLinux: $distroId Uninstalled."
                else
                    echo "First attempt failed, retrying..."
                    sleep 1
                    if proot-distro remove $distroId 2>/dev/null; then
                        echo "FluxLinux: $distroId Uninstalled."
                    else
                        echo "proot-distro command failed, using manual removal..."
                        rm -rf ${'$'}PREFIX/var/lib/proot-distro/installed-rootfs/$distroId
                        echo "FluxLinux: $distroId manually removed."
                    fi
                fi
                sleep 2
                am start -a android.intent.action.VIEW -d "$callbackUrl"
                """.trimIndent()
            }
        }
        return buildRunCommandIntent(command)
    }

    /**
     * EXTENDED INSTALL: Generates a compound script to install base + components
     */
    /**
     * Generates a raw bash script string for installing the base distro.
     * This is intended to be copied to the clipboard.
     */
    fun getBaseInstallScript(context: Context, distro: Distro): String {
        val scriptManager = ScriptManager(context)
        
        // 1. Select Base Script
        val baseScriptName = when (distro.id) {
            "debian13_chroot" -> "debian/chroot/setup/setup_debian13_chroot.sh"
            "debian_chroot" -> "debian/chroot/setup/setup_debian_chroot.sh"
            "termux" -> "termux/setup_termux.sh"   // Base Termux deps (proot, X11, etc.)
            "archlinux" -> "arch/common/setup/setup_arch_family.sh"
            else -> "debian/common/setup/setup_debian_family.sh"
        }
        
        var fullScript = ""
        
        // --- STEP LOGGING HELPER ---
        fullScript += """
            CURRENT_STEP=1
            log_step() {
                echo -e "\n\033[1;36m[STEP ${'$'}{CURRENT_STEP}] ${'$'}1\033[0m"
                ((CURRENT_STEP++))
            }
        """.trimIndent() + "\n\n"
        
        // 0. Prepend Termux Setup (Dependency Check) if not running it directly AND not Chroot
        if (distro.id != "termux" && !distro.id.contains("chroot")) {
             val termuxSetup = scriptManager.getScriptContent("termux/setup_termux.sh")
             // Strip the shebang and exit/callback from setup_termux
             var cleanSetup = termuxSetup.replace("#!/bin/bash", "")
             cleanSetup = cleanSetup.replace("exit 0", "# exit 0 deferred from setup_termux")
             cleanSetup = cleanSetup.replace("am start -a android.intent.action.VIEW", "# Deferred callback from setup_termux")
             
             // Remove the "Skipping" check to ensure dependencies are verified
             cleanSetup = cleanSetup.replace(Regex("if \\[ -f \"\\\$MARKER_FILE\" ]; then[\\s\\S]*?fi"), "# Marker check removed for full install")

             fullScript += "# --- FLUXLINUX TERMUX SETUP (Dependencies) ---\n"
             fullScript += "log_step \"Installing Termux Dependencies (Proot, X11)...\"\n"
             fullScript += cleanSetup
             fullScript += "\n\n# --- DISTRO INSTALLATION ---\n"
        }
        
        fullScript += "log_step \"Installing Base System (${distro.name})...\"\n"
        
        // --- BASE INSTALL LOGIC SPLIT ---
        val isChroot = distro.id.contains("chroot")
        val isTermux = distro.id == "termux"
        
        if (isTermux) {
             // Termux: Dependencies were installed in Step 1 (setup_termux.sh)
             // We might just echo success or run a specific termux desktop setup found in setup_debian_family?
             // Actually setup_debian_family is for Debian. Termux needs its own.
             // Currently setup_termux does most work. We can just append baseScriptName if it's not setup_termux (which it is).
             if (baseScriptName != "termux/setup_termux.sh") {
                 fullScript += scriptManager.getScriptContent(baseScriptName)
             } else {
                 fullScript += "echo 'Termux Environment Ready.'\n"
             }
        } else if (isChroot) {
             // Chroot: Script is self-contained (runs on host). Run it directly.
             // Chroot scripts handle their own internal steps.
             fullScript += scriptManager.getScriptContent(baseScriptName)
        } else {
             // Proot: Needs 'proot-distro install' FIRST, then wrap the config script.
             
             // 1. Install Distro Image
             fullScript += "log_step \"Downloading & Installing Proot Image...\"\n"
             fullScript += "proot-distro install ${distro.id} || echo 'Distro already installed or warning'\n"
             
             // 2. Wrap Configuration Script
             fullScript += "log_step \"Configuring Distro Environment...\"\n"
             val baseConfig = scriptManager.getScriptContent(baseScriptName)
             val baseConfigB64 = android.util.Base64.encodeToString(baseConfig.toByteArray(), android.util.Base64.NO_WRAP)
             
             // Use echo with base64 - avoids all heredoc/trimIndent issues
             fullScript += "echo '$baseConfigB64' | base64 -d > \$HOME/flux_base_setup.sh\n"
             fullScript += "chmod +x \$HOME/flux_base_setup.sh\n"
             fullScript += "\n# Run inside Proot (with fixed PATH)\n"
             fullScript += "proot-distro login ${distro.id} --shared-tmp -- bash -c \"export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin && bash /data/data/com.termux/files/home/flux_base_setup.sh\"\n"
             fullScript += "rm -f \$HOME/flux_base_setup.sh\n"
              
             // Deploy start_gui.sh separately using echo
             val guiScriptB64 = android.util.Base64.encodeToString(scriptManager.getScriptContent("debian/proot/start/start_gui.sh").toByteArray(), android.util.Base64.NO_WRAP)
             fullScript += "\n# Deploy Start GUI Script\nlog_step \"Updating Launch Scripts...\"\n"
             fullScript += "echo '$guiScriptB64' | base64 -d > \$HOME/start_gui.sh\n"
             fullScript += "chmod +x \$HOME/start_gui.sh\n"
             
             // Deploy stop_gui.sh as well
             val stopGuiScriptB64 = android.util.Base64.encodeToString(scriptManager.getScriptContent("debian/proot/stop/stop_gui.sh").toByteArray(), android.util.Base64.NO_WRAP)
             fullScript += "echo '$stopGuiScriptB64' | base64 -d > \$HOME/stop_gui.sh\n"
             fullScript += "chmod +x \$HOME/stop_gui.sh\n"

             // Deploy start_gui_kde.sh (KDE Plasma launcher)
             val kdeGuiScriptB64 = android.util.Base64.encodeToString(scriptManager.getScriptContent("debian/proot/start/start_gui_kde.sh").toByteArray(), android.util.Base64.NO_WRAP)
             fullScript += "echo '$kdeGuiScriptB64' | base64 -d > \$HOME/start_gui_kde.sh\n"
             fullScript += "chmod +x \$HOME/start_gui_kde.sh\n"
        }
        
        // 2. Modify Base Script to defer exit/callback if present (mostly for chroot scripts that have it)
        fullScript = fullScript.replace("exit 0", "# exit 0 deferred")
        // Remove the old AM callback
        fullScript = fullScript.replace("am start -a android.intent.action.VIEW -d \"fluxlinux://callback?result=success", "# Deferred callback")
        
        // 5. Wrap the entire script in a self-extracting runner
        // GZIP COMPRESSION OPTIMIZATION to reduce Clipboard size.
        val safeScript = if (!fullScript.endsWith("\n")) "$fullScript\n" else fullScript
        
        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(byteArrayOutputStream).use { it.write(safeScript.toByteArray()) }
        val fullScriptGzipB64 = android.util.Base64.encodeToString(byteArrayOutputStream.toByteArray(), android.util.Base64.NO_WRAP)
        
        // Generate a random ID for the EOF marker to avoid collisions
        val eofMarker = "EOF_FLUX_INSTALL_${System.currentTimeMillis()}"
        val targetPath = if (isChroot) "/data/local/tmp/flux_full_install.sh" else "$TERMUX_HOME_DIR/flux_full_install.sh"
        val runnerCmd = if (isChroot) "sh" else "bash"
        
        // Command to decode: echo "..." | base64 -d | gunzip > script.sh
        
        val runnerScript = StringBuilder()
        
        if (isChroot) {
            runnerScript.append("su -c '\n")
        }
        
        runnerScript.append("cat << '$eofMarker' > $targetPath.b64\n")
        runnerScript.append(fullScriptGzipB64)
        runnerScript.append("\n$eofMarker\n")
        
        // Decode GZIP
        runnerScript.append("base64 -d $targetPath.b64 | gunzip > $targetPath\n")
        runnerScript.append("rm $targetPath.b64\n")
        runnerScript.append("chmod +x $targetPath\n")
        runnerScript.append("$runnerCmd $targetPath\n")
        
        // --- ADD CALLBACK ---
        // --- ADD CALLBACK ---
        val callbackName = "base_install"
        val callbackUrl = "fluxlinux://callback?result=success&name=$callbackName"
        val errorUrl = "fluxlinux://callback?result=failure&name=$callbackName"
        
        runnerScript.append("if [ $? -eq 0 ]; then\n")
        runnerScript.append("    am start -a android.intent.action.VIEW -d \"$callbackUrl\"\n")
        runnerScript.append("else\n")
        runnerScript.append("    echo \"FluxLinux: Installation Failed!\"\n")
        runnerScript.append("    am start -a android.intent.action.VIEW -d \"$errorUrl\"\n")
        runnerScript.append("fi\n")
        
        if (isChroot) {
            runnerScript.append("'")
        }
        
        return runnerScript.toString()
    }

    /**
     * EXTENDED INSTALL: Generates a compound script to install base + components
     * DEPRECATED: Use getCompoundInstallScript and Manual Flow.
     */
    // Deprecated buildCompoundInstallIntent removed.

    /**
     * Launches a specific distro in CLI mode (login as flux user).
     */
    fun buildLaunchCliIntent(distroId: String): Intent {
        if (distroId == "termux") {
             return buildRunCommandIntent("echo 'You are already in Termux Native environment!' && sleep 2")
        }
        
        if (distroId == "debian_chroot") {
            // Launch Chroot CLI using Android Root (su)
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/enter_debian.sh\"", runInBackground = false)
        }

        if (distroId == "debian13_chroot") {
            // Launch Debian 13 Chroot CLI using Android Root (su)
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/enter_debian13.sh\"", runInBackground = false)
        }
        
        if (distroId == "arch_chroot") {
            // Launch Arch Chroot CLI (via generated script)
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/enter_arch.sh\"", runInBackground = false)
        }
        
        // Default to 'flux' user if setup, fallback to root if not (proot-distro handles login)
        val command = "proot-distro login $distroId --user flux"
        return buildRunCommandIntent(command, runInBackground = false)
    }

    /**
     * Launches a specific chroot distro in CLI mode as ROOT user.
     * Only works for chroot distros (debian13_chroot, debian_chroot, arch_chroot).
     */
    fun buildLaunchRootCliIntent(distroId: String): Intent {
        if (distroId == "debian_chroot") {
            // Launch Chroot CLI as Root
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/enter_debian_root.sh\"", runInBackground = false)
        }

        if (distroId == "debian13_chroot") {
            // Launch Debian 13 Chroot CLI as Root
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/enter_debian13_root.sh\"", runInBackground = false)
        }
        
        if (distroId == "arch_chroot") {
            // Launch Arch Chroot CLI as Root
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/enter_arch_root.sh\"", runInBackground = false)
        }
        
        // For non-chroot distros, fall back to regular CLI (proot doesn't support true root)
        return buildLaunchCliIntent(distroId)
    }

    /**
     * Launches a Qwen2.5-1.5B-Instruct interactive chat session via llama-vulkan inside the distro.
     * Requires: Vulkan Llama.cpp + Qwen2.5 model installed in the distro.
     */
    fun buildRunLlmIntent(context: Context, distroId: String): Intent {
        val scriptManager = ScriptManager(context)
        val scriptContent = scriptManager.getScriptContent("debian/common/addon/launch_qwen25.sh")
        val scriptB64 = android.util.Base64.encodeToString(scriptContent.toByteArray(), android.util.Base64.NO_WRAP)
        val command = "proot-distro login $distroId -- bash -c 'echo \"$scriptB64\" | base64 -d | bash'"
        return buildRunCommandIntent(command, runInBackground = false)
    }

    /**
     * Launches a specific distro in GUI mode (XFCE4).
     */
    fun buildLaunchGuiIntent(context: android.content.Context, distroId: String): Intent {
        val scriptManager = ScriptManager(context)

        if (distroId == "termux") {
            // Termux Native: deploy start_xfce4_termux.sh inline then run it
            // (matches buildLaunchKdeGuiIntent pattern — script lives in assets, not on-disk)
            val xfce4ScriptContent = scriptManager.getScriptContent("termux/start/start_xfce4_termux.sh")
            val xfce4ScriptB64 = android.util.Base64.encodeToString(xfce4ScriptContent.toByteArray(), android.util.Base64.NO_WRAP)
            val command = """
                echo '$xfce4ScriptB64' | base64 -d > ${'$'}HOME/start_xfce4_termux.sh
                chmod +x ${'$'}HOME/start_xfce4_termux.sh
                bash ${'$'}HOME/start_xfce4_termux.sh
            """.trimIndent()
            return buildRunCommandIntent(command, runInBackground = false)
        }

        if (distroId == "debian_chroot") {
            // Launch Chroot GUI as User (Wrapper handles su for Chroot entry)
            return buildRunCommandIntent("sh /data/local/tmp/start_debian_gui.sh", runInBackground = false)
        }

        if (distroId == "debian13_chroot") {
            // Launch Debian 13 Chroot GUI using Android Root (su)
            // IMPORTANT: Start VirGL and PulseAudio in Termux context FIRST (not root)
            // This fixes socket/permission issues
            val command = """
                echo "FluxLinux: Starting services in Termux context..."
                
                # VirGL server
                if [ -x "${'$'}PREFIX/bin/virgl_test_server_android" ]; then
                    nohup setsid ${'$'}PREFIX/bin/virgl_test_server_android >/dev/null 2>&1 &
                    sleep 1
                    echo "[OK] VirGL server started"
                fi
                
                # PulseAudio server
                ${'$'}PREFIX/bin/pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1 2>/dev/null
                ${'$'}PREFIX/bin/pacmd load-module module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1 >/dev/null 2>&1 || true
                echo "[OK] PulseAudio started"
                
                sleep 1
                echo "FluxLinux: Launching Chroot GUI..."
                su -c "sh /data/local/tmp/start_debian13_gui.sh"
            """.trimIndent()
            return buildRunCommandIntent(command, runInBackground = false)
        }
        
        if (distroId == "arch_chroot") {
            // Launch Arch Chroot GUI (Hyprland via VirGL)
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/start_arch_gui.sh\"", runInBackground = false)
        }
        
        // Standard Proot Launch
        // Execute the helper script created during setup
        val command = "bash $TERMUX_HOME_DIR/start_gui.sh $distroId"
        return buildRunCommandIntent(command, runInBackground = false)
    }

    /**
     * Stops the GUI for a specific distro.
     */
    fun buildStopGuiIntent(context: android.content.Context, distroId: String): Intent {
        if (distroId == "termux") {
            // Termux Native: deploy stop_xfce4_termux.sh from assets then run it
            val scriptManager = ScriptManager(context)
            val stopScriptContent = scriptManager.getScriptContent("termux/stop/stop_xfce4_termux.sh")
            val stopScriptB64 = android.util.Base64.encodeToString(stopScriptContent.toByteArray(), android.util.Base64.NO_WRAP)
            val command = """
                echo '$stopScriptB64' | base64 -d > ${'$'}HOME/stop_xfce4_termux.sh
                chmod +x ${'$'}HOME/stop_xfce4_termux.sh
                bash ${'$'}HOME/stop_xfce4_termux.sh
            """.trimIndent()
            return buildRunCommandIntent(command, runInBackground = false)
        }

        if (distroId == "debian13_chroot" || distroId == "debian_chroot") {
            // Stop Chroot GUI using root
            val scriptPath = if (distroId == "debian13_chroot") {
                "/data/local/tmp/stop_debian13_gui.sh"
            } else {
                "/data/local/tmp/stop_debian_gui.sh"
            }
            return buildRunCommandIntent("su -c \"sh $scriptPath\"", runInBackground = false)
        }
        
        if (distroId == "arch_chroot") {
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/stop_arch_gui.sh\"", runInBackground = false)
        }
        
        // Standard Proot Stop
        val command = "bash $TERMUX_HOME_DIR/stop_gui.sh $distroId"
        return buildRunCommandIntent(command, runInBackground = false)
    }

    /**
     * Runs a specific feature script inside the distro.
     * Uses Base64 injection to avoid quoting/escape issues.
     */
    fun buildRunFeatureScriptIntent(distroId: String, scriptContent: String, callbackName: String? = null, isUninstall: Boolean = false): Intent {
        val safeScript = if (!scriptContent.endsWith("\n")) "$scriptContent\n" else scriptContent
        val scriptB64 = android.util.Base64.encodeToString(safeScript.toByteArray(), android.util.Base64.NO_WRAP)

        // When uninstalling, pass "uninstall" as $1 to the script so it can
        // branch into its removal path. Install path runs with no args.
        val scriptArg = if (isUninstall) " uninstall" else ""

        // Success/error callback commands. Hoisted so every install path
        // (termux, chroot, proot) propagates the script's exit code back to
        // the app — without this, failed installs silently report success.
        // `${'$'}` is Kotlin-template-escaped so the resulting bash string
        // contains a literal `$` for shell variable expansion.
        val successCallbackCmd = if (callbackName != null) {
            "am start -a android.intent.action.VIEW -d \"fluxlinux://callback?result=success&name=$callbackName\""
        } else ":"
        val errorCallbackCmd = if (callbackName != null) {
            "am start -a android.intent.action.VIEW -d \"fluxlinux://callback?result=error&name=$callbackName\""
        } else ":"

        if (distroId == "termux") {
            // Termux Native: script runs directly in Termux host (no proot, no chroot)
            val command = """
                echo "$scriptB64" | base64 -d > $TERMUX_HOME_DIR/flux_feature.sh
                chmod +x $TERMUX_HOME_DIR/flux_feature.sh
                bash $TERMUX_HOME_DIR/flux_feature.sh$scriptArg
                STATUS=${'$'}?
                rm -f $TERMUX_HOME_DIR/flux_feature.sh
                if [ ${'$'}STATUS -eq 0 ]; then
                    $successCallbackCmd
                else
                    $errorCallbackCmd
                fi
                exit ${'$'}STATUS
            """.trimIndent()
            return buildRunCommandIntent(command, runInBackground = false)
        }

        if (distroId == "debian_chroot") {
            // For Chroot, we must decode the script on the HOST (Android)
            // Write to Termux's tmp directory since that's what gets mounted into the chroot.
            // Capture the chroot's exit code inside the su -c block, then fire
            // the matching callback after su exits.
            val termuxTmp = "/data/data/com.termux/files/usr/tmp"
            val innerCommand = """
                su -c '
                mkdir -p $termuxTmp;
                echo "$scriptB64" | base64 -d > $termuxTmp/flux_feature.sh;
                chmod +x $termuxTmp/flux_feature.sh;
                busybox chroot /data/local/tmp/chrootDebian /bin/su - root -c "bash /tmp/flux_feature.sh$scriptArg";
                STATUS=${'$'}?;
                rm -f $termuxTmp/flux_feature.sh;
                exit ${'$'}STATUS;
                ';
                sleep 1;
                if [ ${'$'}? -eq 0 ]; then $successCallbackCmd; else $errorCallbackCmd; fi
            """.trimIndent().replace("\n", " ")

            return buildRunCommandIntent(innerCommand, runInBackground = false)
        }

        if (distroId == "debian13_chroot") {
            // Debian 13 Chroot Feature Script
            // Uses generated helper for robustness, falls back to inline mounts if missing.
            // Write to Termux's tmp directory since that's what gets mounted into the chroot
            // This ensures the script is visible inside the chroot after /tmp is mounted
            val termuxTmp = "/data/data/com.termux/files/usr/tmp"
            val innerCommand = """
                su -c '
                ROOT_RUNNER="/data/local/tmp/run_debian13_root.sh";
                if [ -f "${'$'}ROOT_RUNNER" ]; then
                    mkdir -p $termuxTmp;
                    echo "$scriptB64" | base64 -d > $termuxTmp/flux_feature.sh;
                    chmod +x $termuxTmp/flux_feature.sh;
                    sh "${'$'}ROOT_RUNNER" "bash /tmp/flux_feature.sh$scriptArg";
                    STATUS=${'$'}?;
                    rm -f $termuxTmp/flux_feature.sh;
                else
                    mnt=/data/local/tmp/chrootDebian13;
                    mkdir -p $termuxTmp;
                    mount -o remount,dev,suid /data >/dev/null 2>&1;
                    mount -t proc proc ${'$'}mnt/proc >/dev/null 2>&1;
                    mount -t sysfs sysfs ${'$'}mnt/sys >/dev/null 2>&1;
                    mount -o bind /dev ${'$'}mnt/dev >/dev/null 2>&1;
                    mount -o bind /dev/pts ${'$'}mnt/dev/pts >/dev/null 2>&1;
                    mkdir -p ${'$'}mnt/dev/shm;
                    mount -t tmpfs -o size=512M tmpfs ${'$'}mnt/dev/shm >/dev/null 2>&1;
                    mkdir -p ${'$'}mnt/tmp;
                    mount --bind $termuxTmp ${'$'}mnt/tmp >/dev/null 2>&1;
                    echo "$scriptB64" | base64 -d > $termuxTmp/flux_feature.sh;
                    chmod +x $termuxTmp/flux_feature.sh;
                    busybox chroot ${'$'}mnt /bin/su - root -c "bash /tmp/flux_feature.sh$scriptArg";
                    STATUS=${'$'}?;
                    rm -f $termuxTmp/flux_feature.sh;
                fi;
                exit ${'$'}STATUS;
                ';
                sleep 1;
                if [ ${'$'}? -eq 0 ]; then $successCallbackCmd; else $errorCallbackCmd; fi
            """.trimIndent().replace("\n", " ")

            return buildRunCommandIntent(innerCommand, runInBackground = false)
        }

        // Command to run inside Termux (Proot):
        // 1. Run script inside Proot (with optional "uninstall" arg), capture its
        //    exit code BEFORE the trailing rm (which would otherwise mask the
        //    script's status with its own always-zero exit), then propagate the
        //    captured status to the outer shell via `exit $RC`.
        val innerCommand = "echo \"$scriptB64\" | base64 -d > /tmp/flux_feature.sh && bash /tmp/flux_feature.sh$scriptArg; RC=${'$'}?; rm -f /tmp/flux_feature.sh; exit ${'$'}RC"
        // 2. Outer command: run proot, capture its exit, fire success/error callback
        val command = """
            proot-distro login $distroId --shared-tmp -- bash -c '$innerCommand'
            STATUS=${'$'}?
            if [ ${'$'}STATUS -eq 0 ]; then $successCallbackCmd; else $errorCallbackCmd; fi
        """.trimIndent()

        return buildRunCommandIntent(command, runInBackground = false) // Foreground to see progress
    }

    /**
     * Runs a script as Android Root (su).
     * Used for uninstalling/managing Chroot environments.
     */
    fun buildRunRootScriptIntent(scriptContent: String): Intent {
        val safeScript = if (!scriptContent.endsWith("\n")) "$scriptContent\n" else scriptContent
        val scriptB64 = android.util.Base64.encodeToString(safeScript.toByteArray(), android.util.Base64.NO_WRAP)
        
        // Write to tmp, execute, then remove.
        // We use /data/local/tmp as it is writable by shell and accessible by root.
        val command = """
            su -c '
            echo "$scriptB64" | base64 -d > /data/local/tmp/flux_root_task.sh
            chmod +x /data/local/tmp/flux_root_task.sh
            sh /data/local/tmp/flux_root_task.sh
            rm -f /data/local/tmp/flux_root_task.sh
            '
        """.trimIndent().replace("\n", " ")
        
        return buildRunCommandIntent(command, runInBackground = false)
    }

    /**
     * Generates a safe command string that detects if it's running as root,
     * and if not, prompts the user to type 'su'.
     * Used for Clipboard copy-paste interactions.
     */
    fun getSafeRootManualCommand(scriptContent: String, scriptName: String): String {
        val safeScript = if (!scriptContent.endsWith("\n")) "$scriptContent\n" else scriptContent
        val scriptB64 = android.util.Base64.encodeToString(safeScript.toByteArray(), android.util.Base64.DEFAULT)
        // Use Heredoc with wrapped Base64 to prevent terminal freeze (Line length limits)
        val chunkedEchos = "cat << 'EOF_B64' > \"\${S}.b64\"\n$scriptB64\nEOF_B64\n"

        return """
            S="/data/local/tmp/$scriptName"
            if [ "${'$'}(id -u)" != "0" ]; then S="${'$'}HOME/$scriptName"; fi
            $chunkedEchos
            base64 -d "${'$'}S.b64" > "${'$'}S"
            rm -f "${'$'}S.b64"
            chmod +x "${'$'}S"
            if [ "${'$'}(id -u)" = "0" ]; then
                sh "${'$'}S"
            else
                echo "⚠️ PLEASE RUN AS ROOT ⚠️"
                echo "Type su and press Enter."
                echo "Then paste this command again."
            fi
        """.trimIndent() + "\n"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KDE Plasma launch / stop
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Launches KDE Plasma for a given distro.
     * PRoot: runs start_gui_kde.sh (startplasma-x11)
     * Chroot (debian13_chroot): starts VirGL + PulseAudio in Termux context first,
     *   then su into the chroot's start_debian13_kde_gui.sh.
     */
    fun buildLaunchKdeGuiIntent(context: android.content.Context, distroId: String): Intent {
        val scriptManager = ScriptManager(context)

        if (distroId == "termux") {
            // Termux Native KDE: deploy start_kde_termux.sh then run it
            val kdeScriptContent = scriptManager.getScriptContent("termux/start/start_kde_termux.sh")
            val kdeScriptB64 = android.util.Base64.encodeToString(kdeScriptContent.toByteArray(), android.util.Base64.NO_WRAP)
            val command = """
                echo '$kdeScriptB64' | base64 -d > ${'$'}HOME/start_kde_termux.sh
                chmod +x ${'$'}HOME/start_kde_termux.sh
                bash ${'$'}HOME/start_kde_termux.sh
            """.trimIndent()
            return buildRunCommandIntent(command, runInBackground = false)
        }

        if (distroId == "debian13_chroot") {
            // Deploy chroot KDE launcher: write to Termux $HOME first, then su cp to /data/local/tmp/
            val chrootKdeScriptContent = scriptManager.getScriptContent("debian/chroot/start/start_debian13_kde_gui.sh")
            val chrootKdeScriptB64 = android.util.Base64.encodeToString(chrootKdeScriptContent.toByteArray(), android.util.Base64.NO_WRAP)

            val command = """
                echo "FluxLinux: Deploying KDE launcher..."
                echo '$chrootKdeScriptB64' | base64 -d > ${'$'}HOME/start_debian13_kde_gui.sh
                chmod +x ${'$'}HOME/start_debian13_kde_gui.sh
                su -c "cp ${'$'}HOME/start_debian13_kde_gui.sh /data/local/tmp/start_debian13_kde_gui.sh && chmod +x /data/local/tmp/start_debian13_kde_gui.sh"

                echo "FluxLinux: Starting services in Termux context for KDE..."

                # VirGL server
                if [ -x "${'$'}PREFIX/bin/virgl_test_server_android" ]; then
                    nohup setsid ${'$'}PREFIX/bin/virgl_test_server_android >/dev/null 2>&1 &
                    sleep 1
                    echo "[OK] VirGL server started"
                fi

                # PulseAudio server
                ${'$'}PREFIX/bin/pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1 2>/dev/null
                ${'$'}PREFIX/bin/pacmd load-module module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1 >/dev/null 2>&1 || true
                echo "[OK] PulseAudio started"

                sleep 1
                echo "FluxLinux: Launching Chroot KDE GUI..."
                su -c "sh /data/local/tmp/start_debian13_kde_gui.sh"
            """.trimIndent()
            return buildRunCommandIntent(command, runInBackground = false)
        }

        if (distroId == "debian_chroot") {
            return buildRunCommandIntent("sh /data/local/tmp/start_debian_kde_gui.sh", runInBackground = false)
        }

        // Standard PRoot launch — deploy start_gui_kde.sh inline then run it
        val kdeGuiScriptContent = scriptManager.getScriptContent("debian/proot/start/start_gui_kde.sh")
        val kdeGuiScriptB64 = android.util.Base64.encodeToString(kdeGuiScriptContent.toByteArray(), android.util.Base64.NO_WRAP)
        val command = """
            echo '$kdeGuiScriptB64' | base64 -d > ${'$'}HOME/start_gui_kde.sh
            chmod +x ${'$'}HOME/start_gui_kde.sh
            bash ${'$'}HOME/start_gui_kde.sh $distroId
        """.trimIndent()
        return buildRunCommandIntent(command, runInBackground = false)
    }

    /**
     * Launches KDE Plasma for a given distro using Turnip/Zink (Vulkan/Adreno) GPU rendering.
     * Only supported for chroot distros — PRoot already handles Turnip via shared namespace.
     * Mounts /dev/dri and Adreno kgsl nodes inside the chroot so Turnip can access the GPU.
     */
    fun buildLaunchKdeGuiTurnipIntent(context: android.content.Context, distroId: String): Intent {
        val scriptManager = ScriptManager(context)

        if (distroId == "termux") {
            return buildLaunchKdeGuiIntent(context, distroId)
        }

        if (distroId == "debian13_chroot") {
            val chrootKdeScriptContent = scriptManager.getScriptContent("debian/chroot/start/start_debian13_kde_gui_turnip.sh")
            val chrootKdeScriptB64 = android.util.Base64.encodeToString(
                chrootKdeScriptContent.toByteArray(), android.util.Base64.NO_WRAP
            )

            val command = """
                echo "FluxLinux: Deploying KDE Turnip launcher..."
                echo '$chrootKdeScriptB64' | base64 -d > ${'$'}HOME/start_debian13_kde_gui_turnip.sh
                chmod +x ${'$'}HOME/start_debian13_kde_gui_turnip.sh
                su -c "cp ${'$'}HOME/start_debian13_kde_gui_turnip.sh /data/local/tmp/start_debian13_kde_gui_turnip.sh && chmod +x /data/local/tmp/start_debian13_kde_gui_turnip.sh"

                echo "FluxLinux: Starting PulseAudio for KDE (Turnip)..."

                # PulseAudio server (VirGL server not needed for Turnip)
                ${'$'}PREFIX/bin/pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1 2>/dev/null
                ${'$'}PREFIX/bin/pacmd load-module module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1 >/dev/null 2>&1 || true
                echo "[OK] PulseAudio started"

                sleep 1
                echo "FluxLinux: Launching Chroot KDE GUI (Turnip/Zink)..."
                su -c "sh /data/local/tmp/start_debian13_kde_gui_turnip.sh"
            """.trimIndent()
            return buildRunCommandIntent(command, runInBackground = false)
        }

        if (distroId == "debian_chroot") {
            return buildRunCommandIntent("sh /data/local/tmp/start_debian_kde_gui_turnip.sh", runInBackground = false)
        }

        // Standard PRoot — Turnip is accessible natively; just use GALLIUM_DRIVER=zink override
        val kdeGuiScriptContent = scriptManager.getScriptContent("debian/proot/start/start_gui_kde.sh")
        val kdeGuiScriptB64 = android.util.Base64.encodeToString(
            kdeGuiScriptContent.toByteArray(), android.util.Base64.NO_WRAP
        )
        val command = """
            echo '$kdeGuiScriptB64' | base64 -d > ${'$'}HOME/start_gui_kde.sh
            chmod +x ${'$'}HOME/start_gui_kde.sh
            GALLIUM_DRIVER=zink MESA_LOADER_DRIVER_OVERRIDE=zink TU_DEBUG=noconform ZINK_NO_TIMELINES=1 bash ${'$'}HOME/start_gui_kde.sh $distroId
        """.trimIndent()
        return buildRunCommandIntent(command, runInBackground = false)
    }

    /**
     * Launches KDE Plasma using pure CPU software rendering (LLVMpipe).
     * No GPU, no VirGL, no Vulkan required — works on ALL devices.
     * Slowest but most compatible option.
     */
    fun buildLaunchKdeGuiSoftwareIntent(context: android.content.Context, distroId: String): Intent {
        val scriptManager = ScriptManager(context)

        if (distroId == "termux") {
            return buildLaunchKdeGuiIntent(context, distroId)
        }

        if (distroId == "debian13_chroot") {
            val scriptContent = scriptManager.getScriptContent("debian/chroot/start/start_debian13_kde_gui_software.sh")
            val scriptB64 = android.util.Base64.encodeToString(
                scriptContent.toByteArray(), android.util.Base64.NO_WRAP
            )

            val command = """
                echo "FluxLinux: Deploying KDE Software launcher..."
                echo '$scriptB64' | base64 -d > ${'$'}HOME/start_debian13_kde_gui_software.sh
                chmod +x ${'$'}HOME/start_debian13_kde_gui_software.sh
                su -c "cp ${'$'}HOME/start_debian13_kde_gui_software.sh /data/local/tmp/start_debian13_kde_gui_software.sh && chmod +x /data/local/tmp/start_debian13_kde_gui_software.sh"

                echo "FluxLinux: Starting PulseAudio (no VirGL needed for software mode)..."
                ${'$'}PREFIX/bin/pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1 2>/dev/null
                ${'$'}PREFIX/bin/pacmd load-module module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1 >/dev/null 2>&1 || true
                echo "[OK] PulseAudio started"

                sleep 1
                echo "FluxLinux: Launching KDE (Software/LLVMpipe)..."
                su -c "sh /data/local/tmp/start_debian13_kde_gui_software.sh"
            """.trimIndent()
            return buildRunCommandIntent(command, runInBackground = false)
        }

        if (distroId == "debian_chroot") {
            return buildRunCommandIntent("sh /data/local/tmp/start_debian_kde_gui_software.sh", runInBackground = false)
        }

        // PRoot: set LIBGL_ALWAYS_SOFTWARE=1 override on the existing kde script
        val kdeGuiScriptContent = scriptManager.getScriptContent("debian/proot/start/start_gui_kde.sh")
        val kdeGuiScriptB64 = android.util.Base64.encodeToString(
            kdeGuiScriptContent.toByteArray(), android.util.Base64.NO_WRAP
        )
        val command = """
            echo '$kdeGuiScriptB64' | base64 -d > ${'$'}HOME/start_gui_kde.sh
            chmod +x ${'$'}HOME/start_gui_kde.sh
            LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe bash ${'$'}HOME/start_gui_kde.sh $distroId
        """.trimIndent()
        return buildRunCommandIntent(command, runInBackground = false)
    }

    /**
     * Stops the KDE Plasma session for a given distro.
     * Kills plasmashell, kwin_x11, kded5 instead of xfce4 processes.
     */
    fun buildStopKdeGuiIntent(context: android.content.Context, distroId: String): Intent {

        if (distroId == "termux") {
            // Termux Native KDE: deploy stop_kde_termux.sh then run it
            val scriptManager = ScriptManager(context)
            val stopScriptContent = scriptManager.getScriptContent("termux/stop/stop_kde_termux.sh")
            val stopScriptB64 = android.util.Base64.encodeToString(stopScriptContent.toByteArray(), android.util.Base64.NO_WRAP)
            val command = """
                echo '$stopScriptB64' | base64 -d > ${'$'}HOME/stop_kde_termux.sh
                chmod +x ${'$'}HOME/stop_kde_termux.sh
                bash ${'$'}HOME/stop_kde_termux.sh
            """.trimIndent()
            return buildRunCommandIntent(command, runInBackground = false)
        }

        if (distroId == "debian13_chroot") {
            val scriptManager = ScriptManager(context)
            val stopScriptContent = scriptManager.getScriptContent("debian/chroot/stop/stop_debian13_kde_gui.sh")
            val stopScriptB64 = android.util.Base64.encodeToString(stopScriptContent.toByteArray(), android.util.Base64.NO_WRAP)
            val command = """
                echo '$stopScriptB64' | base64 -d > ${'$'}HOME/stop_debian13_kde_gui.sh
                chmod +x ${'$'}HOME/stop_debian13_kde_gui.sh
                su -c "cp ${'$'}HOME/stop_debian13_kde_gui.sh /data/local/tmp/stop_debian13_kde_gui.sh && chmod +x /data/local/tmp/stop_debian13_kde_gui.sh && sh /data/local/tmp/stop_debian13_kde_gui.sh"
            """.trimIndent()
            return buildRunCommandIntent(command, runInBackground = false)
        }

        if (distroId == "debian_chroot") {
            val command = """
                su -c 'if [ -f "/data/local/tmp/stop_debian_kde_gui.sh" ]; then sh "/data/local/tmp/stop_debian_kde_gui.sh"; else pkill -f plasmashell; pkill -f kwin_x11; pkill -f kded6; pkill -f Xwayland; fi'
            """.trimIndent()
            return buildRunCommandIntent(command, runInBackground = false)
        }

        // PRoot stop — mirror XFCE4 stop_gui.sh approach:
        // proot-distro login runs killall INSIDE proot namespace, then kill X11 from Termux.
        // Do NOT use pkill from Termux directly — it kills the Termux session itself.
        val command = """
            echo "FluxLinux: Stopping KDE Plasma..."

            # Step 1: Kill KDE session processes inside proot (same as XFCE4 stop approach)
            proot-distro login ${'$'}{1:-${distroId}} -- bash -c \
                'killall -9 plasmashell kwin_x11 kded6 plasma_session startplasma-x11 dbus-launch 2>/dev/null; sleep 1' \
                2>/dev/null

            # Step 2: Stop Termux:X11
            am broadcast -a com.termux.x11.ACTION_STOP -p com.termux.x11 >/dev/null 2>&1
            killall -9 Xwayland termux-x11 2>/dev/null
            kill -9 $(pgrep -f "termux.x11") 2>/dev/null

            # Step 3: Stop PulseAudio
            pulseaudio --kill 2>/dev/null

            echo "FluxLinux: KDE Plasma stopped."
        """.trimIndent()
        return buildRunCommandIntent(command, runInBackground = false)
    }
}
