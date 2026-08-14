# Ubuntu CHROOT — Device Verification Report

- Date: 2026-08-14
- Device: Xiaomi 2311DRK48I (duchamp), Android 16, KernelSU, serial `Y5WWBMJVOZSK4HU8`
- App: `com.ivarna.fluxlinux` Ivarna release (`adb install -r`)
- Card: `ubuntu_chroot` → `/data/local/tmp/chrootUbuntu`
- Theme: Dark

## Result matrix

| Cat | # | Result | Evidence |
|-----|---|--------|----------|
| I1 | Live card; Root OK; family + customization complete | **PASS** | `ubuntu_chroot_01_distros.png`, `ubuntu_chroot_02_installing.png`, `ubuntu_chroot_03_install_done.png`. Log: `✓ Root OK`, Papirus categories, OMZ, `Install complete`. |
| I2 | `.flux_configured` + `startxfce4` | **PASS** | Both present under `/data/local/tmp/chrootUbuntu`. |
| I3 | ports.ubuntu.com only | **PASS** | `ubuntu.sources` URIs `http://ports.ubuntu.com/ubuntu-ports`; no `archive.ubuntu.com`. |
| T1 | zsh / agnosterzak / pokemon | **PASS** | `ZSH_THEME="agnosterzak"`; `~/.oh-my-zsh`; login shell `/usr/bin/zsh`. |
| T2 | NOPASSWD sudo for flux | **PASS** | `/etc/sudoers.d/flux` = `flux ALL=(ALL) NOPASSWD:ALL`. |
| T3 | apt lists from ports | **PASS** | Family `apt-get update` against ports.ubuntu.com succeeded (install log). |
| T4 | flux uid 1000 | **PASS** | `flux:x:1000:1000::/home/flux:/usr/bin/zsh`. |
| T5 | Root session available | **PASS** | Chroot Start dialog offers Open Root Shell; chroot helper is root. |
| D1 | startxfce4 / X PID | **PASS** | Processes `xfce4-session`, `xfwm4`, `xfce4-panel`, `xfdesktop` (system uid). |
| D2 | X11 resumed | **PASS** | `com.termux.x11.MainActivity` topResumed. |
| D3 | Paint panel + wallpaper | **PASS** | `ubuntu_chroot_xfce_pass.png` — same Space desktop as proot, no failsafe. |
| D4 | Theme/icons/font/wallpaper | **PASS** | xfconf ThemeName/IconThemeName/CursorThemeName Space / Papirus-Dark / Vimix. Wallpaper `fluxlinux-dark.png`. Screenshot: penguin + Applications + flux. |
| D5 | Stop → Start | **PASS** | `ubuntu_chroot_stopped.png`. xfce procs NONE. |

Chroot count 8 → 9. Existing 8 guests untouched.

## Stop retest (2026-08-14 dispatch fix)

D5 **PASS** (real dispatch, not host `pkill` / state flip only).

```
=== STOP method=chroot script=stop_gui_chroot.sh ===
FluxLinux: STOP XFCE (chroot mode)
  distro=ubuntu_chroot path=/data/local/tmp/chrootUbuntu
FluxLinux: Stopping Chroot XFCE
[1/4] Kill XFCE in chroot...
```

- Log path is `/data/local/tmp/chrootUbuntu`, not `chrootDebian13`.
- XFCE pids (10687 session, 10709 xfwm4, 10729 panel, 10742 xfdesktop) had `readlink /proc/<pid>/root` = `/data/local/tmp/chrootUbuntu` before Stop; **NONE** after.
- Card returned to Start. Screenshots: `ubuntu_chroot_xfce_retest.png`, `ubuntu_chroot_stopped_retest.png`.
- Debian intact: `/data/local/tmp/chrootDebian13` present + `usr/bin/startxfce4`. No Debian binds were mounted before this Stop (`NO_DEBIAN_MOUNTS`). Sibling Start after this Stop painted (`ukpa_stopfix_debian_chroot_smoke.png`).
