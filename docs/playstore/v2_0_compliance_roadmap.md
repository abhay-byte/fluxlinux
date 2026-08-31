# FluxLinux v2.0 — Google Play Compliance Roadmap

> **Decision:** keep the v2 embedded Linux architecture and native PRoot backend. For the Google Play build, change the **distribution boundary**, not the Linux execution backend: all FluxLinux-managed executable/runtime payloads must originate from Google Play, using the base AAB/APK for small host components and Play Asset Delivery (PAD) for large distro/rootfs payloads.
>
> **Baseline:** `v2.0.0` (`c83cb17e7a5d4713f8e0b931761061902e9dd345`).
>
> **Play package:** preserve `com.zenithblue.fluxlinux` and version-code continuity.
>
> **Rollback:** preserve the v1.8-era Play branch state until this plan passes every build, artifact, device, policy, and Play-track gate.
>
> **Policy source of truth:** `abhay-byte/abhay-kb/Google_Play_Store_Policy_Compliance_Guide.md` plus current Google Play policy. Live Google policy always wins if the guide becomes stale.

---

## 1. Executive decision

The previous roadmap assumed the v2 embedded PRoot backend itself should be removed from the Play build. That is no longer the recommended architecture.

Acode provides a highly relevant current implementation precedent: it targets modern Android, packages PRoot/native host components as Play-delivered native libraries, packages an Alpine rootfs with the app, extracts it into app-private storage, and starts the Linux userspace through PRoot. FluxLinux already has many of the same technical pieces.

The primary Play blocker in FluxLinux v2 is therefore **where executable/runtime content comes from**, plus a few Android integration details:

1. v2 downloads rootfs archives from GitHub.
2. v2 retains remote bootstrap/repair paths.
3. some host ELF/`.so` content is copied from assets into writable storage and executed.
4. `loader.apk`/`loader.bin` creates a nested-APK/scanner risk.
5. rooted chroot support requests Android-level root capabilities that are unnecessary for the PRoot Play experience.

The Play redesign should preserve the embedded host, terminal, PRoot, X11, PulseAudio, distro setup scripts, installation state machine, session management, and most UI/business logic.

### New rule

> **For the Google Play flavor, FluxLinux itself must never fetch a host executable, native library, bootstrap executable, rootfs archive, nested APK, or other executable runtime payload from GitHub/HTTP. Google Play must deliver those payloads.**

This is consistent with current Google Play policy language prohibiting executable-code downloads from sources other than Google Play. Play Asset Delivery is a Google Play delivery mechanism.

---

## 2. Answer to the architecture question: is the backend almost the same?

**Yes.** The backend is mostly preserved. The main refactor is the **payload provider**.

### Keep essentially unchanged

- embedded terminal process model
- native same-architecture PRoot execution
- distro extraction into app-private storage
- distro configuration scripts
- distro installed-state tracking
- filesystem layout where practical
- host/guest environment setup
- desktop-session lifecycle
- embedded X11 integration
- host PulseAudio integration
- start/stop/restart flows
- logs and diagnostics
- UI distro cards and install/uninstall flows
- guest package managers (`apt`, `apk`, `dnf`, `pacman`, etc.) as user-facing Linux functionality, subject to the policy guardrails in §12
- SHA/integrity verification where useful
- PRoot fake-root behavior

### Change

| v2 implementation | Play implementation |
|---|---|
| `RootfsDownloader` downloads from GitHub | `PlayAssetRootfsProvider` requests an on-demand asset pack from Google Play |
| rootfs profile contains HTTP URL | profile contains `assetPackName`, archive name, SHA256, size/version metadata |
| bootstrap repair can fetch remote executable data | no remote executable repair; restore only from Play-delivered base/PAD content |
| host ELF/`.so` copied to writable storage and executed | host ELFs live in `jniLibs` / `nativeLibraryDir` and are referenced/symlinked from there |
| `loader.apk` nested asset | integrate loader normally into app/module, or remove if no longer required |
| rooted chroot/Magisk/KSU/APatch | compile out of Play flavor |
| direct rootfs URL/resume logic | Play Asset Delivery state/progress/retry logic |

Conceptually:

```text
CURRENT v2
Flux UI -> RootfsDownloader -> GitHub -> archive -> verify -> extract -> configure -> PRoot -> desktop

PLAY v2
Flux UI -> PlayAssetRootfsProvider -> Google Play PAD -> archive -> verify -> extract -> configure -> PRoot -> desktop
```

Everything to the right of `archive` should remain as close to the existing implementation as possible.

---

## 3. Target Play architecture

```text
Google Play App Bundle
|
+-- base app (small)
|   +-- FluxLinux UI/business logic
|   +-- terminal/session management
|   +-- X11 Android integration
|   +-- non-executable scripts/configuration
|   +-- jniLibs/<abi>/
|       +-- libproot*.so
|       +-- host helper ELF libraries packaged as .so where required
|       +-- host X11/Pulse/native components that must execute on Android
|
+-- on-demand asset packs
    +-- distro_debian_arm64/rootfs.tar.*
    +-- distro_ubuntu_arm64/rootfs.tar.*
    +-- distro_alpine_arm64/rootfs.tar.*
    +-- distro_arch_arm64/rootfs.tar.*
    +-- distro_fedora_arm64/rootfs.tar.*
    +-- distro_void_arm64/rootfs.tar.*
    +-- distro_opensuse_arm64/rootfs.tar.*
    +-- distro_chimera_arm64/rootfs.tar.*
    +-- distro_deepin_arm64/rootfs.tar.*
    +-- distro_manjaro_arm64/rootfs.tar.*
    +-- distro_kali_arm64/rootfs.tar.*
    +-- distro_parrot_arm64/rootfs.tar.*
```

The initial app stays small. A distro is downloaded only when the user chooses it.

Current Google Play limits are compatible with this model: individual asset packs may be up to 1.5 GB compressed, on-demand/fast-follow asset packs have a large cumulative allowance, and an app bundle can contain many asset packs. Verify current limits again before every major release.

---

## 4. Delivery classification: what goes where

### 4.1 Base app / normal AAB modules

Put small, always-required content here:

- Kotlin/Java application code
- Compose/UI resources
- session/service code
- distro metadata
- shell scripts and text configuration that ship with the release
- small immutable templates
- integrated X11 Android code
- small non-executable assets

### 4.2 `jniLibs` / native library directory

Put Android-host native code that is actually executed by Android/Linux host processes here:

- PRoot loader/runtime
- PRoot host binary packaged in an Android-compatible native-library form
- AXS/host shell helper if retained
- native X11 helper libraries
- PulseAudio host native libraries where applicable
- required host-side graphics/native bridge libraries

Do **not** extract these from generic assets into `$filesDir` and then `chmod +x` them.

Use `applicationInfo.nativeLibraryDir` as the canonical executable location. If legacy scripts require stable paths such as `$PREFIX/axs`, create a symlink to the Play-installed native-library path rather than copying the executable bytes.

### 4.3 Play Asset Delivery — on-demand

Put large immutable guest payloads here:

- distro rootfs archives
- optional large guest support bundles that are required to create a distro install
- large immutable data tightly coupled to a specific distro/version

Default to **one on-demand asset pack per distro/ABI**. This makes installs independent, keeps the initial download small, and allows clean per-distro progress/error handling.

### 4.4 Optional install-time asset pack

Use only if the base app becomes too large because of a non-code shared data payload that every user needs. Do not move Android host executables into an asset pack just to avoid `jniLibs`; host W^X/execution rules still matter.

---

## 5. New distro source abstraction

Do not rewrite the distro installer around Play APIs. Introduce a narrow source/provider boundary.

Recommended interfaces:

```kotlin
interface RootfsPayloadProvider {
    suspend fun ensureAvailable(profile: DistroInstallProfile): PayloadResult
    fun observe(profile: DistroInstallProfile): Flow<PayloadState>
    suspend fun removeCachedPayload(profile: DistroInstallProfile)
}

data class PayloadResult(
    val archive: File,
    val source: PayloadSource,
)

enum class PayloadSource {
    PLAY_ASSET_DELIVERY,
    LOCAL_BUNDLED,
    REMOTE_NON_PLAY,
}
```

Flavor mapping:

- `zenithblue` -> `PlayAssetRootfsProvider`
- `ivarna` -> existing HTTP/GitHub provider if desired

The common installer then receives a local archive and continues the existing extraction/configuration path.

### Distro profile changes

Replace Play-facing URLs with pack metadata:

```kotlin
data class DistroPayload(
    val assetPackName: String,
    val archiveName: String,
    val sha256: String,
    val minBytes: Long,
    val payloadVersion: Int,
)
```

The non-Play flavor may separately retain URL metadata.

Do not leave `releases/download/rootfs` strings or executable repair URLs in the Play artifact.

---

## 6. Play Asset Delivery install state machine

Reuse the existing install UI and map Play states into Flux states.

Recommended state machine:

```text
NOT_INSTALLED
   |
   v
REQUESTING_PLAY_PACK
   |
   +--> WAITING_FOR_WIFI / USER_CONFIRMATION
   |
   +--> DOWNLOADING(progress)
   |
   +--> PLAY_PACK_AVAILABLE
   |
   v
VERIFYING_ARCHIVE
   |
   v
EXTRACTING_ROOTFS
   |
   v
CONFIGURING_DISTRO
   |
   v
INSTALLED
```

Failure states must remain recoverable:

- network unavailable
- Play Store unavailable/outdated
- user cancels download
- insufficient storage
- pack not found
- archive missing inside pack
- checksum mismatch
- interrupted extraction
- app update changes payload version
- partially installed distro

Rules:

1. never mark a distro installed merely because the asset pack downloaded;
2. verify/extract atomically using staging paths;
3. write the installed marker only after configuration completes;
4. clean stale staging directories after failure;
5. query Play for the current pack location instead of persisting a pack filesystem path forever;
6. preserve the existing installation logs for diagnostics;
7. optionally remove the downloaded asset pack after a verified extraction to reduce duplicate storage, while keeping the extracted distro; a repair/reinstall can request the pack again from Play.

---

## 7. Asset-pack module layout

Example:

```text
settings.gradle.kts
app/
assetpacks/
  distro_debian_arm64/
    build.gradle.kts
    src/main/assets/rootfs/debian.tar.zst
  distro_ubuntu_arm64/
    build.gradle.kts
    src/main/assets/rootfs/ubuntu.tar.zst
  ...
```

Each pack:

```kotlin
plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName = "distro_debian_arm64"
    dynamicDelivery {
        deliveryType = "on-demand"
    }
}
```

Register packs in the app module and `settings.gradle.kts` according to the Android Gradle Plugin/Play Asset Delivery version used by the repository.

Do not hard-code a dependency version from this document; worker 03 must select the currently supported Play Asset Delivery dependency compatible with the project's AGP and lock it in Gradle.

---

## 8. Rootfs build and provenance pipeline

PAD solves delivery, but we still need reproducible payload provenance.

For every distro pack maintain a machine-readable manifest containing:

- distro ID
- distro release/version
- architecture
- upstream source URL used by CI/build maintainers
- upstream checksum when available
- Flux-generated archive checksum
- uncompressed size
- compressed size
- payload schema/version
- build date
- build script/commit
- included Flux customization scripts/packages

Recommended generated file:

```text
assetpacks/distro_debian_arm64/src/main/assets/rootfs/manifest.json
```

CI should fail if the manifest checksum does not match the archive.

Important distinction: maintainers/CI may obtain upstream Linux files while preparing a new Play release. The **released Play app** must receive its Flux-managed executable payload from Google Play rather than performing that GitHub download at runtime.

---

## 9. Host bootstrap refactor

The current `bootstrap.tar` concept can remain only after classifying its contents.

Split it into:

### Android host executable content

Move to `jniLibs` / normal native modules.

### Non-executable scripts/config/data

May remain in base app assets and be copied into the app prefix.

### Large guest/runtime data

Move to an install-time or on-demand Play asset pack if needed.

Remove from the Play flavor:

- executable bootstrap HTTP repair path
- GitHub fallback URL
- code that downloads a replacement host runtime
- recovery UI that fetches executable bootstrap content outside Play

A Play repair operation must reconstruct from currently installed Play content or request the corresponding Play asset pack.

---

## 10. Remove nested APK and writable host executables

The Play AAB must not contain a nested `loader.apk` or APK bytes disguised as `loader.bin`.

Required direction:

- integrate the X11 loader/component through the normal Android module/dependency build, **or**
- replace its behavior with an in-process component if feasible.

Audit every ELF/native file currently under generic assets/scripts.

Host-side executable rule:

> If Android directly executes it, package it as Play-installed native code and execute/reference it from the native library directory. Do not copy executable bytes into app-writable storage and chmod them executable.

Guest rootfs binaries are different: they are delivered by Play inside the rootfs asset pack and subsequently executed under the PRoot guest path.

---

## 11. PRoot sandbox design

Keep PRoot as the Play backend. Do **not** replace it with QEMU for same-architecture devices.

PRoot is a userspace filesystem/syscall translation environment. It shares the Android kernel and runs under the FluxLinux Android UID; fake guest UID 0 does not grant Android root.

For the Play flavor, minimize host bind mounts.

Prefer:

- guest rootfs
- minimal `/proc`
- minimal required `/dev`
- required temp/shared-memory paths
- app-controlled home
- explicit user-selected shared folders via SAF where practical
- X11/Pulse sockets required for desktop use

Avoid broad exposure unless demonstrably required:

- arbitrary `/data`
- whole Android `/system`/`vendor`
- whole `/storage`
- native library directory as a general writable/guest-visible workspace
- sensitive app-private directories unrelated to the Linux environment

Every bind should have a documented reason and test.

---

## 12. Guest package managers: strict boundary

FluxLinux's core Play payload must be fully Play-delivered, but a Linux distro is useful only if the user can operate its normal package manager.

For the initial Play release:

1. bundle enough packages in each Play-delivered rootfs for a complete functional desktop/CLI baseline;
2. do not have Android/Kotlin app code silently download guest executable packages from GitHub as part of startup/repair;
3. user-initiated `apt`, `apk`, `dnf`, `pacman`, etc. runs inside the PRoot guest should remain a clearly user-controlled Linux action;
4. do not expose arbitrary guest scripts as Android APIs, accessibility automation, package-install APIs, root APIs, or device-management capabilities;
5. do not allow downloaded guest code to escape the FluxLinux app UID/sandbox;
6. document this behavior accurately in privacy/store/reviewer notes;
7. treat package-manager behavior as a policy-review item during Internal/Closed testing because Google policy's VM/interpreter exception does not explicitly name PRoot.

Acode is useful practical precedent, but it is not a written exemption. Do not describe it as guaranteed approval.

---

## 13. Remove Android-root/chroot from Play

The Play flavor should provide PRoot fake-root only.

Compile out:

- `ACCESS_SUPERUSER`
- Magisk integration
- KernelSU integration
- APatch integration
- root shell manager
- real `chroot`
- root-only mount management
- root-only UI/cards/settings

Keep full chroot/root capabilities in the non-Play flavor if desired.

This makes the Play security story much simpler:

```text
Linux guest UID 0 (fake PRoot root)
        !=
Android UID 0 / device root
```

---

## 14. Foreground services and long-running sessions

Because the embedded terminal/desktop remains in Play, do not assume all v2 services can be deleted.

Review each retained service against current Android 14–16/Play foreground-service requirements:

- `InstallServerService`
- `BaseInstallService`
- `AppTerminalService`
- `DesktopSessionService`

PAD downloads themselves should use the Play delivery mechanism rather than a custom HTTP foreground downloader.

A retained foreground service must be:

- directly connected to visible core functionality
- user initiated where required
- represented by an accurate ongoing notification
- stoppable
- stopped when the terminal/session ends
- declared with the correct FGS type and type-specific permission
- declared consistently in Play Console

Remove `specialUse` if a more specific valid type or no FGS is appropriate; otherwise document the exact use-case text and test Android 14–16 behavior.

---

## 15. External links, APK installation, callbacks

Never regress the v1.8 Play fixes.

Play flavor must not:

- request `REQUEST_INSTALL_PACKAGES`
- download another app APK
- call `PackageInstaller` for external apps
- direct users to raw APK/ZIP/XAPK/APKS installation payloads
- tell users to disable Play Protect/security
- self-update outside Play

Because the new Play architecture is embedded, the Play flavor should no longer require external Termux/Termux:X11 as its normal runtime. Remove obsolete prerequisite UI/queries/permission usage where no longer needed.

Harden `fluxlinux://callback` and any other browsable intents:

- only accept expected fields
- bind callbacks to active app-created requests
- reject arbitrary shell commands/paths/URLs
- reject stale/replayed callbacks
- add negative tests

---

## 16. Privacy, Data Safety, store metadata

Rewrite Play-facing text for the new architecture.

Accurately state:

- FluxLinux contains an embedded Linux userspace runtime
- selected distro packages are downloaded through Google Play's delivery infrastructure
- distro files are extracted into app-private storage
- network access inside a Linux distro may occur when the user explicitly runs Linux tools/package managers
- whether analytics/crash reporting/telemetry exists
- local diagnostic/log retention
- foreground session behavior
- exact permissions
- no Android root/chroot support in the Play flavor

Update Fastlane/Play description so it does not claim GitHub rootfs downloads or external Termux prerequisites.

Re-run Data Safety after final dependency inspection.

---

## 17. CI policy gate

Add/expand `scripts/verify_play_policy.sh` and CI.

### Source-level deny checks for Play flavor

Reject:

- `REQUEST_INSTALL_PACKAGES`
- `ACCESS_SUPERUSER`
- `PackageInstaller`
- unknown-source install APIs
- Play-flavor `releases/download/rootfs`
- Play-flavor HTTP bootstrap repair URLs
- direct APK download/install flows
- nested `loader.apk`
- nested APK magic bytes in generic assets

### Artifact checks

Build:

```bash
./gradlew test
./gradlew lintZenithblueRelease
./gradlew bundleZenithblueRelease
```

Inspect the AAB and generated device APKs:

- package ID is `com.zenithblue.fluxlinux`
- target SDK is current requirement
- asset packs are present and `on-demand`
- every expected distro archive is in its Play asset pack
- no root/chroot permission
- no nested APK
- host native libraries are under native-lib delivery locations
- no unexpected host ELF under generic writable-copy assets
- no GitHub rootfs/bootstrap download strings in Play dex/resources

### PAD local testing

Use official bundletool local testing:

```bash
bundletool build-apks \
  --bundle app-zenithblue-release.aab \
  --output fluxlinux-play.apks \
  --local-testing

bundletool install-apks --apks fluxlinux-play.apks
```

Then test on-demand pack request/download/extraction locally. Internal App Sharing/Play Internal track must still test real Google Play network behavior, cancellation, cellular confirmation, updates, and pack availability.

---

## 18. Device test matrix

Minimum:

- Android 13 / API 33
- Android 14 / API 34
- Android 15 / API 35
- Android 16 / API 36

For each supported ABI/device class test:

1. fresh install with no distro
2. app initial download remains small
3. request one distro
4. progress UI
5. cancel and retry
6. lose network and retry
7. insufficient-storage path
8. checksum/integrity failure fixture
9. extract/configure
10. launch terminal
11. execute native guest commands
12. install/start desktop
13. X11 rendering
14. PulseAudio
15. stop/restart
16. app process death during PAD download
17. app process death during extraction
18. reboot
19. uninstall/reinstall distro
20. app update with existing distro
21. payload-version update
22. optional package-manager operation inside guest
23. verify no Android root prompt
24. verify no external APK install request

Also upgrade from the existing Play `versionCode 11` build and validate state migration.

---

## 19. Storage and update behavior

Use separate concepts:

- **Play pack cache/source** — immutable archive delivered by Google Play
- **staging extraction** — temporary app-private directory
- **installed distro** — mutable app-private Linux filesystem

Never run the mutable distro directly out of an asset-pack path.

Recommended install transaction:

```text
PAD archive
 -> verify
 -> extract to <distro>.staging
 -> configure staging
 -> fsync/validate critical files
 -> atomically promote to installed path
 -> write installed metadata
 -> optionally request PAD cache removal
```

Persist installed metadata:

- distro ID
- payload version
- archive SHA256
- install timestamp
- app version installed by
- configuration schema version

On app update, migrate only when required. Do not automatically erase user Linux files merely because a newer asset pack exists.

---

## 20. Branch strategy

1. preserve the old Play rollback branch/ref;
2. build this work on `playstore-v2-compliance` based on v2;
3. workers commit independently and keep both flavors buildable;
4. do not move production `playstore` until all gates pass;
5. use Internal App Sharing/Internal testing for real PAD behavior before production.

The non-Play flavor can keep GitHub rootfs downloading/chroot features, but source-set boundaries must prevent those paths from leaking into the Play artifact.

---

## 21. Worker execution order

Execute strictly in order:

1. `01_branch_baseline.md` — preserve rollback and establish v2 Play integration baseline.
2. `02_play_flavor_boundary.md` — preserve embedded backend but create clean Play/non-Play source/provider boundaries.
3. `03_remove_remote_executable_delivery.md` — implement PAD rootfs provider and distro asset packs; eliminate Play runtime rootfs/bootstrap HTTP delivery.
4. `04_remove_nested_and_writable_executables.md` — move Android host ELFs to `jniLibs/nativeLibraryDir`, remove nested loader APK, preserve backend paths through symlinks/adapters.
5. `05_remove_root_chroot_from_play.md` — remove Android-root/chroot while preserving PRoot fake root.
6. `06_links_permissions_and_callbacks.md` — clean obsolete Termux/install permissions/links and harden callbacks/sandbox bridges.
7. `07_foreground_services.md` — adapt install/session services to PAD and current FGS rules.
8. `08_privacy_and_store_metadata.md` — document embedded PRoot + Play-delivered distros accurately.
9. `09_ci_policy_gate.md` — enforce PAD-only Flux runtime delivery and artifact rules in CI.
10. `10_release_validation.md` — bundletool PAD testing, API 33–36 matrix, upgrade test, Internal Play track, final policy reconciliation.

Workers must not opportunistically redesign unrelated UI/backend code. The goal is to retain v2 behavior while changing its distribution/security boundary.

---

## 22. Definition of done

The Play v2 work is complete only when all are true:

- [ ] embedded terminal works without external Termux
- [ ] native PRoot backend remains functional
- [ ] all Flux-managed distro rootfs payloads used by Play build are delivered by Google Play
- [ ] Play build has no GitHub/HTTP rootfs downloader
- [ ] Play build has no remote executable bootstrap repair
- [ ] Android host executables are Play-installed native code, not writable copied ELFs
- [ ] no nested APK or APK-disguised asset exists
- [ ] PRoot fake root works
- [ ] Android-level root/chroot is absent from Play
- [ ] distro install/extract/configure/start flow works for all supported distros
- [ ] X11 and PulseAudio work
- [ ] PAD cancel/retry/update/storage flows work
- [ ] no `REQUEST_INSTALL_PACKAGES`
- [ ] no `ACCESS_SUPERUSER`
- [ ] browsable callback inputs are constrained
- [ ] FGS declarations match actual behavior
- [ ] privacy/Data Safety/store text match the final AAB
- [ ] policy CI gate passes
- [ ] bundletool local PAD tests pass
- [ ] API 33–36 device matrix passes
- [ ] upgrade from v1.8 Play succeeds
- [ ] Internal App Sharing/Internal Play test validates real Play delivery
- [ ] final AAB SHA256 and policy evidence are recorded

---

## 23. Current external references to re-check before release

- Google Play Device and Network Abuse policy: https://support.google.com/googleplay/android-developer/answer/16559646
- Play Asset Delivery integration: https://developer.android.com/guide/playcore/asset-delivery
- Play Asset Delivery testing: https://developer.android.com/guide/playcore/asset-delivery/test
- Google Play app/asset size limits: https://support.google.com/googleplay/android-developer/answer/9859372
- Target API requirements: current Play Console target API policy

Do not treat this roadmap as permanent policy text. Re-check the live pages immediately before production submission.
