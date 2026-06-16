---
- id: T11
  title: Splash screen white in dark theme
  type: bug
  priority: medium
  difficulty: easy
  why: User report — splash screen background is always white, even when the device is in dark theme. Causes jarring white flash on every cold start.
  expected: Splash screen background matches the active theme (white in light, dark in dark).
  actual: Splash is always white because `app/src/main/res/values/themes.xml` hardcodes `<style name="Theme.FluxLinux" parent="android:Theme.Material.Light.NoActionBar" />` and there is no `values-night/themes.xml` override.
  reproduction: |
    1. Set device to dark theme
    2. Force-stop FluxLinux
    3. Launch from launcher
    4. Observe: white flash on splash
  frequency: always
  impact: app/src/main/res/values/themes.xml + new app/src/main/res/values-night/themes.xml + AndroidManifest.xml (theme reference)
  followups: null
  images: null
  github_ref: null
  plan: |
    Goal: Make the splash/window background match the active device
    theme (white in light mode, dark in dark mode).

    Root cause: `app/src/main/res/values/themes.xml` defines
    `<style name="Theme.FluxLinux" parent="android:Theme.Material.Light.NoActionBar" />`
    with no `android:windowBackground` override. There is no
    `values-night/themes.xml`, so the app always uses the light
    theme. The default window background for `Theme.Material.Light`
    is white → white splash on every cold start, even in dark mode.

    minSdk = 26 (Android 8.0). For API 26-30, the splash background
    is whatever `android:windowBackground` resolves to. For API 31+
    (Android 12+), there's the new `Theme.SplashScreen` system; but
    our windowBackground still shows during the pre-31 path and as
    the activity background before Compose takes over. Fixing
    windowBackground fixes both paths.

    Files to change:
    - MOD app/src/main/res/values/themes.xml
        Add `android:windowBackground` set to `?android:colorBackground`
        so the theme's day/night-aware background is used. This
        resolves to white in light mode, dark in dark mode.
    - NEW app/src/main/res/values-night/themes.xml
        Same style name `Theme.FluxLinux`, parent
        `android:Theme.Material.NoActionBar` (the dark variant
        of `Theme.Material`), with the same windowBackground
        override. Android's resource system swaps between
        `values/themes.xml` and `values-night/themes.xml` based
        on the system's Configuration.UI_MODE_NIGHT_* state.
    - (No AndroidManifest change needed — already references
     `@style/Theme.FluxLinux` on both the application and
     MainActivity, lines 28 and 44.)

    Approach:
    1. In `values/themes.xml`, set
       `<item name="android:windowBackground">?android:colorBackground</item>`.
    2. Create `values-night/themes.xml` with the dark parent +
       same windowBackground override. The `?android:colorBackground`
       attribute resolves differently in night mode (dark vs light)
       because `Theme.Material` defines it as a dark color and
       `Theme.Material.Light` defines it as white.
    3. Verify by cold-starting the app in both light and dark mode.

    Edge cases:
    - API 31+ uses the SplashScreen API. Our `windowBackground`
      still shows during the pre-Compose window — that's the
      "flash" the user is seeing. Fix applies.
    - Per-app theme override (e.g., a future in-app theme toggle
      independent of system) is out of scope. This fix only
      follows the device's day/night setting.
    - The Compose UI itself uses MaterialTheme.colorScheme which
      has its own dark/light handling — unaffected by this change.
    - `android:windowBackground` set to `?android:colorBackground`
      means the window paints the theme background before the
      first frame. No flash of the launcher icon background.
    - Fallback if `?android:colorBackground` is unavailable on some
      ancient API: not a concern at minSdk = 26.

    Test plan:
    - Build: ./gradlew assembleDebug succeeds.
    - Manual A: device in light mode, force-stop, launch → expect
      light/white splash, no flash.
    - Manual B: device in dark mode, force-stop, launch → expect
      dark splash, no white flash.
    - Manual C: app launches into main activity normally in both
      modes (regression check).

    Open questions: none.
---
