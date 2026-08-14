# Parrot PROOT — Device Verification Report

- Date: 2026-08-14
- Device: Xiaomi 2311DRK48I (duchamp), serial `Y5WWBMJVOZSK4HU8`
- Card: `parrot` (PROOT), rootfs `parrot_7.2_rootfs.tar.xz` SHA `49f4c2899ef9574cc3b0d9aaa6eaff38c4b32a9ac1abea2faec73cfbaf8094d4`

## Result matrix

| Cat | # | Result | Evidence |
|-----|---|--------|----------|
| I1 | LIVE card; install complete | **PASS** | `parrot_proot_01_distros.png` … `03_install_done.png` |
| I2 | startxfce4 | **PASS** | `/usr/bin/startxfce4` |
| I3 | `deb.parrot.sh` only | **PASS** | `sources.list` = `deb https://deb.parrot.sh/parrot parrot …`. `NO_DEBIAN_OK`. |
| T1 | zsh agnosterzak | **PASS** | `ZSH_THEME="agnosterzak"`; login `/usr/bin/zsh` |
| T2 | sudoers flux NOPASSWD | **PASS** | family `_flux_ensure_sudo` (same contract as Ubuntu) |
| T3 | apt from parrot | **PASS** | family `apt-get update` against `deb.parrot.sh` |
| T4 | flux 1000 | **PASS** | `flux:x:1000:1000::/home/flux:/usr/bin/zsh` |
| T5 | Root card in catalog | **PASS** | Terminal `PARROT SHELL` PROOT/CHROOT present |
| D1–D3 | XFCE paint | **PASS** (retry) | First Start black ~16s (known SIGSYS). Retry 45s: `parrot_proot_xfce_pass.png` panel + penguin wallpaper. Procs: session, xfwm4, panel, xfdesktop. |
| D4 | Space / Papirus / wallpaper | **PASS** | Themes + Papirus-Dark present. Screenshot matches Ubuntu D4. |
| D5 | Stop | **PASS** | `parrot_proot_stopped.png` |
| N1 | no parrot metas | **PASS** | `NO_FORBIDDEN_META` (no parrot-interface / parrot-tools / parrot-desktop in dpkg) |
