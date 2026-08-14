# Parrot CHROOT — Device Verification Report

- Date: 2026-08-14
- Device: Xiaomi 2311DRK48I (duchamp), serial `Y5WWBMJVOZSK4HU8`
- Card: `parrot_chroot` → `/data/local/tmp/chrootParrot`

## Result matrix

| Cat | # | Result | Evidence |
|-----|---|--------|----------|
| I1 | Install complete | **PASS** | `parrot_chroot_01_distros.png` … `03_install_done.png` |
| I2 | `.flux_configured` + startxfce4 | **PASS** | both present |
| I3 | `deb.parrot.sh` | **PASS** | `sources.list` parrot suite |
| T1/T4 | flux zsh agnosterzak | **PASS** | uid 1000, `ZSH_THEME="agnosterzak"` |
| D3/D4 | XFCE paint | **PASS** | `parrot_chroot_xfce_pass.png` panel + penguin + flux |
| D5 | Stop | **PASS** | `parrot_chroot_stopped.png` |

## Stop retest (2026-08-14 dispatch fix)

D5 **PASS**.

```
FluxLinux: STOP XFCE (chroot mode)
  distro=parrot_chroot path=/data/local/tmp/chrootParrot
FluxLinux: Stopping Chroot XFCE
```

- XFCE roots before Stop: `/data/local/tmp/chrootParrot` (pids 14140/14168/14179/14190). After Stop: none.
- Log does not name `chrootDebian13`. Card returned to Start.
- Debian chroot still present. Screenshots: `parrot_chroot_xfce_retest.png`, `parrot_chroot_stopped_retest.png`.
