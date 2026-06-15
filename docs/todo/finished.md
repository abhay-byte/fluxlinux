---
- id: T1
  title: Add option to uninstall feature packages
  type: feature
  priority: high
  difficulty: easy
  why: Users need to cleanly remove feature packages they tried but don't want
  really_needed: Yes, currently only option is rerun script
  impact: UI (uninstall button on feature cards) + scripts (uninstall mode in install scripts)
  followups: T2 (prevent concurrent installs, paired with this)
  images: null
  github_ref: GH-11
  plan: |
    Goal: let users cleanly remove a feature/component (e.g., Web Dev) from an installed distro
    without re-running the script or wiping the distro.

    Implementation (final, all 13 components in one go):
    - MODIFY app/src/main/kotlin/com/ivarna/fluxlinux/core/utils/InstallationQueueManager.kt
        add `isUninstall: Boolean = false` to InstallTask
    - MODIFY app/src/main/kotlin/com/ivarna/fluxlinux/core/data/TermuxIntentFactory.kt
        add `isUninstall: Boolean = false` to buildRunFeatureScriptIntent;
        when true, append " uninstall" to the bash invocation
    - MODIFY app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/DistroSettingsScreen.kt
        add Uninstall button + confirm dialog in ComponentManagementGlassCard;
        plumb onUninstallComponent callback; hide for mandatory components
        AND for xfce4_desktop (uses base-install script with no uninstall branch)
    - MODIFY app/src/main/kotlin/com/ivarna/fluxlinux/MainActivity.kt
        implement onUninstallComponent: enqueue InstallTask with isUninstall=true;
        on success callback (existing fluxlinux://callback deep link), call
        setComponentInstalled(distroId, id, false)
    - MODIFY 13 setup_*.sh scripts: add `if [ "$1" = "uninstall" ]` branch that
        apt-remove --purge a component-specific PKGS array + autoremove + rm
        wrappers, .desktop files, venvs, downloaded assets, symlinks, model files;
        sed-revert .bashrc/.zshrc PATH/alias entries added by installer; exit 0

    Approach:
    1. Each component script declares PKGS=(...) once with the packages it
       actually installs. Shared system deps (build-essential, git, curl,
       python3, dbus-x11, fonts, chromium, adb, cmake, etc.) intentionally
       excluded so uninstalling one component doesn't break others.
    2. Uninstall arg branch: `apt remove -y --purge "${PKGS[@]}"` + `autoremove`
       + targeted `rm` cleanup + sed-revert of shell config.
    3. UI: small red "Uninstall" text button next to "Re-run" in a single Row,
       hidden for: mandatory components, comingSoon components, xfce4_desktop
       (uses base-install script).
    4. State updated to not-installed only on successful completion (the
       existing deep-link callback already only fires on success).
    5. Reuses existing fluxlinux://callback deep link for completion detection
       (no separate marker file needed — uninstall script exits with code 0
       on success, and the outer Termux command fires the callback after).

    Covered (13): web_dev, kde_plasma, app_dev, office, gamedev,
    graphic_design, data_science, cybersec, video_editing, gen_dev,
    vulkan_llamacpp, qwen25_model, qwen35_model.

    Skipped: hw_accel (mandatory), customization + kde_customization
    (per spec), emulation (comingSoon), xfce4_desktop (base-install script
    with no uninstall branch — button hidden).

    Edge cases:
    - Mandatory components → no button
    - xfce4_desktop → no button (no uninstall branch in base-install script)
    - Script failure → state stays installed (callback doesn't fire)
    - User cancel → no state change (pendingUninstallComponent cleared in dialog)
    - Chroot/Termux-missing fallbacks → use existing clipboard-copy pattern
      (reuses the root-command flow from distro-level uninstall)

    Test: build APK, install component, uninstall, verify packages removed
    and UI badge cleared, reinstall works.
---
