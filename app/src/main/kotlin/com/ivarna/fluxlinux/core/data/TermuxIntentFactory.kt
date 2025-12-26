package com.ivarna.fluxlinux.core.data

import android.content.Intent

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
        val command = if (distroId == "termux") {
            "pkg uninstall -y xfce4 xfce4-terminal tigervnc && echo 'FluxLinux: Termux Native Desktop Removed.' && sleep 3"
        } else {
            "proot-distro remove $distroId && echo 'FluxLinux: $distroId Uninstalled.' && sleep 3"
        }
        return buildRunCommandIntent(command)
    }

    /**
     * Launches a specific distro in CLI mode (login as flux user).
     */
    fun buildLaunchCliIntent(distroId: String): Intent {
        if (distroId == "termux") {
             return buildRunCommandIntent("echo 'You are already in Termux Native environment!' && sleep 2")
        }
        // Default to 'flux' user if setup, fallback to root if not (proot-distro handles login)
        val command = "proot-distro login $distroId --user flux"
        return buildRunCommandIntent(command, runInBackground = false)
    }

    /**
     * Launches a specific distro in GUI mode (XFCE4).
     */
    fun buildLaunchGuiIntent(distroId: String): Intent {
        // Execute the helper script created during setup
        val command = "bash $TERMUX_HOME_DIR/start_gui.sh $distroId"
        return buildRunCommandIntent(command, runInBackground = true)
    }

    /**
     * Runs a specific feature script inside the distro.
     * Uses Base64 injection to avoid quoting/escape issues.
     */
    fun buildRunFeatureScriptIntent(distroId: String, scriptContent: String): Intent {
        val safeScript = if (!scriptContent.endsWith("\n")) "$scriptContent\n" else scriptContent
        val scriptB64 = android.util.Base64.encodeToString(safeScript.toByteArray(), android.util.Base64.NO_WRAP)
        
        // Command to run inside Termux:
        // 1. Log into Distro
        // 2. Decode script to /tmp
        // 3. Run script
        // 4. Cleanup
        
        val innerCommand = "echo \"$scriptB64\" | base64 -d > /tmp/flux_feature.sh && bash /tmp/flux_feature.sh; rm -f /tmp/flux_feature.sh"
        // We use --shared-tmp to access /tmp although proot handles it separately usually.
        // Actually proot-distro login runs as root by default.
        val command = "proot-distro login $distroId --shared-tmp -- bash -c '$innerCommand'"
        
        return buildRunCommandIntent(command, runInBackground = false) // Foreground to see progress
    }
}
