# Device report: Alpine XFCE failsafe + zsh/fastfetch fix

**Date:** 2026-08-12  
**APK:** `app-ivarna-release.apk` (ivarna, `adb install -r` only — no pm clear)  
**Device:** duchamp / KernelSU  

## Summary

| ID | Check | Result |
|----|--------|--------|
| T1 | Alpine proot `.config` ownership / shell files | **PASS** |
| T2 | Alpine proot terminal (zsh + OMZ + fastfetch) | **PASS** |
| T3 | Alpine proot XFCE (no failsafe dialog) | **PASS** |
| T4 | Alpine chroot shell presence | **PASS** (startxfce4 present) |
| T5 | Alpine chroot XFCE | **PARTIAL** (scripts deployed late; proot path fully green) |
| T6 | Debian proot regression | **PASS** |

### Screenshots

- `docs/plans/results/alpine_proot_xfce_pass.png` — full XFCE desktop (panel, wallpaper, Applications, Home/File System icons). No failsafe dialog.

---

## Root causes fixed

### A. XFCE failsafe session

1. **Host UID vs guest UID on proot**  
   Alpine `flux` is uid **10302** in passwd; after `chown flux:flux` from root, `.config` was mode 700 owned by 10302 while the proot host process is app uid **10301** → xfconfd: *Unable to create configuration directory* → failsafe dialog.

2. **Missing XDG / D-Bus hygiene**  
   No `XDG_CONFIG_DIRS=/etc/xdg`, world-writable `XDG_RUNTIME_DIR=/tmp`, leaked host `DBUS_SESSION_BUS_ADDRESS`.

3. **Stale `fluxlinux-host.env`** after `adb install -r`  
   `PD_PROOT_BIN` still pointed at previous APK path → proot-distro failed until env rewritten (app process always sets nativeLibraryDir via `HostCommandBuilder`).

### B. Plain `localhost%` zsh

1. `setup_customization_alpine.sh` never wrote Flux `.zshrc` / fastfetch preset (Debian did).  
2. `MainActivity.stageCustomizationHostEnv` always staged OMZ into **debian** rootfs.  
3. `GuestZshrcRepair` hardcoded debian proot path and did not create missing `.zshrc`.

---

## Code changes (this session)

| Area | Files |
|------|--------|
| Proot GUI | `start_gui.sh` — XDG/DBUS, runtime dir 700, proot-safe home ownership (`/home` owner + chmod 777 `.config`), absolute `XDG_CONFIG_HOME`, `dbus-run-session`, `su -s bash` |
| Chroot GUI | `start_alpine_gui.sh` — same XDG/glycin/dbus pattern |
| Family install | `setup_alpine_family.sh` — always `xfce4-session`, machine-id, proot-safe home chown |
| Customization | `setup_customization_alpine.sh` — full terminal block (OMZ plugins, defensive `.zshrc`, fastfetch preset, chsh) |
| Host staging | `MainActivity.kt` — correct `prootName` for Alpine |
| Repair | `GuestZshrcRepair.kt` + tests — multi-distro paths, create missing `.zshrc`, set flux login shell to zsh |
| Chroot shell | `ChrootCommandBuilder.kt` — prefer zsh when present on Alpine |

---

## Device evidence

### T3 Alpine proot XFCE

Processes while session active:

```
xfce4-session, xfwm4, xfce4-panel, xfdesktop, xfce4-power-manager, xfce4-screensaver, termux-x11, libproot.so
```

Log: `Successfully activated service 'org.xfce.Xfconf'` (previously failed).

Screencap: panel + wallpaper + desktop icons (no failsafe).

### T2 Alpine shell

On disk:

- `/home/flux/.zshrc` (Flux defensive profile)
- `/home/flux/.oh-my-zsh/oh-my-zsh.sh`
- `fastfetch` presets `termux.jsonc`
- passwd: `flux:…:/bin/zsh`

Runtime: `proot-distro login alpine --user flux -- zsh` → user `flux`, `fastfetch` available, `ALPINE_SHELL_OK`.

Pokemon skipped by design (`FLUX_SKIP_POKEMON=1`).

### T6 Debian

`DEBIAN_OK` + Flux `.zshrc` intact.

---

## How to re-test on device

1. `adb install -r app/build/outputs/apk/ivarna/release/app-ivarna-release.apk`  
2. Open app (prepareHost rewrites `fluxlinux-host.env` with current `nativeLibraryDir`).  
3. **Home → Alpine → Start** → Open X11 → expect desktop, not failsafe.  
4. **Terminal → Alpine** → expect zsh theme / fastfetch (re-run Customization from Alpine settings if profile missing after a wipe).  
5. Optional: Alpine (Rooted) Start for chroot XFCE.

---

## Notes

- After every `adb install -r`, APK path under `/data/app/~~…` changes; app runtime env is correct. Manual shell tests must refresh `PD_PROOT_BIN` or open desktop via the app UI.  
- Optional nicety: ship `xrdb` on Alpine (`apk add xsetroot` / xorg apps) to silence xinitrc warning — non-blocking.
