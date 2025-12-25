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
    fun getInstallCommand(distroId: String, setupScript: String? = null): String {
        val setupB64 = if (!setupScript.isNullOrEmpty()) {
            android.util.Base64.encodeToString(setupScript.toByteArray(), android.util.Base64.NO_WRAP)
        } else {
            "null"
        }
        return "bash \$HOME/flux_install.sh $distroId \"$setupB64\""
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
        // proot-distro remove <distro>
        // echo to confirm
        val command = "proot-distro remove $distroId && echo 'FluxLinux: $distroId Uninstalled.' && sleep 3"
        return buildRunCommandIntent(command)
    }

    /**
     * Launches a specific distro in CLI mode (login as flux user).
     */
    fun buildLaunchCliIntent(distroId: String): Intent {
        // Default to 'flux' user if setup, fallback to root if not (proot-distro handles login)
        // Ideally we check, but for now let's assume 'flux' if we ran setup. 
        // Safer: just login, it defaults to root, then user can su.
        // User requested CLI option. Let's try to login as flux if possible or just standard login.
        // Standard: proot-distro login <distro> is safest.
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
}
