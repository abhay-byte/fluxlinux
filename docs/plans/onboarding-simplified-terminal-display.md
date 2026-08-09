# Plan: Simplified Onboarding + NativeCode-Parity Terminal & Display

**Date:** 2026-08-09  
**Updated:** 2026-08-09 (review-2 quality pass: cancel, exit codes, cohesion)  
**Status:** **IN PROGRESS — code complete for B1–B7 + review-2; device smoke pending**  
**Branch context:** `feat/embedded-terminal-bootstrap-proot-chroot` (embedded host already largely landed)  
**Reference app:** `~/repos/nativecode-ai` (a.k.a. nativecode / termux-lib lineage)  
**Scope:** Replace long Termux-era onboarding with an in-app full install wizard; strip base install to rootfs + XFCE + customization; align interactive terminal + GUI launch with nativecode-ai (no external Termux / Termux:X11).

**Review artifacts:**  
- Pass 1: `/tmp/grok-1000/grok-review-225303e6.md` (B1–B7)  
- Pass 2: `/tmp/grok-1000/grok-review-56619c21.md` (quality / coupling / cohesion)

---

## 0. Implementation status

| Workstream | Status | Notes |
|------------|--------|--------|
| PR1 Onboarding UI shell | **Landed** | Shared `InstallProgressPanel` / theme rows |
| PR2 Base install runner | **Landed (code)** | Generation cancel + process kill; single runner instance; fail-closed |
| PR3 Terminal ExtraKeys | **Landed (device unverified)** | WINCH only on size/pid change |
| PR4 Desktop launcher | **Landed (code)** | Still-alive preflight only; owns GUI flags; FGS failure toast |
| PR5 Cleanup / docs | **Partial** | Cohesion pass done; tutorials / KDE Termux residual open |

### Files (core)

| Path | Role |
|------|------|
| `ui/onboarding/OnboardingFlowScreen.kt` | Multi-step first-run UI |
| `ui/install/InstallProgressPanel.kt` | Shared progress + theme pick (DRY) |
| `core/install/BaseDesktopInstallPlan.kt` | Family + customization payload |
| `core/install/OnboardingInstallRunner.kt` | Phased install; generation cancel |
| `core/desktop/DesktopLauncher.kt` | Start/stop GUI; owns flags; fail-closed |
| `core/service/DesktopSessionService.kt` | Desktop FGS keep-alive |
| `assets/scripts/chroot/start_debian13_gui.sh`, `stop_debian13_gui.sh` | Root guest XFCE |
| `assets/scripts/.../start_gui*.sh`, `stop_gui*.sh` | Host desktop wrappers (propagate rc) |
| `HostScriptDeployer.kt` | Data-driven script table (required flags) |
| `TerminalScreen.kt` | ExtraKeys + resize/WINCH |
| `InstallConfigScreen.kt` | Theme + shared progress via runner |
| `MainActivity.kt`, `HomeScreen.kt`, `DistroScreen.kt`, `AndroidManifest.xml` | Wiring |

---

## 0.1 Code review findings

### Pass 1 blockers (B1–B7) — all **Fixed**

| # | Area | Status |
|---|------|--------|
| B1–B7 | Chroot GUI, shared runner, fail-closed install, no force-stop, Home Stop, DesktopLauncher, deployer required scripts | **Fixed** (prior pass) |

### Pass 2 (quality / coupling / cohesion) — `/tmp/grok-1000/grok-review-56619c21.md`

| # | Area | Status | What changed |
|---|------|--------|--------------|
| R1 | `start_gui.sh` always exit 0 | **Fixed** | Propagate guest/proot `GUEST_RC` |
| R2 | Install cancel weak | **Fixed** | Generation token; destroy active process; no StateManager after cancel |
| R3 | Parallel runners on Retry | **Fixed** | Single remembered runner; start() invalidates prior gen |
| R4 | Home Stop `canRunCommands` | **Fixed** | XFCE stop ungated; KDE still needs permission |
| R5 | stop_gui guest DE | **Fixed** | `proot-distro login … killall` then host pkill |
| R6 | Progress UI duplication | **Fixed** | `ui/install/InstallProgressPanel` shared |
| R7 | DesktopLauncher flags/cohesion | **Fixed** | Owns `setGuiRunning`; short-lived exit always fails; FGS toast |
| R8 | HostScriptDeployer paths | **Fixed** | `HostScript(name, assetPath, required)` table |
| R9 | SIGWINCH spam | **Fixed** | Only on width/height/pid change |
| R10 | Preflight exit 0 success | **Fixed** | Only “still alive after preflight” succeeds |
| R11 | FGS failure silent | **Partial** | Toast on FGS start failure; icon still generic |
| N13 | Done double complete | **Fixed** | Terminal/Desktop callbacks single-path |

### Remaining before COMPLETE

1. **Device smoke** (acceptance §11): proot + chroot install, Start/Stop XFCE, ExtraKeys/WINCH  
2. FGS monochrome notification icon (S11 remainder)  
3. Residual Termux intent for **KDE** only (`TermuxIntentFactory`)  
4. Root install cancel cannot kill `su` mid-phase (best-effort after phase)

---

## 1. Product decisions (confirmed)

| Decision | Choice |
|----------|--------|
| Where install lives | **Full install inside onboarding** (nativecode-style) |
| Base install contents | **Rootfs + XFCE + customization only** — no feature modules, no silent `hw_accel` |
| Installable distros | **Debian (proot)** + **Debian Rooted (chroot)** |
| Modules (app_dev, cybersec, KDE, …) | **Hidden from onboarding/install**; still available later in **Distro Settings** |
| Terminal + display | **Both**: interactive terminal UX parity + embedded Start/Stop XFCE via host scripts |

---

## 2. Current state (as-is)

### Already embedded (good foundation)

- Host userland: `bootstrap.tar` + W^X jniLibs per flavor (`com.ivarna.fluxlinux` / `com.zenithblue.fluxlinux`)
- Install/CLI via in-app `TerminalSession` / `TerminalView` (`FluxTerminalSessionManager`, command builders)
- Embedded X11 module (`:termux-x11`) + `EmbeddedX11`
- Distro catalog: Debian + Debian Rooted installable; ~18 coming-soon cards
- Plan doc: `docs/plans/embedded-terminal-bootstrap-proot-chroot.md` (Pass 2 code, device R still pending)

### Gaps this plan closes

| Gap | Detail |
|-----|--------|
| Onboarding | Welcome → long `PrerequisitesScreen` (10 steps, residual Termux overlay/RUN_COMMAND/BusyBox for everyone) — **not** a install flow |
| Install UX | `InstallConfigScreen` still has DE (KDE), GPU modes, full **feature module** checklist; `hw_accel` mandatory |
| GUI launch | Home/Settings still call `TermuxIntentFactory.buildLaunchGuiIntent` → external `com.termux` |
| Terminal interactivity | `TerminalView` exists but ExtraKeys/modifiers always `false`; no nativecode-style special-keys toolbar or solid resize/WINCH |
| Chroot install payload | Proot gets setup_b64 (XFCE); chroot path does not apply the same customization chain cleanly |
| Progress model | Legacy `InstallationQueueManager` vs real progress in terminal session |

### Reference: nativecode-ai onboarding

`OnboardingActivity` multi-page flow:

1. Privacy / intro / slideshow  
2. Requirements  
3. Isolation (proot vs chroot)  
4. Install plan  
5. **Progress + live log** (bootstrap → host → Debian → optional extras)  
6. Complete → Main  

FluxLinux should keep **Compose + glass UI**, but mirror the **flow and install phases**, not the View-based widgets.

---

## 3. Target UX

### 3.1 First-run flow

```
App launch
  └─ onboarding_completed == false
       ├─ Page A: Welcome / value props (reuse/adapt OnboardingScreen art)
       ├─ Page B: Distro catalog
       │     • Installable: Debian (PRoot), Debian Rooted (Chroot)
       │     • Coming soon: existing DistroRepository.comingSoon cards (disabled + badge)
       │     • Selecting chroot → short root gate (su detect + BusyBox tip only if needed)
       ├─ Page C: Install options (minimal)
       │     • Theme: dark / light (optional; default dark)
       │     • Confirm: "Install XFCE desktop + Flux customization"
       │     • No feature modules, no KDE, no GPU picker
       ├─ Page D: Setup progress
       │     • Phases + % + elapsed + expandable live log (nativecode pattern)
       │     • Runs: prepareHost → rootfs install → XFCE base → customization
       │     • Fail-closed; Retry / Open logs / Report
       └─ Page E: Complete
             • Mark onboarding complete
             • CTA: Open Terminal | Start Desktop | Go Home
```

**Post-onboarding navigation (unchanged shell):** Home | Distros | Terminal (+ Settings overlay).

### 3.2 Distros tab after onboarding

- Still lists installable + coming soon (same catalog).
- **Install path for a second distro** (e.g. install chroot after proot): thin wizard **without modules** — same base payload as onboarding (XFCE + customization only).
- **Do not** show feature checklist on install; components remain only under **Distro Settings**.

### 3.3 What “basic installation” means (scripts)

| Phase | Proot (`debian`) | Chroot (`debian13_chroot`) |
|-------|------------------|----------------------------|
| Host | `BootstrapInstaller` + `HostScriptDeployer` + `setup_termux.sh` | Same |
| Rootfs | `flux_install.sh debian` + local `debian_13_rootfs.tar.xz` | `setup_debian13_chroot.sh` as root |
| XFCE base | `setup_debian_family.sh` (as root inside guest) | Same guest script after chroot exists |
| Customization | `setup_customization_debian.sh` (theme/wallpaper/fonts/scale) | Same |
| **Not run** | `setup_hw_accel_*`, KDE, app_dev, cybersec, LLM packs, … | Same |

Optional later from Distro Settings: full component list (unchanged backend `openComponentSession` / `runComponentChain`).

### 3.4 Desktop & terminal after install

| Action | Target behavior (match nativecode) |
|--------|--------------------------------------|
| Start Desktop | Host FGS + `$HOME/start_gui.sh` / `start_gui_chroot.sh` via embedded host env → open `com.termux.x11.MainActivity` |
| Stop Desktop | `ACTION_STOP` + `stop_gui*.sh` + stop FGS |
| Reopen display | Intent to embedded X11 activity (no restart DE) |
| Terminal tab | Interactive multi-session `TerminalView` + special keys (Ctrl/Alt/Shift/Esc/Tab/arrows) + resize/WINCH |
| Shell openers | Debian shell / shell-root for proot; chroot shell cards when rooted method selected |

**Hard rule:** no primary path opens external `com.termux` or stock Termux:X11 package.

---

## 4. Architecture

### 4.1 Onboarding state machine

Introduce a small orchestrator (keep MainActivity thin):

```text
OnboardingCoordinator (new)
  steps: Welcome → DistroPick → Options → Running → Done
  selectedDistroId: debian | debian13_chroot
  theme: dark | light
  phase progress: sealed class (Host, Rootfs, Xfce, Customize, Success, Failed)

uses:
  TerminalLauncher.prepareHost
  InstallSessionFactory / FluxTerminalSessionManager.openInstallSession
  sequential guest setup scripts (family + customization) — not component marketplace IDs
  StateManager.setOnboardingComplete
```

Prefer driving install via **existing session factories** (so logs appear in a real PTY if desired) **or** a progress UI fed by a non-interactive runner like nativecode’s phase pipeline. Recommendation:

- **Progress UI primary** (nativecode Page D style) for first-run polish.
- Optionally mirror stdout into a small log panel (ProcessBuilder/`ShellCommandRunner` port) rather than forcing the Terminal tab during onboarding.
- On failure, “Open Terminal” can attach to a retry session.

### 4.2 Install payload builder (shared)

New helper e.g. `BaseDesktopInstallPlan`:

```kotlin
// Always:
// 1) install rootfs for distro method
// 2) run setup_debian_family.sh with FLUX_THEME / FLUX_DESKTOP_ENV=xfce
// 3) run setup_customization_debian.sh
// Never: DistroComponent checklist from InstallConfigScreen
```

Wire:

- Onboarding Running step  
- Distros → Install (stripped wizard)  
- Proot: keep `setup_b64` path or explicit post-install guest sessions  
- **Chroot: apply same guest scripts after rootfs extract** (fix today’s proot/chroot asymmetry)

### 4.3 Terminal parity (Compose, nativecode behavior)

Mirror behavior from nativecode `MainActivity` terminal, implement in Flux Compose:

| Feature | Implementation notes |
|---------|----------------------|
| Multi-tab sessions | Already: `SessionRegistry` / titles — polish UI |
| Special keys toolbar | Compose row: Ctrl/Alt/Shift latches + Esc/Tab/←↑↓→/Home/End; wire `TerminalViewClient.readControlKey()` etc. |
| Focus + IME | On tap show keyboard (partially present) |
| Resize | On size change: `TerminalView.updateSize()` + SIGWINCH to session pid (esp. chroot) |
| Font | Keep bundled font if present; match density |
| FGS | Keep `AppTerminalService`; ensure notification deep-link opens Terminal tab |
| Host gate | Always `prepareHost` / method routing before session |

Reference files:

- `~/repos/nativecode-ai/.../MainActivity.kt` (session create, toolbar, resize)  
- `~/repos/nativecode-ai/docs/plan-special-keys-toolbar.md`, `docs/plan/terminal-auto-resize.md`  
- Flux: `TerminalScreen.kt`, `SessionRegistry.kt`, `FluxTerminalSessionManager.kt`, builders

### 4.4 Display / GUI launch parity

Replace `TermuxIntentFactory.buildLaunchGuiIntent` call sites:

| Call site | New path |
|-----------|----------|
| `HomeScreen` Start GUI | `DesktopLauncher.start(ctx, distroId)` |
| `MainActivity` / Settings | same |
| Stop GUI | `DesktopLauncher.stop(ctx, distroId)` |

`DesktopLauncher` (new) mirrors nativecode `startGui`/`stopGui`:

1. Ensure host ready + scripts deployed  
2. Start desktop keep-alive FGS (`BackgroundService` port or extend existing service with desktop type)  
3. Run host script via env from `HostCommandBuilder`:
   - proot → `start_gui.sh debian`
   - chroot → `start_gui_chroot.sh`
4. Launch `com.termux.x11.MainActivity` (embedded module)  
5. Stop: broadcast `com.termux.x11.ACTION_STOP` + `stop_gui*.sh`

Validate env on every host process:

- `TERMUX_X11_OVERRIDE_PACKAGE`, `TERMUX_X11_APK_PATH`
- `PD_PROOT_BIN`, `PROOT_LOADER`, PREFIX/HOME SSL  
- loader.apk under PREFIX `libexec/termux-x11/`

Scripts already exist under Flux assets (`start_gui.sh`, chroot variants); align with nativecode versions if they diverged.

### 4.5 Prerequisites screen fate

| Old step | Fate |
|----------|------|
| Initialize host | Fold into onboarding Running phase |
| Termux config / RUN_COMMAND | **Delete** (or Settings advanced only) |
| Overlay for `com.termux` | Retarget to **this app** only if Android requires it for X11 overlay; else drop |
| Phantom process tips | Optional “Tips” on Complete page / Help, not blocking |
| BusyBox Magisk | **Only** when user selects Debian Rooted and root check fails |
| System check | Fold into phase validation |

`Screen.PREREQUISITES` can become unused or a thin legacy redirect → new onboarding.

---

## 5. Implementation workstreams (PR-sized)

### PR1 — Onboarding flow shell (UI + nav)

**Goal:** New multi-step Compose onboarding; no Termux prereq wizard as primary path.

- Replace `Screen.ONBOARDING` → multi-page state (or nested steps in one screen).
- Page B: distro list from `DistroRepository` (installable + coming soon).
- Page C: theme + confirm basic desktop install.
- Remove Get Started → Prerequisites as default.
- Settings “Show Onboarding” resets flag and reopens new flow.
- Keep glass/haze visual language.

**Key files:** `MainActivity.kt`, `OnboardingScreen.kt` (rewrite/split), new `ui/onboarding/*`, `StateManager.kt`.

### PR2 — Base install orchestrator (no modules)

**Goal:** Complete rootfs + XFCE + customization for proot **and** chroot.

- Add `BaseDesktopInstallPlan` / `OnboardingInstallRunner`.
- Phases + progress callbacks for UI.
- Proot: `prepareHost` → `flux_install` → family → customization.
- Chroot: root gate → `setup_debian13_chroot` → family → customization (guest via chroot helper).
- Fail-closed; exit-code gated phase advance (same discipline as Pass 2 install sessions).
- Strip `InstallConfigScreen` feature list + KDE/GPU for primary install; or replace with thin options matching onboarding.
- Stop calling `runComponentChain` for default install.

**Key files:** `InstallSessionFactory.kt`, `MainActivity.kt` install callbacks, `InstallConfigScreen.kt`, scripts usage only of family + customization.

### PR3 — Interactive terminal parity

**Goal:** Usable interactive terminal like nativecode.

- Compose ExtraKeys / special keys toolbar; wire modifier reads on `TerminalViewClient`.
- Session resize + SIGWINCH.
- Empty-state cards for proot/chroot shells (already partially there); ensure chroot method when distro is rooted.
- Pinch-zoom / text size optional polish if cheap (nativecode has plans).
- Verify FGS + attach/detach lifecycle.

**Key files:** `TerminalScreen.kt`, `SessionRegistry.kt`, possibly small `TerminalExtraKeys.kt`.

### PR4 — Embedded desktop launch (kill external Termux GUI)

**Goal:** Start/Stop XFCE entirely in-app.

- Implement `DesktopLauncher` using host scripts + X11 activity.
- Port/align `BackgroundService` keep-alive if missing.
- Rewire Home + Settings GUI buttons; deprecate `buildLaunchGuiIntent` for product paths.
- Confirm `EmbeddedX11` vs script-started server: prefer **nativecode script path** as SSOT (loader.apk + app_process + package override), activity only as surface.

**Key files:** `HomeScreen.kt`, `EmbeddedX11.kt`, `TermuxIntentFactory.kt` (quarantine), assets `start_gui*.sh` / `stop_gui*.sh`, `HostCommandBuilder.kt`.

### PR5 — Cleanup, catalog UX, docs

- Distros tab: install CTA opens thin wizard (no modules).
- Distro Settings: keep component install UI (modules later).
- Grep-gate residual `com.termux` product paths for install/run/GUI.
- Update tutorials (`docs/tutorial/setup_fluxlinux.md`, debian proot/chroot) — no paste-into-Termux.
- Update `docs/plans/embedded-terminal-bootstrap-proot-chroot.md` status for GUI pass.
- Device smoke: R-set from embedded plan + onboarding path + Start Desktop + interactive shell.

---

## 6. File map (primary)

### FluxLinux (edit)

| Area | Paths |
|------|--------|
| Nav / orchestration | `app/.../MainActivity.kt`, `core/utils/StateManager.kt` |
| Onboarding UI | `ui/screens/OnboardingScreen.kt`, **new** `ui/onboarding/*`, retire primary use of `PrerequisitesScreen.kt` |
| Install UI | `InstallConfigScreen.kt`, `DistroScreen.kt`, `HomeScreen.kt` |
| Install core | `InstallSessionFactory.kt`, `TerminalLauncher.kt`, `GuestSessionFactory.kt`, **new** plan/runner |
| Terminal | `TerminalScreen.kt`, `SessionRegistry.kt`, `FluxTerminalSessionManager.kt`, builders |
| Display | `EmbeddedX11.kt`, **new** `DesktopLauncher.kt`, GUI scripts under `assets/scripts/` |
| Catalog | `DistroRepository.kt` (no need to delete components; install UX ignores them) |

### Nativecode-ai (read / port patterns — already at `~/repos/nativecode-ai`)

| Pattern | Source |
|---------|--------|
| Onboarding phases + progress | `OnboardingActivity.kt` |
| Terminal sessions + keys + resize | `MainActivity.kt` |
| startGui/stopGui | `MainActivity.kt` + `assets/scripts/start_gui.sh` |
| Host env SSOT | `terminal/TermuxHostPaths.kt`, `HostCommandBuilder.kt` |
| Design notes | `docs/project/onboarding_design.md`, terminal/X11 plan docs |

---

## 7. Explicit non-goals (this plan)

- AI CLI marketplace / project workspace multi-tab (nativecode-only product surface)
- Shipping new distros beyond Debian proot/chroot
- Silent or mandatory `hw_accel` in onboarding (user chose rootfs+XFCE+customization only)
- Removing Distro Settings modules forever (only hide from first-run/install wizard)
- Re-introducing external Termux as a dependency

---

## 8. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Chroot onboarding blocked without BusyBox | Gate clearly; only show BusyBox step when chroot selected + root missing tooling |
| Long install ANR / process death | FGS during install; persist phase marker; resume or clean retry |
| Customization needs network | Document; optional offline skip with warning; retry |
| GUI script vs `EmbeddedX11` double-start | Single launcher path; scripts own X server lifecycle |
| SDK 36 W^X | Keep exec only via `lib*.so` in `nativeLibraryDir` (already) |
| Dual flavors | All host paths via `TermuxHostPaths` / `BuildConfig.APPLICATION_ID` |

---

## 9. Acceptance criteria

1. Fresh install completes **without** Termux or Termux:X11 APKs installed.
2. Onboarding shows **installable distros + coming soon**; picking Debian runs base install only (rootfs + XFCE + customization).
3. No feature-module checklist in onboarding or primary install wizard.
4. Debian Rooted path works when device is rooted (+ BusyBox when required).
5. Terminal tab: interactive shell, special keys functional, multi-session, resize works.
6. Start Desktop opens embedded X11 with XFCE; Stop tears down cleanly.
7. Distro Settings still lists feature modules for later install.
8. Settings can re-run onboarding for testing.

---

## 10. Suggested implementation order

```text
PR1 Onboarding UI shell                    ✅ landed (+ shared progress panel)
  → PR2 Base install runner                ✅ code (cancel + fail-closed)
    → PR3 Terminal interactivity           ✅ code; device TBD
    → PR4 Desktop launcher                 ✅ code (still-alive preflight + owns flags)
      → PR5 Cleanup + docs + device matrix ⚠️ device smoke + KDE residual
```

### Review fix passes (done 2026-08-09)

```text
Pass 1: B1–B7, S8, N14
Pass 2: R1–R10, N13 (exit codes, cancel, cohesion, WINCH, deployer table)
```

### Remaining before COMPLETE

```text
1. Device smoke (acceptance §11)
2. FGS notification icon polish
3. Residual TermuxIntentFactory (KDE only) / tutorial docs
```

---

## 11. Testing checklist (device)

- [ ] Cold start → complete proot onboarding → Home shows Debian installed  
- [ ] Terminal: open proot shell, type, Ctrl-C via special key, rotate device  
- [ ] Start Desktop → XFCE visible; reopen display; Stop Desktop  
- [ ] Coming-soon cards not installable  
- [ ] Chroot onboarding on rooted device  
- [ ] Distro Settings still installs one optional module (e.g. office) post-onboarding  
- [ ] Both flavors (`ivarna`, `zenithblue`) smoke  
- [ ] Uninstall + reinstall from Distros thin wizard  

---

## 12. Open follow-ups (not blocking plan)

- Whether overlay permission is required for in-process X11 on all OEMs  
- Offline packaging of customization assets (today may hit GitHub)  
- Whether to delete `InstallationQueueManager` entirely once progress UI is onboarding-native  
- Full device R1–R11 from existing embedded plan doc  

---

## Summary

FluxLinux already has the embedded host/terminal/X11 **platform**. This plan **repositions first-run UX** to a nativecode-style **full onboarding install** (distro pick + coming soon, then rootfs + XFCE + customization only), **defers feature modules** to Distro Settings, and **finishes product parity** for interactive terminal (extra keys/resize) and desktop launch (host `start_gui*.sh` + embedded X11, zero external Termux).
