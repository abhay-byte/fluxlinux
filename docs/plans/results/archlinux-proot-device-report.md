# Arch PROOT — Device Verification Report

- Date: 2026-08-14
- Device: Xiaomi 2311DRK48I (duchamp), serial `Y5WWBMJVOZSK4HU8`
- Card: `archlinux` (PROOT), rootfs `archlinux_arm_rootfs.tar.xz` SHA `40209ef6…`

## Result matrix

| Cat | # | Result | Evidence |
|-----|---|--------|----------|
| I1 | Family + customization | **PASS** after Landlock fix | First family run failed: `switching to sandbox user 'alpm'` / Landlock. Fixed `DisableSandbox` + comment `DownloadUser`. Re-ran XFCE4 Desktop + Customization. |
| I2 | startxfce4 | **PASS** | present |
| I3 | ALARM mirrors, not Manjaro | **PASS** | `Server = http://mirror.archlinuxarm.org/$arch/$repo`. `HoldPkg = pacman glibc`. |
| T4 | alarm renamed to flux | **PASS** | `flux:x:1000:1000::/home/flux:/bin/bash` (zsh after custom). No `alarm`. |
| D3/D4 | XFCE | **PASS** | `archlinux_proot_xfce_pass.png` flux + penguin |
| D5 | Stop | **PASS** | `archlinux_proot_stopped.png` |

## Bug fixed

Pacman 7 `DownloadUser = alpm` + Landlock sandbox is unsupported on Android. Family now writes `DisableSandbox` and comments `DownloadUser` before `pacman -Sy`.
