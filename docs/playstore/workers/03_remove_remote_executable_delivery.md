# Worker 03 — Replace GitHub Rootfs Delivery With Play Asset Delivery

## Goal
For `zenithblue`, every FluxLinux-managed distro/rootfs payload must come from Google Play Asset Delivery. Preserve the existing extraction/configuration/backend after a local archive becomes available.

## Tasks
1. Add Play Asset Delivery support compatible with the repo's current AGP/Gradle versions; pin the chosen dependency/version.
2. Create **one on-demand asset pack per supported distro/ABI** initially. Example names: `distro_debian_arm64`, `distro_ubuntu_arm64`, etc.
3. Move/copy the release rootfs archives into each pack under `src/main/assets/rootfs/`.
4. Add pack modules to `settings.gradle(.kts)` and the app's `assetPacks` configuration.
5. Replace Play profile URLs with:
   - `assetPackName`;
   - archive filename;
   - SHA256;
   - expected/min size;
   - payload version.
6. Implement `PlayAssetRootfsProvider`:
   - request the pack;
   - expose download/progress states;
   - handle cancel/failure/retry;
   - resolve pack location only when needed;
   - return a local archive to the existing installer.
7. Keep archive verification before extraction.
8. Use staging + atomic promotion for extraction; never mark installed before configuration succeeds.
9. Remove Play runtime use of `RootfsDownloader`, `ROOTFS_RELEASE_BASE`, GitHub rootfs URLs, `/sdcard/Download` rootfs fallback, and executable bootstrap repair URLs.
10. Optional after successful extraction: remove the PAD cache copy to save space; repair/reinstall must re-request it from Play.
11. Add a manifest/provenance file per rootfs pack with distro version, ABI, checksum, compressed/uncompressed size, payload version, and build source commit/script.

## Tests
```bash
./gradlew test
./gradlew bundleZenithblueRelease
bundletool build-apks --bundle <aab> --output fluxlinux.apks --local-testing
bundletool install-apks --apks fluxlinux.apks
```

On-device verify: request pack, progress, cancel, retry, extract, configure, launch distro.

Static checks: Play artifact must not contain `releases/download/rootfs` or the old rootfs HTTP implementation.

## Acceptance
- all Play distro payloads are inside on-demand Google Play asset packs;
- initial base app remains small;
- existing distro installer receives a local archive and otherwise behaves as before;
- no Play runtime rootfs/bootstrap executable fetch from GitHub/HTTP remains;
- local PAD testing succeeds for at least one distro before expanding to all packs.

## Do not
Do not rewrite PRoot, distro setup scripts, terminal, X11, or PulseAudio unless a minimal compatibility change is required.