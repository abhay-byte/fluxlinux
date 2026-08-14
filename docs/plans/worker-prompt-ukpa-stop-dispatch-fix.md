# Worker agent prompt — UKPA chroot Stop / uninstall dispatch fix

**Copy everything below the fence into a new worker.**  
Parent contract: [`docs/plan/ubuntu-kali-parrot-arch.md`](../plan/ubuntu-kali-parrot-arch.md) (DEVICE PASS 2026-08-14 — do not flip off unless a path actually fails after this fix).  
Review that found this: `/tmp/grok-1000/grok-review-fd39cc6f.md` (also summarized below).

```
You are the FluxLinux UKPA follow-up worker.

Repo:     /home/abhaybyte/repos/fluxlinux
Package:  com.ivarna.fluxlinux
Flavor:   :app:assembleIvarnaRelease --no-daemon
APK:      app/build/outputs/apk/ivarna/release/app-ivarna-release.apk
Install:  adb -s Y5WWBMJVOZSK4HU8 install -r <apk>
Device:   Xiaomi 2311DRK48I (duchamp), serial Y5WWBMJVOZSK4HU8, KernelSU
Do not git push. Stop Gradle when done. No debug APK. No APK uninstall unless
signature mismatch. Do not re-install any guest unless a path is actually
broken after the new APK.

==============================================================================
WHY YOU ARE HERE
==============================================================================

UKPA iteration 1 is implemented and device-tested. Review found a real Stop
bug that made D5 look green:

  DesktopLauncher.stop()
    → bash $HOME/stop_gui_chroot.sh <cardId>
    → no CHROOT_PATH env
    → case $1 in …

  start_gui_chroot.sh HAS:
    ubuntu|ubuntu_chroot     → /data/local/tmp/chrootUbuntu  + start_guest_gui.sh
    kali|kali_chroot         → /data/local/tmp/chrootKali    + start_guest_gui.sh
    parrot|parrot_chroot     → /data/local/tmp/chrootParrot  + start_guest_gui.sh
    archlinux|archlinux_chroot → /data/local/tmp/chrootArch  + start_guest_gui.sh
    *                        → /data/local/tmp/chrootDebian13 + start_debian13_gui.sh

  stop_gui_chroot.sh DOES NOT have those four arms.
  ubuntu_chroot / kali_chroot / parrot_chroot / archlinux_chroot hit *:
    CHROOT_PATH=/data/local/tmp/chrootDebian13
    ROOT_STOP_NAME=stop_debian13_gui.sh

  That script chroots Debian and unmounts Debian binds if
  /data/local/tmp/chrootDebian13 exists (it does — existing 8 guests stay
  on this device). UKPA XFCE teardown is skipped. Home still flips to Start
  because StateManager.setGuiRunning is cleared BEFORE the script runs, and
  host `pkill termux-x11` can hide the miss.

Same-class miss (fix in this slice, do not leave it):
  uninstall_guest_chroot.sh allowlist (lines 22–30) has FVO+DCM paths only.
  UninstallSessionFactory runs it with FLUX_CHROOT from DistroInstallProfile.
  In-app uninstall of ubuntu/kali/parrot/archlinux chroot currently:
    "Refusing to remove unexpected path: /data/local/tmp/chrootUbuntu"

==============================================================================
OUT OF SCOPE (hard)
==============================================================================

- Do not re-implement UKPA. Cards, profiles, family scripts, rootfs stay.
- Do not flip plan off DEVICE PASS unless a retest path actually fails.
- Do not change Debian / Alpine / FVO / DCM family scripts except if you
  must touch a shared helper (you should not need to).
- Do not chase Pulse, first-X SIGSYS, host tar xz, VirGL→llvmpipe.
- Do not add pentest metas, OpenBSD, KDE, debug APKs.
- Do not pm clear / uninstall the APK / wipe existing guests.

==============================================================================
ATTACHED / READ THESE FILES FIRST (in this order)
==============================================================================

Review + contract
  /tmp/grok-1000/grok-review-fd39cc6f.md
  docs/plan/ubuntu-kali-parrot-arch.md          §5 Host, §9.3 D5, §11
  docs/plans/worker-prompt-ubuntu-kali-parrot-arch-iter1.md   (context only)

The bug (read whole files)
  app/src/main/assets/scripts/chroot/stop_gui_chroot.sh          ← FIX
  app/src/main/assets/scripts/chroot/start_gui_chroot.sh         ← COPY CASE SHAPE
  app/src/main/assets/scripts/chroot/stop_guest_gui.sh           ← already generic
  app/src/main/assets/scripts/chroot/stop_debian13_gui.sh        ← what * wrongly runs
  app/src/main/assets/scripts/chroot/uninstall_guest_chroot.sh   ← FIX allowlist
  app/src/main/kotlin/com/ivarna/fluxlinux/core/desktop/DesktopLauncher.kt
      start() ~L118, stop() ~L227–262  (stopArg = distroId for chroot; no env)
  app/src/main/kotlin/com/ivarna/fluxlinux/core/root/ChrootPaths.kt
  app/src/main/kotlin/com/ivarna/fluxlinux/core/install/DistroInstallProfile.kt
      UKPA chrootStopGuiScript is already "stop_guest_gui.sh"
  app/src/main/kotlin/com/ivarna/fluxlinux/core/terminal/UninstallSessionFactory.kt
  app/src/main/kotlin/com/ivarna/fluxlinux/core/terminal/HostScriptDeployer.kt

Tests to extend (same repoFile helper)
  app/src/test/java/com/ivarna/fluxlinux/core/install/OmzPokemonContractTest.kt
      startScripts_doNotRelabelHostTmpAsTmpfs already reads start_gui_chroot.sh
      and does NOT read stop_gui_chroot.sh — add a new test method here OR a
      new UkpaHostDispatchContractTest.kt next to it.

Device evidence of the false-green D5
  docs/plans/results/ubuntu-chroot-device-report.md
  docs/plans/results/kali-chroot-device-report.md
  docs/plans/results/parrot-chroot-device-report.md
  docs/plans/results/archlinux-chroot-device-report.md

==============================================================================
PHASE A — Code fix (do this first, before any device work)
==============================================================================

A1. stop_gui_chroot.sh

Mirror start_gui_chroot.sh. Insert BEFORE the `*)` arm (keep `*` = Debian 13):

  ubuntu|ubuntu_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/tmp/chrootUbuntu}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_guest_gui.sh}"
    ;;
  kali|kali_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/tmp/chrootKali}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_guest_gui.sh}"
    ;;
  parrot|parrot_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/tmp/chrootParrot}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_guest_gui.sh}"
    ;;
  archlinux|archlinux_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/tmp/chrootArch}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_guest_gui.sh}"
    ;;

Also update the header comment (line 3) so the Arg1 list includes the four
UKPA chroot ids, same as start_gui_chroot.sh line 4.

Do NOT change:
  alpine → stop_alpine_gui.sh
  *      → chrootDebian13 + stop_debian13_gui.sh
  the su -c env export of DEBIANPATH/CHROOT_ROOT/FLUX_CHROOT
  the host pkill of termux-x11 / pulse / virgl

A2. uninstall_guest_chroot.sh

Add the four UKPA paths to the case allowlist (keep the refuse-unknown
fail-closed). After Manjaro, include:

  /data/local/tmp/chrootUbuntu
  /data/local/tmp/chrootKali
  /data/local/tmp/chrootParrot
  /data/local/tmp/chrootArch

Do not loosen the allowlist to `*`. Do not uninstall anything yourself
unless /data is exhausted during retest (it should not be — guests already
exist).

A3. Do not touch family scripts, DistroInstallProfile, DistroRepository,
    or start_gui_chroot.sh unless you find another dispatch twin that is
    missing the same four ids. If you find one, fix it the same way and
    document it in the report. Candidates to grep (read-only scan):

      rg -n 'chrootManjaro|chrootDebian13' app/src/main/assets/scripts/chroot/

    Only edit a file if it dispatches by name and omits UKPA while listing
    DCM. Do not "fix" Debian-only scripts.

==============================================================================
PHASE B — Unit tests (must be green before assemble)
==============================================================================

Add contract tests that would have caught Issue 1. Prefer a dedicated
class so OmzPokemonContractTest stays about OMZ/pokemon:

  app/src/test/java/com/ivarna/fluxlinux/core/install/UkpaHostDispatchContractTest.kt

Reuse the repoFile() candidate pattern from OmzPokemonContractTest
(cwd / app/ prefixes).

Required assertions (all of these, not a subset):

B1. start_gui_chroot.sh AND stop_gui_chroot.sh each contain ALL of:
      ubuntu|ubuntu_chroot
      kali|kali_chroot
      parrot|parrot_chroot
      archlinux|archlinux_chroot
    and chrootUbuntu / chrootKali / chrootParrot / chrootArch
    and start uses start_guest_gui.sh / stop uses stop_guest_gui.sh
    for those arms.

B2. Both scripts: the `*)` default still names chrootDebian13
    (start → start_debian13_gui.sh, stop → stop_debian13_gui.sh).
    Alpine still uses start_alpine_gui.sh / stop_alpine_gui.sh.

B3. uninstall_guest_chroot.sh allowlist contains the four UKPA paths
    AND still contains chrootDebian13 and chrootManjaro
    AND still has the refuse-unexpected-path error string.

B4. Family landmines (read-only greps — scripts should already pass):
    setup_ubuntu_family.sh
      contains ports.ubuntu.com/ubuntu-ports
      does not contain deb.debian.org
    setup_kali_family.sh
      contains usermod -L kali  (or equivalent lock)
      does not contain kali-desktop-xfce
    setup_parrot_family.sh
      contains deb.parrot.sh
      does not contain parrot-tools or parrot-interface as install targets
    setup_arch_family.sh
      contains archlinuxarm  AND DisableSandbox
      does not contain arm-stable

Run:

  ./gradlew :app:testIvarnaDebugUnitTest --no-daemon

ALL existing tests stay green (24 installable / 12 rootfs must not shrink).
If a new test fails because a family script uses a slightly different lock
string, fix the assertion to the real lock (e.g. `usermod -L kali`) — do
not weaken to assertTrue(true).

==============================================================================
PHASE C — Release APK
==============================================================================

  ./gradlew :app:assembleIvarnaRelease --no-daemon
  adb -s Y5WWBMJVOZSK4HU8 install -r \
    app/build/outputs/apk/ivarna/release/app-ivarna-release.apk
  adb -s Y5WWBMJVOZSK4HU8 shell am start -n \
    com.ivarna.fluxlinux/.MainActivity

Confirm HostScriptDeployer will overwrite $HOME/stop_gui_chroot.sh from
assets on next Start/Stop (deployScripts is called from DesktopLauncher.stop).
If an old copy is sticky, force by starting then stopping once.

==============================================================================
PHASE D — Device tests (no vision-only pass)
==============================================================================

Guests already installed. Do NOT reinstall. Serial Y5WWBMJVOZSK4HU8.

Preconditions (must all exist):

  adb -s Y5WWBMJVOZSK4HU8 shell su -c '
    for p in \
      /data/local/tmp/chrootDebian13 \
      /data/local/tmp/chrootUbuntu \
      /data/local/tmp/chrootKali \
      /data/local/tmp/chrootParrot \
      /data/local/tmp/chrootArch
    do
      echo -n "$p "
      test -d "$p" && echo OK || echo MISSING
    done
    echo -n "debian .flux or startxfce4: "
    test -e /data/local/tmp/chrootDebian13/usr/bin/startxfce4 && echo yes || echo no
  '

If a UKPA chroot is missing, install THAT card only from Distros. If Debian
chroot is missing, skip D-SIB Debian-bind check but still run UKPA D5.

D-PRE — prove the OLD bug is gone on-device (after new APK, before Start):

  adb -s Y5WWBMJVOZSK4HU8 shell '
    P=/data/data/com.ivarna.fluxlinux/files/home/stop_gui_chroot.sh
    # after first Stop, this file is redeployed; if missing, Start+Stop once
    grep -n "ubuntu|ubuntu_chroot\|chrootUbuntu\|chrootDebian13" "$P" || true
  '

For EACH of {ubuntu_chroot, kali_chroot, parrot_chroot, archlinux_chroot}:

D1  Home → that card → Start → Launch XFCE4
    Wait until X11 resumes and desktop paints (retry once if first-X SIGSYS).
    Confirm processes in THAT chroot (not Debian):

      adb shell su -c 'ps -A | rg -i "xfce4-session|xfwm4|xfce4-panel|xfdesktop"'
      adb shell su -c 'ls /proc/*/root 2>/dev/null | head'
      # or: readlink /proc/<xfce-pid>/root  must be /data/local/tmp/chrootXxx
      # MUST NOT be /data/local/tmp/chrootDebian13

    Screenshot: docs/plans/results/<id>_xfce_retest.png
    <id> = ubuntu_chroot | kali_chroot | parrot_chroot | archlinux_chroot

D5  Tap Stop on that card.
    Pass only if ALL of these hold:

    1. Card returns to Start (not stuck RUNNING).
    2. Screenshot: docs/plans/results/<id>_stopped_retest.png
    3. Stop log (View Logs or
       /data/data/com.ivarna.fluxlinux/files/home/.fluxlinux/gui_desktop.log
       or the path GuiDesktopLog uses) contains:
         distro=<cardId> path=/data/local/tmp/chrootXxx
       and does NOT contain path=/data/local/tmp/chrootDebian13
       for this Stop.
    4. xfce4-session / xfwm4 / xfce4-panel / xfdesktop for THAT guest are gone.
    5. Debian chroot still intact:
         test -d /data/local/tmp/chrootDebian13
         test -e /data/local/tmp/chrootDebian13/usr/bin/startxfce4 \
           || test -e /data/local/tmp/chrootDebian13/.flux_configured
         # binds that were mounted for Debian before this Stop are not
         # newly torn down. Record: findmnt | rg chrootDebian13 before+after
         # first UKPA Stop.

    If log still says path=…/chrootDebian13 → deploy failed or case arm
    wrong. Fix, rebuild, reinstall, retest that path.

D-SIB  After the first UKPA Stop (ubuntu_chroot is enough):
    Home → Debian (Rooted) card.
    If it still shows Start: Start XFCE once and confirm it paints
    (proves we did not unmount Debian). Screenshot:
      docs/plans/results/ukpa_stopfix_debian_chroot_smoke.png
    If Debian chroot Start fails because binds were unmounted by the
    previous Stop, that is a P0 regression — fix stop_debian13 path leak
    (should already be fixed by A1) and retest.

D-UN  Do NOT fully uninstall a UKPA chroot unless you have disk pressure.
    Instead, dry-check the allowlist by reading the on-device deployed
    uninstall_guest_chroot.sh after HostScriptDeployer runs:

      grep chrootUbuntu /data/data/com.ivarna.fluxlinux/files/home/uninstall_guest_chroot.sh

    Optional: start the in-app uninstall wizard and cancel before confirm
    if the UI shows the correct path. Never delete a guest just to prove
    the allowlist.

D-PROOT  One proot smoke (ubuntu proot is enough): Start → Stop.
    Proot uses stop_gui.sh, not stop_gui_chroot.sh — should be unchanged.
    Screenshot optional. If it breaks, you touched the wrong file.

Automation hints:

  adb -s Y5WWBMJVOZSK4HU8 logcat -c
  adb -s Y5WWBMJVOZSK4HU8 logcat -s DesktopLauncher:* EmbeddedX11:* GUI:* AndroidRuntime:E
  adb exec-out screencap -p > docs/plans/results/<name>.png
  adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml
  adb shell dumpsys activity activities | rg -i 'termux.x11|mResumed'

==============================================================================
PHASE E — Reports
==============================================================================

Update each of the four chroot device reports with a "Stop retest (2026-08-14
dispatch fix)" section: D5 PASS/FAIL, log excerpt showing the correct
CHROOT_PATH, Debian-intact check, screenshot names.

Write a short fix report:

  docs/plans/results/ukpa-stop-dispatch-fix-report.md

Include:
  - Overall PASS/FAIL
  - Files changed
  - Unit test command + result
  - Per-path D1/D5 matrix
  - Debian sibling smoke
  - APK path
  - Anything else you found in the chroot/ dispatch grep

Do not invent PASS. If Debian smoke fails, say so.

==============================================================================
DEFINITION OF DONE
==============================================================================

- [ ] stop_gui_chroot.sh has the four UKPA arms; * still Debian 13
- [ ] uninstall_guest_chroot.sh allowlist includes the four UKPA paths
- [ ] UkpaHostDispatchContractTest (or equivalent) green
- [ ] :app:testIvarnaDebugUnitTest all green
- [ ] Ivarna release installed with adb install -r
- [ ] On-device deployed stop_gui_chroot.sh contains ubuntu|ubuntu_chroot
- [ ] All 4 UKPA chroot D5: log path is the UKPA chroot, not chrootDebian13
- [ ] xfce processes in that chroot gone after Stop; card shows Start
- [ ] Debian chroot still present; sibling Start still works
- [ ] Screenshots + ukpa-stop-dispatch-fix-report.md written
- [ ] ./gradlew --stop
- [ ] no git push

Until every box is checked, the work is not finished.
```
