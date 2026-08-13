# Plan: Slim Arch Linux ARM official tarball (Option 2)

**Date:** 2026-08-13  
**Status:** SLIM DONE 2026-08-13 — packaged SHA-256 `40209ef6318d3aad732299d46ce224c6a0ecded80b6f8091f5e38b40fa031d75` (111 MiB, orphan purge re-pack).  
**Choice:** Option 2 from the Arch rootfs discussion — do **not** ship the 800 MiB file; extract it, drop hardware-only trees, recompress as `.tar.xz`.

**Source URL (canonical):**  
`https://fl.us.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz`  
(same bytes as `http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz`)

**Observed 2026-08-13:** `Content-Length: 829367415` (~791 MiB), `Last-Modified: Wed, 05 Aug 2026 12:41:36 GMT`.

**Not this plan:** Using the Termux proot-distro 156 MiB xz (option 1). Shipping the 800 MiB gzip as an APK asset (option 6). Activating Deepin/Chimera/Manjaro (separate plan). KDE / modules / debug APKs.

**Downstream:** The output of this plan is a pinned `archlinux_arm_rootfs.tar.xz` that can later feed `archlinux` + `archlinux_chroot` the same way Fedora/Void/openSUSE are wired. App integration is outlined in §10 so the artifact is not orphaned; slimming is the work.

---

## 0. Why this file is 800 MiB

ALARM’s `ArchLinuxARM-aarch64-latest.tar.gz` is a **generic ARMv8 board image**, not a container bootstrap. Upstream says it contains the *base* group **plus kernel, firmware, and utilities**, with `linux-aarch64` dropping an EFI-stubbed `Image` / `Image.gz` under `/boot`.

Typical bulk (order of magnitude; measure on extract — §4):

| Tree / package | Why it exists | Needed in FluxLinux? |
|----------------|---------------|----------------------|
| `/usr/lib/firmware` (`linux-firmware`) | every NIC/GPU/Wi-Fi blob | **No** — Android owns firmware |
| `/lib/modules/*` / `/usr/lib/modules/*` | `linux-aarch64` modules | **No** — guest uses the Android kernel |
| `/boot` (`Image`, `Image.gz`, dtbs, initramfs) | real-hardware boot | **No** |
| `mkinitcpio*` | builds initramfs for that kernel | **No** |
| `uboot-tools` / board boot bits | U-Boot | **No** |
| man / doc / info / locale leftovers | full image defaults | **No** (optional extra trim) |
| `base` userspace, `pacman`, glibc, bash | real Arch | **Yes** |
| `openssh`, `haveged` | image defaults | sshd **no**; haveged **keep if small** (helps `pacman-key`) |

Target after slim + `xz -9`: **100–200 MiB**. Fail the job if the xz is still **> 250 MiB** — something was not stripped.

Compare: Manjaro ARM gzip you already have is 202 MiB (userspace-ish). Termux’s slim Arch xz is ~156 MiB. We should land in that band.

---

## 1. Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Input | Official ALARM `…-aarch64-latest.tar.gz` only | User only has this aarch64 file |
| Method | Extract → delete hardware trees → drop matching pacman local pkgs → xz | No QEMU required for the file-level slim |
| Output name | `archlinux_arm_rootfs.tar.xz` | aapt2-safe (never `.tar.gz` in APK assets) |
| Stage path | `assets/rootfs/archlinux_arm_rootfs.tar.xz` | Same as Fedora/Void/openSUSE |
| Deployed home name | same filename | `DistroInstallProfile.rootfsFileName` |
| Min-size gate | ≥ 40 MiB | Reject a truncated xz |
| Max-size gate | ≤ 250 MiB | Reject a failed slim |
| Work dir | `/tmp/flux-alarm-slim` (or `$HOME/tmp/flux-alarm-slim`) | Do not extract inside the git tree |
| Host arch | x86_64 or aarch64 both OK | Slim is file surgery; no guest exec required |
| `pacman -Rns` inside the image | Optional, only if host can run aarch64 (native or qemu) | File delete + local-db purge is the default |
| Keep | `pacman`, glibc, bash, coreutils, gnupg, keyrings, ca-certificates | Guest must `pacman -Sy` later |
| Drop | kernel, firmware, boot, modules, mkinitcpio, U-Boot | Dead on Android |
| aapt2 | Ship **xz only** | Alpine lesson: aapt2 auto-decompresses `*.gz` |

Do **not** rewrite ALARM mirrors to Manjaro. Do **not** mix this rootfs with the Manjaro card.

---

## 2. Output identity (fill in after the slim)

| Field | Value |
|-------|--------|
| Source URL | `https://fl.us.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz` |
| Source bytes (2026-08-13) | `829367415` |
| Source SHA-256 | `42a4eeaa038994ffd31fa173256ef2f0ef511358eeb41b9ea1f8626391b9b319` |
| Packaged file | `assets/rootfs/archlinux_arm_rootfs.tar.xz` |
| Packaged SHA-256 | `40209ef6318d3aad732299d46ce224c6a0ecded80b6f8091f5e38b40fa031d75` — pin in `DistroInstallProfile` |
| Packaged size | `116277544` bytes (~111 MiB) — within 40–250 MiB |
| `os-release` | `ID=archarm`, `ID_LIKE=arch`, `NAME="Arch Linux ARM"`, `BUILD_ID=rolling` |
| libc | glibc, `usr/lib/ld-linux-aarch64.so.1` |
| PM | `pacman`, repos `core` / `extra` via `/etc/pacman.d/mirrorlist` (`$arch/$repo` ALARM layout) |

---

## 3. Host prerequisites

On the machine that will slim (this repo’s Linux box is fine):

```sh
# required
command -v curl
command -v sha256sum
command -v xz
command -v tar     # GNU tar is OK for a regular file tree; prefer bsdtar if present
command -v bsdtar || true

# optional (only for in-image pacman -R)
command -v qemu-aarch64-static
```

Disk: **≥ 8 GiB free** under `/tmp` (830 MiB gzip + ~2.5–3.5 GiB uncompressed + xz scratch).

Prefer `bsdtar` (`libarchive`) for the **extract** of the official image (ALARM docs: `bsdtar -xpf` preserves xattrs/ACLs). GNU `tar -xpf` is acceptable for a container rootfs if bsdtar is missing — FluxLinux does not need xattrs.

---

## 4. Procedure (do this in order)

All commands are host-side. Do not run them on the phone.

### 4.1 Download + verify

```sh
set -eu
WORKDIR="${WORKDIR:-/tmp/flux-alarm-slim}"
SRC_URL="https://fl.us.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
mkdir -p "$WORKDIR"
cd "$WORKDIR"

curl -fL --retry 3 -o ArchLinuxARM-aarch64-latest.tar.gz "$SRC_URL"
# optional signature (needs ALARM master key imported):
# curl -fL -o ArchLinuxARM-aarch64-latest.tar.gz.sig "$SRC_URL.sig"

ls -lh ArchLinuxARM-aarch64-latest.tar.gz
sha256sum ArchLinuxARM-aarch64-latest.tar.gz | tee source.sha256
# expect ~829367415 bytes for the 2026-08-05 image; a newer "latest" is OK —
# re-record size + SHA, do not hard-fail on the August 5 length.
```

If `curl` dies mid-file, delete and retry. Do not slim a partial gzip.

### 4.2 Extract

```sh
ROOT="$WORKDIR/rootfs"
rm -rf "$ROOT"
mkdir -p "$ROOT"

if command -v bsdtar >/dev/null 2>&1; then
  bsdtar -xpf ArchLinuxARM-aarch64-latest.tar.gz -C "$ROOT"
else
  tar -xpf ArchLinuxARM-aarch64-latest.tar.gz -C "$ROOT"
fi

# sanity
test -x "$ROOT/usr/bin/pacman" || test -x "$ROOT/bin/pacman"
test -e "$ROOT/usr/lib/ld-linux-aarch64.so.1" \
  || test -e "$ROOT/lib/ld-linux-aarch64.so.1"
cat "$ROOT/usr/lib/os-release" "$ROOT/etc/os-release" 2>/dev/null | head
```

If `os-release` is a symlink, read `usr/lib/os-release`. Confirm aarch64, Arch/ALARM, `pacman` present.

### 4.3 Measure before slim (required log)

```sh
du -sh "$ROOT"
du -sh "$ROOT/boot" "$ROOT/usr/lib/firmware" \
      "$ROOT/lib/modules" "$ROOT/usr/lib/modules" \
      "$ROOT/usr/share/man" "$ROOT/usr/share/doc" 2>/dev/null || true

echo "----- local pacman pkgs (kernel/firmware/boot) -----"
ls "$ROOT/var/lib/pacman/local" | rg -i 'linux|firmware|mkinitcpio|uboot|raspberry|broadcom' || true
```

Write these numbers into `docs/plans/results/archlinux-alarm-slim-report.md` when the job is run. The firmware tree alone is usually most of the 800 MiB.

### 4.4 Delete hardware-only trees

Delete **only** these. Do not delete `/usr`, `/etc`, `/var/lib/pacman`.

```sh
# boot + kernel modules + firmware
rm -rf \
  "$ROOT/boot" \
  "$ROOT/lib/modules" \
  "$ROOT/usr/lib/modules" \
  "$ROOT/usr/lib/firmware" \
  "$ROOT/lib/firmware"

# initramfs / U-Boot leftovers if present as dirs
rm -rf \
  "$ROOT/etc/mkinitcpio.d" \
  "$ROOT/usr/lib/initcpio"

# optional extra trim (safe for a container)
rm -rf \
  "$ROOT/usr/share/man" \
  "$ROOT/usr/share/doc" \
  "$ROOT/usr/share/info" \
  "$ROOT/usr/share/gtk-doc" \
  "$ROOT/var/cache/pacman/pkg/"*
```

Keep `/etc/pacman.conf`, `/etc/pacman.d/mirrorlist`, `/etc/pacman.d/gnupg` (if any), keyring files, `/usr/bin/pacman`, `/usr/bin/bash`.

### 4.5 Drop matching packages from pacman’s local DB

If we only `rm` files, the next `pacman -Syu` inside the guest will think `linux-aarch64` / `linux-firmware` are still installed and may try to “upgrade” them back (hundreds of MiB).

**Default (no qemu):** remove the local-db directories for hardware packages.

```sh
LOCAL="$ROOT/var/lib/pacman/local"
# exact names vary by date — match prefixes, do not glob the whole local db
for prefix in \
  linux-aarch64 \
  linux-firmware \
  linux-api-headers \
  mkinitcpio \
  mkinitcpio-busybox \
  uboot-tools \
  crda \
  wireless-regdb
do
  # directories look like linux-firmware-20240610-1
  find "$LOCAL" -maxdepth 1 -type d -name "${prefix}-*" -print -exec rm -rf {} +
done

# refresh the fake local "desc" set: delete leftover .lock
rm -f "$ROOT/var/lib/pacman/db.lck"
```

After this, `pacman -Q linux-aarch64` inside the guest should say “not found”. `base` / `pacman` / `glibc` must remain.

**Optional (aarch64 host or qemu-user-static):** cleaner than DB surgery:

```sh
# only if you can execute aarch64 binaries
sudo pacman --root "$ROOT" --cachedir "$ROOT/var/cache/pacman/pkg" \
  -Rdd --noconfirm linux-aarch64 linux-firmware mkinitcpio mkinitcpio-busybox \
  || true
```

`-Rdd` skips dependency checks so `base` does not pull the kernel back. Prefer this when it works; keep §4.5 file+DB delete as the always-works path.

### 4.6 Light userspace cleanup (do not “upgrade” yet)

Do **not** run `pacman -Syu` during slim on an x86_64 host. That needs a working aarch64 guest.

Safe file-only extras:

```sh
# empty machine-id so dbus gets a fresh one on first family setup
: > "$ROOT/etc/machine-id" 2>/dev/null || true
rm -f "$ROOT/var/lib/dbus/machine-id"

# no sshd needed on Android
rm -f "$ROOT/etc/ssh/ssh_host_"* 2>/dev/null || true

# resolv.conf is often empty or board-specific
printf 'nameserver 8.8.8.8\nnameserver 1.1.1.1\n' > "$ROOT/etc/resolv.conf"
```

Leave `openssh` **installed in the pacman DB** unless you also remove its local-db dir — mixed “files gone / pkg still installed” is OK only for the hardware packages we listed. For sshd, either keep the files or drop the `openssh` local-db dir too. Prefer **keep openssh files** (small) to avoid a half-removed package.

### 4.7 Must-keep checklist (fail if any missing)

```sh
need() { test -e "$1" || { echo "MISSING $1"; exit 1; }; }
need "$ROOT/usr/bin/pacman"
need "$ROOT/usr/bin/bash"
need "$ROOT/etc/pacman.conf"
need "$ROOT/etc/pacman.d/mirrorlist"
# linker — path varies
test -e "$ROOT/usr/lib/ld-linux-aarch64.so.1" \
  || test -e "$ROOT/lib/ld-linux-aarch64.so.1" \
  || { echo "MISSING glibc loader"; exit 1; }
# keyrings (names may vary)
ls "$ROOT/var/lib/pacman/local" | rg -q 'archlinuxarm-keyring|archlinux-keyring' \
  || echo "WARN: no keyring pkg in local db — first pacman-key --populate may fail"
```

Also confirm these are **gone**:

```sh
test ! -e "$ROOT/boot/Image"
test ! -d "$ROOT/usr/lib/firmware" || [ -z "$(ls -A "$ROOT/usr/lib/firmware" 2>/dev/null)" ]
```

### 4.8 Recompress xz (this is the artifact)

```sh
OUT="$WORKDIR/archlinux_arm_rootfs.tar.xz"
# create from inside rootfs so the archive has ./usr ./etc … not ./rootfs/usr
tar -C "$ROOT" -cf - . | xz -T0 -9 > "$OUT"

ls -lh "$OUT"
sha256sum "$OUT" | tee packaged.sha256

BYTES=$(wc -c < "$OUT")
# 40 MiB .. 250 MiB
if [ "$BYTES" -lt $((40*1024*1024)) ] || [ "$BYTES" -gt $((250*1024*1024)) ]; then
  echo "FAIL: packaged size $BYTES out of range"
  exit 1
fi
```

Use `tar`+`xz` (not gzip). Do **not** name it `.tar.gz`.

Copy into the repo:

```sh
REPO="${REPO:-/home/abhaybyte/repos/fluxlinux}"
mkdir -p "$REPO/assets/rootfs"
cp -f "$OUT" "$REPO/assets/rootfs/archlinux_arm_rootfs.tar.xz"
cp -f "$WORKDIR/source.sha256" "$WORKDIR/packaged.sha256" \
  "$REPO/docs/plans/results/" 2>/dev/null || true
```

### 4.9 Record results

Create `docs/plans/results/archlinux-alarm-slim-report.md` with:

- source URL, bytes, SHA-256
- `du -sh` before / after extract
- sizes of `/boot`, firmware, modules before delete
- list of local-db prefixes removed
- packaged bytes + SHA-256
- `os-release` dump
- `ls var/lib/pacman/local | wc -l` after slim
- confirmation `pacman` + `bash` + loader still exist

Then paste the packaged SHA-256 into §2 of this plan and (when wiring) into `DistroInstallProfile`.

---

## 5. What we do **not** do during slim

| Action | Why not |
|--------|---------|
| `pacman -Syu` on the host tree | Needs aarch64 execution; also re-downloads firmware if those pkgs are still in the DB |
| Rewrite mirrorlist to Manjaro `arm-stable` | This is Arch Linux ARM, not Manjaro |
| Rewrite mirrorlist to x86_64 `geo.mirror.pkgbuild.com` | Wrong arch |
| Delete `/var/lib/pacman/local` wholesale | Breaks `pacman -Q` / upgrades |
| Delete `archlinuxarm-keyring` | First `pacman-key --populate` needs it |
| Keep `/usr/lib/firmware` “just in case” | That is the 800 MiB problem |
| Ship the original `.tar.gz` into `app/src/main/assets` | aapt2 strips `.gz`; APK becomes unusable / huge |
| Run the slim on-device | 3 GiB extract + xz will thrash `/data` |

---

## 6. First boot inside FluxLinux (after the xz exists)

The slim rootfs is still a **board image minus kernel**. Same landmines as Manjaro:

1. `/etc/pacman.d/gnupg` is **absent** in the 2026-08-05 image (confirmed) → `pacman-key --init` + `pacman-key --populate archlinuxarm` (and `archlinux` if that keyring is present).
2. Entropy: proot must bind `/dev/urandom`. If `--init` hangs, temporary `SigLevel = Never`, install/populate keyrings, restore `Required DatabaseOptional`.
3. `passwd`/`group` already contains `alarm` (uid 1000) in `wheel` `audio` `video` `input` (confirmed) → family script must drop/repurpose `alarm` and create `flux`, not assume root-only files.
4. Setuid did **not** survive the non-root extract: `/usr/bin/passwd`, `unix_chkpwd`, `newuidmap` are 0755. Proot ignores setuid anyway; the chroot family script must `chmod u+s` the su/sudo set on first setup.
5. `base` only **optdepends** `linux` (confirmed) — `pacman -Syu` will not pull the kernel back after the local-db purge.
6. Do **not** reuse `setup_arch_family.sh` as it exists today (ALARM mirror ranker is OK for this guest, but the script is outdated: `xfce4` metapackage, no `flux_guest_common`, no Mesa, no fail-closed `startxfce4`). Rewrite it to the FVO family contract when Arch is activated.
7. Family install: targeted XFCE (`xfce4-session xfce4-panel xfce4-settings xfce4-terminal xfdesktop xfwm4 thunar`) + `sudo shadow mesa mesa-utils dbus ttf-dejavu`. Not `pacman -S xfce4` group first.

That family work is **not** part of the slim job. The slim job ends when the xz is pinned and the report is written.

---

## 7. Gradle / APK packaging (when the xz is ready)

Same pattern as FVO `stageHostRootfs`:

```
assets/rootfs/archlinux_arm_rootfs.tar.xz
        ↓  :app:stageHostRootfs
app/src/main/assets/rootfs/archlinux_arm_rootfs.tar.xz
```

- `androidResources.noCompress` already includes `xz`.
- Do not add a `.gz` copy.
- `HostScriptDeployer.deployAllRootfsFromAssets` copies every `DistroInstallProfile.allRootfsProfiles()` entry — adding an Arch profile automatically deploys the xz.
- Min bytes in the profile: `40L * 1024L * 1024L`.

Until Arch cards are flipped live, the xz can sit in `assets/rootfs/` unused. That is OK and keeps the slim result versioned.

---

## 8. Suggested one-shot script (host)

Save as something like `scripts/slim_alarm_rootfs.sh` when implementing (not required to exist for this plan to be valid). Behaviour must match §4:

```
WORKDIR=/tmp/flux-alarm-slim
download → sha256 → extract → du-before
→ rm boot/firmware/modules/man/doc
→ purge local-db prefixes
→ keep-check
→ tar | xz -T0 -9
→ size gate 40–250 MiB
→ write source.sha256 + packaged.sha256
→ copy to assets/rootfs/archlinux_arm_rootfs.tar.xz
```

Idempotent: `rm -rf "$WORKDIR/rootfs"` at start. Never delete `assets/rootfs/*` except the Arch xz being replaced.

---

## 9. Verification (slim job done)

| # | Check | Pass |
|---|--------|------|
| S1 | Source gzip SHA recorded | file + this plan §2 |
| S2 | Extract has `pacman` + aarch64 loader | §4.2 |
| S3 | Before/after `du` logged | firmware/boot were the bulk |
| S4 | `/boot/Image` and `/usr/lib/firmware` gone | §4.7 |
| S5 | `var/lib/pacman/local/linux-aarch64-*` and `linux-firmware-*` gone | §4.5 |
| S6 | `pacman`, `bash`, `pacman.conf`, mirrorlist still present | §4.7 |
| S7 | `archlinux_arm_rootfs.tar.xz` is 40–250 MiB | §4.8 |
| S8 | Packaged SHA-256 recorded | §2 + results report |
| S9 | File lives at `assets/rootfs/archlinux_arm_rootfs.tar.xz` | repo |
| S10 | Spot-check: `tar -tJf … \| rg 'usr/bin/pacman|boot/Image|firmware'` | pacman yes, Image/firmware no |

Optional smoke (not required for slim-done): extract xz to a throwaway dir and confirm `usr/bin/pacman` is a real ELF aarch64 (`file usr/bin/pacman` → `ARM aarch64`).

---

## 10. Later: wire as FluxLinux Arch (out of the slim critical path)

Only after S1–S10. Separate implementation slice, same product shape as FVO:

| Card | Method | Path / name |
|------|--------|-------------|
| `archlinux` | proot | proot-distro name `archlinux` |
| `archlinux_chroot` | chroot | `/data/local/tmp/chrootArch` |

- Split the current coming-soon dual-mode `archlinux` stub (same as Fedora).
- `SupportedDistro.ARCH` already exists (`pacman`, `ROLLING`).
- New `DistroInstallProfile` pair, `hwAccelScript` + shared `setup_customization_xfce.sh`.
- Family script: rewrite `setup_arch_family.sh` (do not use the current VNC-era file as-is).
- Reuse `setup_guest_chroot.sh` / `start_guest_gui.sh`.
- `flux_install.sh` case `archlinux`.
- Device matrix I1–D5 on both methods, Ivarna release only, `adb install -r`.

Do **not** start that slice until the xz is pinned. Do **not** block Deepin/Chimera/Manjaro on this file.

---

## 11. Risks

| Risk | Mitigation |
|------|------------|
| Newer `latest` is a different size than 829 MiB | Always SHA the file you actually downloaded; never reuse an old SHA |
| `linux-firmware` split into many pkgs (`linux-firmware-amdgpu`, …) | `find` prefix `linux-firmware` covers them; re-check `ls local \| rg firmware` |
| Accidental delete of `linux-api-headers` needed to build | Harmless for Flux XFCE; reinstall later with pacman if needed |
| `pacman -Syu` reinstalls firmware because local db still lists it | §4.5 is mandatory |
| xz still > 250 MiB | Re-run `du -sh` on remaining large dirs (`usr/lib`, `usr/share`); trim more locale/docs; do not raise the gate without a written reason |
| xz < 40 MiB | Extract failed or we deleted `/usr` — do not ship |
| Host `/tmp` fills | Set `WORKDIR` to a big disk |
| Confusing Arch xz with Manjaro gzip | Different filenames; different cards; never share SHA constants |

---

## 12. Definition of done (this plan)

- [x] Official ALARM gzip downloaded and SHA-256 recorded.  
- [x] Hardware trees removed; kernel/firmware local-db prefixes purged.  
- [x] `assets/rootfs/archlinux_arm_rootfs.tar.xz` exists, 40–250 MiB, SHA-256 recorded in §2.  
- [x] `docs/plans/results/archlinux-alarm-slim-report.md` written with before/after sizes.  
- [x] Spot-check §9 S10 passes.  
- [x] This plan’s status line flipped to `SLIM DONE` with date + packaged SHA.

App cards, family scripts, and device XFCE are **not** required to close this plan.

---

*This file is the slim-rootfs contract. The 800 MiB URL is an input, never an APK asset.*
