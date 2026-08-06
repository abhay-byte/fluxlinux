# Plan: Embed Host Bootstrap + In-App Terminal (Proot + Chroot)

**Date:** 2026-08-05  
**Updated:** 2026-08-05 (Pass 2 code + review round 2 fixes)  
**Status:** **PARTIAL** on `feat/embedded-terminal-bootstrap-proot-chroot`  
- Pass 1: core port landed (host SSOT, builders, terminal, catalog drop Termux Native, primary Install CTAs).  
- **Pass 2 (2026-08-05):** all code items landed — Gradle packaging gate, atomic extract + rootfs fail-closed, raw install argv, session split + RootShell cycle broken, method-from-distro routing, dual-path kill, FS-truth installed state, 33 unit tests green, `verify_apk_host_assets.sh` PASS.  
- **Review round 2 (2026-08-05):** blockers B1–B3 + major M1/M2 fixed — chroot root sessions seeded with package SSOT env, proot containers preserved across re-extract (no wipe on setup failure), success callbacks gated on exit 0, wizard component chain restored, loader fail-closed.  
- **Not COMPLETE:** device R1–R11 pending (no device attached) — exact ADB steps in §5.3; status flips to COMPLETE only with device log pointers.  
**Reference implementation (SSOT):** `~/repos/termux-lib` (a.k.a. nativecode)  
**Prerequisite:** [termux-native-packages-dual-appid.md](./termux-native-packages-dual-appid.md) — **COMPLETE** (both app IDs, `bootstrap.tar` + jniLibs verified)  
**Design review verdict:** Review 1 = MAJOR REVISIONS REQUIRED → Pass 2 landed; Review 2 blockers (B1 env SSOT, B2 destructive recovery, B3 false-success state) fixed with targeted patches — architecture kept.

---

## 0. Goal (what “done” means)

FluxLinux stops depending on **external Termux** for Debian install/run. Instead:

1. **Ship** per-app-id host userland (`bootstrap.tar` + W^X jniLibs) inside the APK (like termux-lib).
2. **Extract + pin** host paths at runtime via `TermuxHostPaths` SSOT (`BuildConfig.APPLICATION_ID`).
3. **Install Debian** from the **same packaged rootfs** for both:
   - **PRoot** → `proot-distro install <abs-path> --name debian`
   - **Chroot** → extract into `/data/local/tmp/chrootDebian13`
4. **Two terminal components** (cards/pages) replace “Copy & Open Termux”:
   - **`termux-flux-terminal`** — host + proot Debian sessions (flux / root guest).
   - **`chroot-root-shell`** — chroot install/run via SSOT helper + root shell (requires su + BusyBox).
5. **Distro page catalog (product cards):**
   - **Keep + rewire** **Debian** (proot) install/run → **`termux-flux-terminal`**
   - **Keep + rewire** **Debian (Rooted)** / `debian13_chroot` install/run → **`chroot-root-shell`**
   - **Drop / remove** the **Termux Native** installation card (`id = "termux"`) from the Distros list and all install/onboarding paths that treat external Termux as a distro
6. **Tutorials** (`docs/tutorial/setup_debian_proot.md`, `setup_debian_chroot.md` + `img/debian-*`) describe in-app install/run, not paste-into-Termux. Remove Termux-native install tutorial references where they conflict.

**Out of scope for this plan (later):** AI CLI marketplace, project workspace multi-tab, full X11 desktop session UX parity with nativecode (can reuse host `start_gui*.sh` later); other distros (Ubuntu/Arch/…) stay coming-soon unless already wired. **In scope:** host bootstrap, debian rootfs, proot/chroot components, **Distro cards for Debian + Debian Rooted only**, drop Termux Native card.

**Explicitly deferred (do not block Pass 2 COMPLETE for CLI install/shell):** GUI launch/stop via embedded host (`start_gui*.sh`); may still touch Termux:X11 package as a **separate** APK until GUI pass. Pass 2 still must not use `com.termux` **app** for Debian install/run/uninstall/component.

---

## 0.1 Pass 1 reality vs plan (honest snapshot)

| Area | Pass 1 state | Pass 2 required? |
|------|--------------|------------------|
| Flavors + `noCompress` + `useLegacyPackaging` | Landed | Harden only |
| `package_host_assets.sh` | Landed | **Yes** — Gradle task must **fail assemble** if assets missing |
| `TermuxHostPaths`, builders, RootShell, helper v2.2 | Landed | Recovery + cohesion |
| `termux` card removed from catalog | Landed | Grep gate residual |
| Install CTA → in-app terminal | Mostly | Finish dual-path kill |
| Prerequisites / Settings host-only | **Incomplete** | **Yes** |
| `TermuxIntentFactory` product paths | **Still live** (Settings, DistroSettings uninstall fallback, Prerequisites, GUI) | **Yes** for install/run/uninstall/component |
| `InstallServerService` HTTP→Termux | Still in tree | Retire primary path or quarantine |
| Device R1–R11 | Not run | **Yes** before COMPLETE |
| Builder unit tests | Partial (`TermuxHostPathsTest` only of planned set) | **Yes** |

---

## 1. As-is vs to-be

### 1.1 FluxLinux Pass 1 (after bulk port — still dual-path)

| Layer | Behavior after Pass 1 |
|-------|------------------------|
| Host runtime | Embedded PREFIX + **residual** external Termux UX |
| Install UX | Primary CTA opens in-app terminal; other screens still open Termux |
| Paths | `TermuxHostPaths` SSOT for host; **`TermuxIntentFactory` still hardcodes `com.termux`** |
| PRoot install | Local rootfs via `flux_install.sh` in-app (when card path used) |
| Chroot install | SSOT helper + root session (when card path used) |
| Terminal | In-app `TerminalSession` + FGS |
| Bootstrap artifacts | Staged via script into flavor source sets — **not assemble-gated** |

### 1.2 termux-lib (nativecode) — transfer source

| Layer | Implementation | Key paths |
|-------|----------------|-----------|
| Host bootstrap | `assets/bootstrap.tar` (~122 MB) | extract → `$filesDir/usr` |
| W^X binaries | `jniLibs/arm64-v8a/{libbash,libproot,libloader,libloader32}.so` | `nativeLibraryDir` |
| Package SSOT | `TermuxHostPaths.PACKAGE` | PREFIX/HOME/TMPDIR |
| Host env | `writeHostEnvFile` → `usr/etc/fluxlinux-host.env` | scripts source this |
| Residual rewrite | `applyPackageToExtractedPrefix` | strip `com.termux` text |
| Host gate | `setup_termux.sh` | validates proot/python/pulse/loader |
| Debian rootfs | `assets/rootfs/debian_13_rootfs.tar.xz` (~82 MB) | deploy → `$HOME/` |
| PRoot install | `flux_install.sh` local archive | `proot-distro install $ABS --name debian` |
| Chroot install | `setup_debian13_chroot.sh` same archive | extract → `chrootDebian13` |
| Chroot SSOT helper | `nativecode_chroot.sh` v2.2 | mounts + login/sh/exec/b64 |
| Session builders | `HostCommandBuilder`, `ProotCommandBuilder`, `ChrootCommandBuilder`, `LinuxCommandBuilder` | argv + env |
| Root | `RootShell` | KSU/Magisk su discovery (no `File.exists` gate) |
| Terminal UI | `TerminalSession` + `TerminalView` (termux-app) | FGS `AppTerminalService` |
| Shell cards | `shell` (flux) + `shell-root` (root) | method = `proot` \| `chroot` |

### 1.3 Rootfs already aligned

| Item | Value |
|------|--------|
| FluxLinux | `assets/rootfs/debian_13_rootfs.tar.xz` |
| termux-lib | `app/src/main/assets/rootfs/debian_13_rootfs.tar.xz` |
| **SHA256** | `13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803` (**identical**) |
| Size | 85 009 380 bytes (~82 MiB) |

No re-download needed — **copy into app assets** (or symlink via packaging script) and deploy at runtime like nativecode.

### 1.4 Bootstrap already built (both app IDs)

```text
native/bootstrap/com.ivarna.fluxlinux/{bootstrap.tar,jniLibs/arm64-v8a/*}
native/bootstrap/com.zenithblue.fluxlinux/{bootstrap.tar,jniLibs/arm64-v8a/*}
```

| App ID | bootstrap.tar | verify |
|--------|---------------|--------|
| `com.ivarna.fluxlinux` | ~122 MB | PASS |
| `com.zenithblue.fluxlinux` | ~122 MB | PASS |

---

## 2. Target architecture

```text
┌─────────────────────────────────────────────────────────────────────────┐
│  FluxLinux APK (flavor: ivarna | zenithblue)                            │
│  applicationId = com.ivarna.fluxlinux | com.zenithblue.fluxlinux        │
│                                                                         │
│  assets/bootstrap.tar          ← packaging script from native/bootstrap │
│  assets/rootfs/debian_13…xz    ← same archive proot + chroot            │
│  assets/scripts/…              ← setup_termux, flux_install, chroot SSOT│
│  jniLibs/arm64-v8a/*.so        ← libbash / libproot / libloader{,32}    │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ first run / onboarding
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Device filesDir                                                            │
│  $filesDir/usr/          ← host PREFIX (proot-distro, python, pulse, …)   │
│  $filesDir/home/         ← HOST home + scripts + debian_13_rootfs.tar.xz  │
│  usr/etc/fluxlinux-host.env  ← generated SSOT for shell                   │
└───────────────────────────────┬─────────────────────────────────────────┘
           │                                      │
           │ proot                                │ chroot (root)
           ▼                                      ▼
  proot-distro containers/debian          /data/local/tmp/chrootDebian13
  + login --user flux|root                + fluxlinux_chroot.sh (SSOT)
           │                                      │
           └──────────────┬───────────────────────┘
                          ▼
              In-app TerminalSession (termux-emulator)
              ┌────────────────────┬─────────────────────┐
              │ termux-flux-terminal│ chroot-root-shell  │
              │ method=proot        │ method=chroot      │
              │ cards: shell,       │ cards: shell,      │
              │        shell-root   │        shell-root  │
              └────────────────────┴─────────────────────┘
```

### 2.1 Two components (product names)

| Component ID | User-facing | Isolation | Requires | Primary use |
|--------------|-------------|-----------|----------|-------------|
| **`termux-flux-terminal`** | Flux Terminal (PRoot) | Host prefix + proot-distro Debian | No root | Install/run Debian proot; interactive shell; later GUI host helpers |
| **`chroot-root-shell`** | Root Shell (Chroot) | `busybox chroot` via SSOT helper | Root + BusyBox NDK | Install/run Debian chroot; root/flux login; near-native perf |

**Rule (from nativecode):** When Distro **Install** or **Run** cards fire for proot → open/use **termux-flux-terminal** sessions (not Termux intent). For chroot → open/use **chroot-root-shell** (RootShell + helper). Never dual-path with external Termux for the same action once embedded path is enabled.

### 2.2 Distro page cards (SSOT catalog) — rewire + drop

Source: `DistroRepository.supportedDistros` + `DistroScreen` / `InstallConfigScreen` / Home installed cards / `DistroSettingsScreen`.

#### Available install cards (`comingSoon = false`)

| Card `id` | UI name | Mode flags | **Product route** |
|-----------|---------|------------|-------------------|
| `debian` | Debian | `prootSupported=true`, **`chrootSupported=false`** (Pass 2 fix if still true) | **Always** `termux-flux-terminal` |
| `debian13_chroot` | Debian (Rooted) | `chrootSupported=true`, `prootSupported=false` | **Always** `chroot-root-shell` |
| ~~`termux`~~ | ~~Termux Native~~ | — | **Removed** from catalog |

Coming-soon cards (Ubuntu, Arch, …) stay listed as compact “soon” only; not wired in this plan.

#### Drop Termux Native — concrete code touch list

| Area | File / symbol | Change |
|------|---------------|--------|
| Catalog | `DistroRepository.supportedDistros` | **Remove** `Distro(id = "termux", …)` entry |
| Components | `termuxComponents` list | Delete or leave unused; no UI reference |
| Sort boost | `DistroScreen` `thenByDescending { it.id == "termux" }` | Remove termux priority sort |
| Spec enum | `SupportedDistro.TERMUX` | Remove or mark unused |
| Scripts | `assets/scripts/termux/**` | Stop shipping as install path; can keep files orphaned until cleanup PR |
| Prerequisites | `PrerequisitesScreen` Termux APK download / RUN_COMMAND permission as **hard gate** | **Remove hard dependency** on external Termux APK for using FluxLinux; keep optional X11 notes if still separate app for GUI later |
| Settings | “Initialize Termux”, “Fix Termux Connection”, tweaks via `com.termux` | Replace with **host bootstrap / setup_termux (embedded)** status; drop external connection panel as required UX |
| Home / install queue | Any path that treats `distroId == "termux"` | Delete or no-op |
| State | `StateManager` markers for termux distro | Migrate/ignore legacy `termux` installed flag |
| Tutorials / storelisting | Mentions of Termux Native distro | Drop or rewrite to embedded host |

**Why drop:** Host bootstrap **is** the embedded Termux-class prefix inside FluxLinux. A separate “Termux Native” distro card reintroduces the external-app orchestrator model this plan replaces.

#### Wire Debian + Debian Rooted — CTA matrix (every button)

| Screen | Card / CTA | `debian` (proot) | `debian13_chroot` |
|--------|------------|------------------|-------------------|
| **DistroScreen** | Install (not installed) | Navigate InstallConfig → component **`termux-flux-terminal`** | InstallConfig → gate root/BusyBox → **`chroot-root-shell`** |
| **InstallConfigScreen** | Primary button | **Install in Flux Terminal** (not “Copy & Open Termux”) | **Install in Root Shell** |
| **InstallConfigScreen** | Secondary | Advanced: copy command (optional debug only) | same optional |
| **Home** (installed) | Open / Run shell | Open terminal session `shell` (proot) | Open session `shell` (chroot) |
| **Home** | Root shell | `shell-root` proot | `shell-root` chroot (requires su) |
| **Home / DistroSettings** | Start GUI | Host `start_gui.sh` via termux-flux-terminal (Phase 4+) | chroot GUI scripts via helper (later) |
| **DistroSettings** | Component install (xfce, hw_accel, …) | Run component script **inside proot guest** via termux-flux-terminal | Run via chroot-root-shell / helper `b64` as root |
| **DistroSettings** | Uninstall distro | proot-distro remove / script in terminal | SSOT uninstall + umount via root shell |
| **Onboarding** | Environment init | Bootstrap extract + setup_termux (embedded) — **not** “install Termux APK” | Root grant + BusyBox only when user chooses chroot |

#### Routing helper (implementation sketch)

```kotlin
// Single place so cards never call TermuxIntentFactory for these ids
fun terminalComponentFor(distroId: String): TerminalComponent = when (distroId) {
    "debian" -> TerminalComponent.TERMUX_FLUX_TERMINAL   // proot
    "debian13_chroot", "debian_chroot" -> TerminalComponent.CHROOT_ROOT_SHELL
    else -> error("unsupported install card: $distroId") // termux removed
}

enum class TerminalComponent { TERMUX_FLUX_TERMINAL, CHROOT_ROOT_SHELL }
```

Install sessions:

```text
debian install:
  component = TERMUX_FLUX_TERMINAL
  session cmd = flux_install.sh debian [setup_b64]

debian13_chroot install:
  component = CHROOT_ROOT_SHELL
  session/root job = setup_debian13_chroot.sh (local rootfs)
```

### 2.3 Package identity SSOT

```kotlin
// TermuxHostPaths — ONLY place that defines package/paths
object TermuxHostPaths {
    // Prefer BuildConfig so flavors stay correct without dual hardcoding
    val PACKAGE: String get() = BuildConfig.APPLICATION_ID
    // constants derived: DATA_ROOT, PREFIX, HOME, TMPDIR, BIN, LIB, PROOT_DISTRO, …
}
```

| Flavor | `applicationId` | Bootstrap source |
|--------|-----------------|------------------|
| `ivarna` (F-Droid) | `com.ivarna.fluxlinux` | `native/bootstrap/com.ivarna.fluxlinux/` |
| `zenithblue` (Play) | `com.zenithblue.fluxlinux` | `native/bootstrap/com.zenithblue.fluxlinux/` |

**Never** hardcode `com.termux` at call sites for host PREFIX. Shell scripts **source** `$PREFIX/etc/fluxlinux-host.env`.

### 2.4 Product routing SSOT (Pass 2 — close dual path)

```text
Distro Install / Run shell / shell-root / Uninstall / Component install
  → terminalComponentFor(distroId) only
  → TerminalLauncher.prepareHost (when host needed)
  → FluxTerminalSessionManager / session factories
  ✗ NEVER TermuxIntentFactory for debian | debian13_chroot | debian_chroot
```

| Legacy surface | Pass 2 action |
|----------------|---------------|
| `TermuxIntentFactory` install/run/uninstall/component for debian* | **Delete call sites** or `#if DEBUG` only |
| `InstallServerService` / `LocalInstallServer` as primary install | **Stop starting** for debian*; keep class only if unused or mark `@Deprecated` |
| Prerequisites Termux APK + RUN_COMMAND hard gate | **Remove** for core CLI product |
| Settings “Fix Termux Connection” / init external Termux | **Replace** with host bootstrap status + re-extract |
| DistroSettings uninstall → open Termux | **→** `openUninstallSession` |
| GUI Start/Stop buttons | **Deferred** (may still use Termux intents until GUI pass) — document as known residual |
| Troubleshooting Termux-only tips | Rewrite to embedded host where they block CLI |

### 2.5 Module boundaries (high cohesion / low coupling)

| Module | Owns | Must not own |
|--------|------|--------------|
| `TermuxHostPaths` | PACKAGE, paths, rewrite, host env file | Sessions, root, UI |
| `BootstrapInstaller` | Extract + recovery markers | Network, su |
| `HostScriptDeployer` | Scripts + rootfs deploy + SHA/size gate | Terminal UI |
| `HostCommandBuilder` / `ProotCommandBuilder` / `ChrootCommandBuilder` | Pure argv + env maps | Session lists, FGS |
| `LinuxCommandBuilder` | Dispatch by **explicit** `method` param | **No ambient global for card actions** |
| `RootShell` | su discovery + capture/execute/stageAsset | **No import of ChrootCommandBuilder** |
| `ChrootCommandBuilder` | Helper stage + login/sh/b64 argv | UI |
| `SessionRegistry` (split from god manager) | Tabs, attach view, FGS count | Install script logic |
| `InstallSessionFactory` / `GuestSessionFactory` / `UninstallSessionFactory` | Build `TerminalSession` for each job | Path SSOT |
| `terminalComponentFor` | distroId → component | — |
| UI screens | Buttons → factories only | Hardcoded `com.termux` paths |

**Pass 2 cohesion target:** split `FluxTerminalSessionManager` (~god object) into registry + factories without changing product behavior.

**Pass 2 coupling fix:** break `RootShell` ↔ `ChrootCommandBuilder` cycle (RootShell = su only).

### 2.6 Method selection rule

```text
Card / install / uninstall / component:
  method = terminalComponentFor(distroId).method   // always
Shared Terminal page tool selector (optional):
  may remember last method for UI default ONLY
Never: LinuxCommandBuilder.currentMethod mutated by one card and reused by another without explicit pass
```

Prefer remove or deprecate `var currentMethod` for product paths; pass `method` at every `openSession*`.

---

## 3. Phase breakdown

### Phase 0 — Packaging script + Gradle wiring  (foundation)

**Goal:** APK contains correct bootstrap + jniLibs + rootfs per flavor. **Assemble fails closed** if host assets missing.

#### 0.1 Script: `scripts/package_host_assets.sh`

Exists (Pass 1). Keep as implementation detail of Gradle tasks.

```bash
# Usage:
#   ./scripts/package_host_assets.sh com.ivarna.fluxlinux
#   ./scripts/package_host_assets.sh com.zenithblue.fluxlinux
#   ./scripts/package_host_assets.sh --all
#
# Actions:
#   1. Verify native/bootstrap/<id>/{bootstrap.tar,jniLibs/arm64-v8a/*.so}
#   2. Copy bootstrap.tar → app/src/<flavor>/assets/bootstrap.tar
#   3. Copy jniLibs → app/src/<flavor>/jniLibs/arm64-v8a/
#   4. Ensure rootfs → app/src/main/assets/rootfs/debian_13_rootfs.tar.xz
#   5. Print sizes + SHA256; fail if rootfs SHA mismatch (optional strict mode)
```

**Gitignore (large binaries — same as nativecode):**

```gitignore
app/src/**/assets/bootstrap.tar
app/src/**/assets/rootfs/*.tar.xz
# jniLibs: prefer regenerate from native/bootstrap (gitignore or CI-stage)
```

#### 0.2 Gradle product flavors + **mandatory package task** (Pass 2)

Flavors + `noCompress` + `useLegacyPackaging` already landed. **Pass 2 gap:**

```text
:app:packageHostAssetsIvarna  / :app:packageHostAssetsZenithblue
  inputs:  native/bootstrap/<applicationId>/**
           assets/rootfs/debian_13_rootfs.tar.xz (repo root or main)
  outputs: app/src/<flavor>/assets/bootstrap.tar
           app/src/<flavor>/jniLibs/arm64-v8a/*
           app/src/main/assets/rootfs/debian_13_rootfs.tar.xz
  mustRunAfter / wired so:
    preBuild or mergeAssets / mergeJniLibFolders dependsOn matching flavor task
  on missing native/bootstrap/<id>/bootstrap.tar:
    FAIL the build with clear message (do not assemble empty host)
```

Document in `native/README.md`: Gradle runs packaging; manual script is for offline staging only.

#### 0.3 Dependencies (terminal stack)

Port from termux-lib `app/build.gradle.kts`:

| Dep | Why |
|-----|-----|
| `com.github.termux:termux-app:v0.118.0` (or vendored `terminal-emulator` + `terminal-view` modules) | `TerminalSession`, `TerminalView`, client APIs |
| Optional local `:termux-x11` later | In-process X11; **phase later** — first ship CLI shells |
| Guava empty listenablefuture conflict fix | Same as nativecode if using termux-app AAR |

**License note:** termux-app is GPLv3 — FluxLinux is already GPL-compatible if published as open source; document in LICENSE/README.

#### 0.4 Phase 0 acceptance tests

| ID | Test | Pass criteria |
|----|------|---------------|
| P0-T1 | `package_host_assets.sh com.ivarna.fluxlinux` | assets + jniLibs present; sizes match native/bootstrap |
| P0-T2 | Same for zenithblue | no cross-copy (ivarna bootstrap not used for zenithblue) |
| P0-T3 | `./gradlew :app:assembleIvarnaDebug` | APK contains `assets/bootstrap.tar`, `assets/rootfs/debian_13_rootfs.tar.xz`, `lib/arm64-v8a/libbash.so` etc. |
| P0-T4 | `aapt2 dump assets` / unzip APK | `noCompress` → bootstrap stored STORED not DEFLATE (or size ≈ on-disk) |
| P0-T5 | Flavor isolation | ivarna APK applicationId + bootstrap path strings contain only `com.ivarna.fluxlinux` (sample `strings` on extracted usr later in P1) |
| **P0-T6** | **Gradle gate** | Missing `native/bootstrap/.../bootstrap.tar` → assemble **fails** (not success with empty host) |
| **P0-T7** | **Task wiring** | `packageHostAssets*` runs as dependency of flavor assemble without manual script |

---

### Phase 1 — Host path SSOT + bootstrap extract  (host component core)

**Goal:** App can extract host PREFIX and run `libbash.so` with correct env (no proot guest yet). Pass 2 adds **recovery** and **fail-closed deploy**.

#### 1.1 Kotlin files (package `…core.terminal` / `…core.root`)

| File | Port from termux-lib | Notes for FluxLinux |
|------|----------------------|---------------------|
| `TermuxHostPaths.kt` | `terminal/TermuxHostPaths.kt` | `PACKAGE = BuildConfig.APPLICATION_ID`; keep rewrite/env write |
| `HostCommandBuilder.kt` | same | envMap + build(script) |
| `ShellCommandRunner.kt` | same | **linker64** prefix for app-data ET_DYN |
| `BootstrapInstaller.kt` | logic from `ensureBootstrapExtracted` + Onboarding extract | progress + **atomic extract** (Pass 2) |
| `HostScriptDeployer.kt` | `deployScripts` + `deployRootfsArchive` | **return success/fail**; rootfs size+SHA gate (Pass 2) |

#### 1.2 Bootstrap extract algorithm (Pass 2 hardened)

```text
Marker: home/.fluxlinux/bootstrap.extracted (version string) AND termux-exec exists
  OR force=true / setup_termux failed after extract:

1. Extract assets/bootstrap.tar → filesDir/.bootstrap_extract_tmp/ (or wipe partial usr only when force)
2. On success: move/merge into filesDir (atomic as far as FS allows)
3. Write marker with bootstrap version / package id
4. TermuxHostPaths.applyPackageToExtractedPrefix(filesDir)
5. On failure: leave/delete partial; return false; UI can retry

If only libtermux-exec exists but setup_termux fails: clear marker + force re-extract once
```

**Critical:** Confirm `assemble_bootstrap.py --mode full` produces tarball rooted at `usr/` (or strip `data/data/<pkg>/files/` prefix on extract). Align extract with termux-lib `extractTarStream`.

#### 1.2b Rootfs deploy gate (Pass 2)

```text
deployRootfsFromAssets:
  - copy to $HOME/debian_13_rootfs.tar.xz
  - require size > 50 MiB
  - prefer SHA256 == 13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803
  - on failure: return false (prepareHost must NOT report success)
  - skip re-copy only if size+SHA ok (not size alone if SHA available)
```

Network URL fallback in install scripts: allowed only if local archive missing; log loud; prefer fail-fast when asset deploy was expected.

#### 1.3 Host scripts in assets

| Script | Dest in FluxLinux assets |
|--------|--------------------------|
| `setup_termux.sh` | `scripts/host/setup_termux.sh` |
| `flux_install.sh` | `scripts/debian/proot/setup/flux_install.sh` (local rootfs) |
| `fluxlinux_chroot.sh` | `scripts/chroot/` |
| `setup_debian13_chroot.sh` / uninstall | `scripts/chroot/` |
| Host env | **generated** by `TermuxHostPaths` |

#### 1.4 Phase 1 acceptance tests

| ID | Test | How | Pass |
|----|------|-----|------|
| P1-T1 | Extract bootstrap | cold start / debug button | `usr/bin/bash` or proot-distro exists; marker rewrite count logged |
| P1-T2 | Host env file | read `files/usr/etc/fluxlinux-host.env` | `TERMUX_APP__PACKAGE_NAME` == applicationId; PREFIX matches |
| P1-T3 | No stock paths | `grep -R com.termux files/usr/etc files/usr/bin` (text) | 0 hits after rewrite (or only intentional docs) |
| P1-T4 | setup_termux | run via HostCommandBuilder + ShellCommandRunner | exit 0; marker `home/.fluxlinux/setup_termux.done` v2 |
| P1-T5 | Host shell session | TerminalSession with `libbash.so -l` | interactive prompt; `echo $PREFIX` correct |
| P1-T6 | Dual flavor | install both APKs on device/emulator | each PREFIX under its own `/data/data/<id>/files/usr` |
| **P1-T7** | Partial extract recovery | kill mid-extract or force corrupt usr | retry force extract succeeds |
| **P1-T8** | Rootfs fail-closed | missing asset | prepareHost false; no silent success |

---

### Phase 2 — Component A: `termux-flux-terminal` (proot)

**Goal:** In-app terminal for host + Debian proot login; install Debian from local rootfs **without** external Termux.

#### 2.1 Files

| File | Role |
|------|------|
| `ProotCommandBuilder.kt` | `python $PROOT_DISTRO login debian --shared-tmp --user flux|root` |
| `LinuxCommandBuilder.kt` | method switch; start with proot only if chroot not ready |
| `AppTerminalService.kt` | FGS specialUse while sessions alive |
| `TerminalScreen` / Compose wrapper | Host `AndroidView(TerminalView)` or hybrid Compose+View |
| `FluxTerminalSessionManager` | create/switch/close sessions; max tabs |

#### 2.2 Session types (cards)

| Card type | Label | Guest user | shellCmd sentinel |
|-----------|-------|------------|-------------------|
| `shell` | Debian Shell | `flux` | `exec zsh` / interactive |
| `shell-root` | Debian Shell Rooted | `root` | interactive as root |
| (later) host | Host Shell | n/a | raw libbash |

Match nativecode: `LinuxCommandBuilder.sessionUserForType("shell-root") == "root"`.

#### 2.3 Install path (proot) — replace clipboard

```text
User taps Install (Debian PRoot)
  → TerminalLauncher.prepareHost (extract + deploy + setup_termux; fail-closed)
  → openInstallSession:
       argv: [libbash, $HOME/flux_install.sh, "debian"] 
         or [libbash, $HOME/flux_install.sh, "debian", setupB64]
       **No shell quoting in argv** — TerminalSession is execve-style, not `sh -c`
  → on success marker ~/.fluxlinux_distro_debian_installed
  → fluxlinux://callback updates installed state
```

**Pass 2 argv contract (critical):**

```kotlin
// CORRECT — setupB64 is a raw argument (script reads $2)
arrayOf(shell, scriptPath, "debian") 
arrayOf(shell, scriptPath, "debian", setupB64)  // no \" wrapping

// WRONG — quotes become part of the argument
arrayOf(shell, scriptPath, "debian", "\"$setupB64\"")
```

`flux_install.sh` behaviors to preserve:

- Resolve `debian_13_rootfs.tar.xz` from `$HOME` first (app-deployed).
- `python proot-distro install $ABS_PATH --name debian` (**absolute path** required).
- SHA256 verify when present.
- Skip if `containers/debian/rootfs/bin/sh` exists.
- Run setup script (base64 or `setup_debian_family.sh`).
- Callback to app.

#### 2.4 Run path (proot)

```text
User taps Run / Open Shell on installed Debian proot
  → LinuxCommandBuilder.currentMethod = "proot"
  → createNewTerminalSession("shell") or "shell-root"
  → ProotCommandBuilder.build(...)
  → TerminalSession(libbash, home, args, env)
```

#### 2.5 UI integration points (FluxLinux screens)

| Screen / card | Change |
|---------------|--------|
| `InstallConfigScreen` | Primary CTA: **Install in Flux Terminal** (not “Copy & Open Termux”); secondary: advanced copy command |
| `DistroSettingsScreen` / Home installed card | **Open Shell**, **Open Root Shell**, (later) Start GUI |
| `SettingsScreen` | Host setup status; “Re-extract bootstrap”; remove external Termux connection panel as **required** path (keep optional for power users) |
| New `TerminalScreen` in bottom nav or overlay | Session tabs + tool selector cards |
| `OnboardingScreen` | Optional: “Initialize environment” extracts bootstrap (like nativecode onboarding phases B–D) |

#### 2.6 Phase 2 acceptance tests

| ID | Test | Pass |
|----|------|------|
| P2-T1 | Rootfs deployed | `$HOME/debian_13_rootfs.tar.xz` size > 50 MiB after deploy |
| P2-T2 | proot-distro install local | `bin/sh` under `usr/var/lib/proot-distro/installed-rootfs/debian` (or containers path used by bundled proot-distro) |
| P2-T3 | No registry pull | airplane mode after assets deployed → install still succeeds |
| P2-T4 | Login flux | `whoami` → flux; `pwd` → /home/flux |
| P2-T5 | Login root | shell-root card → `whoami` → root |
| P2-T6 | Host env inside proot session | guest can `apt` after setup; no `com.termux` in host env dump |
| P2-T7 | Install from Distro card | end-to-end without opening external Termux |
| P2-T8 | FGS | notification while session open; swipe-away policy documented |
| P2-T9 | Multi-tab | 2 sessions; switch; close; no crash |
| P2-T10 | Process death | recreate activity restores or cleanly drops sessions without corrupt state |

---

### Phase 3 — Component B: `chroot-root-shell` (chroot SSOT)

**Goal:** Root install + interactive chroot shells via **one** helper script (nativecode v2.2 semantics). Proot path **untouched**.

#### 3.1 SSOT helper

Port `assets/scripts/chroot/nativecode_chroot.sh` → rename for product:

| Item | Value |
|------|--------|
| Asset | `scripts/chroot/fluxlinux_chroot.sh` (or keep name + rebrand stamp) |
| Version stamp | `fluxlinux-chroot v2.2` (match Kotlin constant) |
| On-device path | `/data/local/tmp/fluxlinux_chroot.sh` |
| Rootfs | `/data/local/tmp/chrootDebian13` |
| Host tmp bridge | `$PREFIX/tmp` → guest `/mnt/host-tmp` |
| Default user | `flux` (uid 1000) |

**CLI (must match nativecode):**

```text
fluxlinux_chroot.sh version
fluxlinux_chroot.sh mount [--x11]
fluxlinux_chroot.sh login [--user flux|root] [--shell zsh|bash] [--workdir PATH]
fluxlinux_chroot.sh sh    [--user flux|root] -- 'shell string'
fluxlinux_chroot.sh exec  [--user flux|root] -- CMD [ARGS...]
fluxlinux_chroot.sh b64   [--user flux|root] -- BASE64_PAYLOAD
```

**Hard rules (device-proven in nativecode):**

- Idempotent mounts (check `/proc/mounts`).
- **Never** bind host tmp over guest `/tmp`.
- Guest `env -i` + Debian PATH (no Android PATH leak).
- TTY-safe b64: decode to file, **not** pipe to bash (preserves PTY).
- Session outer exec: `/system/bin/sh` + WINCH trap (SELinux).
- No nested `su` loops; host `timeout` on ADB tests.

Docs to port as FluxLinux environment docs:

- `termux-lib/docs/environment/nativecode-chroot-ssot.md` → `docs/environment/fluxlinux-chroot-ssot.md`
- Crash postmortem rules remain authoritative for device testing.

#### 3.2 Kotlin

| File | Role |
|------|------|
| `RootShell.kt` | su discovery cache; capture/execute; stageAsset |
| `ChrootCommandBuilder.kt` | ensureHelperScript; build login/sh/exec/b64; SESSION_EXEC |
| Wire `LinuxCommandBuilder` | `method == "chroot"` → ChrootCommandBuilder |

`ensureHelperScript`: stage from assets when version stamp missing/mismatched; root `cp` to `/data/local/tmp/…`.

#### 3.3 Install path (chroot)

```text
User selects Debian (Rooted) / Chroot mode
  → gate: RootShell.isRootAvailable() + BusyBox resolve
  → if fail: show existing BusyBox/root tutorial screens
  → ensure bootstrap + deploy rootfs + deploy setup_debian13_chroot.sh
  → run setup in chroot-root-shell terminal OR RootShell streaming:
       su → sh setup_debian13_chroot.sh
     (script uses SAME debian_13_rootfs.tar.xz from $APP_HOME)
  → marker / configured flag
  → Run card opens chroot session (shell / shell-root)
```

Port rootfs resolve list from nativecode `setup_debian13_chroot.sh` (APP_HOME first, not only `/sdcard/Download`).

#### 3.4 Run path (chroot)

```text
LinuxCommandBuilder.currentMethod = "chroot"
createNewTerminalSession("shell" | "shell-root")
  → ChrootCommandBuilder.build
  → TerminalSession(SESSION_EXEC=/system/bin/sh, …)
```

Root shell card on chroot: probe `RootShell.isRootAvailable()` first (nativecode pattern).

#### 3.5 Settings / storage cards (later parity)

nativecode has chroot size, kill processes, uninstall — port scripts:

- `chroot_size.sh`, `chroot_processes.sh`, `uninstall_debian13_chroot.sh`

Minimum for this plan: **install + login + uninstall** safe umount.

#### 3.6 Phase 3 acceptance tests

| ID | Test | Pass | Safety |
|----|------|------|--------|
| P3-T1 | Root probe | `RootShell.isRootAvailable()` true on rooted device | bg thread only |
| P3-T2 | Helper stage | `head` helper shows version stamp | once per install |
| P3-T3 | Mount idempotent | run `mount` twice; `/proc/mounts` count stable | no loops |
| P3-T4 | Install offline | rootfs from $HOME; chroot has `/bin/sh` | — |
| P3-T5 | login flux | whoami/id/HOME correct | one session |
| P3-T6 | login root | whoami root | — |
| P3-T7 | b64 TTY | interactive tool or `script` keeps stdin | no ENXIO on /dev/tty |
| P3-T8 | Guest PATH | `command -v base64` works as root b64 | env -i contract |
| P3-T9 | Install from card | no external Termux | — |
| P3-T10 | Uninstall | umount + rm rootfs; app state clean | — |
| P3-T11 | Proot regression | proot still works after chroot work | **mandatory** |
| P3-T12 | Unrooted device | chroot cards gated; proot unaffected | — |

**ADB safety (from postmortem):** max one light probe per session; host `timeout`; never nested su stress loops.

---

### Phase 4 — Distro cards: wire Debian + Debian Rooted; **drop Termux Native**; tutorials

This phase is **product-facing**. Components from Phases 2–3 are useless if Distro cards still open external Termux or still show Termux Native.

#### 4.0 Drop Termux Native card (do early in Phase 4, or with Phase 2 if catalog cleanup is easy)

| Step | Action |
|------|--------|
| 1 | Remove `Distro(id = "termux")` from `DistroRepository.supportedDistros` |
| 2 | Remove sort preference for `termux` in `DistroScreen` |
| 3 | Remove / hide InstallConfig + component paths that only apply to termux native |
| 4 | Prerequisites: stop requiring Termux APK install to use the app (bootstrap is in-app) |
| 5 | Settings: replace “external Termux init/connection” primary UX with embedded host status |
| 6 | Grep for `id == "termux"`, `SupportedDistro.TERMUX`, `distro_termux` install CTAs — eliminate product paths |
| 7 | If a user already has `termux` marked installed in prefs, treat as legacy no-op (do not show card) |

**Acceptance:** Distros page shows **Debian** + **Debian (Rooted)** as the only non–coming-soon install cards (plus any future cards explicitly added later). **No Termux Native card.**

#### 4.1 Install / run / uninstall CTA matrix (only remaining product cards)

| Distro card | Mode | Component | Primary Install CTA | Run / shell | Uninstall |
|-------------|------|-----------|---------------------|-------------|-----------|
| **Debian** (`debian`) | PRoot | **`termux-flux-terminal`** | Open terminal → `flux_install.sh debian …` | `shell` / `shell-root` in same component | proot remove in terminal |
| **Debian (Rooted)** (`debian13_chroot`) | Chroot | **`chroot-root-shell`** | Open root shell / job → `setup_debian13_chroot.sh` | helper `login` flux/root | SSOT uninstall + umount |
| ~~Termux Native (`termux`)~~ | — | — | **REMOVED** | — | — |
| Other distros | coming soon | — | no install | — | — |

| Component install cards (inside DistroSettings for debian*) | Runner |
|------------------------------------------------------------|--------|
| xfce4, hw_accel, customization, app_dev, … (debianComponents) | **Same component as parent distro** (proot terminal vs chroot root shell) — never Termux intent |

#### 4.2 InstallConfigScreen wiring

| Before | After |
|--------|-------|
| “Copy & Open Termux” | **“Install in Flux Terminal”** / **“Install in Root Shell”** |
| `TermuxIntentFactory` + `com.termux` | `prepareHost` + `openInstallSession` only |
| InstallServerService HTTP→Termux | **Do not start** for debian* product install |

#### 4.3 Method preference

```text
Per distro id (card actions):
  debian            → always termux-flux-terminal (proot)
  debian13_chroot   → always chroot-root-shell
Shared Terminal page only: optional last-method UI default (never overrides card routing)
```

#### 4.4 Tutorial + prerequisites updates

| Doc / screen | Change |
|--------------|--------|
| `docs/tutorial/setup_debian_proot.md` | In-app Flux Terminal; no paste-into-Termux as primary |
| `docs/tutorial/setup_debian_chroot.md` | Root grant to **FluxLinux** + BusyBox; Root Shell install |
| `docs/tutorial/setup_fluxlinux.md` | Init = extract bootstrap (not Termux APK) |
| `PrerequisitesScreen` | **No** mandatory Termux APK / RUN_COMMAND for core CLI |
| `SettingsScreen` | Host bootstrap status + re-extract; drop required external connection panel |
| `DistroSettingsScreen` uninstall | `openUninstallSession` only (no Termux launch) |
| `docs/architecture.md` | Embedded host + two cards |

#### 4.5 External Termux intent path (Pass 2 hard policy)

| Policy | Detail |
|--------|--------|
| Primary product | **Only** embedded components for `debian` + `debian13_chroot` |
| Termux Native card | **Deleted** |
| Install / Run shell / Uninstall / Component | **Zero** `TermuxIntentFactory` / `com.termux` package launch |
| GUI Start/Stop | **Deferred residual** — may still use intents; must not block COMPLETE for CLI if documented |
| `TermuxIntentFactory` | Debug flag only for residual helpers, or delete dead code after GUI pass |

#### 4.6 Migration (existing users)

| Case | Behavior |
|------|----------|
| Prefs say `debian` installed, no `…/containers/debian/rootfs/bin/sh` under **app** PREFIX | Treat **not installed**; offer Install (do not open empty shell as success) |
| Prefs say `termux` installed | Ignore; no card |
| Prefs say `debian13_chroot` installed, no `/data/local/tmp/chrootDebian13/bin/sh` | Treat not installed / repair CTA |
| Installed only in external Termux from old app version | No automatic import; user reinstalls into embedded host |

Installed-state SSOT for product cards = **filesystem** (`TerminalLauncher.isDebianProotInstalled` / chroot `bin/sh`), prefs are cache updated by callbacks.

#### 4.7 Phase 4 acceptance

| ID | Test | Pass |
|----|------|------|
| P4-T0 | Distro list | **No** Termux Native card; Debian + Debian (Rooted) present |
| P4-T1 | Debian Install CTA | Opens **termux-flux-terminal**, not `com.termux` |
| P4-T2 | Debian Rooted Install CTA | Opens **chroot-root-shell** / root job; not Termux |
| P4-T3 | Fresh install UX proot | Completes without Termux APK installed |
| P4-T4 | Fresh install UX chroot | Root + BusyBox + in-app install; FluxLinux has su grant |
| P4-T5 | Component install on debian | Runs in proot terminal session |
| P4-T6 | Component install on debian13_chroot | Runs via chroot helper |
| P4-T7 | Uninstall both | State + filesystem cleaned; no Termux intent |
| P4-T8 | StateManager | No new `termux` distro installs; legacy flag ignored |
| P4-T9 | Dual app id | both flavors independent |
| P4-T10 | Grep gate | no product install path for `id = "termux"` |
| **P4-T11** | Grep gate dual-path | `TermuxIntentFactory` **not** referenced from InstallConfig / component install / uninstall / shell open for debian* |
| **P4-T12** | Prerequisites | Core flow works with Termux APK **uninstalled** |
| **P4-T13** | Migration | stale “installed” pref + missing rootfs → shows Install, not broken shell |

---

## 4. File / module map (implementation checklist)

### 4.1 New scripts

```text
scripts/package_host_assets.sh          # Phase 0
scripts/verify_apk_host_assets.sh       # unzip APK checks (optional CI)
```

### 4.2 Kotlin modules (Pass 2 shape)

```text
app/src/main/kotlin/com/ivarna/fluxlinux/
  core/terminal/
    TermuxHostPaths.kt
    HostCommandBuilder.kt
    ProotCommandBuilder.kt
    ChrootCommandBuilder.kt      # no cycle with RootShell
    LinuxCommandBuilder.kt       # explicit method param for card paths
    ShellCommandRunner.kt
    BootstrapInstaller.kt        # recovery markers
    HostScriptDeployer.kt        # fail-closed rootfs
    TerminalLauncher.kt
    SessionRegistry.kt           # Pass 2 split: tabs + FGS + view
    InstallSessionFactory.kt     # Pass 2 split
    GuestSessionFactory.kt       # Pass 2 split
    UninstallSessionFactory.kt   # Pass 2 split
    FluxTerminalSessionManager.kt  # thin facade OR deleted after call-site migrate
  core/root/
    RootShell.kt                 # su only
  core/data/
    TerminalComponent.kt         # terminalComponentFor SSOT
    DistroRepository.kt          # no termux; debian.chrootSupported=false
  core/service/
    AppTerminalService.kt
    InstallServerService.kt      # not primary for debian* (Pass 2)
  ui/screens/
    TerminalScreen.kt
    InstallConfigScreen.kt
    PrerequisitesScreen.kt       # no hard Termux APK gate
    SettingsScreen.kt            # host status primary
```

Namespace: package stays `com.ivarna.fluxlinux`; **runtime paths** from `BuildConfig.APPLICATION_ID`.

### 4.3 Assets

```text
app/src/main/assets/
  bootstrap.tar                 # via packaging (flavor-specific input)
  rootfs/debian_13_rootfs.tar.xz
  scripts/host/setup_termux.sh
  scripts/debian/proot/setup/flux_install.sh   # replace with nativecode local-rootfs version
  scripts/chroot/fluxlinux_chroot.sh
  scripts/chroot/setup_debian13_chroot.sh      # port rootfs resolve from nativecode
  scripts/chroot/uninstall_debian13_chroot.sh
  fonts/font.ttf                                 # optional terminal font
app/src/<flavor>/jniLibs/arm64-v8a/
  libbash.so libproot.so libloader.so libloader32.so
```

### 4.4 Manifest

- `AppTerminalService` + `foregroundServiceType=specialUse` + property XML (Play policy — copy nativecode declaration pattern).
- Deep link `fluxlinux://callback` keep for install completion.
- `extractNativeLibs` / legacy packaging already via `useLegacyPackaging = true`.

### 4.5 Docs

```text
docs/plans/embedded-terminal-bootstrap-proot-chroot.md   # this file
docs/environment/fluxlinux-chroot-ssot.md                # port SSOT
docs/environment/host-package-ssot.md                    # TermuxHostPaths
docs/tutorial/setup_debian_{proot,chroot}.md             # update
docs/plans/termux-native-packages-dual-appid.md          # link follow-on COMPLETE packaging
```

---

## 5. Detailed testing plan

### 5.1 Test environments

| Env | Purpose |
|-----|---------|
| Emulator arm64 (no root) | Phase 1–2 only (proot) |
| Physical device unrooted | proot E2E, chroot gated |
| Physical device KSU/Magisk + BusyBox | full chroot |
| Both APK flavors | path/prefix isolation |

### 5.2 Host unit / instrumentation (no full rootfs) — **Pass 2 exit criteria**

| Suite | Cases | Status target |
|-------|-------|---------------|
| `TermuxHostPathsTest` | rewriteStockPaths; writeHostEnvFile keys; PACKAGE from BuildConfig | exists — keep green |
| `TerminalComponentTest` | debian → proot; debian13_chroot → chroot; termux throws | exists |
| `ProotCommandBuilderTest` | argv shape interactive vs payload; user root vs flux | **add** |
| `ChrootCommandBuilderTest` | simple vs b64; session user; workdir parse; SESSION_EXEC | **add** |
| `LinuxCommandBuilderTest` | explicit method dispatch; no reliance on ambient global for card cases | **add** |
| `DistroRepositoryTest` | no termux card; debian chrootSupported false | extend |

### 5.3 Device smoke scripts (ADB)

Pass 2 note: **no device was available during Pass 2 code work** — R1–R11 not yet run. Exact commands below (unrooted + rooted device):

```bash
# ── R1: cold-start bootstrap extract ──
./gradlew :app:assembleIvarnaDebug
adb install -r app/build/outputs/apk/ivarna/debug/app-ivarna-debug.apk
adb shell monkey -p com.ivarna.fluxlinux 1   # open app → onboarding → Initialize Environment
adb shell run-as com.ivarna.fluxlinux ls files/usr/bin/proot-distro   # expect present
adb shell run-as com.ivarna.fluxlinux ls files/usr/bin/bash

# ── R2: setup_termux host gate ──
adb shell run-as com.ivarna.fluxlinux cat files/home/.fluxlinux/setup_termux.done  # expect "2"

# ── R3: Debian proot install OFFLINE (rootfs from app assets) ──
adb shell "settings put global airplane_mode_on 1" || true
# in-app: Distros → Debian → Install in Flux Terminal → wait for install session exit
adb shell run-as com.ivarna.fluxlinux ls \
  files/usr/var/lib/proot-distro/containers/debian/rootfs/bin/sh   # expect present
adb shell "settings put global airplane_mode_on 0" || true

# ── R4: proot shell flux/root ──
# in-app: Home → Debian → Open Shell / Open Root Shell
# expect: whoami → flux | root

# ── R5–R6: chroot install + shells (ROOTED device, KSU/Magisk + BusyBox) ──
# in-app: Distros → Debian (Rooted) → Install in Root Shell (grant su to FluxLinux)
adb shell timeout 10 sh /data/local/tmp/fluxlinux_chroot.sh version        # fluxlinux-chroot v2.2
adb shell timeout 15 sh /data/local/tmp/fluxlinux_chroot.sh sh --user flux -- 'whoami; id'
adb shell timeout 15 sh /data/local/tmp/fluxlinux_chroot.sh login --user root --shell bash -c 'echo root-ok'
adb shell ls /data/local/tmp/chrootDebian13/bin/sh

# ── R7: method switch proot ↔ chroot (rooted only) ──
# open one proot tab + one chroot tab in Terminal; both must stay independent

# ── R8: uninstall ──
# proot: DistroSettings → Uninstall (proot-distro remove in Flux Terminal)
adb shell run-as com.ivarna.fluxlinux test ! -e \
  files/usr/var/lib/proot-distro/containers/debian/rootfs/bin/sh && echo removed
# chroot: DistroSettings → Uninstall (Root Shell) → expect umount + rm
adb shell test ! -e /data/local/tmp/chrootDebian13/bin/sh && echo removed

# ── R9: no com.termux host PREFIX ──
adb shell run-as com.ivarna.fluxlinux sh -c \
  'grep -R com.termux files/usr/etc files/usr/bin 2>/dev/null | head'   # expect empty (or docs only)

# ── R10–R11: UI gates ──
# R10: Distros tab shows Debian + Debian (Rooted) only (no Termux Native card)
# R11: Debian Install opens in-app Flux Terminal; Debian (Rooted) opens Root Shell —
#      verify NO com.termux activity launches: adb shell dumpsys activity activities | grep -i termux

# ── zenithblue package-id smoke ──
./gradlew :app:assembleZenithblueDebug
adb install -r app/build/outputs/apk/zenithblue/debug/app-zenithblue-debug.apk
adb shell run-as com.zenithblue.fluxlinux cat files/usr/etc/fluxlinux-host.env | grep PACKAGE
# expect TERMUX_APP__PACKAGE_NAME=com.zenithblue.fluxlinux
```

### 5.4 Regression matrix (release gate)

Status: **all cells pending device run** (Pass 2 code complete; no device attached during code pass).

| # | Scenario | ivarna | zenithblue | unrooted | rooted |
|---|----------|--------|------------|----------|--------|
| R1 | Cold start extract bootstrap | ⏳ | ⏳ | ⏳ | ⏳ |
| R2 | setup_termux pass | ⏳ | ⏳ | ⏳ | ⏳ |
| R3 | Debian proot install offline | ⏳ | ⏳ | ⏳ | ⏳ |
| R4 | Proot shell flux/root | ⏳ | ⏳ | ⏳ | ⏳ |
| R5 | Debian chroot install | ⏳ | ⏳ | skip | ⏳ |
| R6 | Chroot shell flux/root | ⏳ | ⏳ | skip | ⏳ |
| R7 | Method switch proot↔chroot | ⏳ | ⏳ | partial | ⏳ |
| R8 | Uninstall proot / chroot | ⏳ | ⏳ | ⏳ | ⏳ |
| R9 | No com.termux host PREFIX | ⏳ | ⏳ | ⏳ | ⏳ |
| R10 | Termux Native card absent from Distro list | ⏳ | ⏳ | ⏳ | ⏳ |
| R11 | Debian Install opens termux-flux-terminal; Debian Rooted opens chroot-root-shell (not com.termux) | ⏳ | ⏳ | ⏳ | ⏳ |

### 5.5 Performance / size budget

| Asset | Budget |
|-------|--------|
| bootstrap.tar | ~122 MB per flavor (not both in one APK) |
| rootfs xz | ~82 MB shared |
| jniLibs | ~1.1 MB |
| APK download | expect ~200 MB+ ; document Play/F-Droid size; consider on-demand download later **not** in v1 of this plan |

### 5.6 Failure modes & expected UX

| Failure | UX |
|---------|-----|
| Bootstrap extract I/O error | Dialog + retry; log; no false “ready” |
| Partial / corrupt extract | Force re-extract; clear marker |
| setup_termux missing proot | Show which check failed; offer re-extract |
| Rootfs missing / SHA fail | prepareHost false; “Rebuild APK / re-deploy assets” |
| proot-distro fail | Keep terminal open with logs |
| su denied | Prompt grant root; don’t hang UI |
| BusyBox missing | Existing BusyBox install screen |
| Mount storm risk | Helper idempotent only; no “repair” loops in UI |
| Stale installed pref | FS truth; show Install |

### 5.7 Rollback (minimal)

| Failed step | Rollback |
|-------------|----------|
| proot-distro install half-done | `proot-distro remove debian` via host command session; clear marker |
| chroot extract half-done | uninstall script / umount + rm rootfs (user-confirmed) |
| bootstrap extract fail | delete partial tmp; leave previous PREFIX if any |

---

## 6. Implementation order

### 6.1 Pass 1 (done — bulk port; do not redo from scratch)

```text
0–4 bulk: flavors, script staging, TermuxHostPaths, builders, terminal UI,
         chroot helper, catalog drop termux, primary Install CTAs
```

### 6.2 Pass 2 order (do not reorder casually)

```text
A. Gradle packageHostAssets* gate + P0-T6/T7
B. Bootstrap atomic/recovery + rootfs fail-closed (P1-T7/T8)
C. Fix install argv (no quoted setupB64); builder unit tests
D. Split session god-object (registry + factories) + break RootShell↔Chroot cycle
E. Method always from terminalComponentFor for card actions; deprecate ambient currentMethod for those paths
F. Kill dual-path: InstallConfig, MainActivity install/uninstall/component, DistroSettings uninstall,
   Prerequisites hard Termux gate, Settings host panel (not external Termux)
G. debian.chrootSupported = false; migration FS truth for installed state
H. Grep gates P4-T10…T13
I. Device R1–R11 (ivarna unrooted + rooted; zenithblue package-id smoke)
J. Status → COMPLETE only with log pointers
```

**GUI Start/Stop Termux intents:** leave if needed; document residual; do not expand GUI scope in Pass 2.

---

## 7. Mapping from nativecode → FluxLinux (port table)

| nativecode | FluxLinux target | Adapt |
|------------|------------------|-------|
| `com.zenithblue.nativecode` | `BuildConfig.APPLICATION_ID` | dual flavor |
| `OnboardingActivity` extract phases B–D | `BootstrapInstaller` + optional onboarding/settings | Compose UI |
| `MainActivity` terminal | `TerminalScreen` + session manager | smaller surface first |
| `nativecode_chroot.sh` | `fluxlinux_chroot.sh` | rename stamp + NC_* → FLUX_* env if desired |
| `AppTerminalService` | same pattern | channel ids FluxLinux-prefixed |
| `flux_install.sh` | replace stock-termux script | local rootfs SSOT |
| `setup_debian13_chroot.sh` | update resolve list | same rootfs as proot |
| Marketplace / AI / git | **defer** | not required for terminal/install |

---

## 8. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| **Permanent dual orchestrator** (embedded + Termux intent) | Pass 2 dual-path kill + grep gates |
| APK size / Play limit | Single flavor per APK; document; future on-demand bootstrap **not** v1 |
| Assemble without bootstrap | Gradle fail-closed package task |
| Partial extract / corrupt host | Atomic extract + marker + force re-extract |
| W^X / targetSdk 36 | jniLibs legacy packaging + libbash/libproot as `.so` |
| SELinux blocks script exec | Chroot SESSION_EXEC = `/system/bin/sh`; helper via `sh` |
| Dual package rewrite misses | `applyPackageToExtractedPrefix` after every extract; setup_termux re-validate |
| Chroot device crash | Postmortem: no mount loops; one probe; timeout |
| Global `currentMethod` races | Method always from `terminalComponentFor` on card paths |
| God session manager | Split registry + factories (Pass 2) |
| GPLv3 termux-app dep | Keep app open source; attribute |
| F-Droid large assets | Reproducible packaging from `native/bootstrap`; assets not in git |
| False COMPLETE status | Device R1–R11 + log pointers required |

---

## 9. Definition of Done

### 9.1 Pass 1 (code port — **landed**, not product-complete)

- [x] `package_host_assets.sh` exists; flavors + noCompress + legacy jni
- [x] Rootfs asset path; shared proot + chroot install scripts
- [x] `TermuxHostPaths` SSOT (`BuildConfig.APPLICATION_ID`)
- [x] termux-flux-terminal + chroot-root-shell code paths exist
- [x] Termux Native card removed from `DistroRepository`
- [x] InstallConfig primary CTA labels in-app terminal
- [x] Tutorials partially updated for in-app flow

### 9.2 Pass 2 (required for **COMPLETE**)

**Status 2026-08-05 (code + review round 2):** all code items landed on `feat/embedded-terminal-bootstrap-proot-chroot`; review blockers B1–B3 and majors M1/M2 fixed; **device R-matrix pending** — status stays **PARTIAL** until R1–R11 evidence.

- [x] Gradle `packageHostAssets*` **gates** assemble (P0-T6, P0-T7) — verified: missing bootstrap → assemble fails; task runs as dependency
- [x] Bootstrap recovery + rootfs fail-closed (P1-T7, P1-T8) — version marker, temp-extract→promote, rootfs size+SHA gate, `prepareHost` fail-closed
- [x] **B2:** re-extract preserves `usr/var/lib/proot-distro` (installed guests survive); setup_termux failure NEVER wipes the prefix; corrupt-tree re-extract only
- [x] **B1:** chroot root install/uninstall sessions seeded with `HostCommandBuilder.envMap` + `FLUX_ROOTFS_PATH` — dual-app-id SSOT on the chroot path
- [x] **B3:** `onFinished` success callbacks fire only on `exitStatus == 0` (failed installs never mark state)
- [x] **M1:** wizard component chain — selected components run sequentially after successful base install
- [x] **M2:** loader.apk deploy included in fail-closed (no forced wipe follows)
- [x] Install argv contract correct (no quoted setupB64) — raw argv in `InstallSessionFactory`
- [x] Builder unit tests green (Proot/Chroot/LinuxCommandBuilder + DistroRepository/TerminalComponent) — 33 tests pass
- [x] Session manager split (registry + factories, thin facade) ; RootShell cycle broken via `ChrootPaths` + `RootShell.ensureChrootHelper`
- [x] Card actions: method from `terminalComponentFor` only (incl. Qwen run path); `currentMethod` deprecated (UI-default only)
- [x] **Zero** `TermuxIntentFactory` for debian* install / run shell / uninstall / component (P4-T11) — grep gate clean; residual sites are GUI launch/stop + legacy tweaks (documented)
- [x] Prerequisites: core CLI without Termux APK (P4-T12) — steps 1 + 7 use embedded `prepareHost`
- [x] Settings: host status primary; external Termux marked legacy-optional
- [x] `debian.chrootSupported = false`; installed state = FS truth (P4-T13) — HomeScreen/DistroScreen read filesystem
- [x] InstallServerService not primary for debian* install — `@Deprecated`, no product starts
- [ ] R1–R11 on ivarna (unrooted + rooted as applicable); zenithblue package-id smoke — **pending device**; exact ADB commands in §5.3
- [ ] Status line → **COMPLETE** + device log pointers — blocked by device run

**GUI residual:** Start/Stop GUI may still use Termux intents until a later GUI pass — documented; does not block CLI COMPLETE.

---

## 10. Next action — **Pass 2 only**

1. Wire Gradle packaging gate (A).  
2. Harden extract/rootfs (B) + fix install argv (C).  
3. Cohesion: split sessions + RootShell cycle (D–E).  
4. Kill dual-path product surfaces (F–H).  
5. Device matrix (I); mark COMPLETE (J).

Do **not** re-port nativecode from scratch. Do **not** expand to GUI marketplace/X11 parity.  
Worker prompt: **Appendix C**.

---

## Appendix A — Command cheatsheet (dev)

```bash
# Packages (already COMPLETE for both app ids)
./scripts/build_packages_for_appid.sh com.ivarna.fluxlinux
./scripts/assemble_bootstrap.py --package-name com.ivarna.fluxlinux --mode full
./scripts/verify_bootstrap.sh com.ivarna.fluxlinux

# Stage (or let Gradle task run)
./scripts/package_host_assets.sh com.ivarna.fluxlinux
./gradlew :app:assembleIvarnaDebug

# Rootfs identity
sha256sum assets/rootfs/debian_13_rootfs.tar.xz
# expect 13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803

# Dual-path grep gate (Pass 2)
rg -n 'TermuxIntentFactory' app/src/main/kotlin --glob '**/*.kt'
rg -n 'com\.termux' app/src/main/kotlin/com/ivarna/fluxlinux/ui --glob '**/*.kt'
```

## Appendix B — Related docs

| Doc | Role |
|-----|------|
| [termux-native-packages-dual-appid.md](./termux-native-packages-dual-appid.md) | Package build COMPLETE |
| `~/repos/termux-lib/docs/environment/nativecode-chroot-ssot.md` | Chroot SSOT semantics |
| `~/repos/termux-lib/docs/plan/proot-debian-rootfs-local-install.md` | Local rootfs install |
| `~/repos/termux-lib/docs/plan/host-setup-termux-package-env.md` | Host env SSOT |
| `~/repos/termux-lib/docs/plan/terminal-debian-shell-rooted.md` | Shell / shell-root cards |
| `docs/tutorial/setup_debian_proot.md` | User tutorial |
| `docs/tutorial/setup_debian_chroot.md` | User tutorial |

## Appendix C — Worker agent prompt (Pass 2)

Copy-paste the block below for the implementation agent.

```text
You are implementing Pass 2 of the FluxLinux embedded terminal plan.

READ FIRST (SSOT):
- docs/plans/embedded-terminal-bootstrap-proot-chroot.md  (status PARTIAL; follow §6.2 Pass 2 order)
- Branch: feat/embedded-terminal-bootstrap-proot-chroot
- Reference only if needed: ~/repos/termux-lib (do NOT re-port from scratch)

GOAL OF PASS 2:
Close dual-path (embedded host is the only product path for Debian install/run/uninstall/component),
harden packaging + extract/rootfs recovery, fix argv contract, improve cohesion, then leave device R-matrix ready.
Do NOT expand scope to GUI marketplace, X11 parity, or other distros.
Do NOT mark the plan COMPLETE unless R1–R11 evidence is attached (if you cannot run devices, leave COMPLETE unchecked and list exact ADB steps).

CONSTRAINTS:
- High cohesion / low coupling: follow plan §2.5 module boundaries.
- RootShell must NOT import ChrootCommandBuilder (break cycle).
- Card actions: method = terminalComponentFor(distroId).method only (no ambient currentMethod for cards).
- Install argv: flux_install.sh gets raw setupB64 as argv — NEVER wrap with literal quotes.
- GUI Start/Stop may still use TermuxIntentFactory (document residual); everything else for debian* must not.
- Prefer small targeted fixes over rewrites. Keep public UX: Debian + Debian (Rooted) only install cards.

WORK ORDER (§6.2 A→J):

A. Gradle packaging gate
   - Add packageHostAssetsIvarna / packageHostAssetsZenithblue (or one task parameterized by flavor).
   - inputs: native/bootstrap/<appId>/** ; outputs: flavor assets/jniLibs + main rootfs if needed.
   - Wire so assembleIvarna* / assembleZenithblue* depends on the matching task.
   - Fail build with clear error if bootstrap.tar or required jniLibs missing.
   - Keep scripts/package_host_assets.sh as the script the task runs.
   - Verify P0-T6/T7.

B. Bootstrap + rootfs recovery
   - BootstrapInstaller: version marker; force re-extract on setup_termux failure / force flag; avoid “libtermux-exec exists ⇒ always OK” for corrupt trees.
   - Prefer extract to temp then promote when practical.
   - HostScriptDeployer.deployRootfsFromAssets: size + SHA256 gate
     (expect 13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803);
     return Boolean success.
   - TerminalLauncher.prepareHost: return false if extract OR rootfs deploy OR setup_termux fails (fail-closed).
   - UI: host re-extract action still reachable (Settings).

C. Install argv + unit tests
   - Fix openInstallSession / openHostScriptSession so setupB64 is not "\"...\"".
   - Add ProotCommandBuilderTest, ChrootCommandBuilderTest, LinuxCommandBuilderTest (argv shape, method dispatch).
   - Extend DistroRepositoryTest: no termux card; debian.chrootSupported == false.

D. Cohesion
   - Split FluxTerminalSessionManager responsibilities:
     SessionRegistry (tabs, view attach, FGS) vs Install/Guest/Uninstall session factories.
     Thin facade OK if call sites stay stable.
   - Break RootShell ↔ ChrootCommandBuilder import cycle (RootShell = su only).

E. Method routing
   - All card openSession*/install/uninstall/component paths pass explicit method from terminalComponentFor.
   - Do not let LinuxCommandBuilder.currentMethod leak across tabs for product actions.

F. Kill dual-path (product)
   Grep and fix so these use embedded terminal only for debian / debian13_chroot / debian_chroot:
   - MainActivity install / uninstall / component
   - InstallConfigScreen / InstallationQueueManager paths
   - DistroSettingsScreen uninstall (no buildOpenTermuxIntent)
   - PrerequisitesScreen: remove hard Termux APK + RUN_COMMAND as mandatory for core CLI; host prepare instead
   - SettingsScreen: host bootstrap status / re-extract primary; external Termux connection not required
   - Stop InstallServerService as primary install for debian*
   Leave GUI launch/stop Termux intents if needed; add a short residual comment in plan or code.

G. Catalog + migration
   - DistroRepository: debian.chrootSupported = false
   - Installed UI truth = filesystem (TerminalLauncher.isDebianProotInstalled / chroot bin/sh);
     stale prefs → show Install, not fake installed shell

H. Grep gates (must pass before claiming Pass 2 code done)
   - No product path id = "termux" install
   - TermuxIntentFactory not used for debian* install/run shell/uninstall/component
   - Document remaining TermuxIntentFactory call sites (expect GUI-only or debug)

I. Device (if available)
   - R1–R11 ivarna; zenithblue package-id smoke
   - If no device: do not check COMPLETE; write exact test commands under plan §5

J. Plan doc
   - Update §9.2 checkboxes honestly
   - Status COMPLETE only with device evidence; else keep PARTIAL and list remaining

ACCEPTANCE (code Pass 2):
- ./gradlew :app:assembleIvarnaDebug fails without staged bootstrap; succeeds with package task
- Unit tests for builders + TerminalComponent + DistroRepository green
- Dual-path grep gate for install/run/uninstall/component clean
- prepareHost fail-closed on missing rootfs
- No new scope (no AI marketplace, no full X11 redesign)

OUT OF SCOPE:
- Rebuilding native packages
- New distros
- GUI parity / Termux:X11 embedding
- Rewriting the entire plan

When done: summarize files changed, residual TermuxIntentFactory sites, and which §9.2 boxes are checked vs still open.
```
