# Deepin chroot — device report

**Date:** 2026-08-13
**Package:** `com.ivarna.fluxlinux` 1.8.0 (versionCode 10)
**Device:** 2311DRK48I (Android 16), KernelSU root
**Card:** `deepin_chroot` "Deepin (Rooted)" — path `/data/local/tmp/chrootDeepin`, theme choice **Dark (default)**

| Step | Result |
|------|--------|
| I1 | **PASS** — root-check (`✓ Root OK`), rootfs extract, family apt install (`FluxLinux: deepin Deepin setup complete!`, `✓ XFCE installed`), "Install complete" 100%. `.flux_configured` and `usr/bin/startxfce4` present at `/data/local/tmp/chrootDeepin`. Chroot count 5→6. See BUG-1 (customization aborts). |
| T1 | **PASS** — Terminal → CHROOT tab → Deepin → User opens `Deepin Chroot Shell` (`fluxlinux_chroot.sh login --user flux --shell zsh`, zsh). `id` → `uid=1000(flux) gid=1000(flux) groups=1000(flux),29(audio),44(video),100(users),3003(aid_inet),3005(wheel),3006(input),3007(netdev)`. `deepin_chroot_shell_user.png` |
| T2 | **PASS** — `sudo -n id` → `uid=0(root) gid=0(root) groups=0(root),3003(aid_inet)` (NOPASSWD, no prompt). `deepin_chroot_shell_root.png` |
| T3 | **PASS** — `sudo apt-get update` from the chroot user shell → `Hit:1 https://community-packages.deepin.com/beige crimson InRelease`, `Reading package lists...`, `EXITCODE=0`. No lock/EACCES. `deepin_chroot_apt.png` |
| D1–D3 | **PASS** — Start → Launch XFCE4 → embedded X11 (`com.termux.x11.MainActivity`) → desktop paints: xfce4-session/xfwm4/xfce4-panel/xfdesktop/xfsettingsd, no failsafe, no first-launch SIGSYS crash (retry not needed). GUI log: `FluxLinux: X server PID=19091`, `FluxLinux: host X0 socket ready`, `FluxLinux(guest): GPU mode=virgl` + `software GL fallback`. `deepin_chroot_xfce_pass.png` |
| D4 | **PASS** — after customization re-run (Distro Settings → XFCE4 Customization → Dark), 4 archives staged to chroot guest `/tmp/flux_xfce_assets/` (root copy). `xsettings.xml`: `ThemeName=Space-transparency`, `IconThemeName=Papirus-Dark`, `CursorThemeName=Vimix-white-cursors`, Nerd Font + `WindowScalingFactor=2`; live session verified via `xfconf-query` on the running dbus. `Papirus-Dark/index.theme` present, `Vimix-white-cursors/cursors` = 111 entries (>50). Wallpaper `fluxlinux-dark.png` applied in `xfce4-desktop.xml`. `deepin_chroot_xfce_theme.png` |
| D5 | **PASS** — Stop → `STOP XFCE (chroot mode)`, all xfce/X11 processes gone, card returns to Start (only the terminal-shell chroot session remains, expected). `deepin_chroot_stopped.png` |

## Bugs

- **BUG-1 (fresh install): chroot customization has no theme/icon asset staging.** `OnboardingInstallRunner.runChroot` ran `setup_customization_xfce.sh` in the guest with no `FLUX_ASSET_DIR` staging and no `FLUX_SKIP_THEME_ICONS`; guest `/tmp/flux_xfce_assets` did not exist and icons had no download fallback. Reproduced exactly:
  ```
  FluxLinux: Installing icons Papirus-Dark...

  FluxLinux Error: Script failed at step: Icons Archive Missing
  ---------------------------------------------------
  ```
  **FIXED** — onboarding chroot flow now stages theme/icon/cursor/wallpaper archives into the chroot guest `/tmp/flux_xfce_assets` (root copy) before customization runs; icons branch gained an `icons.zip` download fallback from the fluxlinux release (`ProotXfceAssetInstaller`). Verified on re-run: all 4 archives staged in `/data/local/tmp/chrootDeepin/tmp/flux_xfce_assets/`.
- **BUG-2 (retry path): Distro Settings → XFCE4 Customization → Install staged into the *proot* container and set `FLUX_SKIP_THEME_ICONS=1` for the chroot guest** (`MainActivity.stageCustomizationHostEnv` used `profile.prootName`; `ProotXfceAssetInstaller` targeted the proot rootfs). The chroot guest skipped extraction → `Papirus-Dark incomplete — using Adwaita icons` fallback, and `CursorThemeName=Vimix-white-cursors` was written even though the cursor theme was not in the chroot.
  **FIXED** — component re-run for a chroot card now stages into the CHROOT `/tmp` (not the sibling proot container) and no longer sets `FLUX_SKIP_THEME_ICONS` for chroots. Verified: Papirus-Dark extracted into chroot, Vimix-white-cursors installed (111 cursors), D4 fully PASS.
- **NOTE (benign):** `FluxLinux(guest): software GL fallback` (no VirGL host socket content in chroot mode on this device) and standard xkbcomp/dbind warnings; PulseAudio start line present — audio out of scope.

## OMZ + pokemon (UI, 2026-08-13 later)

New User shell (Terminal → Chroot → Deepin → User), tab `Deepin Chroot`:

| Check | Result |
|-------|--------|
| prompt | `@flux` agnosterzak |
| `echo $ZSH_THEME` | `agnosterzak` |
| `command -v pokemon-colorscripts` | `/usr/local/bin/pokemon-colorscripts` |
| sprite | yes (startup) |

`deepin_chroot_omz_pokemon.png`

## Verification commands (as root)

- `su -c 'ls /data/local/tmp/chrootDeepin/.flux_configured /data/local/tmp/chrootDeepin/usr/bin/startxfce4'`
- `su -c 'cat /data/local/tmp/chrootDeepin/home/flux/.config/xfce4/xfconf/xfce-perchannel-xml/xsettings.xml'`
- GUI log: `/data/data/com.ivarna.fluxlinux/cache/gui_desktop.log`

(End of file - total 20 lines)
