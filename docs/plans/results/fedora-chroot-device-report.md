# Fedora chroot — device report

**Date:** 2026-08-13  
**Package:** `com.ivarna.fluxlinux`  
**Path:** `/data/local/tmp/chrootFedora`

| Step | Result |
|------|--------|
| Rootfs extract | **PASS** |
| Family XFCE (dnf5, noscripts) | **PASS** |
| `sudo -n id` as flux | **PASS** `uid=0(root)` |
| dnf5 present | **PASS** `/usr/sbin/dnf5` |
| XFCE on embedded X11 | **PASS** `fedora_chroot_xfce_pass.png` — Applications panel, Space wallpaper, Home/Trash, flux user |

## Fixes

- Native aarch64 bwrap shim (not first-path shell): glycin `--ro-bind` sources must not be exec'd.
- `glib-compile-schemas` after `tsflags=noscripts`.
- `gdk-pixbuf-query-loaders-64 --update-cache`.
- Adwaita icons (Papirus incomplete 48x48@2x).
