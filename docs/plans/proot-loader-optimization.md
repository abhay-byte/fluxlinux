# Implementation Plan - Optimize PRoot Loader & Terminal Execution

**Task ID:** `proot-opt-01`  
**Date:** 2026-08-20  
**Pass:** 3 | **Iteration:** 3  
**Status:** READY  
**Primary Focus:** Optimize PRoot loader setup, eliminate startup overhead, avoid redundant sweeps/repairs/validation delays, streamline guest login execution, and improve Terminal UI rendering efficiency without touching the Chroot loader.

---

## 1. Problem Statement & Root Cause Archaeology

### 1.1 Inefficiencies Identified in Current PRoot Stack

1. **Slow PRoot Session Launch & Host Gating Overhead:**
   - On every interactive PRoot terminal session launch via `FluxTerminalSessionManager.openSessionAfterHost()`, `TerminalLauncher.prepareHost(ctx)` is called.
   - In `TerminalLauncher.prepareHostBlocking()`:
     - `BootstrapInstaller.ensureExtracted()` is called before checking `isHostSetupDone(ctx)`. Even when the bootstrap is already extracted, `ensureExtracted` unconditionally executes `TermuxHostPaths.applyPackageToExtractedPrefix()`, walking `usr/bin`, `usr/etc`, `usr/share`, `usr/lib/python3.14/...`, and `usr/include/termux-exec` to inspect and patch every file under 2 MB on **every single terminal open**.
     - `HostScriptDeployer.deployScripts()` is called unconditionally before checking `isHostSetupDone(ctx)`. It also invokes `TermuxHostPaths.applyPackageToExtractedPrefix()` a second time, then iterates over and writes 30+ host script assets, font files, and pulse runtime libraries to disk.
     - The `if (!forceHostSetup && isHostSetupDone(ctx)) return true` gate is currently placed **after** all of these heavy tree scans and disk writes have already completed.
   - This causes multi-second UI hangs and flash storage thrashing before `libbash.so` can spawn.

2. **Session Open Redundant Disk Scanning & Regex Patching:**
   - In `GuestSessionFactory.openSession()`, on **every session open**, the app runs:
     - `GuestZshrcRepair.repairIfNeeded(ctx, method, distroId)`: synchronously reads and parses `.zshrc`, `.zprofile`, `/etc/passwd`, `/etc/profile.d/flux-locale.sh`, `/usr/local/sbin/flux-ensure-locale`, and multiple fastfetch preset JSON files on the UI caller thread.
     - `GuestApkDbRepair.repairIfNeeded(ctx, method, distroId)`: runs recursive `ensureWritableTree()` sweeps across `lib/apk`, `var/cache/apk`, `var/log`, `etc/apk` resetting permissions and lock files on every Alpine/Chimera session launch.
   - Zero caching or state flags exist; every new tab or shell launch repeats these synchronous filesystem sweeps.

3. **Distro Installation & Host Script Deployment Redundancy:**
   - During `OnboardingInstallRunner.runProot()`:
     - `flux_install.sh` invokes `proot-distro install "$ROOTFS_ARCHIVE" --name "$DISTRO"` via Python.
     - Sequential post-install steps run redundant tree scans (`applyPackageToExtractedPrefix`) before and after extraction.
   - Note on `PulseHost.ensureStarted(ctx)`: `PulseHost` already uses an in-process `AtomicBoolean` (`startedThisProcess`) CAS guard, so subsequent calls in the same process are cheap no-ops, but it should still be gated properly during host startup.

4. **Terminal UI & TerminalView Frame Lag:**
   - `TerminalScreen.kt` recomposes during UI events, tab changes, and typing.
   - While `lastWinchKey` already dedups `Os.kill(pid, SIGWINCH)` on dimension/pid changes, `AndroidView.update` unconditionally invokes `FluxTerminalSessionManager.attachView(view)` and `view.requestFocus()` on every recomposition even when the active session attached to `TerminalView` has not changed.

---

## 2. Termux Architecture Comparison & Reference Pattern

- **Termux Core Pattern (`termux-app` / `TermuxSession`):**
  - Termux launches sessions directly via `TerminalSession` using JNI pty allocation (`createSubprocess`).
  - Environment variables (`PREFIX`, `HOME`, `PATH`, `TMPDIR`, `LD_PRELOAD`) are passed once in the `env` string array directly to `execve`.
  - Zero prefix scans or file modifications occur during regular interactive session launches. Prefix validation and package rewriting happen strictly at bootstrap install/upgrade time.
  - Zero disk repairs during normal interactive session startup.

---

## 3. Targeted Optimizations & Architecture Changes (PRoot Only)

### 3.1 Top-of-Function Fast-Path Host Preparation Gate (`TerminalLauncher.kt`)
- In `TerminalLauncher.prepareHostBlocking(ctx, forceHostSetup, progress)`:
  - **Move the fast-path check to the top of the function before any expensive calls:**
    ```kotlin
    if (!forceHostSetup && isHostSetupDone(ctx) && BootstrapInstaller.isExtracted(ctx)) {
        PulseHost.ensureStarted(ctx)
        return true
    }
    ```
  - When the host environment is already set up and extracted, `prepareHostBlocking` returns immediately in `< 5ms` without invoking `ensureExtracted()`, `deployScripts()`, or tree sweeps.

### 3.2 Gate Prefix Tree Walks in `BootstrapInstaller.kt` & `HostScriptDeployer.kt`
- **`BootstrapInstaller.ensureExtracted(ctx, force, onProgress)`:**
  - On the happy path (`!force && isExtracted(ctx)`), do **not** invoke `TermuxHostPaths.applyPackageToExtractedPrefix()`. The prefix tree is already rewritten when `markExtracted(ctx)` is called upon initial extraction.
  - Only execute `applyPackageToExtractedPrefix(ctx.filesDir, ctx)` during initial bootstrap extraction or when `force == true` (re-extraction/recovery).
- **`HostScriptDeployer.kt`:**
  - In `deployScripts(ctx, force = false)`:
    - Remove or gate the top `TermuxHostPaths.applyPackageToExtractedPrefix(ctx.filesDir, ctx)` call.
    - Check if script version / deploy marker is current before rewriting assets to disk. When already up to date and `!force`, skip file writes.

### 3.3 Versioned Caching for Guest Repairs (`GuestZshrcRepair.kt` & `GuestApkDbRepair.kt`)
- Implement persistent caching for guest repair passes using `SharedPreferences`:
  - **Preferences Name:** `flux_guest_repairs`
  - **Key Format with Version Suffix:**
    - Zshrc repair: `"guest_zshrc_repair_v2_${distroId ?: method}"`
    - Apk DB repair: `"guest_apk_db_repair_v1_${distroId ?: method}"`
  - **Behavior:**
    - `GuestZshrcRepair.repairIfNeeded(ctx, method, distroId)`: Check SharedPreferences. If key is `true`, return immediately. After completing a successful repair/verification pass, set key to `true`.
    - `GuestApkDbRepair.repairIfNeeded(ctx, method, distroId)`: Check SharedPreferences. If key is `true`, return immediately. After completing the writable tree / lock repair, set key to `true`.
    - Invalidation: If repair logic changes in future releases, incrementing the key version constant (e.g. `v2` → `v3`) automatically invalidates stale caches. Re-installation or user-triggered repair clears the key.
  - **Test Compatibility Requirement:**
    - `FakeContext` in `app/src/test/java/com/ivarna/fluxlinux/core/terminal/FakeContext.kt` MUST be updated to provide an in-memory `getSharedPreferences(name: String?, mode: Int)` implementation (using an in-memory `SharedPreferences` backing or delegating to an internal `InMemoryPrefs` instance) so that JVM unit tests calling `repairIfNeeded(FakeContext(...), ...)` (e.g., in `GuestZshrcRepairTest.kt` and `GuestApkDbRepairTest.kt`) do not throw `NullPointerException` on `ContextWrapper(null)`.

### 3.4 Streamline `GuestSessionFactory.kt` & `ProotCommandBuilder.kt`
- `GuestSessionFactory.openSession()`:
  - Invokes `GuestZshrcRepair.repairIfNeeded` and `GuestApkDbRepair.repairIfNeeded` which now return instantly (0ms) due to the cached flags.
  - Retains direct command building and environment configuration.
- `ProotCommandBuilder.kt`:
  - Verify clean parameter emission for `proot-distro login` avoiding duplicate wrapper subshells where possible.

### 3.5 TerminalScreen Compose & TerminalView Recomposition Dedup (`TerminalScreen.kt`)
- In `TerminalScreen.kt` within `AndroidView.update`:
  - Keep `lastWinchKey` guard for `SIGWINCH` resize signals.
  - Dedup `attachView` and `requestFocus` using `FluxTerminalSessionManager.activeSession?.session`:
    ```kotlin
    val activeTerminalSession = FluxTerminalSessionManager.activeSession?.session
    if (view.currentSession != activeTerminalSession || activeTerminalSession == null) {
        FluxTerminalSessionManager.attachView(view)
        view.requestFocus()
    }
    ```
  - Avoid re-attaching the view and re-requesting focus on non-session recompositions (such as typing, modifier updates, or font size updates).

### 3.6 Strict Boundary: Do Not Touch Chroot Loader
- All modifications apply strictly to PRoot paths: `TerminalLauncher`, `BootstrapInstaller`, `HostScriptDeployer`, `GuestSessionFactory`, `GuestZshrcRepair`, `GuestApkDbRepair`, `ProotCommandBuilder`, and `TerminalScreen`.
- `ChrootCommandBuilder`, `ChrootProcessManager`, `ChrootDetection`, `fluxlinux_chroot.sh`, and root shell pathways remain completely untouched.

---

## 4. Authoritative Files & Modifications

| File | Proposed Change |
|---|---|
| `app/src/main/kotlin/com/ivarna/fluxlinux/core/terminal/TerminalLauncher.kt` | Move fast-path check to the top of `prepareHostBlocking` before `ensureExtracted` / `deployScripts`. |
| `app/src/main/kotlin/com/ivarna/fluxlinux/core/terminal/BootstrapInstaller.kt` | Remove redundant `applyPackageToExtractedPrefix` call from happy path (`!force && isExtracted`). |
| `app/src/main/kotlin/com/ivarna/fluxlinux/core/terminal/HostScriptDeployer.kt` | Gate / eliminate redundant prefix scan on deploy; skip asset re-deployment when already deployed. |
| `app/src/main/kotlin/com/ivarna/fluxlinux/core/terminal/GuestSessionFactory.kt` | Fast and non-blocking session creation with cached repair checks. |
| `app/src/main/kotlin/com/ivarna/fluxlinux/core/terminal/GuestZshrcRepair.kt` | Add versioned per-distro SharedPreferences flag (`flux_guest_repairs`, `"guest_zshrc_repair_v2_$distroId"`) to skip scans on subsequent opens. |
| `app/src/main/kotlin/com/ivarna/fluxlinux/core/terminal/GuestApkDbRepair.kt` | Add versioned per-distro SharedPreferences flag (`flux_guest_repairs`, `"guest_apk_db_repair_v1_$distroId"`) to skip recursive permission sweeps on Alpine/Chimera. |
| `app/src/main/kotlin/com/ivarna/fluxlinux/core/terminal/ProotCommandBuilder.kt` | Ensure clean, direct argument formatting for `proot-distro login`. |
| `app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/TerminalScreen.kt` | Dedup `attachView` and `requestFocus` in `AndroidView.update` comparing `view.currentSession` against `FluxTerminalSessionManager.activeSession?.session`. |
| `app/src/test/java/com/ivarna/fluxlinux/core/terminal/FakeContext.kt` | Implement in-memory `getSharedPreferences` stub so unit tests running `GuestZshrcRepair` and `GuestApkDbRepair` don't throw NPE. |
| `app/src/test/java/com/ivarna/fluxlinux/core/terminal/ProotCommandBuilderTest.kt` | Verify unit test coverage for proot command builder and host paths. |

---

## 5. Test Strategy & Validation Plan

1. **Unit Tests:**
   - Execute `./gradlew :app:testIvarnaDebugUnitTest --tests "com.ivarna.fluxlinux.core.terminal.*"`
   - Verify `ProotCommandBuilderTest`, `GuestZshrcRepairTest`, `TermuxHostPathsTest`, `LinuxCommandBuilderTest`.
   - Add unit test validating that `BootstrapInstaller.isExtracted(ctx)` and `TerminalLauncher.isHostSetupDone(ctx)` fast-path logic behaves correctly.

2. **Benchmarking & Latency Verification:**
   - Measure time spent in `TerminalLauncher.prepareHostBlocking` when host is ready. Expected: < 5ms.
   - Measure session spawn latency from tab creation to prompt. Expected: instant (< 300ms).
   - Verify no recursive file tree scans occur in logcat during new terminal tab launches.

3. **Distro Lifecycle & Regression Verification:**
   - Test PRoot Debian / Alpine / Arch / Ubuntu launches.
   - Verify Chroot execution paths remain unaffected.
   - Verify Terminal typing and UI interactions are responsive without UI jank.

---

## 6. Acceptance Criteria

- [x] Host prepare fast-path in `TerminalLauncher.prepareHostBlocking` returns in < 5ms when host is ready.
- [x] `BootstrapInstaller.ensureExtracted` happy path does not execute `applyPackageToExtractedPrefix`.
- [x] `HostScriptDeployer.deployScripts` does not perform redundant prefix walks.
- [x] Guest repairs (`GuestZshrcRepair` and `GuestApkDbRepair`) utilize versioned SharedPreferences flags (`flux_guest_repairs`) and skip redundant filesystem sweeps.
- [x] PRoot guest login session opens instantly without scanning filesDir or thrashing storage.
- [x] TerminalScreen `AndroidView.update` dedups `attachView` and `requestFocus`.
- [x] Chroot loader and chroot scripts remain completely untouched.
- [x] All unit tests in `:app:testIvarnaDebugUnitTest` pass.
