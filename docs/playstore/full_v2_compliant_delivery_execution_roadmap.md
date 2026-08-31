# FluxLinux — Full v2 Google Play-Compliant Linux Delivery & Execution Roadmap

**Research date:** 2026-08-31  
**Goal:** Restore as much of FluxLinux v2.0's real Linux experience as possible in the Google Play build **without** relying on remote native-code execution, writable direct `execve`, Android root/chroot, APK sideloading, or policy-obscuring tricks.

> This is an engineering/policy design, not a guarantee of Play approval. Google Play makes the final enforcement decision. The architecture below is intentionally conservative and should be accompanied by an explicit Play review/support inquiry before production rollout.

---

## 1. The core policy constraint

Google Play's current **Device and Network Abuse** policy states that a Play-distributed app may not download executable code such as DEX/JAR/`.so` from a source other than Google Play. It also explicitly states an exception for code that runs in a **virtual machine or interpreter**. The same policy separately requires compliance with Android system/security requirements.

Official source:

- https://support.google.com/googleplay/android-developer/answer/16559646

Relevant Android platform constraint:

- Android 10+ removes direct execution permission for files in a writable app home directory for apps targeting API 29+; Android recommends loading binary code embedded in the APK.
- https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission

### Consequence for FluxLinux

The current v2 design is not enough for full Play parity because:

1. `RootfsDownloader` downloads distro rootfs archives from GitHub.
2. PRoot then ultimately runs guest Linux ELF binaries as native executables.
3. Even if the initial rootfs were moved to Google Play delivery, package managers (`apt`, `dnf`, `pacman`, `apk`, `zypper`) would later download additional executable binaries from distro repositories.
4. Therefore **Play Feature Delivery by itself does not solve full Linux extensibility**.

The durable solution is:

> **Google Play delivers FluxLinux's trusted execution engine. All Linux guest code—whether initially delivered by Play or later installed by the user—is executed only through a VM/interpreter/emulator boundary. It is never directly executed by Android.**

---

## 2. Recommended architecture

### Primary architecture: Play Feature Delivery + interpreted Linux guest

```text
Google Play
   |
   +-- base APK / base split
   |     - FluxLinux UI
   |     - terminal frontend
   |     - lifecycle/session manager
   |     - policy-safe host services
   |
   +-- guest_engine dynamic feature
   |     - QEMU user-mode TCG engine (MVP)
   |       or full-system QEMU engine (fallback/stronger isolation)
   |     - Play-delivered native libraries only
   |
   +-- distro_debian dynamic feature
   |     - Debian rootfs/base image
   |
   +-- distro_alpine dynamic feature
   |     - Alpine rootfs/base image
   |
   +-- distro_fedora ... etc.
         - distro base payload

                    Google Play-delivered engine
                              |
                              v
                    +--------------------+
                    | VM / Interpreter   |
                    | boundary           |
                    +--------------------+
                              |
                       Linux guest code
                     /        |          \
                  apt       shell       XFCE
                   |          |           |
              external     scripts     applications
              packages

NO guest ELF is directly execve()'d by Android.
NO guest package becomes a host Android native library.
NO guest has a direct Android API bridge.
```

### Why Play Feature Delivery, not Play Asset Delivery

**Play Feature Delivery (PFD)** is designed to deliver **code and resources** on demand through Google Play, including native code in feature modules.

Official docs:

- https://developer.android.com/guide/playcore/feature-delivery
- https://developer.android.com/guide/playcore/feature-delivery/on-demand

Google explicitly documents loading native code from an on-demand feature module.

**Play Asset Delivery (PAD)** is not the right vehicle for Linux executable payloads. Google's PAD documentation says asset packs contain assets such as textures/shaders/sounds **but no executable code**.

- https://developer.android.com/guide/playcore/asset-delivery

Therefore:

- Use **PFD dynamic feature modules** for distro payloads that contain executable guest software.
- Do not use PAD merely to hide a Linux rootfs as an “asset”.

---

## 3. Why this is feasible for FluxLinux's size

Current Play limits are large enough for the v2 distro catalog:

- individual feature module: up to 500 MB compressed
- cumulative install-time modules: up to 4 GB
- recommended feature module count is far above FluxLinux's ~12 distro modules for minSdk 26+

Official source:

- https://support.google.com/googleplay/android-developer/answer/9859372

FluxLinux v2's existing distro rootfs archives are generally well below the individual 500 MB feature-module limit.

Recommended module layout:

```text
:app
:guest-engine
:distro-debian
:distro-alpine
:distro-fedora
:distro-void
:distro-opensuse
:distro-deepin
:distro-chimera
:distro-manjaro
:distro-ubuntu
:distro-kali
:distro-parrot
:distro-arch
```

Each distro module should be **on-demand** and requested only when the user chooses that distro.

---

# 4. Execution strategy options

## Option A — QEMU user-mode TCG + PRoot filesystem isolation

**Recommended MVP because it has the smallest migration from v2.**

### Model

Current:

```text
Android -> Play-delivered proot -> guest ELF directly executed
```

New:

```text
Android
  -> Play-delivered PRoot/orchestration
      -> Play-delivered qemu-aarch64 TCG
          -> guest ELF treated as guest input/code
```

Every guest executable must be forced through QEMU. PRoot remains only a filesystem/syscall-isolation helper; it must never fall back to native guest execution.

### Benefits

- preserves current rootfs layout
- preserves much of existing install scripting
- preserves `/home`, distro filesystem, users, shell profiles
- package managers can continue to work
- package-installed native ELF remains inside interpreter execution path
- easier to integrate with the current `LinuxCommandBuilder` / `GuestSessionFactory`
- no Android root permission

### Risks

- QEMU user-mode is less visibly isolated than a full-system VM because guest syscalls ultimately reach the host kernel through translation
- same-architecture TCG adds CPU overhead
- absolute path/filesystem behavior must be carefully contained
- GPU acceleration requires a controlled virtual/IPC path rather than passing arbitrary host device access
- Play may still request clarification about whether this architecture qualifies for the interpreter exception

### Mandatory invariant

There must be **no native guest fallback**.

If QEMU is missing, broken, incompatible, or crashes, FluxLinux must stop the session instead of executing the guest ELF directly.

---

## Option B — full-system QEMU TCG VM

**Strongest policy/security boundary and fallback if user-mode interpretation is considered too ambiguous.**

### Model

```text
Android app
   |
   +-- Play-delivered QEMU system engine
           |
           +-- virtual CPU (TCG)
           +-- virtual RAM
           +-- virtual block device
           +-- guest Linux kernel
           +-- guest root filesystem
```

Guest software executes under a separate Linux kernel inside an emulated virtual machine.

### Benefits

- clearest possible VM interpretation of Play's policy exception
- guest root is safe: root exists only inside the VM
- apt/dnf/pacman downloads never become Android-host executables
- no PRoot/native guest execution ambiguity
- easier to demonstrate a security boundary to Play reviewers
- full guest `/proc`, `/sys`, process model and distro behavior

### Costs

- larger architecture change
- more RAM
- higher boot latency
- potentially lower performance
- X11/audio/GPU integration must cross a VM boundary
- persistent disk/overlay management required

### When to choose it

Switch to full-system VM if any of these are true:

1. Google indicates QEMU-user + PRoot is not sufficient for the VM/interpreter exception.
2. Security testing finds a route from guest execution into direct host ELF execution.
3. The user-mode filesystem/syscall sandbox cannot be made reliably fail-closed.
4. Reviewers repeatedly flag the user-mode design despite complete documentation.

---

## Option C — PFD-only native rootfs, package managers disabled

This is compliant-looking for **curated static functionality**, but it does **not** preserve the general Linux experience.

All executables would be delivered through Play and package managers would be prohibited from installing executable packages from external repositories.

Use only as a temporary fallback if the interpreted runtime is not ready.

---

## Option D — cloud Linux

Run Linux on a remote server and make FluxLinux a terminal/desktop client.

This avoids local dynamic executable delivery entirely, but introduces hosting cost, accounts, security, privacy, latency, data-safety declarations, abuse prevention and service availability concerns.

Not recommended as the primary FluxLinux architecture.

---

# 5. Recommended decision

Implement **Option A first**, but design the interfaces so the engine can be replaced with **Option B** without changing the UI or distro model.

Architecture names:

```kotlin
interface GuestExecutionEngine

class QemuUserGuestEngine : GuestExecutionEngine
class QemuSystemGuestEngine : GuestExecutionEngine
```

The old direct PRoot implementation remains available only to the non-Play `ivarna` flavor:

```kotlin
class NativeProotGuestEngine : GuestExecutionEngine
```

Play flavor must never instantiate `NativeProotGuestEngine`.

---

# 6. Required source-set boundary

Recommended:

```text
app/src/main/
    common UI
    distro metadata
    GuestExecutionEngine interface
    session lifecycle
    terminal model

app/src/ivarna/
    NativeProotGuestEngine
    current GitHub RootfsDownloader
    current chroot/root features

app/src/zenithblue/
    PlayGuestEngineProvider
    SplitInstall/PFD integration
    policy-safe downloader facade

modules/guest-engine/
    Play-delivered QEMU engine

modules/distro-*/
    on-demand Play feature modules
```

Do not rely on:

```kotlin
if (BuildConfig.PLAY_STORE) { ... }
```

for the critical boundary. The direct guest executor should be absent from the Play variant/runtime graph where practical.

---

# 7. Play-delivered distro format

Each distro dynamic feature contains:

```text
assets/
  flux-distro-manifest.json
  rootfs.tar.xz
```

Example manifest:

```json
{
  "id": "debian",
  "format": 1,
  "arch": "aarch64",
  "rootfs": "rootfs.tar.xz",
  "sha256": "...",
  "guestExecution": "interpreter-required"
}
```

### Install flow

1. User taps Debian.
2. App checks `SplitInstallManager.installedModules`.
3. If missing, show the module's size/value and request installation.
4. Google Play downloads and verifies the split.
5. FluxLinux reads the distro manifest from the installed module.
6. FluxLinux extracts/copies the rootfs into app-internal storage.
7. Extracted ELF files are treated as **guest files only**.
8. Start shell only through `GuestExecutionEngine`.

Never create a host-native fallback based on file executable bits.

---

# 8. Package manager behavior

This is the reason the interpreter boundary is mandatory.

Inside a Play guest, these should continue to work:

```bash
apt install ...
dnf install ...
apk add ...
pacman -S ...
zypper install ...
```

But every resulting executable is still guest code.

Rules:

1. Package managers execute only inside the interpreted/VM environment.
2. Files downloaded by package managers remain inside the guest filesystem.
3. Host code never calls `execve()` on a guest package.
4. Host code never calls `dlopen()` / `System.load()` on guest libraries.
5. Guest libraries never become Android JNI libraries.
6. No guest package can install an Android APK.
7. No guest process receives a generic Android Binder/JNI bridge.

The policy exception is not a license to expose arbitrary Android APIs to downloaded code.

---

# 9. Host execution must be reduced

The current v2 embedded Termux-prefix host is itself problematic for Play if users can install and directly execute new host-native packages.

For the Play build:

### Do

- keep only a minimal Play-delivered host runtime required to launch the interpreter/VM
- package host native components as normal native libraries / feature-module native code
- use Android/Kotlin for orchestration
- execute user Linux tools inside the guest

### Do not

- expose a general-purpose host package manager
- allow `pkg install` / host `apt` to add direct Android-host executable binaries
- download replacement host bash/proot/QEMU binaries
- repair the engine from GitHub
- execute binaries copied into writable host storage

Conceptually:

```text
Old Play idea: Android + embedded Termux host + PRoot
New Play idea: Android + small fixed VM launcher + Linux guest
```

---

# 10. Android W^X-safe engine packaging

The QEMU/VM engine itself must come through Google Play.

Preferred native packaging:

```text
lib/arm64-v8a/libflux_guest_engine.so
lib/arm64-v8a/libqemu_flux.so
```

or native libraries inside the installed `guest-engine` dynamic feature.

Google documents native code in on-demand feature modules and recommends appropriate loading after module installation.

Do not:

- download QEMU from GitHub at runtime
- copy QEMU into `$filesDir` and execute it there
- disguise QEMU as another extension
- ship a nested APK containing the engine

If an executable process is needed instead of JNI/in-process QEMU, build a Play-packaged PIE/native executable in a location that Android installs with executable permissions, and verify the exact behavior on API 33–36. Prefer a JNI/in-process engine when technically practical because it avoids another `execve` boundary.

---

# 11. Interpreter-only enforcement

Create a central execution guard.

Example conceptual API:

```kotlin
data class GuestExecutable(
    val distroId: String,
    val guestPath: String,
    val argv: List<String>
)

interface GuestExecutionEngine {
    fun start(request: GuestExecutable): GuestProcess
}
```

There should be no API accepting a host filesystem executable path for Play guest sessions.

### Forbidden Play APIs/paths

The Play implementation must not use guest content with:

```text
ProcessBuilder(<guest ELF path>)
Runtime.exec(<guest ELF path>)
Os.execv(<guest ELF path>)
Os.execve(<guest ELF path>)
System.load(<guest library>)
dlopen(<guest library>)
/system/bin/linker64 <guest ELF>
```

A CI scanner should inspect source and bytecode/native strings for accidental fallback paths.

---

# 12. Filesystem sandbox

## QEMU-user MVP

Build a rootfs namespace where guest-visible `/` maps only to the distro tree plus explicitly approved mounts.

Do not bind:

- Android `/system`
- Android `/vendor`
- Android app private native-lib directory
- other apps' storage
- broad `/dev`
- sensitive host `/proc`

Expose only required virtualized resources.

### User files

Use one of:

- an app-private `~/shared` directory
- user-selected SAF directory proxied through controlled import/export

Do not provide arbitrary host path mounts by default.

## Full-system VM

Use a virtual block device with a read-only base and writable overlay. Shared user files cross via an explicit virtual/shared-files channel.

---

# 13. Root model

Remove Android root/chroot from Play.

Replace it with **guest root**:

```text
Android UID: ordinary untrusted app
VM/interpreter guest UID: root
```

Users can run:

```bash
sudo
su
apt
dpkg
mount-like guest operations supported by the guest model
```

without granting FluxLinux Magisk/KernelSU/APatch privileges.

This can provide a more authentic Linux root experience while keeping Android isolated.

---

# 14. Networking

Guest network should be mediated through the engine.

Recommended defaults:

- outbound TCP/UDP through QEMU user networking / controlled socket translation
- no raw host networking
- no TUN/TAP requirement for the MVP
- inbound ports disabled by default
- user-configured port forwarding only
- bind host-exposed ports to `127.0.0.1` by default

This keeps the guest clearly behind the execution boundary.

---

# 15. Terminal integration

Current `GuestSessionFactory` ultimately creates a Termux `TerminalSession` around a host executable command.

Refactor so UI terminals attach to an engine-backed PTY/session abstraction:

```text
Terminal UI
   |
TerminalSessionAdapter
   |
GuestExecutionEngine
   |
QEMU user console / VM serial console
```

Do not make the Compose UI understand whether the backend is PRoot/QEMU/full VM.

Target interface:

```kotlin
interface GuestTerminalChannel {
    fun write(bytes: ByteArray)
    fun resize(rows: Int, cols: Int)
    fun close()
    val output: Flow<ByteArray>
}
```

---

# 16. X11 / desktop plan

The desktop feature should remain possible, but it must not create a guest-to-Android arbitrary API bridge.

## QEMU-user MVP

Potential approach:

```text
Guest XFCE
  -> guest X11 client libraries
  -> controlled local socket transport
  -> Play-delivered FluxLinux X11 server
  -> Android SurfaceView
```

Use a dedicated transport endpoint and explicit protocol, not host filesystem/device access.

## Full-system VM

Options:

1. guest X server + VNC/RDP/SPICE to host client
2. virtio-gpu based display path
3. X11 over virtual network to host X server

Start with software rendering and functional XFCE before GPU acceleration.

---

# 17. Audio plan

Maintain a narrow audio protocol boundary.

Possible MVP:

```text
Guest PulseAudio client
     -> VM/user networking
     -> host PulseAudio-compatible endpoint on loopback/private channel
     -> AAudio
```

Do not expose arbitrary host audio device nodes to the guest.

---

# 18. GPU acceleration plan

Current PRoot Turnip/VirGL paths may rely too heavily on direct host device/driver access to carry into the Play sandbox unchanged.

Use phases:

### GPU Phase 0

Software rendering only. Prove delivery/execution compliance first.

### GPU Phase 1

VirGL/virtio-gpu-style mediated rendering:

```text
Guest Mesa virgl
  -> virtual protocol
  -> Play-delivered virglrenderer host component
  -> Android OpenGL/Vulkan
```

### GPU Phase 2

Investigate Venus/virtio Vulkan or another mediated Vulkan transport.

Rules:

- host renderer is Play-delivered
- guest cannot load arbitrary Android host GPU driver `.so`
- guest does not open privileged/sensitive device nodes directly unless explicitly proven safe and policy-compatible
- no external driver DLL/`.so` download into host execution path

---

# 19. Distro updates

There are two kinds of updates.

## Base image update

Update through a new app release / dynamic feature version on Google Play.

Use when changing:

- initial rootfs
- kernel/initrd for full-system VM
- boot agent
- required guest integration helpers

## User package update

Inside the VM/interpreter:

```bash
apt update && apt upgrade
```

This does not alter the Android app or its Play-delivered execution engine. It only changes guest files that continue to be interpreted/virtualized.

Never use distro package updates to replace the host QEMU/engine.

---

# 20. Security boundary

The interpreter/VM must be treated as hostile-input execution.

Required controls:

- no Binder bridge exposed to guest code
- no arbitrary JNI bridge
- no host `execve` fallback
- no loading guest `.so` into host process
- strict path boundary
- controlled network
- explicit import/export of user files
- no Android root
- no APK installer
- no package visibility beyond genuine app need
- crash/timeout handling
- resource limits

Fuzz/test engine request parsing, path traversal and guest-host protocol handlers.

---

# 21. Full-system VM note: Android AVF is not available to normal Play apps

Android's own Virtualization Framework would be attractive, and Android itself now has a Linux development environment that downloads a Debian-based OS image and runs it in AVF.

However current AVF app APIs are `@SystemApi` and require the restricted `MANAGE_VIRTUAL_MACHINE` permission; they are not available to ordinary third-party Play apps.

Sources:

- https://android.googlesource.com/platform/packages/modules/Virtualization/+/HEAD/libs/framework-virtualization/README.md
- https://source.android.com/docs/core/virtualization/usecases

Therefore FluxLinux cannot base its public Play architecture on AVF today. Use an app-owned emulator such as QEMU unless Android exposes a public third-party virtualization API in the future.

---

# 22. Proof-of-concept gates before large refactor

Do **not** port all 12 distros immediately.

Build three POCs in order.

## POC A — interpreter viability

- Play-packaged QEMU AArch64 TCG
- tiny Alpine rootfs packaged locally
- run `/bin/sh`
- run `uname -a`
- run dynamically linked binary
- create/write/read files
- confirm Android never directly `execve()`s guest ELF

**Gate:** functionality + crash-free execution on API 33–36.

## POC B — package installation

Inside interpreted guest:

```bash
apk update
apk add curl
curl --version
```

or Debian:

```bash
apt update
apt install -y curl
curl --version
```

**Gate:** newly downloaded `curl` runs only via interpreter and no direct host execution occurs.

## POC C — Play Feature Delivery

- move the test rootfs into one on-demand dynamic feature
- upload to Internal App Sharing / internal test track
- install base app from Play
- request distro module via Play Feature Delivery
- run the same shell/package tests

**Gate:** no GitHub rootfs fetch exists in the Play path.

Only after all three gates pass should the product refactor begin.

---

# 23. Performance benchmark gate

Compare:

1. current native PRoot (`ivarna` only)
2. QEMU user-mode TCG
3. full-system QEMU TCG if implemented

Benchmarks:

```text
shell startup
apt update
package install
openssl speed
python startup
compression/decompression
make -jN compile test
XFCE startup
browser/basic GUI if available
RAM idle
RAM during package install
battery/thermal 10-minute load
```

Decision rule:

- If QEMU-user provides acceptable usability, continue with it.
- If it is too slow, optimize interpreter/JIT before weakening the execution boundary.
- Never restore direct guest execution in Play as a performance shortcut.

---

# 24. Compliance evidence package

Before Play submission create:

```text
docs/playstore/evidence/
  architecture.md
  execution-boundary.md
  delivery-flow.md
  package-manager-flow.md
  permissions.md
  foreground-services.md
  data-safety.md
  artifact-scan.md
  reviewer-notes.md
```

Reviewer note should clearly state:

- Linux base modules are delivered by Google Play Feature Delivery.
- FluxLinux does not self-update outside Google Play.
- The execution engine itself is delivered only by Google Play.
- User-installed Linux packages are guest code and execute exclusively under the bundled VM/interpreter.
- Guest code cannot be loaded as Android DEX/JAR/JNI/native host code.
- No Android root permission is requested.
- No APK installation functionality exists.

Request written clarification through Play Console support before broad production rollout and retain the response with the release evidence.

---

# 25. CI policy gates

The Play CI must fail if:

### Delivery violations

- `RootfsDownloader` is reachable/included in the Play runtime when using PFD-only base delivery
- `releases/download/rootfs` exists in Play DEX/native strings for host rootfs delivery
- executable engine update URL exists
- nested APK exists

### Direct execution violations

Search Play code for guest-path use with:

```text
Runtime.exec
ProcessBuilder
Os.execv
Os.execve
System.load
System.loadLibrary (except known Play engine libs)
dlopen
/system/bin/linker
```

Static checks must be backed by instrumentation tests.

### Root violations

- `ACCESS_SUPERUSER`
- Magisk/KernelSU/APatch integration
- Android chroot path

### Artifact checks

Inspect generated AAB and Play-generated APK sets for:

- expected dynamic feature modules
- only expected native engine libraries
- no nested APK
- correct application ID
- correct permissions/services
- no remote host-executable downloader

---

# 26. Runtime anti-escape tests

Build tests specifically attempting to break the invariant:

1. Execute a guest ELF by absolute host path -> must be rejected.
2. Guest installs a new ELF -> must execute only under QEMU.
3. Rename guest ELF to `.so` -> host loader must reject it.
4. Guest writes a fake APK -> app must never invoke installer.
5. Guest tries `/system/bin/sh` -> unavailable/rejected inside sandbox.
6. Guest tries host native-library directory -> unavailable.
7. Guest path traversal through shared folder -> blocked.
8. QEMU missing -> session fails closed.
9. QEMU exits unexpectedly -> no native retry.
10. Malformed distro manifest -> module refused.

---

# 27. Migration from current v2 classes

Current class -> target role:

| Current | New |
|---|---|
| `LinuxCommandBuilder` | delegates to `GuestExecutionEngine` |
| `ProotCommandBuilder` | `ivarna` native engine + optional helper for QEMU-user sandbox |
| `ChrootCommandBuilder` | `ivarna` only; removed from Play |
| `GuestSessionFactory` | engine-neutral terminal/session creator |
| `RootfsDownloader` | `ivarna` only; Play replaced by `PlayDistroDelivery` |
| `DistroInstallProfile.rootfsUrl` | non-Play field; Play uses module name/payload manifest |
| `HostScriptDeployer` | split into safe app integration + non-Play host deployer |
| `TermuxHostPaths` | non-Play host; Play reduced to fixed engine paths |
| `RootShell` | non-Play only |
| `DesktopLauncher` | engine-neutral desktop control channel |

Introduce:

```text
GuestExecutionEngine.kt
GuestEngineProvider.kt
GuestTerminalChannel.kt
GuestFilesystem.kt
PlayDistroDelivery.kt
DistroModuleManifest.kt
QemuUserGuestEngine.kt
QemuSystemGuestEngine.kt
GuestPolicyGuard.kt
```

---

# 28. Implementation phases

## Phase 0 — policy/technical spike

- build QEMU-user TCG POC
- verify API 33–36
- prove package install
- document direct-exec absence

**No product UI refactor yet.**

## Phase 1 — engine abstraction

- introduce `GuestExecutionEngine`
- adapt terminal sessions
- keep native PRoot working on `ivarna`
- add interpreted engine for `zenithblue`

## Phase 2 — Play Feature Delivery

- create `guest-engine` module if appropriate
- create Debian + Alpine distro modules first
- implement module request/progress/error UI
- remove Play rootfs GitHub delivery

## Phase 3 — interpreter-only sandbox

- fail-closed execution
- path boundaries
- network boundary
- guest-root model
- anti-escape tests

## Phase 4 — package managers

- apt/apk first
- then dnf/pacman/zypper
- prove newly downloaded executables stay guest-only

## Phase 5 — all distro modules

- Fedora
- Void
- openSUSE
- Ubuntu
- Arch
- Manjaro
- Deepin
- Chimera
- Kali
- Parrot

## Phase 6 — desktop

- terminal first
- XFCE software rendering
- audio
- X11 transport

## Phase 7 — mediated GPU

- virgl/virtio-style renderer
- later Vulkan transport investigation

## Phase 8 — compliance package

- CI
- AAB/split inspection
- internal Play delivery testing
- privacy/Data Safety
- FGS review
- written Play clarification

## Phase 9 — production

- staged rollout
- monitor policy/pre-launch reports
- keep old Play release rollback path

---

# 29. Stop conditions

Do not proceed to production if any of these remain true:

- a guest ELF can execute natively outside the interpreter/VM
- Play app can download/replace its execution engine outside Google Play
- package-installed guest libraries can be loaded into the Android host
- Android root/chroot remains in Play
- rootfs host download remains outside the interpreter exception without explicit approval
- guest has a generic Android API/Binder/JNI bridge
- QEMU failure triggers native execution fallback
- PFD delivery has not been tested from an actual Play-hosted track
- compliance evidence is incomplete

---

# 30. Worker order

Use the worker files under:

```text
docs/playstore/compliant-runtime-workers/
```

in this order:

1. `01_qemu_user_poc.md`
2. `02_engine_abstraction.md`
3. `03_play_feature_delivery_poc.md`
4. `04_interpreter_only_sandbox.md`
5. `05_package_manager_validation.md`
6. `06_play_distro_modules.md`
7. `07_terminal_desktop_audio.md`
8. `08_gpu_mediation.md`
9. `09_ci_and_compliance_evidence.md`
10. `10_internal_play_validation.md`

Do not start the full distro/module migration until workers 01–05 are PASS.

---

# 31. Bottom line

The intended end-state is **not** a crippled external-Termux Play build.

The intended end-state is:

> **FluxLinux Play ships its own Google-Play-delivered Linux execution engine. Distros are delivered on demand through Play Feature Delivery. Linux programs and future packages execute only as guest code inside a QEMU/interpreter/VM boundary. Users retain normal Linux package managers and guest root, while Android itself remains an ordinary unrooted app sandbox.**

This is the architecture that should be pursued for genuine v2 feature parity while respecting the current executable-code policy rather than merely hiding or disabling features.
