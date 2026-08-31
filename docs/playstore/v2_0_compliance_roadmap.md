# FluxLinux v2.0 — Google Play Compliance Roadmap

> **Canonical implementation plan.** This document supersedes earlier PAD-first, QEMU-first, and external-Termux-first proposals for the Play build.
>
> **Decision:** keep FluxLinux v2's embedded same-architecture PRoot backend and most of its existing terminal/X11/Pulse/install pipeline. Change the **delivery boundary and Android integration surface**, not the Linux backend.
>
> **Play delivery rule:** executable-bearing FluxLinux runtime payloads must be delivered as part of the Google Play app bundle using the base module or **Play Feature Delivery (PFD) dynamic-feature modules**. Do **not** use Play Asset Delivery for Linux rootfs/bootstrap payloads because Google documents asset packs as asset-only and not a vehicle for executable code.
>
> **Baseline:** `v2.0.0` -> `c83cb17e7a5d4713f8e0b931761061902e9dd345`.
>
> **Play package:** preserve `com.zenithblue.fluxlinux`; next Play `versionCode` must remain greater than the v1.8p Play build's `11`.
>
> **Rollback:** preserve the old Play-safe v1.8-era state at `816371bba62535fc3fc3b433fba47e5dcf9bda74` until every gate in Worker 10 passes.

---

## 1. Why the architecture changed after review

The first v2 compliance plan was too conservative and removed most of the embedded backend. A later proposal used QEMU to create an interpreter boundary. Code review plus comparison with modern Android terminal/editor apps showed that this would unnecessarily destroy performance for same-architecture ARM64 Linux userspaces.

A second proposal moved distro archives to Play Asset Delivery. That is also not the correct design: a Linux rootfs contains ELF executables, while Google documents PAD asset packs as containing assets and not executable code.

The corrected design uses **Play Feature Delivery** because dynamic-feature modules are Google Play-delivered app modules intended to deliver code and resources on demand.

### Final architecture rule

Keep:

- embedded terminal;
- same-architecture native PRoot;
- PRoot fake root (`-0` / guest uid 0);
- existing rootfs extraction/configuration flow;
- distro install state and logs;
- existing setup/customization scripts where safe;
- embedded X11 integration;
- host PulseAudio integration;
- desktop/session lifecycle;
- normal guest networking;
- user-facing package managers (`apt`, `apk`, `dnf`, `pacman`, etc.), subject to the review gate in section 12.

Change/remove for the Play flavor:

- GitHub/HTTP rootfs download by Android code;
- GitHub/HTTP host bootstrap repair/download;
- Android-level root/chroot, Magisk/KernelSU/APatch integration;
- nested/disguised APK assets;
- obsolete external-Termux bridge permissions/services/queries;
- unsafe browsable callback behavior;
- app-owned FGS for Google Play module downloads;
- any direct Android execution path that copies a host executable to writable storage and `chmod +x` it.

---

## 2. Code-proven v2 baseline

The implementation workers must work with the existing v2 structure instead of rebuilding it from scratch.

Important current paths/classes to inspect before editing:

- `app/build.gradle.kts`
  - `targetSdk = 36` / `compileSdk = 36`;
  - flavors `ivarna` and `zenithblue`;
  - Play application ID is `com.zenithblue.fluxlinux`;
  - current build logic packages/restores host bootstrap and X11 loader assets;
  - embedded Termux/X11 dependencies are currently common/global.
- `DistroInstallProfile`
  - identifies the supported distro;
  - currently contains GitHub rootfs URL/checksum/name metadata.
- `RootfsDownloader`
  - current OkHttp/resume/hash rootfs source;
  - must remain available only for non-Play if desired.
- `OnboardingInstallRunner`
  - prepares host;
  - calls `RootfsDownloader.ensurePresent(...)`;
  - then runs the existing install/configuration path;
  - currently exports `FLUX_ROOTFS_PATH`, `FLUX_ROOTFS_NAME`, `FLUX_ROOTFS_SHA256`, and `FLUX_ROOTFS_URL`.
- `HostBootstrap`
  - Play currently has a bundled bootstrap but also retains remote repair/fallback behavior.
- `TermuxHostPaths`
  - already contains the important targetSdk-36 W^X design: directly executed argv0 binaries are resolved through `applicationInfo.nativeLibraryDir` (for example host bash/PRoot loader paths) instead of blindly executing `$PREFIX/bin/*`.
- `HostScriptDeployer`
  - copies host scripts/helpers, including the current loader artifact and native support files.
- Android manifest/services
  - currently includes old external-Termux/root declarations and `InstallServerService`, `BaseInstallService`, `AppTerminalService`, `DesktopSessionService`.

### Key implementation insight

The current installer already has the seam we need:

```text
CURRENT
RootfsDownloader -> local archive -> verify/install/configure -> PRoot -> desktop

PLAY
PlayFeatureRootfsProvider -> local archive -> verify/install/configure -> PRoot -> desktop
```

Everything after the verified local archive should stay as close to v2 as practical.

---

## 3. Target Play AAB/module architecture

```text
Google Play App Bundle
|
+-- base app
|   +-- Compose/UI/business logic
|   +-- terminal/session orchestration
|   +-- X11 Android integration
|   +-- safe scripts/configuration
|   +-- Play delivery coordinator
|   +-- jniLibs/<abi>/
|       +-- directly executed host launchers/loaders
|       +-- PRoot loaders/runtime pieces that must be executable by Android
|       +-- host Pulse/X11/native bridge libraries required at app start
|
+-- :runtime_host                 [on-demand dynamic feature]
|   +-- bootstrap.tar / large immutable host-prefix payload
|   +-- provenance manifest
|
+-- :distro_alpine               [on-demand dynamic feature]
|   +-- rootfs archive
|   +-- provenance manifest
|
+-- :distro_debian               [on-demand dynamic feature]
+-- :distro_ubuntu
+-- :distro_arch
+-- :distro_fedora
+-- :distro_void
+-- :distro_opensuse
+-- :distro_chimera
+-- :distro_deepin
+-- :distro_manjaro
+-- :distro_kali
+-- :distro_parrot
+```

All currently measured v2 distro archives are well below the current per-dynamic-feature size ceiling, and the current host bootstrap is also small enough for a feature module. Worker 03 must re-measure the actual release inputs and re-check current Play limits before implementing the modules; do not rely permanently on numbers recorded in this roadmap.

### Why `:runtime_host` is separate

The host bootstrap is large and not required until the user starts installing/using Linux. Keeping it on demand reduces the initial install while still making the complete Flux-managed runtime originate from Google Play.

Small directly executed host launchers should remain in base `jniLibs` where the current API-36/W^X design expects them.

---

## 4. Flavor and source-set boundary

Use build-time separation. Do not rely on runtime booleans or R8 to hide policy-sensitive code.

Recommended shape:

```text
app/src/main/          common safe backend/UI
app/src/ivarna/        non-Play remote/root/chroot integrations
app/src/zenithblue/    Play delivery + Play-safe integrations
```

The Play artifact must be incapable of selecting the HTTP rootfs/bootstrap providers.

Introduce narrow provider boundaries, for example:

```kotlin
interface RootfsPayloadProvider {
    suspend fun ensureAvailable(profile: DistroInstallProfile): PayloadResult
    fun observe(profile: DistroInstallProfile): Flow<PayloadState>
}

interface HostRuntimePayloadProvider {
    suspend fun ensureAvailable(): PayloadResult
    fun observe(): Flow<PayloadState>
}

data class PayloadResult(
    val file: File,
    val source: PayloadSource,
)

enum class PayloadSource {
    PLAY_FEATURE,
    LOCAL_BUNDLED,
    REMOTE_NON_PLAY,
}
```

Mappings:

```text
zenithblue -> PlayFeatureHostRuntimeProvider + PlayFeatureRootfsProvider
ivarna    -> existing bundled/HTTP behavior if desired
```

Keep distro identity/setup metadata common. Split delivery metadata by flavor so Play DEX/resources do not retain executable rootfs fallback URLs.

---

## 5. Play Feature Delivery implementation model

Use the current official Play Core Feature Delivery APIs compatible with the repository's AGP/Gradle versions. Worker 03 must verify the exact current dependency/API instead of copying an old version from a blog post.

Primary concept:

```text
user taps distro
   |
   v
ensure :runtime_host installed
   |
   v
ensure :distro_<id> installed
   |
   v
materialize archive into app-private staging
   |
   v
verify SHA-256 + expected metadata
   |
   v
existing extraction/configuration path
   |
   v
installed marker
```

Use `SplitInstallManager`/current official equivalent for on-demand module requests and state updates.

### Important filesystem rule

Do not persist an internal Play split/module filesystem path as durable application state. After the feature is installed, access its packaged asset/resource through supported Android split/module APIs and copy/stream it into an app-private staging file if the existing installer requires a normal `File`.

During materialization:

- calculate SHA-256 while copying;
- verify expected bytes/checksum before extraction;
- use a temporary name;
- atomically promote the verified archive;
- never mark the distro installed just because the module installation completed.

### Recommended installation states

```text
IDLE
 -> REQUESTING_RUNTIME_FEATURE
 -> DOWNLOADING_RUNTIME_FEATURE
 -> REQUESTING_DISTRO_FEATURE
 -> DOWNLOADING_DISTRO_FEATURE
 -> MATERIALIZING_PAYLOAD
 -> VERIFYING
 -> EXTRACTING
 -> CONFIGURING
 -> INSTALLED
```

Error states must cover cancellation, Play unavailable/outdated, storage failure, module not found, checksum mismatch, interrupted copy/extraction, app update during install, and stale/partial install state.

---

## 6. Rootfs delivery and provenance

For the Play flavor:

- no `RootfsDownloader` network path;
- no `ROOTFS_RELEASE_BASE` use;
- no GitHub `releases/download/rootfs` fallback;
- no `/sdcard/Download` rootfs fallback/import as an executable runtime source;
- remove `FLUX_ROOTFS_URL` from the Play install environment;
- keep `FLUX_ROOTFS_PATH`, file name, checksum, and payload version metadata.

Each distro dynamic feature must contain a provenance manifest containing at least:

- distro ID and release/version;
- architecture;
- upstream source used by release tooling/maintainers;
- upstream checksum where available;
- final Flux archive SHA-256;
- compressed and uncompressed sizes;
- payload schema/version;
- build script + source commit;
- creation date;
- Flux customizations included in the image.

Maintainer/CI tooling may download upstream distro material while preparing a release. The important runtime boundary is that the released Play app receives the Flux-managed runtime/rootfs payload from Google Play, not an app-controlled executable download endpoint.

---

## 7. Host bootstrap and API-36 W^X

Do not replace a working v2 mechanism with a blanket "everything in jniLibs" conversion.

Classify the bootstrap contents:

### A. Directly executed Android-host ELF/native launcher

Keep/package through normal native library locations and use `applicationInfo.nativeLibraryDir`. Preserve the existing `TermuxHostPaths` approach.

### B. Large host-prefix payload used behind the launcher/termux-exec mechanism

Deliver through `:runtime_host`, then extract to the existing app-private prefix layout if the current backend requires it.

### C. Interpreted scripts/config/data

May be copied into the app-private prefix from base or `:runtime_host`.

### D. Obsolete/unsafe executable copies

Remove.

Play must not retain a remote bootstrap repair URL. Repair should re-materialize from the installed/requested `:runtime_host` module.

Worker 04 must audit every direct `ProcessBuilder`, native exec, shell argv0, `chmod +x`, and copied ELF path. Direct execution from writable app data must not be reintroduced.

---

## 8. Nested X11 loader APK

The current `loader.apk` / identical or disguised `loader.bin` pattern is a scanner and policy risk.

Required sequence:

1. identify exactly what the loader provides and all `HostScriptDeployer`/runtime references;
2. integrate that functionality as a normal Android module/dependency or safe in-process component;
3. prove X11 startup works with the replacement;
4. only then remove `loader.apk`, `loader.bin`, reconstruction build tasks, and deploy/copy logic;
5. add a CI magic-byte scan so nested/disguised APKs cannot return.

Do not delete the loader first and leave X11 broken.

---

## 9. Android root/chroot versus PRoot fake root

The Play flavor should be PRoot-only.

Compile out/remove from Play:

- `ACCESS_SUPERUSER`;
- `RootShell` integration;
- Magisk;
- KernelSU;
- APatch;
- real `chroot`;
- root-only mounts/helpers;
- root grant prompts/settings/cards/onboarding.

Preserve:

```text
PRoot -0 / guest uid 0
```

PRoot fake root does not turn the FluxLinux Android process into Android uid 0. Device tests must demonstrate that a guest can report uid 0 while the host app still runs under its normal sandbox UID.

---

## 10. Remove obsolete external-Termux surface

The new Play architecture is embedded. Therefore audit and remove when no embedded component truly requires them:

- `com.termux.permission.RUN_COMMAND`;
- Termux/Termux:X11 package queries;
- external-Termux prerequisite UI;
- deprecated `InstallServerService` external bridge;
- old external callback/install-server flows.

Also ensure the Play build does not regain:

- `REQUEST_INSTALL_PACKAGES`;
- `PackageInstaller`/unknown-sources workflow;
- APK/APKS/XAPK installer CTAs;
- direct links whose purpose is to install executable Android packages outside Play.

---

## 11. Callback/deep-link security

The legacy `fluxlinux://callback` surface must not be allowed to mutate install state from arbitrary browsable URIs.

Preferred Play result: remove the BROWSABLE callback entirely if it existed only for the external-Termux integration.

If a callback is truly still needed:

- generate an unpredictable app-created operation nonce;
- bind it to one current active operation;
- accept only fixed enums/fields;
- make it single-use and expire it;
- reject stale/replayed callbacks;
- reject command/script/path/URL parameters unless an exact allowlisted value is required;
- never pass callback input to a shell;
- never keep legacy fallbacks that simply mark a distro/script complete.

Add negative ADB tests for arbitrary command strings, metacharacters, paths, URLs, stale IDs, replay, missing nonce, and malformed values.

---

## 12. Guest package managers — explicit policy review gate

Acode and similar apps provide useful practical precedent for a bundled PRoot userspace with package managers, but that precedent is not a written Google exemption.

Facts that reviewer notes and documentation must state correctly:

- PRoot is not a VM and not a CPU interpreter;
- it is userspace filesystem/syscall translation;
- it shares the Android kernel;
- guest processes remain under the FluxLinux Android application UID/security boundary;
- guest uid 0 is fake PRoot root, not Android root;
- a user may explicitly run distro package managers that contact normal Linux repositories.

Guardrails:

- the Android/Kotlin app must not silently use Linux repositories as its own executable self-update/repair mechanism;
- no downloaded guest package may be turned into an Android app plugin/native library or privileged Android integration;
- no arbitrary guest command bridge may be exposed to other Android apps;
- keep Android sandbox/file bindings narrow and documented;
- test package-manager behavior in Internal/Closed Play tracks before Production.

If Play review explicitly rejects the PRoot package-manager model, stop and reassess. Do not hide or obfuscate the behavior.

---

## 13. PRoot filesystem bridge

Audit the actual bind list required by the existing guest scripts.

Prefer only what is functionally required:

- guest rootfs;
- minimal `/proc` and required `/dev` views;
- temp/shared-memory paths;
- app-controlled guest home;
- X11/Pulse sockets;
- explicit shared/user file access.

Avoid gratuitous broad bindings of unrelated app-private directories or Android system/vendor data. If user storage access is required, prefer a controlled app-owned/shared-folder model or SAF bridge where practical without breaking Linux workflows.

Every retained broad bind must have a documented reason and a regression test.

---

## 14. Foreground-service redesign

Feature downloads are owned by Google Play Feature Delivery, not by a Flux HTTP download FGS.

### `BaseInstallService`

Do not start it merely because the user requested a dynamic feature. Start it only after required modules are installed, if verify/materialize/extract/configure work genuinely needs a foreground service. Stop it on success, failure, cancellation, and process/session teardown.

### `InstallServerService`

Expected Play result: remove it with the external-Termux bridge.

### `AppTerminalService` / `DesktopSessionService`

Audit independently. Retain an FGS only when necessary for user-visible long-running terminal/desktop work. For each retained service record:

- exact user action that starts it;
- exact stop condition;
- notification title/body/actions;
- visible stop action;
- FGS type/permission;
- why no narrower supported type/lifecycle is sufficient;
- Play Console declaration wording.

Prefer a specific supported type when valid. If `specialUse` remains necessary, document it precisely and test API 34/35/36 behavior.

---

## 15. Privacy, Data Safety, metadata and reviewer notes

Worker 08 must rewrite these from the **final AAB**, not from this roadmap or old v1.8 answers.

Play-facing statements should accurately describe:

- embedded PRoot Linux environment;
- selected host runtime/distro modules delivered by Google Play Feature Delivery;
- extraction into app-private storage;
- no Android-level root/chroot in the Play flavor;
- user-run guest programs/package managers can make network requests;
- logs/diagnostics and retention/deletion;
- any analytics/crash/ads/account/identifier behavior actually present;
- required foreground services.

Remove stale store/help/screenshot claims about external Termux, GitHub rootfs downloads, Android root/chroot, or APK installation.

Do not claim "Google approved PRoot" or "Acode proves compliance." State implementation facts only.

---

## 16. CI policy gate

Create one release gate, e.g. `scripts/verify_play_policy.sh`, that validates **the zenithblue Play variant and its final AAB**, not every non-Play source file.

Required source/config checks:

- Play provider wiring cannot instantiate `RootfsDownloader`/remote bootstrap provider;
- no Play `FLUX_ROOTFS_URL` fallback;
- no Play root/chroot integration;
- no obsolete external-Termux bridge;
- every supported distro maps to a PFD feature module;
- `:runtime_host` is defined and on demand;
- module payload/checksum/provenance registry is complete.

Required final artifact checks:

- package ID/version/target SDK correct;
- required dynamic feature modules exist and are on demand;
- feature compressed size stays under the current Play limit with safety margin;
- expected rootfs/bootstrap archives exist only in their intended modules;
- no nested/disguised APK;
- no `REQUEST_INSTALL_PACKAGES` or `ACCESS_SUPERUSER`;
- no unexpected host executable under generic base assets;
- directly executed host native code is in normal native-lib locations;
- no old executable GitHub fallback strings in Play DEX/resources.

### ELF scanning rule

Do **not** fail merely because a distro rootfs archive contains Linux ELF files. Those are expected guest payloads. Fail unexpected Android-host ELF/native executable materialization paths outside the documented native/PFD design.

Add negative fixtures proving the gate catches regressions.

---

## 17. Worker execution order and hard gates

Execute exactly in this order:

1. `workers/01_branch_baseline.md`
2. `workers/02_play_flavor_boundary.md`
3. `workers/03_remove_remote_executable_delivery.md`
4. `workers/04_remove_nested_and_writable_executables.md`
5. `workers/05_remove_root_chroot_from_play.md`
6. `workers/06_links_permissions_and_callbacks.md`
7. `workers/07_foreground_services.md`
8. `workers/08_privacy_and_store_metadata.md`
9. `workers/09_ci_policy_gate.md`
10. `workers/10_release_validation.md`

Do not start worker N+1 until worker N is `PASS`, except for a narrowly documented compile/test fix required to unblock it.

The old `compliant-runtime-workers/` QEMU-first sequence is deprecated and must not be executed.

---

## 18. Final release gates

Production promotion is blocked until all of these pass:

### Technical

- clean `zenithblue` release build;
- tests/lint and policy gate green;
- API 33/34/35/36 device coverage;
- current supported ABI coverage;
- all 12 distro modules tested;
- terminal/X11/Pulse smoke tests;
- PRoot fake root verified;
- package manager test recorded;
- no direct Android root/chroot behavior;
- no remote Flux-managed executable/rootfs fallback.

### Delivery

- clean install from actual Google Play Internal track/App Sharing;
- base app starts without preinstalled optional features;
- first distro request installs `:runtime_host` and distro feature through Play;
- cancellation/retry/offline/storage/error paths work;
- app/process death during materialization/extraction recovers safely;
- app update + installed distro behavior works;
- feature update/version mismatch behavior is defined.

### Upgrade

Upgrade from v1.8p `versionCode 11` and confirm:

- package continuity;
- user data preserved as intended;
- old external-Termux state does not deadlock onboarding;
- new embedded host can be installed through Play;
- old obsolete permissions/flows are gone.

### Console/policy

- privacy policy matches exact release;
- Data Safety re-audited;
- FGS declarations match manifest/runtime;
- store text/screenshots match features;
- reviewer notes explain PFD + PRoot accurately;
- Internal/Closed testing has not produced a policy objection to the package-manager model.

Record the final AAB SHA-256 and retain release evidence.

---

## 19. Non-negotiable rules for agents

- Do not use PAD for executable-bearing rootfs/bootstrap payloads.
- Do not restore QEMU as the default same-architecture backend.
- Do not revert the Play product to an external-Termux dependency.
- Do not hide prohibited behavior behind a build flag, obfuscation, renamed extension, or scanner trick.
- Do not add an HTTP fallback "temporarily" to the Play flavor.
- Do not remove PRoot/X11/Pulse functionality unless the assigned worker proves a specific Play blocker that cannot be fixed safely.
- Do not break `ivarna` unnecessarily; keep non-Play functionality behind source/dependency boundaries.
- Do not call PRoot a VM/interpreter.
- Do not treat another Play app as a guarantee of approval.
- Live Google Play policy and current Android documentation override stale assumptions in repository docs.

---

## 20. Reference links

- FluxLinux policy guide: `https://github.com/abhay-byte/abhay-kb/blob/main/Google_Play_Store_Policy_Compliance_Guide.md`
- Play Feature Delivery: `https://developer.android.com/guide/playcore/feature-delivery`
- On-demand feature delivery: `https://developer.android.com/guide/playcore/feature-delivery/on-demand`
- Play Asset Delivery: `https://developer.android.com/guide/playcore/asset-delivery`
- Android App Bundle size/configuration guidance: `https://developer.android.com/guide/app-bundle`

Re-check all current limits/policy wording before final release.