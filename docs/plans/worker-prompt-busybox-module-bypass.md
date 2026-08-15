# Worker agent prompt — Drop BusyBox NDK module hard-requirement

**Status: READY.** Copy everything below the fence.

Implementation SSOT: [`docs/plans/busybox-module-bypass-impl.md`](./busybox-module-bypass-impl.md) — **read it whole first**. Locked IDs **B1–B14** win if this prompt and the impl file ever disagree.

Research (why only): [`docs/plans/busybox-module-bypass.md`](./busybox-module-bypass.md). Do not re-research, do not implement Option B (ship ELF) or Option C (Java xz).

```
You are the FluxLinux worker for the BusyBox NDK-module bypass.

Repo:     /home/abhaybyte/repos/fluxlinux
Plan:     docs/plans/busybox-module-bypass-impl.md   ← SSOT, read it whole first
Research: docs/plans/busybox-module-bypass.md        ← why only; do not re-litigate
Package:  com.ivarna.fluxlinux
Tests:    ./gradlew :app:testIvarnaDebugUnitTest --no-daemon
Signing:  keystore.properties → ~/repos/keys/fluxlinux.jks
Do not git push. Stop Gradle daemons when done. Do not implement extra features.

==============================================================================
MISSION
==============================================================================

Stop requiring users to flash osm0sis "Busybox for Android NDK" in Magisk /
KernelSU / APatch.

Those managers already ship a static BusyBox:
  Magisk   /data/adb/magisk/busybox
  KernelSU /data/adb/ksu/bin/busybox
  APatch   /data/adb/ap/bin/busybox

FluxLinux already runs "$BB mount" / "$BB chroot" / "$BB tar". The extra module
only puts applet names on PATH. Detection lists disagree (Debian/GUI miss KSU;
APatch is nowhere). The UI says rooted users MUST flash the zip. That is false.

Implement the impl plan Phases 1 → 2 → 3 → 4 in that order.
Helper bump: fluxlinux-chroot v2.7 → v2.8 (header, VERSION_STR, ChrootPaths).

Device fact already in-tree (do not redo): Fedora flux login used KSU busybox
1.36.1.1 (topjohnwu) with NO NDK module (docs/plans/fedora-chroot-flux-setuidgid.md).

==============================================================================
LOCKED CONTRACTS (do not invent alternatives)
==============================================================================

B1  Candidate order EXACT (first bb_ok wins):
      FLUX_BB (if bb_ok)
      /data/local/tmp/flux_busybox
      /data/adb/ksu/bin/busybox
      /data/adb/ap/bin/busybox
      /data/adb/magisk/busybox
      /data/adb/modules/busybox-ndk/system/xbin/busybox
      /data/adb/modules/busybox-ndk/system/bin/busybox
      /debug_ramdisk/busybox
      /sbin/busybox
      command -v busybox (if bb_ok)
      /system/xbin/busybox
      /system/bin/busybox

B2  bb_ok = -x AND path is not *com.termux* / *fluxlinux* / *nativecode*
    AND "$bb --list" (split on space/tab/newline) contains BOTH chroot AND mount.

B3  Never exec BusyBox from /data/data/<pkg>/files/… (W^X). Pin/helper stay
    under /data/local/tmp.

B4  After resolve, best-effort:
      cp -f "$BB" /data/local/tmp/flux_busybox && chmod 755
    Pin failure is NOT fatal.

B5  Version stamp + VERSION_STR + ChrootPaths.CHROOT_HELPER_VERSION all
    "fluxlinux-chroot v2.8" in the SAME change.

B6  Do NOT restore chroot --userspec. guest_userspec stays numeric setuidgid.
    If setuidgid missing, die (existing message class). No GNU flags.

B7  Root install gate = RootShell.isRootAvailable() ONLY.
    Missing BusyBox must NOT postFail onboarding before setup runs.

B8  BusyBoxInstallStep stays step 6 of PrerequisitesScreen. Transform it.
    Do not renumber the wizard.

B9  Kotlin list = BusyBoxPaths.CANDIDATES. Shell must contain every path.
    Unit test asserts both.

B10 RootShell stays su-only (no ChrootCommandBuilder import).
    New types: BusyBoxPaths + RootShell methods below.

B11 POSIX /system/bin/sh only in resolve_bb.sh. No bashisms.

B12 /system/bin/mount and /system/bin/chroot are FALLBACKS after $BB fails
    that applet. Remount /data already prefers /system/bin/mount — keep it.

B13 Do NOT add a BusyBox ELF to the APK.

B14 Do not touch proot, family scripts, GuestLoginShell, host bootstrap.

------------------------------------------------------------------------------
Locked Kotlin API
------------------------------------------------------------------------------

New file:
  app/src/main/kotlin/com/ivarna/fluxlinux/core/root/BusyBoxPaths.kt

object BusyBoxPaths {
    const val PINNED = "/data/local/tmp/flux_busybox"
    const val RESOLVER_ASSET = "scripts/chroot/resolve_bb.sh"
    const val RESOLVER_ON_DEVICE = "/data/local/tmp/fluxlinux_resolve_bb.sh"
    val CANDIDATES: List<String> = listOf(
        "/data/adb/ksu/bin/busybox",
        "/data/adb/ap/bin/busybox",
        "/data/adb/magisk/busybox",
        "/data/adb/modules/busybox-ndk/system/xbin/busybox",
        "/data/adb/modules/busybox-ndk/system/bin/busybox",
        "/debug_ramdisk/busybox",
        "/sbin/busybox",
        "/system/xbin/busybox",
        "/system/bin/busybox",
    )
}

RootShell additions (names exact):
  resolveBusyBox(): String?                  // bg thread; su; cache
  probeBusyBox(forceClearCache: Boolean = false, onResult: (String?) -> Unit)
                                             // callback on main (like probeRootAvailable)
  cachedBusyBox(): String?                   // cache only; never probe
  clearBusyBoxCache()
  seedBusyBoxForTest(path: String?)
  ensureBusyBoxResolver(ctx: Context): Boolean
                                             // stage RESOLVER_ASSET → RESOLVER_ON_DEVICE
                                             // same su cp/chmod pattern as ensureChrootHelper

resolveBusyBox MUST use su. Do NOT File.exists() on /data/adb/* from app uid.
resolveBusyBox has no Context and does NOT stage. Call ensureBusyBoxResolver first
at every Kotlin site.

ensureChrootHelper ALWAYS calls ensureBusyBoxResolver(ctx) on entry — including
the version-stamp early return (RootShell.kt:343–345). Also self-heal:
$HOME/resolve_bb.sh or staged_scripts/resolve_bb.sh → RESOLVER_ON_DEVICE.

Probe algorithm is the SHELL resolver under su (one algorithm), not a second
Kotlin walk that can drift. Locked probe exits 1 if the sidecar is absent.

------------------------------------------------------------------------------
Locked shell API
------------------------------------------------------------------------------

New file: app/src/main/assets/scripts/chroot/resolve_bb.sh
  #!/system/bin/sh
  No set -e. Safe to source under set -u.

  bb_has()  # $1=busybox $2=applet — --list | tr ' \t' '\n' | grep -qx
  bb_ok()   # $1=path — B2
  resolve_bb()  # sets BB; return 0/1; implements B1+B4

fluxlinux_chroot.sh:
  - Source resolver from dirname $0 then RESOLVER_ON_DEVICE.
  - If source fails, EMBED the same functions (helper must stay single-file).
  - Replace today's resolve_bb() body (FLUX_BB + command -v + short list).
  - die "root-capable busybox not found" on failure (same idea as now).
  - bind_if_missing / mount_type_if_missing: $BB then /system/bin/mount
  - guest_chroot_env: $BB chroot if bb_has chroot, else /system/bin/chroot
  - guest_userspec unchanged except it still requires setuidgid on $BB

Other scripts — TWO snippets (impl §1 "How other scripts consume it"):

  Setup/start/stop: source sidecar if present; if BB still empty, INLINE B1 walk
  (ksu → ap → magisk → ndk → ramdisk → sbin → /system). Then exit 1 if still empty.
  Do NOT last-resort FLUX_BB/PINNED only. Desktop copies only start_*_gui.sh to
  /data/local/tmp; dirname $0/resolve_bb.sh will miss.

  Uninstall: same source + inline walk, but NEVER exit 1 because BB is empty.
  Umount: $BB umount -l then /system/bin/umount -l (today’s || umount -l).

Also change start_gui_chroot.sh / stop_gui_chroot.sh (~L205–229):
  cp $TERMUX_HOME/resolve_bb.sh → /data/local/tmp/fluxlinux_resolve_bb.sh
  export FLUX_BB and FLUX_RESOLVE_BB on the su -c line next to HELPER.

------------------------------------------------------------------------------
FLUX_BB injection
------------------------------------------------------------------------------

RootShell.buildChrootHelperCmd:
  if cachedBusyBox() != null, export FLUX_BB='…' next to FLUX_PACKAGE / FLUX_CHROOT.

ChrootCommandBuilder.build:
  ensureBusyBoxResolver + resolveBusyBox.
  Do NOT put FLUX_BB inside buildRootInner (keep that contract).
  Prefix withPath only:
    val bb = RootShell.cachedBusyBox()
    val exportBb = if (!bb.isNullOrEmpty()) "export FLUX_BB='$bb'; " else ""
    val withPath = "${exportBb}export FLUX_CHROOT='$chrootPath'; $rootInner"
  buildEnv: put FLUX_BB when cachedBusyBox() non-null.

OnboardingInstallRunner.runChroot rootCmd:
  After root OK: ensureBusyBoxResolver + resolveBusyBox().
  If non-null, export FLUX_BB in rootCmd.
  Do NOT postFail when null.
  Change the existing root-missing string from
    "Root not available. Grant superuser to FluxLinux, install BusyBox if needed, then retry."
  to
    "Root not available. Grant superuser to FluxLinux, then retry."

DesktopLauncher (chroot start/stop):
  ensureBusyBoxResolver + resolveBusyBox BEFORE launching start_gui_chroot.sh.

UninstallSessionFactory inner su string:
  ensureBusyBoxResolver + resolveBusyBox first.
  Copy resolver onto RESOLVER_ON_DEVICE in the cp chain.
  Export FLUX_BB and FLUX_RESOLVE_BB INSIDE the su string (su drops extraEnv).

TermuxIntentFactory: replace the bare token "busybox chroot" with
  BB="${FLUX_BB:-/data/local/tmp/flux_busybox}";
  [ -x "$BB" ] || BB="$(command -v busybox)";
  "$BB" chroot …
Do not rewrite the rest of that factory.

HostScriptDeployer.HOST_SCRIPTS: add required
  HostScript("resolve_bb.sh", "scripts/chroot/resolve_bb.sh")

------------------------------------------------------------------------------
UI copy (exact)
------------------------------------------------------------------------------

OnboardingFlowScreen chroot blurb — REPLACE the current sentence with:
  Rooted path: grant superuser to FluxLinux. Magisk, KernelSU, and APatch already include BusyBox — a separate module is not required.

BusyBoxInstallStep:
  Probe via RootShell.probeBusyBox (NOT RootUtils).
  Success: "Using the BusyBox that came with your root manager. You do not need to flash a separate module."
           Show path. Continue ON. No checkbox.
  Fail + rooted: Download Module card may stay. Soften MUST. Checkbox optional:
           "I will install BusyBox NDK only if chroot setup fails"
           Continue still allowed (B7).
  Not rooted: keep skip card.

TerminalComponent.CHROOT_ROOT_SHELL KDoc: "Root + SSOT chroot helper."

docs/architecture.md table: Root (drop "BusyBox NDK").

Tutorials setup_fluxlinux.md + setup_debian_chroot.md: BusyBox is optional /
only if setup cannot find a manager BusyBox. Do not say it is required.

CHANGELOG Unreleased:
  - feat: chroot uses Magisk/KernelSU/APatch built-in BusyBox — NDK module no longer required
  - fix: fluxlinux-chroot v2.8 — shared BusyBox resolver (APatch + KSU paths, applet probe, pin to /data/local/tmp/flux_busybox)

==============================================================================
OUT OF SCOPE — do not touch
==============================================================================

- BusyBox ELF in APK / jniLibs / assets/busybox
- Java xz / Commons Compress extract
- PREFIX/Termux tar, xz, or busybox as root
- GNU chroot --userspec
- proot, family scripts, XFCE, ExtraKeys, GuestLoginShell
- Renumbering PrerequisitesScreen steps
- zenithblue-only sources
- git push
- Re-opening Fedora setuidgid design

==============================================================================
PHASE 1 — Paths + resolver + helper v2.8
==============================================================================

1. BusyBoxPaths.kt (exact API above)
2. resolve_bb.sh (exact functions above)
3. fluxlinux_chroot.sh v2.8 + fallbacks (B12)
4. ChrootPaths.CHROOT_HELPER_VERSION = "fluxlinux-chroot v2.8"
5. RootShell APIs + ensureChrootHelper stages resolver + buildChrootHelperCmd export
6. HostScriptDeployer entry
7. ChrootPathsTest: helper_version_is_v28
8. BusyBoxPathsTest: see impl §4

Run: ./gradlew :app:testIvarnaDebugUnitTest --no-daemon

==============================================================================
PHASE 2 — Delete every private detector
==============================================================================

Replace detectors in EVERY file listed in impl §3 Phase 2
(chroot setup/start/stop/uninstall + debian/chroot + arch setup + kde start/stop
+ leftover debian/chroot/setup uninstall_*.sh + start_gui_chroot.sh/stop_gui_chroot.sh
copy+FLUX_BB). Use the UNINSTALL snippet (no exit 1 on empty BB) for uninstall_*.

Critical binds that today do "$BB mount … || goodbye":
  add "/system/bin/mount … || goodbye" after $BB fails.

Grep the tree when done. These must be GONE as the ONLY detector:
  - candidate lists that omit /data/adb/ap/bin/busybox AND do not source resolve_bb.sh
  - uninstall "command -v busybox" with no resolver
  - bare "busybox chroot" in TermuxIntentFactory.kt

==============================================================================
PHASE 3 — Inject + UI + docs
==============================================================================

OnboardingInstallRunner, ChrootCommandBuilder.build/buildEnv,
DesktopLauncher, UninstallSessionFactory,
TermuxIntentFactory, BusyBoxInstallStep, OnboardingFlowScreen,
TerminalComponent KDoc, architecture.md, two tutorials, CHANGELOG.
Live nav is OnboardingFlowScreen only (MainActivity); PrerequisitesScreen is
leftover — still transform step 6 (B8); user-visible gate is the onboarding blurb.

==============================================================================
PHASE 4 — Tests + optional device
==============================================================================

Required tests (impl §4) — all must exist and pass:

BusyBoxPathsTest:
  CANDIDATES order exact
  first three = ksu, ap, magisk
  constants exact
  helper asset + resolve_bb.sh contain every CANDIDATES path
  PREFIX reject strings present
  bb_ok / bb_has require chroot and mount
  helper VERSION / header v2.8
  helper mentions /system/bin/mount and /system/bin/chroot
  Phase 2 scripts source resolve_bb.sh or call resolve_bb
  TermuxIntentFactory.kt does not contain the substring "busybox chroot"

ChrootPathsTest:
  version v2.8
  setuidgid still present
  no chroot --userspec

RootShell seed/clear cache test.

Existing ChrootCommandBuilderTest / GuestLoginShell tests stay green.
buildRootInner argv MUST NOT change.

Device (ONLY if adb devices shows a device; do not block the task):
  1. ls manager busybox paths as root
  2. After APK install, open an already-installed chroot tab
  3. helper on device is v2.8
  4. /data/local/tmp/flux_busybox and fluxlinux_resolve_bb.sh exist
  Do NOT uninstall a working chroot to re-extract unless asked.
  APatch hardware is optional.

If you assemble a release for smoke:
  ./gradlew :app:assembleIvarnaRelease --no-daemon
  then adb install -r. Stop daemons after.

==============================================================================
ACCEPTANCE
==============================================================================

- testIvarnaDebugUnitTest green
- helper v2.8 everywhere
- resolve_bb.sh exists; setup/start/stop source it AND inline-walk if sidecar missing
- uninstall never exit 1 solely because BB is empty
- start_gui_chroot.sh / stop_gui_chroot.sh copy resolver + export FLUX_BB
- DesktopLauncher + UninstallSessionFactory call ensureBusyBoxResolver
- APatch + KSU + Magisk in Kotlin AND shell
- PREFIX still rejected
- no chroot --userspec
- UI does not say MUST flash NDK when probe succeeds
- onboarding blurb exact
- CHANGELOG updated
- no BusyBox ELF in APK
- no git push
- impl plan status line can be flipped to IMPLEMENTED + date if you land it

Wrap up with: files touched, test command + result, device smoke (or "no device"),
any residual. Do not claim device pass without adb output.
```
