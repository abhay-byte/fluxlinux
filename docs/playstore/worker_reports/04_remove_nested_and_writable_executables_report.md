# Worker 04 — remove nested and writable executables

Last updated: 2026-09-01

## Result

**PARTIAL.** The host-prefix, source/artifact cleanup, DNS boundary, local-only
Play setup path, and build-time Alpine transaction contracts pass. A shared
12-distro Play baseline framework is present, but only Alpine is provenance
complete in this pass; Debian has a completed transaction with incomplete
upstream provenance and the other ten inputs have not been provisioned. The
16 KB ELF gate still has two unaligned libraries, and clean device-visible
Alpine/XFCE/X11, bundletool, and offline end-to-end evidence remain unproven.

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

- Added `GuestDnsConfigurator`, which reads only numeric DNS servers from the
  active Android `LinkProperties.dnsServers` list and exports them as
  `FLUX_DNS_SERVERS`.
- An empty Android list is intentionally exported as empty. The common guest
  helper then preserves a usable existing guest resolver; public resolvers are
  written only when the existing resolver is absent or malformed.
- Resolver replacement writes through `/etc/resolv.conf` so an Alpine bind
  mount is not broken by an attempted `mv`.
- Container probes cover preservation of a valid resolver, replacement of a
  malformed resolver, and application of valid Android IPv4/IPv6 values. Unit
  coverage includes empty, invalid, duplicate, and fallback inputs.

### D. Play baseline provisioning and customization boundary

- Built the Play Alpine `aarch64` baseline from the pinned official Alpine
  3.24.1 minirootfs using a real `apk --root ... --arch aarch64 --update add`
  transaction. The upstream URL is recorded in the sidecar with input SHA-256
  `f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259`.
- Alpine final artifact: `alpine_3.24_rootfs.tar.gz`, `255103653` bytes,
  SHA-256
  `88714e4cc1637cdad5916200c5ac5b72c506506dd33166a12a0a58635618724c`.
  The sidecar records APK database hash
  `a669f37eef75ed89a54db93d84f93bad6e7735af89d0d761105b4e2a683736b0`,
  repository index hashes, 359 requested packages, and
  `runtimeNetworkRequired=false`. Final archive and upstream hashes are
  distinct and both are recorded.
- Debian has a valid xz-compressed final artifact from a completed aarch64
  package transaction: `420609048` bytes, SHA-256
  `48f341afe25da408fe30a41a1420f307c61feea93f5ad0be5682c8cea319d823`.
  Its sidecar deliberately retains `upstreamSource=null` and
  `upstreamSha256=null` because the original source URL/checksum was not
  recorded; the provenance gate therefore rejects it.
- `scripts/play_rootfs/manifests.json` and
  `scripts/play_rootfs/build_play_rootfs.py` define the shared build/validation
  framework for Debian, Alpine, Fedora, Void, openSUSE, Chimera, Deepin,
  Manjaro, Ubuntu, Kali, Parrot, and Arch. The current validator result is:
  Alpine **PASS**; Debian **FAIL** for incomplete provenance; Fedora, Void,
  openSUSE, Chimera, Deepin, Manjaro, Ubuntu, Kali, Parrot, and Arch **FAIL**
  because their build-time provenance sidecars are missing.
- `prepare_play_payloads.py` now requires a sidecar with upstream source/hash
  and `runtimeNetworkRequired=false`, so it refuses to stage raw rootfs inputs.
- Play family and customization assets are local-only overlays. The Ivarna
  flavor retains its existing remote customization implementation behind the
  flavor boundary.

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

Validated artifact facts from the existing Play outputs and the refreshed
Ivarna debug build:

- Package metadata remains `com.zenithblue.fluxlinux`, `versionCode=12`,
  `versionName=2.0.0`, `targetSdk=36`.
- The existing Zenithblue debug APK is `48141518` bytes,
  `5b4d7af8f2dc485ee4b5a8a124029cffab89d445df377ceab01c81add6d6c41f`; the
  existing Zenithblue release AAB is `1086112666` bytes,
  `3cea428aee5244776f4793ad3d323455261d6fe4aa9f254fd179e304f5c46e31`.
  These Play artifacts predate the current fail-closed payload staging change
  and are not presented as a refreshed compliant Play build.
- The refreshed Ivarna debug APK is `48755699` bytes,
  `289477779f7c374bfa47ed09f35de255d896e088b14549c6271df29ebaadecc3`.
- `scripts/verify_play_host_artifacts.sh` and
  `scripts/verify_apk_host_assets.sh` pass on the refreshed Ivarna debug APK;
  the merged Play runtime scanner also passes on source assets. The existing
  Play DEX contains no `IvarnaRemoteCustomization` or
  `ProotZshBootstrap` class.
- `adb devices` sees realme serial `2a580689` and two wireless entries, but no
  refreshed Play AAB could be installed locally because bundletool is absent
  and Play payload preparation is correctly blocked. No current device
  Alpine/PFD/XFCE/X11 E2E pass is recorded.

### I. 16 KB compatibility blocker

The permanent audit script scans every arm64 native library and every `LOAD`
segment. On the inspected Zenithblue debug APK, the full result was:

| Library | ELF class | All `LOAD` alignments | Status |
|---|---|---|---|
| `libXlorie.so` | ELF64 | `0x4000,0x4000,0x4000` | aligned |
| `libandroidx.graphics.path.so` | ELF64 | `0x4000,0x4000,0x4000` | aligned |
| `libbash.so` | ELF64 | `0x4000,0x4000,0x4000` | aligned |
| `libloader.so` | ELF64 | `0x4000,0x4000` | aligned |
| `libloader32.so` | ELF32 | `0x1000,0x1000,0x1000` | **UNALIGNED** |
| `libpactl.so` | ELF64 | `0x4000,0x4000,0x4000` | aligned |
| `libproot.so` | ELF64 | `0x4000,0x4000,0x4000` | aligned |
| `libpulseaudio.so` | ELF64 | `0x4000,0x4000,0x4000` | aligned |
| `libtermux.so` | ELF64 | `0x1000,0x1000` | **UNALIGNED** |

The APK ZIP page-alignment check passes. A bundletool check could not run because
bundletool is not installed. `libtermux.so` comes from the pinned
`terminal-emulator-v0.118.0` dependency, and `libloader32.so` is a prebuilt
Termux loader without a source checkout in this worktree; neither was rebuilt
or replaced. This remains a release blocker carried into Workers 09 and 10.

## Verification

Passed:

- `scripts/assemble_bootstrap.py --mode full`
- `scripts/verify_bootstrap.sh`
- `scripts/build_alpine_play_baseline.sh` with a real `apk` transaction and
  complete provenance sidecar
- `scripts/verify_apk_host_assets.sh` on the refreshed Ivarna debug APK
- `scripts/verify_play_host_artifacts.sh` on the refreshed Ivarna debug APK
- `scripts/verify_play_runtime_scripts.sh`
- `testIvarnaDebugUnitTest` (371 tests, 0 failures)
- Python and shell syntax checks
- Android-layer DNS and Alpine resolver container probes

Expected/recorded failures in this pass:

- `scripts/verify_play_rootfs_provenance.py`: Alpine passes; Debian lacks
  upstream source/hash; Fedora, Void, openSUSE, Chimera, Deepin, Manjaro,
  Ubuntu, Kali, Parrot, and Arch lack build sidecars.
- `scripts/prepare_play_payloads.py --verify-only`: refuses Debian because
  provenance is incomplete.
- `testZenithblueDebugUnitTest`: stops at `preparePlayPayloads` refusing the
  raw Debian input; no Play unit results are claimed from that invocation.
- `scripts/verify_16k_page_compat.sh`: APK ZIP alignment passes, but
  `libloader32.so` and `libtermux.so` are unaligned and bundletool is absent.
- `:app:assembleZenithblueDebug` and `:app:bundleZenithblueRelease` were not
  accepted as current builds because Play payload preparation is blocked by
  the missing provenance sidecars.

Not proven in this pass:

- clean bundletool/local-testing installation of a refreshed AAB;
- offline Alpine extraction/setup through XFCE and visible X11;
- visible X11 start/stop/restart on device;
- clean PRoot/Pulse device smoke tests;
- real Google Play feature delivery.

Gradle was run with `--no-daemon`; after each Gradle invocation,
`./gradlew --stop` was run and the process list was checked to confirm no
Gradle daemon remained.

## Follow-up

Worker 05 remains pending at
`docs/playstore/workers/05_remove_root_chroot_from_play.md`; it was not started
as part of Worker 04.
