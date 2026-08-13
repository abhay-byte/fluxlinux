# Plan: Deepin, Chimera, and Manjaro (proot + chroot)

**Date:** 2026-08-13  
**Status:** PLAN ONLY — do not implement until this contract is accepted.  
**Scope:** First-class FluxLinux guests for **Deepin 25 (crimson/beige)**, **Chimera Linux (2025-12-20 bootstrap)**, and **Manjaro ARM** — same product shape as Alpine / Debian / Fedora / Void / openSUSE: install → internal terminal → XFCE on the **embedded Termux:X11** display.

**Not in this plan:** Arch Linux (no dedicated rootfs; do not activate the `archlinux` card). KDE, DDE, Debian-style feature modules (appdev / webdev / …), debug APKs, multi-arch.

**Device policy:** `assembleIvarnaRelease` only + `adb install -r`. No APK uninstall unless signature mismatch. Test with a subagent; fix and retest until all **6** paths pass (3 distros × proot + chroot). Do not stop on the first green path.

**References:** [`docs/scripts_reference.md`](../scripts_reference.md), [`docs/scripts_flowchart.md`](../scripts_flowchart.md), [`docs/adding_new_distro.md`](../adding_new_distro.md), live FVO contract [`docs/plan/fedora-void-opensuse.md`](./fedora-void-opensuse.md).

---

## 0. Product decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Distros | Deepin 25, Chimera Linux rolling, Manjaro ARM | Three rootfs archives provided by the user |
| Cards | Six installable cards (proot + rooted chroot each) | Same split as `alpine` / `alpine_chroot` and FVO |
| Desktop | XFCE4 only | User: XFCE + theme/icons/fonts/xfce config + HW accel |
| Modules | None | User: “all of this only required” |
| Deepin DE | XFCE only — **never** DDE / Treeland | DDE is huge, Wayland-first, and not the Flux desktop |
| Package managers | `apt-get` (Deepin), `apk` v3 (Chimera), `pacman` (Manjaro) | Native to each rootfs |
| libc | Deepin + Manjaro = **glibc**; Chimera = **musl** | Chimera is closer to Alpine than to Debian |
| Default user | `flux` / `flux`, NOPASSWD sudo | Alpine/Debian/FVO parity |
| Login shell | zsh after customization | Same Flux `.zshrc` + Oh My Zsh (host-staged) |
| GUI | Shared `start_gui.sh` (proot) + generic `start_guest_gui.sh` (chroot) | FVO already parameterized `CHROOT_ROOT` |
| HW accel | Mesa in family + dedicated `hw_accel` component | VirGL default; no Turnip tarball for these three |
| Rootfs shipping | Convert provided `.tar.gz` → packaged `.tar.xz` (aapt2-safe) | Alpine lesson: aapt2 auto-decompresses `*.gz` |
| Kotlin SSOT | `DistroInstallProfile` | Callers must not grow debian/alpine `when` trees |
| Scripts | Per-family guest + **reuse** generic chroot host trio | High cohesion, low duplication |
| Existing stubs | Flip `comingSoon` and **split** cards | Catalog already has `deepin` / `chimera` / `manjaro` as dual-mode coming-soon |

---

## 1. Rootfs identity (inspected 2026-08-13)

Source files (do not ship these names into APK assets):

| Distro | Source file | Bytes | SHA-256 (source gzip) |
|--------|-------------|------:|------------------------|
| Chimera | `/home/abhaybyte/Downloads/chimera-linux-aarch64-ROOTFS-20251220-bootstrap.tar.gz` | 7 828 734 | `65f738dad84c8d81dc0e17b686a6e1eaf88820d7555ea920ca906f83a7e962b3` |
| Deepin | `/home/abhaybyte/Downloads/deepin-docker-rootfs-arm64.tar.gz` | 93 951 614 | `f11297d18322648b8182213d29ef8b841bc023fecfba034188dae22e16412ee6` |
| Manjaro | `/home/abhaybyte/Downloads/Manjaro-ARM-aarch64-latest.tar.gz` | 211 531 014 | `ce6701a0ddea623fb2752179666f426d9fd1c04805ace73d35a3fc1a314da1ca` |

`file(1)`: all three are gzip. Uncompressed (tar `original size`): Chimera ~17 MiB, Deepin ~276 MiB, Manjaro ~572 MiB.

### Packaged names (after xz recompress)

| Distro | Packaged asset | Deployed home name | Min-size gate |
|--------|----------------|--------------------|---------------|
| Chimera | `rootfs/chimera_20251220_rootfs.tar.xz` | same | ≥ 4 MiB |
| Deepin | `rootfs/deepin_25_rootfs.tar.xz` | same | ≥ 40 MiB |
| Manjaro | `rootfs/manjaro_arm_rootfs.tar.xz` | same | ≥ 80 MiB |

**aapt2 rule (non-negotiable):** APK asset path must **not** end in `.gz`. Alpine ships gzip bytes as `alpine_3.24_rootfs.minirootfs`. FVO ships `.tar.xz` (`noCompress` already includes `xz`). These three follow FVO: convert gzip → xz at stage time, pin SHA-256 of the **xz** file in `DistroInstallProfile`.

Staging command (run once, commit xz into `assets/rootfs/`):

```sh
mkdir -p assets/rootfs
gzip -dc /home/abhaybyte/Downloads/chimera-linux-aarch64-ROOTFS-20251220-bootstrap.tar.gz \
  | xz -T0 -9 > assets/rootfs/chimera_20251220_rootfs.tar.xz
gzip -dc /home/abhaybyte/Downloads/deepin-docker-rootfs-arm64.tar.gz \
  | xz -T0 -9 > assets/rootfs/deepin_25_rootfs.tar.xz
gzip -dc /home/abhaybyte/Downloads/Manjaro-ARM-aarch64-latest.tar.gz \
  | xz -T0 -9 > assets/rootfs/manjaro_arm_rootfs.tar.xz
sha256sum assets/rootfs/chimera_20251220_rootfs.tar.xz \
          assets/rootfs/deepin_25_rootfs.tar.xz \
          assets/rootfs/manjaro_arm_rootfs.tar.xz
```

Record those three SHA-256 values in this plan and in `DistroInstallProfile` **before** the first device install. `setup_guest_chroot.sh` and `flux_install.sh` already extract `*.tar.xz`.

Fallback if xz recompress is blocked: Alpine-style non-`.gz` names (`*.minirootfs`) holding the original gzip bytes, with SHA of the gzip. Prefer xz.

### `os-release` facts (from the archives)

**Chimera** (`./etc/os-release` → `../usr/lib/os-release`):

```
NAME="Chimera"
ID="chimera"
PRETTY_NAME="Chimera Linux"
```

`./usr/lib/chimera-release` = `rolling`.  
`apk` arch = `aarch64`. World = `base-bootstrap` only.  
Repo (apk-tools **v3**): `v3 ${CHIMERA_REPO_URL}/${CHIMERA_REPO_RELEASE}/main` with defaults `https://repo.chimera-linux.org` + `current`.  
libc = `usr/lib/ld-musl-aarch64.so.1`. Userland = **chimerautils** (BSD tools), **not** GNU coreutils.  
28 packages. **No** bash, sudo, shadow/`useradd`, doas, XFCE. Default shell `/usr/bin/sh`. apk-tools **3.0.3**. DB at `/usr/lib/apk/db` (not Alpine `/lib/apk/db`). Config defaults: `interactive` + `cache-packages`.

**Deepin** (`./usr/lib/os-release`):

```
PRETTY_NAME="Deepin 25"
ID=deepin
VERSION_ID="25"
VERSION_CODENAME=crimson
```

`etc/deepin_version` = `25`. `etc/debian_version` = `bookworm/sid`.  
`etc/apt/sources.list`:

```
deb https://community-packages.deepin.com/beige/ crimson main commercial community
```

Keys already in `etc/apt/trusted.gpg.d/` (camel + crimson + app-store).  
161 installed packages. Has `apt`, `apt-get`, `dpkg`, `bash`, `sudo`, `useradd`. **No** XFCE. glibc loader at `usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1`. Docker leftovers (openssh, runit meta) — ignore, do not start sshd.

**Manjaro ARM** (`./usr/lib/os-release`):

```
NAME="Manjaro ARM"
ID="manjaro-arm"
ID_LIKE="manjaro arch"
PRETTY_NAME="Manjaro ARM"
```

`Architecture = aarch64` in `pacman.conf`. Repos `core` / `extra` / `community` via `/etc/pacman.d/mirrorlist` (`…/manjaro/arm-stable/$repo/$arch`). Mirrors dated 2026-08-10.  
92 packages including `pacman`, `bash`, `gnupg`, `archlinuxarm-keyring`, `manjaro-arm-keyring`, `manjaro-keyring`, `glibc-2.35`. **No** sudo, **no** `useradd`/`shadow`, **no** XFCE. **Empty** `/etc/pacman.d/gnupg` — `pacman-key --init` is mandatory. `passwd`/`group` contain **only** `root`. No `/boot`, no firmware (rootfs, not a disk image). `HoldPkg = pacman glibc manjaro-system`.

---

## 2. Cards and paths

Replace the three **Coming Soon** stubs. Do **not** leave a dual-mode card. Same pattern as Fedora:

| Card id | UI name | Method | proot-distro name | Chroot path | Color (existing stub) | Icon (already in tree) |
|---------|---------|--------|-------------------|-------------|-----------------------|------------------------|
| `deepin` | Deepin | proot | `deepin` | — | `0xFF2CA7F8` | `R.drawable.distro_deepin` |
| `deepin_chroot` | Deepin (Rooted) | chroot | — | `/data/local/tmp/chrootDeepin` | same | same |
| `chimera` | Chimera | proot | `chimera` | — | `0xFFFF6B35` | `R.drawable.distro_chimera` |
| `chimera_chroot` | Chimera (Rooted) | chroot | — | `/data/local/tmp/chrootChimera` | same | same |
| `manjaro` | Manjaro | proot | `manjaro` | — | `0xFF35BF5C` | `R.drawable.distro_manjaro` |
| `manjaro_chroot` | Manjaro (Rooted) | chroot | — | `/data/local/tmp/chrootManjaro` | same | same |

Descriptions (short):

- Deepin: “Deepin 25 with apt and XFCE4 (proot).” / “Deepin 25 chroot environment (Requires Root).”
- Chimera: “Chimera Linux (musl, apk v3) with XFCE4 (proot).” / “Chimera Linux chroot environment (Requires Root).”
- Manjaro: “Manjaro ARM with pacman and XFCE4 (proot).” / “Manjaro ARM chroot environment (Requires Root).”

`prootSupported` / `chrootSupported` are mutually exclusive per card (Home MethodTabs + Terminal MethodTabs depend on this).

---

## 3. Components (required only)

Each of the six cards gets the same three components as FVO:

| id | Script | Role |
|----|--------|------|
| `xfce4_desktop` | `setup_<family>_family.sh` | DNS, repos, base pkgs, XFCE, dbus, user `flux`, sudo, Mesa, `/etc/fluxlinux/gpu_mode` |
| `hw_accel` | `common/setup/setup_hw_accel_guest.sh` | Extra GL/Vulkan, `gpu_mode`, `gpu-launch` wrapper |
| `customization` | `common/setup/setup_customization_xfce.sh` | Space theme, Papirus, Vimix, JetBrainsMono Nerd, xfconf XML, zsh/OMZ, PM wrapper |

Onboarding still runs **family + customization**. Family **must** install Mesa + write `gpu_mode=virgl` so the first Start paints. After family success, `OnboardingInstallRunner` marks `hw_accel` installed when the profile has `hwAccelScript` (same as FVO). Distro Settings can re-run the dedicated hw script.

Do **not** attach Debian module scripts to Deepin even though it is apt-based.

---

## 4. Script map

### Guest (inside the container)

```
scripts/deepin/common/setup/
  setup_deepin_family.sh
scripts/chimera/common/setup/
  setup_chimera_family.sh
scripts/manjaro/common/setup/
  setup_manjaro_family.sh
scripts/common/setup/
  flux_guest_common.sh          # EXTEND: portable stat, extra PM paths
  setup_customization_xfce.sh   # EXTEND: apt / pacman / apk-v3
  setup_hw_accel_guest.sh       # EXTEND: apt / pacman / apk-v3
```

Family scripts are the **only** place package names live. Customization sources the shared XFCE branding script after installing `zsh git fontconfig unzip curl wget` (or Chimera equivalents).

Do **not** reuse:

| Existing script | Why not |
|-----------------|--------|
| `setup_debian_family.sh` for Deepin | Assumes Debian repos / package set; would add debian.org or pull the wrong metapackage |
| `setup_alpine_family.sh` for Chimera | Alpine apk **v2** (`--no-cache`, `/etc/apk/repositories`, community pin). Chimera is apk **v3** + `user` repo + BSD userland |
| `setup_arch_family.sh` for Manjaro | Rewrites mirrors to **Arch Linux ARM**, not Manjaro ARM-stable. Would break the provided rootfs |

### Host (proot — reuse + case)

| Script | Change |
|--------|--------|
| `debian/proot/setup/flux_install.sh` | `case` for `deepin` / `chimera` / `manjaro` (rootfs name + family script). `FLUX_ROOTFS_*` still wins. Prepend `flux_guest_common.sh` for all three (not debian/alpine). |
| `debian/proot/start/start_gui.sh` | No change (already takes proot name). |
| `debian/proot/stop/stop_gui.sh` | No change. |

### Host (chroot — generic, env-driven)

Reuse the FVO generic trio. Identity is **only** `FLUX_CHROOT` + rootfs env.

| Script | Change |
|--------|--------|
| `scripts/chroot/setup_guest_chroot.sh` | Already extracts xz/gz. Extend populated-rootfs probe to treat `/usr/bin/sh` + `/usr/bin/apk` as present (Chimera). Useradd is best-effort; family creates `flux` if shadow was missing. |
| `scripts/chroot/uninstall_guest_chroot.sh` | No change (env path). |
| `scripts/chroot/start_guest_gui.sh` | No change (`CHROOT_ROOT` + `/bin/sh`). |
| `scripts/chroot/stop_guest_gui.sh` | No change. |
| `scripts/chroot/start_gui_chroot.sh` | Add `deepin_chroot` / `chimera_chroot` / `manjaro_chroot` → matching path + `start_guest_gui.sh`. |
| `scripts/chroot/stop_gui_chroot.sh` | Same dispatch if it has a distro case. |

Kotlin profiles all point `chrootSetupAsset` at `setup_guest_chroot.sh` and `chrootStartGuiScript` at `start_guest_gui.sh`.

---

## 5. Shared helper extensions (do these first)

`flux_guest_common.sh`, `setup_customization_xfce.sh`, and `setup_hw_accel_guest.sh` currently know dnf / xbps / zypper (plus customization also has Alpine `apk add --no-cache` and `apt-get`). Three gaps will break the new guests if left as-is.

### 5.1 Portable `stat` (Chimera BSD userland)

`stat -c %u` is GNU. Chimera `stat` is BSD (`stat -f %u`). Add:

```sh
_flux_stat_u() { stat -c %u "$1" 2>/dev/null || stat -f %u "$1" 2>/dev/null || true; }
_flux_stat_g() { stat -c %g "$1" 2>/dev/null || stat -f %g "$1" 2>/dev/null || true; }
```

Use these in `_flux_ensure_home` and `_flux_fix_pm_writable`. Do not assume GNU coreutils anywhere in Chimera paths.

### 5.2 PM writable paths

Extend `_flux_fix_pm_writable` with:

```
/usr/lib/apk /usr/lib/apk/db /var/cache/apk /etc/apk     # Chimera apk v3
/var/lib/pacman /var/cache/pacman /etc/pacman.d          # Manjaro
/var/lib/apt /var/cache/apt /var/lib/dpkg                # Deepin
```

### 5.3 User creation order

`_flux_ensure_user` already tries `useradd` then Alpine `adduser`. Chimera/Manjaro bootstrap have **no** `useradd` until `shadow` is installed. Family scripts must:

1. Install `shadow` (and `bash` on Chimera) **before** calling `_flux_ensure_user`.
2. Fall back to `/bin/sh` as login shell if `/bin/bash` is still missing.
3. Create missing groups (`wheel`, `audio`, `video`, `input`, `users`) — Manjaro `group` file is only `root`.

### 5.4 Customization `_flux_pkg_add`

Current `apk add --no-cache` is Alpine v2 and will fail or warn on Chimera v3. Detect:

```sh
if [ -e /usr/lib/os-release ] && grep -q '^ID="chimera"' /usr/lib/os-release 2>/dev/null; then
    # apk v3, non-interactive (see §6.2)
    apk add "$@"
elif command -v apk >/dev/null 2>&1; then
    apk add --no-cache "$@"          # Alpine
elif command -v pacman >/dev/null 2>&1; then
    pacman -S --noconfirm --needed "$@"
elif command -v apt-get >/dev/null 2>&1; then
    DEBIAN_FRONTEND=noninteractive apt-get install -y "$@"
# existing dnf / xbps / zypper ...
```

zsh wrappers to append (only when the binary exists):

```sh
apt-get() { command sudo apt-get "$@"; }
apt()     { command sudo apt "$@"; }
pacman()  { command sudo pacman "$@"; }
apk()     { command sudo apk "$@"; }     # Chimera; Alpine already has this
```

### 5.5 HW accel `_pkg_add`

Same PM detection. Package lists:

| Distro | Packages (best-effort, then smaller fallback) |
|--------|-----------------------------------------------|
| Deepin | `mesa-utils libgl1-mesa-dri libegl1-mesa libegl1 mesa-vulkan-drivers` |
| Manjaro | `mesa mesa-utils vulkan-mesa-layers` |
| Chimera | `mesa mesa-dri mesa-gl` (exact names via `apk search mesa` on first device fail) |

Always write `/etc/fluxlinux/gpu_mode` (`virgl`). No lfdevs Turnip tarball for these three.

---

## 6. Family setup (per PM)

Common (via `flux_guest_common.sh`), same contract as FVO:

1. Must run as root.
2. `PATH` + sticky `/tmp` `/var/tmp`; unset `PROOT_TMP_DIR`.
3. Write `resolv.conf` if empty (8.8.8.8 / 1.1.1.1 / 8.8.4.4).
4. Create `flux` (uid 1000 if free), password `flux`, groups `wheel`/`audio`/`video`/`input`/`users` (best-effort).
5. `flux ALL=(ALL) NOPASSWD:ALL` + `@includedir /etc/sudoers.d`.
6. dbus machine-id.
7. Home ownership: prefer `/home` owner (proot app uid), then `flux:flux` only when home owner is 0.
8. Write `/etc/fluxlinux/gpu_mode` = `virgl`.
9. VNC `xstartup` → `startxfce4`.
10. Proot-safe PM DB ownership.
11. Fail closed if `/usr/bin/startxfce4` is missing (`_flux_require_startxfce4`).

POSIX `sh` only (`#!/bin/sh`). Chimera has no bash until we install it.

### 6.1 Deepin (`apt-get`) — `setup_deepin_family.sh`

Deepin 25 is Debian-**like**, not Debian. Keep the beige/crimson sources. **Never** add `deb.debian.org`.

```
export DEBIAN_FRONTEND=noninteractive
export DEBCONF_NONINTERACTIVE_SEEN=true
# proot/chroot apt sandbox (copy Debian lesson)
mkdir -p /etc/apt/apt.conf.d
printf 'APT::Sandbox::User "root";\nDPkg::Use-Pty "false";\n' \
  > /etc/apt/apt.conf.d/99flux-nosandbox

apt-get update
apt-get install -y --no-install-recommends \
  bash sudo passwd adduser ca-certificates curl wget unzip tar \
  dbus dbus-x11 \
  xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
  xfdesktop4 xfwm4 thunar \
  adwaita-icon-theme fonts-dejavu-core \
  libgl1-mesa-dri libegl1 mesa-utils
```

Rules:

- Do **not** `apt-get install xfce4` metapackage first — it may pull lightdm / extra recommends. Targeted list, then fallback to `xfce4` only if `startxfce4` is still missing.
- Do **not** install `dde-*`, `deepin-desktop-environment*`, or Treeland.
- If `apt-get update` fails on `community-packages.deepin.com`, retry once; do not rewrite the suite.
- `dpkg` configure: `DEBIAN_FRONTEND=noninteractive` + `systemctl` masked if present (`SYSTEMD_OFFLINE=1`).
- After root-owned writes, chown apt/dpkg caches to owner of `/etc` (proot).
- Locale: `C.UTF-8` / `en_US.UTF-8` if `locales` is cheap; do not block on locale-gen failure.

### 6.2 Chimera (`apk` v3) — `setup_chimera_family.sh`

Highest novelty. Treat as a new family, not “Alpine with a different tarball.”

**Non-interactive apk v3.** Default `/usr/lib/apk/config` contains `interactive`. Override:

```
# /etc/apk/config  (replaces system config; list only what we want)
cache-packages
```

Omit `interactive`. Docs: user `/etc/apk/config` overrides `/usr/lib/apk/config`.

```
apk update
apk add bash sudo shadow ca-certificates curl wget unzip tar \
        dbus font-dejavu adwaita-icon-theme
# user repo — XFCE lives here, not in main
apk add chimera-repo-user
apk update
# TARGETED xfce — do NOT apk add xfce4 metapackage
# (meta pulls gvfs + udisks → udev/proot hangs, same class as Fedora groupinstall)
apk add xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
        xfdesktop xfwm4 thunar \
        mesa mesa-dri
```

If a name 404s, use virtual providers (`apk add cmd:startxfce4`, `cmd:sudo`, `cmd:useradd`) then record the real name.

Rules:

- `--no-cache` is Alpine v2. Do not use it.
- Do not `apk add base-full` or any `base-full-kernel`.
- Do not enable debug repos.
- Install `bash` + `shadow` **before** `_flux_ensure_user` (`-s /bin/bash` with `/bin/sh` fallback).
- dbus machine-id: `dbus-uuidgen` if present; else write a hex id.
- apk DB ownership: `/usr/lib/apk` `/usr/lib/apk/db` `/var/cache/apk`.
- Prefer `cmd:startxfce4` as the fail-closed check if the binary lands under `/usr/libexec`.

**Do not** run Alpine `GuestApkDbRepair` as-is (it keys off proot name `alpine` and Alpine DB paths). Either generalize it to “apk guest” with per-profile db roots, or add a Chimera branch (`usr/lib/apk/db`). Wrong-path chown is worse than none.

### 6.3 Manjaro (`pacman`) — `setup_manjaro_family.sh`

Do **not** touch Manjaro ARM mirrors (already `arm-stable`). Do **not** write ALARM `mirror.archlinuxarm.org` lists.

**pacman-key is the #1 landmine.** `/etc/pacman.d/gnupg` is empty. Plan:

```
# 1) entropy — proot must bind /dev/urandom (already does)
# 2) init + populate
mkdir -p /etc/pacman.d/gnupg
pacman-key --init
pacman-key --populate archlinuxarm manjaro-arm manjaro \
  || pacman-key --populate archlinuxarm manjaro-arm
# 3) if init/populate fails or hangs: temporary SigLevel=Never,
#    pacman -Sy --noconfirm archlinuxarm-keyring manjaro-arm-keyring manjaro-keyring
#    then restore SigLevel = Required DatabaseOptional and retry populate
```

Never leave `SigLevel = Never` as the permanent config after bootstrap.

Then:

```
# create standard groups (rootfs group file is only root)
for g in wheel audio video input users storage optical; do
  getent group "$g" >/dev/null || groupadd "$g"
done

pacman -Sy --noconfirm
pacman -S --noconfirm --needed \
  shadow sudo \
  xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
  xfdesktop xfwm4 thunar \
  ttf-dejavu adwaita-icon-theme \
  mesa mesa-utils \
  dbus
```

Rules:

- Do **not** `pacman -S xfce4` group first (pulls extras). Targeted list; fallback to group only if `startxfce4` missing.
- Skip full `pacman -Syu` on first boot if it wants to replace `glibc`/`manjaro-system` in a way that breaks the container. Prefer `pacman -Sy` + explicit packages. If a dependency forces a partial upgrade, allow it with `--needed` and log it.
- `HoldPkg = pacman glibc manjaro-system` — do not remove those.
- Scriptlets: `SYSTEMD_OFFLINE=1`. Ignore systemd unit failures.
- `CheckSpace` can lie on Android bind mounts — if pacman aborts on disk space, comment `CheckSpace` for the family run only.
- After writes, chown `/var/lib/pacman` `/var/cache/pacman`.
- ParallelDownloads is already `5` — leave it.

---

## 7. Customization (theme, icons, font, xfconf)

Shared `setup_customization_xfce.sh` (already Alpine branding, PM-agnostic after §5.4):

| Item | Dark | Light |
|------|------|-------|
| GTK / xfwm theme | `Space-transparency` | `Space-light` |
| Icons | `Papirus-Dark` | `Papirus-Dark` |
| Cursor | `Vimix-white-cursors` | `Vimix-cursors` |
| Wallpaper | `fluxlinux-dark.png` | `fluxlinux-light.png` |
| Font | JetBrainsMono Nerd Font 10 | same |
| Scale | `WindowScalingFactor=2` | same |
| Compositor | **off** (`use_compositing=false`) | same |

Honor `FLUX_SKIP_THEME_ICONS`, `FLUX_SKIP_OMZ`, `FLUX_SKIP_POKEMON`, `FLUX_ASSET_DIR` (host `ProotXfceAssetInstaller` / `ProotZshBootstrap`).

If Papirus/theme extract fails on musl (Chimera), keep Adwaita + DejaVu and still write xfconf so the session is not failsafe. Device gate D4 then records “fallback icons” vs “Papirus”. Prefer fixing the extract (need `unzip`/`tar`/`xz` only — already in family).

---

## 8. Hardware acceleration

| Distro | Strategy | Extra |
|--------|----------|--------|
| Deepin | Mesa DRI/EGL via apt | VirGL only. Watch for glycin-only gdk-pixbuf (Fedora lesson). If PNG icons fail, ship/reuse `bwrap-proot-shim` **or** classic loader — never `GDK_DEBUG=no-glycin` unless a PNG loader exists. |
| Manjaro | `mesa` + `mesa-utils` | VirGL only |
| Chimera | `mesa` / `mesa-dri` from main | VirGL only. musl Mesa is fine with virpipe |

Always write `/etc/fluxlinux/gpu_mode`. Start scripts already read this file.

Do **not** ship Debian `apt` Turnip blobs into Chimera/Manjaro.

---

## 9. Kotlin / Gradle (decoupled wiring)

### `SupportedDistro` / `DistroFamily` / `PackageManager`

```
DEEPIN   id=deepin    family=DEBIAN   pm=APT     release=FIXED
CHIMERA  id=chimera   family=CHIMERA  pm=APK     release=ROLLING
MANJARO  id=manjaro   family=ARCH     pm=PACMAN  release=ROLLING
```

Add `DistroFamily.CHIMERA`. Reuse `PackageManager.APK` (same binary name, different family). Do **not** invent `APK3` unless a caller truly needs it — family script is the SSOT for apk v2 vs v3 flags.

### `DistroInstallProfile`

Six profiles. `hwAccelScript = common/setup/setup_hw_accel_guest.sh`.  
`customizationScript = common/setup/setup_customization_xfce.sh`.  
Chroot trio = generic guest scripts + new `ChrootPaths`.

`allRootfsProfiles()` grows from 5 → **8** (Debian, Alpine, Fedora, Void, openSUSE, Deepin, Chimera, Manjaro).  
`allInstallable()` grows by the six new cards.  
`isInstallable` true for all six ids.

### `ChrootPaths`

```
DEEPIN_CHROOT_PATH   = /data/local/tmp/chrootDeepin
CHIMERA_CHROOT_PATH  = /data/local/tmp/chrootChimera
MANJARO_CHROOT_PATH  = /data/local/tmp/chrootManjaro
```

`pathForDistro` covers the three new chroot ids.

### `DistroRepository`

Replace the three coming-soon entries with six live cards (proot-only + chroot-only). Components via a small helper (copy `glibcXfceComponents` + Chimera uses the same three ids with `setup_chimera_family.sh`).

### `TerminalShellCatalog` / `TerminalShellAvailability`

Add booleans **or** (preferred, leftover FVO note) a `Map<String, Boolean>` keyed by card id so the next distro is one profile, not six catalog fields. If a map rewrite is too large for this pass, add six booleans the same way Fedora was added — but do not leave the catalog unable to show User/Root for the new guests.

`prootInstalled` / `chrootInstalled` / labels / icons for `deepin`, `chimera`, `manjaro` and `*_chroot`.

`TerminalLauncher.probe` must call `isProotInstalled(ctx, "deepin"|"chimera"|"manjaro")` and `isChrootInstalled` on the three new paths.

### `TerminalLauncher.guestRootfsHasShell`

Chimera has `/usr/bin/sh` and `/bin` → `usr/bin`. Current probe should work via `bin/sh`. Add `usr/bin/sh` and `usr/bin/apk` as extra true conditions so a broken `bin` symlink cannot false-negative. Unit test: Chimera-shaped tree (`usr/bin/sh` + `bin` symlink).

### `HostScriptDeployer`

Deploy the three family scripts. Shared customization / hw / guest chroot already listed.

### `GuestZshrcRepair`

- `resolveProotName("deepin_chroot") == "deepin"` (existing suffix strip).
- `isGlibcGuest` += `deepin`, `manjaro`.
- Chimera is **musl**: apk wrapper like Alpine, but **not** Alpine-only strings (`/lib/apk`). New `isApkGuest` = alpine **or** chimera.
- PM wrappers: `apt-get`/`apt` (Deepin), `pacman` (Manjaro), `apk` (Chimera).

### `GuestApkDbRepair`

Today: `if (prootName != "alpine") return`. Either skip Chimera (family chown is enough) or generalize db roots from the profile. Do not chown Alpine paths inside a Chimera rootfs.

### `OnboardingFlowScreen` / `OnboardingInstallRunner`

Already profile-driven. Copy uses `displayName`. After family, mark `hw_accel` installed (existing FVO behavior).

### `BaseDesktopInstallPlan.familySetupPayload`

Prepends `flux_guest_common.sh` unless the family path contains `debian` or `alpine`. Deepin/Chimera/Manjaro paths do not — good. Keep it that way.

### Gradle `stageHostRootfs`

Copy the three xz archives from `assets/rootfs/` into `app/src/main/assets/rootfs/` (no rename). Add them to task inputs/outputs next to the FVO three.

---

## 10. Tests (unit)

| File | Updates |
|------|---------|
| `DistroInstallProfileTest` | Six new profiles; SHA hex; asset not `.gz`; `allRootfsProfiles` size **8**; all six ids installable |
| `DistroRepositoryTest` | Live cards 10 → **16**; each new pair is proot-only / chroot-only; stubs no longer `comingSoon` |
| `TerminalComponentTest` | deepin/chimera/manjaro + chroots |
| `TerminalShellCatalogTest` | New sections enabled/disabled; titles `DEEPIN SHELL` / `CHIMERA SHELL` / `MANJARO SHELL` |
| `ChrootPathsTest` | Three new paths, distinct from Fedora/Void/SUSE/Debian/Alpine |
| `BaseDesktopInstallPlanTest` | `distroById` + `methodFor` + `profileFor` for the six ids |
| `GuestZshrcRepairTest` | `resolveProotName("manjaro_chroot") == "manjaro"`; apk wrapper for chimera; pacman/apt wrappers |
| `GuestRootfsShellTest` | Chimera `usr/bin/sh` + `bin`→`usr/bin` counts as installed |

No Android instrumentation in this pass. Device E2E is the gate.

---

## 11. Device test matrix (must all pass)

Package: `com.ivarna.fluxlinux`. Build: `:app:assembleIvarnaRelease`. Install: `adb install -r`. Launch: `com.ivarna.fluxlinux/.MainActivity`.

For **each** of `{deepin, chimera, manjaro}` × `{proot, chroot}`:

| # | Step | Pass |
|---|------|------|
| I1 | Distros / onboarding install completes | `startxfce4` present; `.flux_configured` (chroot) |
| T1 | Terminal card opens flux shell | Prompt; zsh after customization |
| T2 | `sudo -n id` | uid 0 |
| T3 | PM works | Deepin: `sudo apt-get update` (or `apt-cache policy`); Chimera: `sudo apk update` / `apk info`; Manjaro: `sudo pacman -Sy` or `-Q`; no lock EACCES |
| D1 | Home → Start XFCE | Log shows Pulse + X server PID + startxfce4 |
| D2 | Embedded X11 activity | `com.termux.x11.MainActivity` (or app-id X11) resumed |
| D3 | Desktop paints | Screenshot: panel + wallpaper; **no** failsafe dialog |
| D4 | Theme / icons / font | GTK Space theme, Papirus icons (or documented Adwaita fallback on Chimera), Nerd font in xfce4-terminal |
| D5 | Stop | Card returns to Start; X11 idle |

If any cell fails: fix the owning script/profile, rebuild **release**, `adb install -r`, retest that path (and regress the last passing sibling if the change was shared — especially `flux_guest_common.sh`, customization, hw, `setup_guest_chroot.sh`, `flux_install.sh`).

Write a device report per path under `docs/plans/results/`:

- `deepin-proot-device-report.md`
- `deepin-chroot-device-report.md`
- `chimera-proot-device-report.md`
- `chimera-chroot-device-report.md`
- `manjaro-proot-device-report.md`
- `manjaro-chroot-device-report.md`

plus screenshots (`deepin_proot_xfce_pass.png`, …).

**Storage:** Manjaro uncompressed + XFCE is the largest guest. Install/test **sequentially**. Uninstall a finished chroot via the in-app uninstall script only if `/data` is exhausted. Do **not** uninstall the FluxLinux APK. Do not delete Debian/Alpine/FVO guests unless the device is actually full.

**APK size:** three more xz archives will grow the Ivarna APK significantly (Manjaro alone is ~200 MiB gzip / likely 150–190 MiB xz). Acceptable for this device-test track; do not split APKs.

---

## 12. Implementation order

1. Convert + pin rootfs xz; Gradle stage; record SHA-256 in profile + this plan.  
2. Shared helper extensions (§5) + unit tests for stat/PM detection where cheap.  
3. Kotlin SSOT + catalog + repository cards (compile-safe, comingSoon=false).  
4. Guest family scripts + `flux_install.sh` cases + `start_gui_chroot.sh` dispatch + deployer list.  
5. Unit tests green.  
6. Release APK → `adb install -r` → launch.  
7. E2E in this order (fastest fail loops first):
   1. **Deepin proot** (apt, closest to Debian)
   2. **Deepin chroot**
   3. **Chimera proot** (smallest; apk v3 / musl / BSD)
   4. **Chimera chroot**
   5. **Manjaro proot** (largest; pacman-key)
   6. **Manjaro chroot**
8. Fix-forward. Shared-script fixes require a sibling smoke (at least the last green path).

Subagent tests the device the same way FVO was tested: UI install, Terminal User/Root, Home Start, embedded X11 screenshot, Stop.

---

## 13. Known landmines (read before writing scripts)

### Shared (from Alpine + FVO — do not re-litigate, just apply)

- aapt2 strips `*.gz` assets.
- Toybox `/system/bin/tar` has no xz — chroot extract uses root busybox.
- Proot home ownership: `chown flux:flux` on `/home/flux` when `/home` is the app uid breaks xfconfd → failsafe.
- `PROOT_TMP_DIR` must be unset inside the guest; `/tmp` sticky 1777.
- `pkill -f` too broad kills the app.
- Never `GDK_DEBUG=no-glycin` without a classic PNG loader.
- Chroot GUI user: `runuser -u flux` (or `su - flux`) — `su` cannot always set groups.
- Empty `machine-id` breaks dbus on some guests.
- Only Ivarna release APK.

### Deepin-specific

- Beige/crimson repos ≠ Debian. Adding debian.org will brick apt.
- Docker image may have empty `resolv.conf` or Docker DNS — always rewrite if no `nameserver`.
- apt sandbox / `_apt` user write probe (Debian chroot already does this).
- `01dangerous-packages.conf` is autoremove warnings only — leave it.
- DDE packages must never be in the family install set.
- Deepin 25 gtk stack might be new enough for glycin — watch first XFCE paint.

### Chimera-specific

- apk-tools **3** ≠ Alpine apk 2. Interactive by default. Different DB path. `user` repo required for XFCE.
- `xfce4` **metapackage** pulls `gvfs` + `udisks` — skip the meta.
- No bash/useradd/sudo in the bootstrap. `/usr/bin/sh` only.
- BSD `stat`/`ps`/`find` flags.
- musl: no glibc AppImages; Papirus extract must use packaged `tar`/`unzip`.
- Do not point Chimera at Alpine CDNs.

### Manjaro-specific

- Empty pacman keyring. `pacman-key --init` hangs without entropy (classic proot bug).
- Existing `setup_arch_family.sh` would overwrite Manjaro mirrors with ALARM — forbidden.
- `group`/`passwd` are root-only — create groups before `useradd`.
- First `-Syu` against current arm-stable can be huge and scriptlet-heavy — prefer `-Sy` + targeted packages.
- `HoldPkg` includes `manjaro-system`.
- `community` repo still listed; if sync fails on community, drop that section rather than rewriting to ALARM.

---

## 14. Out of scope

- Arch Linux card (`archlinux` stays coming soon).  
- KDE / DDE / Treeland / VirGL-Turnip-Software *KDE* launchers.  
- Feature modules (appdev, webdev, …) on any of the three.  
- Multi-arch (x86_64).  
- Changing Debian / Alpine / FVO behavior except shared helper extensions in §5.  
- Publishing rootfs to GitHub Releases (local APK assets only for this track).

---

## 15. Key files

| Path | Action |
|------|--------|
| `assets/rootfs/{chimera_20251220,deepin_25,manjaro_arm}_rootfs.tar.xz` | Create (xz from user gzip) |
| `app/src/main/kotlin/.../DistroInstallProfile.kt` | Six profiles + SHA + `allRootfsProfiles` |
| `app/src/main/kotlin/.../DistroSpec.kt` | `CHIMERA` family + three enum values |
| `app/src/main/kotlin/.../DistroRepository.kt` | Six live cards; remove three coming-soon stubs |
| `app/src/main/kotlin/.../TerminalComponent.kt` | Profile-driven (should already work) |
| `app/src/main/kotlin/.../TerminalShellCatalog.kt` | Three guests × two methods |
| `app/src/main/kotlin/.../TerminalLauncher.kt` | Probe + `guestRootfsHasShell` |
| `app/src/main/kotlin/.../ChrootPaths.kt` | Three paths |
| `app/src/main/kotlin/.../HostScriptDeployer.kt` | Three family scripts |
| `app/src/main/kotlin/.../GuestZshrcRepair.kt` | apt / pacman / chimera apk wrappers |
| `app/src/main/assets/scripts/{deepin,chimera,manjaro}/common/setup/setup_*_family.sh` | New |
| `app/src/main/assets/scripts/common/setup/flux_guest_common.sh` | Portable stat + PM paths |
| `app/src/main/assets/scripts/common/setup/setup_customization_xfce.sh` | apt / pacman / apk v3 |
| `app/src/main/assets/scripts/common/setup/setup_hw_accel_guest.sh` | apt / pacman / apk v3 |
| `app/src/main/assets/scripts/debian/proot/setup/flux_install.sh` | Three `case` arms |
| `app/src/main/assets/scripts/chroot/start_gui_chroot.sh` | Three dispatch arms |
| `app/build.gradle.kts` | Stage three xz |
| `docs/distro/{deepin,chimera,manjaro}.md` | Operator refs **after** device pass |
| `docs/distro/README.md` | Link the three docs |
| `docs/plans/results/*-device-report.md` | Per-path E2E evidence |

Icons already exist (`distro_deepin.webp`, `distro_chimera.webp`, `distro_manjaro.webp`). No new drawables.

---

## 16. Definition of done

- [ ] Six cards installable in the Ivarna release APK (not coming soon).  
- [ ] All six device-matrix rows I1–D5 pass.  
- [ ] Internal terminal User + Root works on each (T1–T3).  
- [ ] XFCE paints on the **internal** display server with theme / icons / font (D3–D4).  
- [ ] Unit tests listed in §10 green.  
- [ ] This plan’s SHA-256 table updated to the **packaged xz** hashes.  
- [ ] Per-path reports + screenshots under `docs/plans/results/`.  
- [ ] Status line at the top of this file flipped to `DEVICE PASS` with the date.

Until every box is checked, the work is not finished.

---

*This file is the implementation contract. Keep SHA-256 and card ids in sync with `DistroInstallProfile`. Do not start coding until the xz archives are pinned.*
