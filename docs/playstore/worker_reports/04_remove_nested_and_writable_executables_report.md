# Worker 04 — remove nested and writable executables

Last updated: 2026-08-31

## Result

**PARTIAL.** The source, generated host prefix, Play bundle, split APKs, and
device host-runtime path satisfy the Worker 04 cleanup contracts. The device
also requested and materialized the Alpine feature and entered the installed
guest through PRoot. The final Alpine package/customization phase could not be
completed because Alpine repository DNS was intermittent on the validation
device, so a full XFCE startup and visible embedded X11 session are not claimed.

The required next worker remains:

`docs/playstore/workers/05_remove_root_chroot_from_play.md`

## Inventory and changes

### A. Host prefix and W^X routing

- Added `attr` to the host package SSOT and bootstrap-host package list. The
  regenerated full bootstrap contains both `usr/lib/libattr.so` and
  `usr/lib/libacl.so`.
- Full bootstrap artifact:
  - archive identity: `bootstrap_com.zenithblue.fluxlinux.v2.tar`
  - compressed size: `129576960` bytes
  - SHA-256:
    `87da10cf99613c00b6841200c29be1d9bcebaf36a2e7c5e312660807e9f965bd`
- `TermuxHostPaths.nativeLibraryDir` remains the source for directly executed
  Android-native host binaries and libraries (`libbash.so`, `libproot.so`,
  `libloader.so`, `libloader32.so`, `libpulseaudio.so`, and `libpactl.so`).
- Writable app-private prefix data contains scripts/configuration and the
  verified extracted bootstrap; there is no direct host-library fallback copy
  in `files/usr/lib`.
- `setup_termux.sh` now fails closed unless the prefix has both `libacl.so` and
  `libattr.so`, and retains direct native-library W^X checks.

### B. External host executable and rootfs fallbacks

- The Play source set has no `RootfsDownloader`, release-URL executable
  fallback, or `/sdcard/Download` fallback.
- Play payload staging keeps host and distro delivery in dynamic-feature
  payloads with SHA-256 provenance. The base APK contains neither a bootstrap
  nor a rootfs archive.
- `HostScriptDeployer` no longer copies loader or Pulse `.so` files into
  writable app data. It writes only non-executable Pulse configuration.

### C. Nested/disguised APK and ELF inventory

- Removed tracked `app/src/main/assets/loader.apk` and `loader.bin`.
- Removed all flavor `assets/pulse-runtime/*.so` overlays.
- Added `scripts/verify_play_host_artifacts.sh` and extended
  `scripts/verify_apk_host_assets.sh` to inspect generic writable assets for
  nested APK signatures and unexpected ELF bytes. Guest-only ELF helpers are
  explicitly allowlisted with their guest context.
- Added `Worker04ArtifactContractTest` covering removed loader/Pulse assets,
  Play fallback boundaries, embedded GUI markers, package-list closure, and
  scanner contracts.

### D. GUI/X11 launcher audit

- `EmbeddedX11` starts `CmdEntryPoint` in the app process and launches the
  same-package X11 activity.
- `CmdEntryPoint` uses the app's `System.loadLibrary("Xlorie")` resolution and
  does not extract `libXlorie.so` from an APK or app-private copy.
- GUI scripts require `FLUX_EMBEDDED_X11=1` and no longer resolve a Termux:X11
  APK, `app_process`, `CLASSPATH`, `pm path`, or loader asset.
- Stop/setup scripts were cleaned of the corresponding nested-loader and
  external-X11 paths.

### E. PulseAudio and libattr/libacl

- Pulse host libraries remain in the verified host runtime's native library
  directory. App-private deployment writes only `default.pa.d/99-fluxlinux.pa`.
- Device host initialization logged successful `libacl.so`, `libattr.so`,
  `pulseaudio --version`, PRoot launcher, and loader checks.
- Pulse supervisor exited successfully and logged an AAudio sink on
  `tcp=127.0.0.1:4713`. Existing nonfatal SLES/D-Bus symbol warnings remain
  outside this worker's scope.

### F. Device and artifact evidence

Validated on realme device serial `2a580689`:

- `bundletool build-apks --local-testing` succeeded for the final AAB.
- Bundletool installation staged the runtime and Alpine feature modules through
  the local-testing FakeSplitInstallManager path.
- Package state: `versionCode=12`, `targetSdk=36`, `versionName=2.0.0`.
- Runtime feature extraction produced marker `2|com.zenithblue.fluxlinux`.
- Alpine feature materialized
  `alpine_3.24_rootfs.tar.gz`, size `4023732`, SHA-256
  `f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259`.
- PRoot smoke commands succeeded:
  `proot-distro login alpine -- /bin/echo guest-ok` returned `guest-ok`, and
  the BusyBox identity command returned `uid=0(root)` inside the guest.
- Device inspection found no `loader.apk`, `loader.bin`, or
  `pulse-runtime` directory in app data, and no
  `usr/libexec/termux-x11` directory.

The device reached `Installing Alpine rootfs + XFCE…` and the app's setup
phase reached `Configuring alpine (Alpine Family)...`. A direct `apk update`
could reach both v3.24 repositories, but subsequent package resolution
intermittently failed with DNS errors and the full setup did not reach the
XFCE-ready marker. Consequently, the visible X11 display and end-to-end GUI
session remain unverified in this worker.

The debug device also displayed Android's existing 16 KB compatibility warning
for several prebuilt native libraries. This is recorded as validation evidence;
it is not silently treated as a Worker 04 pass condition.

## Verification

Passed:

- `scripts/assemble_bootstrap.py --mode full`
- `scripts/verify_bootstrap.sh`
- `scripts/prepare_play_payloads.py`
- `scripts/verify_play_host_artifacts.sh` on the AAB and base split APK
- `scripts/verify_apk_host_assets.sh` on the base split APK
- Zenithblue and Ivarna flavor APK builds
- Zenithblue Play AAB build
- bundletool split generation and installation
- host bootstrap/Pulse/PRoot device smoke checks above

Gradle was run with `--no-daemon`; after each Gradle invocation,
`./gradlew --stop` was run and the process list was checked to confirm no
Gradle daemon remained.

## Follow-up

Worker 05 should begin at
`docs/playstore/workers/05_remove_root_chroot_from_play.md`.
