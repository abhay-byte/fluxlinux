---
- id: T5
  title: Add more OS support (postmarketOS, Redox OS, BSDs)
  type: feature
  priority: nice-to-have
  difficulty: hard
  why: User requested postmarketOS, Redox, GhostBSD, HardenedBSD, FreeBSD, OpenBSD
  really_needed: No — current Ubuntu/Debian/Fedora/Arch covers most users
  impact: Distro definition system + install scripts (BSDs need custom paths, not proot)
  followups: null
  images: null
  github_ref: GH-6
  plan: null
---
- id: T9
  title: Add uninstall branch to Termux component setup scripts
  type: feature
  priority: high
  difficulty: easy
  why: User report — Termux setup scripts (setup_customization_termux.sh, setup_customization_kde_termux.sh, setup_kde_termux.sh) have zero "uninstall" handling. T1 only added uninstall blocks to the 13 feature-component scripts in Debian path. Termux native scripts (xfce4, kde, customisation, hw_accel) are a separate set that were not covered by T1.
  really_needed: Yes — without an uninstall block, the UI uninstall button (T1) cannot work end-to-end on Termux components. Pair this with T8.
  impact: app/src/main/assets/scripts/termux/setup/setup_xfce4_termux.sh, setup_kde_termux.sh, setup_customization_termux.sh, setup_customization_kde_termux.sh, setup_hw_accel_termux.sh (verify) — add `if [ "$1" = "uninstall" ]` branch that removes installed packages + assets
  followups: T8
  images: null
  github_ref: null
  plan: null
---
- id: T10
  title: KDE Plasma launch broken on Termux
  type: bug
  priority: high
  difficulty: unknown
  why: User report — installed kde plasma desktop component on Termux, launch does not work. Also flagged: needs to test kde desktop customisation component (setup_customization_kde_termux.sh — see T9, has zero uninstall handling, may also be incomplete install path).
  expected: Tapping "Launch" on a Termux KDE install starts a working Plasma session via Termux:X11.
  actual: Launch does not work. Root cause unknown — needs investigation across setup_kde_termux.sh (package set), start_kde_termux.sh (session startup), D-Bus / PulseAudio / Termux:X11 wiring.
  reproduction: |
    1. Open FluxLinux, Termux distro
    2. Install kde_plasma component
    3. Tap Launch
    4. Nothing / session fails to start
  frequency: always (per user)
  impact: app/src/main/assets/scripts/termux/setup/setup_kde_termux.sh, start_kde_termux.sh, possibly setup_customization_kde_termux.sh; TermuxIntentFactory.kt launch path
  followups: T9 (customisation kde script also incomplete)
  images: null
  github_ref: null
  plan: null
---
