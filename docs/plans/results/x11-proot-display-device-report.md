# Device report — Proot X11 display + VIEW LOGS

**Date:** 2026-08-10 / 2026-08-11  
**Package:** `com.ivarna.fluxlinux`  
**APK:** `app/build/outputs/apk/ivarna/release/app-ivarna-release.apk`  
**Version:** `1.8.0` (`versionCode=10`)  
**Device:** `Y5WWBMJVOZSK4HU8` (adb)  
**Debian proot:** present (`…/containers/debian/rootfs/usr/bin/startxfce4`)  
**Overall:** **PASS**

---

## Matrix D1–D9

| # | Step | Result | Evidence |
|---|------|--------|----------|
| D1 | Launch app → Home shows Debian installed | **PASS** | UI: Installed Distros / Debian / Start |
| D2 | Start → Launch XFCE4 → toast Starting; log dialog opens | **PASS** | Dialog title `Graphical Desktop Log`, subtitle Starting…/Running; `DesktopLauncher: startGui method=proot` |
| D3 | Live log lines (Pulse/X server/VirGL/startxfce4) | **PASS** | `gui_desktop.log` + dialog: `X server PID=…`, `Launching X11 display activity…`, `startxfce4=READY`, VirGL→llvmpipe fallback |
| D4 | X11 surface opens (desktop/XFCE) | **PASS** | `com.termux.x11.MainActivity` resumed; screencap shows XFCE panel + penguin wallpaper (`x11-d4-xfce-desktop.png`, ~1.4 MB) |
| D5 | Back → Open X11 reopens display | **PASS** | Card + log-dialog Open X11 → X11 resumed; BACK returns to `.MainActivity` after Activity-context fix |
| D6 | View Logs transcript; Copy OK | **PASS** | View Logs shows full `start_gui` transcript incl. startxfce4; Copy sets clipboard (`setPrimaryClip` + “Log copied” toast path) |
| D7 | Stop → RUNNING gone; Start visible | **PASS** | Card: Start + View Logs only; `stop_gui.sh` → `GUI stopped successfully!`; xfce/termux-x11 processes gone |
| D8 | logcat no fatal for DesktopLauncher / termux.x11 | **PASS** | No app `FATAL EXCEPTION` for DesktopLauncher/termux.x11. (uiautomator `UiAutomationService already registered` is test harness only.) |
| D9 | Second start after stop works | **PASS** | Second `startGui` → new X server PID + `xfce4-session` + X11 activity; Stop returns idle again |

---

## Acceptance checklist (plan)

1. Proot Debian → Start XFCE streams host logs into UI — **yes**  
2. First script output → Open X11 + singleTop X11 launch — **yes** (~400 ms after first healthy line)  
3. Live log sheet / View Logs show start_gui lines — **yes**  
4. Stop tears down; Open X11 hides; log retained — **yes** (View Logs still available when idle)  
5. Fail-closed not fully retested with intentional failure; normal stop path not stuck RUNNING — **yes**  
6. Release APK on device; `./gradlew --stop` after work — **yes**

---

## Fixes applied (this worker)

File: [`app/src/main/kotlin/com/ivarna/fluxlinux/core/desktop/DesktopLauncher.kt`](../../app/src/main/kotlin/com/ivarna/fluxlinux/core/desktop/DesktopLauncher.kt)

### 1. First-healthy-line only for RUNNING / prefs / X11

**Bug:** Every non-blank `start_gui.sh` line called `StateManager.setGuiRunning`, `setGuiRunningType`, and `TermuxX11Preferences.applyToTermux`, thrashing SharedPreferences / logcat (dozens–hundreds of “GUI running status set to: true” per start).

**Fix:** Gate those side effects on `healthyLineSeen.compareAndSet(false, true)`; subsequent lines only append to `GuiDesktopLog` / live StateFlow.

**Retest:** `setGuiRunning` count per start ≈ 1 (was spam).

### 2. Open X11 prefers Activity context (BACK stack)

**Bug:** `reopenDisplay` always used `applicationContext` + `FLAG_ACTIVITY_NEW_TASK`, so BACK from X11 sometimes left the app to the launcher instead of Home.

**Fix:**  
- `reopenDisplay(ctx)` passes through `ctx` (Compose `LocalContext` = Activity).  
- `openX11`: Activity → `SINGLE_TOP | CLEAR_TOP` (nativecode parity); Application → also `NEW_TASK` (auto-open after first healthy line).

**Retest:** Card Open X11 → BACK → `.MainActivity` resumed in same task.

---

## Logcat / log file excerpts

```
I DesktopLauncher: startGui method=proot script=.../files/home/start_gui.sh
=== START method=proot script=start_gui.sh ===
FluxLinux: VirGL unavailable; using software rendering
FluxLinux: Starting termux-x11 server...
FluxLinux: X server PID=5328
FluxLinux: Launching X11 display activity...
FluxLinux: startxfce4=READY
FluxLinux(guest): VirGL socket missing — llvmpipe fallback
...
I DesktopLauncher: stopGui method=proot script=.../files/home/stop_gui.sh
=== STOP method=proot script=stop_gui.sh ===
✅ GUI stopped successfully!
[exit 0]
```

Processes while RUNNING (sample): `termux-x11`, `libproot.so`, `xfce4-session`, `xfce4-panel`, …

---

## Screenshots (under `docs/plans/results/`)

| File | What |
|------|------|
| `x11-d4-xfce-desktop.png` | XFCE desktop on embedded X11 |
| `x11-d5-reopen.png` | Reopened display after Open X11 |
| `x11-running-card.png` | Home card RUNNING + Open X11 / Stop / View Logs |
| `x11-d7-idle-after-stop.png` | Idle card after Stop (Start + View Logs) |

---

## Notes / residual risk (non-blocking)

- **Guest noise (expected under proot):** no D-Bus/ConsoleKit/colord/DPMS; light-locker hidepid; VirGL socket missing → llvmpipe. Does not block XFCE paint.  
- **First paint timing:** early screencaps can be black (~11 KB) for a few seconds before the first XFCE frame; later captures consistently show full desktop (~1.4 MB).  
- **Stale RUNNING prefs:** force-stop of the app process leaves `StateManager` GUI flags true until user taps Stop (or next successful stop path). Not part of D1–D9; optional hardening later (clear flags on process start if host DE not alive).  
- **Auto-open X11** still uses application context + `NEW_TASK` (no Activity available from stream callback); card/dialog reopen uses Activity path.

---

## Build / install commands used

```bash
./gradlew :app:assembleIvarnaRelease --no-daemon
adb install -r app/build/outputs/apk/ivarna/release/app-ivarna-release.apk
./gradlew --stop
```

**Final APK path:** `/home/abhaybyte/repos/fluxlinux/app/build/outputs/apk/ivarna/release/app-ivarna-release.apk`  
**Installed:** `lastUpdateTime` after final retest install (versionName `1.8.0` / versionCode `10`)

No `git push`.
