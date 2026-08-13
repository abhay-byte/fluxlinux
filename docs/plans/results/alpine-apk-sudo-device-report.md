# Device report: Alpine `apk` (package manager) + sudo

**Date:** 2026-08-12  
**Package:** Alpine package manager (`/sbin/apk`), not Android APK  
**Release:** ivarna `adb install -r` after script fixes  

## Matrix

| Check | Proot Alpine | Chroot Alpine |
|--------|--------------|---------------|
| `sudo -n id` → root | **PASS** | **PASS** |
| bare `apk update` as flux | Fail (expected; needs root) | Fail log (expected) |
| `sudo apk update` **no lock permission denied** | **PASS** (was FAIL) | **PASS** |
| `sudo apk add <pkg>` install | **PASS*** (see DNS note) | **PASS** (`tree` installed) |
| zsh `apk()` → sudo wrapper | Present in `.zshrc` | N/A (ash login until zsh) |

\*Proot after ownership fix: **no more** `Unable to lock database: Permission denied`.  
`apk update` returns packages available. Fresh index fetch may show `DNS: transient error` when launched via `adb su` (SELinux/`Operation not permitted` on UDP DNS). In-app terminal uses app network domain — prefer re-test there. Chroot has full network and installs cleanly.

## Root cause (what looked like “sudo broken”)

1. **`sudo` itself worked** (`sudo -n id` → uid 0).  
2. **`sudo apk` failed** because `/lib/apk/db/lock` (and siblings) were **host-root owned (uid 0, mode 600)**.  
3. Under **proot**, guest root still runs as the **Android app uid**, so it cannot write host-uid-0 files → permission denied on lock.

## Fixes shipped

| File | Change |
|------|--------|
| `setup_alpine_family.sh` | `_flux_fix_apk_writable` (chown apk paths to `/etc` owner, 666 lock/log); sudoers include assert; multi-NS DNS |
| `setup_customization_alpine.sh` | same apk ownership repair; **zsh `apk()` wrapper** → `sudo apk` |
| `GuestApkDbRepair.kt` | session-open best-effort lock/chmod on Alpine proot |
| `GuestZshrcRepair.kt` | Alpine profile includes `apk()` wrapper |
| `GuestSessionFactory.kt` | calls `GuestApkDbRepair` |

## How to use in Alpine terminal

```sh
sudo apk update
sudo apk add htop
# or, after zsh profile load:
apk update          # function → sudo apk
apk add figlet
```

## Re-test in-app

1. Open **Terminal → Alpine** (flux).  
2. `sudo -n id`  
3. `sudo apk update`  
4. `sudo apk add htop` (or any package)  
Expect: no lock permission errors; packages install when network/DNS is healthy.
