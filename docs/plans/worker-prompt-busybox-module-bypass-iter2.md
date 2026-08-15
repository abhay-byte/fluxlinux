# Worker agent prompt — BusyBox bypass iter 2 (noexec pin + full device smoke)

**Status: READY.** Copy everything below the fence.

Iter 1 already landed. Do **not** re-implement B1–B14. Do **not** bump the helper unless you touch `fluxlinux_chroot.sh` logic (comment-only = no bump).

SSOT: [`docs/plans/busybox-module-bypass-impl.md`](./busybox-module-bypass-impl.md) **B15** + review `/tmp/grok-1000/grok-review-797a728f.md`.

```
You are the FluxLinux worker for BusyBox-bypass ITER 2.

Repo:     /home/abhaybyte/repos/fluxlinux
Plan:     docs/plans/busybox-module-bypass-impl.md   (B15 + Iter 2 section)
Review:   /tmp/grok-1000/grok-review-797a728f.md     (1 bug, 2 suggestions, 1 nit)
Package:  com.ivarna.fluxlinux
Tests:    ./gradlew :app:testIvarnaDebugUnitTest --no-daemon
Signing:  keystore.properties → ~/repos/keys/fluxlinux.jks
Device:   USB adb — Xiaomi 2311DRK48I (duchamp), KernelSU
           /data/adb/ksu/bin/busybox = BusyBox v1.36.1.1 topjohnwu
           /data/local/tmp is NOEXEC (pin ELF exec = 127)
Do not git push. Stop Gradle daemons when done.

==============================================================================
MISSION
==============================================================================

Iter 1 already dropped the NDK-module hard-req. Debian (Rooted) uninstall +
reinstall and XFCE4 worked on this phone with KSU busybox. Helper is v2.8.

Fix the remaining hole, then MANUAL-TEST install, uninstall, terminal, XFCE4
the same way iter 1 did (in-app, on this device).

==============================================================================
LOCKED — do not reopen
==============================================================================

- Do not ship a BusyBox ELF. Do not Java-xz extract. No chroot --userspec.
- resolve_bb.sh / helper embed pin block (bb_ok after cp) is CORRECT. Leave it.
- B1 order, PREFIX reject, uninstall must not exit 1 solely because BB is empty.
- Helper stays v2.8 unless you change helper logic (not just a comment).

==============================================================================
B15 (new contract)
==============================================================================

Never assign BB=/data/local/tmp/flux_busybox (or any candidate) on [ -x ] alone.

[ -x ] is true after chmod 755 even when the mount is noexec (this device:
exec 127). bb_ok is safe because it runs "$1 --list". Last-resort walks are not.

Every last-resort block that today does:

  if [ -z "${BB:-}" ] && [ -x /data/local/tmp/flux_busybox ]; then
    BB=/data/local/tmp/flux_busybox
  fi

MUST change. Pick ONE pattern and use it everywhere:

  if [ -z "${BB:-}" ] && [ -x /data/local/tmp/flux_busybox ] &&
     /data/local/tmp/flux_busybox --list >/dev/null 2>&1; then
    BB=/data/local/tmp/flux_busybox
  fi

Then still walk ksu → ap → magisk → ndk → ramdisk → sbin → /system if BB empty.
Do not skip the manager walk just because the pin file exists.

If resolve_bb was already sourced, prefer bb_ok "$path" instead of --list.

==============================================================================
CODE FIXES (do all four)
==============================================================================

1) BUG — last-resort [ -x PINNED ]  (review Issue 1)

Grep for:
  [ -x /data/local/tmp/flux_busybox ]
That string must not assign BB without an exec probe.

Known copies (fix every one; grep to catch stragglers):
  setup_guest_chroot.sh
  setup_debian13_chroot.sh
  setup_alpine_chroot.sh
  setup_arch_chroot.sh
  start_guest_gui.sh start_debian13_gui.sh start_alpine_gui.sh
  stop_guest_gui.sh stop_debian13_gui.sh stop_alpine_gui.sh
  uninstall_guest_chroot.sh uninstall_debian13_chroot.sh uninstall_alpine_chroot.sh
  debian/chroot/setup/setup_debian13_chroot.sh setup_debian_chroot.sh
  debian/chroot/setup/uninstall_debian13.sh uninstall_debian_chroot.sh
  debian/chroot/start/start_debian13_kde_gui.sh
  start_debian13_kde_gui_software.sh start_debian13_kde_gui_turnip.sh
  debian/chroot/stop/stop_debian13_gui.sh stop_debian13_kde_gui.sh

Do NOT change resolve_bb.sh pin-after-copy (already uses bb_ok).
Do NOT change the helper embed pin-after-copy (already uses bb_ok).

2) SUGGESTION — TermuxIntentFactory.kt (~L568 and ~L612)

Today:
  BB="${FLUX_BB:-/data/local/tmp/flux_busybox}";
  [ -x "$BB" ] || BB="$(command -v busybox)";

On this device [ -x pin ] is true and chroot is 127.
Change to: use FLUX_BB if set and executable-for-real; else try
  /data/adb/ksu/bin/busybox
  /data/adb/ap/bin/busybox
  /data/adb/magisk/busybox
  then command -v busybox
  then pin ONLY if "$pin --list" succeeds.
Do not rewrite the rest of the factory.

3) SUGGESTION — UninstallSessionFactory.kt (~L46-52)

Resolver cp is hard-&& in the su string. If that cp fails, uninstall never runs.
After the uninstall script is in /data/local/tmp, copy the resolver with || true
(or ; ) then still sh '$tmpPath' with FLUX_BB / FLUX_RESOLVE_BB when present.

4) NIT — fluxlinux_chroot.sh guest_as_user header (~L355)

Says “then --userspec”. Change to numeric setuidgid. No version bump for this.

==============================================================================
TESTS
==============================================================================

./gradlew :app:testIvarnaDebugUnitTest --no-daemon

Add/extend BusyBoxPathsTest:
  No Phase 2 setup/start/stop/uninstall file may contain a BB= assignment
  to /data/local/tmp/flux_busybox that is guarded only by [ -x ] (no --list
  and no bb_ok on the same if).
  TermuxIntentFactory.kt must not default BB to the pin without an exec probe.

Existing tests stay green. Do not change buildRootInner.

==============================================================================
MANUAL DEVICE SMOKE (required — same style as iter 1)
==============================================================================

Device is already rooted (KSU). FluxLinux Ivarna is installed. Debian chroot
was reinstalled in iter 1 and should still be present.

Do this IN THE APP (not only adb), then confirm with adb.

A. Pin / noexec regression
   1. adb shell su -c 'ls -l /data/local/tmp/flux_busybox; /data/local/tmp/flux_busybox --list | head'
      Expect: file may exist; --list fails (127 / Permission denied).
   2. Temporarily hide the sidecar to force last-resort:
        adb shell su -c 'mv /data/local/tmp/fluxlinux_resolve_bb.sh /data/local/tmp/fluxlinux_resolve_bb.sh.bak'
      Unset is automatic if you run a start script without FLUX_BB.
   3. Open Debian chroot **flux** terminal from the Terminal page (or Start Desktop).
      Must still work — BB must be /data/adb/ksu/bin/busybox, NOT the pin.
   4. Restore sidecar:
        adb shell su -c 'mv /data/local/tmp/fluxlinux_resolve_bb.sh.bak /data/local/tmp/fluxlinux_resolve_bb.sh'
   If you cannot hide the sidecar safely, at least log `echo Using Root Busybox:`
   from a start and prove it is the KSU path.

B. Terminal (required)
   1. Terminal page → Debian (Rooted) flux shell. Login works (prompt, uid=1000).
   2. Debian (Rooted) root shell. Login works (uid=0).
   3. Type a command in each (id; pwd). ExtraKeys not in scope unless broken.

C. XFCE4 desktop (required)
   1. Home / Distros → Debian CHROOT → Start.
   2. X11 comes up; XFCE desktop appears (same as iter 1).
   3. Stop desktop. Confirm it stops without a hang.

D. Uninstall + reinstall (required — same clicks as iter 1)
   1. Home → Chroot → Debian settings → Uninstall Distribution → confirm.
   2. Terminal shows uninstall complete. adb: /data/local/tmp/chrootDebian13 gone.
   3. Distros → Chroot → Debian (Rooted) → Install → Install base desktop (Root).
   4. Log: Root OK (no BusyBox-module failure) → extract → XFCE → customization.
   5. UI 100% / Environment ready.
   6. After install: helper still v2.8; resolver present;
      su resolve still /data/adb/ksu/bin/busybox.
   7. Open flux terminal once AND Start desktop once on the FRESH install.

E. Record a table like iter 1 (manager path, helper version, resolver path,
   pin exec result, uninstall result, reinstall result, terminal, XFCE).

If no adb device, stop after unit tests and say so. Do not claim device pass
without adb/UI evidence.

Assemble only if you need a new APK for the pin fix:
  ./gradlew :app:assembleIvarnaRelease --no-daemon
  adb install -r
Stop daemons after.

==============================================================================
OUT OF SCOPE
==============================================================================

- Shipping busybox in the APK
- Proot, family scripts, GuestLoginShell
- Renumbering PrerequisitesScreen
- git push
- Re-doing the whole resolver

==============================================================================
ACCEPTANCE
==============================================================================

- testIvarnaDebugUnitTest green
- grep: no last-resort BB=pin on [ -x ] alone
- TermuxIntentFactory does not prefer a dead pin
- Uninstall still runs if resolver cp fails
- Device table: uninstall, reinstall, flux terminal, root terminal, XFCE start/stop
- Live BB stays /data/adb/ksu/bin/busybox on this phone
- no git push

Wrap up with files touched, test result, device table, residuals.
```
