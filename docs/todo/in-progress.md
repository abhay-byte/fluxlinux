---
- id: T8
  title: Termux customisation + hardware-accel components missing uninstall button
  type: bug
  priority: high
  difficulty: easy
  why: User report — xfce customisation has Uninstall button (Debian path works), but Termux customisation and hw_accel components do not. Inconsistent UX across components. Possibly tied to T1 plan: hw_accel is mandatory (T1 explicitly hid button for it), but customisation was skipped per spec — user wants it unhidden.
  expected: Every non-mandatory component (including xfce4 customisation, kde customisation, hw_accel-if-applicable) shows an Uninstall button in the Termux distro ComponentManagementGlassCard.
  actual: Termux customisation and hw_accel components do not render the Uninstall button. xfce4 customisation does (Debian path). Inconsistent.
  reproduction: |
    1. Open FluxLinux on a Termux distro
    2. Install a component (e.g., xfce4 customisation or hw_accel)
    3. Open DistroSettings screen
    4. Expected: Uninstall button visible next to Re-run
    5. Actual: button missing for customisation + hw_accel
  frequency: always
  impact: DistroSettingsScreen.kt (button visibility logic) — TermuxIntentFactory.kt (intent plumbing) — possibly setup_hw_accel_termux.sh / setup_customization_termux.sh if they lack uninstall branch
  followups: T9 (add uninstall block to termux component scripts)
  images: null
  github_ref: null
  plan: |
    Goal: Hide the broken Uninstall button on Termux XFCE4 Customization
    card. The button is currently visible (id="customization" is not
    mandatory, not comingSoon, not xfce4_desktop) but tapping it runs
    `setup_customization_termux.sh uninstall` — the script has no
    `uninstall` arg branch, so it re-runs the install. Same risk T1
    noted for xfce4_desktop.

    T8 = UI fix (hide). T9 = add `uninstall` branch to Termux scripts so
    the button can be re-enabled in a follow-up. Pair stays decoupled
    per user direction.

    Files to change:
    - MOD app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/DistroSettingsScreen.kt
        Add `component.id == "customization" && component.scriptName.contains("termux")`
        to the gate at line 707 so the button is hidden for the broken
        case. Scope to Termux only via the scriptName check — the same
        id ("customization") in Debian will still get the button
        (Debian's setup_customization_debian.sh also has no uninstall
        branch, but T1 already excluded it from coverage; if the user
        wants the Debian path hidden too, that becomes a follow-up).
        Update the comment block at lines 702-706 to mention
        "Termux xfce customisation: hidden because its script lacks an
        uninstall branch (T9 will add it)."

    Approach:
    1. At line 707, add one more condition to the AND chain:
       `&& !(component.id == "customization" && component.scriptName.contains("termux"))`
    2. Update the explanatory comment above to call out the new
       exclusion.
    3. No model changes, no intent changes — UI-only.

    Edge cases:
    - The same id "customization" exists in debianComponents (Debian
      path) and termuxComponents (Termux path). The scriptName check
      scopes the new exclusion to Termux. Debian's behavior is
      unchanged (still shows button — same T1 ambiguity that user
      can fix in a separate todo if they want).
    - If user installs Termux xfce4_desktop, then customisation, the
      customisation card no longer shows Uninstall. They can still
      use Re-run. To remove: re-flash Termux prefix (heavy) — same
      constraint as xfce4_desktop and hw_accel, which are also
      hidden. Acceptable.
    - T9 will add `if [ "$1" = "uninstall" ]` branches to
      setup_customization_termux.sh and the other 4 Termux setup
      scripts. Once T9 lands, this gate's exclusion can be relaxed
      in a follow-up PR.

    Test plan:
    - Build: ./gradlew assembleDebug succeeds.
    - Manual visual: open Termux distro, install xfce4_desktop + then
      customization. Expect: customization card has Re-run button
      but no Uninstall button. xfce4_desktop card has neither
      (already excluded). hw_accel card has neither (mandatory).
      kde_plasma + kde_customization cards: still have Uninstall
      button (they're also broken but user only flagged xfce
      customisation; T9 will fix all of them).
    - Manual regression: Debian distro customization card still
      shows Uninstall button (scriptName doesn't match).

    Open questions: none.
---
- id: T9
  title: Add uninstall branch to Termux component setup scripts
  type: feature
  priority: high
  difficulty: easy
  why: User report — Termux setup scripts lack "uninstall" handling. T1 only added uninstall blocks to 13 Debian scripts. Termux scripts (xfce4, kde, customisation, hw_accel) are a separate set that were not covered. Per user direction: add uninstall to kde + kde customisation only (xfce4 / xfce custom / hw_accel skipped — buttons hidden by T1/T8/mandatory).
  really_needed: Yes — without uninstall branches, the UI Uninstall button on Termux kde_plasma / kde_customization cards currently re-runs install instead of removing.
  impact: app/src/main/assets/scripts/termux/setup/setup_kde_termux.sh, setup_customization_kde_termux.sh — add `if [ "$1" = "uninstall" ]` branch
  followups: T8
  images: null
  github_ref: null
  plan: |
    (in-progress, awaiting user manual-test approval)
---
