# Worker agent prompt — Onboarding + Debian proot shell + ExtraKeys (device + unit tests)

**Package:** `com.ivarna.fluxlinux`  
**Repo:** `/home/abhaybyte/repos/fluxlinux`  
**Branch:** current working branch (do not force-push; local commits OK)  
**Reference SSOT (nativecode-ai copy):** `/home/abhaybyte/repos/termux-lib`  
**Signing:** `keystore.properties` → `~/repos/keys/fluxlinux.jks`  
**Device:** USB adb (`adb devices` must show a device)

---

## Mission (do all of this end-to-end)

1. **Complete full onboarding** on device for **Debian proot** (rootfs + XFCE + customization only).
2. **Open Debian proot shell** from Terminal page and verify interactive PTY works.
3. **Verify every ExtraKeys toolbar control** works in the Debian proot shell (parity with termux-lib / nativecode inject path).
4. **Write complete unit tests** covering pass **and** fail paths for onboarding runner gates, shell catalog availability, ExtraKeys inject / modifier state, proot command builder.
5. **Fix any bugs** found in code or on device so acceptance passes. Prefer parity with `~/repos/termux-lib` when behavior is ambiguous.
6. **Rebuild signed release APK, install on device, re-smoke critical paths.** Stop Gradle daemons when done.

Do **not** expand scope to AI CLI marketplace, image attach, KDE, or Play flavor (`zenithblue`) unless required to fix a blocking proot path.

---

## Reference implementations (when in doubt)

| Area | Flux (this repo) | termux-lib / nativecode |
|------|------------------|-------------------------|
| ExtraKeys UI + inject | `ui/terminal/TerminalExtraKeys.kt` (`TerminalModifierState`, `TerminalKeyInjector`, `TerminalKeyCodes`) | `app/src/main/java/com/zenithblue/nativecode/MainActivity.kt` — `ModifierState` ~L512, `SPECIAL_KEY_CODES` ~L3291, `injectKey` ~L3320, `buildSpecialKeysToolbar` ~L3484+ |
| Terminal screen | `ui/screens/TerminalScreen.kt` | `MainActivity` terminal view client + toolbar |
| Shell card grid | `ui/terminal/TerminalToolSelector.kt`, `core/terminal/TerminalShellCatalog.kt` | tool selector / `ToolLauncherCatalog` pattern |
| Guest session open | `GuestSessionFactory.kt`, `ProotCommandBuilder.kt`, `SessionRegistry.kt`, `FluxTerminalSessionManager.kt` | proot session open paths in MainActivity |
| Onboarding install | `ui/onboarding/OnboardingFlowScreen.kt`, `core/install/OnboardingInstallRunner.kt`, `BaseDesktopInstallPlan.kt` | onboarding install chain docs under `termux-lib/docs/plan/` |
| Plans (Flux) | `docs/plans/onboarding-simplified-terminal-display.md` §9–§11  
| | `docs/plans/terminal-grid-extrakeys-interactive.md` §4 + T6 |

**Injection rule (must stay true):**
- Special keys → `TerminalView.onKeyDown` / `onKeyUp` with meta flags  
- Printable symbols → `TerminalView.inputCodePoint(cp, ctrl, alt)`  
- **Never** go back to `session.write("\u001b…")` for toolbar keys  

**Modifier rule:**
- Tap = toggle one-shot; long-press = lock; `consumeModifiers()` clears unlocked after inject; locked survive until next toggle  

---

## Phase A — Code audit (read first, fix if broken)

Read and understand before changing:

1. Onboarding: `OnboardingFlowScreen` steps Welcome → DistroPick → Options → Running → Done  
2. Runner: `OnboardingInstallRunner` proot path (prepareHost → flux_install / proot-distro → customization)  
3. Terminal: empty grid → open `shell` (proot, user flux) → `TerminalView` + ExtraKeys  
4. Compare ExtraKeys row layout, key map, inject path, and IME/WINCH handling with termux-lib  

**Fail-closed expectations:**
- Proot cards disabled when Debian not installed (reason: install message)  
- Chroot cards require rootfs **and** root (all chroot cards)  
- Host shell gated on host ready / libbash  
- Session open toasts differentiate prepare fail vs open fail vs tab limit  

If Flux diverges from termux-lib in a way that breaks keys or shell, fix Flux toward termux-lib parity (minimal diff).

---

## Phase B — Unit tests (complete pass + fail matrix)

Add/extend JVM unit tests under `app/src/test/…`. Run:

```bash
./gradlew :app:testIvarnaDebugUnitTest
```

### Required coverage

#### B1 — `TerminalModifierState` (new or existing test class)
| Case | Expected |
|------|----------|
| Default | ctrl/alt/shift inactive, unlocked |
| toggleCtrl | active true → false |
| lockCtrl | active+locked; consumeModifiers keeps ctrl |
| consumeModifiers | clears unlocked only |
| readCtrl(true) | returns true once and clears if not locked |
| readCtrl locked | returns true, stays active |
| Same matrix for Alt and Shift |
| readFn | always false |

#### B2 — `TerminalKeyInjector` / `TerminalKeyCodes`
| Case | Expected |
|------|----------|
| Every SPECIAL_KEY_CODES entry present (ESC TAB ENTER BKSP DEL arrows HOME END PGUP PGDN INS F1–F12) | map non-null |
| injectKey(null, …) | no throw |
| injectKey special | calls onKeyDown+onKeyUp (mock TerminalView if feasible; otherwise pure map assertions + documented instrumented smoke) |
| injectKey printable `/` `\|` `~` `-` `_` | inputCodePoint path |

Use mocks/fakes where Android `TerminalView` is hard on JVM; if inject needs instrumentation, put pure logic tests on JVM and document device checks for View path.

#### B3 — `TerminalShellCatalog`
| Case | Expected |
|------|----------|
| proot not installed | shell + shell-root disabled, reason set |
| proot installed | both proot cards enabled |
| chroot installed, no root | all chroot cards disabled, "Root required" |
| chroot missing | "Chroot not installed" |
| host card always present and enabled in catalog (host-ready is open path concern) |
| sections order | PROOT → CHROOT → HOST |

#### B4 — Proot command / session builders
Extend `ProotCommandBuilderTest` (and related) for:
- pass: login as flux / root builds expected proot-distro args  
- fail: missing paths / invalid method does not produce silent wrong command  

#### B5 — Onboarding / install plan pure logic
Where extractable without Android:
- `BaseDesktopInstallPlan` family/theme payloads for proot  
- fail cases: unknown distro id / wrong method  

Keep tests deterministic; no real rootfs extraction in unit tests.

**Definition of done for Phase B:**  
`./gradlew :app:testIvarnaDebugUnitTest` — **all green**. Any intentional skip must be justified in the report.

---

## Phase C — Build + install release APK

```bash
./gradlew :app:assembleIvarnaRelease --no-daemon
adb install -r app/build/outputs/apk/ivarna/release/app-ivarna-release.apk
# if UPDATE_INCOMPATIBLE: adb uninstall com.ivarna.fluxlinux && adb install …
```

Sign with existing `keystore.properties` (do not commit secrets).

---

## Phase D — Device: full onboarding (Debian proot)

### Reset first-run if needed

```bash
# Clear app data to force onboarding (destructive to guest data — OK for this mission)
adb shell pm clear com.ivarna.fluxlinux
# Or only flip prefs if guest rootfs must be preserved:
# adb shell run-as com.ivarna.fluxlinux …  (if debuggable) / shared_prefs edit
```

Prefer **clean slate** (`pm clear`) unless an existing proot rootfs is required to save time **and** onboarding skip is already marked — then re-run install from Distros thin wizard instead. Prefer full onboarding path when possible.

### UI flow (uiautomator / adb input / accessibility dump)

1. Launch app: `adb shell am start -n com.ivarna.fluxlinux/.MainActivity`
2. **Welcome** → Get Started  
3. **DistroPick** → select **Debian** (proot, **not** Debian Rooted)  
4. **Options** → theme dark (or light), start install  
5. **Running** → wait for finish (can take **15–45+ min**). Stream logcat:

```bash
adb logcat -c
adb logcat -s FluxLinux:* OnboardingInstallRunner:* ShellCommandRunner:* AndroidRuntime:E
```

6. **Done** → either Open Terminal or Home  
7. Confirm Home / Distros show Debian installed  

### Pass criteria
- [ ] Install finishes without crash; `failed=false`  
- [ ] No external Termux / Termux:X11 required  
- [ ] Live progress + log updates during install  

### Fail criteria (must diagnose and fix)
- Host bootstrap fail  
- proot-distro / flux_install hang or non-zero without UI error  
- Customization crash leaving half-installed state without Retry  
- App kills FGS / process mid-install without recovery  

On fail: capture logcat + screenshot, fix code, rebuild, retest from clean or Retry.

---

## Phase E — Device: Debian proot shell interactivity

1. Open **Terminal** tab  
2. Empty state: 2-column grid with **DEBIAN SHELL / PROOT**  
3. Tap **Debian Shell** (`shell`, user flux)  
4. Expect interactive prompt (zsh/bash), not a frozen view  

### Shell smoke commands (type via soft keyboard or `adb shell input text` where possible)

```text
whoami          # expect flux (or root for shell-root)
uname -a
pwd
echo hello-flux
ls /
```

Also open **Debian Shell Rooted** once and check `whoami` → `root`.

### Pass
- [ ] Prompt appears; typed chars echo  
- [ ] Commands execute; output scrolls  
- [ ] Soft keyboard opens on tap; ExtraKeys sit above IME (not covered)  
- [ ] Multi-session: + tab works; close tab returns to grid when last closed  

### Fail → fix
- Session open toast / crash  
- Host prepare loop  
- No PTY / dead session  
- Wrong user / wrong method (chroot vs proot)  

---

## Phase F — Device: ExtraKeys full matrix (Debian proot shell)

With an active proot shell and a working line editor (readline/zsh):

| Key / control | How to verify | Pass |
|---------------|---------------|------|
| CTRL + C | type `sleep 30`, press CTRL then C (or locked CTRL + C) | interrupt, prompt returns |
| CTRL + L or clear | if shell supports | screen clears or expected control |
| ALT | ALT+b / ALT+f or visible meta behavior if bound | does not hard-crash; meta applied |
| SHFT | with letter keys if relevant | no crash |
| ESC | press ESC | no crash; mode exit if applicable |
| TAB | type partial path + TAB | completion attempt |
| ENT | submit command | newline / execute |
| BKSP | type junk + BKSP | deletes char |
| ← ↑ ↓ → | history / cursor move in line | cursor/history moves |
| DEL / Ins | delete forward / toggle insert if applicable | no crash; sensible PTY |
| `/ \ | ~ - _` | printable inject | char appears |
| Fn → F1–F12 | toggle Fn row, press F keys | no crash; escape sequences reach PTY |
| Long-press CTRL/ALT/SHFT | lock indicator, then keys | lock stays until toggled off |
| IME + ExtraKeys | open soft keyboard | ExtraKeys remain tappable above IME |

Compare any broken key against termux-lib `injectKey` / toolbar and fix Flux.

Document each row as **PASS** or **FAIL** (+ log/screenshot on fail) in the final report.

---

## Phase G — Optional / if time (not blocking unless broken)

- Start Desktop / Stop Desktop from Done or Home (XFCE)  
- Rotate device: cols/rows update + single SIGWINCH (no spam)  
- Chroot cards disabled without root  

---

## Phase H — Report + cleanup

Write report to:

`docs/plans/results/onboarding-proot-terminal-extrakeys-report.md`

Include:
1. Summary (PASS/FAIL overall)  
2. Unit test command + counts  
3. Device matrix (onboarding, shell, each ExtraKeys row)  
4. Code changes (files + why)  
5. Remaining known issues  
6. APK path + versionCode/versionName  

Then:

```bash
./gradlew --stop
```

---

## Constraints

- Minimal, focused diffs; no drive-by refactors  
- Do not commit secrets / keystore passwords  
- Do not `git push` unless explicitly asked later  
- Prefer `ivarna` flavor only  
- Onboarding install is long — **do not** kill it early; monitor logcat  
- If device loses adb, reconnect and resume  
- If you must choose: **proot shell + ExtraKeys correctness** > desktop GUI polish  

---

## Success definition (all must be true)

1. Unit tests: **all pass** (`testIvarnaDebugUnitTest`)  
2. On-device: **Debian proot onboarding completes** (or already-installed equivalent path fully verified)  
3. On-device: **Debian Shell (proot)** interactive  
4. On-device: **ExtraKeys matrix** — all critical keys PASS (CTRL/C, arrows, ENT, BKSP, TAB, ESC, symbols, Fn row, modifier lock)  
5. Report written; Gradle stopped  

---

## Suggested first commands for the agent

```bash
cd /home/abhaybyte/repos/fluxlinux
adb devices
git status -sb
# audit key files vs termux-lib
# then implement unit tests → assembleIvarnaRelease → install → device smoke → fix loop
```
