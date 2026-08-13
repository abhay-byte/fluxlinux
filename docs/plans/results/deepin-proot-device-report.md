# Deepin PROOT — Device Verification Report

- Date: 2026-08-13
- Device: Xiaomi 2311DRK48I (duchamp), Android 16, KernelSU root, serial Y5WWBMJVOZSK4HU8
- App: com.ivarna.fluxlinux (freshly reinstalled via `adb install -r`)
- Card: `deepin` (PROOT), rootfs `deepin_25_rootfs.tar.xz`, apt-based XFCE, theme choice: **Dark (default)**
- Method: UI-driven via uiautomator dumps + `input tap/text`, corroborated by `adb shell su` filesystem/process inspection and `adb logcat`

## Result matrix

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| I1 | Distros list shows live Deepin card (not "Coming Soon"); install wizard → progress → success | **PASS (with customization errors, see bugs)** | `deepin_proot_distros.png` (card with Install button), `deepin_proot_installing.png`, `deepin_02_install_done.png` ("Install complete" panel), `deepin_03_installed_card.png` (card in Installed Distros, PRoot count 5→6) |
| T1 | Terminal tab DEEPIN SHELL section (PROOT); user shell prompt; zsh (oh-my-zsh) | **PASS after re-run** — zsh prompt works; oh-my-zsh now installed (agnosterzak) | Session tab titled "Deepin Shell" (no literal "DEEPIN SHELL" header in UI tree; Terminal list shows row `Deepin PROOT User Root`). proot cmdline: `/bin/zsh -c ... exec /bin/zsh -l`. After customization re-run: `~flux/.oh-my-zsh` present, fresh shell `echo $ZSH_THEME` → `agnosterzak`. `deepin_proot_shell_user.png` |
| T2 | `id`, then `sudo -n id` (expect uid=0) | **PASS** | `id` → `uid=1000(flux) gid=1000(flux) groups=1000(flux),3003(aid_inet),9997(aid_everybody),20301(aid_u0_a301_cache),50301(aid_all_a301)`; `sudo -n id` → `uid=0(root) gid=0(root) groups=0(root),3003,9997,20301,50301`. sudoers: `flux ALL=(ALL) NOPASSWD:ALL`. Screenshots: `deepin_proot_shell_user.png`, `deepin_proot_shell_root.png` |
| T3 | `sudo apt-get update` — no lock EACCES / repo errors | **PASS** | Output: `Hit:1 https://community-packages.deepin.com/beige crimson InRelease`, `Reading package lists...`, `EXITCODE=0`. No lock errors (lock files exist but 0 bytes). Benign: `sudo: unable to send audit message: Operation not permitted` (proot audit noise). `deepin_proot_apt.png` |
| D1-D3 | Start (desktop) → Pulse/X server PID/startxfce4 log lines → termux-x11 opens → desktop paints (panel + wallpaper, no failsafe) | **PASS** (no PulseAudio process — see bugs) | gui_desktop.log: `FluxLinux: X server PID=6987`, `FluxLinux: startxfce4=READY`, `FluxLinux(guest): GPU mode=virgl`, `VirGL socket missing — llvmpipe fallback`, `/usr/bin/startxfce4` exec via `dbus-run-session`. `com.ivarna.fluxlinux/com.termux.x11.MainActivity` opened (focused window). Session stack alive: xfce4-session → xfwm4, xfce4-panel, xfdesktop, xfsettingsd (no failsafe dialog, no xterm). `deepin_proot_xfce_pass.png`, `deepin_07_gui_log.png` |
| D4 | GTK theme Space (dark) + wallpaper; icons Papirus→Adwaita acceptable | **PASS (final)** — Space-transparency dark + fluxlinux-dark.png wallpaper + Vimix-white cursors + **Papirus-Dark icons** | Final state after icon-seeding fix re-run: `xsettings.xml` → `ThemeName=Space-transparency`, `IconThemeName=Papirus-Dark`, `CursorThemeName=Vimix-white-cursors`; `Papirus-Dark/48x48@2x/status/image-missing.svg` exists (1331 B, seeded from Adwaita; the dangling `48x48@2x → 48x48` symlink was removed and replaced with a real seeded dir). `xfce4-desktop.xml` `last-image=/home/flux/Pictures/Wallpapers/fluxlinux-dark.png`. `deepin_proot_xfce_pass.png`, `deepin_proot_xfce_theme.png` |
| D5 | Stop on card → returns to Start state | **PASS** | Tapped Stop on RUNNING card; xfce4-session/xfwm4/termux-x11 processes gone (only terminal-shell proot remains); card shows Start/Logs again. `deepin_08_stopped.png` |

## Re-run after fixes (new APK, same existing deepin container)

Procedure: Distros → Deepin card → Settings (gear) → Features & Components → "XFCE4 Customization" → Install → theme dialog → Dark Mode (default) → Apply Theme. The component runs as a visible terminal session ("Install XFCE4 Customization").

Results of the re-run:
- **T1 fix verified**: `~flux/.oh-my-zsh/` installed (`oh-my-zsh.sh`, `custom/themes/agnosterzak.zsh-theme`, plugins `zsh-autosuggestions` + `zsh-syntax-highlighting`); `.zshrc` has `ZSH_THEME="agnosterzak"`. Fresh user shell: `echo $ZSH_THEME` → `agnosterzak`. `deepin_proot_shell_user.png`.
- **D4 fix verified**: `/usr/share/icons/Papirus-Dark/index.theme` exists (`Name=Papirus-Dark`, `Inherits=breeze-dark,hicolor`); `/usr/share/icons/Vimix-white-cursors/cursors/` populated (111 files); `xsettings.xml` exists with `ThemeName=Space-transparency`, `CursorThemeName=Vimix-white-cursors`, JetBrainsMono Nerd fonts, `WindowScalingFactor=2`. Wallpaper: `~flux/Pictures/Wallpapers/fluxlinux-dark.png` + `xfce4-desktop.xml last-image` pointing to it.
- **Icons fix (final round)**: on the latest re-run the script removed the dangling `48x48@2x → 48x48` symlink and seeded `status/image-missing.svg` from Adwaita; `IconThemeName=Papirus-Dark` now sticks (previously the GTK3-safety guard had fallen back to Adwaita).
- **Caveat found during re-run (existing containers)**: the first re-run attempt failed at icon extraction because the container (family-installed with the pre-fix script) had no `xz` binary (`/usr/bin/xz: No such file or directory`) while the new customization uses `tar -xJf` for `.tar.xz`. After `sudo apt-get install -y xz-utils` in the DEEPIN SHELL and re-running the component, everything completed. Fresh installs are covered by the new family script (installs xz-utils), but the standalone customization component does not install xz itself — worth adding if customization re-run on old containers must be self-sufficient.
- Desktop re-start after re-run: X server PID=9831, xfce4-session/xfwm4/xfsettingsd/xfce4-panel/xfdesktop all up, no failsafe, no plugin-removal spam in the new gui_desktop.log. Stop returned the card to Start state. `deepin_proot_xfce_pass.png` (painted dark desktop), `deepin_proot_xfce_theme.png`.

## Bugs found and fixed (release vs previous findings)

- (a) **aapt2 stripped the Papirus asset** — previously the `.tar.gz` asset was auto-decompressed to `.tar` inside the APK, so `find_asset "papirus-dark-only.tar.gz"` found nothing → `FluxLinux Error: Script failed at step: Icons Archive Missing` (old BUG-1). Fixed: asset now shipped as `papirus-dark-only.tar.xz` (`ICON_TAR="papirus-dark-only.tar.xz"`, `tar -xJf` path). Verified: `FluxLinux: Host theme (deepin): Staged papirus-dark-only.tar.xz from app assets`.
- (b) **Deepin family script now installs xz-utils** (guest-side tar extract safety). Verified: `xz-utils 5.4.5-0.3deepin1` installs cleanly on this container; guest `tar -xJf` extraction of all three archives succeeds.
- (c) **Papirus @2x dangling-symlink icon fallback** — the reduced Papirus-Dark tree shipped dangling `48x48@2x → 48x48` symlinks (with `48x48` absent), so the GTK3-safety guard fell back to Adwaita icons (D4). Fixed: the customization script now removes dangling symlinks/placeholder files before seeding `image-missing.svg` from Adwaita. Verified on final re-run: `Papirus-Dark/48x48@2x` is a real directory containing `status/image-missing.svg` (1331 B), and `xsettings.xml` → `IconThemeName=Papirus-Dark`. Host staging also now skips already-installed assets (`Themes/icons already installed (Space-transparency + Papirus-Dark) — skip extract`, `Oh My Zsh already installed — skip`).
- Also fixed by the earlier re-run: theme apply (xsettings.xml + xfwm4 theme), wallpaper install, cursor install, and oh-my-zsh install (old BUG-3) — all previously skipped because the script aborted at the icons step.

## OMZ + pokemon (UI, 2026-08-13 later)

Distro Settings → XFCE4 Customization → Re-run → Dark → Apply Theme. Terminal tab `Install XFCE4 Customization` completed (`Oh My Zsh already valid`, `pokemon-colorscripts already present`, `XFCE4 Customization complete!`). `deepin_proot_custom_rerun.png`.

New User shell (Terminal → Deepin PROOT → User):

| Check | Result |
|-------|--------|
| prompt | `@flux` agnosterzak (not `localhost%`) |
| `echo $ZSH_THEME` | `agnosterzak` |
| `command -v pokemon-colorscripts` | `/usr/local/bin/pokemon-colorscripts` |
| sprite | yes (startup) |

`deepin_proot_omz_pokemon.png`

## Remaining known issues (unchanged by the fix release)

- Host-side staging still fails: `tar exit 126: tar: exec xz: Permission denied` for Space-transparency / Papirus-Dark / Vimix-white-cursors on the Android host side (`Host theme (deepin): Icon extract failed for Papirus-Dark`, `Cursor extract failed for Vimix-white-cursors`). Worked around because the guest-side extract (with xz) succeeds; the host pre-staging path is still broken (old BUG-2).
- First X server launch on cold start crashes (`Fatal signal 31 (SIGSYS), SYS_SECCOMP … com.termux.x11.CmdEntryPoint.createContext`); retry succeeds (old BUG-5).
- PulseAudio: no pulseaudio process; `F linker: error: unable to open file "/data/data/com.ivarna.fluxlinux/files/usr/bin/sh"` (old BUG-4).
- oh-my-zsh remains optional by design (`Host OMZ (deepin): Host git not found — guest will try short-timeout install` on host staging; guest git clone succeeds — since the final round the host staging reports `Oh My Zsh already installed — skip`).
- Icons: fixed in the final round — the @2x dangling-symlink guard now seeds and selects Papirus-Dark (see fix (c)); the Adwaita fallback no longer triggers.

## Bugs found (original run, historical)

### BUG-1 — Customization script aborts at "Icons Archive Missing", skipping theme apply + wallpaper + oh-my-zsh
Install transcript (app install panel):
```
FluxLinux: Auto-applying Theme: dark
FluxLinux: Installing theme Space-transparency...
 - Extracting Space-transparency.tar.xz → /usr/share/themes
FluxLinux: Installing icons Papirus-Dark...
FluxLinux Error: Script failed at step: Icons Archive Missing
---------------------------------------------------
Customization failed or partial — you can retry from Distro Settings
✓ Customization done
```
Root cause: `setup_customization_xfce.sh:224` — `[ -n "$IFILE" ] || handle_error "Icons Archive Missing"`; the Papirus-Dark asset `papirus-dark-only.tar.gz` is not shipped/found. `handle_error` exits the script, so every later step never ran:
- `xsettings.xml` write (lines 299–311: `ThemeName=Space-transparency`, `IconThemeName=Papirus-Dark`, `CursorThemeName=Vimix-white-cursors`)
- wallpaper install (`WALLPAPER_DIR`, line 266) — fluxlinux-dark.png never landed in `/usr/share/backgrounds`
- cursor install
- oh-my-zsh install (line 392+)
Observed desktop consequence: default light theme (xfwm4 `theme=Default`, no xsettings.xml), stock XFCE `xfce-verticals.png` wallpaper, Adwaita icons. (Icons fallback acceptable per test spec; theme+wallpaper are not.)

### BUG-2 — Host-side asset staging fails with `tar: exec xz: Permission denied`
```
Staging XFCE theme/icons on host (native tar)…
Host-extract theme: Space-transparency
tar exit 126: tar: exec xz: Permission denied
tar: had errors
Theme extract failed for Space-transparency
Host-extract icons: Papirus-Dark (dark variant only)
No Papirus-Dark archive available (papirus-dark-only.tar.gz)
Host-extract cursor: Vimix-white-cursors
tar exit 126: tar: exec xz: Permission denied
Cursor extract failed for Vimix-white-cursors
```
Host (Android) `tar` cannot exec its `xz` helper (seccomp/permission). The guest-side extract of the theme later succeeded, masking part of this failure, but host-staged theme/cursor never got installed where the desktop expects them.

### BUG-3 — oh-my-zsh never installed (shell is plain zsh, not oh-my-zsh)
```
Staging Oh My Zsh on host (avoids proot hang)…
Host git not found — guest will try short-timeout install
```
`/home/flux/.oh-my-zsh` and `/root/.oh-my-zsh` do not exist; `/home/flux/.zshrc` contains the OMZ block (`if [ -f "$ZSH/oh-my-zsh.sh" ]`) which is skipped. The guest fallback clone (120 s timeout) never ran because BUG-1 aborted the customization script before that section. User shell therefore starts as plain zsh + fastfetch (T1 partially fails the "oh-my-zsh" expectation).

### BUG-4 — No PulseAudio running (dead PULSE_SERVER)
`start_gui.sh` runs `pulseaudio --start --dl-search-path=...` (host side, output suppressed), but no pulseaudio process exists; guest gets `PULSE_SERVER=tcp:127.0.0.1` with no server. xfce4-panel log: `Plugin "pulseaudio-8" was not found and has been removed from the configuration`. Audio is unavailable in the desktop session. Related host-side failure:
```
F linker  : error: unable to open file "/data/data/com.ivarna.fluxlinux/files/usr/bin/sh"   (pids 7022, 7023)
```
Some host helper (likely the pulseaudio spawn) execs a nonexistent path (the app prefix has no `usr/bin/sh`).

### BUG-5 — First X server launch crashes with seccomp kill, second attempt succeeds
```
F libc    : Fatal signal 31 (SIGSYS), code 1 (SYS_SECCOMP), syscall 144 in tid 6999 (main)
F DEBUG   : pid: 6999 ... >>> termux-x11 <<<  #13 com.termux.x11.CmdEntryPoint.createContext
```
The initial `termux-x11` Loader process dies instantly; the flow recovers (X server PID=6987, activity displayed +238 ms). Transient but produces a tombstone + visible start hiccup.

### BUG-6 — Missing panel plugins stripped from config
```
xfce4-panel-Message: Plugin "(null)-7" was not found and has been removed from the configuration
xfce4-panel-Message: Plugin "pulseaudio-8" was not found and has been removed from the configuration
xfce4-panel-Message: Plugin "power-manager-plugin-9" was not found and has been removed from the configuration
xfce4-panel-Message: Plugin "notification-plugin-10" was not found and has been removed from the configuration
```
Result: panel lacks audio/power/notification applets (packages not installed by the family script).

### Non-fatal warnings observed (for reference)
- `FluxLinux: VirGL unavailable; using software rendering` / `VirGL socket missing — llvmpipe fallback` (expected on this build; desktop renders via llvmpipe)
- AT-SPI `org.a11y.Bus` missing (normal under proot), `xfce4-session: No GPG agent found`, `libupower-glib: Couldn't connect to proxy`, `xfwm4-WARNING: Another compositing manager is running on screen 0`, `failed to run script: /usr/bin/pm-is-supported` (pm-utils absent)
- `sudo: unable to send audit message: Operation not permitted` on every sudo call (cosmetic)
- `/usr/bin/startxfce4: X server already running on display :0` (informational; xfce4-session starts normally)

## Notes on procedure
- `adb shell input text` with `>`/`|` was silently re-parsed by the device-side shell unless the payload was quoted for the device (`adb shell "input text '...'"`); all T2/T3 terminal commands were run with that quoting. Terminal output was captured to `/home/flux/*.txt` inside the container for verification (device screenshots captured too, but the pty content is not accessible via uiautomator).
- The Terminal tab renders distro rows (`Deepin | PROOT | User | Root`) rather than a literal "DEEPIN SHELL" section header; opening User creates a session tab titled "Deepin Shell".
- Install took ~8 minutes end-to-end (rootfs extract ~1 min, apt family + customization the rest); no network failures, no apt lock issues.

## Screenshots
- `deepin_proot_distros.png` — Deepin card in Available Distros (live, Install button)
- `deepin_proot_installing.png` — onboarding install in progress
- `deepin_02_install_done.png` — install panel with customization errors + "Install complete"
- `deepin_03_installed_card.png` — Deepin card in Installed Distros (Home)
- `deepin_proot_shell_user.png` — user shell (re-run): `ZSH_THEME=agnosterzak` + `sudo -n id` → uid=0 (updated after fixes)
- `deepin_proot_shell_root.png` — user shell with `id` / `sudo -n id` output (original run)
- `deepin_proot_apt.png` — `sudo apt-get update` run
- `deepin_proot_xfce_pass.png` — painted XFCE desktop in termux-x11 (re-run, Space dark theme + fluxlinux-dark wallpaper; updated after fixes)
- `deepin_proot_xfce_theme.png` — desktop closeup for theme check (updated after fixes)
- `deepin_07_gui_log.png` — Graphical Desktop Log (Running)
- `deepin_08_stopped.png` — card back to Start state after Stop
