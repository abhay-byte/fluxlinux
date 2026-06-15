---
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
