# Worker agent prompt — Proot X11 display + VIEW LOGS (device)

**Repo:** `/home/abhaybyte/repos/fluxlinux`  
**Package:** `com.ivarna.fluxlinux`  
**Reference:** `~/repos/termux-lib` MainActivity `startGui` / `stopGui` / `displayBtn` / `guiLog*`  
**Plan:** `docs/plans/x11-proot-display-logs-nativecode-parity.md`  
**APK:** release only — `assembleIvarnaRelease` + `adb install -r` (uninstall only if signature mismatch)  
**Do not** `git push`. Stop Gradle when done.

---

## Mission

Device-test **proot X11 / Graphical Desktop** after the termux-lib parity implementation:

1. Confirm Debian proot is installed (rootfs + startxfce4). If missing, complete proot onboarding first or install via Distros.
2. From **Home** → installed Debian → **Start** → **Launch XFCE4**.
3. **Log sheet auto-opens** with live `start_gui.sh` output.
4. First healthy lines → card shows **STARTING/RUNNING**, **Open X11**, **View Logs**, **Stop**.
5. X11 activity (`com.termux.x11.MainActivity`) launches (embedded display server).
6. **Open X11** reopens display without restarting DE.
7. **View Logs** / Copy work; log contains X server / am start / startxfce4 lines.
8. **Stop** returns card to Start; Open X11 hides; FGS stops.
9. Fail path (optional): if start fails, logs still visible + not stuck RUNNING.
10. On failure: fix code → rebuild release → reinstall → retest until pass.

---

## Preconditions

```bash
cd /home/abhaybyte/repos/fluxlinux
adb devices
# Debian proot present?
adb shell "ls /data/data/com.ivarna.fluxlinux/files/usr/var/lib/proot-distro/containers/debian/rootfs/usr/bin/startxfce4"
```

If missing: run onboarding install Debian proot (long) OR Distros install wizard.

Build/install latest release if not already on device:

```bash
./gradlew :app:assembleIvarnaRelease --no-daemon
adb install -r app/build/outputs/apk/ivarna/release/app-ivarna-release.apk
# INSTALL_FAILED_UPDATE_INCOMPATIBLE → uninstall then install
```

---

## Device matrix

| # | Step | Pass |
|---|------|------|
| D1 | Launch app → Home shows Debian installed | |
| D2 | Start → Launch XFCE4 | toast Starting; log dialog opens |
| D3 | Live log lines appear (Pulse/X server/VirGL/startxfce4) | |
| D4 | X11 surface opens (black/desktop/XFCE) | activity in resume |
| D5 | Back to app → Open X11 reopens display | |
| D6 | View Logs still shows transcript; Copy OK | |
| D7 | Stop → STARTING/RUNNING gone; Start visible | |
| D8 | logcat no fatal AndroidRuntime for DesktopLauncher / termux.x11 | |
| D9 | Optional: second start after stop works | |

Automation hints:

```bash
adb logcat -c
adb logcat -s DesktopLauncher:* EmbeddedX11:* GUI:* AndroidRuntime:E
adb shell am start -n com.ivarna.fluxlinux/.MainActivity
adb shell dumpsys activity activities | rg -i 'termux.x11|fluxlinux|mResumed'
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml
# screenshots
adb exec-out screencap -p > /tmp/x11_test.png
```

UI automation: use `uiautomator` text match for "Launch XFCE4", "Open X11", "View Logs", "Stop". Coordinates from dump if needed.

---

## Code touchpoints (if fixing)

| Piece | Path |
|-------|------|
| Stream start/stop + StateFlow | `core/desktop/DesktopLauncher.kt` |
| Log ring file | `core/desktop/GuiDesktopLog.kt` |
| Card Open X11 / View Logs | `ui/components/GlassCard.kt` |
| Home live logs dialog | `ui/screens/HomeScreen.kt` |
| Host start script | `assets/scripts/debian/proot/start/start_gui.sh` |
| termux-lib SSOT | `MainActivity.kt` ~startGui / onGuiStreamHealthyLine |

Rules:
- X11 open on **first healthy line** (+ ~400ms), not blind long sleep only.
- Never hold a shared install terminal lock for XFCE lifetime.
- Special-use FGS for desktop keep-alive.

---

## Report

Write: `docs/plans/results/x11-proot-display-device-report.md`

- Overall PASS/FAIL  
- Matrix D1–D9  
- Screenshots / logcat excerpts on fail  
- Fixes applied (if any) + retest outcome  
- APK path + version  

Then: `./gradlew --stop`
