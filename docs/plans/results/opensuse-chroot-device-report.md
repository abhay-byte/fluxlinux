# openSUSE chroot — device report

**Date:** 2026-08-13  
**Package:** `com.ivarna.fluxlinux`  
**Path:** `/data/local/tmp/chrootOpenSUSE`

| Step | Result |
|------|--------|
| Rootfs extract | **PASS** |
| Family XFCE | **PASS** |
| `sudo -n id` | **PASS** (md2 stub + setuid). Guest `su` can fail `cannot set groups`; start uses `runuser`. |
| zypper present | **PASS** |
| XFCE on embedded X11 | **PASS** `opensuse_chroot_xfce_pass.png` — Applications panel, Space wallpaper, Home, xfce4-terminal as flux/zsh, fastfetch Tumbleweed. |

## Fixes

- TW package for `dbus-launch` is `dbus-1-daemon` (not `dbus-1-x11`).
- Empty `/etc/machine-id` from minirootfs must be rewritten (32 hex) or dbus-broker refuses the session bus.
- Prefer `runuser -u flux` over `su` inside `/data` chroots.
- Same bwrap shim + Adwaita icons as Fedora 43 (glycin-only gdk-pixbuf).
