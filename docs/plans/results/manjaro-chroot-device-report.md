# Manjaro CHROOT — device report

**Date:** 2026-08-13  
**Package:** `com.ivarna.fluxlinux` (Ivarna release, `adb install -r`)  
**Device:** Xiaomi 2311DRK48I, KernelSU  
**Card:** `manjaro_chroot` — `/data/local/tmp/chrootManjaro`  
**Method:** UI (Distro Settings → XFCE4 Desktop / Customization; Terminal User; Home Start / Stop)

## Matrix

| Step | Result |
|------|--------|
| I1 family + startxfce4 | **PASS** — family log `manjaro Manjaro setup complete!`; `/usr/bin/startxfce4` present |
| T1 User shell | **PASS** — Terminal → Chroot → Manjaro → User; sprite + agnosterzak bar |
| T2 `sudo -n id` | **PASS** — `uid=0(root) gid=0(root) groups=0(root)` |
| T3 `sudo pacman -Q pacman` | **PASS** — `pacman 6.0.2-2` (no lock / EACCES) |
| D1–D3 Start → X11 → paint | **PASS** — `com.termux.x11.MainActivity`; xfce4-session / xfwm4 / xfce4-panel / xfdesktop / Thunar / xfsettingsd |
| D4 theme / icons | **PASS** — Space-transparency, Papirus-Dark, Vimix-white-cursors |
| D5 Stop | **PASS** — card back to Start; no xfce processes |
| OMZ | **PASS** — `~/.oh-my-zsh/oh-my-zsh.sh`; `echo $ZSH_THEME` → `agnosterzak` |
| pokemon | **PASS** — sprite on start; `command -v` → `/usr/local/bin/pokemon-colorscripts` |

## Failure that blocked I1 (fixed)

First install aborted at pacman XFCE:

```
error: could not determine cachedir mount point /var/cache/pacman/pkg
error: failed to commit transaction (not enough free disk space)
/tmp/.nc_b64_9736: line 533: sed: command not found
```

`/data` had ~83 GiB free. CheckSpace cannot see Android bind-mount free space. The retry path used `sed -i`, but Manjaro bootstrap has no GNU sed.

**Fix (this APK):** disable CheckSpace with POSIX file rewrite **before** the first `pacman -S`; pin `dbus-broker-units`; install `sed gzip`. Host `setup_guest_chroot.sh` also comments CheckSpace after extract.

## Locale / prompt (fixed)

First User shells printed `prompt_segment:5: character not in range` and hid `@flux` because `locale.gen` was fully commented (no `locale-archive`). `en_US.UTF-8` is now generated; hostname is `manjaro`. Fresh shells show the date bar + `@flux` with no glyph error.

`manjaro_chroot_custom_done.png`, `manjaro_chroot_omz_pokemon.png`, `manjaro_chroot_t2_t3.png`, `manjaro_chroot_xfce_pass.png`, `manjaro_chroot_stopped.png`, `manjaro_chroot_prompt_fixed.png`
