# Plan: Fedora, Void, and openSUSE (proot + chroot)

**Date:** 2026-08-13  
**Status:** DEVICE PASS (2026-08-13) — I1–D3/D5/T2/T3 still stand. **Follow-up (not implemented):** Fedora locale + fastfetch disk flood + XFCE ⊘ category icons (openSUSE + shared Papirus stub). SSOT: [`xfce-icons-locale-fastfetch.md`](./xfce-icons-locale-fastfetch.md).  
**Scope:** First-class FluxLinux guests for **Fedora 43**, **Void Linux (glibc aarch64, 2025-02-02)**, and **openSUSE Tumbleweed (20251127)** — same product shape as Alpine/Debian: install → internal terminal → XFCE on the embedded X11 display.

**Not in this plan:** Arch Linux (no rootfs provided). KDE, Debian-style feature modules (appdev/webdev/…), and debug APKs.

**Device policy:** `assembleIvarnaRelease` only + `adb install -r`. No uninstall unless signature mismatch. Test with a subagent; fix and retest until all **6** paths pass (3 distros × proot + chroot).

---

## 0. Product decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Distros | Fedora 43, Void glibc aarch64, openSUSE Tumbleweed | Rootfs files provided by user |
| Cards | Six installable cards (proot + rooted chroot each) | Same split as `alpine` / `alpine_chroot` |
| Desktop | XFCE4 only | User: XFCE + theme/icons/fonts/xfce config + HW accel |
| Modules | None | User: “all of this only required” |
| Package managers | `dnf`/`dnf5`, `xbps-install`, `zypper` | Native to each rootfs |
| libc | All three are **glibc** | Void archive has `ld-linux-aarch64.so.1` (not musl) |
| Default user | `flux` / `flux`, NOPASSWD sudo | Alpine/Debian parity |
| Login shell | zsh after customization | Same Flux `.zshrc` + Oh My Zsh (host-staged) |
| GUI | Shared `start_gui.sh` (proot) + generic guest chroot GUI | Alpine already parameterized `CHROOT_ROOT` |
| HW accel | Mesa in family + dedicated `hw_accel` component | VirGL default; Fedora may try Turnip fedora_43 tarball |
| Rootfs shipping | Pin `.tar.xz` in APK assets (aapt2-safe) | Same as Debian; **not** `.tar.gz` |
| Kotlin SSOT | `DistroInstallProfile` | Callers must not grow debian/alpine `when` trees |
| Scripts | Per-family guest + **one** generic chroot host trio | High cohesion, low duplication |

---

## 1. Rootfs identity (pinned)

| Distro | Source file | Packaged name | SHA-256 | Size |
|--------|-------------|----------------|---------|------|
| Fedora 43 container | `/home/abhaybyte/Downloads/fedora-rootfs-arm64.tar.xz` | `fedora_43_rootfs.tar.xz` | `baade82fcea89be5986ee6e0dd3cd8ff04125bf7995c0e9fc3db5020fb0722fd` | 35 MiB |
| Void 20250202 | `/home/abhaybyte/Downloads/void-arm64-ROOTFS-20250202.tar.xz` | `void_20250202_rootfs.tar.xz` | `01a30f17ae06d4d5b322cd579ca971bc479e02cc284ec1e5a4255bea6bac3ce6` | 44 MiB |
| openSUSE TW 20251127 | `/home/abhaybyte/Downloads/openSUSE-Tumbleweed-rootfs-arm64.tar.xz` | `opensuse_tumbleweed_rootfs.tar.xz` | `bdcb8522a9672cfa513081313b2788f8844340e800918d16a2154e4ed785a12a` | 22 MiB |

`os-release` facts:

- Fedora: `ID=fedora` `VERSION_ID=43` (Container Image Prerelease). Has `dnf` + `dnf5`, `tsflags=nodocs`.
- Void: `ID=void`. XBPS `architecture=aarch64`, repo `https://repo-default.voidlinux.org/current/aarch64`.
- openSUSE: `ID=opensuse-tumbleweed` `VERSION_ID=20251127`. OSS repo `http://download.opensuse.org/ports/aarch64/tumbleweed/repo/oss/`.

Asset path: `assets/rootfs/<name>.tar.xz` (xz is already `noCompress`).  
Deployed home name = packaged name. Min-size gates: Fedora/Void ≥ 20 MiB, openSUSE ≥ 15 MiB.

---

## 2. Cards and paths

| Card id | UI name | Method | proot-distro name | Chroot path |
|---------|---------|--------|-------------------|-------------|
| `fedora` | Fedora | proot | `fedora` | — |
| `fedora_chroot` | Fedora (Rooted) | chroot | — | `/data/local/tmp/chrootFedora` |
| `void` | Void | proot | `void` | — |
| `void_chroot` | Void (Rooted) | chroot | — | `/data/local/tmp/chrootVoid` |
| `opensuse` | openSUSE | proot | `opensuse` | — |
| `opensuse_chroot` | openSUSE (Rooted) | chroot | — | `/data/local/tmp/chrootOpenSUSE` |

Replace the existing **Coming Soon** stubs for `fedora` / `void` / `opensuse` with live cards (do not leave duplicate ids).

---

## 3. Components (required only)

Each of the six cards gets the same three components (Alpine-style, plus HW accel as requested):

| id | Script (per family) | Role |
|----|---------------------|------|
| `xfce4_desktop` | `setup_<family>_family.sh` | DNS, repos, base pkgs, XFCE, dbus, user `flux`, sudo, Mesa, `/etc/fluxlinux/gpu_mode` |
| `hw_accel` | `setup_hw_accel_<family>.sh` | Extra GL/Vulkan, `gpu_mode`, gpu-launch wrapper; Fedora may fetch Turnip `fedora_43` tarball |
| `customization` | `setup_customization_<family>.sh` | Space theme, Papirus, Vimix, JetBrainsMono Nerd, xfconf XML, zsh/OMZ, PM wrapper |

Onboarding still runs **family + customization** (same phases as Alpine). Family **must** install Mesa + write `gpu_mode=virgl` so the first Start paints. The `hw_accel` component is marked installed after family when Mesa/`gpu_mode` are present; Distro Settings can re-run the dedicated script.

---

## 4. Script map

### Guest (inside the container)

```
scripts/fedora/common/setup/
  setup_fedora_family.sh
  setup_customization_fedora.sh
  setup_hw_accel_fedora.sh
scripts/void/common/setup/
  setup_void_family.sh
  setup_customization_void.sh
  setup_hw_accel_void.sh
scripts/opensuse/common/setup/
  setup_opensuse_family.sh
  setup_customization_opensuse.sh
  setup_hw_accel_opensuse.sh
scripts/common/setup/
  flux_guest_common.sh          # sourced: DNS, user, sudo, dbus, home, gpu_mode
  setup_customization_xfce.sh   # sourced: theme/icon/font/xfconf/zsh (PM-agnostic)
```

Family scripts are the **only** place package names live. Customization sources the shared XFCE branding script after installing `zsh git fontconfig unzip curl wget`.

### Host (proot — reuse)

| Script | Change |
|--------|--------|
| `debian/proot/setup/flux_install.sh` | `case` for `fedora`/`void`/`opensuse` (rootfs name + family script). `FLUX_ROOTFS_*` still wins. |
| `debian/proot/start/start_gui.sh` | No change (already takes proot name). |
| `debian/proot/stop/stop_gui.sh` | No change. |

### Host (chroot — generic, env-driven)

Alpine start/stop are already `CHROOT_ROOT`-parameterized. New distros share **one** pair:

| Script | Role |
|--------|------|
| `scripts/chroot/setup_guest_chroot.sh` | Extract pinned xz, DNS, android groups, flux user, helper, `.flux_configured`. **No** XFCE (family phase). |
| `scripts/chroot/uninstall_guest_chroot.sh` | Unmount + `rm -rf $FLUX_CHROOT`. Do not delete shared helper. |
| `scripts/chroot/start_guest_gui.sh` | Copy of Alpine guest launcher; default `CHROOT_ROOT` from env. |
| `scripts/chroot/stop_guest_gui.sh` | Copy of Alpine stop; env path. |
| `scripts/chroot/start_gui_chroot.sh` | Dispatch `fedora_chroot` / `void_chroot` / `opensuse_chroot` → path + `start_guest_gui.sh`. |

Kotlin profiles all point `chrootSetupAsset` at `setup_guest_chroot.sh` and `chrootStartGuiScript` at `start_guest_gui.sh`. Identity is **only** `FLUX_CHROOT` + rootfs env.

---

## 5. Family setup (per PM)

Common (via `flux_guest_common.sh`):

1. Must run as root.
2. `PATH` + sticky `/tmp` `/var/tmp`; unset `PROOT_TMP_DIR`.
3. Write `resolv.conf` if empty (8.8.8.8 / 1.1.1.1).
4. Create `flux` (uid 1000 if free), password `flux`, groups `wheel`/`audio`/`video`/`input` (best-effort).
5. `flux ALL=(ALL) NOPASSWD:ALL` + `@includedir /etc/sudoers.d`.
6. dbus machine-id.
7. Home ownership: prefer `/home` owner (proot app uid), then `flux:flux`.
8. Write `/etc/fluxlinux/gpu_mode` = `virgl`.
9. VNC `xstartup` → `startxfce4` (parity).
10. Proot-safe PM DB ownership (chown cache/db to owner of `/etc`).

### Fedora (`dnf` / `dnf5`)

```
dnf -y --setopt=install_weak_deps=False --setopt=tsflags=nodocs install \
  bash sudo shadow-utils passwd ca-certificates curl wget unzip tar \
  dbus dbus-x11 \
  xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
  xfdesktop xfwm4 thunar \
  adwaita-icon-theme dejavu-sans-fonts \
  mesa-dri-drivers mesa-libGL mesa-libEGL mesa-vulkan-drivers
```

Do **not** `groupinstall "Xfce Desktop"` (pulls lightdm/NetworkManager).  
Disable guest SELinux if present (`SELINUX=disabled`).  
Fix `/var/lib/dnf` `/var/cache/dnf` `/var/lib/rpm` ownership after root-owned writes.

### Void (`xbps-install`)

Assert `architecture=aarch64` and main repo `…/current/aarch64` (never musl).

```
xbps-install -Syu
xbps-install -y \
  bash sudo shadow ca-certificates curl wget unzip \
  dbus dbus-x11 \
  xfce4 xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
  xfdesktop xfwm4 Thunar \
  adwaita-icon-theme dejavu-fonts-ttf \
  mesa mesa-dri
```

Best-effort extras if names differ (`thunar` vs `Thunar`).  
Fix `/var/db/xbps` `/var/cache/xbps` ownership.

### openSUSE (`zypper`)

```
zypper --non-interactive --gpg-auto-import-keys refresh
zypper --non-interactive install --no-recommends \
  bash sudo shadow ca-certificates curl wget unzip tar \
  dbus-1 dbus-1-x11 \
  xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
  xfdesktop xfwm4 thunar \
  adwaita-icon-theme dejavu-fonts \
  Mesa-dri Mesa-libGL1 Mesa-libEGL1
```

Fix `/var/cache/zypp` `/var/lib/zypp` ownership.

Fail closed if `/usr/bin/startxfce4` is missing.

---

## 6. Customization (theme, icons, font, xfconf)

Shared `setup_customization_xfce.sh` (Alpine branding, PM-agnostic):

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

`.zshrc`: Alpine defensive profile + PM wrappers:

```sh
dnf() { command sudo dnf "$@"; }
dnf5() { command sudo dnf5 "$@"; }
xbps-install() { command sudo xbps-install "$@"; }
zypper() { command sudo zypper "$@"; }
```

Only define a wrapper when the binary exists.

---

## 7. Hardware acceleration

| Distro | Packages | Extra |
|--------|----------|--------|
| Fedora | mesa-dri-drivers, mesa-libGL, mesa-vulkan-drivers, mesa-utils, vulkan-tools | Optional Turnip tarball `fedora_43` from lfdevs (same URL family as Debian script). Fallback virgl. |
| Void | mesa, mesa-dri, mesa-dri32 (skip if missing) | VirGL only |
| openSUSE | Mesa-dri, Mesa-libGL1, Mesa-vulkan-device-select (best-effort) | VirGL only |

Always write `/etc/fluxlinux/gpu_mode` (`virgl` unless Turnip install succeeded → `turnip`).  
Start scripts already read this file (Alpine guest block).

Do **not** ship Debian `apt` Turnip into Void/SUSE.

---

## 8. Kotlin / Gradle (decoupled wiring)

### `SupportedDistro`

Add `FEDORA` (`dnf`, `SEMI_ROLLING`), `VOID` (`xbps`, `ROLLING`), `OPENSUSE` (`zypper`, `ROLLING`). Families `FEDORA` / `VOID` / `SUSE` already exist.

### `DistroInstallProfile`

Six profiles. New optional `hwAccelScript`.  
`allRootfsProfiles()` = Debian + Alpine + Fedora + Void + openSUSE (dedupe by file name).  
`isInstallable` true for all six ids.

### `terminalComponentFor`

**Stop hardcoding ids.**  
`DistroInstallProfile.forId(id)?.method` → `TERMUX_FLUX_TERMINAL` vs `CHROOT_ROOT_SHELL`. Unknown still throws.

### `ChrootPaths`

Add `FEDORA_CHROOT_PATH`, `VOID_CHROOT_PATH`, `OPENSUSE_CHROOT_PATH`.  
`pathForDistro` covers the three new chroot ids.

### `TerminalShellCatalog`

Data-driven sections from `DistroInstallProfile` installable cards (proot then chroot, then host).  
Extend `TerminalShellAvailability` with fedora/void/opensuse booleans **or** a `Map<String, Boolean>` keyed by card id. Prefer a map so the next distro is one profile, not six catalog fields.

`prootDefs` / `chrootDefs` take any card id and pick label/icon from `DistroRepository` (or a small table).

### `HostScriptDeployer`

Deploy family + customization + hw + generic chroot setup/start/stop/uninstall.

### `GuestZshrcRepair`

`resolveProotName` already falls through to `removeSuffix("_chroot")` — works for `fedora` / `void` / `opensuse`.  
`profileFor` appends PM wrappers when those binaries exist (not Alpine-only).

### `OnboardingFlowScreen`

Replace alpine/debian-only copy with `DistroInstallProfile.displayName` + family name.

### `OnboardingInstallRunner`

Already profile-driven. After family success, `setComponentInstalled(..., "hw_accel", true)` when profile has `hwAccelScript` (Mesa installed by family).

### Gradle `stageHostRootfs`

Copy the three xz archives from `assets/rootfs/` into `app/src/main/assets/rootfs/` (no rename).

---

## 9. Tests (unit)

| File | Updates |
|------|---------|
| `DistroInstallProfileTest` | Six new profiles; SHA hex; asset not `.gz`; `allRootfsProfiles` size 5; `void` installable |
| `DistroRepositoryTest` | 10 live cards; each new pair is proot-only / chroot-only |
| `TerminalComponentTest` | fedora/void/opensuse + chroots; unknown still throws |
| `TerminalShellCatalogTest` | New sections enabled/disabled; order includes new guests |
| `ChrootPathsTest` | Three new paths distinct |
| `BaseDesktopInstallPlanTest` | `distroById` + `methodFor` + `profileFor` for the six ids |
| `GuestZshrcRepairTest` | `resolveProotName("fedora_chroot") == "fedora"` etc. |

No Android instrumentation in this pass.

---

## 10. Device test matrix (must all pass)

Package: `com.ivarna.fluxlinux`. Build: `:app:assembleIvarnaRelease`. Install: `adb install -r`.

For **each** of `{fedora, void, opensuse}` × `{proot, chroot}`:

| # | Step | Pass |
|---|------|------|
| I1 | Distros / onboarding install completes | `startxfce4` present; `.flux_configured` (chroot) |
| T1 | Terminal card opens flux shell | Prompt; zsh after customization |
| T2 | `sudo -n id` | uid 0 |
| T3 | PM works | `sudo dnf/xbps-install/zypper` refresh or query; no lock EACCES |
| D1 | Home → Start XFCE | Log shows Pulse + X server PID + startxfce4 |
| D2 | Embedded X11 activity | `com.termux.x11.MainActivity` resumed |
| D3 | Desktop paints | Screenshot: panel + wallpaper; **no** failsafe dialog |
| D4 | Theme / icons / font | GTK Space theme, Papirus icons, Nerd font in xfce4-terminal (visual) |
| D5 | Stop | Card returns to Start; X11 idle |

If any cell fails: fix the owning script/profile, rebuild **release**, `adb install -r`, retest that path (and regress the last passing sibling if the change was shared).

Storage: device has ~104 GB free — enough for six XFCE trees sequentially. Uninstall a chroot via the in-app uninstall script only if `/data` is exhausted; do not uninstall the FluxLinux APK.

---

## 11. Implementation order

1. Copy + pin rootfs; Gradle stage.  
2. Kotlin SSOT + catalog + tests (compile-safe).  
3. Guest family / customization / hw scripts + generic chroot host scripts.  
4. `flux_install.sh` + `start_gui_chroot.sh` + deployer list.  
5. Unit tests green.  
6. Release APK → device.  
7. E2E Fedora proot → Fedora chroot → Void proot → Void chroot → openSUSE proot → openSUSE chroot. Fix-forward.

---

## 12. Out of scope

- Arch Linux (no archive).  
- KDE / VirGL-Turnip-Software *KDE* launchers.  
- Feature modules.  
- Multi-arch (x86_64).  
- Changing Debian/Alpine behavior except shared catalog/profile helpers.

---

## 13. Key files

| Path | Action |
|------|--------|
| `app/src/main/kotlin/.../DistroInstallProfile.kt` | Six profiles |
| `app/src/main/kotlin/.../DistroSpec.kt` | Three enum values |
| `app/src/main/kotlin/.../DistroRepository.kt` | Live cards + components |
| `app/src/main/kotlin/.../TerminalComponent.kt` | Profile-driven |
| `app/src/main/kotlin/.../TerminalShellCatalog.kt` | Data-driven |
| `app/src/main/kotlin/.../ChrootPaths.kt` | Three paths |
| `app/src/main/kotlin/.../HostScriptDeployer.kt` | New scripts |
| `app/src/main/kotlin/.../GuestZshrcRepair.kt` | PM wrappers |
| `app/src/main/assets/scripts/{fedora,void,opensuse}/…` | Guest scripts |
| `app/src/main/assets/scripts/chroot/setup_guest_chroot.sh` | Shared extract |
| `app/src/main/assets/scripts/chroot/start_guest_gui.sh` | Shared XFCE |
| `app/build.gradle.kts` | Stage three xz |
| `docs/distro/{fedora,void,opensuse}.md` | Operator refs (after device pass) |

---

*This file is the implementation contract. Keep SHA-256 and card ids in sync with `DistroInstallProfile`.*
