# Worker 09 — CI Gate for PAD-Only Play Runtime Delivery

## Goal
Make policy/distribution regressions fail CI before a Play AAB can be released.

## Tasks
Create or extend `scripts/verify_play_policy.sh` and run it in GitHub Actions for the Play release build.

### Source/static deny checks
Fail Play build if it contains functional paths for:
- `REQUEST_INSTALL_PACKAGES`;
- `ACCESS_SUPERUSER`;
- `PackageInstaller` / unknown-source installation;
- GitHub `releases/download/rootfs` delivery;
- Play HTTP rootfs provider;
- executable bootstrap repair/fallback URL;
- nested `loader.apk`;
- Magisk/KernelSU/APatch Play integration.

### PAD checks
Verify:
- every supported Play distro has an asset-pack module;
- delivery type is `on-demand` unless explicitly documented otherwise;
- each pack contains expected archive + provenance manifest;
- manifest SHA256/size matches archive;
- Play distro registry maps only to Play asset packs, never HTTP URLs.

### Artifact checks
Build `bundleZenithblueRelease`, extract/inspect AAB/generated APKs and reject:
- nested APK/JAR/DEX payloads under generic assets unless explicitly justified;
- APK magic disguised with another extension;
- unexpected Android-host ELF in generic assets;
- host executables that should be `jniLibs` but are copied as writable assets;
- root/chroot permissions;
- old GitHub rootfs/bootstrap strings in Play dex/resources.

Expected host native libraries under normal native-lib locations are allowed.

### Build matrix
```bash
./gradlew test
./gradlew lintZenithblueRelease
./gradlew bundleZenithblueRelease
```

Also keep `ivarna` compile coverage so flavor isolation does not rot.

### Negative fixtures
Add CI fixtures/tests proving the checker fails when:
- a fake nested APK is added;
- a rootfs GitHub URL is added to Play source;
- `ACCESS_SUPERUSER` returns;
- a distro pack checksum is wrong.

## Acceptance
- one CI command validates source + final artifact;
- a release cannot pass if Play rootfs delivery regresses to HTTP/GitHub;
- PAD manifests/checksums are verified;
- expected Play native libraries are not falsely rejected;
- negative fixtures prove the gate works.

## Do not
Do not simply grep the source tree without inspecting the final AAB; flavor/resource merging can reintroduce forbidden content.