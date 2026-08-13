# Worker agent prompt — UKPA iteration 1 (Ubuntu / Kali / Parrot / Arch)

**Copy everything below the fence into a new worker.**  
Plan SSOT: [`docs/plan/ubuntu-kali-parrot-arch.md`](../plan/ubuntu-kali-parrot-arch.md)

```
You are the FluxLinux UKPA worker (Ubuntu 26.04 / Kali 2026.2 / Parrot 7.2 / Arch Linux ARM).

This is ITERATION 1 of that plan: implement the full contract, then device-test
ALL 8 new paths (4 distros × proot + chroot). Start E2E with Ubuntu. Do not
stop on the first green path.

Repo:     /home/abhaybyte/repos/fluxlinux
Plan:     docs/plan/ubuntu-kali-parrot-arch.md   ← SSOT, read it whole first
Package:  com.ivarna.fluxlinux
Flavor:   :app:assembleIvarnaRelease --no-daemon
APK:      app/build/outputs/apk/ivarna/release/app-ivarna-release.apk
Install:  adb install -r <apk>
Device:   Xiaomi 2311DRK48I (duchamp) serial Y5WWBMJVOZSK4HU8, Android 16, KernelSU
          (emulator-5554 may also be attached — use the physical device for chroot)
Signing:  keystore.properties → ~/repos/keys/fluxlinux.jks
Do not git push. Stop Gradle when done. No debug APK. No APK uninstall unless
signature mismatch (INSTALL_FAILED_UPDATE_INCOMPATIBLE).

==============================================================================
STATUS OF THE TREE (2026-08-13) — read before writing
==============================================================================

LIVE already (DO NOT BREAK — 8 distros / 16 cards, DEVICE PASS):
  alpine, alpine_chroot
  debian, debian13_chroot
  fedora, fedora_chroot
  void, void_chroot
  opensuse, opensuse_chroot
  deepin, deepin_chroot
  chimera, chimera_chroot
  manjaro, manjaro_chroot

STUBS to replace (comingSoon = true, dual-mode on one id — FORBIDDEN in product):
  ubuntu     (prootSupported && chrootSupported, comingSoon)
  kali       (chroot-only stub, comingSoon, "Not in proot-distro")
  parrot     (chroot-only stub, comingSoon)
  archlinux  (prootSupported && chrootSupported, comingSoon, VNC-era family)

SupportedDistro already has UBUNTU, KALI, ARCH. ADD PARROT.
DistroInstallProfile has ZERO ubuntu/kali/parrot/archlinux entries.
  isInstallable("archlinux") is currently FALSE — tests assert that; flip them.
allRootfsProfiles() size is 8 (debian+alpine+fvo+dcm). After this work: 12
  (dedupe by filename → +4: ubuntu + kali + parrot + arch).
allInstallable() size is 16. After this work: 24.

Arch rootfs already in tree:
  assets/rootfs/archlinux_arm_rootfs.tar.xz
  SHA-256 40209ef6318d3aad732299d46ce224c6a0ecded80b6f8091f5e38b40fa031d75
  116_277_544 bytes. Slim done. See docs/plans/results/archlinux-alarm-slim-report.md
  and docs/plan/archlinux-alarm-slim-rootfs.md.

Source archives (host, not yet packaged):
  /home/abhaybyte/Downloads/resolute-base-arm64.tar.gz
    35094784  SHA e9dfcbf8763371965597edcb351eaa7daacfb0805bb9ae9c8d6479a0b25bf928
  /home/abhaybyte/Downloads/kali-nethunter-rootfs-minimal-arm64.tar.xz
    137313840 SHA d6403a5da175df325611d23af4b92330856059c45454eced7f4cdf3ca6df2e4e
    NESTED under kali-arm64/
  /home/abhaybyte/Downloads/parrot-arm64.tar.xz
    111838320 SHA 8a486c8635918de6cebc3b339265c4cea73cb9d73f709d56d98e487769f78582
    NESTED under parrot-arm64/

==============================================================================
OUT OF SCOPE (hard)
==============================================================================

- OpenBSD. Do not copy openbsd_miniroot79.img into assets/rootfs/.
- NetHunter Magisk, nh CLI, hid/wifi injection.
- kali-linux-default / kali-linux-large / kali-tools-* / kali-desktop-*
- parrot-tools / parrot-interface / parrot-desktop-*
- ubuntu-desktop, do-release-upgrade, PPAs, deb.debian.org on Ubuntu/Kali/Parrot
- KDE / GNOME / DDE
- Debian-style feature modules (appdev/webdev/…)
- Debug / zenithblue APKs
- Re-installing the existing 8 guests unless a shared-script change requires
  a sibling smoke (last green path only — Deepin or Manjaro proot is enough)
- Changing Debian / Alpine / FVO / DCM behavior except shared helpers already
  in those contracts (flux_guest_common, setup_customization_xfce,
  setup_hw_accel_guest). Shared change → sibling-smoke last green path.

==============================================================================
PRODUCT SHAPE (must match existing 8)
==============================================================================

Install (Distros wizard or onboarding) → internal Terminal (User = flux, zsh,
OMZ agnosterzak, pokemon-colorscripts) → Home Start XFCE on embedded
Termux:X11 → Space theme + Papirus-Dark + Vimix cursors + JetBrainsMono Nerd
+ fluxlinux-dark.png wallpaper → Stop returns Start.

Cards (SPLIT dual-mode stubs — never leave prootSupported && chrootSupported
on one id):

  ubuntu           Ubuntu            proot  0xFFE95420  R.drawable.distro_ubuntu
  ubuntu_chroot    Ubuntu (Rooted)   chroot /data/local/tmp/chrootUbuntu   same
  kali             Kali              proot  0xFF367BF5  R.drawable.distro_kali
  kali_chroot      Kali (Rooted)     chroot /data/local/tmp/chrootKali     same
  parrot           Parrot            proot  0xFF00D9FF  R.drawable.distro_parrot
  parrot_chroot    Parrot (Rooted)   chroot /data/local/tmp/chrootParrot   same
  archlinux        Arch              proot  0xFF1793D1  R.drawable.distro_arch
  archlinux_chroot Arch (Rooted)     chroot /data/local/tmp/chrootArch     same

Descriptions: Ubuntu 26.04 / Kali Rolling / Parrot 7.2 / Arch Linux ARM +
“with XFCE4 (proot)” / “chroot environment (Requires Root).”
Icons already exist. No new drawables.

Components on every new card (same helper as FVO/DCM):
  glibcXfceComponents("…/setup_<family>_family.sh")
  → xfce4_desktop + hw_accel + customization
Do NOT attach debian/common/setup/setup_*_debian.sh modules.

==============================================================================
PHASE 0 — Read first
==============================================================================

Read in this order:

  docs/plan/ubuntu-kali-parrot-arch.md          (whole file)
  docs/plan/deepin-chimera-manjaro.md §4–§7, §13 (pattern to copy)
  docs/plan/archlinux-alarm-slim-rootfs.md
  docs/plan/xfce-icons-locale-fastfetch.md §1.3  (Papirus ⊘ — D4 must not lie)
  app/src/main/kotlin/…/install/DistroInstallProfile.kt
  app/src/main/kotlin/…/data/DistroRepository.kt  (glibcXfceComponents + stubs)
  app/src/main/kotlin/…/root/ChrootPaths.kt
  app/src/main/kotlin/…/terminal/TerminalShellCatalog.kt
  app/src/main/kotlin/…/terminal/GuestZshrcRepair.kt
  app/src/main/kotlin/…/terminal/HostScriptDeployer.kt
  app/src/main/assets/scripts/debian/proot/setup/flux_install.sh
  app/src/main/assets/scripts/chroot/start_gui_chroot.sh
  app/src/main/assets/scripts/deepin/common/setup/setup_deepin_family.sh
  app/src/main/assets/scripts/manjaro/common/setup/setup_manjaro_family.sh
  app/src/main/assets/scripts/arch/common/setup/setup_arch_family.sh  (REWRITE)
  app/src/main/assets/scripts/common/setup/flux_guest_common.sh
  app/src/main/assets/scripts/common/setup/setup_customization_xfce.sh
  app/src/main/assets/scripts/common/setup/setup_hw_accel_guest.sh
  app/build.gradle.kts  stageHostRootfs
  existing unit tests listed in Phase 4

Clone Deepin (apt) / Manjaro (pacman) family shape. Do not clone
setup_debian_family.sh or the current VNC-era setup_arch_family.sh.

==============================================================================
PHASE 1 — Flatten + xz + pin SHA (P1–P5) BEFORE any device install
==============================================================================

Never ship Ubuntu as .tar.gz (aapt2 auto-decompresses). Kali/Parrot MUST be
flat (./usr/bin/bash at archive root). A wrapper dir installs an empty guest.

  mkdir -p /tmp/ukpa-repack assets/rootfs

  # Ubuntu: gzip → xz, already flat
  gzip -dc /home/abhaybyte/Downloads/resolute-base-arm64.tar.gz \
    | xz -T0 -9 > assets/rootfs/ubuntu_26.04_rootfs.tar.xz

  # Kali: drop kali-arm64/ prefix
  rm -rf /tmp/ukpa-repack/kali && mkdir -p /tmp/ukpa-repack/kali
  tar -xJf /home/abhaybyte/Downloads/kali-nethunter-rootfs-minimal-arm64.tar.xz \
    -C /tmp/ukpa-repack/kali
  test -x /tmp/ukpa-repack/kali/kali-arm64/usr/bin/bash
  tar -C /tmp/ukpa-repack/kali/kali-arm64 -cf - . \
    | xz -T0 -9 > assets/rootfs/kali_2026_2_rootfs.tar.xz

  # Parrot: drop parrot-arm64/ prefix
  rm -rf /tmp/ukpa-repack/parrot && mkdir -p /tmp/ukpa-repack/parrot
  tar -xJf /home/abhaybyte/Downloads/parrot-arm64.tar.xz -C /tmp/ukpa-repack/parrot
  test -x /tmp/ukpa-repack/parrot/parrot-arm64/usr/bin/bash
  tar -C /tmp/ukpa-repack/parrot/parrot-arm64 -cf - . \
    | xz -T0 -9 > assets/rootfs/parrot_7.2_rootfs.tar.xz

Spot-check (all must pass):

  tar -tJf assets/rootfs/ubuntu_26.04_rootfs.tar.xz | rg '^(./)?usr/bin/bash$'
  tar -tJf assets/rootfs/kali_2026_2_rootfs.tar.xz   | rg 'kali-arm64'    # EMPTY
  tar -tJf assets/rootfs/parrot_7.2_rootfs.tar.xz    | rg 'parrot-arm64'  # EMPTY
  tar -tJf assets/rootfs/archlinux_arm_rootfs.tar.xz | rg 'usr/bin/pacman'
  test ! -e assets/rootfs/openbsd_miniroot79.img
  test ! -e assets/rootfs/*.tar.gz   # no ubuntu gzip in assets

  sha256sum assets/rootfs/ubuntu_26.04_rootfs.tar.xz \
            assets/rootfs/kali_2026_2_rootfs.tar.xz \
            assets/rootfs/parrot_7.2_rootfs.tar.xz \
            assets/rootfs/archlinux_arm_rootfs.tar.xz

Pin the THREE new packaged SHA-256 values in:
  1. DistroInstallProfile constants
  2. docs/plan/ubuntu-kali-parrot-arch.md §1.2
  3. flux_install.sh case arms (fallback SHA, env still wins)

Min-size gates: Ubuntu ≥ 15 MiB, Kali ≥ 40 MiB, Parrot ≥ 30 MiB, Arch ≥ 40 MiB
(and Arch ≤ 250 MiB).

Gradle stageHostRootfs: add the four xz names next to the DCM trio (Arch
already lives in assets/rootfs/; still list it so assemble copies it into
app/src/main/assets/rootfs/). Update description string + inputs + outputs.

==============================================================================
PHASE 2 — Kotlin SSOT + catalog
==============================================================================

Cohesion table (plan §4) — do not leak package names into UI or SHA into
DistroRepository.

2.1 DistroSpec.kt
  Add:
    PARROT(id="parrot", family=DEBIAN, pm=APT, release=ROLLING)
  UBUNTU / KALI / ARCH already exist. Do not invent a second ARCH id.

2.2 DistroInstallProfile.kt
  Eight profiles. Pattern = Deepin/Manjaro:
    customizationScript = XFCE_CUSTOM
    hwAccelScript       = HW_ACCEL_GUEST
    chroot: setup_guest_chroot.sh / uninstall_guest_chroot.sh
            start_guest_gui.sh / stop_guest_gui.sh
  familyScript:
    ubuntu/common/setup/setup_ubuntu_family.sh
    kali/common/setup/setup_kali_family.sh
    parrot/common/setup/setup_parrot_family.sh
    arch/common/setup/setup_arch_family.sh
  prootName: ubuntu / kali / parrot / archlinux
    resolveProotName("archlinux_chroot") MUST stay "archlinux"
    (suffix strip already works; never removePrefix("arch")).
  BY_ID + allRootfsProfiles (+4 files) + allInstallable (+8 cards).
  isInstallable true for all eight ids.

2.3 ChrootPaths.kt
  UBUNTU_CHROOT_PATH = /data/local/tmp/chrootUbuntu
  KALI_CHROOT_PATH   = /data/local/tmp/chrootKali
  PARROT_CHROOT_PATH = /data/local/tmp/chrootParrot
  ARCH_CHROOT_PATH   = /data/local/tmp/chrootArch
  pathForDistro: four new arms. Distinct from Debian/Alpine/FVO/DCM.

2.4 DistroRepository.kt
  Split the four stubs into eight live cards (comingSoon = false).
  Each pair: proot-only / chroot-only.
  Components via existing glibcXfceComponents(familyScript).
  Leave adelie/artix/backbox/… stubs comingSoon.

2.5 TerminalShellCatalog / TerminalShellAvailability / TerminalLauncher
  Plan prefers Map<String, Boolean> keyed by card id. If that rewrite is
  too large this pass, add eight booleans the same way Deepin was added.
  Either way: User/Root cards MUST appear for the four new guests.
  Probe proot names ubuntu/kali/parrot/archlinux and the four chroot paths.
  Sections: "UBUNTU SHELL" / "KALI SHELL" / "PARROT SHELL" / "ARCHLINUX SHELL"
  (pick ARCHLINUX SHELL — test that exact string).
  prootDefs / chrootDefs when-arms + icons (already exist).

2.6 GuestZshrcRepair
  isGlibcGuest += ubuntu, kali, parrot, archlinux
    so apt-get/apt/pacman sudo wrappers apply.
  Do NOT treat Parrot as Debian-the-card (os-release ID=debian is a lie).
  resolveProotName: suffix-strip is enough; add a unit test that
    resolveProotName("archlinux_chroot") == "archlinux"
    and that a future removePrefix("arch") cannot land.

2.7 HostScriptDeployer
  Required HostScript entries:
    setup_ubuntu_family.sh
    setup_kali_family.sh
    setup_parrot_family.sh
    setup_arch_family.sh   (rewrite in place; already referenced by TermuxIntentFactory)
  allRootfsProfiles() already drives rootfs copy — once profiles exist,
  the four xz files deploy automatically.

2.8 Onboarding / BaseDesktopInstallPlan
  Profile-driven. Family paths do not contain debian/alpine, so
  flux_guest_common.sh is prepended — good. Do not special-case strings.

2.9 TerminalComponent / terminalComponentFor
  ubuntu/kali/parrot/archlinux → TERMUX_FLUX_TERMINAL
  *_chroot → CHROOT_ROOT_SHELL
  TerminalComponentTest currently expects archlinux to THROW. Flip it.

==============================================================================
PHASE 3 — Family scripts + host dispatch
==============================================================================

POSIX #!/bin/sh. Inline (or source) flux_guest_common.sh at the top of each
family file the same way Deepin/Manjaro do — HostScriptDeployer runs those
files standalone. If you edit flux_guest_common.sh, copy the same change
into every inlined family copy (Deepin/Chimera/Manjaro + the four new ones).

Shared apt prep — ONE helper, not three copy-pastes. Put _flux_apt_prep in
flux_guest_common.sh (and the inlined copies) and call it from Ubuntu/Kali/
Parrot families:

  export DEBIAN_FRONTEND=noninteractive
  export DEBCONF_NONINTERACTIVE_SEEN=true
  export SYSTEMD_OFFLINE=1
  mkdir -p /etc/apt/apt.conf.d
  printf 'APT::Sandbox::User "root";\nDPkg::Use-Pty "false";\n' \
    > /etc/apt/apt.conf.d/99flux-nosandbox

Targeted XFCE (all three apt families — NOT `apt-get install xfce4` first):

  xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
  xfdesktop4 xfwm4 thunar \
  adwaita-icon-theme fonts-dejavu-core \
  dbus dbus-x11 \
  libgl1-mesa-dri libegl1 mesa-utils \
  sudo passwd ca-certificates curl wget unzip tar xz-utils locales

Fallback to xfce4 metapackage ONLY if startxfce4 is still missing.
Never kali-desktop-xfce / parrot-interface / ubuntu-desktop.

Also install xz-utils (guest-side theme extract; Deepin lesson).
Call _flux_ensure_en_us_locale (already in common) after packages.
Call the shared contract: tmp, dns, flux user, NOPASSWD sudo, dbus,
home ownership, gpu_mode=virgl, VNC xstartup → startxfce4, chown PM
caches, _flux_require_startxfce4.

3.1 setup_ubuntu_family.sh  (NEW)
  Rewrite ubuntu.sources URIs:
    http://archive.ubuntu.com/ubuntu/   → http://ports.ubuntu.com/ubuntu-ports
    http://security.ubuntu.com/ubuntu/  → http://ports.ubuntu.com/ubuntu-ports
  Keep suites resolute* and components main universe.
  apt-get update (fail closed). Targeted list. _flux_ensure_user (uid 1000 free).
  No do-release-upgrade. No PPAs. No deb.debian.org.

3.2 setup_kali_family.sh  (NEW)
  Keep kali-rolling. Do not add Debian.
  Targeted XFCE only. Forbidden metas listed above.
  Leave user `kali` (uid 100000) alone or lock it (`usermod -L kali`).
  Create flux at uid 1000. Flux is the only user Start / customization / zsh
  repair touch. Ignore kali-nethunter-core / nethunter-utils.

3.3 setup_parrot_family.sh  (NEW)
  Keep https://deb.parrot.sh/parrot. Do not add deb.debian.org.
  If HTTPS/CA fails: install ca-certificates first, retry update.
  Create flux (uid 1000 free). Install sudo.
  Detection is card id `parrot`, never ID=debian.

3.4 setup_arch_family.sh  (REWRITE in place — VNC-era is wrong)
  Do NOT rewrite mirrors to Manjaro or x86_64 Arch.
  Keep ALARM: Server = http://mirror.archlinuxarm.org/$arch/$repo
  HoldPkg stays `pacman glibc`.
  pacman-key (empty gnupg — same landmine as Manjaro):
    mkdir -p /etc/pacman.d/gnupg
    pacman-key --init
    pacman-key --populate archlinuxarm
    pacman-key --populate archlinux || true
  If init hangs: temporary SigLevel = Never, -Sy keyrings, restore
  Required DatabaseOptional. NEVER leave Never as the permanent config.
  User `alarm` occupies uid 1000 — rename, do not create a second 1000:
    if id alarm && ! id flux; then
      usermod -l flux -d /home/flux -m alarm || { userdel -r alarm || true; }
    fi
    then _flux_ensure_user; echo flux:flux | chpasswd
    usermod -aG wheel,audio,video,input,users flux
  Packages:
    pacman -Sy --noconfirm
    pacman -S --noconfirm --needed \
      sudo \
      xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
      xfdesktop xfwm4 thunar \
      ttf-dejavu adwaita-icon-theme \
      mesa mesa-utils dbus xz
  SYSTEMD_OFFLINE=1. If CheckSpace lies on Android, comment it for the
  family run only (Manjaro lesson: bootstrap may have no sed — use POSIX).
  Chown /var/lib/pacman /var/cache/pacman. Fail closed on startxfce4.

3.5 flux_install.sh
  Add case arms ubuntu / kali / parrot / archlinux with pinned SHA +
  FAMILY_SCRIPT_NAME. Prepend flux_guest_common (already happens for
  non-debian/alpine family paths). Do not fall through to debian.

3.6 start_gui_chroot.sh
  Four new case arms → CHROOT_PATH + start_guest_gui.sh
    ubuntu|ubuntu_chroot   → /data/local/tmp/chrootUbuntu
    kali|kali_chroot       → /data/local/tmp/chrootKali
    parrot|parrot_chroot   → /data/local/tmp/chrootParrot
    archlinux|archlinux_chroot → /data/local/tmp/chrootArch
  Default * must stay Debian 13 — do not steal unknown hints.

Customization + HW: REUSE setup_customization_xfce.sh and
setup_hw_accel_guest.sh (already apt + pacman from DCM).
Dark theme defaults:
  GTK/xfwm Space-transparency
  Icons    Papirus-Dark
  Cursor   Vimix-white-cursors
  Wallpaper fluxlinux-dark.png
  Font     JetBrainsMono Nerd 10
  Scale    WindowScalingFactor=2
  Compositor off
zsh wrappers only if binary exists: apt-get / apt / pacman → sudo.
gpu_mode=virgl. Ubuntu 26 gtk/glycin (Fedora lesson): NEVER
GDK_DEBUG=no-glycin without a classic PNG loader.

==============================================================================
PHASE 4 — Unit tests (must be green before any device install)
==============================================================================

./gradlew :app:testIvarnaDebugUnitTest --no-daemon

Update / add:

  DistroInstallProfileTest
    eight new profiles; SHA 64-hex; no .gz asset; flatten names
    allRootfsProfiles grows by 4 (size 8 → 12)
    allInstallable grows by 8 (size 16 → 24)
    all eight isInstallable true
    DELETE or invert assertFalse(isInstallable("archlinux"))

  DistroRepositoryTest
    live count 16 → 24
    each UKPA pair proot-only / chroot-only
    stubs no longer comingSoon
    components = xfce4_desktop + hw_accel + customization
    no debian module scripts on the new cards

  TerminalComponentTest
    eight new ids map to proot / chroot
    invert unknownThrows for archlinux (now valid)

  TerminalShellCatalogTest
    sections UBUNTU / KALI / PARROT / ARCHLINUX SHELL × PROOT/CHROOT
    disabled reason when not installed
    extend TerminalShellAvailability constructor call sites

  ChrootPathsTest
    four new paths, distinct from Debian/Alpine/FVO/DCM

  BaseDesktopInstallPlanTest
    distroById + methodFor + profileFor for eight ids
    family paths do not contain debian/alpine (common prepends)

  GuestZshrcRepairTest
    resolveProotName("archlinux_chroot") == "archlinux"
    resolveProotName("kali_chroot") == "kali"
    resolveProotName("ubuntu_chroot") == "ubuntu"
    resolveProotName("parrot_chroot") == "parrot"
    apt + pacman wrappers for those names

  GuestRootfsShellTest
    a tree that only has kali-arm64/usr/bin/bash is NOT installed
    (guards flatten regression)

  OmzPokemonContractTest
    already mentions archlinux — keep that working

Do not weaken existing FVO/DCM/Alpine/Debian assertions.

==============================================================================
PHASE 5 — Release APK
==============================================================================

  ./gradlew :app:assembleIvarnaRelease --no-daemon
  adb -s Y5WWBMJVOZSK4HU8 install -r \
    app/build/outputs/apk/ivarna/release/app-ivarna-release.apk
  adb -s Y5WWBMJVOZSK4HU8 shell am start -n \
    com.ivarna.fluxlinux/.MainActivity

Never adb uninstall unless signature mismatch.
Do not start device installs until Kali/Parrot are flattened and Ubuntu is xz
and SHA is pinned in DistroInstallProfile.

==============================================================================
PHASE 6 — Device E2E (iteration order — do not skip)
==============================================================================

Test sequentially (storage: 8 new XFCE trees + existing 8). Uninstall a
finished CHROOT via in-app uninstall only if /data is exhausted. Never
uninstall the FluxLinux APK. Prefer keeping proot guests.

Order (fastest fail loops first):

  1. Ubuntu  proot  →  Ubuntu  chroot     ← start here
  2. Parrot  proot  →  Parrot  chroot
  3. Kali    proot  →  Kali    chroot     (flux vs kali uid)
  4. Arch    proot  →  Arch    chroot     (alarm rename + pacman-key)

If any cell fails: fix the owning layer (plan §4), rebuild release,
adb install -r, retest THAT path. Shared-script fix → smoke last green
sibling (e.g. Deepin proot Start + sudo -n id + one XFCE screenshot).

Automation (no vision-only pass — corroborate with adb):

  adb logcat -c
  adb logcat -s DesktopLauncher:* EmbeddedX11:* GUI:* AndroidRuntime:E
  adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml
  adb exec-out screencap -p > docs/plans/results/<name>.png
  adb shell dumpsys activity activities | rg -i 'termux.x11|fluxlinux|mResumed'

UI: Distros → card → Install (or Home if already onboarding). Theme Dark.
Wait for family + customization OK. Then Terminal tab + Home Start.

For EACH of the 8 rows, every cell must pass:

INSTALL
  I1  Distros card is LIVE (not Coming Soon). Wizard completes.
      log: family + customization OK (not "Icons Archive Missing").
  I2  startxfce4 present. Chroot has .flux_configured.
  I3  Repos:
        Ubuntu : ports.ubuntu.com in sources; NO archive.ubuntu.com
        Kali   : kali-rolling only; no deb.debian.org
        Parrot : deb.parrot.sh only; no deb.debian.org
        Arch   : ALARM $arch/$repo ; NOT Manjaro arm-stable

TERMINAL
  T1  Terminal → User : flux prompt, zsh, $ZSH_THEME=agnosterzak,
      command -v pokemon-colorscripts
  T2  sudo -n id → uid=0
  T3  PM works, no EACCES on locks:
        Ubuntu/Kali/Parrot : sudo apt-get update  (or apt-cache policy)
        Arch               : sudo pacman -Sy or -Q
  T4  id -un is flux (NOT kali, NOT alarm). grep ^flux /etc/passwd
  T5  Terminal Root card opens; can id

DISPLAY — visual contract is NON-NEGOTIABLE (user: correct font, XFCE
render, correct bg, all icons). Do not mark D3/D4 pass from a filename.
Read the PNG. Also dump xfconf.

  D1  Home → Start XFCE : Pulse/X PID/startxfce4 in gui_desktop.log
  D2  Embedded X11 : com.termux.x11.MainActivity resumed
  D3  Paint : screenshot shows xfce4-panel + wallpaper. NO failsafe.
      Processes: xfce4-session, xfwm4, xfce4-panel, xfdesktop.
      FAIL if xterm-only failsafe or black screen after 60s healthy log.
  D4  Theme / icons / font / wallpaper (Dark):
        xsettings.xml
          ThemeName=Space-transparency
          IconThemeName=Papirus-Dark
          CursorThemeName=Vimix-white-cursors
          FontName contains JetBrainsMono (or JetBrains Mono)
          WindowScalingFactor=2
        xfce4-desktop.xml last-image …/fluxlinux-dark.png
          (NOT xfce-verticals.png, NOT a solid color)
        xfce4-terminal.xml font JetBrainsMono Nerd 10 (or 10pt)
        Applications menu: Settings / Accessories / Internet icons
          present — NO ⊘ / image-missing on category icons
        If stub Papirus lacks categories, fix the shared customization
        (seed categories from papirus-xfce-categories.tar.xz or install
        a real theme) and sibling-smoke last green path. Do NOT claim
        D4 pass with ⊘ menus. Adwaita fallback is only acceptable if
        documented AND category icons still render.
  D5  Stop → card back to Start; X11 idle; xfce processes gone

NEGATIVE
  N1  Kali/Parrot: dpkg -l has no newly-installed kali-desktop-xfce
      / parrot-tools / parrot-interface metas
  N2  OpenBSD: no card, no asset

Per-path screenshot set (save under docs/plans/results/):

  <id>_01_distros.png          live card, not Coming Soon
  <id>_02_installing.png
  <id>_03_install_done.png
  <id>_04_home_installed.png
  <id>_shell_user.png          flux + zsh + pokemon
  <id>_shell_root.png
  <id>_pm.png                  apt-get update / pacman -Q
  <id>_gui_log.png
  <id>_xfce_pass.png           FULL desktop: panel + wallpaper + icons
  <id>_xfce_theme.png          close-up of panel + Applications menu
  <id>_xfce_terminal.png       xfce4-terminal showing Nerd font glyphs
  <id>_xfce_menu.png           Applications menu — no ⊘
  <id>_stopped.png

<id> = ubuntu_proot | ubuntu_chroot | kali_proot | kali_chroot |
       parrot_proot | parrot_chroot | archlinux_proot | archlinux_chroot

Also write the eight device reports (template = deepin-proot-device-report.md):

  docs/plans/results/ubuntu-proot-device-report.md
  docs/plans/results/ubuntu-chroot-device-report.md
  docs/plans/results/kali-proot-device-report.md
  docs/plans/results/kali-chroot-device-report.md
  docs/plans/results/parrot-proot-device-report.md
  docs/plans/results/parrot-chroot-device-report.md
  docs/plans/results/archlinux-proot-device-report.md
  docs/plans/results/archlinux-chroot-device-report.md

Each report: device, APK path/version, I1–I3 T1–T5 D1–D5 N1 matrix with
PASS/FAIL + evidence filenames + log excerpts + bugs found + fixes +
retest. Do not invent PASS.

Guest verify snippets (run inside the guest User shell or via
proot-distro/chroot login as flux):

  id -un                              # flux
  sudo -n id                          # uid=0
  echo $ZSH_THEME                     # agnosterzak
  command -v pokemon-colorscripts
  command -v startxfce4
  # Ubuntu
  grep -R ports.ubuntu.com /etc/apt ; grep -R archive.ubuntu.com /etc/apt && echo FAIL
  # Kali
  grep -R kali-rolling /etc/apt ; id kali ; id -u flux   # flux=1000
  # Parrot
  grep -R deb.parrot.sh /etc/apt ; grep -R deb.debian.org /etc/apt && echo FAIL
  # Arch
  grep -v '^#' /etc/pacman.d/mirrorlist | head
  id alarm && echo FAIL_ALARM_STILL_EXISTS
  # Desktop (after Start)
  grep -E 'ThemeName|IconThemeName|CursorThemeName|FontName|WindowScaling' \
    ~/.config/xfce4/xfconf/xfce-perchannel-xml/xsettings.xml
  grep last-image ~/.config/xfce4/xfconf/xfce-perchannel-xml/xfce4-desktop.xml
  ls /usr/share/icons/Papirus-Dark/index.theme
  ls /usr/share/themes/Space-transparency

==============================================================================
PHASE 7 — Existing-8 no-breakage
==============================================================================

After ANY edit to flux_guest_common.sh, setup_customization_xfce.sh, or
setup_hw_accel_guest.sh:

  1. Keep inlined family copies in sync (Deepin/Chimera/Manjaro + UKPA).
  2. Sibling smoke last green path (Deepin proot or Manjaro proot):
       Terminal User: sudo -n id
       Home Start → screenshot panel+wallpaper still Space / Papirus
       Stop
  3. Do not reinstall all 16 existing cards.

If Distros / Home / Terminal grid layout breaks (cards missing, Coming Soon
on a previously live card), that is a P0 — fix before continuing UKPA E2E.

==============================================================================
LANDMINES (plan §11)
==============================================================================

- aapt2: never ship Ubuntu as .tar.gz in assets
- Nested Kali/Parrot: flatten or install looks empty
- Ubuntu mirrors: ports.ubuntu.com/ubuntu-ports only
- Kali uid 100000: flux is uid 1000; do not adopt NH uid
- Parrot ID=debian: card id parrot is SSOT
- Arch alarm: rename/replace; one uid 1000
- Arch gnupg: pacman-key --init required
- Arch vs Manjaro: different cards, mirrors, HoldPkg
- Pentest metas: out of scope
- OpenBSD img: out of scope
- Home chown: same proot app-uid rule as Alpine/FVO
- glycin: Ubuntu 26 gtk — Fedora lesson
- Ivarna only
- Host tar xz Permission denied is a known non-blocker; guest tar -xJf
  must work (install xz-utils / xz in family)
- First X SIGSYS / Pulse: known; retry is OK; do not chase unless D3 blocks

==============================================================================
DOCS AFTER DEVICE PASS
==============================================================================

- docs/distro/{ubuntu,kali,parrot,archlinux}.md  (shape of docs/distro/fedora.md)
- Link from docs/distro/README.md
- Flip plan status line to: DEVICE PASS — <date>
- Check every box in plan §14

==============================================================================
DEFINITION OF DONE
==============================================================================

- [ ] OpenBSD not packaged
- [ ] Four xz assets pinned (Ubuntu/Kali/Parrot SHA filled into plan §1.2
      and DistroInstallProfile). Kali/Parrot archives flat
- [ ] Eight cards live in Ivarna release (not coming soon)
- [ ] Unit tests in plan §9.1 green
- [ ] Packaging checks P1–P5 green
- [ ] All 8 device rows: I1–I3, T1–T5, D1–D5, N1 green
- [ ] D4 screenshots show Space theme + fluxlinux-dark wallpaper +
      Papirus icons (no ⊘) + JetBrainsMono Nerd in xfce4-terminal
- [ ] Reports + XFCE screenshots under docs/plans/results/
- [ ] Existing 8 cards still live; sibling smoke after shared edits
- [ ] Plan status flipped to DEVICE PASS
- [ ] ./gradlew --stop

Until every box is checked, the work is not finished.
```
