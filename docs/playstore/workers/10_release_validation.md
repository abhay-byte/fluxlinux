# Worker 10 — Final Play Asset Delivery Release Validation

## Goal
Prove the exact Play AAB works, upgrades safely, receives Flux-managed runtime payloads only from Google Play, and matches Play Console declarations.

## Build gates
```bash
./gradlew clean test lintZenithblueRelease bundleZenithblueRelease
./scripts/verify_play_policy.sh
```

Record final AAB SHA256.

## Bundle/PAD validation
1. Inspect AAB manifest/modules/native libs/asset packs.
2. Confirm package ID/version/target SDK/ABIs.
3. Confirm each distro pack is on-demand and within current Play size limits.
4. Confirm no rootfs/bootstrap executable URL remains in the Play artifact.
5. Confirm no nested APK and no Android-root permission.
6. Run official local PAD test:

```bash
bundletool build-apks \
  --bundle <play.aab> \
  --output fluxlinux-play.apks \
  --local-testing
bundletool install-apks --apks fluxlinux-play.apks
```

## Device matrix
Test API 33, 34, 35, 36 on representative supported ABIs.

For at least one distro on every API and **every supported distro before release**:
- request PAD pack;
- show progress;
- cancel/retry;
- network-loss path;
- storage failure path;
- verify archive;
- extract/configure;
- terminal start;
- normal guest commands;
- PRoot fake root;
- desktop/X11;
- PulseAudio;
- stop/restart/reboot;
- uninstall/reinstall distro.

Test process death during download and extraction.

## Upgrade tests
Upgrade from Play v1.8p `versionCode 11` to the new build:
- app data migration;
- old install-state migration;
- no external-Termux prerequisite dead state;
- existing user files preserved where intended.

Also test Play app update with an already installed v2 distro and with a newer distro payload version available.

## Real Play tests
Upload the exact AAB to Internal App Sharing/Internal track and test real Google Play delivery, including:
- on-demand download from Play servers;
- cellular/Wi-Fi confirmation behavior;
- cancellation;
- Play Store unavailable/error behavior;
- app update + pack behavior.

Do not rely only on bundletool local testing; local PAD has known differences from real Play delivery.

## Policy/Console reconciliation
Compare final AAB with:
- privacy policy;
- Data Safety;
- permissions;
- FGS declarations/video/text if required;
- store description/screenshots;
- reviewer notes.

Reviewer notes should state that distro base images are delivered via Google Play Asset Delivery and executed inside the app's PRoot Linux environment; Android-level root/chroot is not present in the Play flavor.

## Acceptance
- all automated gates green;
- all distros install from PAD and run;
- API 33–36 matrix passes;
- upgrade from v1.8p passes;
- real Internal Play delivery passes;
- no Flux-managed executable/rootfs payload is fetched by Play app from GitHub/HTTP;
- final policy/store declarations match the exact AAB;
- AAB SHA256 and evidence are recorded.

## Do not
Do not promote production `playstore` merely because the APK works when sideloaded. The exact AAB must pass real Google Play delivery testing first.