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
- id: T4
  title: Disclaimer banner on Termux/X11 install checks
  type: feature
  priority: medium
  difficulty: easy
  why: New users still hit "stuck at grant permission" with outdated Play Store Termux; need a visible warning in onboarding
  really_needed: Yes, prevents repeat of GH-8-style issues
  impact: Onboarding UI (Termux + X11 install check screens)
  followups: null
  images: null
  github_ref: GH-8
  plan: |
    Goal: add a visible warning card on the Termux and Termux:X11 install-check
    screens in onboarding, telling users to UNINSTALL the Play Store version
    first and to use the GitHub release version FluxLinux already downloads.

    Reference: docs/to_be_fixed.md § "Play Store Termux Version Warning"
    (already has a stub plan). Expand it into a working feature.

    Files to change:
    - MOD app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/PrerequisitesScreen.kt
            Add a warning card to the Termux + Termux:X11 install-check sections.
            Card is always visible while the corresponding check is in any state
            other than "installed via GitHub release" (Play Store install +
            outdated install both trigger the warning).
    - NEW app/src/main/res/values/strings.xml entries (or inline string)
            Re-use the existing inline Text strings pattern in the screen;
            no new string resources needed.
    - MOD docs/to_be_fixed.md
            Mark the "Play Store Termux Version Warning" entry as
            implemented in T4.

    Approach:
    1. PrerequisitesScreen — locate the Termux install check block and the
       Termux:X11 install check block. Both use a GlassCard pattern with
       status text (e.g. "Installed", "Not installed", "Play Store version").
    2. Add a warning Card (or alert banner inside the existing GlassCard)
       that renders when:
         a. installedVia == PLAY_STORE, OR
         b. installedVersion is below the minimum (e.g. v0.118.3)
    3. The banner content:
         Title:  "⚠️ Use the GitHub version of Termux"
         Body:   "The Play Store version of Termux is no longer maintained and
                  will fail at the 'grant permission' step. Uninstall it, then
                  tap 'Re-check' below to install the version FluxLinux
                  recommends."
         CTA:    small inline "How to uninstall" text with a deeplink
                 (termux:// uninstall flow) — or just a "Re-check" button that
                 re-triggers the Termux download.
    4. Add an idempotent guard: if the warning is already visible and the
       user is on this screen, don't re-show it on every recomposition.

    Detection (PrerequisitesScreen already has Termux package info):
    - Read pmPath: /data/data/com.termux is the install path for F-Droid
      and GitHub builds. Play Store path is /data/data/com.termux too, so
      we cannot tell from the path alone. Use the versionName from
      PackageManager: Play Store has been stuck at v0.117 since 2020.
      GitHub/F-Droid are on v0.118+ (we ship 0.118.3 or later).
    - Threshold: versionName < "0.118" → "outdated" → show banner.
    - If versionName is null (not installed) → no banner (the install
      button is the right affordance).
    - If versionName >= "0.118" → no banner.

    Edge cases:
    - Termux not installed yet: no banner. The download button is the
      primary affordance.
    - Termux installed from Play Store AND version is current (rare,
      possible if user updates via Play Store): no banner. The version
      check is the source of truth.
    - Termux:X11: same version check, but minimum version is different
      (Termux:X11 has its own release cadence). For T4 use a softer
      banner ("Make sure you have the latest Termux:X11 from GitHub
      releases") since the exact threshold isn't well-known.
    - Screen rotation: the version check is a one-shot at screen
      launch; state is recomposed on recomposition. No persistence
      needed for the banner itself.

    Test plan:
    - Build: ./gradlew assembleDebug succeeds.
    - Manual A (Play Store installed): install v0.117 from Play Store,
      open FluxLinux onboarding, expect red warning banner on Termux
      check card.
    - Manual B (GitHub installed): install v0.118+ from GitHub, expect
      no banner.
    - Manual C (not installed): no banner; download button is primary.
    - Manual D (X11): install Termux:X11 from Play Store (if possible),
      expect warning banner.

    Open questions:
    - Should the banner have a "dismiss for this session" affordance?
      Lean: no, the user must fix the underlying state, the banner is
      factual.
    - For Termux:X11, do we have a well-known "outdated" version number?
      Lean: no, use a softer warning without version threshold.

  note: Branch flat-named T4-disclaimer-banner-termux-x11 (same git ref-
        nesting workaround as T3).
- id: T6
  title: Add monochrome icon for Android theming
  type: feature
  priority: low
  difficulty: easy
  why: Modern Android supports themed icons; monochrome layer lets icon adapt to user's theme
  really_needed: Cosmetic polish, not blocking
  impact: Assets (monochrome PNG/vector + adaptive icon config)
  followups: null
  images: https://github.com/user-attachments/assets/3b15435d-8e54-4d58-88ed-b85b722d8e90
  github_ref: GH-2
  plan: |
    Goal: ship a Material You–compatible monochrome icon for the adaptive
    launcher icon. Android 13+ themed icons pick up the wallpaper-derived
    tint and apply it to a single-colour silhouette the app provides.

    What shipped:
    - app/src/main/res/drawable/ic_launcher_monochrome.png: 1024x1024 RGBA
      PNG, black ink on transparent background. Sourced from a user-provided
      Gemini-generated line drawing of the FluxLinux penguin-on-box mascot.
      Icon occupies ~70% of the 108dp canvas so the launcher mask
      (squircle / circle / teardrop) doesn't crop the silhouette.
    - mipmap-anydpi-v26/ic_launcher.xml: <monochrome> element points at
      @drawable/ic_launcher_monochrome (unchanged).
    - Manual retest on OnePlus (Android 16, themed icons enabled): the
      icon shows on the home screen in the system's gray tint, with the
      penguin-on-box mascot clearly visible. User approved.

    Verification: adaptive-icon manifest merger resolves the reference
    cleanly; the icon tints correctly under Material You.

    Closes GH-2.
  note: Original e60f18a + f04a043 commits shipped a multi-shade raster
        PNG which the system-tint pass couldn't flatten. T6 swaps in a
        clean B/W silhouette that tints cleanly under any wallpaper.
- id: T7
  title: Review and fix XFCE customisation script
  type: bug
  priority: high
  difficulty: unknown
  why: XFCE customisation script not working completely — needs full review
  really_needed: Yes, core feature
  impact: termux/setup/setup_xfce4_termux.sh (customisation steps), possibly start/stop
  followups: null
  images: null
  github_ref: null
  plan: |
    Goal: Make native Termux XFCE4 setup reliable and complete.
    Files: MODIFY app/src/main/assets/scripts/termux/setup/setup_xfce4_termux.sh; inspect start/stop/customization scripts for compatibility.
    Approach:
      1. Review package dependencies against start_xfce4_termux.sh and stop_xfce4_termux.sh.
      2. Ensure setup installs everything needed for Termux:X11, PulseAudio, D-Bus, and XFCE session startup.
      3. Add safe handoff to setup_customization_termux.sh when available.
      4. Harden verification and callback result reporting so failed installs do not report success.
    Edge cases: missing optional apps, missing customization script, unavailable repo packages, failed core packages, app callback on failure.
    Test plan: shell syntax check changed scripts; run project build if available.
    Open questions: none.
  note: |
    Final ship (commit 493f389, PR #21):
    - setup_xfce4_termux.sh: send_callback helper + verify_installation returns
      1/0; default xfwm4.xml + gtk-3.0 settings.ini written so first XFCE4
      launch isn't bare; PulseAudio verification now MISSING=1.
    - setup_customization_termux.sh: full rewrite to mirror Debian XFCE4
      customization for Termux. Pulls theme/icon/cursor/wallpaper/font zips
      from debian-v1 GitHub release; subdir-tolerant extraction; light/dark
      theme branches via FLUX_THEME env.
    - setup_hw_accel_termux.sh: Adreno fix — on Turnip path, removes
      conflicting vulkan-loader-android, installs
      vulkan-loader-generic mesa-vulkan-icd-freedreno-dri3 (fallback to
      plain mesa-vulkan-icd-freedreno). Non-Adreno keeps the android
      loader. Verification: OnePlus CPH2691 (Snapdragon/Adreno, Android 16).
    - TermuxIntentFactory.kt: error-callback plumbing (result=error) added
      to all 4 install paths (termux, debian_chroot, debian13_chroot,
      proot). Chroot branches capture chroot exit inside su -c and
      propagate via exit $STATUS; proot inner command captures script RC
      BEFORE the trailing rm (which would otherwise mask the status).
    - MainActivity.kt: strict base_install callback match (no longer
      matches arbitrary currentTask via the redundant `|| scriptName ==
      "base_install"` fallback).
    - DistroSettingsScreen.kt: remember(component.id, distro.id, refreshKey)
      — per-distro install state no longer re-uses cached value when
      switching distros.

    Known followups (not blocking T7):
    - Shell scripts still call send_callback from handle_error and
      final-success line. The outer wrapper also fires a matching
      callback. Duplicates are silently dropped by the strict
      currentTask.id check in MainActivity, but the second toast is
      suppressed naturally. Cleanest fix: drop send_callback from the
      shell scripts and rely on the outer wrapper exclusively.
    - docs/termux/native_gui_research.md §5 still says Turnip needs
      vulkan-loader-android (now wrong). Per user instruction, not
      updating in this T7.
    - result=failure (line 316) vs result=error (line 520) in
      TermuxIntentFactory — MainActivity's else-branch handles both, but
      the values are inconsistent for grep-ability.
- id: T7
  title: Review and fix XFCE customisation script
  type: bug
  priority: high
  difficulty: unknown
  why: XFCE customisation script not working completely — needs full review
  really_needed: Yes, core feature
  impact: termux/setup/setup_xfce4_termux.sh (customisation steps), possibly start/stop
  followups: null
  images: null
  github_ref: null
  plan: |
    Goal: Make native Termux XFCE4 setup reliable and complete.
    Files: MODIFY app/src/main/assets/scripts/termux/setup/setup_xfce4_termux.sh; inspect start/stop/customization scripts for compatibility.
    Approach:
      1. Review package dependencies against start_xfce4_termux.sh and stop_xfce4_termux.sh.
      2. Ensure setup installs everything needed for Termux:X11, PulseAudio, D-Bus, and XFCE session startup.
      3. Add safe handoff to setup_customization_termux.sh when available.
      4. Harden verification and callback result reporting so failed installs do not report success.
    Edge cases: missing optional apps, missing customization script, unavailable repo packages, failed core packages, app callback on failure.
    Test plan: shell syntax check changed scripts; run project build if available.
    Open questions: none.
