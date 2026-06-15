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
- id: T2
  title: Prevent concurrent feature package installs
  type: feature
  priority: high
  difficulty: easy
  why: Concurrent installs can corrupt package state; user got bitten by misclick
  really_needed: Yes, can break installs
  impact: UI (disable install buttons + progress indicator while running)
  followups: null
  images: null
  github_ref: GH-10
  plan: |
    Goal: prevent concurrent feature/component installs from corrupting package state.

    Verification result: ALREADY IMPLEMENTED. No code work needed.

    Existing implementation:
    - `InstallationQueueManager.InstallationState.isInstalling` flag (singleton, queue-scoped)
    - `GlassCard.kt:220-233` — install button disabled when `isGlobalInstalling`; label flips to
      "Installation Busy..." or "View Progress" (when this card's distro is the active one)
    - `DistroSettingsScreen.kt:676,705` — component Install/Re-run + Uninstall buttons disabled;
      label "Busy..." when busy; icon alpha dimmed
    - `DistroScreen.kt:110-113` — `isCurrentlyInstalling` distinguishes this-distro vs other-distro;
      extra guard rejects enqueue from a non-active distro while another is running
    - `MainActivity.kt:509,613,638` — single enqueue path through `InstallationQueueManager.enqueue()`
    - Commit `8d2ea22 feat: concurrent installation prevention with user cancel` ships the feature
    - Commit `e04da8f` follow-up: base install handled manually (curl/Termux) so it no longer
      leaves UI stuck in Busy state
  note: Verified — already implemented. Closed without code changes.
- id: T3
  title: Foreground service to keep local server alive during OS install
  type: bug
  priority: medium
  difficulty: easy
  why: Android kills FluxLinux's background server, breaking the install bridge (curl: connection refused on localhost)
  really_needed: Workaround exists (split-screen), but proper fix avoids user friction
  impact: Bridge/service layer + notification for the foreground service
  followups: null
  images: null
  github_ref: GH-9
  plan: |
    Goal: host LocalInstallServer in a foreground Service so Android keeps the
    process alive across activity death (app backgrounded, low-memory kill during
    long OS installs).

    Branch note: dev-cycle spec uses `$VERSION_BRANCH/$ID-$SLUG` but git refuses
    nested refnames when a sibling file ref exists (v1.8.x is a file, not a
    directory). Using flat `T3-foreground-service-keep-alive`; PR still targets
    v1.8.x.

    Files to change:
    - NEW  app/src/main/kotlin/com/ivarna/fluxlinux/core/service/InstallServerService.kt
            ForegroundService that owns a LocalInstallServer instance, builds
            the persistent notification, and stops cleanly.
    - MOD  app/src/main/AndroidManifest.xml
            Register <service> with foregroundServiceType="dataSync";
            add FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC perms.
    - MOD  app/src/main/kotlin/com/ivarna/fluxlinux/MainActivity.kt
            Replace both inline LocalInstallServer usages (lines ~518 and ~660)
            with ContextCompat.startForegroundService(...) bound to the script
            via Intent extras; read bound port back via a LocalBroadcastManager
            receiver; stop service on download callback or 5-min timeout
            (whichever fires first).

    Approach:
    1. InstallServerService.onCreate — create notification channel
       ("fluxlinux_install_server", IMPORTANCE_LOW).
    2. onStartCommand — extract script string from intent extras, call
       startForeground(NOTIF_ID, buildNotification()), instantiate
       LocalInstallServer, start it on Dispatchers.IO, broadcast
       `com.ivarna.fluxlinux.PORT_READY` with the port extra, set
       resultCode=START_NOT_STICKY (one-shot — recreating after kill doesn't
       help when the script is single-use).
    3. Server's onDownload callback — broadcast a STOP_HINT and self-stop
       (preserves current 5-min fallback for re-runs; we tighten to ~90s after
       first download).
    4. onDestroy — stop server, cancel notification.

    Manifest additions:
    - <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    - <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    - <service android:name=".core.service.InstallServerService"
              android:exported="false"
              android:foregroundServiceType="dataSync" />

    Edge cases:
    - Android 14+ requires foregroundServiceType — pick "dataSync" (script
      download is short-lived HTTP, fits the spec).
    - Android 13+ POST_NOTIFICATIONS runtime grant — already in manifest;
      prompt deferred to first install (or rely on system default for FGS,
      which does NOT require runtime grant on Android 13+ — only user-initiated
      notifications do).
    - Multiple startService calls while server is up — service is sticky
      across start ids; replace script + re-broadcast port instead of
      double-binding the socket.
    - Activity killed before port broadcast received — receiver
      re-subscribes in onResume and checks a SharedPreferences "lastPort".
    - Termux curl times out (port not bound yet) — broadcast fires before
      Termux is invoked; no race in practice.
    - OEM aggressive killers (Xiaomi/MIUI, Huawei EMUI) — FGS is still
      killed on some OEMs even with notification. Document in
      docs/to_be_fixed.md; out of scope for this fix.

    Test plan:
    - Build: ./gradlew assembleDebug succeeds.
    - Unit: InstallServerService.start binds port; second start replaces
      script without rebinding.
    - Manual A (background test): trigger Base Install, swipe app from
      recents, check notification is still showing, from Termux run
      `curl -s http://localhost:PORT | head -1` — expect 200 + script.
    - Manual B (low-memory): trigger install, run `adb shell am send-trim-memory
      <pid> COMPLETE`, verify server still serves.
    - Manual C (download callback): after curl downloads, check service
      stops within ~5s and notification dismisses.

    Open questions:
    - Re-run flow: keep current 5-min idle window, or shorten to 90s after
      first download? (Plan: 90s after first download, 5 min if never
      downloaded — matches current re-run support.)
    - LocalBroadcastManager — deprecated in AndroidX but still works on
      API <34. Acceptable here; switch to Flow/SharedFlow if we want to drop
      the dependency later.

  note: Branch flat-named due to git ref-nesting conflict with v1.8.x file ref.
