# Worker 09 — CI Policy Gate for the Play Feature Delivery Architecture

## Goal
Make distribution/policy regressions fail automatically before a `zenithblue` AAB can be released, while avoiding false positives against expected Linux guest payloads.

## Scope rule

Validate the **Play source sets/wiring and the final Play AAB**. Do not blanket-ban behavior that intentionally remains in `ivarna`.

## Tasks

Create/extend `scripts/verify_play_policy.sh` and invoke it from CI for the Play release build.

### 1. Source/config wiring checks

Fail if `zenithblue` has a functional path for:

- `REQUEST_INSTALL_PACKAGES` / `PackageInstaller` / unknown-source install;
- `ACCESS_SUPERUSER`;
- Magisk/KernelSU/APatch/real-chroot integration;
- `RootfsDownloader` as the Play provider;
- GitHub `releases/download/rootfs` fallback;
- remote executable host-bootstrap repair/fallback;
- `FLUX_ROOTFS_URL` as a Play install dependency;
- obsolete external-Termux `RUN_COMMAND`/install-server bridge;
- nested `loader.apk` reconstruction/deployment;
- legacy browsable callback that can mutate install state without a nonce-bound protocol.

### 2. Play Feature Delivery registry checks

Verify:

- `:runtime_host` exists and is configured for on-demand delivery;
- every supported Play distro has exactly one expected on-demand dynamic-feature module;
- the Play distro registry maps to feature-module IDs, never HTTP executable/rootfs URLs;
- every feature contains the expected payload + provenance manifest;
- manifest SHA-256/size/version matches the packaged archive;
- feature compressed sizes remain below the **current** Google Play per-feature limit with a safety margin;
- module naming/registry has no orphaned or missing distro.

### 3. Final AAB checks

Build `bundleZenithblueRelease`, inspect the AAB/generated APK set, and verify:

- package ID/version/target SDK/ABI metadata;
- expected dynamic features + delivery conditions;
- expected directly executed host native libraries under normal native-lib locations;
- no nested/disguised APK;
- no root/install permissions;
- no obsolete external-Termux service/permission/query;
- no unexpected directly executed Android-host ELF in generic base assets;
- no old GitHub rootfs/bootstrap executable fallback string in Play DEX/resources;
- no Play code path that silently falls back from PFD to HTTP.

### 4. ELF/archive classification rule

A Linux distro archive is **expected** to contain guest ELF executables. Do not reject a dynamic-feature/rootfs archive merely because ELF magic exists inside it.

Instead distinguish:

- expected guest/rootfs/archive content in the documented distro feature;
- expected Android-host native code in normal native-lib locations;
- unexpected host executable/nested Android code in generic writable assets or fallback payloads.

### 5. Build/test matrix

```bash
./gradlew test
./gradlew lintZenithblueRelease
./gradlew bundleZenithblueRelease
./gradlew assembleIvarnaDebug
./scripts/verify_play_policy.sh
```

Use actual task names discovered from Gradle if variants differ.

### 6. Negative fixtures

Prove the checker fails when test fixtures introduce at least:

- fake nested APK/APK magic under another extension;
- Play rootfs GitHub URL/provider wiring;
- remote host bootstrap executable fallback;
- `ACCESS_SUPERUSER`;
- `REQUEST_INSTALL_PACKAGES`;
- wrong distro feature checksum;
- missing required distro feature;
- wrong delivery mode;
- unexpected host ELF in generic base assets.

Keep fixtures isolated from production build inputs.

## Acceptance

- one CI gate validates Play source/config + final AAB;
- all PFD modules/payload manifests are checked;
- release fails if Play regresses to remote Flux-managed executable/rootfs delivery;
- expected guest ELF/rootfs content is not falsely rejected;
- expected Android native libraries are not falsely rejected;
- negative fixtures prove the gate detects real regressions;
- `ivarna` build coverage remains green.

## Do not

Do not rely only on source grep. Do not scan the whole repository as though non-Play behavior must disappear. Do not whitelist failures merely to get CI green without documenting the exact expected artifact.