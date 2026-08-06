# Plan: Custom Termux Packages for FluxLinux Native Terminal

**Date:** 2026-08-05  
**Status:** **COMPLETE** — full termux-lib SSOT + `bootstrap.tar` + jniLibs for **both** `com.ivarna.fluxlinux` and `com.zenithblue.fluxlinux` (2026-08-05)  
**Reference implementation:** `~/repos/termux-lib` (`assemble_bootstrap.py` `PACKAGES` = SSOT)  
**Goal:** Build a Termux userland (bootstrap + jniLibs) for an **embedded native terminal** inside FluxLinux, with correct `$PREFIX` for **both** shipped application IDs.

### Progress log

| When | What |
|------|------|
| 2026-08-05 | Added `native/` layout, scripts (`build_packages_for_appid`, `set_termux_package_name`, `verify_deb_prefix`, `assemble_bootstrap`, `verify_bootstrap`), package list, gitignore. |
| 2026-08-05 | `FORCE_DEPS=1` dry-run: built **bash + full dep chain** for `com.ivarna.fluxlinux`. Verified deb paths + `RUNPATH` under `/data/data/com.ivarna.fluxlinux/files/usr`. |
| 2026-08-05 | **Terminal+proot stack complete** for `com.ivarna.fluxlinux`: `libandroid-shmem`, `libtalloc` (pinned 2.4.2 via Debian; samba.org unreachable), `proot`, `python`, `proot-distro` + deps. Assembled `bootstrap-terminal-proot.tar` (~82 MB) + jniLibs. ~143 mirrored debs. |
| 2026-08-05 | **Verify vs termux-lib:** 45/62 SSOT debs present for ivarna; 17 missing (GUI/audio/XKB + TLS extras). Prefix/RUNPATH OK for terminal stack. `com.zenithblue.fluxlinux` not started. |
| 2026-08-05 | **This session:** force-rebuild 17 missing for ivarna → assemble full `bootstrap.tar` → FORCE rebuild full SSOT for zenithblue → assemble + verify both. |
| 2026-08-05 | **ivarna COMPLETE:** SSOT 62/62 debs; full `bootstrap.tar` 122 MB; jniLibs; pulseaudio + xkb; `verify_bootstrap.sh` **PASS**. Patched `libacl` SRCURL→Debian (savannah 502). `libtalloc` remains Debian-pinned. Build script: reuse docker container by default (`RECREATE_BUILDER=1` to reset). |
| 2026-08-05 | **zenithblue COMPLETE:** SSOT 62/62 debs; full `bootstrap.tar` 122 MB; jniLibs; `verify_bootstrap.sh` **PASS**. `libcap` built from local `file://` tarball (kernel.org DNS broken in container). Subpackage names (`curl`, `xz-utils`, `flac`, `libltdl`) come from parents `libcurl`/`liblzma`/`libflac`/`libtool`. |

### Dual-appid completion status (2026-08-05)

| App ID | SSOT debs | bootstrap.tar | jniLibs | verify_bootstrap |
|--------|-----------|---------------|---------|------------------|
| `com.ivarna.fluxlinux` | **62/62** (~196 mirrored) | **122 MB** | bash/proot/loader{,32} | **PASS** |
| `com.zenithblue.fluxlinux` | **62/62** (~196 mirrored) | **122 MB** | bash/proot/loader{,32} | **PASS** |

**Artifacts:**
```text
native/output/com.ivarna.fluxlinux/*.deb
native/bootstrap/com.ivarna.fluxlinux/{bootstrap.tar,jniLibs/arm64-v8a/}
native/output/com.zenithblue.fluxlinux/*.deb
native/bootstrap/com.zenithblue.fluxlinux/{bootstrap.tar,jniLibs/arm64-v8a/}
native/package-lists/termux-lib-ssot.txt   # exact termux-lib PACKAGES
```

**Upstream pin/patches (shared termux-packages tree):**
- `packages/libtalloc/build.sh` — Debian 2.4.2 (samba.org timeout)
- `packages/libacl/build.sh` — Debian orig tar.xz (savannah 502)
- `packages/libcap/build.sh` — `file://…/libcap-2.69.tar.xz` local seed (kernel.org DNS in Docker)

**Follow-on (runtime embed):** See **[embedded-terminal-bootstrap-proot-chroot.md](./embedded-terminal-bootstrap-proot-chroot.md)** — package bootstrap/jniLibs/rootfs into APK flavors, `TermuxHostPaths` SSOT, in-app **termux-flux-terminal** (proot) + **chroot-root-shell**, shared Debian rootfs install, Distro cards/tutorials.

---

## 0. Execution plan (this session — do not stop)

Reference SSOT: **`~/repos/termux-lib/assemble_bootstrap.py` → `PACKAGES` (62 packages)**  
Package lists in repo:

| List | Path | Role |
|------|------|------|
| Full SSOT | `native/package-lists/termux-lib-ssot.txt` | Exact termux-lib host bootstrap set |
| Host extended | `native/package-lists/bootstrap-host.txt` | SSOT + extras (`gawk`, `gzip`, `less`, …) |
| Terminal-only | `native/package-lists/terminal-proot.txt` | Shell + proot (no pulse/xkb) |
| Ivarna gap | `native/package-lists/ivarna-missing.txt` | 17 packages still nativecode-prefix only |

### Phase A — `com.ivarna.fluxlinux` (complete full host)

1. **Force-rebuild missing 17** (shared `termux-packages/output` still has `com.zenithblue.nativecode` debs for these; must use `FORCE=1`):
   - TLS: `libidn2`, `libunistring`, `libnettle`, `libunbound`
   - Audio chain: `libogg`, `flac`, `libvorbis`, `libopus`, `speexdsp`, `libsndfile`, `libltdl`, `libcap`, `libevent`, `dbus`, `glib`, `pulseaudio`
   - XKB: `xkeyboard-config`
2. **Mirror** all debs whose archive paths match `com.ivarna.fluxlinux` → `native/output/com.ivarna.fluxlinux/`.
3. **Verify** every SSOT package has an ivarna-prefix deb (62/62).
4. **Assemble full bootstrap:**
   ```bash
   ./scripts/assemble_bootstrap.py --package-name com.ivarna.fluxlinux \
     --list native/package-lists/termux-lib-ssot.txt --mode full
   ```
5. **Verify bootstrap:** `./scripts/verify_bootstrap.sh com.ivarna.fluxlinux`  
   Required: bash, python, proot, proot-distro, pulseaudio, loader.apk, jniLibs, RUNPATH.

**Exit criteria A:** full `bootstrap.tar` + jniLibs PASS for ivarna.

### Phase B — `com.zenithblue.fluxlinux` (full rebuild)

1. **Set package name** to `com.zenithblue.fluxlinux` (`set_termux_package_name.sh`).
2. **Force rebuild full SSOT** (all ELFs/paths must embed Play app id; cannot reuse ivarna debs):
   ```bash
   FORCE=1 CONTINUE_ON_FAIL=1 ./scripts/build_packages_for_appid.sh \
     com.zenithblue.fluxlinux --list termux-lib-ssot
   ```
   Prefer `FORCE=1` package-by-package (faster than `FORCE_DEPS=1` if ivarna-built headers already exist); escalate to `FORCE_DEPS=1` on seed failures.
3. **Mirror** → `native/output/com.zenithblue.fluxlinux/`.
4. **Assemble + verify** same as Phase A for zenithblue.

**Exit criteria B:** full `bootstrap.tar` + jniLibs PASS for zenithblue.

### Phase C — documentation / handoff (same session if time)

- Update this progress log with final counts, sizes, log paths.
- Note: Gradle product flavors + app asset wiring remain **follow-on** (not blocking package artifacts).

### Critical constraints (from termux-lib)

| Constraint | Action |
|------------|--------|
| Never mix prefixes | Mirror only after `verify_deb_prefix.sh` PASS |
| Shared `termux-packages` tree | FORCE rebuild when switching app id; nativecode debs in `output/` are invalid for Flux |
| Docker volume | Build into `termux-packages/output/`; mirror by path prefix into `native/output/<appId>/` |
| W^X | jniLibs: `libbash.so`, `libproot.so`, `libloader.so`, `libloader32.so` |
| loader.apk | From `native/loader/shell-loader.apk` → `$PREFIX/libexec/termux-x11/loader.apk` |
| `libtalloc` | Keep Debian 2.4.2 pin if samba.org still times out |

### Artifact targets

```text
native/output/com.ivarna.fluxlinux/*.deb
native/bootstrap/com.ivarna.fluxlinux/bootstrap.tar
native/bootstrap/com.ivarna.fluxlinux/jniLibs/arm64-v8a/{libbash,libproot,libloader,libloader32}.so

native/output/com.zenithblue.fluxlinux/*.deb
native/bootstrap/com.zenithblue.fluxlinux/bootstrap.tar
native/bootstrap/com.zenithblue.fluxlinux/jniLibs/arm64-v8a/...
```

### Command cheat-sheet

```bash
# A1 — missing for ivarna
FORCE=1 CONTINUE_ON_FAIL=1 ./scripts/build_packages_for_appid.sh \
  com.ivarna.fluxlinux --list ivarna-missing

# A4 — assemble full
./scripts/assemble_bootstrap.py --package-name com.ivarna.fluxlinux \
  --list native/package-lists/termux-lib-ssot.txt --mode full
./scripts/verify_bootstrap.sh com.ivarna.fluxlinux

# B — zenithblue full
FORCE=1 CONTINUE_ON_FAIL=1 ./scripts/build_packages_for_appid.sh \
  com.zenithblue.fluxlinux --list termux-lib-ssot
./scripts/assemble_bootstrap.py --package-name com.zenithblue.fluxlinux \
  --list native/package-lists/termux-lib-ssot.txt --mode full
./scripts/verify_bootstrap.sh com.zenithblue.fluxlinux
```

---

## 1. Context

### 1.1 Current FluxLinux model

Today FluxLinux is an **orchestrator**: it drives external **Termux** + **Termux:X11** via scripts and intents.

| Channel | Application ID | Notes |
|---------|----------------|-------|
| F-Droid / open APK | `com.ivarna.fluxlinux` | Default `applicationId` in `app/build.gradle.kts` |
| Google Play | `com.zenithblue.fluxlinux` | Store listing / separate product identity |

Paths hard-coded into Termux packages always include the app package name:

```text
PREFIX = /data/data/<APPLICATION_ID>/files/usr
HOME   = /data/data/<APPLICATION_ID>/files/home
```

Stock Termux binaries are compiled for `com.termux`. They **cannot** run natively under either FluxLinux ID without rebuild (or fragile proot/patchelf hacks).

### 1.2 What termux-lib already proved

In `~/repos/termux-lib` (`com.zenithblue.nativecode`):

1. **`termux-packages`** is built with  
   `TERMUX_APP__PACKAGE_NAME="com.zenithblue.nativecode"` in `scripts/properties.sh`.
2. Core packages are compiled in Docker (`./build-package.sh -a aarch64 …`).
3. Selected `.deb`s are unpacked into a tree and packaged as  
   `app/src/main/assets/bootstrap.tar` (`assemble_bootstrap.py`).
4. Critical executables are copied to `jniLibs/arm64-v8a` as  
   `libbash.so`, `libproot.so`, `libloader.so`, `libloader32.so`  
   to satisfy **W^X** on `targetSdk 36` (see `docs/target_sdk_36_wx_bypass.md` in termux-lib).
5. Runtime still rewrites residual stock `com.termux` strings in bootstrap text  
   (`TermuxHostPaths.applyPackageToExtractedPrefix`) as a safety net.

FluxLinux should **repeat that pipeline twice** (or once per flavor) with:

| Flavor / product | `TERMUX_APP__PACKAGE_NAME` | Intended distribution |
|------------------|----------------------------|------------------------|
| **fdroid** (open) | `com.ivarna.fluxlinux` | F-Droid, GitHub release |
| **play** | `com.zenithblue.fluxlinux` | Google Play |

### 1.3 Path length check (Termux build system limits)

Termux validates:

- `TERMUX__ROOTFS` max length ≤ **86**
- `TERMUX__PREFIX` max length ≤ **90**

| Application ID | `/data/data/<id>/files` | `…/files/usr` |
|----------------|-------------------------|---------------|
| `com.ivarna.fluxlinux` | 37 | 41 |
| `com.zenithblue.fluxlinux` | 41 | 45 |
| `com.zenithblue.nativecode` (ref) | 42 | 46 |

Both FluxLinux IDs are **well under limits**. No path-layout hacks required.

---

## 2. Objectives

1. **Build-time correctness:** every ELF `RPATH`/`RUNPATH`, shebang, and script path embeds  
   `/data/data/com.ivarna.fluxlinux/...` **or**  
   `/data/data/com.zenithblue.fluxlinux/...` — not `com.termux`.
2. **Ship two bootstraps** (or two full APK flavors) so each store listing never installs the wrong prefix.
3. **Reuse termux-lib playbook:** Docker package builder, package set, bootstrap assembly, jniLibs W^X layout, host env SSOT.
4. **Keep packaging reproducible enough** for F-Droid (`com.ivarna.fluxlinux`) where binary blobs may need scanignore / separate assets pipeline.
5. **Do not mix** stock Termux repos at runtime for host-critical packages without rebuild/hold policy (same lesson as nativecode).

Out of scope for **this** plan (follow-on docs):

- Full UI port of nativecode terminal / onboarding into FluxLinux Compose screens.
- Complete marketplace / guest Debian feature parity.
- Multi-arch (`arm`, `x86_64`) — start **`aarch64` only**.

---

## 3. Architecture

```text
                    ┌─────────────────────────────────────┐
                    │  termux-packages (fork or submodule)│
                    │  properties.sh → PACKAGE_NAME       │
                    └──────────────┬──────────────────────┘
                                   │ Docker build-package.sh
                                   ▼
                    ┌─────────────────────────────────────┐
                    │  output/*.deb  (per PACKAGE_NAME)   │
                    └──────────────┬──────────────────────┘
                                   │ assemble_bootstrap.py
              ┌────────────────────┴────────────────────┐
              ▼                                         ▼
   bootstrap-ivarna.tar                      bootstrap-zenithblue.tar
   jniLibs-ivarna/arm64-v8a                  jniLibs-zenithblue/arm64-v8a
              │                                         │
              ▼                                         ▼
   productFlavor fdroid                      productFlavor play
   applicationId com.ivarna.fluxlinux        applicationId com.zenithblue.fluxlinux
```

**Runtime layout (either flavor):**

```text
/data/data/<APPLICATION_ID>/
  files/
    usr/          ← extracted bootstrap.tar
    home/
    …             ← proot containers, scripts, rootfs
  lib/            ← Android-extracted jniLibs (executable)
    libbash.so
    libproot.so
    libloader.so
    libloader32.so
```

Execution model (from termux-lib W^X fix):

- Host shells start via `nativeLibraryDir/libbash.so` (or linker + libtermux-exec).
- `PD_PROOT_BIN` → `libproot.so`; `PROOT_LOADER` → `libloader.so`.
- Guest Debian remains under proot/chroot; host tools must match package prefix.

---

## 4. Single source of truth (app side)

Mirror termux-lib’s host path SSOT (`docs/plan/host-setup-termux-package-env.md`):

| Piece | Role |
|-------|------|
| `TermuxHostPaths` (or `FluxHostPaths`) | `PACKAGE`, `PREFIX`, `HOME`, `TMPDIR`, stock rewrite helpers |
| `HostCommandBuilder` | ProcessBuilder / terminal env for all host runs |
| `usr/etc/fluxlinux-host.env` | Generated shell env; scripts **source** this |
| Setup scripts | Never hardcode the other product’s package name |

**Rule:** package string comes from `BuildConfig.APPLICATION_ID` (or flavor constant), not copy-pasted literals in scripts.

Canonical values per flavor:

```text
# fdroid
PACKAGE = com.ivarna.fluxlinux
PREFIX  = /data/data/com.ivarna.fluxlinux/files/usr
HOME    = /data/data/com.ivarna.fluxlinux/files/home

# play
PACKAGE = com.zenithblue.fluxlinux
PREFIX  = /data/data/com.zenithblue.fluxlinux/files/usr
HOME    = /data/data/com.zenithblue.fluxlinux/files/home
```

---

## 5. Package set (bootstrap minimum)

Start from termux-lib `assemble_bootstrap.py` `PACKAGES` list — host runtime + GUI services:

### 5.1 Required for interactive host terminal + proot

| Group | Packages |
|-------|----------|
| Shell & core | `bash`, `termux-exec`, `coreutils`, `findutils`, `grep`, `sed`, `gawk`, `psmisc`, `procps`, `tar`, `xz-utils`, `gzip`, `less`, `diffutils` |
| Net / TLS | `curl`, `libcurl`, `ca-certificates`, `openssl`, `libnghttp2`, `libssh2`, `libidn2`, `libunistring` |
| Package tooling (optional host apt later) | `dpkg`, `apt` (+ deps) — only if in-app `pkg` is desired |
| Proot | `proot`, `proot-distro`, `libtalloc`, `python` (+ python deps) |
| Termux plumbing | `termux-tools`, `termux-am` |
| Audio (X11 guest) | `pulseaudio` + `libandroid-shmem`, `libsndfile`, `libvorbis`, `libogg`, `flac`, `libopus`, `speexdsp`, `dbus`, … |
| XKB | `xkeyboard-config` (and/or `libx11` chain if needed) |
| Base libs | `libandroid-support`, `readline`, `ncurses`, `zlib`, `libiconv`, `libc++`, `libgmp`, `liblzma`, `pcre2`, `libffi`, `glib`, … |

### 5.2 X11 loader (not a normal .deb)

`termux-x11-nightly` does **not** ship a usable `loader.apk` alone.

- Build or extract **loader APK** from Termux:X11 / shell-loader project (as termux-lib does with `shell-loader-debug.apk`).
- Place at:  
  `$PREFIX/libexec/termux-x11/loader.apk`  
- Runtime: class name remains **`com.termux.x11.Loader`** inside the APK (do **not** sed-replace Java class names). Permissions: read-only for W^X/dex rules.

### 5.3 Explicitly deferred (same as nativecode notes)

Until builder compatibility is fixed:

- `virglrenderer-android`, `mesa-zink`, full native XFCE host packages may stay out of **bootstrap** and install later via held custom repo or guest-only.

### 5.4 Arch

| Arch | Priority |
|------|----------|
| `aarch64` | **P0** (all modern phones) |
| `arm` | P2 if 32-bit devices still matter |
| `x86_64` | P3 emulators only |

---

## 6. Build pipeline (packages)

### 6.1 Environment

Prerequisites (same as termux-lib):

- Docker
- Host networking for builder (IPv4 forwarding issues):  
  `export TERMUX_DOCKER_RUN_EXTRA_ARGS="--network host"`  
  Optional resource caps: `--cpus 10 --memory 10g`
- Disk: multi-GB for two full package trees + intermediate sources
- Clone of `termux-packages` (prefer **submodule or sibling** under `fluxlinux/native/` or reuse `~/repos/termux-lib/termux-packages` with **clean rebuild** when switching package names)

### 6.2 Set package name (critical step)

In `termux-packages/scripts/properties.sh`:

```bash
TERMUX_APP__PACKAGE_NAME="com.ivarna.fluxlinux"   # or com.zenithblue.fluxlinux
```

Derived paths become:

```text
TERMUX_APP__DATA_DIR=/data/data/$TERMUX_APP__PACKAGE_NAME
TERMUX__ROOTFS=$TERMUX_APP__DATA_DIR/files
TERMUX__PREFIX=$TERMUX__ROOTFS/usr
```

**Never** reuse `.deb`s built for another `TERMUX_APP__PACKAGE_NAME`.  
When switching flavors: clean builder container + clear `output/` (or use isolated output dirs).

### 6.3 Build commands

Automated pattern (adapt from termux-lib `custom_package_build_plan.md` and `scripts/build_live.sh`):

```bash
#!/usr/bin/env bash
# scripts/build_packages_for_appid.sh
set -euo pipefail

CUSTOM_PACKAGE="${1:?usage: $0 com.ivarna.fluxlinux|com.zenithblue.fluxlinux [pkg…]}"
ARCH="${ARCH:-aarch64}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TP="$ROOT/native/termux-packages"   # or path to termux-packages

# Apply package name
sed -i -E \
  's/^TERMUX_APP__PACKAGE_NAME=.*/TERMUX_APP__PACKAGE_NAME="'"${CUSTOM_PACKAGE}"'"/' \
  "$TP/scripts/properties.sh"

# Isolated output per app id
OUT="$ROOT/native/output/${CUSTOM_PACKAGE}"
mkdir -p "$OUT"
# point builder output → OUT (symlink termux-packages/output or TERMUX_OUTPUT_DIR if supported)

export TERMUX_DOCKER_RUN_EXTRA_ARGS="--network host --cpus 10 --memory 10g"
docker rm -f termux-package-builder 2>/dev/null || true

cd "$TP"
# Build leaves first; build-package.sh resolves deps automatically for top-level targets
for pkg in "${@:2}"; do
  echo "=== $CUSTOM_PACKAGE :: $pkg ==="
  ./scripts/run-docker.sh ./build-package.sh -a "$ARCH" "$pkg"
done
```

Recommended **seed top-level packages** (pulls dependency chain):

```text
bash coreutils curl proot proot-distro python pulseaudio termux-tools termux-am termux-exec
```

Then fill remaining bootstrap deps listed in §5 until `assemble` verification passes.

Resume/failure logging: copy termux-lib `scripts/resume_bootstrap_packages.sh` pattern (continue on fail, log `OK`/`FAIL`).

### 6.4 Verify prefix inside each .deb

```bash
deb=output/bash_*_aarch64.deb
d=$(ar t "$deb" | grep '^data.tar' | head -1)
ar p "$deb" "$d" | tar -tf - | head
# Expect: data/data/com.ivarna.fluxlinux/files/usr/...
# Never:  data/data/com.termux/...
```

ELF check:

```bash
# after extract
readelf -d usr/bin/bash | grep -E 'RPATH|RUNPATH'
# must contain /data/data/<APPLICATION_ID>/files/usr/lib
```

### 6.5 Two complete artifact trees

```text
native/
  termux-packages/          # shared sources; package name rewritten per build
  output/
    com.ivarna.fluxlinux/
      *.deb
    com.zenithblue.fluxlinux/
      *.deb
  bootstrap/
    com.ivarna.fluxlinux/
      bootstrap.tar
      jniLibs/arm64-v8a/*.so
    com.zenithblue.fluxlinux/
      bootstrap.tar
      jniLibs/arm64-v8a/*.so
  loader/
    shell-loader.apk        # shared; class names stay com.termux.x11.*
```

---

## 7. Bootstrap assembly

Port `termux-lib/assemble_bootstrap.py` → `fluxlinux/scripts/assemble_bootstrap.py` with parameters:

| Arg | Meaning |
|-----|---------|
| `--package-name` | `com.ivarna.fluxlinux` or `com.zenithblue.fluxlinux` |
| `--deb-dir` | `native/output/<package-name>` |
| `--out-tar` | assets path for that flavor |
| `--jni-dir` | jniLibs path for that flavor |
| `--loader-apk` | path to loader APK |

Steps:

1. Clean extract root.
2. Unpack selected `.deb` data tars into  
   `…/data/data/<package-name>/files/`.
3. Install `loader.apk` under `usr/libexec/termux-x11/`.
4. Verify required paths (bash, python, proot, proot-distro, pulseaudio, pkill, pulse module, loader, xkb).
5. Copy jniLibs mapping:

   | Source under prefix | jniLibs name |
   |---------------------|--------------|
   | `usr/bin/proot` | `libproot.so` |
   | `usr/bin/bash` | `libbash.so` |
   | `usr/libexec/proot/loader` | `libloader.so` |
   | `usr/libexec/proot/loader32` | `libloader32.so` |

6. `tar -cf bootstrap.tar usr` from the files root (same as nativecode).

Optional: **build-time** string rewrite of residual `com.termux` in text files inside the tree (nativecode currently does this at runtime; prefer both).

---

## 8. Android product flavors

Introduce flavors so one repo produces both store APKs:

```kotlin
// app/build.gradle.kts (sketch)
android {
    namespace = "com.ivarna.fluxlinux" // or shared; sources may stay under one package

    flavorDimensions += "store"
    productFlavors {
        create("fdroid") {
            dimension = "store"
            applicationId = "com.ivarna.fluxlinux"
            // assets: bootstrap from native/bootstrap/com.ivarna.fluxlinux/
        }
        create("play") {
            dimension = "store"
            applicationId = "com.zenithblue.fluxlinux"
            // assets: bootstrap from native/bootstrap/com.zenithblue.fluxlinux/
        }
    }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }
    androidResources {
        noCompress += listOf("xz", "tar")
    }
}
```

**Asset wiring options:**

1. **Gradle copy task** before `merge*Assets`:  
   copy the correct `bootstrap.tar` + jniLibs for the selected flavor.
2. **Source sets:**  
   `app/src/fdroid/assets/bootstrap.tar` and `app/src/play/assets/bootstrap.tar`.

Build outputs:

```text
./gradlew :app:assembleFdroidRelease
./gradlew :app:assemblePlayRelease
```

F-Droid metadata (`com.ivarna.fluxlinux.yml`) must pin the **fdroid** flavor (or default config that only builds ivarna).

---

## 9. Runtime integration checklist (after packages exist)

These are app changes that **consume** the packages (detailed UI plan can be separate):

| Area | Work |
|------|------|
| Bootstrap extract | First-run extract `bootstrap.tar` → `files/` |
| Path SSOT | `FluxHostPaths` from `BuildConfig.APPLICATION_ID` |
| Residual rewrite | Scan `usr/{bin,etc,share}` text; replace stock `com.termux` data roots |
| Host gate | `setup_termux.sh` validates PREFIX package, refuses `/com.termux/` |
| Terminal | Embed terminal-emulator/view (from termux-app modules, as nativecode) |
| W^X | Always exec from `nativeLibraryDir` for bash/proot/loader |
| X11 | Bundle termux-x11 module or intent; loader.apk path + read-only chmod |
| Scripts | Port FluxLinux orchestration scripts off external Termux → host prefix |
| Hold policy | `apt-mark hold` on custom-prefix critical packages if host apt enabled |

Do **not** sed-replace Java class names in `am` / `termux-x11` wrappers (nativecode regression: broke `com.termux.x11.Loader` / `com.termux.termuxam.Am`).

---

## 10. Work phases

### Phase 0 — Repo layout & tooling (½–1 day)

- [ ] Add `docs/plans/` (this doc) and link from `docs/README.md`.
- [ ] Decide location of `termux-packages` (submodule vs path to termux-lib tree).
- [ ] Add `scripts/build_packages_for_appid.sh`, `scripts/assemble_bootstrap.py`.
- [ ] Document host prerequisites (Docker, disk, network host mode).

### Phase 1 — Build `com.ivarna.fluxlinux` packages (P0 F-Droid ID)

- [ ] Set `TERMUX_APP__PACKAGE_NAME=com.ivarna.fluxlinux`.
- [ ] Clean docker builder + output.
- [ ] Build seed + dependency closure for bootstrap package list.
- [ ] Verify deb path prefixes and ELF RUNPATH.
- [ ] Assemble bootstrap + jniLibs; run verification list.
- [ ] Smoke-extract on device/emulator with a minimal harness APK if full app not ready.

### Phase 2 — Build `com.zenithblue.fluxlinux` packages (P0 Play ID)

- [ ] Repeat Phase 1 with second package name.
- [ ] **Separate** output and bootstrap trees; never overwrite ivarna artifacts.
- [ ] Same verification suite with zenithblue paths.

### Phase 3 — Gradle flavors + asset/jni wiring

- [ ] `fdroid` / `play` product flavors and applicationIds.
- [ ] Wire bootstrap.tar + jniLibs per flavor.
- [ ] `useLegacyPackaging = true`, `noCompress` for tar/xz.
- [ ] CI or local scripts: `assembleFdroidDebug` / `assemblePlayDebug`.

### Phase 4 — Host path SSOT + extract/setup

- [ ] Port `TermuxHostPaths` / `HostCommandBuilder` patterns.
- [ ] First-run bootstrap extract + rewrite residual stock strings.
- [ ] Port `setup_termux.sh` gate to FluxLinux scripts under app assets.

### Phase 5 — Terminal + X11 shell integration

- [ ] Integrate terminal modules / session spawn via `libbash.so`.
- [ ] Proot-distro env (`PD_PROOT_BIN`, `PROOT_LOADER`, SSL certs).
- [ ] loader.apk for termux-x11; pulseaudio path pins.
- [ ] Device smoke: shell prompt under correct PREFIX; no `mkdir …/com.termux`.

### Phase 6 — Release hardening

- [ ] Size budget (bootstrap may be hundreds of MB — Play / F-Droid asset policy).
- [ ] F-Droid: scanignore large archives if needed; reproducible notes.
- [ ] Play: policy checklist (nativecode docs as template); no runtime download of host natives if avoidable.
- [ ] Version matrix: document which APK ships which package name.

---

## 11. Verification matrix

| Check | ivarna | zenithblue |
|-------|--------|------------|
| `properties.sh` package name | `com.ivarna.fluxlinux` | `com.zenithblue.fluxlinux` |
| Deb archive paths | `/data/data/com.ivarna.fluxlinux/files/usr` | `/data/data/com.zenithblue.fluxlinux/files/usr` |
| `readelf` RUNPATH on bash/proot | contains ivarna prefix | contains zenithblue prefix |
| `tar -tf bootstrap.tar` required bins | present | present |
| jniLibs four `.so` files | present | present |
| Device `echo $PREFIX` after extract | ivarna path | zenithblue path |
| No host log `mkdir …/com.termux` | pass | pass |
| `libbash.so` launches interactive shell | pass | pass |
| proot login debian (if rootfs present) | pass | pass |

---

## 12. Risks & mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Building full set twice is slow (hours–days) | Schedule | Cache Docker layers; only rebuild when package name or package version changes; keep both output trees on disk |
| Accidentally shipping wrong bootstrap in a flavor | Broken install | Gradle assert: fail build if bootstrap tar contains the other package string |
| Using stock Termux apt upgrades | Overwrites RPATH | Hold critical pkgs; prefer custom repo or disable host apt for bootstrap packages |
| W^X on targetSdk 36 | `Permission denied` exec | jniLibs + legacy packaging (proven in nativecode) |
| sed-replacing Java classes in wrappers | X11/am crash | Only rewrite **filesystem data roots**, never class names |
| APK size / Play limits | Rejected or huge download | Split assets later (OBB/Play Asset Delivery) if needed; document size |
| F-Droid binary scrutiny | Policy | Build packages in documented open pipeline; avoid undisclosed blobs |
| Dual maintenance of package name | Drift | One script parameterized by app id; CI builds both |

---

## 13. File / directory targets (proposed)

```text
fluxlinux/
  docs/plans/
    termux-native-packages-dual-appid.md   ← this plan
  native/                                  ← optional large artifacts (git-lfs or local only)
    termux-packages/                       # or submodule
    output/<applicationId>/*.deb
    bootstrap/<applicationId>/bootstrap.tar
    bootstrap/<applicationId>/jniLibs/...
    loader/shell-loader.apk
  scripts/
    build_packages_for_appid.sh
    assemble_bootstrap.py
    verify_bootstrap.sh
  app/
    build.gradle.kts                       # flavors
    src/fdroid/...
    src/play/...
    src/main/jniLibs/                      # or flavor-specific
```

Large `.deb` / `bootstrap.tar` may stay **out of git**; CI or release scripts produce them and attach to releases.

---

## 14. Relationship to termux-lib

| Item | Action |
|------|--------|
| `custom_package_build_plan.md` | Pattern for `properties.sh` + Docker |
| `assemble_bootstrap.py` | Port; parameterize package name |
| `scripts/build_live.sh` / resume scripts | Port |
| `docs/plan/host-setup-termux-package-env.md` | Port SSOT ideas to FluxLinux IDs |
| `docs/target_sdk_36_wx_bypass.md` | Required for Play/fdroid modern SDK |
| Existing `output/*.deb` for **nativecode** | **Do not reuse** for FluxLinux — wrong prefix |

Reuse source tree and operational knowledge; **rebuild all packages** for each FluxLinux application ID.

---

## 15. Success criteria

1. Two independent bootstrap artifacts exist, one per application ID, with verified path prefixes.
2. Gradle can build `fdroid` and `play` APKs each embedding the matching bootstrap + jniLibs.
3. On a clean install of either APK, host shell runs without proot-to-`com.termux` bind tricks for core tools.
4. Device logs show `TERMUX_APP__PACKAGE_NAME` / `PREFIX` matching the installed app id.
5. No reliance on separately installed F-Droid Termux for the **native terminal** path (orchestrator mode may remain optional during migration).

---

## 16. Immediate next actions

1. Confirm `termux-packages` location (link `~/repos/termux-lib/termux-packages` vs clone under `fluxlinux/native/`).
2. Implement `scripts/build_packages_for_appid.sh` and run a **single** package (`bash`) for `com.ivarna.fluxlinux` as a dry run.
3. If path layout in deb looks correct, expand to full bootstrap set, then repeat for `com.zenithblue.fluxlinux`.
4. Only after both bootstraps verify: land Gradle flavors and asset wiring.

---

## References (local)

| Path | Why |
|------|-----|
| `~/repos/termux-lib/custom_package_build_plan.md` | Custom package name build recipe |
| `~/repos/termux-lib/assemble_bootstrap.py` | Deb → bootstrap.tar + jniLibs |
| `~/repos/termux-lib/termux-packages/scripts/properties.sh` | `TERMUX_APP__PACKAGE_NAME` SSOT for builds |
| `~/repos/termux-lib/docs/plan/host-setup-termux-package-env.md` | Runtime host path SSOT |
| `~/repos/termux-lib/docs/target_sdk_36_wx_bypass.md` | jniLibs execution model |
| `~/repos/termux-lib/docs/agent-handoff.md` | Bootstrap required file list |
| `fluxlinux/app/build.gradle.kts` | Current `com.ivarna.fluxlinux` id |
| `fluxlinux/README.md` | Dual store IDs (F-Droid vs Play) |
| `fluxlinux/docs/architecture.md` | Historical orchestrator vs monolithic decision |
