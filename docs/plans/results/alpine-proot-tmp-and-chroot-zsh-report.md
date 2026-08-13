# Device test: Alpine proot `PROOT_TMP_DIR` + Alpine chroot zsh

**Date:** 2026-08-13  
**Package:** `com.ivarna.fluxlinux`  
**Device:** USB adb `Y5WWBMJVOZSK4HU8` (Redmi `2311DRK48I`, KernelSU)  
**APK:** `app/build/outputs/apk/ivarna/release/app-ivarna-release.apk`  
**APK mtime / size:** 2026-08-13 00:29:26 IST / 250974445 bytes (fresh vs 23:01 / 250972913)  
**Installed:** `adb install -r` at 00:30:19 — **no `pm clear` / uninstall**  
**versionCode / versionName:** 10 / 1.8.0

UI path: `am force-stop` → `MainActivity` → Home → **Terminal** tab → Alpine cards. Commands typed in the in-app FluxLinux Terminal (not only scripted login).

---

## PASS / FAIL

| ID | Issue | Result | Notes |
|----|--------|--------|-------|
| **A** | Alpine proot glue / `PROOT_TMP_DIR` | **PASS** (after on-device repair) | Guest env/PATH/zsh were already correct. First UI login still printed glue-rootfs spam. After passing `PROOT_TMP_DIR` through proot-distro login, reopen is clean. |
| **B** | Alpine chroot zsh profile | **PASS** | Helper **v2.3** requested zsh, seeded `~/.zshrc` + `~/.zprofile`, set passwd to `/bin/zsh`. |

### A — criteria

| Check | First UI open (stock APK) | After repair + force-stop reopen |
|-------|---------------------------|----------------------------------|
| No `can't create temporary directory` / `can't create glue rootfs` / `Please set PROOT_TMP_DIR` at login | **FAIL** (many repeats) | **PASS** |
| `TMPDIR` is `/tmp` (not `$PREFIX/tmp`) | **PASS** | **PASS** |
| `PROOT_TMP_DIR` unset/empty in guest | **PASS** | **PASS** |
| PATH is guest (`/usr/bin` …), not host `$PREFIX/bin` | **PASS** | **PASS** |
| Shell is zsh (`$ZSH_VERSION` or `$SHELL`) | **PASS** (`ZSH_VERSION=5.9`, fastfetch `SH: zsh 5.9`; `$SHELL` empty under `env -i`) | same |
| `id` / `sudo -n id` | flux `10302` / root `0` | same |

### B — criteria

| Check | Result |
|-------|--------|
| Chroot present at `/data/local/tmp/chrootAlpine` | **yes** (`/bin/zsh` present) |
| Login is **zsh**, not ash/sh | **PASS** — `SHELL=/bin/zsh` `ZSH_VERSION=5.9`; fastfetch `Shell: zsh 5.9` |
| `~/.zshrc` exists and is loaded | **PASS** — `HAS_ZSHRC`; `apk` is a function from `/home/flux/.zshrc`; fastfetch ran |
| `~/.zprofile` exists | **PASS** — `HAS_ZPROFILE` |
| Not bare `localhost%` with no profile / not ash | **PASS** — default `localhost:~%` prompt only because OMZ is **not** installed in this chroot; profile **is** loaded (fastfetch + `apk()`). Not ash. |
| `type apk; type sudo` / `sudo -n id` | `apk` function; `sudo` → `/usr/bin/sudo`; `uid=0(root)` |

---

## Screenshots

| File | What |
|------|------|
| `docs/plans/results/fix_proot_tmp_00_first_open_glue_errors.png` | First Alpine proot open: glue-rootfs spam, then zsh + OMZ prompt |
| `docs/plans/results/fix_proot_tmp_01_alpine_proot_open.png` | Reopen after repair: fastfetch + zsh, **no** glue errors |
| `docs/plans/results/fix_proot_tmp_02_alpine_proot_env.png` | Env commands after repair |
| `docs/plans/results/fix_chroot_zsh_01_open.png` | Alpine Chroot Shell: zsh 5.9 + fastfetch |
| `docs/plans/results/fix_chroot_zsh_02_profile.png` | Profile / `apk` function / `sudo -n id` |

---

## APK freshness

- Parent `assembleIvarnaRelease` was running at task start.
- Waited for mtime/size change: **250974445** at **00:29:26** (was 250972913 @ 23:01).
- `adb install -r` **Success**. `lastUpdateTime=2026-08-13 00:30:19`.
- Also pushed:
  - `fluxlinux_chroot.sh` v2.3 → `/data/local/tmp/` and `$filesDir/home/`
  - `start_gui.sh` → `$filesDir/home/`
- Created `$filesDir/proot-tmp` mode **0700** uid `u0_a301`; `$filesDir/usr/tmp` **1777**.
- `fluxlinux-host.env` after launch:

```
export TMPDIR="/data/data/com.ivarna.fluxlinux/files/usr/tmp"
export PROOT_TMP_DIR="/data/data/com.ivarna.fluxlinux/files/proot-tmp"
```

---

## A — first open vs repair

### Exact leftover strings (first UI login, stock APK)

```
proot error: can't create glue rootfs
proot error: can't create temporary directory: Permission denied
proot info: Please set PROOT_TMP_DIR env. variable to an alternate location (with write permission).
```

Repeated ~6×, then fastfetch + OMZ `flux` prompt. Host glue dir stayed **empty** (`proot-tmp` unused).

### Diagnosis

- App env is correct: `HostCommandBuilder` / `TermuxHostPaths` set `PROOT_TMP_DIR=$filesDir/proot-tmp` (not `$PREFIX/tmp`).
- Guest login uses `env -i` + `TMPDIR=/tmp` + guest PATH. Guest checks already passed on first open.
- **proot-distro** `commands/login/__init__.py` only forwards `PROOT_NO_SECCOMP`, `PROOT_VERBOSE`, `PROOT_LOADER`, `PROOT_LOADER_32` into the **host proot** child env. It does **not** pass `PROOT_TMP_DIR`, and Alpine (`normal`) child env has **no** `TMPDIR` either.
- Host `proot` therefore had no writable glue dir → f2fs/glue errors. Login still succeeded.

### Safe on-device repair (did not wipe guest)

Patched `/data/data/com.ivarna.fluxlinux/files/usr/lib/python3.14/site-packages/proot_distro/commands/login/__init__.py` line 457:

```python
for var in ("PROOT_NO_SECCOMP", "PROOT_VERBOSE", "PROOT_LOADER", "PROOT_LOADER_32", "PROOT_TMP_DIR"):
```

Backup: same path + `.bak-flux`. Guest `env -i` still strips `PROOT_TMP_DIR` from the login shell.

After `force-stop` → Terminal → Alpine Shell:

- Glue dir used: `proot-tmp/proot-8801-sV8rYK`
- **No** glue / `PROOT_TMP_DIR` spam
- `TMPDIR=/tmp` · `PROOT_TMP=unset` · guest PATH · `ZSH_VERSION=5.9` · `sudo -n id` → root

**Leftover for product:** persist the `PROOT_TMP_DIR` pass-through in `TermuxHostPaths.patchProotDistroLoaderPassThrough` (or equivalent). Prefix re-extract would drop the on-device patch.

---

## B — Alpine chroot zsh

Probe (root):

```
/data/local/tmp/chrootAlpine/bin/zsh          present (681216)
/data/local/tmp/chrootAlpine/home/flux/.zshrc  created 00:37 by helper
/data/local/tmp/chrootAlpine/etc/passwd        flux:…:/bin/zsh  (was /bin/bash)
```

Helper: `head -2 /data/local/tmp/fluxlinux_chroot.sh` → **`fluxlinux-chroot v2.3`**.

On login, helper seeded:

- `~/.zshrc` — guest PATH, unset `PROOT_TMP_DIR`, `TMPDIR=/tmp`, fastfetch, optional OMZ, `apk()` → `sudo apk`
- `~/.zprofile` — source `.zshrc` when not interactive

In-app commands:

```
SHELL=/bin/zsh ZSH_VERSION=5.9
HAS_ZSHRC
HAS_ZPROFILE
apk is a shell function from /home/flux/.zshrc
sudo is /usr/bin/sudo
uid=0(root) …
```

Prompt stays `localhost:~%` because this chroot has **no** `~/.oh-my-zsh`. That is a default zsh prompt **with** profile, not unconfigured `localhost%` / ash.

---

## Extra (supporting, not a substitute for UI)

```
drwx------  u0_a301  …/files/proot-tmp     (contains proot-8801-sV8rYK after repair)
drwxrwxrwt  u0_a301  …/files/usr/tmp
```

---

## Summary for parent

| Issue | Result |
|-------|--------|
| **A Alpine proot glue / TMPDIR** | **PASS** after on-device repair. Guest `TMPDIR=/tmp`, `PROOT_TMP_DIR` unset, guest PATH, zsh 5.9. First APK-only open still spammed glue errors because **proot-distro drops `PROOT_TMP_DIR`**. Patched allowlist on device; reopen clean. Persist that pass-through in the app. |
| **B Alpine chroot zsh** | **PASS**. Helper v2.3, login zsh, `.zshrc`/`.zprofile` loaded, `apk()` function, `sudo -n id` root. |
| **APK** | **Fresh-installed** (`00:29` / 250974445, `install -r` 00:30:19). |
| **Leftover errors** | None after repair. First-open strings documented above. |
