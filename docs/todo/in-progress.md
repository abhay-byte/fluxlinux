---
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