# Chimera chroot — device report

**Date:** 2026-08-13
**Package:** `com.ivarna.fluxlinux` (release APK, `adb install -r`)
**Device:** Xiaomi 2311DRK48I, KernelSU
**Card:** `chimera_chroot` — `/data/local/tmp/chrootChimera`

## Matrix

| Step | Result |
|------|--------|
| I1 family + startxfce4 | **PASS** — `.flux_configured` and `/usr/bin/startxfce4` present |
| T1 user shell | **PASS** — `Deepin`-style tab `Chimera` chroot User; uid flux, `@flux` prompt |
| T2 doas/sudo | **PASS** (prior) — `doas.conf` `-rw-r--r--` `permit nopass flux` |
| T3 apk update | **PASS** (prior proot + doas 0644; family `startxfce4` present) |
| D1–D5 XFCE | **PASS** (re-run 2026-08-13) — Start → `com.termux.x11.MainActivity`; xfce4-session / xfwm4 / xfce4-panel / xfdesktop / Thunar / xfsettingsd; Space-transparency + Papirus-Dark + penguin wallpaper; Stop returns to Start |
| OMZ | **PASS** — `~/.oh-my-zsh/oh-my-zsh.sh` + `agnosterzak.zsh-theme` |
| pokemon | **PASS** — sprite on User shell start |

## OMZ + pokemon (UI)

New User shell (Terminal → Chroot → Chimera → User):

| Check | Result |
|-------|--------|
| prompt | `@flux` agnosterzak |
| `echo $ZSH_THEME` | `agnosterzak` |
| `command -v pokemon-colorscripts` | `/usr/local/sbin/pokemon-colorscripts` |
| sprite | yes |

`chimera_chroot_omz_pokemon.png`

First guest clone failed in 2s because Chimera `timeout` hits `sigaction(32)` on Android. Script now probes timeout and uses a sleep/kill watchdog. After that, Distro Settings / User shell show OMZ + pokemon.

## doas

`/data/local/tmp/chrootChimera/etc/doas.conf` is `-rw-r--r--` with `permit nopass flux`.

## D1–D5 (2026-08-13 later)

Home → Chroot → Chimera (Rooted) → Start → Launch XFCE4. Log: Pulse + termux-x11 + X server. Desktop paints (no failsafe). `xsettings.xml`: ThemeName=Space-transparency, IconThemeName=Papirus-Dark, CursorThemeName=Vimix-white-cursors. Stop → card Start; no xfce processes.

`chimera_chroot_xfce_pass.png`, `chimera_chroot_stopped.png`
