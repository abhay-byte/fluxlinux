# Hardware acceleration — Pass 2 device report

**Date:** 2026-08-13  
**Package:** `com.ivarna.fluxlinux` Ivarna release (`assembleIvarnaRelease` + `adb install -r`). Gradle stopped.

## Devices (`adb devices`; skipped `emulator-5554`)

| Serial | Phone | SoC | `/dev/kgsl-3d0` | Root | Expected mode |
|--------|-------|-----|-----------------|------|---------------|
| `Y5WWBMJVOZSK4HU8` | Xiaomi 2311DRK48I (duchamp) | `ro.soc.model=MT6897` Mediatek, `ro.hardware.egl=meow` | **missing** | KernelSU yes | **virgl** / mali |
| `2a580689` | Realme X2 Pro (RMX1931) | `ro.soc.model=SM8150` Qualcomm, `ro.hardware.egl=adreno` | **present** `crw-rw-rw-` | **no** (`su` missing) | **turnip** / adreno |
| `emulator-5554` | — | x86_64 | — | — | skipped |

## Code (Pass 2 only)

`flux_gpu_has_kgsl_dri` in **both** copies (`flux_gpu_common.sh` function + `apply_gpu_env.sh` heredoc) now probes **only** `kgsl_dri.so` (explicit paths + `ls /usr/lib*/dri/kgsl_dri.so /usr/lib/*/dri/kgsl_dri.so`). Stock `msm_dri.so` / `freedreno_dri.so` probes **removed**. `sh -n` both scripts: OK.

On-disk `apply_gpu_env.sh` after re-run: `probe=kgsl_only` on every installed guest (comments still mention msm/freedreno as “do not treat as KGSL”).

**Note (not changed this pass):** Debian stock Mesa ships `kgsl_dri.so -> libdril_dri.so` (dri-loader stub, 2026-06-20). The probe treats that name as kgsl. Harmless while `gpu_mode=virgl`. On Snapdragon after a Turnip-ICD-only extract this stub can still force `MESA_LOADER_DRIVER_OVERRIDE=kgsl`. Follow-up if a Start black-screens: ignore symlink-to-`libdril_dri.so`.

---

## Xiaomi Mali — full matrix (mandatory)

APK `install -r` Success after the kgsl-probe fix. Scripts staged into guest `/tmp` (`$PREFIX/tmp` shared-tmp for proot; `$CHROOT/tmp` for chroot). Run as root (`FLUX_GPU=virgl` except the family/URL probes below). `PD_PROOT_BIN` in `fluxlinux-host.env` was stale after `install -r` (old `nativeLibraryDir`); rewritten to current `libproot.so` before XFCE Start.

Host: **no** `virgl_test_server_android` → Start logs `VirGL unavailable` / `VirGL socket missing — llvmpipe fallback`. GPU **mode line still printed**. Desktop stopped after the line.

### Family / URL probes (then restored to virgl)

| Probe | Result |
|-------|--------|
| Chimera proot `FLUX_GPU=turnip` | `no Turnip tarball for this guest (no-tarball) — VirGL.` → `gpu_mode=virgl` |
| openSUSE proot `FLUX_GPU=turnip` | same `no-tarball` → `gpu_mode=virgl` |
| Debian proot `FLUX_GPU=turnip` | `Downloading Turnip 26.2.0-devel-20260709 (debian_trixie)` then `curl: (6) Could not resolve host: github.com` → virgl (`+turnip-download-fail`). Re-ran `FLUX_GPU=virgl`. |
| Alpine proot `FLUX_GPU=turnip` | same URL path (`alpine_3.24`); DNS fail → virgl. Re-ran `FLUX_GPU=virgl`. |
| Host (not proot) `curl -sI` of the debian_trixie tarball | **HTTP 302 → 200**, `Content-Length: 3546003` — URL is live. Guest DNS inside proot is the failure, not the pin. |

### Installed guests

| Guest | Method | hw re-run | gpu_mode | apply_gpu_env | XFCE Start log |
|-------|--------|-----------|----------|---------------|----------------|
| debian | proot | virgl (also turnip URL attempt) | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| alpine | proot | virgl (also turnip URL attempt) | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| fedora | proot | virgl | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| void | proot | virgl | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| opensuse | proot | virgl (also forced turnip → no-tarball) | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| deepin | proot | virgl | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| chimera | proot | virgl (also forced turnip → no-tarball) | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| manjaro | proot | virgl | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| debian13 | chroot | virgl | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| alpine | chroot | virgl | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| fedora | chroot | virgl | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| void | chroot | virgl | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| opensuse | chroot | virgl | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| deepin | chroot | virgl | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| chimera | chroot | virgl | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |
| manjaro | chroot | virgl | virgl | yes, kgsl_only | `FluxLinux(guest): GPU mode=virgl` |

No SKIP on this phone — all eight proot containers (`startxfce4` present) and all eight chroots (`/data/local/tmp/chroot{Debian13,Alpine,Fedora,Void,OpenSUSE,Deepin,Chimera,Manjaro}`) were installed.

**Final disk state:** every guest `gpu_mode=virgl` `gpu_vendor=mali`. None left on turnip.

**Start notes (not GPU bugs):** first Debian Start failed `PD_PROOT_BIN` stale path after `install -r` (host line already `Guest GPU mode=virgl`; guest line missing). Env rewritten; re-Start printed `FluxLinux(guest): GPU mode=virgl`. Several Starts: `X server already running on display :0` from leftover Loader; still printed the GPU line. Host has no VirGL server → llvmpipe.

---

## Realme Snapdragon — proot only (no root)

**Attached.** SM8150 + `/dev/kgsl-3d0`. No `su`. `run-as` fails (release not debuggable). Chroot **SKIP — no root**.

APK `install -r` Success (streamed install after an earlier USB drop). Fresh onboarding (no guests yet). UI: Get Started → Debian **PRoot** (not Debian Rooted) → Dark → Install.

**Status at report time:** still running — UI `20%` / `Installing Debian rootfs + XFCE…`. Guest apt is retrying `deb.debian.org` (`Temporary failure resolving` / many `Ign:`). Host bootstrap already `✓ Host ready`. Turnip extract + `gpu_mode=turnip` + XFCE Start **not yet** — install has not reached hw_accel.

Scripts also pushed to `/sdcard/Download/flux_gpu_common.sh` + `setup_hw_accel_guest.sh` (kgsl-only) for a post-install guest re-run if onboarding hw is skipped or DNS fails.

---

## Pass 2 acceptance

| Check | Result |
|-------|--------|
| `kgsl_dri.so` only probe; msm/freedreno removed from both copies | **PASS** |
| Report row for every **installed** guest | **PASS** (Xiaomi 16/16). Realme: none installed yet |
| Every started desktop printed `GPU mode=` | **PASS** Xiaomi 16/16 |
| Mali: no guest left on `gpu_mode=turnip` | **PASS** |
| Snapdragon: one proot + one chroot Turnip **or** explicit skip | **IN PROGRESS** — Snapdragon attached; Debian proot installing; chroot SKIP (no root) |
| No new Kotlin architecture. No KDE/Termux-native | **PASS** |

---

## Pass 1 (historical)

Mali-only, three proot guests, no XFCE Start, no Snapdragon. Superseded by the table above.
