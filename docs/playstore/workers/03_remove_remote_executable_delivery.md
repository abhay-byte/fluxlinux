# Worker 03 — Remove Remote Executable Delivery from Play

## Goal
Ensure the Google Play build never downloads and executes Linux rootfs/bootstrap/native executable payloads from GitHub or user storage.

## Do
1. Exclude `RootfsDownloader` and rootfs-network implementation from `zenithblue`.
2. Remove Play references to `ROOTFS_RELEASE_BASE` and `releases/download/rootfs`.
3. Remove Play bootstrap repair/fallback downloads, even if the normal Play bootstrap is packaged.
4. Remove Play local-candidate import paths from `/sdcard/Download` for executable rootfs.
5. Ensure no Play code extracts/launches externally supplied rootfs archives.
6. Keep remote delivery only in the non-Play source set.
7. Add tests proving the Play implementation fails closed and never makes the download call.

## Tests
```bash
./gradlew :app:testZenithblueDebugUnitTest
./gradlew :app:assembleZenithblueDebug
unzip -p app/build/outputs/apk/zenithblue/debug/*.apk classes.dex | strings | grep -E 'RootfsDownloader|releases/download/rootfs|bootstrap_com\.zenithblue' && exit 1 || true
```
Use a proper dex/string scanner if `strings` cannot inspect the built artifact reliably.

## Acceptance
- No rootfs/bootstrap executable network-delivery implementation in Play artifact.
- No rootfs release URL in Play dex/resources.
- Play UI does not offer a hidden/manual download workaround.
- Non-Play behavior remains functional.
