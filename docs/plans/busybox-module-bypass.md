# Plan: Drop the BusyBox NDK module requirement

**Date:** 2026-08-15
**Status:** RESEARCH DONE — implement from [busybox-module-bypass-impl.md](./busybox-module-bypass-impl.md) + [worker-prompt-busybox-module-bypass.md](./worker-prompt-busybox-module-bypass.md)
**Implementation:** PR1+PR2 **READY**. PR3 (ship ELF) out of scope.
**Question:** Can FluxLinux chroot install/run without the user flashing osm0sis **Busybox for Android NDK** in Magisk / KernelSU / APatch?

**Short answer:** Yes for Magisk, KernelSU, and APatch. Those managers already ship a feature-complete static BusyBox. FluxLinux already calls `$BB applet` (not bare `wget`/`tar` on PATH). The extra module only puts applet **symlinks** on PATH. The current “you MUST install this module” gate is a detection + UX problem, not a missing binary.

A second, harder track can drop BusyBox entirely by using `/system/bin/{mount,umount,chroot,stat,mknod}` plus a non-exec extract. That is optional insurance, not required to stop forcing the module.

---

## 1. What the user-facing module actually is

Downloaded and inspected: [Magisk-Modules-Repo/busybox-ndk](https://github.com/Magisk-Modules-Repo/busybox-ndk) (v1.36.1, `versionCode=13614`, osm0sis).

| File | Role |
| --- | --- |
| `busybox-arm64-selinux` (2.1 MiB) | Static ELF, Android 26, NDK r25c. Default on modern ARM64. |
| `busybox-arm64` (1.5 MiB) | Older NDK r15c, Android 21, no SELinux applets. |
| Same pair for arm / x86 / x86_64 / mips | ABI picker in `find_arch`. |
| `customize.sh` | osm0sis “Diffusion” installer (`SKIPUNZIP=1`). |
| `diffusion_config.sh` | Real install: copy `busybox-$ARCH$SELINUX` → `$XBIN/busybox`, then `busybox --list` → symlink every applet. |

Install location after flash:

```
/data/adb/modules/busybox-ndk/system/xbin/busybox   # or system/bin if no xbin
/data/adb/modules/busybox-ndk/system/xbin/mount     # symlink → busybox
/data/adb/modules/busybox-ndk/system/xbin/chroot
… hundreds of applet names …
```

Magisk/KSU overlay-mounts `system/` onto `/system` after reboot. Then `command -v busybox` succeeds because `/system/xbin` is on the root shell PATH.

The binary itself is **not** unique. It is a static multi-call BusyBox. Confirmed by running the x86_64 build on the host:

```
BusyBox v1.36.1-osm0sis (2023-05-25)
```

Required applets FluxLinux uses are **all present**: `mount`, `umount`, `chroot`, `tar` (`-J` xz), `wget`, `unxz`/`xz`, `setuidgid`, `stat`, `mknod`, `ln`, `timeout`, `base64`, `ash`/`sh`.

What the module does that Magisk/KSU/APatch built-in does **not** do: install those applet names onto PATH. FluxLinux does not need that. Scripts already invoke `$BB mount`, `$BB chroot`, `$BB tar`.

osm0sis README notes that unpatched `mount` / `umount` / `wget` “do not entirely work correctly on Android.” His NDK build includes those patches. Magisk/KSU’s own BusyBox is a separate topjohnwu build, also Android-patched, and is what Magisk module scripts run under (`ASH_STANDALONE=1`).

---

## 2. BusyBox already on the device (no extra module)

Official docs:

| Root manager | Built-in binary | Docs |
| --- | --- | --- |
| Magisk | `/data/adb/magisk/busybox` | [Magisk developer guides](https://topjohnwu.github.io/Magisk/guides.html): “feature complete BusyBox (including full SELinux support)” |
| KernelSU | `/data/adb/ksu/bin/busybox` | [KSU module guide](https://kernelsu.org/guide/module.html): same Magisk binary |
| APatch | `/data/adb/ap/bin/busybox` | [APatch APM guide](https://apatch.dev/apm-guide.html) |

These are **not** on the default `su -c` PATH (`/system/bin:/system/xbin`). `command -v busybox` is false until the NDK module (or BuiltIn-BusyBox) installs PATH links. That is why users think they “need the module.”

**Device fact (this repo, 2026-08-15):** [fedora-chroot-flux-setuidgid.md](./fedora-chroot-flux-setuidgid.md) used **KernelSU busybox 1.36.1.1 (topjohnwu)** with no NDK module. Confirmed working: `chroot`, `setuidgid` (numeric uid), bind mounts via the helper. `chroot --userspec` is **not** supported (BusyBox CLI is `chroot NEWROOT [PROG]`).

Peer project [chroot-distro](https://github.com/Magisk-Modules-Alt-Repo/chroot-distro) *recommends* the NDK module, then says Magisk/KSU/APatch built-in is “community-supported.” Their own issues (#34, #42, #60) are the same PATH-detection bug: APatch “comes with prebuilt Busybox so no extra module is required” until detection forgot `/data/adb/ap/bin/busybox`.

---

## 3. What FluxLinux actually calls

SSOT helper: `assets/scripts/chroot/fluxlinux_chroot.sh` v2.7.

| Applet | Where | Why |
| --- | --- | --- |
| `mount --bind` | helper, every setup/start | `/dev`, `/sys`, `/sdcard`, host-tmp, X11 socket |
| `mount -t proc/devpts/tmpfs` | helper, setup/start | guest `/proc`, `/dev/pts`, `/dev/shm` |
| `mount -o remount,dev,suid` | helper, setup | `/data` nosuid → suid (already prefers `/system/bin/mount`) |
| `umount` / `umount -l` | helper, stop, uninstall | teardown |
| `chroot NEWROOT PROG` | helper, setup, start, GUI | enter guest. **No `--userspec`** |
| `tar xJf` / `tar xzf` | setup_guest / setup_debian13 | extract `.tar.xz` rootfs. Comment: “toybox `/system/bin/tar` has no xz” |
| `unxz` pipe | setup_debian13 fallback | if `tar -J` missing |
| `wget` | setup `download_file` | **fallback only**. Onboarding uses Kotlin `RootfsDownloader` first |
| `setuidgid UID` | helper `guest_userspec` | Fedora flux login when guest has no `su`/`runuser` |
| `stat -c '%a'` / `'%t %T'` | helper | ptmx mode, kgsl mknod |
| `mknod` | helper | kgsl-3d0 if bind missed it |
| `ln -sf` | helper | `/dev/ptmx` → `pts/ptmx` |
| `timeout` | setup_arch_chroot | mirror probe |
| `base64` | helper `guest_sh` | host encode if no `/system/bin/base64` |

Proot / Termux-native paths do **not** need any of this. Bootstrap already has GNU `tar` + `xz-utils` + `curl`. Those cannot be exec’d as root from app-data (W^X + SELinux). Same reason `ChrootPaths.SESSION_EXEC = /system/bin/sh` and the helper is copied to `/data/local/tmp`.

---

## 4. Detection today (why the module still looks required)

`resolve_bb` is copy-pasted ~18 times. Lists disagree.

| Script | KSU | Magisk | APatch | NDK module | Notes |
| --- | --- | --- | --- | --- | --- |
| `fluxlinux_chroot.sh` | yes | yes | **no** | yes | Best list. Honors `FLUX_BB`. |
| `setup_guest_chroot.sh` | yes | yes | **no** | yes | |
| `setup_alpine_chroot.sh` | yes | yes | **no** | yes | |
| `setup_debian13_chroot.sh` | **no** | yes | **no** | yes | Debian chroot misses KSU |
| `setup_arch_chroot.sh` | **no** | yes | **no** | **no** | Tiny list |
| start/stop `*_gui.sh` | **no** | yes | **no** | yes | GUI can fail on KSU/APatch if PATH empty |
| uninstall `*_chroot.sh` | — | — | — | — | `command -v busybox` only |
| `TermuxIntentFactory` | — | — | — | — | Bare `busybox chroot` (legacy Termux RUN_COMMAND) |

Common bugs:

1. `command -v busybox` first. If a stub `/system/bin/busybox` exists, it wins even if applets are incomplete.
2. App/Termux PREFIX busybox is correctly rejected (`*com.termux*|*fluxlinux*|*nativecode*`) — W^X, and it is the wrong tool for host mounts.
3. APatch path is **nowhere**.
4. UI (`BusyBoxInstallStep`) says rooted users **MUST** flash the NDK zip. No probe of Magisk/KSU/APatch built-in. Checkbox is honor-system, not a real detect.
5. Onboarding still: “BusyBox may be required on some devices.”

So a KSU-only device (exactly the Fedora test device) already works **if** the script that ran was `fluxlinux_chroot.sh` / `setup_guest_chroot.sh`. A Debian-chroot install via `setup_debian13_chroot.sh` on the same device can fail because KSU is missing from that list.

---

## 5. Can applets be replaced (no BusyBox at all)?

| Need | `/system/bin` (toybox) | Magisk/KSU/APatch bb | osm0sis NDK | Ship-our-own bb | Kotlin / guest |
| --- | --- | --- | --- | --- | --- |
| bind / proc / tmpfs / devpts | **yes** (`/system/bin/mount`) | yes | yes | yes | — |
| remount `/data` | **preferred already** | often “can’t find /data” | same | same | — |
| umount -l | **yes** | yes | yes | yes | — |
| chroot | **yes** (same CLI, no `--userspec`) | yes | yes | yes | — |
| tar + xz | **no** (this is why scripts force `$BB tar`) | yes | yes | yes | Commons Compress / xz-java, or guest after first extract |
| wget | no | historically flaky ([Magisk#8403](https://github.com/topjohnwu/Magisk/issues/8403)) | patched | yes | **already** `RootfsDownloader` |
| setuidgid | **no** | **yes (device)** | yes | yes | guest `runuser`/`su` (Fedora family already installs util-linux) |
| stat / mknod / ln / base64 | yes | yes | yes | yes | — |

**Cannot delete the concept of a capable host `chroot`/`mount`.** Those syscalls need uid 0. Toybox provides them. BusyBox is not special for mounts.

**Cannot delete a host xz-tar or a Java extract** unless every rootfs is gzip (Alpine is `.tar.gz`; everything else is `.tar.xz` because aapt2 strips `.gz` assets).

**setuidgid is only needed when the guest has no `su`/`runuser`.** New Fedora installs get util-linux. The helper already tries runuser → su → setuidgid.

PREFIX `tar`/`xz`/`busybox` cannot replace host tools: W^X on app-data, and `LD_LIBRARY_PATH` Termux ELF as root is fragile. Copying them to `/data/local/tmp` still needs the Termux linker + libs.

---

## 6. Options

### A — Stop requiring the module (recommended, first PR)

Use the BusyBox the root manager already installed. Do not flash anything.

1. One `resolve_bb()` in `fluxlinux_chroot.sh`. Every other script sources it or is told `FLUX_BB=`.
2. Search order (first **executable** that has `chroot` + `mount` in `$bb --list`):
   1. `FLUX_BB` / `/data/local/tmp/flux_busybox`
   2. `/data/adb/ksu/bin/busybox`
   3. `/data/adb/ap/bin/busybox`
   4. `/data/adb/magisk/busybox`
   5. `/data/adb/modules/busybox-ndk/system/{xbin,bin}/busybox` (optional leftover)
   6. `/debug_ramdisk/busybox`, `/sbin/busybox`
   7. `command -v busybox` only if not Termux/Flux PREFIX
   8. Last: `/system/xbin/busybox`, `/system/bin/busybox` — **only if applet probe passes**
3. After resolve, `cp -f "$BB" /data/local/tmp/flux_busybox && chmod 755` (same pattern as the helper). Pin `FLUX_BB` for the rest of the session.
4. Kotlin `RootShell` probe: `test -x` those paths via `su`, cache the winner, pass `FLUX_BB=...` into setup/start/helper.
5. Per-applet fallback: remount/bind/umount/chroot → `/system/bin/{mount,umount,chroot}` if `$BB` lacks them.
6. UI: delete “MUST install NDK.” Root gate = `RootShell.isRootAvailable()`. Optional “BusyBox not found” only if the probe fails (rare). Keep Download Module as a last-resort link, not a blocker.
7. Kill bare `busybox` in `TermuxIntentFactory` (legacy). Uninstall scripts must use the same resolver, not `command -v`.

**Risk:** Magisk wget regression is irrelevant (Kotlin downloads). Magisk `tar -J` is used by Magisk itself; KSU device already extracted xz rootfs via guest/helper path. If a provider binary ever lacks `tar`/`unxz`, fall through to Option C extract or fail with “xz extract needs BusyBox tar — install NDK or retry.”

**F-Droid:** no new binary, no new license text.

### B — Ship a static BusyBox in the APK (insurance)

Bundle `busybox-arm64-selinux` (~2.1 MiB) + `busybox-armeabi-v7a-selinux`. On first root use, `su` copies it to `/data/local/tmp/flux_busybox` (never exec from `files/usr`).

- Works if the manager binary is missing or stripped.
- GPLv2: F-Droid needs corresponding source (osm0sis [android-busybox-ndk](https://github.com/osm0sis/android-busybox-ndk) or a Flux-built config). Do not drop a prebuilt with no source pointer.
- Prefer building a **slim** config (mount, umount, chroot, tar+xz, setuidgid, stat, mknod, ln, wget, unxz, timeout, base64, ash) instead of 300 applets.
- Still reject PREFIX exec.

Do this only if A fails on a real device.

### C — True zero-BusyBox (stretch)

| Job | Replacement |
| --- | --- |
| mounts | `/system/bin/mount` + `/system/bin/umount` (already used for remount) |
| chroot | `/system/bin/chroot` |
| extract | Kotlin `XZInputStream` + Apache Commons Compress (or `org.tukaani.xz`) writing into `/data/local/tmp/chroot*` **as root** via a small extract helper, **or** `su` + toybox `tar` reading a gzip stream that Kotlin pre-decompresses to a fifo |
| wget | already Kotlin |
| setuidgid | require `util-linux` (or `shadow`) in every chroot family so `runuser`/`su` always exist; delete the staged-busybox fallback |

Hard parts: root-owned extract into `/data/local/tmp` from the app process (SELinux), and guaranteeing `su` on every family (Chimera/Alpine minirootfs). Do **not** start here.

### D — Keep forcing the module

Reject. It is the current UX and it is false for Magisk/KSU/APatch.

---

## 7. Recommended sequence

### PR1 — Detection SSOT + stop blocking (implements A)

**Goal:** Magisk / KSU / APatch users install a chroot with **only** su grant. No module.

- Extract `resolve_bb` + applet probe + `/data/local/tmp/flux_busybox` pin into `fluxlinux_chroot.sh` (or a tiny `assets/scripts/chroot/resolve_bb.sh` that setup/start/stop/uninstall all `.` source).
- Add APatch; add KSU to debian/arch/gui/uninstall lists.
- `RootShell.resolveBusyBox(): String?` + `FLUX_BB` on every root install/start command.
- Unit tests: candidate order, reject PREFIX paths, require `chroot`+`mount` in `--list` sample fixtures.
- UI: `BusyBoxInstallStep` becomes optional / auto-skip when probe succeeds. Copy change in `docs/tutorial/setup_debian_chroot.md`, `setup_fluxlinux.md`, `docs/architecture.md` (`Root + BusyBox NDK` → `Root`; BusyBox only if probe fails).
- `OnboardingInstallRunner` fail text: drop “install BusyBox if needed” unless probe failed.

**Device smoke (required):**

1. KSU **without** busybox-ndk: Debian + Fedora + Alpine chroot install, flux/root login, GUI start/stop.
2. Magisk without module: same.
3. APatch without module: at least helper `mount` + `login`.
4. Regression: device **with** the NDK module still works (candidate 5).
5. Fedora flux still uses setuidgid when guest `su` is missing.

### PR2 — Per-applet `/system/bin` fallback

If `$BB` is found but `tar` lacks `-J`, try `$BB unxz | /system/bin/tar xf -`. If `$BB mount` fails, retry `/system/bin/mount`. Do not treat toybox as the primary chroot tool until PR1 is proven.

### PR3 — Optional bundled slim BusyBox (B)

Only if a manager is found whose built-in binary fails the applet probe (document the device). F-Droid source note + `scanignore` if prebuilt.

### Non-goals

- Do not exec PREFIX/Termux busybox as root.
- Do not install a FluxLinux Magisk module just to expose applets.
- Do not restore GNU `chroot --userspec`.
- Do not change proot.
- Do not rewrite family scripts (they run **inside** the guest).

---

## 8. Implementation notes (PR1)

`resolve_bb` sketch:

```sh
bb_has() { "$1" --list 2>/dev/null | tr ' \t' '\n' | grep -qx "$2"; }

bb_ok() {
  [ -x "$1" ] || return 1
  case "$1" in *com.termux*|*fluxlinux*|*nativecode*) return 1 ;; esac
  bb_has "$1" chroot && bb_has "$1" mount
}

resolve_bb() {
  if [ -n "${FLUX_BB:-}" ] && bb_ok "$FLUX_BB"; then BB="$FLUX_BB"; return 0; fi
  if [ -x /data/local/tmp/flux_busybox ] && bb_ok /data/local/tmp/flux_busybox; then
    BB=/data/local/tmp/flux_busybox; return 0
  fi
  for path in \
    /data/adb/ksu/bin/busybox \
    /data/adb/ap/bin/busybox \
    /data/adb/magisk/busybox \
    /data/adb/modules/busybox-ndk/system/xbin/busybox \
    /data/adb/modules/busybox-ndk/system/bin/busybox \
    /debug_ramdisk/busybox \
    /sbin/busybox
  do
    if bb_ok "$path"; then BB="$path"; break; fi
  done
  if [ -z "$BB" ]; then
    _det=$(command -v busybox 2>/dev/null || true)
    bb_ok "$_det" && BB="$_det"
  fi
  [ -n "$BB" ] || return 1
  if [ "$BB" != /data/local/tmp/flux_busybox ]; then
    cp -f "$BB" /data/local/tmp/flux_busybox 2>/dev/null && chmod 755 /data/local/tmp/flux_busybox \
      && BB=/data/local/tmp/flux_busybox
  fi
}
```

Kotlin probe (same paths, `su -c 'test -x … && … --list'`). Cache like `cachedSuInvocation`.

Mount helper already prefers `/system/bin/mount` for remount. Extend `bind_if_missing` to:

```sh
$BB mount --bind "$_src" "$_dst" 2>/dev/null \
  || /system/bin/mount --bind "$_src" "$_dst" 2>/dev/null || true
```

`guest_chroot_env`:

```sh
if [ -n "$BB" ] && bb_has "$BB" chroot; then
  exec $BB chroot "$FLUX_CHROOT" /usr/bin/env -i $GUEST_ENV_ARGS "$@"
fi
exec /system/bin/chroot "$FLUX_CHROOT" /usr/bin/env -i $GUEST_ENV_ARGS "$@"
```

`guest_userspec` still requires `$BB` + `setuidgid`. If missing, die with “install util-linux in the guest or a BusyBox with setuidgid” — do not invent `--userspec`.

---

## 9. Files to touch (PR1)

| Area | Files |
| --- | --- |
| SSOT | `assets/scripts/chroot/fluxlinux_chroot.sh` (+ optional `resolve_bb.sh`) |
| Setup | `setup_guest_chroot.sh`, `setup_debian13_chroot.sh`, `setup_alpine_chroot.sh`, `setup_arch_chroot.sh`, legacy `debian/chroot/setup/*` |
| GUI | `start_*_gui.sh`, `stop_*_gui.sh`, `debian/chroot/start|stop/*` |
| Uninstall | `uninstall_*_chroot.sh` |
| Kotlin | `RootShell.kt` (probe + `FLUX_BB`), `OnboardingInstallRunner.kt`, `PrerequisitesScreen.kt` `BusyBoxInstallStep`, `OnboardingFlowScreen.kt` |
| Tests | `ChrootPathsTest.kt` + new `BusyBoxResolveTest` (fixture `--list` text) |
| Docs | tutorials, `docs/architecture.md` component table, this plan status |

Bump helper version (`v2.7` → `v2.8`) so `ensureChrootHelper` restages.

---

## 10. Verdict

| Question | Answer |
| --- | --- |
| Must the user flash BusyBox NDK? | **No**, if they have Magisk, KernelSU, or APatch. |
| Can we delete every BusyBox call? | Not in PR1. Mount/chroot can move to toybox; xz-tar and setuidgid cannot without extra work. |
| Can we use the app’s Termux `tar`/`xz`/`busybox`? | **No** (W^X / SELinux). Same reason the helper lives in `/data/local/tmp`. |
| Is the NDK module useless? | Useless as a **hard requirement**. Still a valid last resort if a manager binary is missing or fails the applet probe. |
| What should ship first? | PR1: one resolver, APatch+KSU everywhere, probe, drop the mandatory UI step. |

The module is a PATH installer. FluxLinux already has the multi-call binary on every supported root stack. Point `$BB` at it.
