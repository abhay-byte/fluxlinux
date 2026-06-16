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
- id: T10
  title: KDE Plasma launch broken on Termux
  type: bug
  priority: high
  difficulty: unknown
  why: User report — installed kde plasma desktop component on Termux, launch does not work. Setup completes (verification green) but tapping Launch does not start a working Plasma session.
  expected: Tapping "Launch" on a Termux KDE install starts a working Plasma session via Termux:X11.
  actual: Launch does not work. Likely root causes: (1) `start_kde_termux.sh` missing/wrong D-Bus / PulseAudio / Termux:X11 wiring; (2) `plasma` package provides startplasma-x11 binary but some session services fail to start inside Termux proot-like env. The verification step in setup_kde_termux.sh checks for binaries but doesn't validate the session can actually start.
  reproduction: |
    1. Open FluxLinux, Termux distro
    2. Install kde_plasma component (install verification passes ✅)
    3. Tap Launch
    4. Nothing / session fails to start
  frequency: always (per user)
  impact: app/src/main/assets/scripts/termux/setup/setup_kde_termux.sh, start_kde_termux.sh, possibly start_xfce4_termux.sh; TermuxIntentFactory.kt launch path
  followups: null
  images: null
  github_ref: null
  plan: null
---
