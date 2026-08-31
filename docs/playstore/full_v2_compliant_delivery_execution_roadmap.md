# Full v2 Play Delivery/Execution Architecture — Technical Supplement

> **Status:** active technical supplement to `v2_0_compliance_roadmap.md`.
>
> This file no longer defines a separate QEMU-first implementation track. The canonical worker sequence is `docs/playstore/workers/01...10`.

## 1. Final architecture decision

FluxLinux Play keeps the v2 embedded Linux backend:

```text
terminal + install/session orchestration
                |
                v
same-architecture native PRoot
                |
                +-- guest rootfs
                +-- XFCE/X11
                +-- PulseAudio
                +-- package managers
```

The major compliance refactor is **delivery**, not CPU execution.

Flux-managed executable-bearing runtime payloads are distributed by Google Play as part of the App Bundle using:

- the base module for small always-required app code and directly executed host native launchers;
- `:runtime_host` as an on-demand **Play Feature Delivery dynamic-feature module** for the large host bootstrap/prefix payload;
- one on-demand PFD dynamic-feature module per distro/rootfs.

Do not use Play Asset Delivery for these rootfs/bootstrap payloads. Do not use QEMU as the default same-architecture backend.

---

## 2. Target module graph

```text
:app  (base / zenithblue)
|
|-- Kotlin/Java/Compose application
|-- install/session state machine
|-- Play feature coordinator
|-- X11 Android integration
|-- safe scripts/config
|-- jniLibs/<abi>/
|   |-- direct host bash/launcher pieces
|   |-- PRoot loaders/runtime pieces
|   |-- required Pulse/X11/native bridges
|
|-- dynamicFeatures
    |-- :runtime_host
    |-- :distro_alpine
    |-- :distro_debian
    |-- :distro_fedora
    |-- :distro_void
    |-- :distro_opensuse
    |-- :distro_chimera
    |-- :distro_deepin
    |-- :distro_manjaro
    |-- :distro_ubuntu
    |-- :distro_kali
    |-- :distro_parrot
    `-- :distro_arch
```

The exact module names may be adjusted for Gradle conventions, but the registry must have a deterministic one-to-one mapping from supported Play distro to its on-demand module.

---

## 3. Existing backend seam

The most important code-preservation goal is to keep the existing installer after a local rootfs becomes available.

Current v2 concept:

```text
RootfsDownloader
 -> rootfs file
 -> SHA/check
 -> flux_install / proot-distro path
 -> setup/customization
 -> StateManager installed state
```

Play concept:

```text
PlayFeatureRootfsProvider
 -> rootfs file
 -> same SHA/check
 -> same flux_install / proot-distro path
 -> same setup/customization
 -> same StateManager installed state
```

`OnboardingInstallRunner` currently provides this seam directly: it obtains the rootfs, then exports local path/name/SHA metadata and invokes the existing host install script.

For Play, remove `FLUX_ROOTFS_URL` from that contract. The install script must not need a remote fallback once PFD has supplied the archive.

---

## 4. Payload provider design

Recommended abstractions:

```kotlin
interface HostRuntimePayloadProvider {
    fun observe(): Flow<PayloadState>
    suspend fun ensureAvailable(): PayloadResult
}

interface RootfsPayloadProvider {
    fun observe(profile: DistroInstallProfile): Flow<PayloadState>
    suspend fun ensureAvailable(profile: DistroInstallProfile): PayloadResult
}

data class PayloadResult(
    val file: File,
    val version: Int,
    val sha256: String,
    val source: PayloadSource,
)
```

The common installer must not depend directly on `SplitInstallManager`.

### Play implementation

`PlayFeature*Provider` owns:

- checking whether a feature is installed;
- requesting the on-demand feature;
- observing download/install states;
- cancellation/error mapping;
- materializing the packaged archive into an app-private staging file;
- verification;
- returning a local file.

### Non-Play implementation

`ivarna` may keep existing remote/bundled providers behind its build-time source boundary.

---

## 5. Feature request flow

```text
Distro install button
     |
     v
Is host runtime ready?
     | no
     v
request :runtime_host from Play
     |
     v
materialize + verify bootstrap
     |
     v
existing host extraction/prepareHost flow
     |
     v
Is distro feature installed?
     | no
     v
request :distro_<id> from Play
     |
     v
materialize + verify rootfs
     |
     v
existing rootfs installation/configuration
     |
     v
INSTALLED
```

The runtime and distro features may be requested together if the current Play APIs and UX make that cleaner, but failure/cancellation state must remain individually diagnosable.

Do not mark host/distro installed based only on PFD module state. The payload must also pass local verification and setup.

---

## 6. Feature payload materialization

The existing backend expects normal filesystem paths. Dynamic feature internals should not leak throughout the app.

Recommended flow:

1. PFD reports module installed.
2. Obtain the feature's packaged asset/resource through supported split/module APIs.
3. Stream it to `filesDir` staging.
4. Calculate SHA-256 during the copy.
5. Verify manifest/version/size/checksum.
6. Rename/promote only after verification.
7. Hand the local file to existing installer.
8. Clean stale staging files after cancellation/failure/process restart.

Never persist the feature's internal split APK path as durable state.

Optional later optimization: after successful extraction and robust reinstall/repair behavior is proven, request deferred feature uninstall to reduce duplicate archive storage. This is not required for the first compliant build.

---

## 7. Host bootstrap classification

The current bootstrap is not a single category of file.

### Direct Android-host executable launcher/native code

Keep in normal Play-installed native locations. The existing v2 `TermuxHostPaths` W^X strategy should remain the baseline.

### Large host-prefix archive

Deliver in `:runtime_host`, then extract to the existing app-private prefix if required.

### Interpreted scripts/configuration

Can live in base or runtime feature and be copied into the prefix.

### Remote repair

Remove from Play. Repair means re-request/re-materialize `:runtime_host`.

This preserves performance and current targetSdk-36 behavior without forcing the full Termux-style prefix into `jniLibs`.

---

## 8. PRoot execution/security model

PRoot is a userspace filesystem/syscall translation environment. It is not a VM and not a CPU interpreter.

For Play:

```text
Android process = normal FluxLinux app UID
PRoot guest uid=0 = fake guest root
real Android root = absent
```

Remove Android root/chroot integrations while preserving PRoot `-0` and normal Linux administration.

The PRoot bind surface should be deliberately documented. Keep only the Android paths/sockets required for guest operation, user files, X11 and audio.

---

## 9. Package-manager boundary

A normal Linux environment is expected to permit user actions such as:

```text
apt update
apt install <package>
apk add <package>
dnf install <package>
pacman -S <package>
```

These actions are initiated by the user inside the guest and use distro repositories. They must not become a hidden Android-side executable update mechanism.

Rules:

- Android/Kotlin code must not silently invoke guest package managers to update Flux host code;
- downloaded guest packages must not be loaded as Android app plugins/native libraries;
- guest execution remains under PRoot/app UID;
- no exported arbitrary-command bridge;
- reviewer/privacy notes must describe this behavior factually;
- real Internal/Closed Play testing is a release gate.

If review explicitly rejects the model, reassess openly rather than disguising it.

---

## 10. X11 loader cleanup

The old nested `loader.apk`/`loader.bin` mechanism must not survive in the final Play AAB.

However, loader removal is sequenced after replacement:

```text
understand loader consumers
 -> integrate normal Android module/dependency/in-process replacement
 -> prove X11 works
 -> remove nested APK + reconstruction/deploy logic
 -> add CI magic-byte check
```

---

## 11. Foreground-service model

PFD download does not require FluxLinux's custom HTTP/data-sync service.

```text
PFD downloading feature
    -> no Flux download FGS

feature installed
    -> optional BaseInstallService for long local verify/extract/configure

interactive terminal/desktop
    -> AppTerminalService/DesktopSessionService only if genuinely necessary
```

Remove deprecated external-Termux `InstallServerService` from the Play flavor.

Every retained FGS needs accurate type, user-visible notification, start/stop lifecycle and Play Console declaration.

---

## 12. Failure/recovery requirements

The new delivery provider must recover from:

- Play Store unavailable/outdated;
- feature not found;
- user cancellation;
- network loss;
- insufficient storage;
- process death during PFD request;
- process death during archive materialization;
- checksum mismatch;
- interrupted extraction;
- stale installed markers;
- app update with old extracted runtime/rootfs;
- feature payload version changes.

Never rely only on in-memory callbacks to know whether runtime/distro setup is complete.

---

## 13. CI evidence model

The final Play AAB should provide auditable evidence that:

- all expected dynamic features exist;
- feature delivery mode is on demand;
- payload checksum/provenance is correct;
- feature size stays within current limits;
- Play flavor has no remote rootfs/bootstrap provider;
- no nested APK/root/install permission/external-Termux bridge remains;
- direct host executable locations follow the documented native/W^X design.

CI must distinguish expected guest ELF files inside rootfs archives from unexpected Android-host executable payloads.

---

## 14. Implementation ownership

Do not use the old QEMU-first `compliant-runtime-workers` as a second plan.

The only implementation order is:

```text
workers/01 -> 02 -> 03 -> 04 -> 05 -> 06 -> 07 -> 08 -> 09 -> 10
```

Refer to `v2_0_compliance_roadmap.md` for policy gates and each canonical worker file for acceptance criteria.