# Plan Review — PRoot Loader Optimization
**Task ID:** proot-opt-01  
**Plan file:** docs/plans/proot-loader-optimization.md  
**Review date:** 2026-08-20  
**Pass:** 3 | **Iteration:** 3  
**Verdict:** APPROVE

---

## Pass 2 Finding Resolution

Both Pass 2 [MAJOR] findings are confirmed resolved:

### [MAJOR] FakeContext NPE on `getSharedPreferences` → ✅ RESOLVED
Plan §3.3 now explicitly mandates: "`FakeContext` in `app/src/test/…/core/terminal/FakeContext.kt` MUST be updated to provide an in-memory `getSharedPreferences` implementation." The requirement is unambiguous. `FakePrefsContext` already exists in `core.utils` and already extends `FakeContext` with a working `InMemoryPrefs` — Worker can reuse that class or inline the same pattern into `FakeContext`.

### [MAJOR] Wrong API `currentSession()` / type mismatch → ✅ RESOLVED
Plan §3.5 now contains the exact corrected snippet:
```kotlin
val activeTerminalSession = FluxTerminalSessionManager.activeSession?.session
if (view.currentSession != activeTerminalSession || activeTerminalSession == null) {
    FluxTerminalSessionManager.attachView(view)
    view.requestFocus()
}
```
`FluxTerminalSessionManager.activeSession` (property, not method) returns `ManagedSession?`; `.session` is `TerminalSession?`. `view.currentSession` is `TerminalSession` (confirmed used at `TerminalScreen.kt:175`). Types align.

---

## New Findings (Pass 3)

### [MINOR] `HostScriptDeployer` deploy-marker spec underspecified
**Location:** Plan §3.2, `HostScriptDeployer.kt`  
**Problem:** Plan says "check if script version / deploy marker is current before rewriting assets" but gives no marker file path, key name, or version constant. Worker must invent all of this.  
**Evidence:** No existing deploy marker in `HostScriptDeployer.kt` or any related file. No `deployMarker`, `DEPLOY_VERSION`, or `scripts_deployed` constant exists anywhere in the codebase.  
**Impact:** Low. The top-level fast-path in `prepareHostBlocking` already skips `deployScripts()` entirely on the warm path (`isHostSetupDone && isExtracted`). The 30-script loop overhead only occurs on first-install or forced recovery. Worker can implement any reasonable marker without correctness risk.  
**Required planner change:** None blocking. Worker can use any stable marker (e.g., `home/.fluxlinux/scripts.deployed` with a version integer). Document the chosen path in a comment.

### [SUGGESTION] Reuse existing `FakePrefsContext` instead of patching `FakeContext`
**Location:** `app/src/test/java/com/ivarna/fluxlinux/core/utils/FakePrefsContext.kt`  
**Problem:** Plan says add `getSharedPreferences` stub to `FakeContext`. `FakePrefsContext` already exists, extends `FakeContext`, and provides a complete `InMemoryPrefs` implementation. Migrating `GuestZshrcRepairTest` to use `FakePrefsContext` instead of `FakeContext` is less code than duplicating the in-memory prefs logic.  
**Evidence:** `FakePrefsContext.kt` (line 12–19) already overrides `getSharedPreferences` using `InMemoryPrefs`. It is already used by `ChrootInfoStoreTest`, `DesktopSessionQueryTest`, `ProotSizeManagerTest`, `LegacyTermuxCallbackTest`.  
**Impact:** None. Either approach compiles and passes tests. Reuse is less code.  
**Required planner change:** None. Informational only.

---

## Pass 2 Finding Status (verified resolved)

| Finding | Pass 2 Status | Pass 3 Status |
|---|---|---|
| [MAJOR] `FakeContext` NPE on `getSharedPreferences` | REVISE | ✅ RESOLVED |
| [MAJOR] Wrong API `currentSession()` / type mismatch | REVISE | ✅ RESOLVED |

## Pass 1 Finding Status (verified still resolved)

| Finding | Pass 1 Status | Pass 3 Status |
|---|---|---|
| [CRITICAL] Fast-path gate placed wrong | REVISE | ✅ RESOLVED |
| [MAJOR] `deployScripts` uncovered | REVISE | ✅ RESOLVED |
| [MAJOR] `GuestZshrcRepair` caching underspecified | REVISE | ✅ RESOLVED |
| [MINOR] PulseHost cost misstated | REVISE | ✅ RESOLVED |
| [MINOR] SIGWINCH already guarded | REVISE | ✅ RESOLVED |
| [SUGGESTION] `GuestApkDbRepair` not included | SUGGESTION | ✅ RESOLVED |

---

## Architecture Verification (Pass 3)

| Claim | Verified |
|---|---|
| `TerminalLauncher.prepareHostBlocking` calls `ensureExtracted` before `isHostSetupDone` gate | ✅ Confirmed (lines 181–189 of `TerminalLauncher.kt`) |
| `BootstrapInstaller.ensureExtracted` calls `applyPackageToExtractedPrefix` on happy path | ✅ Confirmed (lines 84–88) |
| `HostScriptDeployer.deployScripts` calls `applyPackageToExtractedPrefix` unconditionally | ✅ Confirmed (line 142) |
| `GuestZshrcRepair.repairIfNeeded` has no caching | ✅ Confirmed — no SharedPreferences usage in current source |
| `GuestApkDbRepair.repairIfNeeded` has no caching, runs `ensureWritableTree` recursively | ✅ Confirmed (lines 60–64) |
| `FluxTerminalSessionManager.activeSession` is a property returning `ManagedSession?` | ✅ Confirmed (line 40, `FluxTerminalSessionManager.kt`) |
| `SessionRegistry.ManagedSession.session` is `TerminalSession?` | ✅ Confirmed (line 30, `SessionRegistry.kt`) |
| `TerminalView.currentSession` exists and compiles | ✅ Confirmed (already used at `TerminalScreen.kt:175`, repo compiles) |
| `FakePrefsContext` in `core.utils` extends `FakeContext` with `getSharedPreferences` | ✅ Confirmed (lines 12–19, `FakePrefsContext.kt`) |
| Chroot loader files excluded from scope | ✅ Plan §3.6 explicit exclusion list matches repo files |
| `PulseHost.ensureStarted` already guarded by `AtomicBoolean` | ✅ Plan §1.1 correctly notes this |

---

## Acceptance Criteria Mapping vs. Reality

| Criterion | Achievable | Note |
|---|---|---|
| Host prepare fast-path < 5ms when host ready | ✅ | Fast-path at top of `prepareHostBlocking` before heavy calls |
| `BootstrapInstaller.ensureExtracted` happy path skips `applyPackageToExtractedPrefix` | ✅ | Plan §3.2 specifies removal from `!force && isExtracted` branch |
| `HostScriptDeployer.deployScripts` no redundant prefix walks | ✅ (warm-path) | Top-level fast-path skips `deployScripts` entirely; deploy-marker for first-run is underspecified but non-blocking |
| Guest repairs use versioned SharedPreferences flags | ✅ | Key format, version suffix, invalidation strategy fully specified |
| PRoot session opens instantly | ✅ | Repairs become 0ms no-ops after first run |
| TerminalScreen dedup `attachView`/`requestFocus` | ✅ | API corrected in §3.5 |
| Chroot untouched | ✅ | Explicit exclusion list in §3.6 |
| All unit tests pass | ✅ | `FakeContext` stub mandated; existing `FakePrefsContext` reusable |
