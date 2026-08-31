# Worker 01 completion report — branch baseline

Date: 2026-08-31

## Worker

`docs/playstore/workers/01_branch_baseline.md`

## Result

`PARTIAL`

The required integration branch, rollback ref, planning documentation, and
baseline records are established. The untouched exact v2 source baseline does
not build in this checkout because required generated/release inputs are absent;
those failures are recorded below and no later-worker code was imported to mask
them.

## What changed

Only planning/baseline documentation was added or copied:

- `docs/playstore/**` — current planning documents copied from the fetched
  `playstore` branch onto the v2 integration branch.
- `docs/playstore/implementation_status.md` — Worker 01–10 state tracking.
- `docs/playstore/worker_reports/01_branch_baseline_report.md` — this report.

No Kotlin, Java, native, Gradle, manifest, dependency, flavor, or runtime code
was changed. No Play Feature Delivery, PAD, QEMU, root/chroot, loader, callback,
foreground-service, privacy, or CI implementation was added.

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

All commands below ran from the untouched v2 checkout before the documentation
records were added.

| Command | Result | Evidence / important output |
|---|---|---|
| `git rev-parse v2.0.0^{commit}` | PASS | Resolved to `c83cb17e7a5d4713f8e0b931761061902e9dd345`. |
| `git show -s --oneline 816371bba62535fc3fc3b433fba47e5dcf9bda74` | PASS | Verified the v1.8-era Play-safe reference. |
| `./gradlew --version` | PASS | Gradle `8.14`, Kotlin runtime `2.0.21`, Java 17. |
| `./gradlew tasks --all` | PASS | Actual flavor tasks include `app:assembleZenithblueDebug`, `app:assembleIvarnaDebug`, and both flavor unit-test tasks. |
| `./gradlew clean` | PASS | All project clean tasks completed. |
| `./gradlew test` | FAIL (pre-existing) | `:app:packageHostAssetsZenithblue` fails because `native/bootstrap/com.zenithblue.fluxlinux/bootstrap.tar` is absent. Unit tests do not complete. |
| `./gradlew assembleZenithblueDebug` | FAIL (pre-existing) | Same missing `bootstrap.tar` failure in `:app:packageHostAssetsZenithblue`. |
| `./gradlew assembleIvarnaDebug` | FAIL (pre-existing) | `:termux-x11:configureCMakeDebug[arm64-v8a]` fails because `termux-x11/src/main/cpp/xorgproto/include/X11/Xpoll.h.in` and related generated patch inputs are absent. |

Important warnings observed during the attempted non-Play build include Android
SDK XML version mismatch (`CXX5304`), inconsistent `android-37.0` SDK package
location, deprecated Java/Kotlin APIs, and failed patch hunks caused by the
missing X11 source inputs. These are not caused by Worker 01's documentation.

## Artifact inspection

No `zenithblue` APK/AAB was generated: `assembleZenithblueDebug` stops in
`packageHostAssetsZenithblue` before packaging. Source/config inspection verified
the Play application ID, version, SDK values, flavor wiring, manifest inputs,
and arm64 host-library inventory. Final Play artifact checks are not testable
until the pre-existing bootstrap/source-input failures are resolved.

## Acceptance criteria

| Criterion | Result | Evidence |
|---|---|---|
| Rollback branch exists remotely | NOT TESTED | Local `backup/playstore-v1.8p` exists at the verified old reference; remote push is pending this commit. |
| Integration branch is based on the exact v2 baseline | PASS | `playstore-v2-compliance` starts at `c83cb17e7a5d4713f8e0b931761061902e9dd345`, matching `v2.0.0^{commit}`. |
| Planning docs are available on the integration branch | PASS | Current `docs/playstore/**` tree was restored from `playstore`; Worker 01 records and status file are included. |
| No wholesale old-Play code merge occurred | PASS | Diff is documentation-only; no application source/config files were changed. |
| Play package continuity is documented | PASS | `com.zenithblue.fluxlinux` is recorded above and in `implementation_status.md`. |
| Next Play versionCode is planned as `> 11` | PASS | v2 baseline is `versionCode 12`, and the report records the old Play value as `11`. |
| Baseline build/test status is recorded honestly | PASS | All required commands attempted; failures and pre-existing missing inputs are recorded without claiming a build artifact. |

The first criterion will be rechecked after the focused commit is pushed. The
overall result remains `PARTIAL` until the baseline build inputs are restored or
the repository owner explicitly accepts the documented pre-existing failures.

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
- The baseline checkout is missing generated/release inputs needed by the
  existing host-asset and X11 builds: `bootstrap.tar` for Zenithblue and the
  X11 `Xpoll.h.in` source tree/patch inputs. Worker 01 does not import
  post-v2 code or invent a compliance fix for these inputs.

## Commit

Pending until the focused Worker 01 documentation commit is created and the
rollback ref is pushed.

## Next worker

`docs/playstore/workers/02_play_flavor_boundary.md`

Do not begin Worker 02 while this report remains `PARTIAL`.
