# Slim report: Arch Linux ARM official rootfs

**Date:** 2026-08-13
**Plan:** `docs/plan/archlinux-alarm-slim-rootfs.md` (Option 2 — extract, drop hardware trees, recompress xz)
**Status:** SLIM DONE

## Source

| Field | Value |
|-------|-------|
| URL | `https://fl.us.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz` |
| Bytes | `829367415` (791 MiB) |
| SHA-256 | `42a4eeaa038994ffd31fa173256ef2f0ef511358eeb41b9ea1f8626391b9b319` |

Matches the size observed in the plan (2026-08-05 image).

## Sizes before / after

| Measurement | Value |
|-------------|-------|
| Extracted rootfs (before slim) | **2.1 GiB** |
| `/boot` | 193 MiB |
| `/usr/lib/firmware` | 958 MiB |
| `/lib/modules` | 185 MiB |
| `/usr/share/man` | 37 MiB |
| `/usr/share/doc` | 13 MiB |
| Rootfs after slim | **699 MiB** |
| Rootfs after orphan purge (re-pack) | **691 MiB** |
| Packaged `archlinux_arm_rootfs.tar.xz` | **116,277,544 bytes (~111 MiB)** — gate 40–250 MiB: PASS |

`/usr/lib/modules` and `/lib/firmware` did not exist in this image.

## Local-db prefixes removed (16 packages)

`linux-aarch64-7.1.6-1`, `linux-api-headers-7.1-1`,
`linux-firmware-{20260622,amdgpu,atheros,broadcom,cirrus,intel,mediatek,nvidia,other,radeon,realtek,whence}-20260622-1` (12 pkgs),
`mkinitcpio-41-4`, `mkinitcpio-busybox-1.36.1-1`.

`linux-aarch64` DB dir also covered `/boot` purge; no `uboot-tools` / `crda` / `wireless-regdb` present.
Keyrings kept: `archlinuxarm-keyring-20240419-2`, `archlinux-keyring-20260727-1`.
`openssh-10.4p1-3` kept (files + DB per plan §4.6).

## Packaged artifact

| Field | Value |
|-------|-------|
| File | `assets/rootfs/archlinux_arm_rootfs.tar.xz` |
| SHA-256 | `40209ef6318d3aad732299d46ce224c6a0ecded80b6f8091f5e38b40fa031d75` |
| Bytes | `116277544` |
| Compression | `xz -T0 -9`, archive root `./` (no top dir) |

## os-release

```
NAME="Arch Linux ARM"
PRETTY_NAME="Arch Linux ARM"
ID=archarm
ID_LIKE=arch
BUILD_ID=rolling
HOME_URL="https://archlinuxarm.org/"
```

## Verification (§9)

| # | Check | Result |
|---|-------|--------|
| S1 | Source gzip SHA recorded | `source.sha256` + this report |
| S2 | `pacman` + aarch64 loader present | PASS (`/usr/bin/pacman`, `/usr/lib/ld-linux-aarch64.so.1`) |
| S3 | Before/after `du` logged | PASS (2.1 GiB → 699 MiB; firmware/boot/modules = 1.34 GiB) |
| S4 | `/boot/Image` and `/usr/lib/firmware` gone | PASS |
| S5 | `linux-aarch64-*` / `linux-firmware-*` local-db gone | PASS (16 pkgs purged) |
| S6 | `pacman`, `bash`, `pacman.conf`, `mirrorlist` present | PASS |
| S7 | xz 40–250 MiB | PASS (111 MiB) |
| S8 | Packaged SHA recorded | PASS (see above) |
| S9 | File at `assets/rootfs/archlinux_arm_rootfs.tar.xz` | PASS |
| S10 | `tar -tJf` spot-check | `./usr/bin/pacman` present; no `boot/Image`, no `usr/lib/firmware`, no `lib/modules`, no `mkinitcpio`/`initcpio` remnants, no `licenses/linux-firmware-*`, no `usr/include/linux`. Only `./usr/lib/modules-load.d/` remains (systemd config dir, not kernel modules) |

Smoke: `file usr/bin/pacman` / `usr/bin/bash` → ELF 64-bit ARM aarch64, interpreter `/lib/ld-linux-aarch64.so.1`.

Pacman local-db count after slim: **149 packages** (`ls | wc -l` = 150 including the `ALPM_DB_VERSION` file).

## Orphan purge (review follow-up, 2026-08-13)

File-level leftovers from DB-purged packages, deleted before re-pack (review's note 1):

| Removed | Package |
|---------|---------|
| `/etc/mkinitcpio.conf`, `/etc/mkinitcpio.conf.d`, `/etc/initcpio`, `/usr/bin/mkinitcpio`, `/usr/bin/lsinitcpio`, `/usr/share/mkinitcpio`, bash/zsh completions, `/usr/share/libalpm/scripts/mkinitcpio`, libalpm hooks, tmpfiles entry, `usr/lib/kernel/install.d/50-mkinitcpio.install`, systemd shutdown-ramfs units/wants | `mkinitcpio` + `mkinitcpio-busybox` |
| `/usr/share/licenses/linux-firmware-*` (12 dirs, ~1 MiB) | `linux-firmware-*` |
| `/usr/include/linux` (7.1 MiB UAPI headers) | `linux-api-headers` |

Systemd-owned `/usr/lib/kernel/install.d/*` (loaderentry/depmod/uki-copy) and `systemd-pcrlock-firmware-*` were left untouched. Rootfs 699 → 691 MiB; re-packed xz 112 → 111 MiB, `xz -t` clean, tar exit 0.

## Host notes (deviations from plan §4)

- Workdir was `/tmp/opencode/flux-alarm-slim` (tmpfs): `bsdtar` failed to restore `security.capability` xattrs on tmpfs (`newuidmap`/`newgidmap` only); extracted with `--no-xattrs --no-acls`. GNU `tar` also aborted on the image's mode-000 dirs, so bsdtar with xattrs off was used. FluxLinux does not need those caps/xattrs.
- No sudo on host → default file+DB-delete path used (§4.5).
- `chmod -R u+rX` applied before packing because the image ships some mode-000 files (`usr/lib/dbus-daemon-launch-helper`, tpm2 keystore, ca-certs dir) that a non-root `tar` cannot read.
- `/etc/resolv.conf` was a dangling symlink (systemd stub) → replaced with a real file (8.8.8.8 / 1.1.1.1).
- `/etc/machine-id` and `var/lib/dbus/machine-id` cleared; ssh host keys removed.
