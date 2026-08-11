# Plan: Proot X11 display + VIEW LOGS (termux-lib / nativecode parity)

**Date:** 2026-08-10  
**Status:** implemented (code + release on device); device smoke via worker agent  
**Reference:** `~/repos/termux-lib` (`MainActivity.startGui` / `stopGui` / `guiLog*` / header `displayBtn`)  
**Scope:** Proot (and shared chroot path) Graphical Desktop start/stop, in-app X11 display button, live desktop logs. Release APK → device only.

---

## 1. Problem

Flux `DesktopLauncher` already runs host `start_gui.sh` and opens `com.termux.x11.MainActivity`, but diverges from nativecode:

| Gap | termux-lib | Flux (before) |
|-----|------------|---------------|
| Start model | `runStreamedCancelable` (long-lived XFCE; never holds script-install lock) | `ProcessBuilder` + **6s preflight** then open X11 |
| X11 open | On **first healthy stdout line** (+ short delay) | After preflight timeout only |
| Logs | `cacheDir/gui_desktop.log` 512KB ring; **VIEW LOGS** | Logcat only (`Log.d`) |
| Display button | Header `displayBtn` visible while desktop running | DistroCard **Open X11** only after `isGuiRunning` pref set |
| Fail path | Toast + auto-open log | Toast only |

User ask: implement display server **as in termux-lib**, **X11 display button**, **show logs when X11 starts**, test proot X11 on device, release APK update only.

---

## 2. Architecture (match termux-lib § settings-xfce-desktop-view-logs)

```
START XFCE (proot)
  prepareHost + deployScripts
  FGS DesktopSessionService
  ShellCommandRunner.runStreamedCancelable(bash, start_gui.sh, debian)
       onLine → GuiDesktopLog.append + UI StateFlow
              → first line: set running flags, show Open X11, open X11 once
       onDone → fail → clear flags, toast, surface logs; clean exit → idle
STOP
  ACTION_STOP package-local
  cancel stream job
  runStreamedCancelable(stop_gui.sh)
  clear flags + hide display
VIEW LOGS / auto sheet
  read gui_desktop.log (or live StateFlow buffer)
  Compose dialog: monospace + Copy
```

**Do not** route long-lived `start_gui*` through a terminal session that blocks other installers.

---

## 3. Implementation tasks

| ID | Work | Files |
|----|------|--------|
| I1 | `GuiDesktopLog` ring file + in-memory tail for Compose | `core/desktop/GuiDesktopLog.kt` |
| I2 | Rewrite `DesktopLauncher` stream start/stop + StateFlow UI state | `core/desktop/DesktopLauncher.kt` |
| I3 | DistroCard: **Open X11** + **View Logs** + Stop while starting/running | `ui/components/GlassCard.kt` |
| I4 | HomeScreen: collect launcher state; auto show log sheet on start; wire logs | `ui/screens/HomeScreen.kt` |
| I5 | Optional unit tests for log ring / phase transitions (pure) | `app/src/test/...` |
| I6 | Release `assembleIvarnaRelease` + `adb install -r` | device |
| I7 | Device test plan + worker agent | `docs/plans/…` |

Out of scope: KDE path, zenithblue flavor, marketplace, termux-x11 module branding rehaul.

---

## 4. Acceptance

1. Proot Debian installed → Start XFCE streams host logs into UI.  
2. First script output → **Open X11** available; X11 activity launches (singleTop).  
3. Live log sheet / View Logs shows `start_gui` lines (X server PID, am start, startxfce4).  
4. Stop tears down; Open X11 hides; log retained until next start.  
5. Fail-closed: missing script / early non-zero → not stuck on RUNNING; logs visible.  
6. Release APK on device; `./gradlew --stop` after work.

---

## 5. Device test plan (worker)

See `docs/plans/worker-prompt-x11-proot-display-device-test.md`.
