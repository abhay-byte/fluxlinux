# Worker 03 — Replace Remote Runtime Delivery With Play Feature Delivery

## Goal
For `zenithblue`, deliver the Flux-managed host runtime and every supported distro/rootfs through **Google Play Feature Delivery (PFD) on-demand dynamic-feature modules**, while preserving the existing local archive -> extraction -> configuration -> PRoot backend.

## Policy/architecture rule

Do **not** use Play Asset Delivery for rootfs/bootstrap payloads. Linux rootfs/bootstrap archives contain executable-bearing runtime content; the canonical design is Play Feature Delivery.

Use the current official Play Core Feature Delivery API compatible with the repo's AGP/Gradle versions. Do not copy a stale dependency/API version from this document.

## Phase A — prove the mechanism with the smallest useful payload

1. Add an on-demand `:runtime_host` dynamic feature carrying the large immutable host bootstrap payload and provenance manifest.
2. Add one small on-demand distro feature first, preferably `:distro_alpine`, carrying its rootfs archive + provenance manifest.
3. Register dynamic features correctly in the base app/App Bundle configuration.
4. Implement `PlayFeatureHostRuntimeProvider` and `PlayFeatureRootfsProvider` using `SplitInstallManager` or the current official equivalent.
5. Expose feature request/progress/cancel/error states to the existing install UI/state model.
6. After a feature is installed, access its packaged payload using supported split/module APIs and materialize/copy it into app-private staging if the existing installer requires a `File`.
7. Hash while materializing; reject checksum/metadata mismatch before extraction.
8. Do not persist an internal split/module filesystem path as durable state.
9. Feed the verified local archive into the existing installer and prove Alpine installs/launches without a GitHub rootfs request.

## Phase B — expand to all current distros

Create one on-demand dynamic feature for each supported Play distro:

- `:distro_debian`
- `:distro_alpine`
- `:distro_fedora`
- `:distro_void`
- `:distro_opensuse`
- `:distro_chimera`
- `:distro_deepin`
- `:distro_manjaro`
- `:distro_ubuntu`
- `:distro_kali`
- `:distro_parrot`
- `:distro_arch`

Use actual IDs from `DistroInstallProfile`; do not silently drop or rename a distro without updating the registry/tests/UI.

## Payload metadata

Each feature must contain a machine-readable provenance manifest with at least:

- distro/runtime ID + version;
- ABI;
- upstream source used during release preparation;
- upstream checksum when available;
- final archive SHA-256;
- compressed/uncompressed size;
- payload schema/version;
- source/build commit + build script;
- creation date.

CI/release tooling must verify the manifest against the packaged archive.

## Remove Play runtime network delivery

For `zenithblue`, eliminate functional use of:

- `RootfsDownloader`;
- `ROOTFS_RELEASE_BASE` / GitHub `releases/download/rootfs`;
- `/sdcard/Download` rootfs fallback/import as a runtime executable source;
- remote host bootstrap repair/fallback;
- `FLUX_ROOTFS_URL`.

`ivarna` may retain remote providers behind Worker 02's build-time boundary.

## Install state requirements

Recommended state sequence:

```text
REQUESTING_RUNTIME_FEATURE
DOWNLOADING_RUNTIME_FEATURE
REQUESTING_DISTRO_FEATURE
DOWNLOADING_DISTRO_FEATURE
MATERIALIZING_PAYLOAD
VERIFYING
EXTRACTING
CONFIGURING
INSTALLED
```

Handle cancellation, Play unavailable, module not found, insufficient storage, checksum failure, interrupted materialization/extraction, process death, and stale partial installs.

Never mark the distro installed merely because Play reports the feature module installed.

## Tests

```bash
./gradlew test
./gradlew bundleZenithblueRelease
```

Use current official bundletool/local-testing support for dynamic features, then test the same AAB through Internal App Sharing/Internal Play before Production.

On device, prove for the POC distro:

1. clean app has no distro payload installed;
2. user requests Alpine;
3. Play/local feature delivery installs `runtime_host` and `distro_alpine`;
4. payload is materialized and checksum verified;
5. existing extraction/configuration runs;
6. PRoot starts;
7. no GitHub rootfs/bootstrap request occurs.

Then repeat installation smoke tests for all supported distro features.

## Acceptance

- Play uses PFD dynamic features, not PAD, for executable-bearing runtime/rootfs payloads;
- `:runtime_host` exists and works on demand;
- every supported Play distro has an on-demand feature;
- base app remains materially smaller than bundling all payloads;
- existing installer consumes local archives and otherwise stays close to v2;
- no Flux-managed Play rootfs/bootstrap executable fetch from GitHub/HTTP remains;
- at least one PFD POC passes end to end before expanding to all modules.

## Do not

Do not rewrite PRoot, distro setup scripts, terminal, X11, or PulseAudio unless a minimal compatibility change is proven necessary. Do not add an HTTP fallback to make local testing easier.