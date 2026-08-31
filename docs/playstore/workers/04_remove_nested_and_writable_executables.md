# Worker 04 — Harden Host Runtime Packaging, API-36 W^X, and X11 Loader

## Goal
Keep the existing embedded host backend working while ensuring directly executed Android-host code is Play-installed and W^X-safe, the large bootstrap is sourced from `:runtime_host`, and the nested/disguised loader APK is removed only after a working replacement exists.

## Critical existing design to preserve

FluxLinux v2 already avoids executing normal `$PREFIX/bin/*` as argv0 on targetSdk 36. `TermuxHostPaths`/launch code resolves important host launchers such as bash/PRoot loader paths from `applicationInfo.nativeLibraryDir`.

Do **not** replace this with a blanket "move the whole bootstrap to jniLibs" rewrite.

## Tasks

1. Inventory every executable/ELF/native library currently present in:
   - `jniLibs` / native modules;
   - bootstrap tarball/prefix;
   - generic assets;
   - `HostScriptDeployer` output;
   - copied Pulse/X11/helper paths;
   - shell/process argv0 call sites.
2. Classify each item as:
   - directly executed Android-host launcher/native library;
   - host-prefix program executed behind the existing launcher/termux-exec mechanism;
   - guest/rootfs binary;
   - interpreted script/config/data;
   - obsolete.
3. Preserve/package directly executed host launchers in normal Play native-library locations and use `applicationInfo.nativeLibraryDir` as the canonical executable source.
4. Preserve `TermuxHostPaths` W^X routing unless a device test proves a specific path must change.
5. Change Play host bootstrap sourcing so the large bootstrap archive comes from Worker 03's `:runtime_host` dynamic feature. Keep the existing extraction/layout where practical.
6. Remove all Play remote bootstrap repair/fallback URLs. Repair must re-materialize from the Play feature.
7. Audit any `chmod +x` or copied `.so`/ELF behavior. Remove direct execution of writable copied host bytes; do not remove benign permissions on scripts/data without understanding their execution path.
8. Audit `assets/loader.apk`, `loader.bin`, build tasks that reconstruct the APK, and every `HostScriptDeployer`/X11 consumer.
9. Implement the required X11 loader behavior as a normal Android module/dependency or safe in-process integration **before deleting the old nested loader**.
10. Prove X11 startup on device using the replacement.
11. Remove:
    - `loader.apk` generic asset;
    - disguised/duplicate `loader.bin` APK bytes;
    - reconstruction Gradle task;
    - deploy/copy logic that only exists for the nested APK.
12. Add artifact tests that scan generic assets for APK magic and unexpected Android-host ELF payloads.
13. Keep guest rootfs binaries untouched; they are handled by PRoot after Play Feature Delivery materialization/extraction.

## Tests

```bash
./gradlew test
./gradlew bundleZenithblueRelease
```

Artifact checks:

- expected directly executed host `.so`/launcher files are in native library locations;
- `runtime_host` contains the intended bootstrap payload/provenance;
- no nested `.apk` exists;
- no APK bytes disguised under another extension;
- no unexpected directly executed host ELF is shipped as a generic writable asset;
- no remote bootstrap fallback string remains in the Play artifact.

Device smoke tests on targetSdk/API-36 path:

- embedded shell starts;
- host bootstrap installs/restores from PFD;
- PRoot starts;
- one PFD-delivered distro launches;
- X11 starts after nested-loader removal;
- PulseAudio starts;
- reinstall/repair of host runtime works without HTTP fallback.

## Acceptance

- current W^X-safe launcher strategy is preserved or improved with proof;
- large bootstrap comes from `:runtime_host` PFD module;
- no Play remote host-runtime repair path remains;
- no directly executed host bytes are copied to writable storage as a fallback;
- nested/disguised loader APK is gone;
- X11/terminal/PRoot/Pulse still work;
- no unnecessary whole-prefix-to-jniLibs rewrite was introduced.

## Do not

Do not convert same-architecture PRoot to QEMU. Do not delete the loader before its replacement is proven. Do not move every file in the Termux-style prefix into `jniLibs`.