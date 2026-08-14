# Kali PROOT — Device Verification Report

- Date: 2026-08-14
- Device: Xiaomi 2311DRK48I (duchamp), serial `Y5WWBMJVOZSK4HU8`
- Card: `kali` (PROOT), rootfs `kali_2026_2_rootfs.tar.xz` SHA `01c48a29ebb543954ef200e766076a143cf42744760d7ccdc31683a19f670689`

## Result matrix

| Cat | # | Result | Evidence |
|-----|---|--------|----------|
| I1 | Install complete | **PASS** | `kali_proot_01_distros.png` … `03_install_done.png` |
| I2 | startxfce4 | **PASS** | present |
| I3 | kali-rolling only | **PASS** | `kali.sources` Suites: kali-rolling; no debian.org |
| T1 | zsh agnosterzak | **PASS** | `ZSH_THEME="agnosterzak"` |
| T4 | flux uid 1000, not kali | **PASS** | flux=1000; leftover `kali` uid **100000** left in place |
| D3/D4 | XFCE | **PASS** | `kali_proot_xfce_pass.png` panel `flux` + penguin wallpaper |
| D5 | Stop | **PASS** | `kali_proot_stopped.png` |
| N1 | no kali-desktop-xfce | **PASS** | `NO_FORBIDDEN_META` |
