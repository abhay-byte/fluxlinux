# Plan: Ubuntu, Kali, Parrot, and Arch (proot + chroot)

**Date:** 2026-08-13  
**Status:** DEVICE PASS — 2026-08-14. Eight new cards (Ubuntu/Kali/Parrot/Arch × proot+chroot) live in Ivarna release. Arch family needed `DisableSandbox` (pacman 7 alpm/Landlock on Android).  
**Scope:** First-class FluxLinux guests for **Ubuntu 26.04 LTS (Resolute)**, **Kali Rolling 2026.2** (NetHunter-minimal rootfs, treated as Kali), **Parrot Security 7.2**, and **Arch Linux ARM** (already-slimmed xz) — same product shape as Alpine / Debian / FVO / Deepin-Chimera-Manjaro: install → internal terminal → XFCE on the **embedded Termux:X11** display.

**Feasibility (inspected 2026-08-13):**

| Artifact | Verdict |
|----------|---------|
| `resolute-base-arm64.tar.gz` | **Viable** — Ubuntu 26.04 arm64 base (apt, glibc). Must rewrite mirrors to `ports.ubuntu.com`. |
| `kali-nethunter-rootfs-minimal-arm64.tar.xz` | **Viable** — Kali 2026.2, nested under `kali-arm64/`. Re-pack flat. Do not install NetHunter Magisk/ pentest metas. |
| `parrot-arm64.tar.xz` | **Viable** — Parrot 7.2, nested under `parrot-arm64/`. Re-pack flat. Keep `deb.parrot.sh`. |
| `assets/rootfs/archlinux_arm_rootfs.tar.xz` | **Viable** — slim ALARM userspace (111 MiB). See [`archlinux-alarm-slim-rootfs.md`](./archlinux-alarm-slim-rootfs.md). |
| `openbsd_miniroot79.img` | **Not feasible** — DOS/MBR disk image (partition type `0xa6` = OpenBSD), not a Linux rootfs. No glibc, no `pacman`/`apt`, no XFCE. Out of this plan. |

**Not in this plan:** OpenBSD. KDE. Kali/Parrot pentest tool metas (`kali-linux-default`, `kali-desktop-xfce`, `parrot-tools`, `parrot-interface`). Debian-style appdev/webdev modules. Debug APKs. Multi-arch. Changing Deepin/Chimera/Manjaro except shared helper extensions already required by those guests.

**Device policy:** `:app:assembleIvarnaRelease` only + `adb install -r`. No APK uninstall unless signature mismatch. Test with a subagent; fix and retest until all **8** paths pass (4 distros × proot + chroot). Do not stop on the first green path.

**References:** [`docs/scripts_reference.md`](../scripts_reference.md), [`docs/scripts_flowchart.md`](../scripts_flowchart.md), [`docs/adding_new_distro.md`](../adding_new_distro.md), [`docs/plan/fedora-void-opensuse.md`](./fedora-void-opensuse.md), [`docs/plan/deepin-chimera-manjaro.md`](./deepin-chimera-manjaro.md), [`docs/plan/archlinux-alarm-slim-rootfs.md`](./archlinux-alarm-slim-rootfs.md).

**How to use this file:** this is the implementation contract. Paste-ready iteration-1 worker prompt (implement + device-test all 8 new paths, Ubuntu first): [`docs/plans/worker-prompt-ubuntu-kali-parrot-arch-iter1.md`](../plans/worker-prompt-ubuntu-kali-parrot-arch-iter1.md). Existing 8 guests (Alpine / Debian / FVO / DCM) stay live — do not re-install them unless a shared helper changes.

---

## 0. Product decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Distros | Ubuntu 26.04, Kali 2026.2, Parrot 7.2, Arch Linux ARM | Four viable Linux archives; OpenBSD rejected |
| Cards | Eight installable cards (proot + rooted chroot each) | Same split as `alpine` / `alpine_chroot` |
| Desktop | XFCE4 only | Same contract as FVO / DCM |
| Modules | None | XFCE + theme/icons/font/xfconf + HW accel only |
| Kali identity | Kali Rolling guest, **not** NetHunter product | Rootfs happens to be NH-minimal; Flux is not Magisk NH |
| Parrot identity | Parrot Security 7.2 via `deb.parrot.sh` | `os-release` `ID=debian` is a lie — never key logic off it |
| Arch identity | Distinct from Manjaro | ALARM repos (`$arch/$repo`), not Manjaro `arm-stable` |
| Package managers | `apt-get` (Ubuntu/Kali/Parrot), `pacman` (Arch) | Native |
| libc | All four **glibc** | No musl surprises |
| Default user | `flux` / `flux`, NOPASSWD sudo | Parity. See §6 for Kali `kali` uid 100000 and Arch `alarm` uid 1000 |
| Login shell | zsh after customization | Shared Flux `.zshrc` + OMZ |
| GUI | Shared `start_gui.sh` + generic `start_guest_gui.sh` | Already env-driven |
| HW accel | Mesa in family + `hw_accel` component | VirGL; no Turnip tarball |
| Rootfs shipping | Flat `.tar.xz` in `assets/rootfs/` | aapt2-safe; Kali/Parrot must lose wrapper dir |
| Kotlin SSOT | `DistroInstallProfile` | No new `when` trees keyed on string literals in UI |
| Scripts | One family script per distro + shared customization/hw/chroot | High cohesion, low coupling |
| Existing stubs | Split + flip `comingSoon` | `ubuntu`, `kali`, `parrot`, `archlinux` already exist as stubs |

---

## 1. Rootfs identity (inspected 2026-08-13)

### 1.1 Source files

| Distro | Source | Bytes | SHA-256 | Notes |
|--------|--------|------:|---------|-------|
| Ubuntu | `/home/abhaybyte/Downloads/resolute-base-arm64.tar.gz` | 35 094 784 | `e9dfcbf8763371965597edcb351eaa7daacfb0805bb9ae9c8d6479a0b25bf928` | gzip; **not** aapt2-safe; flat layout |
| Kali | `/home/abhaybyte/Downloads/kali-nethunter-rootfs-minimal-arm64.tar.xz` | 137 313 840 | `d6403a5da175df325611d23af4b92330856059c45454eced7f4cdf3ca6df2e4e` | xz; **nested** `kali-arm64/` |
| Parrot | `/home/abhaybyte/Downloads/parrot-arm64.tar.xz` | 111 838 320 | `8a486c8635918de6cebc3b339265c4cea73cb9d73f709d56d98e487769f78582` | xz; **nested** `parrot-arm64/` |
| Arch | `assets/rootfs/archlinux_arm_rootfs.tar.xz` | 116 277 544 | `40209ef6318d3aad732299d46ce224c6a0ecded80b6f8091f5e38b40fa031d75` | already flat xz; slim done |
| OpenBSD | `/home/abhaybyte/Downloads/openbsd_miniroot79.img` | ~43 MiB | `f57a306dffaf8f0f1f489d07ee408cb1fd4e6ab7d3263c73ba1cd1ea6314cf6f` | MBR image — **do not ship** |

### 1.2 Packaged names

| Distro | Packaged asset | How | Min-size |
|--------|----------------|-----|----------|
| Ubuntu | `rootfs/ubuntu_26.04_rootfs.tar.xz` | `gzip -dc … \| xz -T0 -9` | ≥ 15 MiB |
| Kali | `rootfs/kali_2026_2_rootfs.tar.xz` | extract `kali-arm64/` then `tar -C that -c \| xz` | ≥ 40 MiB |
| Parrot | `rootfs/parrot_7.2_rootfs.tar.xz` | extract `parrot-arm64/` then recompress | ≥ 30 MiB |
| Arch | `rootfs/archlinux_arm_rootfs.tar.xz` | already in tree | ≥ 40 MiB (≤ 250 MiB) |

**Pinned packaged xz** (also in `DistroInstallProfile`):

| Asset | SHA-256 | Bytes |
|-------|---------|------:|
| `ubuntu_26.04_rootfs.tar.xz` | `e648a5302dd273c476e5658e652f88d1e66ece69b487431521c5caef4b960efc` | 20 734 792 |
| `kali_2026_2_rootfs.tar.xz` | `01c48a29ebb543954ef200e766076a143cf42744760d7ccdc31683a19f670689` | 123 244 844 |
| `parrot_7.2_rootfs.tar.xz` | `49f4c2899ef9574cc3b0d9aaa6eaff38c4b32a9ac1abea2faec73cfbaf8094d4` | 111 851 420 |
| `archlinux_arm_rootfs.tar.xz` | `40209ef6318d3aad732299d46ce224c6a0ecded80b6f8091f5e38b40fa031d75` | 116 277 544 |

**Kali / Parrot flatten (mandatory).** `proot-distro install archive.tar.xz` and `setup_guest_chroot.sh` expect `./usr/bin/bash` at archive root. A wrapper directory installs an empty-looking guest.

```sh
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
test ! -e /tmp/placeholder
# archive root must list usr/, etc/ — not kali-arm64/

# Parrot: drop parrot-arm64/ prefix
rm -rf /tmp/ukpa-repack/parrot && mkdir -p /tmp/ukpa-repack/parrot
tar -xJf /home/abhaybyte/Downloads/parrot-arm64.tar.xz -C /tmp/ukpa-repack/parrot
test -x /tmp/ukpa-repack/parrot/parrot-arm64/usr/bin/bash
tar -C /tmp/ukpa-repack/parrot/parrot-arm64 -cf - . \
  | xz -T0 -9 > assets/rootfs/parrot_7.2_rootfs.tar.xz
```

Pin SHA-256 of the **packaged** Ubuntu/Kali/Parrot xz files in this plan §1.2 and in `DistroInstallProfile` **before** the first device install. Arch SHA is already pinned (`40209ef6…`).

Spot-check each packaged xz:

```sh
tar -tJf assets/rootfs/ubuntu_26.04_rootfs.tar.xz | rg '^(./)?usr/bin/bash$'
tar -tJf assets/rootfs/kali_2026_2_rootfs.tar.xz   | rg 'kali-arm64'   # must be empty
tar -tJf assets/rootfs/parrot_7.2_rootfs.tar.xz    | rg 'parrot-arm64' # must be empty
tar -tJf assets/rootfs/archlinux_arm_rootfs.tar.xz | rg 'usr/bin/pacman'
```

### 1.3 `os-release` / repo facts

**Ubuntu** (`usr/lib/os-release`):

```
PRETTY_NAME="Ubuntu 26.04 LTS"
ID=ubuntu
ID_LIKE=debian
VERSION_ID="26.04"
VERSION_CODENAME=resolute
```

87 packages. `apt`, `bash`, `useradd`. **No** `sudo`, **no** XFCE. Groups include `sudo`/`audio`/`video`.  
`etc/apt/sources.list.d/ubuntu.sources` (DEB822) currently:

```
URIs: http://archive.ubuntu.com/ubuntu/
URIs: http://security.ubuntu.com/ubuntu/
Components: main universe restricted multiverse
Suites: resolute resolute-updates resolute-backports / resolute-security
```

Those URIs are **amd64**. This rootfs is **arm64**. Family script **must** rewrite both to:

```
http://ports.ubuntu.com/ubuntu-ports
```

Universe is already listed — XFCE lives there. Do not add `deb.debian.org`.

Also present: `coreutils-from-uutils` **and** `gnu-coreutils`. Family scripts stay POSIX `sh` + GNU-flag fallbacks already in `flux_guest_common.sh`.

**Kali** (`kali-arm64/etc/os-release`):

```
PRETTY_NAME="Kali GNU/Linux Rolling"
ID=kali
ID_LIKE=debian
VERSION_ID="2026.2"
VERSION_CODENAME=kali-rolling
```

`kali.sources`: `http://http.kali.org/kali/` suite `kali-rolling` (`main contrib non-free non-free-firmware`), signed by `/usr/share/keyrings/kali-archive-keyring.gpg`.  
267 packages. Has `apt`, `bash`, `sudo`, `dbus`, `kali-archive-keyring`, `kali-defaults`, `kali-nethunter-core`, `nethunter-utils`. **No** XFCE.  
Root shell: `/usr/bin/zsh`. Extra user **`kali` uid 100000** (NetHunter Android uid). `_apt` gid 3004. Hostname `atlas`.  
Treat `kali-nethunter-core` as leftover metadata — do not start NH daemons; do not install `kali-linux-large` / `kali-desktop-xfce`.

**Parrot** (`parrot-arm64/etc/os-release`):

```
PRETTY_NAME="Parrot Security 7.2 (echo)"
NAME="Parrot Security"
VERSION_ID="7.2"
VERSION_CODENAME=echo
ID=debian          # ← do not trust this
```

`debian_version` = `13.1` (Trixie-era).  
`sources.list`: `deb https://deb.parrot.sh/parrot parrot main contrib non-free non-free-firmware`.  
Keys: `parrot-archive-keyring.gpg` + Debian archive keys.  
150 packages. `apt`, `bash`, `useradd`. **No** `sudo`, **no** XFCE.  
Never add Debian `trixie`/`sid` as a second suite (pin hell).

**Arch** (`usr/lib/os-release`):

```
NAME="Arch Linux ARM"
ID=archarm
ID_LIKE=arch
BUILD_ID=rolling
```

149 local pkgs after slim. `pacman`, `bash`, `useradd`/`shadow`. **No** `sudo`. Firmware/kernel trees gone. **Empty** `/etc/pacman.d/gnupg`.  
User **`alarm` uid 1000** already exists. Mirrorlist is ALARM (`http://mirror.archlinuxarm.org/$arch/$repo`). `Architecture = aarch64`. `HoldPkg = pacman glibc`. `DownloadUser = alpm`.

---

## 2. Cards and paths

Replace coming-soon stubs. **Split** dual-mode cards. Do not leave `prootSupported && chrootSupported` on one id.

| Card id | UI name | Method | proot name | Chroot path | Color | Icon |
|---------|---------|--------|------------|-------------|-------|------|
| `ubuntu` | Ubuntu | proot | `ubuntu` | — | `0xFFE95420` | `R.drawable.distro_ubuntu` |
| `ubuntu_chroot` | Ubuntu (Rooted) | chroot | — | `/data/local/tmp/chrootUbuntu` | same | same |
| `kali` | Kali | proot | `kali` | — | `0xFF367BF5` | `R.drawable.distro_kali` |
| `kali_chroot` | Kali (Rooted) | chroot | — | `/data/local/tmp/chrootKali` | same | same |
| `parrot` | Parrot | proot | `parrot` | — | `0xFF00D9FF` | `R.drawable.distro_parrot` |
| `parrot_chroot` | Parrot (Rooted) | chroot | — | `/data/local/tmp/chrootParrot` | same | same |
| `archlinux` | Arch | proot | `archlinux` | — | `0xFF1793D1` | `R.drawable.distro_arch` |
| `archlinux_chroot` | Arch (Rooted) | chroot | — | `/data/local/tmp/chrootArch` | same | same |

Descriptions (short): Ubuntu 26.04 / Kali Rolling / Parrot 7.2 / Arch Linux ARM + “with XFCE4 (proot)” / “chroot environment (Requires Root).”

Icons already exist. No new drawables.

---

## 3. Components (required only)

Same three ids as FVO / DCM on every card:

| id | Script | Role |
|----|--------|------|
| `xfce4_desktop` | `setup_<family>_family.sh` | DNS, repos, base pkgs, XFCE, dbus, user `flux`, sudo, Mesa, `gpu_mode=virgl` |
| `hw_accel` | `common/setup/setup_hw_accel_guest.sh` | Extra Mesa/GL, `gpu-launch` |
| `customization` | `common/setup/setup_customization_xfce.sh` | Space theme, Papirus, Vimix, JetBrainsMono Nerd, xfconf, zsh |

Onboarding = family + customization. After family success, mark `hw_accel` installed when the profile has `hwAccelScript`. Distro Settings can re-run hw.

Do **not** attach Debian module scripts to Ubuntu/Kali/Parrot.

---

## 4. Cohesion / coupling (clean code)

| Layer | Owns | Must not own |
|-------|------|----------------|
| `DistroInstallProfile` | card id, rootfs name/SHA/min, family/custom/hw paths, chroot path, display name | UI strings beyond `displayName`; package names |
| `DistroRepository` | live cards, colors, icons, component **ids** pointing at profile scripts | SHA, chroot paths, extract logic |
| `ChrootPaths` | absolute chroot dirs + `pathForDistro` | scripts |
| Family `setup_*_family.sh` | **only** package names + repo rules for that distro | theme XML, X11 launch |
| `flux_guest_common.sh` | user/sudo/dns/tmp/dbus/home/gpu_mode/stat | apt/pacman CLI flags |
| `setup_customization_xfce.sh` | branding (PM-agnostic) | distro repos |
| `setup_hw_accel_guest.sh` | Mesa extras + `gpu_mode` | desktop session |
| `setup_guest_chroot.sh` / `start_guest_gui.sh` | extract + XFCE launch via env | distro `when` trees |
| `flux_install.sh` | one `case` arm per proot name | package lists |
| `OnboardingInstallRunner` | phases via profile | hardcoded ubuntu/kali strings |

Callers: `DistroInstallProfile.forId(id)` / `require` / `methodFor`. Adding the ninth guest later must be **one profile + one family script + one catalog row**, not six new `when` branches in UI.

Prefer extending `TerminalShellAvailability` to a `Map<String, Boolean>` keyed by card id. If that rewrite is too large this pass, add eight booleans the same way Deepin was added — but do not leave User/Root hidden for the new guests.

---

## 5. Script map

### Guest

```
scripts/ubuntu/common/setup/setup_ubuntu_family.sh
scripts/kali/common/setup/setup_kali_family.sh
scripts/parrot/common/setup/setup_parrot_family.sh
scripts/arch/common/setup/setup_arch_family.sh   # REWRITE in place
scripts/common/setup/
  flux_guest_common.sh            # already used by DCM; keep portable
  setup_customization_xfce.sh     # already has apt-get + pacman from DCM
  setup_hw_accel_guest.sh         # already has apt + pacman from DCM
```

Do **not** reuse:

| Script | Why not |
|--------|---------|
| `setup_debian_family.sh` for Ubuntu/Kali/Parrot | Debian repos, Debian-only assumptions, extra modules |
| Current `setup_arch_family.sh` | VNC-era; `xfce4` metapackage; ALARM ranker OK-ish but no flux user contract, no Mesa, no `startxfce4` fail-closed |
| `setup_manjaro_family.sh` for Arch | Manjaro `arm-stable` mirrors + `manjaro-system` HoldPkg |

### Host

| Script | Change |
|--------|--------|
| `debian/proot/setup/flux_install.sh` | `case` `ubuntu` / `kali` / `parrot` / `archlinux`. Prepend `flux_guest_common.sh` (not debian/alpine). |
| `start_gui.sh` / `stop_gui.sh` | No change |
| `setup_guest_chroot.sh` | No new clone; env `FLUX_CHROOT` |
| `start_gui_chroot.sh` | Four dispatch arms → new paths + `start_guest_gui.sh` |
| `HostScriptDeployer` | Four family scripts |

---

## 6. Family setup (per distro)

Shared contract via `flux_guest_common.sh` (same as FVO/DCM):

1. Root only. POSIX `#!/bin/sh`.  
2. Sticky `/tmp` `/var/tmp`; unset `PROOT_TMP_DIR`.  
3. `resolv.conf` if empty.  
4. User `flux` / password `flux`.  
5. NOPASSWD sudo + `@includedir /etc/sudoers.d`.  
6. dbus `machine-id`.  
7. Home ownership: proot → owner of `/home`; chroot (home owner 0) → `flux:flux`.  
8. `/etc/fluxlinux/gpu_mode` = `virgl`.  
9. VNC `xstartup` → `startxfce4`.  
10. Chown PM caches to owner of `/etc`.  
11. Fail closed if `startxfce4` missing.

Apt guests share this prefix (do **not** copy-paste three slightly different sandboxes — one `_flux_apt_prep` in common or a tiny `flux_apt_common.sh` sourced by the three families):

```
export DEBIAN_FRONTEND=noninteractive
export DEBCONF_NONINTERACTIVE_SEEN=true
export SYSTEMD_OFFLINE=1
mkdir -p /etc/apt/apt.conf.d
printf 'APT::Sandbox::User "root";\nDPkg::Use-Pty "false";\n' \
  > /etc/apt/apt.conf.d/99flux-nosandbox
```

Targeted XFCE (all three apt families — **not** `apt-get install xfce4` first):

```
xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
xfdesktop4 xfwm4 thunar \
adwaita-icon-theme fonts-dejavu-core \
dbus dbus-x11 \
libgl1-mesa-dri libegl1 mesa-utils \
sudo passwd ca-certificates curl wget unzip tar
```

Fallback to `xfce4` metapackage **only** if `startxfce4` is still missing. Never `kali-desktop-xfce` / `parrot-interface` / `ubuntu-desktop`.

### 6.1 Ubuntu — `setup_ubuntu_family.sh`

1. Rewrite `ubuntu.sources` URIs:
   - `http://archive.ubuntu.com/ubuntu/` → `http://ports.ubuntu.com/ubuntu-ports`
   - `http://security.ubuntu.com/ubuntu/` → `http://ports.ubuntu.com/ubuntu-ports`
   - Keep suites `resolute*` and components `main universe` (restricted/multiverse may stay).
2. `apt-get update` (fail closed).  
3. Install targeted list.  
4. `_flux_ensure_user` (uid 1000 is free).  
5. Mesa already in the list; `_flux_write_gpu_mode virgl`.  
6. `_flux_require_startxfce4`.

Do not `do-release-upgrade`. Do not add PPAs.

### 6.2 Kali — `setup_kali_family.sh`

1. Keep `kali-rolling` sources. Do not add Debian.  
2. `apt-get update`.  
3. Targeted XFCE only. **Forbidden:** `kali-linux-default`, `kali-linux-large`, `kali-tools-*`, `kali-desktop-*`.  
4. User:
   - Leave `kali` (uid 100000) alone or lock it (`usermod -L kali`) so Terminal/Home never default to it.
   - Create **`flux` at uid 1000** (100000 is not free-for-1000; 1000 should be free).
   - Flux is the only user Start / customization / zsh repair touch.
5. Root’s login shell is already zsh — fine. Flux still gets zsh from customization.  
6. Ignore `kali-nethunter-core` / `nethunter-utils` (no Magisk, no `nh` wrapper as the Flux CLI).  
7. Hostname may stay `atlas` or become `kali` — do not block on it.

### 6.3 Parrot — `setup_parrot_family.sh`

1. Keep `https://deb.parrot.sh/parrot`. Do not add `deb.debian.org`.  
2. `apt-get update`. If HTTPS/CA fails, install/keep `ca-certificates` first then retry.  
3. Targeted XFCE. **Forbidden:** `parrot-tools`, `parrot-interface`, `parrot-desktop-*`.  
4. Create `flux` (uid 1000 free). Install `sudo`.  
5. Detection: card id `parrot`, **not** `ID=debian` from os-release.

### 6.4 Arch — `setup_arch_family.sh` (rewrite)

Do **not** rewrite mirrors to Manjaro or x86_64 Arch.

**pacman-key** (empty gnupg — same landmine as Manjaro):

```
mkdir -p /etc/pacman.d/gnupg
pacman-key --init
pacman-key --populate archlinuxarm
pacman-key --populate archlinux || true
```

If init hangs: temporary `SigLevel = Never`, `-Sy` keyrings, restore `Required DatabaseOptional`. Never leave `Never` as the permanent config.

**User `alarm` occupies uid 1000.** Required sequence:

```
# Prefer rename so home/files stay
if id alarm >/dev/null 2>&1 && ! id flux >/dev/null 2>&1; then
  usermod -l flux -d /home/flux -m alarm 2>/dev/null \
    || { userdel -r alarm 2>/dev/null || true; }
fi
# then _flux_ensure_user (creates flux uid 1000 if still missing)
echo "flux:flux" | chpasswd
usermod -aG wheel,audio,video,input,users flux
```

Do **not** create a second uid-1000 user.

Packages:

```
pacman -Sy --noconfirm
pacman -S --noconfirm --needed \
  sudo \
  xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
  xfdesktop xfwm4 thunar \
  ttf-dejavu adwaita-icon-theme \
  mesa mesa-utils dbus
```

`SYSTEMD_OFFLINE=1`. If `CheckSpace` lies on Android, comment it for the family run only. `HoldPkg` stays `pacman glibc`. Chown `/var/lib/pacman` `/var/cache/pacman`. Fail closed on `startxfce4`.

---

## 7. Customization + HW accel

Reuse DCM-extended shared scripts.

| Item | Dark | Light |
|------|------|-------|
| GTK / xfwm | Space-transparency | Space-light |
| Icons | Papirus-Dark | Papirus-Dark |
| Cursor | Vimix-white-cursors | Vimix-cursors |
| Wallpaper | fluxlinux-dark.png | fluxlinux-light.png |
| Font | JetBrainsMono Nerd 10 | same |
| Scale | WindowScalingFactor=2 | same |
| Compositor | off | off |

zsh PM wrappers (only if binary exists):

```
apt-get() { command sudo apt-get "$@"; }
apt()     { command sudo apt "$@"; }
pacman()  { command sudo pacman "$@"; }
```

`GuestZshrcRepair.isGlibcGuest` += `ubuntu`, `kali`, `parrot`, `archlinux`.  
`resolveProotName("ubuntu_chroot") == "ubuntu"`, `"kali_chroot" == "kali"`, `"archlinux_chroot" == "archlinux"` (suffix strip already works; `archlinux` must **not** become `arch`).

HW packages:

| Distro | Extra / fallback |
|--------|------------------|
| Ubuntu / Kali / Parrot | `mesa-utils libgl1-mesa-dri libegl1` (Ubuntu 26 may use `libegl1` without `-mesa` suffix) |
| Arch | `mesa mesa-utils` |

`gpu_mode=virgl`. Watch Ubuntu 26 gtk/glycin (Fedora lesson): never `GDK_DEBUG=no-glycin` without a classic PNG loader.

---

## 8. Kotlin / Gradle

### `SupportedDistro`

Already: `UBUNTU`, `KALI`, `ARCH`.  
Add:

```
PARROT(id="parrot", family=DEBIAN, pm=APT, release=ROLLING)
```

### `DistroInstallProfile`

Eight profiles. Shared `XFCE_CUSTOM` + `HW_ACCEL_GUEST`. Generic chroot trio.  
`allRootfsProfiles()`: previous set + Ubuntu + Kali + Parrot + Arch (dedupe by filename → **+4**).  
`isInstallable` true for all eight ids.

### `ChrootPaths`

```
UBUNTU_CHROOT_PATH = /data/local/tmp/chrootUbuntu
KALI_CHROOT_PATH   = /data/local/tmp/chrootKali
PARROT_CHROOT_PATH = /data/local/tmp/chrootParrot
ARCH_CHROOT_PATH   = /data/local/tmp/chrootArch
```

### `DistroRepository`

Split stubs → eight live cards. Components via the same `glibcXfceComponents(familyScript)` helper already used for Fedora/Deepin.

### `TerminalShellCatalog` / `TerminalLauncher`

Probe `ubuntu`/`kali`/`parrot`/`archlinux` proot names and the four chroot paths. Labels/icons from the table in §2.

### `GuestZshrcRepair`

Glibc PM wrappers for apt + pacman. Do **not** treat Parrot as Debian-the-card.  
`resolveProotName`: if someone used `removeSuffix("_chroot")` only, `archlinux` stays `archlinux`. Add a unit test so a future `removePrefix("arch")` cannot land.

### `Onboarding*` / `BaseDesktopInstallPlan`

Profile-driven. Family paths do not contain `debian`/`alpine`, so `flux_guest_common.sh` is prepended — good.

### Gradle `stageHostRootfs`

Copy the four xz files (Arch already there; three new). Inputs/outputs next to DCM.

---

## 9. Tests (unit) — every category

### 9.1 Profile / catalog

| File | Assert |
|------|--------|
| `DistroInstallProfileTest` | Eight new profiles; SHA 64-hex; **no** `.gz` asset; flatten names; `allRootfsProfiles` grows by 4; all eight `isInstallable` |
| `DistroRepositoryTest` | Live count previous + 8; each pair proot-only / chroot-only; stubs no longer `comingSoon` |
| `TerminalComponentTest` | All eight ids map to `proot` / `chroot` |
| `TerminalShellCatalogTest` | Sections `UBUNTU SHELL` / `KALI SHELL` / `PARROT SHELL` / `ARCH SHELL` (or `ARCHLINUX SHELL` — pick one string and test it) × PROOT/CHROOT; disabled reason when not installed |
| `ChrootPathsTest` | Four new paths, distinct from Debian/Alpine/FVO/DCM |
| `BaseDesktopInstallPlanTest` | `distroById` + `methodFor` + `profileFor` for eight ids |
| `GuestZshrcRepairTest` | `resolveProotName("archlinux_chroot") == "archlinux"`; `kali_chroot` → `kali`; apt + pacman wrappers |
| `GuestRootfsShellTest` | Flat `usr/bin/bash` counts; a tree that only has `kali-arm64/usr/bin/bash` is **not** installed (guards flatten regression) |

### 9.2 Packaging / extract (host, before device)

| # | Category | Pass |
|---|----------|------|
| P1 | Ubuntu xz | SHA recorded; `tar -tJf` has `usr/bin/bash`, no `.gz` in assets |
| P2 | Kali flatten | no `kali-arm64/` prefix; `usr/bin/apt` at root |
| P3 | Parrot flatten | no `parrot-arm64/` prefix |
| P4 | Arch xz | still 40–250 MiB; `usr/bin/pacman`; no `/boot/Image` |
| P5 | OpenBSD | **not** copied into `assets/rootfs/` |

### 9.3 Device matrix — **every** path, every category

Package `com.ivarna.fluxlinux`. Build `:app:assembleIvarnaRelease`. Install `adb install -r`. Launch `MainActivity`.

For **each** of `{ubuntu, kali, parrot, archlinux}` × `{proot, chroot}` (8 rows):

| Cat | # | Step | Pass |
|-----|---|------|------|
| **Install** | I1 | Onboarding / Distros install completes | log: family + customization OK |
| | I2 | Guest marker | `startxfce4` present; chroot has `.flux_configured` |
| | I3 | Repos | Ubuntu: `ports.ubuntu.com` in sources (not `archive.ubuntu.com`). Kali: `kali-rolling` only. Parrot: `deb.parrot.sh` only. Arch: ALARM `$arch/$repo` (not Manjaro). |
| **Terminal** | T1 | Terminal tab → User | flux prompt; zsh after customization |
| | T2 | `sudo -n id` | uid 0 |
| | T3 | PM works | Ubuntu/Kali/Parrot: `sudo apt-get update` (or `apt-cache policy`). Arch: `sudo pacman -Sy` or `-Q`. No EACCES on locks. |
| | T4 | Identity | `id -un` is `flux` (not `kali`, not `alarm`). `grep ^flux /etc/passwd`. |
| | T5 | Root session | Terminal Root card opens; can `id` |
| **Display** | D1 | Home → Start XFCE | Pulse + X PID + `startxfce4` in logs |
| | D2 | Embedded X11 | Termux:X11 / app X11 activity resumed |
| | D3 | Paint | Screenshot: panel + wallpaper; **no** failsafe |
| | D4 | Theme / icons / font | Space GTK, Papirus (or documented Adwaita fallback), Nerd font in xfce4-terminal |
| | D5 | Stop | Card back to Start; X11 idle |
| **Negative** | N1 | Kali/Parrot | `dpkg -l` has no `kali-desktop-xfce` / `parrot-tools` as newly installed metas |
| | N2 | OpenBSD | no card, no asset |

If any cell fails: fix the owning layer (§4 table), rebuild **release**, `adb install -r`, retest that path. Shared-script fixes require a sibling smoke (last green path).

Reports under `docs/plans/results/`:

- `ubuntu-proot-device-report.md` / `ubuntu-chroot-device-report.md`
- `kali-proot-device-report.md` / `kali-chroot-device-report.md`
- `parrot-proot-device-report.md` / `parrot-chroot-device-report.md`
- `archlinux-proot-device-report.md` / `archlinux-chroot-device-report.md`

plus `*_xfce_pass.png` screenshots.

**Storage:** 8 XFCE trees plus existing guests. Test sequentially. Uninstall a finished **chroot** via in-app uninstall only if `/data` is exhausted. Never uninstall the FluxLinux APK.

---

## 10. Implementation order

1. Flatten + xz + pin SHA (P1–P5). Gradle stage.  
2. Kotlin SSOT + catalog + tests (compile-safe).  
3. Shared apt prep if not already in common (Ubuntu/Kali/Parrot).  
4. Family scripts + `flux_install.sh` + `start_gui_chroot.sh` + deployer. Rewrite Arch family.  
5. Unit tests green.  
6. Ivarna release → `adb install -r` → launch.  
7. E2E (fastest fail loops first):
   1. Ubuntu proot → Ubuntu chroot  
   2. Parrot proot → Parrot chroot  
   3. Kali proot → Kali chroot (`flux` vs `kali` uid)  
   4. Arch proot → Arch chroot (`alarm` rename + pacman-key)  
8. Fix-forward. Shared helper changes smoke the last green sibling.

---

## 11. Landmines

| Item | Rule |
|------|------|
| aapt2 | Never ship Ubuntu as `.tar.gz` in assets |
| Nested Kali/Parrot | Flatten or install looks empty |
| Ubuntu mirrors | `ports.ubuntu.com/ubuntu-ports` only |
| Kali uid 100000 | Flux is uid 1000; do not adopt NH uid |
| Parrot `ID=debian` | Card id `parrot` is SSOT |
| Arch `alarm` | Rename/replace; one uid 1000 |
| Arch gnupg | `pacman-key --init` required |
| Arch vs Manjaro | Different cards, mirrors, HoldPkg |
| Pentest metas | Out of scope — desktop only |
| OpenBSD img | Out of scope |
| Home chown | Same proot app-uid rule as Alpine/FVO |
| glycin | Ubuntu 26 gtk — Fedora lesson |
| Ivarna only | `assembleIvarnaRelease` + `adb install -r` |

---

## 12. Out of scope

- OpenBSD / any BSD guest.  
- NetHunter Magisk, `nh` CLI, hid/wifi injection.  
- Kali/Parrot tool suites.  
- KDE / GNOME / Ubuntu desktop meta.  
- Official `archlinux` x86_64 Docker image.  
- Changing Debian/Alpine/FVO/DCM behavior except shared helpers already in those contracts.

---

## 13. Key files

| Path | Action |
|------|--------|
| `assets/rootfs/ubuntu_26.04_rootfs.tar.xz` | Create (xz from gzip) |
| `assets/rootfs/kali_2026_2_rootfs.tar.xz` | Create (flatten + xz) |
| `assets/rootfs/parrot_7.2_rootfs.tar.xz` | Create (flatten + xz) |
| `assets/rootfs/archlinux_arm_rootfs.tar.xz` | Already present |
| `DistroInstallProfile.kt` | Eight profiles + SHA |
| `DistroSpec.kt` | `PARROT` enum |
| `DistroRepository.kt` | Eight live cards |
| `ChrootPaths.kt` | Four paths |
| `TerminalShellCatalog.kt` / `TerminalLauncher.kt` | Probe + labels |
| `GuestZshrcRepair.kt` | apt + pacman + name tests |
| `HostScriptDeployer.kt` | Four family scripts |
| `scripts/ubuntu|kali|parrot/common/setup/setup_*_family.sh` | New |
| `scripts/arch/common/setup/setup_arch_family.sh` | Rewrite |
| `flux_install.sh` / `start_gui_chroot.sh` | Four arms |
| `app/build.gradle.kts` | Stage four xz |
| `docs/distro/{ubuntu,kali,parrot,archlinux}.md` | After device pass |
| `docs/plans/results/*-device-report.md` | Per-path evidence |

---

## 14. Definition of done

- [x] OpenBSD not packaged.  
- [x] Four xz assets pinned (Ubuntu/Kali/Parrot SHA filled into §1.2). Kali/Parrot archives flat.  
- [x] Eight cards live in Ivarna release (not coming soon).  
- [x] Unit tests in §9.1 green.  
- [x] Packaging checks P1–P5 green.  
- [x] All 8 device rows: I1–I3, T1–T5, D1–D5, N1 green.  
- [x] Reports + XFCE screenshots under `docs/plans/results/`.  
- [x] Status line of this file flipped to `DEVICE PASS` with date.

Until every box is checked, the work is not finished.

---

*This file is the implementation contract. Keep packaged SHA-256 and card ids in sync with `DistroInstallProfile`. Do not start device installs until Kali/Parrot are flattened and Ubuntu is xz.*
