# Plan: Hardware acceleration for every proot + chroot guest

**Date:** 2026-08-13  
**Status:** SLICES A–F LANDED. **Pass 2 OPEN** — code review + full device matrix (all installed guests). Do not re-implement A–F.  
**Package:** `com.ivarna.fluxlinux`. **APK:** `assembleIvarnaRelease` + `adb install -r` only.  
**Native reference (read-only):** `~/repos/termux-lib`  
**Upstream tarballs:** [lfdevs/mesa-for-android-container](https://github.com/lfdevs/mesa-for-android-container)  
**Prior device note (Mali only, incomplete):** [`docs/plans/results/hw-accel-all-distros-report.md`](../plans/results/hw-accel-all-distros-report.md)

**Not in this plan:** KDE launch variants, native Termux desktop, Vulkan llama.cpp, packaging Turnip in the APK, Home/KDE GPU UI, Ubuntu/Kali/Parrot/Arch cards.

---

## Pass 2 — review verdict (do this next)

Slices A–F are wired in tree. **Do not rewrite** `GpuAccelDetector`, the guest installer, or onboarding unless a Pass 2 item requires a small fix.

### What is correct

| Item | Evidence |
|------|----------|
| Host detect | `GpuAccelDetector.kt` — Build + getprop + kgsl; token match (no `sun`⊂`samsung`); `resolveFluxGpu` never returns `auto` |
| Prefs | `fluxlinux_state` keys `flux_gpu` / `flux_gpu_vendor` |
| Settings Auto | `DistroSettingsScreen` calls `resolveFluxGpu(selectedGpu)` |
| Onboarding runs hw | `runHwAccelIfPresent` after custom; mark installed only if exit 0 |
| One installer | `setup_hw_accel_guest.sh` + `flux_gpu_common.sh`; debian script is a 16-line wrapper |
| Tarball pin | `26.2.0-devel-20260709`; map matches §6; Chimera/openSUSE empty suffix |
| Family first-paint | Debian/Alpine write `gpu_mode=virgl`; Debian installs stock Mesa |
| Start scripts | source `apply_gpu_env.sh` in proot + chroot `su` blocks; inline fallback remains |
| Chroot kgsl | `fluxlinux_chroot.sh` mknod if host node exists and guest missing |
| Profiles / catalog | every `allInstallable()` has `hwAccelScript`; Alpine has `hw_accel` |
| Unit tests | `GpuAccelDetectorTest`, profile + repo asserts |

### What is wrong or unfinished

| Sev | Issue | Fix |
|-----|--------|-----|
| **bug** | `flux_gpu_has_kgsl_dri` treats **stock** `msm_dri.so` / `freedreno_dri.so` as KGSL. Stock Mesa on Debian/Fedora/Manjaro can ship those for DRM. On Snapdragon after a Turnip-ICD-only extract (mesa upgrade fail), `apply_gpu_env` would set `MESA_LOADER_DRIVER_OVERRIDE=kgsl` and GL dies. | Only treat **`kgsl_dri.so`** as kgsl. Drop msm/freedreno from the probe. If a device extract later shows lfdevs uses another **kgsl-specific** name, add that name only. Duplicate lives in `flux_gpu_common.sh` **and** the `apply_gpu_env.sh` heredoc — change both. |
| **gap** | Pass 1 device work hit **Mali only**, **3 proot guests**, **no XFCE Start**, **no Snapdragon Turnip extract**. User: all distros are installed — run the hw script **manually on every live guest** (proot + chroot) and Start XFCE. | Pass 2 matrix below. Write `docs/plans/results/hw-accel-all-distros-report.md` (replace/extend). |
| **nit** | Debian wrapper (`setup_hw_accel_debian.sh`) is unused by catalog (points at guest SSOT). Manual `$HOME` run cannot find `/tmp/flux_gpu_common.sh` unless staged. | Keep wrapper. Document: Settings/onboarding use guest script. No extra work unless you have 5 minutes — then look in `$HOME/flux_gpu_common.sh` too. |
| **nit** | `apply_gpu_env.sh` is a copy-paste of `flux_gpu_apply_runtime`. Drift risk. | Do not refactor unless you touch the kgsl probe anyway — then keep the two copies in sync. |

### Pass 2 code (small)

1. Fix `flux_gpu_has_kgsl_dri` in `flux_gpu_common.sh` (function + heredoc). `sh -n` both scripts.
2. Rebuild **only if** you changed scripts that must be on device: `assembleIvarnaRelease` + `adb install -r`. If you only re-run **already-deployed** guest scripts from `$PREFIX/tmp` / assets you push yourself, you can `adb push` the two scripts into host tmp and copy into each guest `/tmp` — still `install -r` if the APK’s assets must match.
3. Do **not** change Kotlin detect, onboarding, or family scripts unless a device failure proves they are wrong.

### Pass 2 device matrix (mandatory)

Phone(s): use every **physical** device attached (`adb devices`). Current known: Xiaomi `Y5WWBMJVOZSK4HU8` (MT6897 **Mali**). If a Snapdragon is attached, it is required for Turnip. Skip `emulator-5554`.

**Discover installed guests first** (do not assume the Pass 1 subset):

```
# proot
ls $PREFIX/var/lib/proot-distro/containers/
# expect some of: debian alpine fedora void opensuse deepin chimera manjaro
# each must have startxfce4 to count as installed

# chroot (root)
ls /data/local/tmp/chroot*/
```

For **each** installed proot name `D` and each installed chroot path:

1. Stage current `flux_gpu_common.sh` + `setup_hw_accel_guest.sh` into guest `/tmp` (proot: `--shared-tmp` + host `$PREFIX/tmp`; chroot: root copy into `$CHROOT/tmp`).
2. Run as root:

   ```
   env FLUX_GPU=auto   → must NOT stay "auto"; guest normalize maps auto→ask→detect
   ```

   Prefer host-resolved mode. On Mali: `FLUX_GPU=virgl`. Also once per **family** (not every card): `FLUX_GPU=turnip` to prove map/no-tarball.
3. Record: `cat /etc/fluxlinux/gpu_mode`, `cat /etc/fluxlinux/gpu_vendor`, whether `/usr/local/lib/fluxlinux/apply_gpu_env.sh` exists, `gpu-launch` exists, last log lines (`no-tarball` / extract / virgl).
4. **Start XFCE** for that card (app Start, or host `start_gui.sh D` / `start_gui_chroot.sh <id>`). Capture guest log line `FluxLinux(guest): GPU mode=…`. Stop desktop after the line (or ~20s). Do not leave sessions hanging.
5. Expected on **this Mali phone**: every guest `gpu_mode=virgl`, Start log `GPU mode=virgl` (or llvmpipe if VirGL socket missing — then note host `virgl_test_server_android`).
6. Expected if **`FLUX_GPU=turnip`** on Chimera/openSUSE: log `no-tarball`, stay virgl. On Debian/Fedora/Alpine/Void/Manjaro/Deepin: **will try download**. On Mali that is optional (Turnip will not help); **do it on one glibc + Alpine only** to prove the URL/extract path, then leave `gpu_mode=virgl` (re-run with `FLUX_GPU=virgl`) so daily Start stays correct.
7. Snapdragon (if present): Settings-equivalent `FLUX_GPU=turnip` (or detect) on Debian proot **and** one chroot. Require `gpu_mode=turnip`, `kgsl_dri.so` **or** freedreno ICD, Start log `GPU mode=turnip`, no black screen. Confirm `/dev/kgsl-3d0` inside that chroot.

Minimum rows in the report table (skip a row only if that guest is **not** installed; mark SKIP + reason):

| Guest | Method | hw re-run | gpu_mode | apply_gpu_env | XFCE Start log |
|-------|--------|-----------|----------|---------------|----------------|
| debian | proot | | | | |
| alpine | proot | | | | |
| fedora | proot | | | | |
| void | proot | | | | |
| opensuse | proot | | | | |
| deepin | proot | | | | |
| chimera | proot | | | | |
| manjaro | proot | | | | |
| debian13 | chroot | | | | |
| alpine | chroot | | | | |
| fedora | chroot | | | | |
| void | chroot | | | | |
| opensuse | chroot | | | | |
| deepin | chroot | | | | |
| chimera | chroot | | | | |
| manjaro | chroot | | | | |

Host detect row: `getprop ro.soc.model`, `ls /dev/kgsl-3d0`, expected mode.

If XFCE Start fails for a **pre-existing** guest reason (bwrap, locale, not GPU), say so — do not expand scope. GPU log line is still required.

### Pass 2 acceptance

- [ ] `kgsl_dri.so` is the only kgsl probe; msm/freedreno removed from both copies
- [ ] Report table has a row for every **installed** guest
- [ ] Every started desktop printed `GPU mode=`
- [ ] Mali: no guest left on `gpu_mode=turnip` after tests
- [ ] Snapdragon: at least one proot + one chroot Turnip path **or** explicit “no Snapdragon attached”
- [ ] No new Kotlin architecture. No KDE/Termux-native work.

---

## Pass 1 (historical — already implemented)

The sections below are the original A–F design. **Do not re-do them.** They remain as the contract the code should still match.

---

## 0. Verdict (current FluxLinux is wrong)

GPU detection is **not implemented on the host** and **not correct in the guest**. Start scripts already honor `/etc/fluxlinux/gpu_mode`, but almost every install writes `virgl` (or writes nothing) and never installs Turnip except a Fedora-only best-effort download.

Onboarding **never runs** the hw-accel script. It only marks `hw_accel` installed when `profile.hwAccelScript != null`. Debian and Alpine do not even have that field.

Settings “Auto Detect” sends `FLUX_GPU=auto`. Guest scripts only accept exact `turnip|virgl`, so `auto` becomes `virgl`. Snapdragon devices stay on VirGL / llvmpipe.

---

## 1. What nativecode does (source of truth for detect + env)

Read these files before editing FluxLinux:

| File | Role |
|------|------|
| `~/repos/termux-lib/app/src/main/java/com/zenithblue/nativecode/terminal/GpuAccelDetector.kt` | Host detect: `Build.*` + `SystemProperties`/`getprop` + `/dev/kgsl-3d0` → `turnip` or `virgl` |
| `~/repos/termux-lib/app/src/main/assets/scripts/setup_hw_accel_debian.sh` | Guest: `normalize_mode`, `auto_detect_mode`, never hang, Turnip+Mesa tarball, state files, `gpu-launch` |
| `~/repos/termux-lib/app/src/main/assets/scripts/start_gui.sh` | Reads guest `gpu_mode`; Zink / virpipe / llvmpipe |
| `~/repos/termux-lib/docs/plan/gpu-accel-vendor-detect-turnip-virgl.md` | Policy + env table |

### 1.1 Detect policy (copy this)

```
Host (Kotlin GpuAccelDetector)
  Build.HARDWARE/BOARD/DEVICE/PRODUCT/MANUFACTURER/BRAND/MODEL
  API 31+ SOC_MANUFACTURER / SOC_MODEL
  getprop: ro.hardware, ro.hardware.chipname, ro.chipname,
           ro.board.platform, ro.soc.model, ro.soc.manufacturer,
           ro.product.board, ro.hardware.egl, ro.hardware.vulkan,
           ro.gfx.driver.0, ro.opengles.version
  Strong Adreno: /dev/kgsl-3d0 exists or readable
        │
        ├─ Adreno / QCOM / KGSL  →  mode=turnip  vendor=adreno/snapdragon
        ├─ Mali / Exynos / MTK / Tensor  →  mode=virgl  vendor=mali
        ├─ PowerVR  →  mode=virgl  vendor=powervr
        ├─ Xclipse  →  mode=virgl  vendor=xclipse
        └─ else  →  mode=virgl  vendor=unknown
```

Adreno needles (native list, keep in one Kotlin table):  
`qcom`, `qualcomm`, `adreno`, `kgsl`, `snapdragon`, `msm`, `sdm`, `sm8150`…`sm8750`, `sm4`/`sm5`/`sm6`/`sm7`/`sm8`, `lahaina`, `taro`, `kalama`, `pineapple`, `canoe`, `sun`, `kona`, `lito`, `bengal`, `holi`, `crow`, `ravelin`, `parrot`, `blair`, `anorak`, `hamoa`, `volcano`, `pitti`, `niobe`, `cliq`, `shima`, `yupik`, `atoll`, `trinket`, `guppy`, `strait`, `bitra`, `waipio`.

Mali needles: `mali`, `exynos`, `kirin`, `hisi`, `mediatek`, `mt68`, `mt67`, `mt69`, `dimensity`, `helio`, `tensor`, `gs10`, `gs20`, `gs30`.

PowerVR: `powervr`, `imgtec`, `imagination`, `rogue`.  
Xclipse: `xclipse`, `amdgpu`, `samsung_xclipse`.

**False-positive watch:** substring `sm4` matches `sm4xxx` boards but also random strings that contain `sm4`. Native has this risk. Prefer word-ish / SoC-shaped matches in Kotlin (`\\bsm[0-9]{3,4}\\b` on the blob, plus explicit `sm4`/`sm5` only as prefixes like `sm4[0-9]{3}`). Do **not** treat `parrot` the OS as Adreno — that needle is the QCOM board name. Guest `os-release` is a separate file; do not feed it into the host blob.

### 1.2 Native guest behavior to port

- `FLUX_GPU` aliases: `turnip|adreno|snapdragon|qcom|qualcomm|kgsl|zink` → turnip; `virgl|virpipe|mali|powervr|xclipse|llvmpipe|soft|software|sw` → virgl; `ask|manual|""` → auto or TTY menu.
- Empty / unset `FLUX_GPU` → guest `auto_detect_mode` (getprop + `/proc/cpuinfo` + `/dev/kgsl-3d0`). **Never** `read` unless `FLUX_GPU=ask|manual` **and** stdin is a TTY.
- Non-arm64 turnip → virgl.
- Turnip download fail → virgl (do not `exit 1`).
- State: `/etc/fluxlinux/gpu_mode`, `/etc/fluxlinux/gpu_vendor`, `/etc/profile.d/flux-gpu.sh`.
- `gpu-launch` reads state file + `FLUX_GPU_RUNTIME` override.
- XFCE compositor off for turnip (black screen otherwise).
- Fake `/dev/dri/card0` + `renderD128` → `/dev/null` (Turnip uses KGSL; some apps probe DRI).

### 1.3 Native is not a blind copy

lfdevs README (2026) is newer than nativecode:

- Full Mesa tarball: prefer `MESA_LOADER_DRIVER_OVERRIDE=kgsl` (Freedreno GL, no Zink tax).
- Turnip-only ICD: keep Zink (`MESA_LOADER_DRIVER_OVERRIDE=zink` + `VK_ICD_FILENAMES=…/freedreno_icd.aarch64.json`).
- Direct-extract suffixes now include **`void`** and **`alpine_3.24`**, not just debian/ubuntu/fedora.
- Pin version to **`26.2.0-devel-20260709`** (native). FluxLinux still has `20260610`.

FluxLinux start scripts already apply Zink/virpipe/llvmpipe like native. After this plan, `apply_gpu_env` prefers **kgsl** when the Freedreno DRI/kgsl driver file exists, else Zink, else virgl.

---

## 2. FluxLinux audit (what is actually wired)

### 2.1 Scripts

| Script | What it does | Defect |
|--------|----------------|--------|
| `common/setup/setup_hw_accel_guest.sh` | PM mesa pkgs; writes `gpu_mode`; writes `gpu-launch` | No detect/normalize. Turnip **only if Fedora**. Incomplete `gpu-launch` (duplicate `GALLIUM_DRIVER=virpipe`, no ICD, no `MESA_*`, no `VTEST_SOCKET_NAME`). No compositor, no `/dev/dri`, no `gpu_vendor`. |
| `debian/common/setup/setup_hw_accel_debian.sh` | Interactive menu; Turnip+Mesa for debian/ubuntu/fedora; richer `gpu-launch` | Hang if no TTY and `FLUX_GPU` unset. Download fail **exits 1**. `dpkg` arch only. Version `20260610`. Distro map misses void/alpine/archlinux. Bakes MODE then also reads file (OK). |
| Family `setup_*_family.sh` (fedora/void/suse/deepin/chimera/manjaro) | `_flux_write_gpu_mode virgl` | Always virgl. Never host detect. |
| `setup_debian_family.sh` | XFCE + user only | **No Mesa. No `gpu_mode`.** |
| `setup_alpine_family.sh` | Installs `mesa-dri-gallium mesa-gl` | **No `gpu_mode`.** |
| `debian/proot/start/start_gui.sh` | Host VirGL + guest env from `gpu_mode` | Correct **if** file is right. Duplicated env block. |
| `chroot/start_gui_chroot.sh` | Host VirGL + X11 | Does not pass GPU. Fine. |
| `chroot/start_guest_gui.sh` + alpine/debian13 variants | Guest env from `gpu_mode` | Correct **if** file is right. No explicit KGSL node (relies on `/dev` bind). |
| `fluxlinux_chroot.sh` | Binds `/dev` | KGSL usually visible. Still add explicit kgsl ensure (KDE turnip script already does this). |
| `termux/setup/setup_hw_accel_termux.sh` | Host getprop detect | Out of scope. Do not merge into guest. |

### 2.2 Kotlin

| Location | Defect |
|----------|--------|
| **No `GpuAccelDetector`** | Host never identifies Adreno vs Mali. |
| `OnboardingInstallRunner` | After family+custom: `if (profile.hwAccelScript != null) setComponentInstalled(hw_accel)` **without running the script**. No `FLUX_GPU`. |
| `DistroInstallProfile` | `hwAccelScript` set for fedora/void/suse/deepin/chimera/manjaro only. **Debian + Alpine = null.** |
| `DistroRepository` | Debian cards use `setup_hw_accel_debian.sh`. Alpine cards have **no** `hw_accel` component. Others use `setup_hw_accel_guest.sh`. |
| `DistroSettingsScreen` | “Auto Detect” passes `FLUX_GPU=auto` (guest treats as virgl). “Force Re-Detect” = `ask` (OK only in a TTY). |
| `MainActivity.runComponentStep` | Injects `FLUX_GPU` only when `component.id == "hw_accel"`. Wizard GPU string is whatever the UI sent — often unused for XFCE onboarding. |
| `HomeScreen` KDE dialog | Turnip vs Software only. Out of scope. |

### 2.3 Why first XFCE paint is software / VirGL

1. Family writes `gpu_mode=virgl` (or writes nothing).
2. Hw script not run on onboarding.
3. Even if Settings re-runs guest script, `FLUX_GPU=auto` → virgl.
4. Fedora-only Turnip URL; other distros cannot install the driver.
5. Start script then sets virpipe if socket exists, else llvmpipe.

---

## 3. Goal

```
Host GpuAccelDetector.detect()
        │
        ├─ prefs flux_gpu / flux_gpu_vendor
        └─ env FLUX_GPU=<mode>  FLUX_GPU_VENDOR=<hint>
                │
                ▼
Guest setup_hw_accel_guest.sh  (SSOT for every distro)
  normalize + (if empty) guest auto-detect
  install distro Mesa pkgs
  if turnip AND tarball mapped AND arm64 → extract Turnip+Mesa
  else virgl
  write gpu_mode / gpu_vendor / gpu-launch / profile.d / apply_gpu_env.sh
                │
                ▼
start_gui.sh / start_guest_gui.sh source apply_gpu_env.sh
  kgsl → Freedreno
  else turnip ICD → Zink
  else virgl + socket → virpipe
  else llvmpipe
```

Same path for **proot and chroot** of every live card.

---

## 4. Policy (do not invent extra modes)

| Condition | `gpu_mode` | Guest GL |
|-----------|------------|----------|
| Host Adreno/KGSL **and** distro has lfdevs tarball **and** arm64 **and** extract ok | `turnip` | kgsl if driver present, else Zink+freedreno ICD |
| Host Adreno but distro has **no** tarball (openSUSE, Chimera) | `virgl` | virpipe (log: no Turnip build for this guest) |
| Mali / PowerVR / Xclipse / unknown | `virgl` | virpipe |
| Turnip download/extract fail | `virgl` | virpipe |
| Non-arm64 | `virgl` | virpipe |
| `gpu_mode=virgl` but no VirGL socket at start | (unchanged) | llvmpipe + warning |
| User `FLUX_GPU=virgl` | `virgl` | never download Turnip |
| User `FLUX_GPU=turnip` on unsupported guest | try map; fail → `virgl` | |
| `FLUX_GPU=ask\|manual` and TTY | menu | |
| `FLUX_GPU=ask\|manual` and not TTY | auto-detect | never block |
| `FLUX_GPU` empty / `auto` | host mode if set, else guest auto | |

Modes on disk: only `turnip` or `virgl`. Software is a **runtime fallback**, not a stored mode.

---

## 5. Cohesion / coupling (clean code — do not violate)

| Layer | Owns | Must not own |
|-------|------|----------------|
| `GpuAccelDetector` | Android vendor → `mode` + `vendorHint` + `signals` | Distro ids, package names, URLs, scripts |
| `GpuAccelPrefs` (tiny helper or functions on detector) | Read/write `flux_gpu`, `flux_gpu_vendor` | UI widgets |
| `DistroInstallProfile` | `hwAccelScript` path per card | Detect logic, tarball URLs |
| `DistroRepository` | Component id `hw_accel` + script path from profile / shared SSOT | SHA, chroot paths |
| `OnboardingInstallRunner` | After custom: run `hwAccelScript` with `FLUX_GPU` from detector | Package lists |
| `DistroSettingsScreen` | Resolve `auto` → `GpuAccelDetector.fluxGpuEnv()` **before** export | Reimplement keyword tables |
| `scripts/common/setup/flux_gpu_common.sh` | normalize, guest detect, state files, `gpu-launch`, `apply_gpu_env.sh` | `apt`/`dnf`/tarball URLs |
| `scripts/common/setup/setup_hw_accel_guest.sh` | PM mesa packages + lfdevs map + extract + pin + compositor + `/dev/dri` | X11 start, theme |
| `setup_hw_accel_debian.sh` | **Thin wrapper**: `exec` / `.` the guest SSOT | Second copy of detect/Turnip |
| Family scripts | Mesa **stock** pkgs + `_flux_write_gpu_mode virgl` first-paint | Turnip download |
| `start_gui.sh` / `start_*_gui.sh` | Host VirGL server + source guest `apply_gpu_env.sh` | Detect, download |
| `fluxlinux_chroot.sh` / `start_guest_gui.sh` | Bind `/dev` + ensure kgsl nodes | Mesa packages |

Adding a ninth guest later = one row in the tarball map + family Mesa package list. No new detector. No new start-script env block.

---

## 6. Distro capability matrix

lfdevs **direct-extract** suffixes (README):  
`debian_trixie`, `ubuntu_noble`, `ubuntu_questing`, `ubuntu_resolute`, `fedora_43`, `fedora_44`, `archlinux`, `void`, `alpine_3.24`.

Pin:

```
TURNIP_VERSION=26.2.0-devel-20260709
MESA_VERSION=26.2.0-devel-20260709
TURNIP_URL=…/turnip-${VER}/turnip_${VER}_${SUFFIX}_arm64.tar.gz
MESA_URL=…/mesa-${VER}/mesa-for-android-container_${VER}_${SUFFIX}_arm64.tar.gz
```

| Card ids | libc / PM | Turnip suffix | Mesa pin after extract | Notes |
|----------|-----------|---------------|------------------------|-------|
| `debian`, `debian13_chroot` | glibc apt | `debian_trixie` | `/etc/apt/preferences.d/pin-mesa` (native list) | Debian family must also install stock Mesa + write `gpu_mode=virgl` |
| `deepin`, `deepin_chroot` | glibc apt (beige/crimson **only**) | `debian_trixie` (best-effort) | same apt pin | Never add debian.org. If extract/ldconfig fails → virgl |
| `fedora`, `fedora_chroot` | glibc dnf5/dnf | `fedora_43` | `dnf versionlock` if plugin exists, else log | |
| `void`, `void_chroot` | glibc xbps | `void` | none | lfdevs now ships this — Flux guest script ignored it |
| `manjaro`, `manjaro_chroot` | glibc pacman | `archlinux` | none (do **not** rewrite mirrors) | |
| `alpine`, `alpine_chroot` | musl apk v2 | `alpine_3.24` | none | Add `hw_accel` component + profile field |
| `opensuse`, `opensuse_chroot` | glibc zypper | **none** | — | VirGL only. Log clearly. |
| `chimera`, `chimera_chroot` | musl apk **v3** | **none** | — | Do **not** extract `alpine_3.24` (layout/apk differ). VirGL only. |

Future cards (do not implement cards): Ubuntu → `ubuntu_resolute` if 26.04, else `ubuntu_noble`; Kali/Parrot → `debian_trixie`; Arch → `archlinux`. Encode in the **same map** so later family scripts inherit.

---

## 7. Implementation DAG (worker order)

Do in this order. Each PR-sized slice should compile and keep existing guests bootable.

### Slice A — Host detector + prefs + Settings resolve

**New file:** `app/src/main/kotlin/com/ivarna/fluxlinux/core/terminal/GpuAccelDetector.kt`

Port native object. Package `com.ivarna.fluxlinux.core.terminal`. Public API:

```kotlin
object GpuAccelDetector {
    const val MODE_TURNIP = "turnip"
    const val MODE_VIRGL = "virgl"
    data class Detection(val mode: String, val vendorHint: String, val signals: String)
    fun detect(): Detection
    fun fluxGpuEnv(): String            // detect().mode
    fun resolveFluxGpu(raw: String?): String
    // raw: null/""/"auto" → detect().mode
    // "ask"/"manual" → "ask"
    // aliases via same table as guest normalize
    // unknown → virgl
}
```

Prefs (same keys as native so behavior matches):

```kotlin
fun persist(ctx: Context, d: Detection) {
    ctx.getSharedPreferences("fluxlinux_prefs", Context.MODE_PRIVATE) // USE EXISTING PREFS NAME
        .edit().putString("flux_gpu", d.mode).putString("flux_gpu_vendor", d.vendorHint).apply()
}
```

**Find the real prefs file name** FluxLinux already uses (`StateManager` / `ThemePreferences`). Do not invent a second prefs file. Put persist/read next to that SSOT or on the detector with an injected `SharedPreferences`.

`resolveFluxGpu`:

| input | output |
|-------|--------|
| null, `""`, `auto` | `detect().mode` |
| `ask`, `manual` | `ask` |
| turnip aliases | `turnip` |
| virgl aliases | `virgl` |
| else | `virgl` |

**Settings:** `onInstallComponent(..., mapOf("FLUX_GPU" to GpuAccelDetector.resolveFluxGpu(selectedGpu)))`.  
If `ask`, pass `ask` (guest may menu in a TTY; component session is a TTY — OK).

**Tests:** `app/src/test/java/com/ivarna/fluxlinux/core/terminal/GpuAccelDetectorTest.kt`  
Extract matching to `internal`/`@VisibleForTesting` functions that take a lowercase blob + `kgslPresent: Boolean` so unit tests do **not** need Robolectric `Build`. Cover:

- blob with `kalama` + kgsl → turnip / adreno
- blob with `dimensity` / `mali` no kgsl → virgl / mali
- blob with `powervr` → virgl / powervr
- empty blob no kgsl → virgl / unknown
- `resolveFluxGpu("auto")` cannot call `Build` in JVM — test the **alias** function separately: `normalize("adreno")==turnip`, `normalize("auto")==auto`, `normalize("AUTO")==auto`.

Do not put Android `Build` reads inside the pure normalize/match functions.

### Slice B — Guest SSOT library + installer

**New:** `app/src/main/assets/scripts/common/setup/flux_gpu_common.sh`  
POSIX `sh`. Functions only. No `exit` at end. No package manager.

Required functions:

```
flux_gpu_normalize <raw>          → turnip|virgl|ask
flux_gpu_collect_hints            → lowercase blob
flux_gpu_auto_detect              → prints mode|vendor|hints
flux_gpu_arch                     → arm64|armhf|amd64|…
flux_gpu_write_state <mode> <vendor>
flux_gpu_write_gpu_launch         # wrapper reads state + FLUX_GPU_RUNTIME
flux_gpu_write_apply_env          # /usr/local/lib/fluxlinux/apply_gpu_env.sh
flux_gpu_disable_xfce_compositor  # turnip only
flux_gpu_fake_dri
flux_gpu_apply_runtime            # sourced by start scripts: reads gpu_mode, exports env
```

`apply_gpu_env.sh` contract (must be `sh`, no bashisms):

```sh
# /usr/local/lib/fluxlinux/apply_gpu_env.sh
# Sets GPU env in the current shell. Safe to source twice.
# VTEST_SOCKET_NAME must already be set by the start script
#   proot:  /tmp/.virgl_test
#   chroot: /mnt/host-tmp/.virgl_test
flux_gpu_apply_runtime() {
  unset GALLIUM_DRIVER MESA_LOADER_DRIVER_OVERRIDE VK_ICD_FILENAMES
  unset LIBGL_ALWAYS_SOFTWARE TU_DEBUG MESA_VK_WSI_DEBUG
  MODE=virgl
  [ -r /etc/fluxlinux/gpu_mode ] && MODE=$(tr -d '[:space:]' </etc/fluxlinux/gpu_mode)
  case "$MODE" in turnip|virgl) ;; *) MODE=virgl ;; esac
  if [ "$MODE" = turnip ]; then
    if [ -e /usr/lib/aarch64-linux-gnu/dri/kgsl_dri.so ] \
       || [ -e /usr/lib64/dri/kgsl_dri.so ] \
       || [ -e /usr/lib/dri/kgsl_dri.so ] \
       || ls /usr/lib*/dri/kgsl_dri.so >/dev/null 2>&1 \
       || ls /usr/lib/aarch64-linux-gnu/dri/kgsl_dri.so >/dev/null 2>&1; then
      export MESA_LOADER_DRIVER_OVERRIDE=kgsl
    else
      export MESA_LOADER_DRIVER_OVERRIDE=zink
      export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json
    fi
    export TU_DEBUG=noconform
    export MESA_VK_WSI_DEBUG=sw
    export MESA_GL_VERSION_OVERRIDE=4.6
    export MESA_GLES_VERSION_OVERRIDE=3.2
    export MESA_NO_ERROR=1
  elif [ "$MODE" = virgl ]; then
    _sock="${VTEST_SOCKET_NAME:-/tmp/.virgl_test}"
    if [ -S "$_sock" ]; then
      export GALLIUM_DRIVER=virpipe
      export MESA_GL_VERSION_OVERRIDE=4.0
      export MESA_GLES_VERSION_OVERRIDE=3.1
      export MESA_NO_ERROR=1
    else
      export LIBGL_ALWAYS_SOFTWARE=1
      export GALLIUM_DRIVER=llvmpipe
      echo "FluxLinux(guest): VirGL socket missing — llvmpipe fallback"
    fi
  else
    export LIBGL_ALWAYS_SOFTWARE=1
    export GALLIUM_DRIVER=llvmpipe
  fi
  export GPU_MODE="$MODE"
}
```

Search real `kgsl_dri.so` paths after first device extract; add every path you find to the test. If lfdevs uses a different name (`msm_dri.so` / `freedreno`), detect that file instead — **do not guess after implement**; `tar tzf` the tarball or `find` on device.

**Rewrite:** `setup_hw_accel_guest.sh`

1. Root check. `set -eu` (not `pipefail` unless bash — this script must stay **POSIX sh** for Alpine/Chimera).
2. `. /path` — the installer is copied to `/tmp` by the runner. **Bundle common** by having Kotlin prepend `flux_gpu_common.sh` (same pattern as `familySetupPayload` prepends `flux_guest_common.sh`). Script may also try:

   ```
   for f in /tmp/flux_gpu_common.sh /usr/local/lib/fluxlinux/flux_gpu_common.sh; do
     [ -r "$f" ] && . "$f" && break
   done
   ```

   Runner **must** stage common next to the installer.

3. Install **stock** Mesa via existing `_pkg_add` tree (keep Chimera apk v3 / dnf / xbps / zypper / pacman / apt). Package lists stay as they are today (they already work).
4. Resolve MODE from `FLUX_GPU` via `flux_gpu_normalize`. Empty/`auto` → `flux_gpu_auto_detect`.
5. If MODE=turnip: map suffix (`flux_gpu_tarball_suffix` in the **installer**, not common):

   ```
   ID from /etc/os-release (or /usr/lib/os-release)
   debian|raspbian           → debian_trixie
   ubuntu                   → ubuntu_noble   # unless VERSION_ID is 26* → ubuntu_resolute
   deepin                   → debian_trixie
   fedora                   → fedora_43      # VERSION_ID=44 → fedora_44
   void                     → void
   manjaro|arch             → archlinux
   alpine                   → alpine_3.24
   opensuse*|sle*           → ""   (unsupported)
   chimera                  → ""   (unsupported)
   else                     → ""
   ```

6. If suffix empty → MODE=virgl, vendor note `no-tarball`.
7. If turnip: curl `--fail --connect-timeout 15 --max-time 180`. Extract `-C /`. `ldconfig` if present. Then Mesa upgrade tarball (same suffix). Pin if apt. Fail any step → MODE=virgl, do not abort script.
8. If final MODE=turnip: compositor off + fake dri.
9. Always: write state, `gpu-launch`, `apply_gpu_env.sh`, profile.d. Exit 0.

**Rewrite:** `setup_hw_accel_debian.sh` to:

```sh
#!/bin/sh
# Compatibility wrapper — Debian component id still points here.
# Implementation lives in setup_hw_accel_guest.sh + flux_gpu_common.sh
```

Either concat at deploy time or have the wrapper source guest after locating it. **Simplest:** change `DistroRepository` debian `hw_accel.scriptName` to `common/setup/setup_hw_accel_guest.sh` and keep the debian filename as a 5-line wrapper that execs guest if present. HostScriptDeployer already deploys both.

### Slice C — Family first-paint + Alpine/Debian holes

Every family script that already has `_flux_write_gpu_mode` keeps **`virgl`** so first Start before hw-accel still paints.

**Debian family** (`setup_debian_family.sh`): add Mesa packages (match current debian hw script: `mesa-utils libgl1-mesa-dri libegl1 mesa-vulkan-drivers` — fail soft on missing names) and write `/etc/fluxlinux/gpu_mode` = `virgl`. Do **not** download Turnip here.

**Alpine family:** already has mesa. Add `mkdir -p /etc/fluxlinux; echo virgl > /etc/fluxlinux/gpu_mode`.

**Alpine components:** add `hw_accel` pointing at `common/setup/setup_hw_accel_guest.sh` (same as glibc guests).  
**Profiles:** set `hwAccelScript = HW_ACCEL_GUEST` on `DEBIAN_PROOT`, `DEBIAN_CHROOT`, `ALPINE_PROOT`, `ALPINE_CHROOT`.

### Slice D — Onboarding actually runs hw-accel

`OnboardingInstallRunner`:

- After customization completes (success or partial), if `profile.hwAccelScript != null`:
  - `val gpu = GpuAccelDetector.detect()`; persist prefs.
  - **Proot:** `runProotGuestScript(..., scriptAssetPath = profile.hwAccelScript, envPrefix = "FLUX_GPU=${gpu.mode} FLUX_GPU_VENDOR=${gpu.vendorHint}")` after staging `flux_gpu_common.sh` to guest `/tmp` (copy asset to `$PREFIX/tmp` like other scripts — `--shared-tmp` makes it `/tmp` in guest).
  - **Chroot:** build payload = common + installer, `export FLUX_GPU=…`, `runChrootGuestBlocking`.
- Mark `hw_accel` installed **only if** that run exits 0. If it fails, log and **do not** fail the whole onboarding (VirGL first-paint from family is enough). User can re-run from Settings.

**Do not** add a new progress phase id unless weights still sum to 100. Prefer logging under CUSTOM (`log(..., "Hardware acceleration: mode=$mode")`) so UI contracts/tests stay stable. If you add a phase, update `BaseDesktopInstallPlan.phasesFor` **and** `BaseDesktopInstallPlanTest`.

`BaseDesktopInstallPlan`: add `hwAccelPayload(ctx, distroId, fluxGpu, vendor)` that prepends `flux_gpu_common.sh` + exports. Reuse from runner + Settings component path.

`MainActivity.runComponentStep` for `hw_accel`:

```
val raw = extraEnv["FLUX_GPU"]
val mode = GpuAccelDetector.resolveFluxGpu(raw)
merged["FLUX_GPU"] = mode
```

Always prepend `flux_gpu_common.sh` when loading the hw script (same as family prepends common).

### Slice E — Start scripts source apply helper (dedupe)

Replace the duplicated `if turnip / elif virgl / else llvmpipe` blocks with:

```sh
if [ -r /usr/local/lib/fluxlinux/apply_gpu_env.sh ]; then
  . /usr/local/lib/fluxlinux/apply_gpu_env.sh
  flux_gpu_apply_runtime
else
  # keep today's inline block as fallback for old guests
fi
```

Files:

- `debian/proot/start/start_gui.sh` (guest `-c` block **and** `su - flux` block — flux login must source again or inherit exports; `su -` wipes env, so the inner script must source `apply_gpu_env.sh` itself).
- `chroot/start_guest_gui.sh`
- `chroot/start_alpine_gui.sh`
- `chroot/start_debian13_gui.sh`

Set `VTEST_SOCKET_NAME` **before** sourcing.

**Chroot KGSL:** in `fluxlinux_chroot.sh ensure_mounts` (or `start_guest_gui.sh` after `/dev` bind), if `/dev/kgsl-3d0` exists on host and is missing inside chroot, `mknod` + `chmod 666` like `start_debian13_kde_gui_turnip.sh`. `/dev` bind usually suffices; this is fail-soft.

**Proot:** `proot-distro login --shared-tmp` already exposes `/dev`. Do not add extra binds unless device verify shows kgsl missing. If missing, add `--bind=/dev/kgsl-3d0` only when the node exists (one line in `start_gui.sh` login invocation). Document the check in the device report.

### Slice F — Tests + HostScriptDeployer

`HostScriptDeployer`: add `flux_gpu_common.sh` as a required host script (or required asset — it is sourced in guest, so deploying to `$HOME` is optional; **assets must exist**). If you list it, path `scripts/common/setup/flux_gpu_common.sh`.

Tests to add/update:

| Test | Assert |
|------|--------|
| `GpuAccelDetectorTest` | blob/kgsl matrix + normalize aliases |
| `DistroInstallProfileTest` | **every** `allInstallable()` has non-null `hwAccelScript` |
| `DistroRepositoryTest` | alpine + alpine_chroot + debian + debian13_chroot have `hw_accel`; alpine component count becomes 3 |
| `BaseDesktopInstallPlanTest` | only if phases change |
| `OmzPokemonContractTest` / others | do not break |
| Optional: small test that `setup_hw_accel_debian.sh` is a wrapper (file contains `setup_hw_accel_guest` or is < 30 lines) |

No instrumentation tests required for merge. Device checklist is below.

---

## 8. File list (expected touch set)

**New**

- `app/src/main/kotlin/com/ivarna/fluxlinux/core/terminal/GpuAccelDetector.kt`
- `app/src/main/assets/scripts/common/setup/flux_gpu_common.sh`
- `app/src/test/java/com/ivarna/fluxlinux/core/terminal/GpuAccelDetectorTest.kt`

**Rewrite / substantial**

- `app/src/main/assets/scripts/common/setup/setup_hw_accel_guest.sh`
- `app/src/main/assets/scripts/debian/common/setup/setup_hw_accel_debian.sh` (wrapper)

**Kotlin**

- `OnboardingInstallRunner.kt` — run hw script; stop fake-mark
- `BaseDesktopInstallPlan.kt` — `hwAccelPayload`
- `DistroInstallProfile.kt` — debian + alpine `hwAccelScript`
- `DistroRepository.kt` — alpine `hw_accel` component; debian may keep same id, point at SSOT
- `MainActivity.kt` — resolve `FLUX_GPU` + prepend common
- `DistroSettingsScreen.kt` — resolve `auto`
- `HostScriptDeployer.kt` — deploy `flux_gpu_common.sh`

**Start / family**

- `setup_debian_family.sh` — mesa + `gpu_mode=virgl`
- `setup_alpine_family.sh` — write `gpu_mode`
- `start_gui.sh`, `start_guest_gui.sh`, `start_alpine_gui.sh`, `start_debian13_gui.sh` — source apply helper
- `fluxlinux_chroot.sh` — kgsl ensure

**Do not** fork per-distro `setup_hw_accel_fedora.sh` etc. One guest installer.

---

## 9. Device verify (worker must run if a device is attached)

`assembleIvarnaRelease` + `adb install -r`. No uninstall unless signature mismatch.

Pick **one Snapdragon** and **one Mali/other** if both exist; otherwise one device and record `GpuAccelDetector` log.

Minimum:

1. Logcat `GpuAccelDetector` → `mode=` matches SoC.
2. Fresh or Settings re-run hw_accel on **Debian proot** and **Fedora proot**:
   - Adreno: `/etc/fluxlinux/gpu_mode` is `turnip`; `ls` freedreno ICD or `kgsl_dri.so`.
   - Mali: `gpu_mode=virgl`; no turnip tarball extract.
3. **Alpine proot** (musl): Adreno → alpine_3.24 extract or clean virgl fallback (must not brick apk).
4. **Chimera** and **openSUSE**: stay `virgl`, log `no-tarball`, XFCE still starts.
5. **One chroot** (Debian or Fedora): kgsl node visible (`ls /dev/kgsl-3d0` inside chroot on Adreno).
6. Start XFCE: guest log `GPU mode=turnip|virgl`. No black screen (compositor off on turnip).
7. Settings Auto → installs turnip on Adreno (not silent virgl).
8. `FLUX_GPU=virgl` force stays virgl on Adreno.

If no device: unit tests + `sh -n` on every touched script. State that in the report.

---

## 10. Out of scope / follow-ups (do not do now)

- Offline Turnip in APK.
- Per-Adreno-gen blacklist (old 5xx).
- UI toggle on Home XFCE start (Settings is enough).
- KDE three-variant launchers.
- Host Termux native GPU script merge.
- Changing VirGL host server flags (keep `virgl_test_server_android --socket-path $PREFIX/tmp/.virgl_test`).

---

## 11. Worker agent prompt — Pass 2 (copy-paste)

```
You are on Pass 2 of FluxLinux hardware acceleration. Slices A–F are already in the tree. Do NOT re-implement detect, onboarding, or a new installer.

READ FIRST:
- /home/abhaybyte/repos/fluxlinux/docs/plan/hw-accel-all-distros.md  (Pass 2 at the top is SSOT)
- /home/abhaybyte/repos/fluxlinux/docs/plans/results/hw-accel-all-distros-report.md  (Pass 1, Mali-only, incomplete)
- app/src/main/assets/scripts/common/setup/flux_gpu_common.sh
- app/src/main/assets/scripts/common/setup/setup_hw_accel_guest.sh
- debian/proot/start/start_gui.sh and chroot/start_guest_gui.sh (already source apply_gpu_env.sh)

CODE (small):
1. Bug: flux_gpu_has_kgsl_dri treats stock msm_dri.so / freedreno_dri.so as KGSL. That can force MESA_LOADER_DRIVER_OVERRIDE=kgsl on Snapdragon when only stock Mesa DRI exists and GL dies.
   Fix: only kgsl_dri.so (all existing kgsl_dri.so paths). Remove msm/freedreno probes.
   Change BOTH copies: the function in flux_gpu_common.sh AND the heredoc written to /usr/local/lib/fluxlinux/apply_gpu_env.sh.
   sh -n the scripts. Do not refactor Kotlin.

DEVICE (mandatory — user: all distros are installed; run the script on every guest):
2. adb devices. Use every physical phone. Skip emulator-5554.
3. Discover installed proot containers and chroot paths. Do not assume only Debian/Fedora/Alpine.
4. For EACH installed guest (proot AND chroot):
   - Push/stage flux_gpu_common.sh + setup_hw_accel_guest.sh into guest /tmp
   - Run as root with FLUX_GPU=virgl on Mali (this Xiaomi is MT6897 Mali, no /dev/kgsl-3d0)
   - Record gpu_mode, gpu_vendor, apply_gpu_env.sh present, last log lines
   - Start XFCE (app Start or start_gui.sh / start_gui_chroot.sh). Capture "FluxLinux(guest): GPU mode=". Stop the desktop after the line.
5. Once per family, force FLUX_GPU=turnip on Chimera and openSUSE → must log no-tarball and stay virgl. On one glibc + Alpine only, you may try turnip download to prove the URL; then re-run FLUX_GPU=virgl so Mali guests are not left on turnip.
6. If a Snapdragon phone is attached: FLUX_GPU=turnip on Debian proot AND one chroot. Require gpu_mode=turnip, kgsl_dri.so or freedreno ICD, Start log GPU mode=turnip, kgsl node in that chroot. If no Snapdragon, write that explicitly.
7. Replace/extend docs/plans/results/hw-accel-all-distros-report.md with the full table (every installed guest). SKIP only if not installed.

RULES:
- Do not rewrite GpuAccelDetector, OnboardingInstallRunner, family scripts, or add per-distro hw_accel_*.sh.
- Do not implement KDE / Termux-native GPU.
- Deepin: never add debian.org. Manjaro: never rewrite mirrors.
- Modes on disk: only turnip or virgl. Leave Mali guests on virgl when you finish.
- assembleIvarnaRelease + adb install -r if APK assets must update after the kgsl probe fix.
- If a Start fails for a known non-GPU reason, say so; still record the GPU mode line if printed.

Stop when the kgsl probe is fixed, the report table is complete, and Gradle is not left running.
```

---

## 12. Acceptance (Pass 2)

- [ ] `kgsl_dri.so` is the only kgsl probe (both copies).
- [ ] Report table has a row for every **installed** proot and chroot guest.
- [ ] Every started desktop printed `GPU mode=`.
- [ ] Mali: no guest left on `gpu_mode=turnip`.
- [ ] Snapdragon Turnip path verified **or** “no Snapdragon attached”.
- [ ] No A–F rewrite. No KDE/Termux-native work.
