# Kali CHROOT — Device Verification Report

- Date: 2026-08-14
- Device: Xiaomi 2311DRK48I (duchamp), serial `Y5WWBMJVOZSK4HU8`
- Card: `kali_chroot` → `/data/local/tmp/chrootKali`

## Result matrix

| Cat | # | Result | Evidence |
|-----|---|--------|----------|
| I1 | Install complete | **PASS** | `kali_chroot_01_distros.png` … `03_install_done.png` |
| I2 | `.flux_configured` + startxfce4 | **PASS** | both present |
| I3 | kali-rolling | **PASS** | `kali.sources` Suites: kali-rolling |
| T4 | flux=1000, kali leftover 100000 | **PASS** | both users present as specified |
| D3/D4 | XFCE | **PASS** | `kali_chroot_xfce_pass.png` flux + penguin |
| D5 | Stop | **PASS** | `kali_chroot_stopped.png` |

## Stop retest (2026-08-14 dispatch fix)

D5 **PASS**.

```
FluxLinux: STOP XFCE (chroot mode)
  distro=kali_chroot path=/data/local/tmp/chrootKali
FluxLinux: Stopping Chroot XFCE
```

- XFCE roots before Stop: `/data/local/tmp/chrootKali` (pids 13413/13439/13459/13473). After Stop: none.
- Log does not name `chrootDebian13`. Card returned to Start.
- Debian chroot still present (`startxfce4`). Screenshots: `kali_chroot_xfce_retest.png`, `kali_chroot_stopped_retest.png`.
