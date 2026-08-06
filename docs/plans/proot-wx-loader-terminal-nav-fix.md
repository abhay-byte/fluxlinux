# Plan: Fix PRoot W^X setup failure + Terminal bottom nav

**Date:** 2026-08-06  
**Branch:** `feat/embedded-terminal-bootstrap-proot-chroot`  
**Status:** **COMPLETE** (device verified 2026-08-06)  
**App ID (device):** `com.ivarna.fluxlinux`  
**Reference:** `~/repos/termux-lib` (W^X loader patch + bottom-nav Terminal page)

---

## 1. Problem (from device screenshot)

Debian base install reported OK (rootfs already present), then configuration failed:

```text
proot error: execve("/usr/bin/bash"): Permission denied
fatal error: see 'libproot.so --help'
proot error: can't chmod '…/usr/tmp/proot-…': No such file or directory
FluxLinux: Configuration/Setup Script Failed! (exit 1)
```

### Root cause (targetSdk 36 W^X)

| Layer | Required | Bug without fix |
|-------|----------|-----------------|
| Host proot binary | `PD_PROOT_BIN` → jniLibs `libproot.so` | Uses non-exec `$PREFIX/bin/proot` |
| Host loader | `PROOT_LOADER` → jniLibs `libloader.so` | proot defaults to `$PREFIX/libexec/proot/loader` (blocked) |
| proot-distro pass-through | Allowlist must include `PROOT_LOADER` | Stock only allows `PROOT_NO_SECCOMP`, `PROOT_VERBOSE` |

termux-lib patches `proot_distro/commands/login/__init__.py` after bootstrap extract. FluxLinux was missing that patch → guest `execve("/usr/bin/bash")` Permission denied.

---

## 2. Goals

1. **PRoot login + setup work** on device (flux_install config phase + interactive Debian shell).
2. **Terminal in bottom nav** (Home · Distros · Terminal), matching termux-lib product UX.
3. **Release APK** built, installed on attached device, verified via adb.

**Out of scope:** GUI/X11 parity, chroot deep fixes, Play upload.

---

## 3. Implementation checklist

### 3.1 Host / proot W^X (code)

| # | Item | File(s) | Done? |
|---|------|---------|-------|
| A1 | Patch proot-distro allowlist for `PROOT_LOADER` + `PROOT_LOADER_32` (idempotent) | `TermuxHostPaths.kt` | Yes |
| A2 | Call patch + pin W^X paths on every extract/prepare | `BootstrapInstaller.kt`, `HostScriptDeployer.kt` | Yes |
| A3 | Env: `PD_PROOT_BIN`, `PROOT_LOADER`, `PROOT_LOADER_32` | `HostCommandBuilder.kt`, `writeHostEnvFile` | Yes |
| A4 | `flux_install.sh`: preserve W^X env after profile; fail-closed; shared tmp | `assets/scripts/debian/proot/setup/flux_install.sh` | Yes |
| A5 | `setup_termux.sh`: require `PROOT_LOADER` | `assets/scripts/host/setup_termux.sh` | Yes |
| A6 | Unit test for patch | `TermuxHostPathsTest.kt` | Yes |

### 3.2 Terminal bottom nav (UI)

| # | Item | File(s) | Done? |
|---|------|---------|-------|
| B1 | `BottomTab.TERMINAL` | `GlassBottomNavigation.kt` | Yes |
| B2 | Terminal as tab content under GlassScaffold | `MainActivity.kt` | Yes |
| B3 | Install / shell / uninstall / FGS → Terminal tab | `MainActivity.kt`, `HomeScreen.kt` | Yes |
| B4 | TerminalScreen: no back when embedded; bottom padding for floating nav | `TerminalScreen.kt` | Yes |

### 3.3 Device verify (this session)

| # | Step | Command / check |
|---|------|-----------------|
| C1 | Unit tests | `./gradlew :app:testIvarnaDebugUnitTest --tests '…TermuxHostPathsTest'` |
| C2 | Release APK | `./gradlew :app:assembleIvarnaRelease` then **kill gradle daemons** |
| C3 | Install | `adb install -r <apk>` |
| C4 | Cold start host prepare | Launch app; confirm no crash; Terminal tab visible |
| C5 | Force host re-patch | Via prepareHost on install or Settings host init |
| C6 | Proot smoke | adb run-as / run shell: `PD_PROOT_BIN`+`PROOT_LOADER` set; proot-distro login debian `echo OK` |
| C7 | UI install path | Re-run Debian install or open Debian shell from Terminal; no Permission denied |

---

## 4. Acceptance criteria

- [x] Bottom nav shows **Home · Distros · Terminal** (uiautomator dump)
- [x] Host prepare patches proot-distro (`PROOT_LOADER` + `PROOT_LOADER_32` in login `__init__.py`)
- [x] proot-distro login debian prints `FLUX_PROOT_OK` + Debian 13 (no Permission denied)
- [x] Debian shell session opens from Terminal tab (`Debian Shell (PRoot)` session tab)
- [x] Release APK installed (`app-ivarna-release.apk` 216M); gradle daemons stopped after build

### Device evidence (2026-08-06)

| Check | Result |
|-------|--------|
| APK | `app/build/outputs/apk/ivarna/release/app-ivarna-release.apk` install Success |
| Bottom nav | Home / Distros / Terminal visible |
| Patch line 457 | `for var in ("PROOT_NO_SECCOMP", "PROOT_VERBOSE", "PROOT_LOADER", "PROOT_LOADER_32"):` |
| Proot smoke | `FLUX_PROOT_OK` · `aarch64` · `Debian GNU/Linux 13 (trixie)` |
| UI shell | Tap Terminal → Debian Shell (PRoot) → session tab present |

---

## 5. Device notes

- Package: `com.ivarna.fluxlinux`
- Existing guest rootfs may remain; prepareHost re-applies patch without wiping containers
- If setup still fails for non-loader reasons, uninstall distro and reinstall once

## 6. Rollback

- Revert `TermuxHostPaths` patch + script env pins if needed
- Bottom nav Terminal is additive; safe to hide tab without reverting proot fix
