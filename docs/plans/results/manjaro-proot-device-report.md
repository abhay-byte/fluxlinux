# Manjaro PROOT — device report

**Date:** 2026-08-13  
**Package:** `com.ivarna.fluxlinux` (Ivarna release, `adb install -r`)  
**Device:** Xiaomi 2311DRK48I  
**Card:** `manjaro` — Manjaro ARM XFCE (proot)  
**Method:** UI only

## Matrix

| Step | Result |
|------|--------|
| I1 install to completion | **PASS** — 100% Environment ready; family + customization |
| T1 user shell | **PASS** — Terminal → Manjaro PROOT → User |
| T2 `sudo -n id` | **PASS** — `uid=0(root)` (+ Android aid groups via proot binds) |
| T3 `sudo pacman -Q pacman` | **PASS** — `pacman 6.0.2-2` |
| D1–D3 Start → X11 → paint | **PASS** — `com.termux.x11.MainActivity`; xfce4-session / xfwm4 / xfce4-panel / xfdesktop / Thunar / xfsettingsd |
| D4 theme / icons | **PASS** — Space-transparency dark + penguin wallpaper + Papirus panel icons |
| D5 Stop | **PASS** — card returns to Start; xfce processes gone |
| OMZ | **PASS** — `echo $ZSH_THEME` → `agnosterzak` |
| pokemon | **PASS** — sprite; `/usr/local/bin/pokemon-colorscripts` |

## Prompt locale

First shells showed `prompt_segment:5: character not in range` (no `en_US.UTF-8` locale-archive). After `localedef` + `/etc/hostname=manjaro`, fresh **Manjaro Shell** paints `@flux` with no glyph error.

`manjaro_proot_install_done.png`, `manjaro_proot_omz_pokemon.png`, `manjaro_proot_t2_t3.png`, `manjaro_proot_xfce_pass.png`, `manjaro_proot_stopped.png`
