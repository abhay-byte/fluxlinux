# Chimera proot — device report

**Date:** 2026-08-13
**Package:** `com.ivarna.fluxlinux` (release APK, 1.8.0 / versionCode 10)
**Device:** Xiaomi 2311DRK48I, KernelSU (root available for diagnostics)
**Card:** `chimera` — "Chimera Linux (musl, apk v3) with XFCE4 (proot)."

## Matrix

| Step | Result |
|------|--------|
| I1 card live + onboarding | **PASS** (retry) — family + customization completed; base pkgs installed, user `flux` created, doas.conf written |
| T1 user shell (flux) | **PASS** — `uid=1000(flux) gid=1000(flux) groups=3003(aid_inet),9997(aid_everybody),20301(aid_u0_a301_cache),50301(aid_all_a301)` |
| T2 `sudo -n id` → uid=0 | **PASS** — `uid=0(root) gid=0(root) groups=3003(aid_inet),9997(aid_everybody),20301(aid_u0_a301_cache),50301(aid_all_a301)` |
| T3 `sudo apk update` | **PASS** — `[https://repo.chimera-linux.org/current/main]` + `[https://repo.chimera-linux.org/current/user]` → `OK: 11901 distinct packages available`, no Permission denied |
| D1–D3 Start → embedded X11 → XFCE | **PASS** — xfce4-session/xfwm4/xfce4-panel/xfsettingsd/Thunar/xfdesktop all up on first start (no SIGSYS); desktop painted |
| D4 theme/icons/xsettings.xml | **PASS** — Space-transparency, Papirus-Dark, Vimix-white-cursors (52), JetBrainsMono Nerd Font |
| D5 Stop → card returns to Start | **PASS** — `✅ GUI stopped successfully!` (exit 0), card reverts to Start |

## T1/T2/T3 evidence (app's own Terminal → CHIMERA SHELL (PROOT) → User session, captured in-session)

```
$ id
uid=1000(flux) gid=1000(flux) groups=3003(aid_inet),9997(aid_everybody),20301(aid_u0_a301_cache),50301(aid_all_a301)

$ sudo -n id
uid=0(root) gid=0(root) groups=3003(aid_inet),9997(aid_everybody),20301(aid_u0_a301_cache),50301(aid_all_a301)

$ sudo apk update
 [https://repo.chimera-linux.org/current/main]
 [https://repo.chimera-linux.org/current/user]
OK: 11901 distinct packages available

$ apk info | head -3
acl
adw-gtk3
adw-xfwm4
```

The Android aid groups in T1 are bind-mount leakage (proot), not a failure: uid/gid are 1000(flux) as expected. T2/T3 outputs captured verbatim via guest redirect to `/tmp` (host: `files/usr/tmp`) after the on-screen runs shown in the screenshots.

## doas fix (shipped in release APK) — verification of the T2 failure analysis

The previously predicted fix direction is now shipped (`flux_guest_common.sh` doas branch) and verified on-device:

- `/etc/doas.conf` written as `permit nopass flux` with **`chmod 0644`** and `chown root:root` (was 0400 in the earlier round). Host-side check of the fresh container: `-rw-r--r-- 1 root root 19` with content `permit nopass flux`.
- OpenDoas now parses the config as the calling user (flux) pre-elevation without `Permission denied`, so both T2 and T3 pass through the `/usr/local/bin/sudo` → doas shim.

Other shipped shell fixes verified in the same round:

- **zshrc `setopt no_monitor`** — proot does not implement `tcsetpgrp`; job control caused ENOSYS/SIGTTIN kills. Present at `.zshrc:17`.
- **fastfetch disk-filter preset** — user-local `~/.local/share/fastfetch/presets/termux.jsonc` filters the disk module to `folders: ["/"]` with `showRemovable/showHidden/showSubvolumes: false` (proot leaks every Android mount via `/proc/mounts`); zshrc runs `fastfetch --config termux 2>/dev/null` (`.zshrc:21-22`).
- The `apk()` zsh wrapper (`apk() { command sudo apk "$@"; }`, `.zshrc:41-43`) makes plain `apk` work in the user shell.

## D1–D5 evidence

- Start → "Launch XFCE4" → embedded X11 (`com.termux.x11.MainActivity`). First start succeeded with no SIGSYS crash; retry was not needed.
- Process tree observed: `start_gui.sh chimera → proot → bash → su - flux → dbus-run-session -- startxfce4 → xfce4-session → xfwm4, xfce4-panel (+systray, actions plugins), xfsettingsd, Thunar --daemon, xfdesktop`.
- `gui_desktop.log` start section: only non-fatal warnings (no GPG/SSH agent, missing `/usr/bin/pm-is-supported`, `_NET_CURRENT_DESKTOP` fetch fallback, Thumbnailer/GetFlavors). No error.
- D4: `/home/flux/.config/xfce4/xfconf/xfce-perchannel-xml/xsettings.xml` contains `ThemeName=Space-transparency`, `IconThemeName=Papirus-Dark`, `CursorThemeName=Vimix-white-cursors`, `CursorThemeSize=52`, `FontName=JetBrainsMono Nerd Font 10`.
- D5: Stop → log `=== STOP method=proot script=stop_gui.sh ===` … `✅ GUI stopped successfully!` `[exit 0]`; card returns to **Start**; all guest XFCE processes gone; terminal sessions deliberately left alive (log: "Skipping proot shell kill").

## Screenshots

- `chimera_proot_sudo.png` — Chimera user shell: `sudo -n id` → `uid=0(root) gid=0(root) groups=…` (T2)
- `chimera_proot_apk_update.png` — Chimera user shell: `sudo apk update` → main+user repo URLs + `OK: 11901 distinct packages available` (T3)
- `chimera_proot_xfce_pass.png` — embedded X11 view, XFCE painted (Space wallpaper + panel; verified via process tree + screen pixel sampling, 9.8k distinct colors, dark Space palette with blue accent tones, distinct panel band)
- `chimera_proot_shell_sudo_fail.png` — pre-fix round: Chimera user shell after T1–T3 (uid=1000(flux) + doas permission error)
- Historical (pre-fix round): `chimera_proot_installing.png`, `chimera_proot_shell_user.png`, `chimera_proot_shell_root.png`, `chimera_proot_apk.png`, `chimera_proot_xfce_fail.png`, `chimera_proot_gui_log.png`

## OMZ + pokemon (UI, 2026-08-13 later)

Distro Settings → XFCE4 Customization → Re-run completed (`Oh My Zsh already valid`, `pokemon-colorscripts already present`). `chimera_proot_custom_rerun.png`.

New User shell (Terminal → Chimera PROOT → User):

| Check | Result |
|-------|--------|
| prompt | `@flux` agnosterzak (not `localhost%`) |
| `echo $ZSH_THEME` | `agnosterzak` |
| `command -v pokemon-colorscripts` | `/usr/local/sbin/pokemon-colorscripts` |
| sprite | yes (startup) |

`chimera_proot_omz_pokemon.png`

Chimera `timeout(1)` under Android dies with `sigaction(32): Invalid argument`. Guest script now probes timeout and uses a portable watchdog so git clone actually runs.

## Other observations

- Terminal tab row has a stale-tab UI bug: closing sessions kills them (`Session finished: -9`) but tabs remain visible until another recomposition (revision flow at `TerminalScreen.kt:88` is collected but never read). Non-blocking; new sessions attach correctly.
- The stale "Chimera Shell" tabs from the failed earlier round now open a working flux shell (guest state is live).
- Privileged package operations now work end-to-end from the user shell: `sudo apk update` (T3) passes, and the zsh `apk()` wrapper gives plain `apk` the same path (`apk info | head -3` → `acl`, `adw-gtk3`, `adw-xfwm4`).
