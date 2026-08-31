# Worker 04 — remove nested and writable executables

Last updated: 2026-09-01

## Result

**PARTIAL.** The source, generated host prefix, Play bundle, split APKs, and
host-runtime cleanup contracts pass. Worker 04 now also carries Android's
active-network DNS into the guest, uses a pre-provisioned Alpine Play baseline,
keeps Play customization local-only, and has a non-process-killing embedded
X11 lifecycle. Device-visible X11 start/stop/restart and a clean offline Alpine
end-to-end run remain unverified.

The precise networking status is: **Android connectivity/DNS is healthy;
guest resolver/package-network failure is under investigation.** On device
`2a580689`, Android could ping both `1.1.1.1` and
`dl-cdn.alpinelinux.org`, and the active Wi-Fi DNS was `192.168.1.1`. The PRoot
guest initially had public resolvers (`8.8.8.8`, `8.8.4.4`); guest hostname
resolution failed and `apk update` reported transient DNS errors. Replacing the
guest resolver with the Android DNS was attempted, but the interrupted install
left a rootfs directory without PRoot container metadata, so no valid Alpine
package/customization retry is claimed. Guest BusyBox ping and the direct app
UID ping also hit raw-socket `EPERM`; those results are not treated as proof of
an Android network failure.

The next worker remains pending and was not started:

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

### C. Android-derived guest DNS

- Added `GuestDnsConfigurator`, which reads numeric DNS servers from the active
  Android `LinkProperties.dnsServers` list and exports them as
  `FLUX_DNS_SERVERS`.
- `flux_guest_common.sh` writes that list to the guest `/etc/resolv.conf`
  after validation. Public resolvers are used only when Android supplies no
  usable resolver and the guest has no usable existing resolver.
- Alpine family/customization setup uses the same helper for direct invocation;
  the common concatenated PRoot path remains the normal path.
- Unit coverage includes IPv4, IPv6, multiple, empty, invalid, duplicate, and
  final-fallback resolver inputs.

### D. Alpine Play baseline and customization boundary

- Built the Play Alpine `aarch64` baseline from the official Alpine 3.24
  minirootfs plus the required shell/base tools, certificates, D-Bus, XFCE4,
  terminal, Mesa, Pulse client, and current Alpine family package set.
- Baseline marker: `/etc/fluxlinux/play-baseline-v1`.
- Baseline archive: `alpine_3.24_play_baseline_v1.tar.gz`,
  `254299349` bytes, SHA-256
  `da25146101274ce944472380285f09b96583dcb6093cdf57058ef2648b5f75d7`.
- Play Alpine setup verifies the marker and performs only local user,
  ownership, machine-id, Pulse, XFCE, and Flux asset setup. It does not run
  `apk update`, `apk add`, or remote customization downloads at runtime.
- Play Alpine, Debian, KDE-Debian, generic XFCE, and Debian customization
  assets are flavor-specific local-only overrides. The ivarna flavor keeps its
  existing remote customization implementation behind the flavor boundary.

### E. Nested/disguised APK and ELF inventory

- Removed tracked `app/src/main/assets/loader.apk` and `loader.bin`.
- Removed all flavor `assets/pulse-runtime/*.so` overlays.
- Added `scripts/verify_play_host_artifacts.sh` and extended
  `scripts/verify_apk_host_assets.sh` to inspect generic writable assets for
  nested APK signatures and unexpected ELF bytes. Guest-only ELF helpers are
  explicitly allowlisted with their guest context.
- Added `Worker04ArtifactContractTest` covering removed loader/Pulse assets,
  Play fallback boundaries, embedded GUI markers, package-list closure, and
  scanner contracts.

### F. GUI/X11 launcher audit

- `EmbeddedX11` starts `CmdEntryPoint` in the app process and launches the
  same-package X11 activity.
- `CmdEntryPoint` uses the app's `System.loadLibrary("Xlorie")` resolution and
  does not extract `libXlorie.so` from an APK or app-private copy.
- GUI scripts require `FLUX_EMBEDDED_X11=1` and no longer resolve a Termux:X11
  APK, `app_process`, `CLASSPATH`, `pm path`, or loader asset.
- Stop/setup scripts were cleaned of the corresponding nested-loader and
  external-X11 paths.
- `EmbeddedX11` now exposes `STOPPED`, `STARTING`, `RUNNING`, and `STOPPING`,
  with explicit `startServer`, `stopServer`, and `restartServer` ownership.
  Native `dix_main` returns to its owning thread; the old `System.exit` and
  `exit(dix_main(...))` shutdown paths were removed. Static lifecycle tests and
  both debug/release native links pass. Device-visible lifecycle proof is still
  pending.

### G. PulseAudio, libattr/libacl, and deployed permissions

- Pulse host libraries remain in the verified host runtime's native library
  directory. App-private deployment writes only `default.pa.d/99-fluxlinux.pa`.
- Device host initialization logged successful `libacl.so`, `libattr.so`,
  `pulseaudio --version`, PRoot launcher, and loader checks.
- Pulse supervisor exited successfully and logged an AAudio sink on
  `tcp=127.0.0.1:4713`. Existing nonfatal SLES/D-Bus symbol warnings remain
  outside this worker's scope.
- `HostScriptDeployer` now classifies deployed files as scripts, guest
  executables, guest libraries, configuration, or data. `libevp_md2.so` is a
  guest library and is not marked executable; scripts and the guest bwrap shim
  retain executable permissions.

### H. Device and artifact evidence

Validated artifact facts:

- Package metadata remains `com.zenithblue.fluxlinux`, `versionCode=12`,
  `versionName=2.0.0`, `targetSdk=36`.
- Fresh Zenithblue and Ivarna debug APKs and the Zenithblue release AAB build
  successfully with the Play baseline source and native X11 lifecycle.
- Final artifact hashes:
  - Zenithblue debug APK: `48141518` bytes,
    `5b4d7af8f2dc485ee4b5a8a124029cffab89d445df377ceab01c81add6d6c41f`.
  - Ivarna debug APK: `48084453` bytes,
    `6190e9358bd8488b61ed6f22b157568f6f7d09ffef4e61788652c2ba4967ca84`.
  - Zenithblue release AAB: `1086112666` bytes,
    `3cea428aee5244776f4793ad3d323455261d6fe4aa9f254fd179e304f5c46e31`.
- `scripts/verify_play_host_artifacts.sh` passes on the release AAB and
  Zenithblue debug APK. `scripts/verify_apk_host_assets.sh` passes on both
  Zenithblue and Ivarna debug APKs.
- The refreshed AAB customization scan passes for Alpine, Debian, KDE-Debian,
  generic XFCE, and the four legacy Termux customization entry points; no
  remote installer/download command remains in those Play assets. The Play
  DEX contains no `IvarnaRemoteCustomization` or `ProotZshBootstrap` class.
- The current device package is present on realme serial `2a580689`, but its
  interrupted Alpine attempt is not a valid installed PRoot container:
  `proot-distro list` reports no installed containers despite a stale rootfs
  directory. Therefore no current device Alpine/PFD/XFCE/X11 E2E pass is
  recorded here.

### I. 16 KB compatibility blocker

Android reported the exact warning: **“This app isn’t 16 KB compatible. ELF
alignment check failed.”** The affected libraries and first `LOAD` alignment in
the inspected arm64 base APK were:

| Library | First `LOAD` alignment |
|---|---:|
| `libtermux.so` | `0x1000` |
| `libpulseaudio.so` | `0x4000` |
| `libproot.so` | `0x4000` |
| `libpactl.so` | `0x4000` |
| `libloader32.so` | `0x1000` |
| `libloader.so` | `0x4000` |
| `libbash.so` | `0x4000` |
| `libandroidx.graphics.path.so` | `0x4000` |
| `libXlorie.so` | `0x4000` |

This is a release blocker carried into Workers 09 and 10. Worker 04 does not
claim release compatibility until every affected library is rebuilt or
otherwise supplied with verified 16 KB-compatible ELF alignment and the exact
warning is absent.

## Verification

Passed:

- `scripts/assemble_bootstrap.py --mode full`
- `scripts/verify_bootstrap.sh`
- `scripts/prepare_play_payloads.py`
- `scripts/build_alpine_play_baseline.sh` with deterministic hash/size output
- `scripts/verify_play_host_artifacts.sh` on the AAB and base split APK
- `scripts/verify_apk_host_assets.sh` on the Zenithblue and Ivarna debug APKs
- `testZenithblueDebugUnitTest` (367 tests, 0 failures)
- combined Zenithblue and Ivarna debug unit-test build
- Zenithblue and Ivarna debug APK builds plus the Zenithblue release AAB build
- static DNS, typed-permission, Play asset, and embedded-X11 lifecycle tests
- Android-layer DNS evidence and host-prefix inspection described above

Not proven in this pass:

- clean bundletool/local-testing installation of the refreshed AAB;
- offline Alpine extraction/setup through XFCE and visible X11;
- visible X11 start/stop/restart on device;
- real Google Play feature delivery.

Gradle was run with `--no-daemon`; after each Gradle invocation,
`./gradlew --stop` was run and the process list was checked to confirm no
Gradle daemon remained.

## Follow-up

Worker 05 remains pending at
`docs/playstore/workers/05_remove_root_chroot_from_play.md`; it was not started
as part of Worker 04.
