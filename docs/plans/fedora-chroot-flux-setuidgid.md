# Plan: Fedora chroot flux login without `--userspec`

**Date:** 2026-08-15  
**Status:** **IMPLEMENTED + device-verified** (2026-08-15)  
**Device:** Xiaomi 2311DRK48I (duchamp) `Y5WWBMJVOZSK4HU8`, KernelSU busybox 1.36.1.1 (topjohnwu)

## 1. Symptom (device)

Fedora Chroot Shell (user flux) exits immediately:

```
chroot: can't change root directory to '--userspec=1000:1000': No such file or directory
[Process completed (code 1)]
```

Helper on device is already **v2.6** (runuser → su → `chroot --userspec`). Fedora 44 rootfs has **no** `/bin/su` / `runuser` (su moved to util-linux; family did not install it). Fallback used GNU `chroot --userspec`. KSU busybox is:

```
Usage: chroot NEWROOT [PROG ARGS]
```

`--userspec=1000:1000` is treated as NEWROOT → ENOENT.

## 2. Device facts (adb, 2026-08-15)

| Check | Result |
| --- | --- |
| Guest `su` / `runuser` / `setpriv` | missing |
| `flux` passwd | `1000:1000` `/bin/bash` |
| Busybox applets | `chroot`, `setuidgid`, `setpriv` (no `--userspec`) |
| `setuidgid flux` inside chroot | **fail** `unknown user/group flux` (name lookup ≠ guest passwd) |
| `setuidgid 1000` inside chroot | **ok** `uid=1000(flux) gid=1000(flux)` |
| Invoke copied bb as `/tmp/.flux_bb` | **fail** `applet not found` (argv0 must be `busybox`) |
| `chroot ROOT /tmp/busybox setuidgid 1000 env -i … bash --login` | **ok** `uid=1000 user=flux home=/home/flux pwd=/home/flux` |

## 3. Design

Keep runuser → su first (Debian/Alpine/openSUSE unchanged).

Replace `guest_userspec` (v2.6 `--userspec`) with:

1. Read **numeric** uid from **guest** `/etc/passwd` (never the name `flux`).
2. `cp` host `$BB` → `$FLUX_CHROOT/tmp/busybox` (must be named `busybox`).
3. `$BB chroot "$FLUX_CHROOT" /tmp/busybox setuidgid "$UID" /usr/bin/env -i $GUEST_ENV_ARGS "$@"`

If `setuidgid` applet is missing, die with a clear message (do not retry `--userspec`).

Bump helper **v2.6 → v2.7** so `ensureChrootHelper` restages on next chroot open.

Fedora family already installs `util-linux` (new installs get `su`/`runuser`). Existing device Fedora stays on the setuidgid path.

## 4. Non-goals

- Do not mutate guest `/etc/passwd`.
- Do not `dnf install` on session open.
- Do not change proot, Settings toggle, or host shell.

## 5. Verify

- Unit: helper text has `setuidgid`, no `chroot --userspec`; version `v2.7`.
- Device (after `adb install -r` release, no uninstall) **PASS 2026-08-15:**
  1. Helper on device is `fluxlinux-chroot v2.7`.
  2. Fedora flux: `uid=1000(flux) gid=1000(flux)`, `HOME=/home/flux`. No `--userspec` error.
  3. Fedora root: `uid=0(root)`.
  4. Debian flux still uses `su` (groups audio/video/wheel/aid_inet).
  5. Ivarna release installed (`adb install -r`); app launched.
