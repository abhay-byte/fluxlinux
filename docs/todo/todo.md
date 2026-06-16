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
