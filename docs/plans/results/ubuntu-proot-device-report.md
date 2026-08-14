# Ubuntu PROOT — Device Verification Report

- Date: 2026-08-14
- Device: Xiaomi 2311DRK48I (duchamp), Android 16, KernelSU, serial `Y5WWBMJVOZSK4HU8`
- App: `com.ivarna.fluxlinux` Ivarna release (`adb install -r` of `app/build/outputs/apk/ivarna/release/app-ivarna-release.apk`, 867 MiB)
- Card: `ubuntu` (PROOT), rootfs `ubuntu_26.04_rootfs.tar.xz` SHA `e648a5302dd273c476e5658e652f88d1e66ece69b487431521c5caef4b960efc`
- Theme: Dark (default)

## Result matrix

| Cat | # | Result | Evidence |
|-----|---|--------|----------|
| I1 | Distros card LIVE (not Coming Soon); wizard Dark → Install complete | **PASS** | `ubuntu_proot_01_distros.png`, `ubuntu_proot_02_installing.png`, `ubuntu_proot_03_install_done.png`. Log: `SHA256 OK`, `ubuntu Ubuntu setup complete!`, `XFCE4 Customization complete!`, `✓ Customization done`, `Install complete`. |
| I2 | `startxfce4` present | **PASS** | `/usr/bin/startxfce4`; `STARTXFCE4_OK` |
| I3 | Repos: `ports.ubuntu.com` only | **PASS** | `ubuntu.sources` URIs both `http://ports.ubuntu.com/ubuntu-ports`. `NO_ARCHIVE_OK` (no `archive.ubuntu.com`). |
| T1 | User = flux, zsh, agnosterzak, pokemon | **PASS** | `ubuntu_proot_shell_user.png`. `.zshrc` has `ZSH_THEME="agnosterzak"`; `~/.oh-my-zsh`; `/usr/local/bin/pokemon-colorscripts`; login shell `/usr/bin/zsh`. |
| T2 | `sudo -n id` → uid 0 | **PASS** | In-app User shell: `uid=0(root)` (benign `unable to send audit message`). |
| T3 | `apt-get update` / ports lists | **PASS** | `ubuntu_proot_pm.png`. Guest apt lists under `ports.ubuntu.com_ubuntu-ports_dists_resolute*`. |
| T4 | `id -un` is flux uid 1000 | **PASS** | `uid=1000(flux)`; `grep ^flux /etc/passwd` → `flux:x:1000:1000::/home/flux:/usr/bin/zsh`. |
| T5 | Root card `id` | **PASS** | `ubuntu_proot_shell_root.png`: `uid=0(root)` `whoami=root`. |
| D1 | Start XFCE; Pulse/X/startxfce4 in log | **PASS** | `gui_desktop.log`: `X server PID=18686`, `startxfce4=READY`. `ubuntu_proot_gui_log.png`. |
| D2 | Embedded X11 resumed | **PASS** | `com.ivarna.fluxlinux/com.termux.x11.MainActivity` topResumed. |
| D3 | Paint: panel + wallpaper, no failsafe | **PASS** | `ubuntu_proot_xfce_pass.png`. Processes: `xfce4-session`, `xfwm4`, `xfce4-panel`, `xfdesktop`. |
| D4 | Space + Papirus + Vimix + Nerd + wallpaper | **PASS** | xfconf: `ThemeName=Space-transparency`, `IconThemeName=Papirus-Dark`, `CursorThemeName=Vimix-white-cursors`, `FontName=JetBrainsMono Nerd Font 10`, `WindowScalingFactor=2`; `last-image=…/fluxlinux-dark.png`. Category icons present (`applications-accessories/internet/system.svg`). Screenshot shows penguin wallpaper + XFCE panel `Applications` / `flux`. |
| D5 | Stop → Start; xfce gone | **PASS** | `ubuntu_proot_stopped.png`. Card shows Start. `ps` after stop: NONE. |
| N1 | n/a (Ubuntu) | — | — |
| N2 | OpenBSD not packaged | **PASS** | APK `unzip -l` has no openbsd / no `.tar.gz`. |

## Notes

- Host `tar: exec xz: Permission denied` during staging is the known non-blocker. Guest `xz-utils` extracted themes/icons/fonts.
- First X SIGSYS did not block; session painted on first Start.
- VirGL socket missing → llvmpipe fallback (logged). Desktop still painted.
- Raw `adb` `proot-distro login --user flux` reports sudo ownership `10301` (app uid). In-app Terminal User session maps correctly — T2 uses the product path.

## Bugs found

None that blocked I1–D5. No family/customization abort.
