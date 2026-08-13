# XFCE icons + Fedora locale + fastfetch — device notes

**Date:** 2026-08-13  
**Device:** Xiaomi 2311DRK48I `Y5WWBMJVOZSK4HU8`  
**Package:** `com.ivarna.fluxlinux` Ivarna release (`adb install -r`)

## What was fixed in the APK

- Shared `setup_customization_xfce.sh` no longer hardcodes `SEL_ICON=Adwaita` on dnf/zypper.
- `_flux_ensure_dir` replaces dangling Papirus-Dark `status` / `@2x` / `categories` symlinks before `mkdir`/`cp` (the `File exists` / `No such file` lines).
- Category extras `papirus-xfce-categories.tar.xz` + rebuilt stub with `applications-internet` etc.
- Fastfetch preset always overwritten: `folders: ["/"]`, no `(pacman)`.
- `.zshrc` uses `locale -a` / `C.UTF-8` fallback. Fedora family + customization install `glibc-langpack-en`.
- `papirus-icon-theme` (~100 MiB) is only pulled if the category gate still fails.

## Repair-path checks (existing guests)

| Guest | en_US | no setlocale | 1× DSK | PKG not (pacman) | IconThemeName | applications-internet | settings.ini |
|-------|-------|--------------|--------|------------------|---------------|----------------------|--------------|
| fedora proot | **PASS** `en_US.utf8` | **PASS** | **PASS** | **PASS** `459 (rpm)` | Papirus-Dark | present | Papirus-Dark |
| fedora chroot | **PASS** | **PASS** | no DSK line (not /apex flood) | **PASS** `472 (rpm)` | Papirus-Dark | present | Papirus-Dark |
| opensuse proot | **PASS** | **PASS** | **PASS** | **PASS** `407 (rpm)` | Papirus-Dark | present | Papirus-Dark |
| opensuse chroot | en_US.utf8 on disk | — | folders in jsonc | — | Papirus-Dark | present | Papirus-Dark |
| manjaro proot | locale-archive + zshrc locale-a | — | folders in jsonc | no (pacman) | Papirus-Dark | present | Papirus-Dark |
| manjaro chroot | locale-archive; customization wrote zshrc | — | folders in jsonc | — | Papirus-Dark | present | Papirus-Dark |
| deepin proot | locale-archive | — | folders | — | Papirus-Dark | present | Papirus-Dark |
| void proot/chroot | locale-archive | — | folders | — | Papirus-Dark | present | Papirus-Dark |

Deepin proot smoke: `sudo -n id` as flux → `uid=0(root)`.

## XFCE Applications menu (I3)

Manjaro proot Start → Launch XFCE4 reached `termux.x11` and `startxfce4=READY`, then the **guest session died** on `/tmp` dbus bind (`Permission denied` under `--shared-tmp`). Desktop never painted, so **I3 (no ⊘ in the Applications menu) was not visually confirmed**. Category SVGs are on disk for all matrix guests.

## Fresh install (plan §5.2)

**Not run.** Fedora proot was repaired in place instead of wipe+reinstall. A clean onboarding of fedora/opensuse/manjaro proot is still required before claiming new-install D4.

## Screenshots

- `docs/plans/results/xfce_icons_manjaro_proot.png` — GUI log (session ended)
- `docs/plans/results/xfce_icons_manjaro_menu.png` — Home after return
