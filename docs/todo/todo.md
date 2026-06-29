---
- id: T1
  title: Add Chinese (zh) language support
  type: feature
  priority: high
  difficulty: easy
  why: Chinese-speaking users need localized UI
  really_needed: unknown
  impact: All UI strings, locale config
  followups: null
  images: null
  github_ref: null
  plan: null
- id: T2
  title: Uninstall and remove Termux tweaks (auto revert)
  type: feature
  priority: high
  difficulty: medium
  why: From GH-28 — no way to undo Termux tweaks short of manual cleanup
  really_needed: unknown
  impact: Termux setup scripts, uninstall UI affordance
  followups: null
  images: null
  github_ref: GH-28
  plan: null
- id: T3
  title: Broken permissions after using Debian chroot (Trixie)
  type: bug
  priority: high
  difficulty: medium
  frequency: often
  expected: pkg/apt/chown work normally in Termux after exiting Debian chroot
  actual: Error 13 (Permission denied) on /data/data/com.termux/files/usr/tmp; pkg/apt/apt-get break
  reproduction: |
    1. Install Debian chroot container (Trixie)
    2. Use it, then exit back to plain Termux
    3. Run pkg / apt / chown / chmod → EACCES on usr/tmp
  impact: Termux environment unusable for package management until manual fix
  images: null
  github_ref: GH-27
  plan: null
- id: T4
  title: Black screen + mouse cursor; display crashes back to X11
  type: bug
  priority: high
  difficulty: medium
  frequency: sometimes
  expected: GUI session runs normally
  actual: Black screen with mouse cursor, then crash and return to X11; repair has same result
  reproduction: |
    1. Launch a desktop session (xfce/kde/etc.)
    2. Observe black screen with only the cursor
    3. Session crashes, drops back to X11
  impact: Affected users cannot run a working desktop session
  images: null
  github_ref: GH-15
  plan: null
---
