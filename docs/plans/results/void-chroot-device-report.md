# Void chroot — device report

**Date:** 2026-08-13  
**Package:** `com.ivarna.fluxlinux`  
**Path:** `/data/local/tmp/chrootVoid`

| Step | Result |
|------|--------|
| Rootfs extract | **PASS** |
| Family XFCE (xbps, minimal set) | **PASS** |
| `sudo -n id` as flux | **PASS** `uid=0(root)` |
| xbps-query | **PASS** |
| XFCE on embedded X11 | **PASS** `void_chroot_xfce_pass.png` — Applications panel, Space wallpaper, Home, flux user. PolicyKit dialog is harmless (no systemd). |

## Notes

Incomplete Papirus-Dark (missing `image-missing.svg`) aborted GTK3 until IconThemeName was switched to Adwaita.
