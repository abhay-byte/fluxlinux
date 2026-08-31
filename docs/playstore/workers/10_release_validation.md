# Worker 10 — Final AAB, Play Feature Delivery, Upgrade, and Policy Validation

## Goal
Prove the exact `zenithblue` AAB is technically sound, upgrades safely, receives Flux-managed executable/runtime payloads only through Google Play, and matches every Play Console/store declaration.

## Inherited Worker 04 release blocker

The inspected arm64 base APK produced Android's exact warning: **“This app
isn’t 16 KB compatible. ELF alignment check failed.”** Affected libraries and
first `LOAD` alignments were:

`libtermux.so=0x1000`, `libpulseaudio.so=0x4000`, `libproot.so=0x4000`,
`libpactl.so=0x4000`, `libloader32.so=0x1000`, `libloader.so=0x4000`,
`libbash.so=0x4000`, `libandroidx.graphics.path.so=0x4000`, and
`libXlorie.so=0x4000`.

This is a release blocker for Worker 10. The exact tested AAB cannot be
accepted for release until every affected library is rebuilt or supplied with
verified 16 KB-compatible alignment and the warning is absent.

## Build gates

Run from a clean checkout of the implementation branch:

```bash
./gradlew clean test lintZenithblueRelease bundleZenithblueRelease
./scripts/verify_play_policy.sh
```

Use discovered task names if variants differ. Record:

- source commit;
- Gradle/AGP/JDK versions;
- final AAB path/size;
- final AAB SHA-256;
- policy-gate output.

## 1. Bundle/PFD inspection

Inspect the final AAB and generated APK set. Confirm:

- package is `com.zenithblue.fluxlinux`;
- `versionCode > 11`;
- current target SDK requirement is met;
- supported ABIs are intentional;
- `:runtime_host` is on demand;
- every supported distro has the expected on-demand dynamic feature;
- all feature sizes are within current Play limits;
- provenance/checksums match;
- no rootfs/bootstrap executable HTTP/GitHub fallback remains;
- no nested/disguised APK;
- no `REQUEST_INSTALL_PACKAGES` or `ACCESS_SUPERUSER`;
- no obsolete external-Termux bridge/permission/query;
- final FGS declarations match Worker 07.

## 2. Local dynamic-feature testing

Use the current official bundletool/local-testing procedure for App Bundles with dynamic features. Do not assume the old PAD test syntax is correct without checking current bundletool docs.

Prove from a clean install:

1. base app starts with optional runtime/distro features absent;
2. selecting the POC distro requests `runtime_host` if needed;
3. selecting distro requests its feature;
4. progress/error/cancel state is shown correctly;
5. payload materializes into app-private staging;
6. SHA verification succeeds;
7. existing extraction/configuration runs;
8. PRoot starts;
9. terminal/X11/Pulse work.

## 3. Device/API matrix

Test API 33, 34, 35 and 36 on representative supported ABIs/devices.

For at least one distro on every API level, and **every supported distro before release**, test:

- feature request/download/install;
- cancellation/retry;
- offline/network-loss path;
- insufficient-storage path;
- Play/module unavailable path where practical;
- process death during feature request and during local materialization/extraction;
- checksum failure handling using a controlled test fixture;
- extraction/configuration;
- terminal start;
- common guest commands;
- PRoot fake root;
- desktop/X11;
- PulseAudio;
- stop/restart;
- app/device reboot;
- distro uninstall/reinstall/repair;
- app update with existing installed distro.

## 4. Host/runtime tests

Verify:

- `runtime_host` installs before host setup requires it;
- API-36 W^X-safe direct launcher paths use Play-installed native-library locations;
- host bootstrap can be re-materialized/repaired from PFD without HTTP fallback;
- nested X11 loader APK is absent and replacement X11 integration works;
- no writable direct-exec host fallback is triggered.

## 5. Root/security tests

On both rooted and unrooted Android devices when possible:

- Play always selects PRoot, never real chroot;
- FluxLinux never requests Android superuser;
- guest `id -u`/`whoami` may report root through PRoot fake root;
- Android host process remains normal application UID;
- no Magisk/KernelSU/APatch flow appears;
- callbacks/deep links cannot mutate install state without the Worker 06 security protocol; if callback was removed, confirm no BROWSABLE handler remains.

## 6. Guest package-manager review test

For at least Debian/Ubuntu or Alpine, perform an explicit user-initiated package-manager smoke test, for example:

```text
apt update / apk update
install a small package
run the installed command
```

Record:

- exact action;
- network destination/repository class;
- resulting guest executable behavior;
- confirmation that Android app code did not use that repository as a hidden self-update/repair path;
- confirmation that the guest stayed under the app sandbox/PRoot environment.

This test is also a **policy-review gate**, because PRoot package managers are practical precedent but not a written blanket exemption.

## 7. Upgrade from existing Play build

Upgrade from v1.8p `versionCode 11` to the new build and verify:

- package/update accepted;
- app data preserved as intended;
- old external-Termux/prerequisite state is migrated or ignored safely;
- no dead onboarding state;
- new `runtime_host`/distro features can be requested;
- user files/settings preserved where intended;
- obsolete permissions/services do not remain functionally required.

Also test subsequent new-v2 -> newer-v2 update with already extracted distros and changed payload versions.

## 8. Real Google Play test — mandatory

Upload the **same AAB hash** to Internal App Sharing or Internal testing and test real Play delivery.

Validate:

- clean base install from Play;
- on-demand `runtime_host` from Play servers;
- on-demand distro feature from Play servers;
- cancellation/retry;
- cellular/Wi-Fi/user-confirmation behavior where applicable;
- Play Store unavailable/error recovery;
- app update + optional feature behavior;
- feature version update behavior;
- uninstall/reinstall/repair.

Do not treat sideload/bundletool success as proof of real PFD behavior.

## 9. Console/store reconciliation

Compare the exact tested AAB to:

- privacy policy;
- Data Safety;
- permissions;
- FGS declarations and any required supporting material;
- target audience/content rating as applicable;
- store title/short/full descriptions;
- screenshots;
- release notes;
- reviewer notes.

Reviewer notes must describe PFD + PRoot factually and disclose user-initiated guest package-manager networking. Do not call PRoot a VM/interpreter or cite Acode as a guarantee of approval.

## Release acceptance

All must be true:

- automated gates green;
- final AAB SHA recorded;
- API 33–36 matrix passes;
- all supported distro features install/run;
- real Internal Play PFD succeeds;
- v1.8p upgrade succeeds;
- PRoot fake root works without Android root;
- terminal/X11/Pulse work;
- no Flux-managed Play executable/rootfs/bootstrap payload is fetched from GitHub/HTTP;
- no nested loader APK/install-package/root permission remains;
- callback/bridge security tests pass;
- guest package-manager test is recorded and Internal/Closed review has not produced a blocking policy objection;
- Console/store declarations match the exact AAB.

Only then may `playstore-v2-compliance` be reviewed/merged or promoted toward the production Play branch/track.

## Do not

Do not promote because a debug APK works. Do not change the AAB after Internal testing without rerunning the relevant gates and recording the new SHA.
