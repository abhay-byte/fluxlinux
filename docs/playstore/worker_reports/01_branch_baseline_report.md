# Worker 01 completion report — branch baseline

Date: 2026-08-31

## Worker

`docs/playstore/workers/01_branch_baseline.md`

## Result

`PASS`

The required integration branch, rollback ref, planning documentation, and
baseline records are established. The exact v2 source baseline builds after
following its documented preparation path: initialize the pinned X11
submodules and generate the ignored `bootstrap.tar` from tracked package
outputs. No later-worker code was imported.

## What changed

Only planning/baseline documentation was added or copied:

- `docs/playstore/**` — current planning documents copied from the fetched
  `playstore` branch onto the v2 integration branch.
- `docs/playstore/implementation_status.md` — Worker 01–10 state tracking.
- `docs/playstore/worker_reports/01_branch_baseline_report.md` — this report.

The 26 copied planning files are:

- `docs/playstore/README.md`
- `docs/playstore/WORKER_PROMPT.md`
- `docs/playstore/full_v2_compliant_delivery_execution_roadmap.md`
- `docs/playstore/policies_and_violations.md`
- `docs/playstore/privacy_policy.md`
- `docs/playstore/v2_0_compliance_roadmap.md`
- `docs/playstore/compliant-runtime-workers/01_qemu_user_poc.md`
- `docs/playstore/compliant-runtime-workers/02_engine_abstraction.md`
- `docs/playstore/compliant-runtime-workers/03_play_feature_delivery_poc.md`
- `docs/playstore/compliant-runtime-workers/04_interpreter_only_sandbox.md`
- `docs/playstore/compliant-runtime-workers/05_package_manager_validation.md`
- `docs/playstore/compliant-runtime-workers/06_play_distro_modules.md`
- `docs/playstore/compliant-runtime-workers/07_terminal_desktop_audio.md`
- `docs/playstore/compliant-runtime-workers/08_gpu_mediation.md`
- `docs/playstore/compliant-runtime-workers/09_ci_and_compliance_evidence.md`
- `docs/playstore/compliant-runtime-workers/10_internal_play_validation.md`
- `docs/playstore/workers/01_branch_baseline.md`
- `docs/playstore/workers/02_play_flavor_boundary.md`
- `docs/playstore/workers/03_remove_remote_executable_delivery.md`
- `docs/playstore/workers/04_remove_nested_and_writable_executables.md`
- `docs/playstore/workers/05_remove_root_chroot_from_play.md`
- `docs/playstore/workers/06_links_permissions_and_callbacks.md`
- `docs/playstore/workers/07_foreground_services.md`
- `docs/playstore/workers/08_privacy_and_store_metadata.md`
- `docs/playstore/workers/09_ci_policy_gate.md`
- `docs/playstore/workers/10_release_validation.md`

No Kotlin, Java, native, Gradle, manifest, dependency, flavor, or runtime code
was changed. No Play Feature Delivery, PAD, QEMU, root/chroot, loader, callback,
foreground-service, privacy, or CI implementation was added.

The required build preparation produced only ignored local inputs: initialized
submodules, generated `native/bootstrap/com.zenithblue.fluxlinux/bootstrap.tar`,
and Gradle staging/build outputs. These are not part of the tracked Worker 01
diff.

## Baseline verified

### Branch and history

- Current integration branch: `playstore-v2-compliance`.
- Starting `HEAD`: `c83cb17e7a5d4713f8e0b931761061902e9dd345`.
- `v2.0.0^{commit}` resolves to
  `c83cb17e7a5d4713f8e0b931761061902e9dd345`.
- The v2 commit subject is `release: v2.0.0 notes and fix X11 LorieView JNI for nativeInit()J`.
- The old Play-safe reference resolves to
  `816371bba62535fc3fc3b433fba47e5dcf9bda74` (`feat(playstore): onboarding resources + prereq warnings`).
- `backup/playstore-v1.8p` was created at the old Play-safe reference and is
  intended as the permanent rollback ref.
- `playstore` is on the separate v1.8-era documentation line and does not
  contain the v2 baseline.
- `main` contains the v2 baseline as an ancestor but is three commits ahead of
  it, so no post-v2 `main` code was imported.
- The exact v2 source commit has no `docs/playstore/**` tree; the 26 current
  planning files were copied from `playstore` without merging its application
  code tree.

### Android and Gradle configuration at the v2 baseline

- Play namespace/application ID: `com.zenithblue.fluxlinux`.
- Non-Play application ID: `com.ivarna.fluxlinux`.
- `versionCode`: `12`.
- `versionName`: `2.0.0`.
- `compileSdk`: `36`.
- `targetSdk`: `36`.
- `minSdk`: `26`.
- Flavors: `ivarna` and `zenithblue`, in the `store` flavor dimension.
- Host native asset inventory: arm64-v8a (`libbash.so`, `libloader.so`,
  `libloader32.so`, `libpactl.so`, `libproot.so`, `libpulseaudio.so`) for each
  application ID. The `ivarna` flavor explicitly filters to `arm64-v8a`;
  `termux-x11` also filters to `arm64-v8a`.
- Android Gradle Plugin: `8.7.3`.
- Kotlin Gradle plugin: `2.0.20`.
- Gradle wrapper: `8.14`.
- JVM/toolchain observed: Java 17.
- Common app dependencies include AndroidX/Compose, Haze, Accompanist
  permissions, OkHttp, embedded Termux app, `listenablefuture`, and the local
  `:termux-x11` project. `:termux-x11` uses the local `:stub` compile-only
  module and an external CMake build.

### Manifest baseline

The untouched v2 main manifest contains:

- Permissions: `com.termux.permission.RUN_COMMAND`, `INTERNET`,
  `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_SPECIAL_USE`, and
  `ACCESS_SUPERUSER`.
- Package queries: `com.termux` and `com.termux.x11`.
- A browsable `fluxlinux://callback` deep link on `MainActivity`.
- Services: `InstallServerService` (`dataSync`), `BaseInstallService`
  (`dataSync`), `AppTerminalService` (`specialUse`), and
  `DesktopSessionService` (`specialUse`).

These are baseline findings for later workers. They were not changed by Worker
01.

## Validation

All source/configuration commands and the initial build attempts ran from the
exact v2 checkout before any application code was changed. The final build
attempts ran after the repository's supported local preparation steps below;
those steps do not change tracked source.

| Command | Result | Evidence / important output |
|---|---|---|
| `git rev-parse v2.0.0^{commit}` | PASS | Resolved to `c83cb17e7a5d4713f8e0b931761061902e9dd345`. |
| `git show -s --oneline 816371bba62535fc3fc3b433fba47e5dcf9bda74` | PASS | Verified the v1.8-era Play-safe reference. |
| `./gradlew --version` | PASS | Gradle `8.14`, Kotlin runtime `2.0.21`, Java 17. |
| `./gradlew tasks --all` | PASS | Actual flavor tasks include `app:assembleZenithblueDebug`, `app:assembleIvarnaDebug`, and both flavor unit-test tasks. |
| `git submodule update --init --recursive` | PASS | All 16 X11 submodules checked out at the commits recorded by the exact v2 baseline. |
| `python3 scripts/assemble_bootstrap.py --package-name com.zenithblue.fluxlinux --mode full` | PASS | Generated and verified the ignored 123.42 MB bootstrap; SHA-256 `3ffef7f92820341e2a74b739fb15695a16fe4622e80cfc81d18bd98461712609`. |
| `./gradlew clean` | PASS | All project clean tasks completed. |
| `./gradlew test` (before preparation) | FAIL (pre-existing input) | Stopped at `:app:packageHostAssetsZenithblue` because the ignored generated `bootstrap.tar` was absent. |
| `./gradlew assembleZenithblueDebug` (before preparation) | FAIL (pre-existing input) | Same missing ignored `bootstrap.tar` failure. |
| `./gradlew assembleIvarnaDebug` (before preparation) | FAIL (pre-existing input) | Stopped at X11 CMake configuration because the pinned submodules were not initialized. |
| `./gradlew assembleZenithblueDebug` (prepared) | PASS | APK assembled successfully; host bootstrap staged and X11 native library compiled. |
| `./gradlew test` (prepared) | PASS | All project test tasks completed successfully (`BUILD SUCCESSFUL`, 155 actionable tasks). |
| `./gradlew assembleIvarnaDebug` (prepared) | PASS | Non-Play debug APK assembled successfully. |
| `./gradlew bundleZenithblueDebug` (prepared) | PASS | Play debug AAB assembled successfully. |
| `apkanalyzer`/`aapt2` manifest and ZIP inspection | PASS | APK package `com.zenithblue.fluxlinux`, version `12 / 2.0.0`, min SDK 26, target SDK 36; expected base bootstrap and arm64 native payloads present. |

Important non-fatal warnings include Android SDK XML version mismatch (`CXX5304`),
an inconsistent `android-37.0` SDK package location, deprecated Java/Kotlin APIs,
native compiler warnings, and Gradle's inability to strip several native
libraries (they were packaged unstripped). These are pre-existing baseline
warnings and are not caused by Worker 01's documentation.

## Artifact inspection

Generated artifact inspection passed:

- APK: `app/build/outputs/apk/zenithblue/debug/app-zenithblue-debug.apk`,
  177,970,728 bytes, SHA-256
  `d5225cc9622a996577a4dfb4580436c8cdc175549e88dc18d569f003c7ba4087`.
- AAB: `app/build/outputs/bundle/zenithblueDebug/app-zenithblue-debug.aab`,
  82,778,819 bytes, SHA-256
  `b24fd26bbd9ffef0c1435350e9be7dcd424dd62c29633ddf9909e99e12e9d087`.
- APK manifest: package `com.zenithblue.fluxlinux`, version `12 / 2.0.0`,
  min SDK 26, target SDK 36.
- APK/AAB payload: `assets/bootstrap.tar` and the six expected
  `lib/arm64-v8a/` host libraries are present.
- AAB modules: only `base`, `BundleConfig.pb`, and metadata; dynamic features
  are intentionally not present until later workers.

## Acceptance criteria

| Criterion | Result | Evidence |
|---|---|---|
| Rollback branch exists remotely | PASS | `origin/backup/playstore-v1.8p` was pushed at `816371bba62535fc3fc3b433fba47e5dcf9bda74`. |
| Integration branch is based on the exact v2 baseline | PASS | `playstore-v2-compliance` starts at `c83cb17e7a5d4713f8e0b931761061902e9dd345`, matching `v2.0.0^{commit}`. |
| Planning docs are available on the integration branch | PASS | Current `docs/playstore/**` tree was restored from `playstore`; Worker 01 records and status file are included. |
| No wholesale old-Play code merge occurred | PASS | Diff is documentation-only; no application source/config files were changed. |
| Play package continuity is documented | PASS | `com.zenithblue.fluxlinux` is recorded above and in `implementation_status.md`. |
| Next Play versionCode is planned as `> 11` | PASS | v2 baseline is `versionCode 12`, and the report records the old Play value as `11`. |
| Baseline build/test status is recorded honestly | PASS | All required commands attempted; failures and pre-existing missing inputs are recorded without claiming a build artifact. |

All Worker 01 acceptance criteria are satisfied. The initial unprepared build
failures were caused by documented local preparation inputs, and the prepared
exact v2 baseline passes the required builds/tests.

## Diff review

Worker 01 remained within scope. The diff contains planning documentation and
baseline records only. There is no QEMU architecture, PAD implementation,
dynamic-feature implementation, remote-delivery removal, root/chroot removal,
loader change, callback change, FGS redesign, privacy rewrite, or CI policy gate.

## Issues discovered for later workers

- Worker 02 must establish a build-time Play flavor boundary; the baseline
  manifest and common app currently retain external-Termux queries/permission,
  `ACCESS_SUPERUSER`, callback handling, and legacy service declarations.
- Worker 03 must replace the Play remote bootstrap/rootfs delivery path with the
  canonical PFD provider boundary; the baseline Gradle comments and installer
  still describe/download remote assets.
- Worker 04 must audit the nested `loader.apk`/`loader.bin` pattern and all
  writable executable paths.
- Worker 05 must separate Android-level root/chroot code from the Play flavor
  while preserving native PRoot fake-root behavior.
- A fresh checkout must initialize the 16 pinned X11 submodules and generate
  the ignored `bootstrap.tar` from tracked package outputs before building;
  this is a baseline preparation requirement, not a later-worker compliance
  change.

## Commit

- Initial Worker 01 documentation commit:
  `b9029a9d3ba51bac473bfbcdce0bb2ed311b08b6`.
- Follow-up report/status evidence commit: `12e33fc39e7a8bbd0cb1483d8555c2fd2f5f76e2`.
- Final validation/report update commit:
  `0a53096accce549bd18d756b073b5d6350e95b05`.
- Published integration branch: `origin/playstore-v2-compliance`.
- Published rollback branch: `origin/backup/playstore-v1.8p`.

## Next worker

`docs/playstore/workers/02_play_flavor_boundary.md`

Do not begin Worker 02 in this task; it is the next separately assigned worker.
