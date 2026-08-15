# Alpine Linux in FluxLinux

Complete reference for **Alpine Linux** support in FluxLinux: identity, install modes (proot + chroot), rootfs packaging, scripts, desktop, terminal, packages (`apk`), known issues, and developer SSOT.

---

## 1. Overview

| | |
|---|---|
| **Upstream** | [Alpine Linux](https://alpinelinux.org/) |
| **Version (pinned)** | **3.24.1** (aarch64 minirootfs) |
| **libc** | **musl** (not glibc) |
| **Package manager** | **`apk`** (`apk-tools`) |
| **Release model** | Fixed (stable branch `v3.24`), not rolling |
| **Default desktop** | **XFCE4** |
| **Default user** | `flux` / password `flux` (NOPASSWD sudo) |
| **Default login shell (after customization)** | **zsh** + Oh My Zsh (when staged) |
| **App cards** | `alpine` (proot) · `alpine_chroot` (rooted) |

Alpine is the lightweight FluxLinux distro: small rootfs downloaded on demand, fast boot, musl/`apk` instead of Debian’s glibc/`apt`. It is a **first-class** install path alongside Debian—not a “coming soon” stub.

### Why Alpine on Android

- **Small base**: official minirootfs is a few MiB before XFCE packages.
- **Security-oriented upstream** defaults and simple layout.
- **apk** is fast for installs once the index is warm.
- Coexists with Debian proot/chroot on the same device (separate paths).

### Caveats (read this)

- **musl ≠ glibc**: many prebuilt glibc binaries (AppImages, some third-party tools) will **not** run. Prefer packages from Alpine repos or musl-compatible builds.
- **Not a full Debian module stack**: Alpine does **not** ship the Debian “appdev / webdev / gamedev / …” component scripts. MVP components are **XFCE4 Desktop** + **Customization** only.
- **Proot is not a real kernel root**: `sudo` elevates the *guest* identity, but filesystem ownership must match the **Android app uid** or `apk` locks fail (see [§9](#9-known-issues--fixes)).

---

## 2. Cards and install methods

| Card id | UI name | Method | Root required | Container / path |
|---------|---------|--------|---------------|------------------|
| `alpine` | **Alpine** | **proot** | No | proot-distro name `alpine` |
| `alpine_chroot` | **Alpine (Rooted)** | **chroot** | Yes (KernelSU / Magisk + BusyBox) | `/data/local/tmp/chrootAlpine` |

Both cards share:

- Same rootfs archive identity  
- Same guest scripts: `setup_alpine_family.sh`, `setup_customization_alpine.sh`  
- Same package family (`SupportedDistro.ALPINE` / `PackageManager.APK`)

Registration: `DistroRepository` + `DistroInstallProfile` + `SupportedDistro.ALPINE`.

---

## 3. Rootfs (SSOT)

Defined in `DistroInstallProfile` (Kotlin):

| Field | Value |
|-------|--------|
| **Upstream-style name (home deploy)** | `alpine_3.24_rootfs.tar.gz` |
| **Release asset (GitHub tag `rootfs`)** | `alpine_3.24_rootfs.tar.gz` |
| **Legacy APK asset path (unused)** | `assets/rootfs/alpine_3.24_rootfs.minirootfs` |
| **SHA-256** | `f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259` |
| **Min size check** | ≥ 1 MiB |
| **Arch** | aarch64 |
| **Source** | Official Alpine minirootfs 3.24.1 |

### Rootfs distribution (current)

Rootfs archives are **not packaged in the APK**. The Alpine archive ships as
`alpine_3.24_rootfs.tar.gz` on the GitHub release tag `rootfs` and is
downloaded by `RootfsDownloader` at install time (local-first: a verified
`$HOME/alpine_3.24_rootfs.tar.gz` short-circuits the network). The `.minirootfs`
name existed only to dodge aapt2 `*.gz` auto-decompression inside the APK —
with no packaging, there is no rename (D8). The runtime/release name is always
the real `.tar.gz`.

### On-device locations

| Mode | Rootfs path |
|------|-------------|
| **Proot** | `$filesDir/usr/var/lib/proot-distro/containers/alpine/rootfs` |
| **Chroot** | `/data/local/tmp/chrootAlpine` |

Proot also keeps container metadata under:

`…/proot-distro/containers/alpine/` (`rootfs/`, `sysdata/`, locks/sessions under proot-distro).

---

## 4. User, sudo, groups

| | |
|---|---|
| **User** | `flux` |
| **Password** | `flux` |
| **Sudo** | `flux ALL=(ALL) NOPASSWD:ALL` in `/etc/sudoers.d/flux` |
| **Groups (best-effort)** | `wheel`, `audio`, `video`, `netdev`, `input` |
| **Preferred UID** | `1000` when free at create time (else next available; proot may show high UIDs e.g. 10302) |

Family setup creates `flux` via `useradd` / `adduser` if missing, sets password, and configures sudoers (`@includedir /etc/sudoers.d` asserted).

### Packages (terminal)

After customization, interactive zsh may define:

```sh
apk() { command sudo apk "$@"; }
```

So plain `apk update` / `apk add …` work without typing `sudo` (when the function is loaded). Explicit form always works:

```sh
sudo apk update
sudo apk add htop
```

Bare `apk` **without** privileges is expected to fail (needs root for DB lock).

---

## 5. Install phases (user flow)

Typical path matches Debian-style Flux onboarding / Distro install:

1. **Host ready** — embedded Termux-class bootstrap, scripts deployed (`HostScriptDeployer`).
2. **Rootfs** — download/verify `alpine_3.24_rootfs.tar.gz` (`RootfsDownloader`), then extract into the proot container or chroot path.
3. **Family (XFCE)** — `setup_alpine_family.sh` as root inside guest (`apk` base + XFCE).
4. **Customization** — themes/icons (host-staged when possible) + zsh/OMZ/fastfetch profile via `setup_customization_alpine.sh`.
5. **Use** — Terminal cards + **Start** desktop (XFCE via shared `start_gui.sh` / chroot `start_alpine_gui.sh`).

### Components (UI)

| Component id | Name | Script | Notes |
|--------------|------|--------|--------|
| `xfce4_desktop` | XFCE4 Desktop | `alpine/common/setup/setup_alpine_family.sh` | Base DE + user + dbus |
| `customization` | XFCE4 Customization | `alpine/common/setup/setup_customization_alpine.sh` | Theme, wallpapers, zsh |

Size estimates in UI are approximate (~250 MB XFCE stack after packages; base minirootfs is tiny).

---

## 6. Scripts and assets map

### Guest (inside Alpine)

| Script | Role |
|--------|------|
| `scripts/alpine/common/setup/setup_alpine_family.sh` | DNS, repos, base packages, XFCE, dbus machine-id, user flux, sudo, bwrap shim, apk DB ownership fix |
| `scripts/alpine/common/setup/setup_customization_alpine.sh` | Themes/icons/wallpaper, OMZ/plugins, `.zshrc`, fastfetch preset, `apk()` wrapper, apk ownership fix |

### Proot host

| Script | Role |
|--------|------|
| `scripts/debian/proot/setup/flux_install.sh` | Shared installer; Alpine selected via env / profile (`FLUX_ROOTFS_*`, proot name `alpine`) |
| `scripts/debian/proot/start/start_gui.sh` | Shared XFCE launcher for **all** proot distros including Alpine |
| `scripts/debian/proot/stop/stop_gui.sh` | Shared stop |

### Chroot host

| Script | Role |
|--------|------|
| `scripts/chroot/setup_alpine_chroot.sh` | Extract rootfs under `/data/local/tmp/chrootAlpine`, mounts/bootstrap hooks |
| `scripts/chroot/uninstall_alpine_chroot.sh` | Tear down Alpine chroot |
| `scripts/chroot/start_gui_chroot.sh` | Host Pulse/VirGL/X11; dispatches Alpine vs Debian guest |
| `scripts/chroot/start_alpine_gui.sh` | Root mounts + `su` flux + `dbus-run-session` / `startxfce4` |
| `scripts/chroot/stop_alpine_gui.sh` | Kill Alpine XFCE/session processes |
| `scripts/chroot/fluxlinux_chroot.sh` | SSOT mount/login helper (`FLUX_CHROOT`) |

### Kotlin SSOT

| Type / object | Alpine-related |
|---------------|----------------|
| `DistroInstallProfile` | `alpine` / `alpine_chroot` profiles, rootfs constants |
| `DistroRepository` | Cards + components |
| `SupportedDistro.ALPINE` | Family + APK package manager |
| `ChrootPaths.ALPINE_CHROOT_PATH` | `/data/local/tmp/chrootAlpine` |
| `TerminalShellCatalog` | Alpine proot + chroot terminal cards |
| `ProotZshBootstrap` | Host-stage OMZ into **correct** proot name (`alpine`) |
| `GuestZshrcRepair` | Multi-distro `.zshrc`; Alpine `apk()` wrapper |
| `GuestApkDbRepair` | Proot apk lock/db writability on session open |
| `HostScriptDeployer` | Deploys Alpine setup/GUI scripts to host `$HOME` |

---

## 7. Family setup details (`setup_alpine_family.sh`)

Runs **as root inside the guest**.

### DNS

Writes `/etc/resolv.conf` if missing/empty:

```text
nameserver 8.8.8.8
nameserver 1.1.1.1
nameserver 8.8.4.4
```

### Repositories

Keeps Alpine **v3.24** main; ensures **community** is present:

```text
https://dl-cdn.alpinelinux.org/alpine/v3.24/main
https://dl-cdn.alpinelinux.org/alpine/v3.24/community
```

### Base packages (excerpt)

`bash`, `sudo`, `shadow`, `ca-certificates`, `curl`, `wget`, `unzip`, `tar`, `tzdata`, `musl-locales`, `dbus`, `dbus-x11`

### Desktop packages (excerpt)

`xfce4`, `xfce4-session`, `xfce4-settings`, `xfce4-panel`, `xfce4-terminal`, `xfce4-screensaver`, `xfdesktop`, `xfwm4`, `thunar`, icons/fonts, `mesa-dri-gallium`, `mesa-gl`

### D-Bus machine-id

Ensures `/etc/machine-id` and `/var/lib/dbus/machine-id` for session bus stacks.

### Glycin / bwrap (proot)

GTK image loaders may invoke **bubblewrap**. Real `bwrap` fails under proot (GTK abort). Family installs a **FluxLinux proot-safe `/usr/bin/bwrap` shim** (and related glycin packages when available). Proot `start_gui.sh` also sets:

- `GLYCIN_DISABLE_SANDBOX=1`
- `GDK_DEBUG=no-glycin`
- `GSK_RENDERER=cairo`

### apk DB writability (proot-critical)

After install, family runs `_flux_fix_apk_writable`:

- Chown `/lib/apk`, `/var/cache/apk`, `/var/log`, `/etc/apk` to match **owner of `/etc`** (app uid under proot).
- Recreate `/lib/apk/db/lock` mode `666`.
- Touch `/var/log/apk.log` mode `666`.

Without this, `sudo apk update` can fail with **Permission denied** even though `sudo -n id` returns root.

---

## 8. Customization (`setup_customization_alpine.sh`)

- Installs `zsh`, `git`, `fastfetch` (best-effort), fonts tooling, screenshooter, etc.
- Themes/icons/wallpapers: same Flux asset story as Debian when host-staged (`FLUX_SKIP_THEME_ICONS`, `FLUX_ASSET_DIR`).
- Writes XFCE xfconf XML (theme, panel compositing off, wallpaper).
- **Oh My Zsh**: prefer host pre-stage (`ProotZshBootstrap` + `FLUX_SKIP_OMZ=1`); guest git clone fallback with timeouts (not bare `curl | sh` hang path).
- Writes defensive **`.zshrc`**: async fastfetch/pokemon guards, OMZ + agnosterzak, **`apk()` → sudo apk**.
- Downloads fastfetch `termux.jsonc` preset when network allows.
- `chsh` flux → zsh when available; ensures `/etc/shells`.

Pokemon colorscripts are **optional** and **skipped by default** under proot (`FLUX_SKIP_POKEMON=1`) because GitLab clones often stall.

---

## 9. Desktop (XFCE)

### Proot

- Shared launcher: `$HOME/start_gui.sh alpine`
- Host: Pulse → optional VirGL → termux-x11 `:0` → `proot-distro login alpine` → `su -s bash - flux` → session bus + `startxfce4`
- Env hardened for failsafe session: `XDG_CONFIG_DIRS=/etc/xdg`, private `XDG_RUNTIME_DIR`, unset leaked `DBUS_SESSION_BUS_*`, proot-safe home ownership (match `/home` owner; open perms on `.config` if needed)
- Prefer `dbus-run-session -- startxfce4` when available

### Chroot

- Host: `start_gui_chroot.sh alpine_chroot` → root runs `start_alpine_gui.sh`
- Mounts via `fluxlinux_chroot.sh` when present (`FLUX_CHROOT=/data/local/tmp/chrootAlpine`)
- Same XDG/dbus/glycin patterns as proot guest block

### Failsafe dialog (historical)

“Unable to load a failsafe session” was typically:

1. xfconfd could not write `~/.config` (ownership/mode under proot), and/or  
2. Bad/missing `XDG_CONFIG_DIRS` / session D-Bus.

Fixed in start scripts + family home ownership policy. Device proof: `docs/plans/results/alpine_proot_xfce_pass.png`.

---

## 10. Terminal

| Card style | Method | Notes |
|------------|--------|--------|
| Alpine flux / root | proot | `proot-distro login alpine --user …` |
| Alpine Chroot | chroot | Helper login; **zsh** when installed (resolved as root; SELinux hides `/data/local/tmp` from the app) |

Session open hooks:

- `GuestZshrcRepair` — create/repair Flux `.zshrc` + `.zprofile`, Alpine `apk()` wrapper, prefer zsh in passwd when binary exists. Chroot writes may skip (SELinux); helper seeds the profile as root on login.
- `GuestApkDbRepair` — Alpine proot only; best-effort apk lock/chmod when app can write the tree.

---

## 11. Package management (`apk`)

### Everyday usage

```sh
# Explicit
sudo apk update
sudo apk upgrade
sudo apk add htop curl
sudo apk del htop
sudo apk search nano
sudo apk info nano

# With zsh wrapper (after customization)
apk update
apk add figlet
```

### Repos

Pinned to **v3.24** main + community (CDN: `dl-cdn.alpinelinux.org`).

### Proot ownership rule

| Symptom | Cause | Fix |
|---------|--------|-----|
| `sudo apk …` → **Unable to lock database: Permission denied** | `/lib/apk/db/*` owned by **host root (0)** while process is app uid | Family/customization `_flux_fix_apk_writable`; host `chown -R $APP_UID` on apk paths; `GuestApkDbRepair` |
| bare `apk update` as flux | No privileges | Expected — use `sudo` or `apk()` wrapper |
| DNS / “no such package” with stale index | Network/DNS under some SELinux/`adb su` contexts | Prefer **in-app** terminal; check `/etc/resolv.conf`; chroot usually has full network |
| `proot error: can't create temporary directory` / `can't create glue rootfs` | Host `PROOT_TMP_DIR` was the same as `--shared-tmp` (`$PREFIX/tmp`), **and** proot-distro dropped `PROOT_TMP_DIR` from the host proot child | Private `$filesDir/proot-tmp`; patch login `__init__.py` allowlist to pass `PROOT_TMP_DIR`; guest `TMPDIR=/tmp` via `env -i` |
| Alpine chroot “no zsh configured” (bare ash) | App uid cannot `stat /data/local/tmp` (SELinux), so login probed “no zsh” and forced `sh` | Always request zsh; helper v2.3 resolves the binary as root and seeds `~/.zshrc` + `~/.zprofile` |

Device report: `docs/plans/results/alpine-apk-sudo-device-report.md`.

---

## 12. Comparison: Alpine vs Debian (FluxLinux)

| | Alpine | Debian |
|---|--------|--------|
| libc | musl | glibc |
| packages | apk | apt |
| rootfs distribution | download at install (GitHub tag `rootfs`, ~3.8 MiB `.tar.gz`) | download at install (GitHub tag `rootfs`, ~81 MiB `.tar.xz`) |
| cards | `alpine`, `alpine_chroot` | `debian`, `debian13_chroot` |
| proot path | `…/containers/alpine/rootfs` | `…/containers/debian/rootfs` |
| chroot path | `/data/local/tmp/chrootAlpine` | `/data/local/tmp/chrootDebian13` |
| family script | `setup_alpine_family.sh` | `setup_debian_family.sh` |
| modules (appdev, webdev, …) | **No** (MVP) | Yes |
| GUI start proot | Shared `start_gui.sh` | Same |
| Default DE | XFCE4 | XFCE4 |

---

## 13. Developer checklist (SSOT)

When changing Alpine support:

1. Update **`DistroInstallProfile`** constants if rootfs version/SHA changes.  
2. Pin the new `ROOTFS_URL` / upload the archive to the GitHub release tag `rootfs` — **do not** package it in the APK and do **not** rename it (the `.minirootfs` aapt2 dodge is obsolete).  
3. Do not hardcode `debian` only for OMZ/theme staging — always pass **`prootName`** (`alpine`).  
4. Guest scripts must remain **POSIX sh-friendly** where minirootfs is ash-only until bash is installed.  
5. After apk operations that might run as real host root into the tree, re-run **apk ownership fix**.  
6. Deploy scripts via **`HostScriptDeployer`** list when adding new host script files.  
7. Unit tests: install profile, shell catalog, guest rootfs shell probe (`bin/sh` may be absolute busybox symlink).

---

## 14. Manual test matrix (device)

| # | Action | Pass criteria |
|---|--------|----------------|
| 1 | Install Alpine proot (onboarding / Distros) | Rootfs + `startxfce4` present |
| 2 | Terminal → Alpine flux | zsh profile / fastfetch (after customization) |
| 3 | `sudo -n id` | root |
| 4 | `sudo apk update` | No lock permission denied |
| 5 | `sudo apk add <pkg>` | Package installs (network permitting) |
| 6 | Home → Alpine → **Start** XFCE | Desktop paints; **no** failsafe dialog |
| 7 | Alpine (Rooted) install + terminal + `sudo apk` | Works with root |
| 8 | Debian proot regression | Still works |

Results samples:

- XFCE: `docs/plans/results/alpine_proot_xfce_pass.png`  
- apk/sudo: `docs/plans/results/alpine-apk-sudo-device-report.md`  
- XFCE/zsh: `docs/plans/results/alpine-xfce-zsh-fix-device-report.md`

---

## 15. Quick reference paths

```text
# Proot rootfs
/data/data/<appId>/files/usr/var/lib/proot-distro/containers/alpine/rootfs

# Chroot rootfs
/data/local/tmp/chrootAlpine

# Host scripts (after deploy)
/data/data/<appId>/files/home/start_gui.sh
/data/data/<appId>/files/home/start_gui_chroot.sh
/data/data/<appId>/files/home/start_alpine_gui.sh
/data/data/<appId>/files/home/setup_alpine_family.sh
/data/data/<appId>/files/home/setup_customization_alpine.sh

# Helper
/data/local/tmp/fluxlinux_chroot.sh
```

`<appId>` is typically `com.ivarna.fluxlinux` (ivarna flavor) or the zenithblue applicationId for the other store flavor.

---

## 16. Related docs

| Doc | Topic |
|-----|--------|
| [`docs/adding_new_distro.md`](../adding_new_distro.md) | How distros are wired |
| [`docs/distro_classification_matrix.md`](../distro_classification_matrix.md) | Family / package manager matrix |
| [`docs/plans/results/alpine-xfce-zsh-fix-device-report.md`](../plans/results/alpine-xfce-zsh-fix-device-report.md) | XFCE + shell device report |
| [`docs/plans/results/alpine-apk-sudo-device-report.md`](../plans/results/alpine-apk-sudo-device-report.md) | apk + sudo device report |
| [`docs/script_execution_workflow.md`](../script_execution_workflow.md) | How scripts run |

---

## 17. Status summary

| Area | Status |
|------|--------|
| Proot install + XFCE | Supported / device-tested |
| Chroot install + XFCE | Supported / device-tested |
| Terminal (zsh, OMZ, fastfetch) | Supported after customization |
| `sudo` + `apk` | Supported (proot requires correct apk DB ownership) |
| Debian-style modules (appdev, …) | **Not** on Alpine MVP |
| Multi-arch (x86_64, …) | **aarch64** focus for shipped minirootfs |

---

*Maintained for FluxLinux Alpine 3.24. Keep this file in sync with `DistroInstallProfile` when the pinned rootfs or card IDs change.*
