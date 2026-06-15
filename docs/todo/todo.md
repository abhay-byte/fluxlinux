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
  plan: null
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
  plan: null
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
  plan: null
---
