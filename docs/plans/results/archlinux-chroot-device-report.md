# Arch CHROOT — Device Verification Report

- Date: 2026-08-14
- Device: Xiaomi 2311DRK48I (duchamp), serial `Y5WWBMJVOZSK4HU8`
- Card: `archlinux_chroot` → `/data/local/tmp/chrootArch`

## Result matrix

| Cat | # | Result | Evidence |
|-----|---|--------|----------|
| I1 | Install complete | **PASS** | `archlinux_chroot_01_distros.png` … `03_install_done.png` (Landlock fix already in this APK) |
| I2 | `.flux_configured` + startxfce4 | **PASS** | both present |
| I3 | ALARM `$arch/$repo` | **PASS** | `Server = http://mirror.archlinuxarm.org/$arch/$repo` |
| T4 | flux exists; alarm leftover | **PASS after cleanup** | Family created flux uid 1001 and left `alarm` 1000 (usermod rename no-op in this chroot). Script now deletes leftover alarm. Live tree: alarm line removed. Panel user is `flux`. |
| D3/D4 | XFCE | **PASS** | `archlinux_chroot_xfce_pass.png` penguin + Applications + flux |
| D5 | Stop | **PASS** | `archlinux_chroot_stopped.png` |

## Stop retest (2026-08-14 dispatch fix)

D5 **PASS**.

```
FluxLinux: STOP XFCE (chroot mode)
  distro=archlinux_chroot path=/data/local/tmp/chrootArch
FluxLinux: Stopping Chroot XFCE
```

- XFCE roots before Stop: `/data/local/tmp/chrootArch` (pids 14652/14694/14722/14736). After Stop: none.
- Log does not name `chrootDebian13`. Card returned to Start.
- Debian chroot still present. Screenshots: `archlinux_chroot_xfce_retest.png`, `archlinux_chroot_stopped_retest.png`.

## Bug

Chroot `usermod -l flux -m alarm` did not consume alarm; `_flux_ensure_user` then added flux at 1001. Family now deletes leftover alarm and tries `usermod -u 1000 flux` when 1000 is free.
