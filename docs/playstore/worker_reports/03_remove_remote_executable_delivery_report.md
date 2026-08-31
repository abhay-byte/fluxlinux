# Worker 03 completion report — Play Feature Delivery executable payloads

Date: 2026-08-31

## Result

**PARTIAL**

The implementation, unit tests, payload verification, both flavor builds, Play
AAB inspection, and a local-testing device run completed. The device proved
base-only installation, on-demand `runtime_host` delivery, SplitCompat asset
access, bootstrap materialization, and bootstrap SHA verification. The later
host initialization failed in the existing Pulse/system-library setup before a
distro could be selected, and a real Play serving backend/internal track was
not available. Per the worker contract, this is not reported as PASS.

## Scope and boundary

- Branch: `playstore-v2-compliance`
- Worker starting commit: `de9a1260ec5cd083ac8cb513f0b09c292ce8c70d`
- Play application ID: `com.zenithblue.fluxlinux`
- Play version: `versionCode 12`, `versionName 2.0.0`
- Preserved backend: existing embedded PRoot, terminal, X11, and Pulse paths
- Deferred: Worker 04 nested/writable executables, Worker 05 root/chroot removal,
  and later policy, service, metadata, CI, and release workers

## Implemented delivery path

The Play flavor now requests the exact selected module through
`SplitInstallManager`:

| Payload | Dynamic feature | On-demand asset |
|---|---|---|
| Host runtime | `runtime_host` | `payloads/runtime_host/bootstrap.tar` |
| Debian | `distro_debian` | `payloads/distro_debian/debian_13_rootfs.tar.xz` |
| Alpine | `distro_alpine` | `payloads/distro_alpine/alpine_3.24_rootfs.minirootfs` |
| Fedora | `distro_fedora` | `payloads/distro_fedora/fedora_44_rootfs.tar.xz` |
| Void | `distro_void` | `payloads/distro_void/void_20250202_rootfs.tar.xz` |
| openSUSE | `distro_opensuse` | `payloads/distro_opensuse/opensuse_tumbleweed_rootfs.tar.xz` |
| Chimera | `distro_chimera` | `payloads/distro_chimera/chimera_20251220_rootfs.tar.xz` |
| Deepin | `distro_deepin` | `payloads/distro_deepin/deepin_25_rootfs.tar.xz` |
| Manjaro | `distro_manjaro` | `payloads/distro_manjaro/manjaro_arm_rootfs.tar.xz` |
| Ubuntu | `distro_ubuntu` | `payloads/distro_ubuntu/ubuntu_26.04_rootfs.tar.xz` |
| Kali | `distro_kali` | `payloads/distro_kali/kali_2026_2_rootfs.tar.xz` |
| Parrot | `distro_parrot` | `payloads/distro_parrot/parrot_7.2_rootfs.tar.xz` |
| Arch | `distro_arch` | `payloads/distro_arch/archlinux_arm_rootfs.tar.xz` |

All thirteen modules are flavor-matched dynamic-feature modules with
`dist:on-demand` delivery and fusing enabled. The common installer requests a
host runtime first, then only the selected distro module. It does not know
about HTTP, release URLs, split storage paths, or shared-storage payload
locations.

### Alpine asset identity follow-up

The Worker 03 artifact preflight found that a `.tar.gz` asset is handled by
`aapt2` as a gzip asset: the built entry was renamed to `.tar` and contained
expanded tar bytes, so it no longer matched the pinned gzip SHA-256. This was
a packaging bug, not a source-input discrepancy. The focused correction keeps
the original source and provenance `archiveFileName` as
`alpine_3.24_rootfs.tar.gz`, but stages those exact gzip bytes under the
neutral feature asset name:

```text
source input:
  assets/rootfs/alpine_3.24_rootfs.tar.gz
feature/AAB/split asset:
  payloads/distro_alpine/alpine_3.24_rootfs.minirootfs
runtime AssetManager lookup:
  payloads/distro_alpine/alpine_3.24_rootfs.minirootfs
materialized app-private filename:
  alpine_3.24_rootfs.tar.gz
SHA-256:
  f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259
```

The corrected AAB and feature asset were inspected byte-for-byte; the old
`.tar` entry is absent. The correction is recorded in the Worker 04 change
set because it was discovered by that mandatory preflight.

`PlayFeatureDeliveryCoordinator` handles already-installed modules, active
session re-entry after process death, progress callbacks, cancellation, failed
sessions, and the foreground Activity Result bridge for user confirmation.
The provider then creates a split context, validates the feature's provenance
manifest, streams the exact asset to a deterministic `.part` file under the
app-private host home, verifies byte count/size/SHA-256, and atomically renames
it into place. A failed or cancelled stream leaves no usable partial archive.

## Provenance and inputs

`scripts/prepare_play_payloads.py` is a no-download maintainer/CI staging
script. It verifies every source archive against the existing Worker 02 pins,
measures compressed and regular-member uncompressed bytes, copies bytes into
ignored `src/zenithblue/assets/payloads/` directories, and writes a
module-unique `payloads/<module>/provenance.json` beside each archive. The manifest records schema,
payload identity/version, distro, architecture, archive filename/hash/sizes,
upstream source/checksum where available, source commit, build script/date, and
Flux customization record. No rootfs archive or bootstrap tar is committed.

Example source-input command used for this worker:

```sh
SOURCE_DATE_EPOCH=1755129600 python3 scripts/prepare_play_payloads.py \
  --source-root /home/abhaybyte/repos/fluxlinux/assets/rootfs \
  --host-source /home/abhaybyte/repos/fluxlinux-playstore-v2-compliance/native/bootstrap/com.zenithblue.fluxlinux/bootstrap.tar \
  --source-commit de9a1260ec5cd083ac8cb513f0b09c292ce8c70d
```

The generated host archive measured 129,413,120 bytes with SHA-256
`3ffef7f92820341e2a74b739fb15695a16fe4622e80cfc81d18bd98461712609`. The
staging script reports `PASS: verified 12 distro payloads + runtime_host`.

## Remote/shared-storage fallback removal

The Play source set contains no `RootfsDownloader`, HTTP URL, GitHub release
fallback, or `/sdcard/Download` payload path. Common rootfs setup scripts now
require the verified `FLUX_ROOTFS_PATH` supplied by the provider and fail closed
when it is absent. The non-Play `ivarna` downloader remains isolated in its
flavor source set for the later non-Play regression boundary.

## Verification

| Check | Result |
|---|---|
| `python3 -m py_compile scripts/prepare_play_payloads.py` | PASS |
| shell syntax checks for changed rootfs setup scripts | PASS |
| `./gradlew test --no-daemon` with external source-input properties | PASS |
| `assembleZenithblueDebug`, `assembleIvarnaDebug`, `bundleZenithblueDebug` | PASS |
| PFD registry/provenance/staging/session tests | PASS |
| `git diff --check` | PASS |
| `bundletool 1.17.1 build-apks --local-testing` | PASS |
| `bundletool install-apks` base-only on device `2a580689` | PASS — only base/config splits installed initially |
| Device `runtime_host` request + SplitCompat install | PASS — feature copied and emulated locally |
| Device bootstrap materialization + SHA-256 | PASS — 129,413,120 bytes, SHA-256 `3ffef7f92820341e2a74b739fb15695a16fe4622e80cfc81d18bd98461712609` |
| Device host initialization through Pulse/PRoot | PARTIAL — failed later on existing `libattr.so`/Pulse setup |
| Device Alpine request, rootfs extraction, and PRoot launch | NOT RUN — blocked by host initialization failure |

## AAB inventory and sizes

The final Play bundle is:

```text
app/build/outputs/bundle/zenithblueDebug/app-zenithblue-debug.aab
SHA-256: b0c31603e701aca4269e8ca2a7b035c7d75c4fa21349498b2c3e378932bc4939
```

Bundletool reports application ID `com.zenithblue.fluxlinux`, version code
`12`, version name `2.0.0`, and these module roots. Sizes below are the AAB
uncompressed/compressed ZIP totals in MiB:

| Module | Uncompressed | Compressed |
|---|---:|---:|
| `base` | 108.54 | 42.10 |
| `runtime_host` | 123.42 | 36.79 |
| `distro_alpine` | 8.54 | 3.85 |
| `distro_arch` | 110.89 | 110.92 |
| `distro_chimera` | 5.10 | 5.10 |
| `distro_debian` | 81.07 | 81.10 |
| `distro_deepin` | 53.13 | 53.14 |
| `distro_fedora` | 29.49 | 29.50 |
| `distro_kali` | 117.54 | 117.57 |
| `distro_manjaro` | 126.88 | 126.91 |
| `distro_opensuse` | 21.11 | 21.11 |
| `distro_parrot` | 106.67 | 106.69 |
| `distro_ubuntu` | 19.78 | 19.78 |
| `distro_void` | 43.67 | 43.68 |

The AAB contains 13 payload archives and 13 provenance manifests, each under
its owning module. It contains no `base/assets/bootstrap.tar`, no base rootfs
asset, and no duplicate shared provenance path. Bundletool's generated local
testing APK set stores the payload-bearing split APKs without compression and
the Android package manifest confirms all modules are `dist:on-demand`.

## Regressions and known limitations

- Existing Ivarna release-backed payload behavior remains flavor-owned.
- The Play build cannot be fully validated against Play's serving backend in
  this environment; Internal App Sharing or an Internal testing track remains
  required before production.
- Root/chroot implementation remains in the source tree and is outside this
  worker's assigned removal boundary.
- Payload inputs are intentionally external, ignored files; CI/release jobs
  must provide the exact pinned archives and the generated Zenithblue host tar.

## Next worker

`docs/playstore/workers/04_remove_nested_and_writable_executables.md`

Stop here. Worker 04 must begin only after this report and commit are reviewed.
