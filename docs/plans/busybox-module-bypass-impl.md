# Implementation: Drop the BusyBox NDK module hard-requirement

| Field | Value |
| --- | --- |
| **Date** | 2026-08-15 |
| **Status** | **ITER 1 LANDED + DEVICE SMOKE** — iter 2 open (noexec pin last-resort). Review: `/tmp/grok-1000/grok-review-797a728f.md` |
| **Repo** | `/home/abhaybyte/repos/fluxlinux` |
| **Research SSOT** | [busybox-module-bypass.md](./busybox-module-bypass.md) (why; do not re-litigate) |
| **Worker prompt (iter 1)** | [worker-prompt-busybox-module-bypass.md](./worker-prompt-busybox-module-bypass.md) |
| **Worker prompt (iter 2)** | [worker-prompt-busybox-module-bypass-iter2.md](./worker-prompt-busybox-module-bypass-iter2.md) |
| **Audience** | Worker agent implementing chroot BusyBox resolve + UI |
| **Helper bump** | `fluxlinux-chroot v2.7` → **`v2.8`** |
| **Flavor** | `com.ivarna.fluxlinux` / `:app:testIvarnaDebugUnitTest` |

This file is the **implementation contract**. If this file and the tree disagree, this file wins for new work. If this file and the research plan disagree on *why*, the research plan wins; if they disagree on *what to ship*, this file wins.

---

## 0. Verdict (do not reopen)

The osm0sis **Busybox for Android NDK** Magisk/KSU/APatch module is **not required** for FluxLinux chroot.

Magisk (`/data/adb/magisk/busybox`), KernelSU (`/data/adb/ksu/bin/busybox`), and APatch (`/data/adb/ap/bin/busybox`) already ship a feature-complete static BusyBox. FluxLinux already invokes `$BB applet` (not bare `wget`/`tar` on PATH). The module only installs applet **symlinks** onto PATH.

**Ship:** one resolver, applet probe, pin to `/data/local/tmp/flux_busybox`, pass `FLUX_BB`, `/system/bin` mount/chroot fallback, stop telling users they MUST flash the zip.

### Iter 1 device (2026-08-15, duchamp / KSU)

Debian (Rooted) uninstall + reinstall in-app succeeded. Helper restaged to v2.8. Resolver present. `BB=/data/adb/ksu/bin/busybox` (no NDK module). XFCE4 / X11 reported working. Unit tests green.

**Device fact:** `/data/local/tmp` is **noexec**. Pin file is created (`chmod 755` → `[ -x ]` true) but exec is **127**. `resolve_bb` / helper embed correctly refuse to switch `BB` to the pin (`bb_ok` runs `--list`). The last-resort `[ -x PINNED ]` walk in every setup/start/stop/uninstall does **not**. That is iter 2.

### Iter 2 (must ship)

**B15.** Never assign `BB` to `/data/local/tmp/flux_busybox` (or any path) on `[ -x ]` alone. Require a real exec (`"$path" --list >/dev/null` or `bb_ok` if sourced). Last-resort walk must still reach KSU/APatch/Magisk when the pin exists but cannot exec. Same for `TermuxIntentFactory` legacy `BB=…flux_busybox`.

Uninstall resolver `cp` is best-effort (`|| true`); do not `&&` it in front of `sh uninstall`.

**Do not ship in this pass:** a BusyBox ELF in the APK, Java xz extract, PREFIX/Termux busybox as root, GNU `chroot --userspec`.

Device fact already in-tree: [fedora-chroot-flux-setuidgid.md](./fedora-chroot-flux-setuidgid.md) used **KSU busybox 1.36.1.1 (topjohnwu)** with no NDK module.

---

## 1. Locked contracts

Break any of these and the work is wrong even if tests are green.

| ID | Contract |
| --- | --- |
| **B1** | Candidate order is exactly: `FLUX_BB` (if `bb_ok`) → `/data/local/tmp/flux_busybox` → KSU → APatch → Magisk → NDK `system/xbin` → NDK `system/bin` → `/debug_ramdisk/busybox` → `/sbin/busybox` → `command -v busybox` (if `bb_ok`) → `/system/xbin/busybox` → `/system/bin/busybox`. First `bb_ok` wins. |
| **B2** | `bb_ok` = executable **and** not a Termux/Flux PREFIX path (`*com.termux*` / `*fluxlinux*` / `*nativecode*`) **and** `$bb --list` contains both `chroot` and `mount` (split on space/tab/newline). |
| **B3** | Never exec BusyBox from app-data (`/data/data/<pkg>/files/...`). W^X / SELinux. Same reason the helper lives in `/data/local/tmp`. |
| **B4** | After a successful resolve, best-effort `cp -f "$BB" /data/local/tmp/flux_busybox && chmod 755`. Pin failure is **not** fatal; keep the resolved path. |
| **B5** | Helper version stamp, `VERSION_STR`, and `ChrootPaths.CHROOT_HELPER_VERSION` all become **`fluxlinux-chroot v2.8`** in the **same** change. `ensureChrootHelper` restages on the version grep. |
| **B6** | Do **not** restore `chroot --userspec`. `guest_userspec` stays numeric `setuidgid`. If `setuidgid` is missing, die with the existing class of message — do not invent GNU flags. |
| **B7** | Root install gate stays **`RootShell.isRootAvailable()` only**. Missing BusyBox must **not** fail onboarding before the setup script runs. Setup/helper still exit non-zero if resolve fails (existing behavior). |
| **B8** | `BusyBoxInstallStep` stays step 6 of `PrerequisitesScreen` (do not renumber the wizard). Transform copy + probe; do not delete the step. |
| **B9** | Kotlin path list lives in `BusyBoxPaths.CANDIDATES`. Shell list must contain every Kotlin path. Unit test asserts both. |
| **B10** | `RootShell` stays su-only: no import of `ChrootCommandBuilder`. New BusyBox APIs live on `RootShell` + `BusyBoxPaths` (next to `ChrootPaths`). |
| **B11** | POSIX `/system/bin/sh` only in new/changed scripts. No bashisms in `resolve_bb.sh`. |
| **B12** | `/system/bin/mount` and `/system/bin/chroot` are **fallbacks** after `$BB` fails that applet, not the primary tools. Remount `/data` already prefers `/system/bin/mount` — keep that. |
| **B13** | Do not bundle a BusyBox binary in the APK (research Option B / PR3). |
| **B14** | Do not change proot, family scripts, `GuestLoginShell`, or host bootstrap. |

### Locked paths

```
PINNED            = /data/local/tmp/flux_busybox
RESOLVER_ON_DEVICE = /data/local/tmp/fluxlinux_resolve_bb.sh
RESOLVER_ASSET    = scripts/chroot/resolve_bb.sh
HELPER            = /data/local/tmp/fluxlinux_chroot.sh   (unchanged)
```

`BusyBoxPaths.CANDIDATES` (search after FLUX_BB + PINNED):

```
/data/adb/ksu/bin/busybox
/data/adb/ap/bin/busybox
/data/adb/magisk/busybox
/data/adb/modules/busybox-ndk/system/xbin/busybox
/data/adb/modules/busybox-ndk/system/bin/busybox
/debug_ramdisk/busybox
/sbin/busybox
/system/xbin/busybox
/system/bin/busybox
```

`command -v busybox` is **between** `/sbin/busybox` and `/system/xbin/busybox` in the shell resolver (B1). Kotlin probe walks `CANDIDATES` then `command -v` the same way — implement the Kotlin probe as `su -c` running the shell resolver so there is one algorithm.

### Locked Kotlin API

New file `app/src/main/kotlin/com/ivarna/fluxlinux/core/root/BusyBoxPaths.kt`:

```kotlin
object BusyBoxPaths {
    const val PINNED = "/data/local/tmp/flux_busybox"
    const val RESOLVER_ASSET = "scripts/chroot/resolve_bb.sh"
    const val RESOLVER_ON_DEVICE = "/data/local/tmp/fluxlinux_resolve_bb.sh"
    val CANDIDATES: List<String> = listOf(
        "/data/adb/ksu/bin/busybox",
        "/data/adb/ap/bin/busybox",
        "/data/adb/magisk/busybox",
        "/data/adb/modules/busybox-ndk/system/xbin/busybox",
        "/data/adb/modules/busybox-ndk/system/bin/busybox",
        "/debug_ramdisk/busybox",
        "/sbin/busybox",
        "/system/xbin/busybox",
        "/system/bin/busybox",
    )
}
```

Add to `RootShell` (names exact):

| Function | Thread | Behavior |
| --- | --- | --- |
| `resolveBusyBox(): String?` | background | Stage resolver if `Context` not available skip stage; `su` run resolver; cache first success; return absolute path or null |
| `probeBusyBox(forceClearCache: Boolean = false, onResult: (String?) -> Unit)` | any (callback main) | Same pattern as `probeRootAvailable` |
| `cachedBusyBox(): String?` | any | Cache only; **never** probe |
| `clearBusyBoxCache()` | any | |
| `seedBusyBoxForTest(path: String?)` | test | |
| `ensureBusyBoxResolver(ctx: Context): Boolean` | background | Stage asset → `RESOLVER_ON_DEVICE` via the same su `cp`/`chmod` pattern as `ensureChrootHelper` |

`resolveBusyBox()` **must** use `su` (same `resolveSuInvocation`). Do **not** `File.exists()` on `/data/adb/*` from app uid.

Probe command (locked shape; helper/resolver implements the logic):

```sh
export FLUX_PACKAGE='…'
if [ -f /data/local/tmp/fluxlinux_resolve_bb.sh ]; then
  . /data/local/tmp/fluxlinux_resolve_bb.sh
  resolve_bb && printf '%s\n' "$BB" && exit 0
fi
exit 1
```

If resolver is not staged yet, `ensureBusyBoxResolver` runs first when a `Context` is in hand (`ensureChrootHelper` already has ctx). From `OnboardingInstallRunner` / `ChrootCommandBuilder.build`, call `ensureBusyBoxResolver` then `resolveBusyBox`.

### Locked shell API (`resolve_bb.sh`)

File: `app/src/main/assets/scripts/chroot/resolve_bb.sh`

Shebang: `#!/system/bin/sh`  
No `set -e`. Safe to `.` source under `set -u` (only set `BB` / use `${FLUX_BB:-}`).

Functions (names exact):

```sh
bb_has() {   # $1=busybox $2=applet
  "$1" --list 2>/dev/null | tr ' \t' '\n' | grep -qx "$2"
}

bb_ok() {    # $1=path
  [ -n "${1:-}" ] && [ -x "$1" ] || return 1
  case "$1" in *com.termux*|*fluxlinux*|*nativecode*) return 1 ;; esac
  bb_has "$1" chroot && bb_has "$1" mount
}

resolve_bb() {
  BB=""
  if [ -n "${FLUX_BB:-}" ] && bb_ok "$FLUX_BB"; then
    BB="$FLUX_BB"
  elif bb_ok /data/local/tmp/flux_busybox; then
    BB=/data/local/tmp/flux_busybox
  else
    # walk B1 list (KSU … /sbin), then command -v, then /system/*
    …
  fi
  [ -n "$BB" ] || return 1
  if [ "$BB" != /data/local/tmp/flux_busybox ]; then
    cp -f "$BB" /data/local/tmp/flux_busybox 2>/dev/null \
      && chmod 755 /data/local/tmp/flux_busybox 2>/dev/null \
      && bb_ok /data/local/tmp/flux_busybox \
      && BB=/data/local/tmp/flux_busybox || true
  fi
  return 0
}
```

`resolve_bb` returns 0 and sets `BB` on success; returns 1 and leaves `BB` empty on failure.

### How other scripts consume it

`$(dirname "$0")/resolve_bb.sh` is **not** enough by itself. Live callers copy **only** the script they run:

- Setup: `RootShell.stageAsset` → `filesDir/staged_scripts/setup_*.sh` (no sibling resolver)
- Desktop: `start_gui_chroot.sh` copies **only** the guest start script + helper to `/data/local/tmp/` then `su -c sh /data/local/tmp/start_*_gui.sh` (`start_gui_chroot.sh` ~L205–229). It does **not** copy `resolve_bb.sh` and does **not** set `FLUX_BB`.
- Uninstall: `UninstallSessionFactory` copies **only** `uninstall_*.sh` to `/data/local/tmp` and `su` drops the env map.

So the consume block **must** still find Magisk/KSU/APatch when the sidecar is missing. Last-resort is an **inline B1 walk**, not `FLUX_BB`/PINNED only.

**Setup / start / stop** (fatal if still empty):

```sh
# resolve root BusyBox (manager built-in; NDK module not required)
_rr=""
for _c in \
  "${FLUX_RESOLVE_BB:-}" \
  "$(dirname "$0")/resolve_bb.sh" \
  /data/local/tmp/fluxlinux_resolve_bb.sh
do
  [ -n "$_c" ] && [ -f "$_c" ] && _rr="$_c" && break
done
if [ -n "$_rr" ]; then
  # shellcheck disable=SC1090
  . "$_rr"
  resolve_bb || true
fi
if [ -z "${BB:-}" ]; then
  # sidecar missing (desktop/uninstall/staged setup) — same B1 walk as resolve_bb
  if [ -n "${FLUX_BB:-}" ] && [ -x "$FLUX_BB" ]; then BB="$FLUX_BB"; fi
  if [ -z "${BB:-}" ] && [ -x /data/local/tmp/flux_busybox ]; then
    BB=/data/local/tmp/flux_busybox
  fi
  if [ -z "${BB:-}" ]; then
    for path in \
      /data/adb/ksu/bin/busybox \
      /data/adb/ap/bin/busybox \
      /data/adb/magisk/busybox \
      /data/adb/modules/busybox-ndk/system/xbin/busybox \
      /data/adb/modules/busybox-ndk/system/bin/busybox \
      /debug_ramdisk/busybox \
      /sbin/busybox \
      /system/xbin/busybox \
      /system/bin/busybox
    do
      if [ -x "$path" ]; then BB="$path"; break; fi
    done
  fi
fi
if [ -z "${BB:-}" ]; then
  echo "FluxLinux: ERROR — root-capable busybox not found" >&2
  exit 1
fi
```

The inline walk here may skip `bb_ok` (no sourced functions). That is acceptable for last-resort only. Prefer the sidecar when present.

**Uninstall is a different snippet.** After the same source + inline walk, **do not `exit 1` if `BB` is empty.** Umount with `$BB umount -l` then `/system/bin/umount -l` (today’s `|| umount -l`). Missing BusyBox must not leave mounts. Do not keep PATH-only detect as the *only* probe.

`fluxlinux_chroot.sh`:

1. After `set -u`, source resolver from `dirname $0` then `RESOLVER_ON_DEVICE` (same loop).
2. If source failed, **embed a copy** of `resolve_bb`/`bb_ok`/`bb_has` in the helper (helper must stay a single-file fallback because old devices may have only the helper copied). The embedded copy must implement B1–B4.
3. `resolve_bb` call stays where `resolve_bb` is today (`require_root` then resolve). On failure: `die "root-capable busybox not found"`.
4. Today’s `FLUX_BB` + `command -v` + short candidate loop is **deleted** and replaced by this.

### Helper v2.8 mount/chroot fallback (PR2, same change)

`bind_if_missing`:

```sh
$BB mount --bind "$_src" "$_dst" 2>/dev/null \
  || /system/bin/mount --bind "$_src" "$_dst" 2>/dev/null || true
```

`mount_type_if_missing`: same, `$BB mount …` then `/system/bin/mount …`.

`guest_chroot_env`:

```sh
if [ -n "${BB:-}" ] && bb_has "$BB" chroot; then
  exec $BB chroot "$FLUX_CHROOT" /usr/bin/env -i $GUEST_ENV_ARGS "$@"
fi
exec /system/bin/chroot "$FLUX_CHROOT" /usr/bin/env -i $GUEST_ENV_ARGS "$@"
```

`guest_userspec` still requires `bb_has "$BB" setuidgid`. No toybox equivalent.

Setup scripts that **must** succeed on bind (`|| goodbye`) may add the same `/system/bin/mount` fallback after `$BB mount` fails.

Extract stays `$BB tar`. If tar/xz fails, existing unxz-pipe in `setup_debian13_chroot.sh` stays. Do **not** invent Java extract.

### Kotlin injection of `FLUX_BB`

| Call site | Change |
| --- | --- |
| `RootShell.buildChrootHelperCmd` | If `cachedBusyBox()` non-null, add `export FLUX_BB='…';` next to the existing `FLUX_PACKAGE`/`FLUX_CHROOT` exports. |
| `ChrootCommandBuilder.build` | Call `ensureBusyBoxResolver` + `resolveBusyBox` (already on a session-open path that can hit su). Prefix `withPath` with `export FLUX_BB='…';` when non-null. `buildEnv` puts `FLUX_BB` when `cachedBusyBox()` non-null. |
| `OnboardingInstallRunner.runChroot` `rootCmd` | After root OK: `ensureBusyBoxResolver` + `resolveBusyBox()`. If non-null, `export FLUX_BB='…';` in `rootCmd`. **Do not** `postFail` when null. Fail text: drop “install BusyBox if needed” unless probe returned null **and** setup later failed — then the setup script’s own error is enough. Change the pre-setup fail string to: `"Root not available. Grant superuser to FluxLinux, then retry."` |
| `DesktopLauncher` chroot start/stop | Call `ensureBusyBoxResolver` + `resolveBusyBox()` before launching `start_gui_chroot.sh` / `stop_gui_chroot.sh`. |
| `start_gui_chroot.sh` / `stop_gui_chroot.sh` | Same `cp` pattern as the helper (~L205–229): copy `$TERMUX_HOME/resolve_bb.sh` (or staged) → `/data/local/tmp/fluxlinux_resolve_bb.sh`, `chmod 755`, and put `FLUX_BB=… FLUX_RESOLVE_BB=/data/local/tmp/fluxlinux_resolve_bb.sh` on the `su -c` env line next to `HELPER`. Do **not** assume the guest script can source a sibling — `$0` is `/data/local/tmp/start_*_gui.sh`. |
| `UninstallSessionFactory` inner `su` string | `ensureBusyBoxResolver` + `resolveBusyBox` first. Copy `$HOME/resolve_bb.sh` → `RESOLVER_ON_DEVICE` in the `cp` chain. Export `FLUX_BB` and `FLUX_RESOLVE_BB` **inside** the `su` string (`extraEnv` is dropped by `su` — see factory comment at L38). |

`ensureChrootHelper` **always** calls `ensureBusyBoxResolver(ctx)` on entry, **including** the version-stamp early return (`RootShell.kt:343–345`). Otherwise a deleted `/data/local/tmp/fluxlinux_resolve_bb.sh` is never repaired. Mirror helper self-heal: if on-device resolver missing, `cp` from `$HOME/resolve_bb.sh` or `staged_scripts/resolve_bb.sh`.

`resolveBusyBox()` has **no** `Context` and does **not** stage. Every Kotlin caller must call `ensureBusyBoxResolver` first. The locked probe (`su` + source sidecar) is `exit 1` if the sidecar is absent — that is why staging is mandatory for `probeBusyBox` / `BusyBoxInstallStep`. Session login still works via helper embed even if the sidecar is gone.

### UI (locked copy)

**`BusyBoxInstallStep`** (`PrerequisitesScreen.kt`):

- Keep the step in the wizard.
- On enter: `RootShell.probeBusyBox` (async). While probing, show “Looking for BusyBox…”.
- **Probe success:** title can stay “Step 6: BusyBox”. Body: *“Using the BusyBox that came with your root manager. You do not need to flash a separate module.”* Show the resolved path in monospace (small). **Continue enabled. No checkbox.**
- **Probe fail + rooted:** keep Download Module card (same XDA URL is fine). Checkbox: *“I will install BusyBox NDK only if chroot setup fails”* — Continue still allowed (B7). Soften MUST → *“If install fails later, flash BusyBox NDK in Magisk / KernelSU / APatch.”*
- **Not rooted:** keep today’s skip card.

Do **not** use `RootUtils` for the BusyBox probe. `RootUtils` may remain for the existing `isRooted` remember-block if you do not want to restyle the whole page; prefer `RootShell.probeRootAvailable` if you touch that line anyway.

**`OnboardingFlowScreen`** chroot blurb (exact replacement):

```
Rooted path: grant superuser to FluxLinux. Magisk, KernelSU, and APatch already include BusyBox — a separate module is not required.
```

**`TerminalComponent.CHROOT_ROOT_SHELL` KDoc:** `Root + SSOT chroot helper.` (drop “BusyBox NDK”).

**`docs/architecture.md` table:** `Root` (not `Root + BusyBox NDK`).

Tutorials (`docs/tutorial/setup_fluxlinux.md`, `setup_debian_chroot.md`): BusyBox step becomes optional / “only if setup cannot find a manager BusyBox.” Do not claim it is required.

### Legacy `TermuxIntentFactory`

Replace bare `busybox chroot` with:

```sh
BB="${FLUX_BB:-/data/local/tmp/flux_busybox}";
[ -x "$BB" ] || BB="$(command -v busybox)";
"$BB" chroot …
```

Do not rewrite the rest of that factory.

---

## 2. Out of scope

- Shipping `busybox-arm64-selinux` (or any ELF) in the APK
- Java / Commons Compress xz extract
- Exec of `PREFIX/bin/tar`, `PREFIX/bin/xz`, or PREFIX busybox as root
- GNU `chroot --userspec`
- Proot install/run, family scripts, XFCE, ExtraKeys, guest shell toggle
- Rewriting `RootUtils` globally
- Renumbering `PrerequisitesScreen` steps
- Play flavor (`zenithblue`) except shared main source
- `git push`

---

## 3. Work sequence

Implement in this order. Run unit tests after phase 1 and after phase 3.

```bash
./gradlew :app:testIvarnaDebugUnitTest --no-daemon
```

### Phase 1 — Paths + resolver + helper v2.8 (no UI)

1. Add `BusyBoxPaths.kt`.
2. Add `resolve_bb.sh`.
3. Replace `resolve_bb` in `fluxlinux_chroot.sh`; bump to **v2.8**; add `/system/bin` mount/chroot fallbacks (B12).
4. `ChrootPaths.CHROOT_HELPER_VERSION = "fluxlinux-chroot v2.8"`.
5. `RootShell`: cache + `ensureBusyBoxResolver` + `resolveBusyBox` / `probeBusyBox` / `cachedBusyBox` / seed/clear; `ensureChrootHelper` also stages resolver; `buildChrootHelperCmd` exports `FLUX_BB` when cached.
6. `HostScriptDeployer.HOST_SCRIPTS`: add `resolve_bb.sh` → `scripts/chroot/resolve_bb.sh` (required).
7. Tests in §4 (paths, helper version, resolver text, reject PREFIX).

### Phase 2 — Every script drops its private detector

Replace local loops in **all** of:

| File |
| --- |
| `assets/scripts/chroot/setup_guest_chroot.sh` |
| `assets/scripts/chroot/setup_debian13_chroot.sh` |
| `assets/scripts/chroot/setup_alpine_chroot.sh` |
| `assets/scripts/arch/chroot/setup/setup_arch_chroot.sh` |
| `assets/scripts/chroot/start_guest_gui.sh` |
| `assets/scripts/chroot/start_debian13_gui.sh` |
| `assets/scripts/chroot/start_alpine_gui.sh` |
| `assets/scripts/chroot/stop_guest_gui.sh` |
| `assets/scripts/chroot/stop_debian13_gui.sh` |
| `assets/scripts/chroot/stop_alpine_gui.sh` |
| `assets/scripts/chroot/uninstall_guest_chroot.sh` |
| `assets/scripts/chroot/uninstall_debian13_chroot.sh` |
| `assets/scripts/chroot/uninstall_alpine_chroot.sh` |
| `assets/scripts/debian/chroot/setup/setup_debian13_chroot.sh` |
| `assets/scripts/debian/chroot/setup/setup_debian_chroot.sh` |
| `assets/scripts/debian/chroot/start/start_debian13_kde_gui.sh` |
| `assets/scripts/debian/chroot/start/start_debian13_kde_gui_software.sh` |
| `assets/scripts/debian/chroot/start/start_debian13_kde_gui_turnip.sh` |
| `assets/scripts/debian/chroot/stop/stop_debian13_gui.sh` |
| `assets/scripts/debian/chroot/stop/stop_debian13_kde_gui.sh` |
| `assets/scripts/debian/chroot/setup/uninstall_debian13.sh` |
| `assets/scripts/debian/chroot/setup/uninstall_debian_chroot.sh` |
| `assets/scripts/chroot/start_gui_chroot.sh` (copy resolver + `FLUX_BB` on `su -c`; no private detector today) |
| `assets/scripts/chroot/stop_gui_chroot.sh` (same) |

`start_gui_chroot.sh`, `chroot_processes.sh`, and `chroot_size.sh` have **no** host BusyBox detector. Only the two GUI wrappers need the copy/`FLUX_BB` change. Uninstall leftovers under `debian/chroot/setup/` are not the live Kotlin path (`DistroInstallProfile` uses `scripts/chroot/uninstall_*`) but must still get the **uninstall** snippet so PATH-only detect does not remain.

Setup scripts: keep `|| goodbye` on critical binds; add `/system/bin/mount` after `$BB mount` failure on those lines.

### Phase 3 — Kotlin inject + UI + docs

1. `OnboardingInstallRunner.runChroot` — `FLUX_BB` export; fail string (B7).
2. `ChrootCommandBuilder.build` / `buildEnv` — `FLUX_BB`.
3. `DesktopLauncher` — `ensureBusyBoxResolver` + `resolveBusyBox` before chroot start/stop.
4. `UninstallSessionFactory` — stage resolver + `FLUX_BB` **inside** the `su` string.
5. `TermuxIntentFactory` bare `busybox` fix.
6. `BusyBoxInstallStep` probe + copy. Note: live nav uses `OnboardingFlowScreen` only (`MainActivity.kt`); `PrerequisitesScreen` is leftover wizard — still transform step 6 (B8) but the **user-visible** gate is the onboarding blurb.
7. `OnboardingFlowScreen` blurb.
8. `TerminalComponent` KDoc.
9. Tutorials + `docs/architecture.md`.
10. `CHANGELOG.md` Unreleased:

```
- feat: chroot uses Magisk/KernelSU/APatch built-in BusyBox — NDK module no longer required
- fix: fluxlinux-chroot v2.8 — shared BusyBox resolver (APatch + KSU paths, applet probe, pin to /data/local/tmp/flux_busybox)
```

### Phase 4 — Tests + optional device smoke

See §4 and §5. Do not claim device pass without adb output.

---

## 4. Unit tests (required)

### `ChrootPathsTest`

- `helper_version_is_v27` → **`helper_version_is_v28`** asserting `"fluxlinux-chroot v2.8"`.
- Keep setuidgid / no `--userspec` assertions.

### New `BusyBoxPathsTest` (JVM, no Android)

| Case | Expected |
| --- | --- |
| `CANDIDATES` order | Exact list in §1 |
| First three | ksu, ap, magisk (APatch must not be last or missing) |
| `PINNED` / resolver constants | Exact strings |
| Helper asset + `resolve_bb.sh` contain every `CANDIDATES` path | `readText().contains(path)` |
| Helper + resolver reject PREFIX | contain `*com.termux*` / `*fluxlinux*` / `*nativecode*` |
| Helper + resolver require `chroot` and `mount` in `bb_ok` / `bb_has` | |
| Helper has **no** `chroot --userspec` | already in `ChrootPathsTest` |
| Helper `VERSION_STR` / header contain `v2.8` | |
| Helper mount fallback mentions `/system/bin/mount` | |
| Helper `guest_chroot_env` mentions `/system/bin/chroot` | |
| No setup/start/stop/uninstall under `assets/scripts/chroot/` (and the debian/arch lists in Phase 2) still has a candidate list **without** `/data/adb/ap/bin/busybox` **unless** it sources `resolve_bb.sh` | Prefer: those files contain `resolve_bb.sh` or `resolve_bb` |
| `TermuxIntentFactory.kt` source does not contain the bare token `busybox chroot` | |

### New `RootShellBusyBoxTest` (or extend an existing RootShell test)

- `seedBusyBoxForTest("/data/adb/ksu/bin/busybox")` → `cachedBusyBox()` returns that.
- `clearBusyBoxCache()` → null.
- If there is an existing test that snapshots `buildChrootHelperCmd`, assert `FLUX_BB=` appears after seed. If `buildChrootHelperCmd` is private, do not make it public just for the test; cover via `ChrootCommandBuilder.buildRootInner` remaining stable **and** a small internal test of export string if you already have a package-visible hook. **Do not** break RootShell’s su-only rule.

`GuestLoginShell` / `ChrootCommandBuilderTest` must stay green (no argv change except optional `FLUX_BB` prefix on the **outer** `withPath`, not inside `buildRootInner`).

**Do not** put `FLUX_BB` inside `buildRootInner` — keep that function’s contract. Export happens in `build()`’s `withPath` wrapper only:

```kotlin
val bb = RootShell.cachedBusyBox()
val exportBb = if (!bb.isNullOrEmpty()) "export FLUX_BB='$bb'; " else ""
val withPath = "${exportBb}export FLUX_CHROOT='$chrootPath'; $rootInner"
```

---

## 5. Device smoke (if `adb devices` shows a device)

Not a substitute for unit tests. If no device, write that in the wrap-up and stop.

**Minimum (KSU or Magisk, NDK module not required):**

1. `adb shell su -c 'ls -l /data/adb/ksu/bin/busybox /data/adb/magisk/busybox /data/adb/ap/bin/busybox'` — note which exist.
2. After install of the new APK: open any **already installed** chroot tab (Fedora or Debian). Helper on device is `fluxlinux-chroot v2.8`. Login works.
3. `adb shell su -c 'ls -l /data/local/tmp/flux_busybox /data/local/tmp/fluxlinux_resolve_bb.sh'` — pin + resolver present after first chroot open.
4. Cold desktop: reboot or `rm /data/local/tmp/fluxlinux_resolve_bb.sh /data/local/tmp/flux_busybox`, then Start Desktop on an existing chroot **without** opening a terminal tab first. XFCE must still start (inline B1 walk + wrapper copy).
5. Optional: `adb shell su -c '/data/local/tmp/flux_busybox --list'` contains `chroot` and `mount`.

**Do not** uninstall a working chroot just to re-install unless the user asked. Fresh chroot extract is optional and slow.

**APatch:** only if the device is APatch. Do not fail the task for lack of APatch hardware.

---

## 6. Acceptance

Done when:

- [ ] `./gradlew :app:testIvarnaDebugUnitTest --no-daemon` green
- [ ] Helper is v2.8; `ChrootPaths.CHROOT_HELPER_VERSION` matches
- [ ] `resolve_bb.sh` exists; setup/start/stop source it **and** inline-walk if sidecar missing
- [ ] Uninstall does not `exit 1` solely because `BB` is empty; umount falls back to `/system/bin/umount`
- [ ] `start_gui_chroot.sh` / `stop_gui_chroot.sh` copy resolver + export `FLUX_BB`
- [ ] `DesktopLauncher` + `UninstallSessionFactory` call `ensureBusyBoxResolver`
- [ ] APatch + KSU + Magisk paths are in Kotlin **and** shell
- [ ] PREFIX busybox still rejected
- [ ] No `chroot --userspec`
- [ ] UI no longer says users **MUST** flash NDK when probe succeeds
- [ ] Onboarding chroot blurb matches §1
- [ ] CHANGELOG Unreleased updated
- [ ] No BusyBox ELF added to the APK
- [ ] No `git push`

---

## 7. Files (expected touch set)

**New**

- `app/src/main/assets/scripts/chroot/resolve_bb.sh`
- `app/src/main/kotlin/com/ivarna/fluxlinux/core/root/BusyBoxPaths.kt`
- `app/src/test/java/com/ivarna/fluxlinux/core/root/BusyBoxPathsTest.kt`

**Kotlin**

- `ChrootPaths.kt` (version only)
- `RootShell.kt`
- `ChrootCommandBuilder.kt` (`build` / `buildEnv` only)
- `OnboardingInstallRunner.kt`
- `HostScriptDeployer.kt`
- `DesktopLauncher.kt`
- `UninstallSessionFactory.kt`
- `TermuxIntentFactory.kt`
- `PrerequisitesScreen.kt` (`BusyBoxInstallStep`)
- `OnboardingFlowScreen.kt` (one string)
- `TerminalComponent.kt` (KDoc)

**Scripts** — helper + Phase 2 table

**Tests** — `ChrootPathsTest.kt`

**Docs**

- `CHANGELOG.md`
- `docs/architecture.md`
- `docs/tutorial/setup_fluxlinux.md`
- `docs/tutorial/setup_debian_chroot.md`
- this file status after landing (`IMPLEMENTED` + date)

Do not edit `busybox-module-bypass.md` research sections except a one-line status pointer if you want.

---

## 8. Risks (known, do not over-engineer)

| Risk | Mitigation already in contract |
| --- | --- |
| Stub `/system/bin/busybox` on PATH | `bb_ok` requires `chroot`+`mount`; `/system` is last |
| Magisk Hide hides `/data/adb` from app uid | Probe and resolve run under `su` |
| Pin copy fails | Non-fatal (B4) |
| Resolver not staged, only helper copied | Helper embeds fallback functions |
| Desktop/uninstall copy only their own script | Inline B1 walk + wrappers/`UninstallSessionFactory` stage resolver |
| `tar -J` missing on a weird binary | Existing unxz pipe; fail extract with current error |
| Fedora flux without `setuidgid` | Existing die; new Fedora has util-linux `su` |

If a real device has **no** manager BusyBox at the three paths and no NDK module, setup still fails — that is correct. The Download Module link remains as last resort.
