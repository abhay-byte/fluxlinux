# Fedora proot — device report

**Date:** 2026-08-13  
**Package:** `com.ivarna.fluxlinux` (ivarna release)  
**Device:** Xiaomi 2311DRK48I  

## Results

| # | Step | Result |
|---|------|--------|
| I1 | Distros card + wizard copy (Fedora 43 / dnf / Mesa) | **PASS** (`fvo_03_fedora_config.png`) |
| I2 | Rootfs extract via `flux_install.sh` | **PASS** (SHA256 OK) |
| I3 | Family XFCE (`dnf5`) | **PASS** with fixes (see below) |
| T2 | `sudo -n id` as flux | **PASS** after sudoers mode fix (`uid=0(root)`) |
| D2 | Embedded X11 `com.termux.x11.MainActivity` | **PASS** |
| D3 | XFCE paints (panel, desktop icons, no failsafe) | **PASS** (`fedora_proot_xfce_pass.png`) |
| D4 | Space theme + wallpaper | **PASS** (`fedora_proot_xfce_theme.png`) — Space wallpaper, Adwaita icons (Papirus SVG aborts GTK/glycin). |

## Bugs found and fixed

1. **`flux_install.sh` b64 decode** wrote `$PREFIX/tmp/flux_setup_temp.sh` → Permission denied. Decode to `$HOME` then stage into shared tmp; use `/system/bin/base64`.
2. **Chroot extract** used toybox `tar` (no xz). Now `$BB tar xJf`.
3. **Family helpers missing** on component install. Common functions inlined into family scripts.
4. **`dnf5` hung** on `%triggerin gnome-icon-theme` under proot. Family now uses `tsflags=nodocs,noscripts` + `SYSTEMD_OFFLINE=1`.
5. **`chown flux:flux` (uid 1000)** made `/home/flux` unwritable to the app uid under proot. Only apply `flux:flux` when `/home` owner is 0 (real chroot).
6. **Stale X11 socket** left `termux-x11` running → unix bind fail. `start_gui.sh` now removes `.X0-lock` / `X0` after pkill.
7. **Theme tar 0600** in `flux_xfce_assets` → extract Permission denied. Customization copies archive to `/var/tmp` and chmod 644.
8. **sudoers.d world-writable** → sudo refused drop-in. chmod 755/440 in `_flux_ensure_sudo`.

## Screenshot

`docs/plans/results/fedora_proot_xfce_pass.png` — XFCE panel (Applications), user `flux`, desktop icons, embedded X11.
