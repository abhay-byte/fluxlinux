---
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
