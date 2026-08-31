# Worker 02 completion report — Play flavor and payload-provider boundary

Date: 2026-08-31

## Status

**PASS**

Worker 02 is complete on playstore-v2-compliance. Common onboarding,
bootstrap extraction, terminal-session construction, PRoot launch, X11, Pulse,
state, logging, and UI paths remain in place. Payload source selection is now a
build-time flavor concern:

- **zenithblue** uses packaged bootstrap.tar or a verified local repair copy and
  accepts only verified local rootfs materialization until Worker 03 supplies
  the approved delivery path.
- **ivarna** keeps the existing pinned release downloader and remote bootstrap
  behavior behind its flavor source set.

No Play Feature Delivery/PAD implementation, root/chroot removal, loader
cleanup, manifest cleanup, or other later-worker work was included.

## Changes

Common provider contracts and verification:

- app/src/main/kotlin/com/ivarna/fluxlinux/core/install/PayloadProvider.kt
  adds RootfsPayloadProvider, HostRuntimePayloadProvider, source-neutral
  progress/results, and local SHA/size-gated materialization.
- app/src/main/kotlin/com/ivarna/fluxlinux/core/install/DistroInstallProfile.kt
  now contains common distro identity/setup and payload verification metadata;
  release URLs are no longer part of the common profile.
- app/src/main/kotlin/com/ivarna/fluxlinux/core/install/HostBootstrap.kt
  now contains host archive identity only; transport is flavor-owned.
- app/src/main/kotlin/com/ivarna/fluxlinux/core/install/OnboardingInstallRunner.kt
  consumes the rootfs provider for both proot and chroot flows and no longer
  exports FLUX_ROOTFS_URL.
- app/src/main/kotlin/com/ivarna/fluxlinux/core/terminal/BootstrapInstaller.kt
  consumes the host provider while retaining extraction, prefix rewrite, and
  installed-proot preservation.
- app/src/main/kotlin/com/ivarna/fluxlinux/core/terminal/InstallSessionFactory.kt
  exports only local rootfs path/name/SHA metadata.

Flavor boundaries:

- app/src/ivarna/kotlin/com/ivarna/fluxlinux/core/install/IvarnaPayloadProviders.kt
  wraps the existing remote rootfs/bootstrap implementation.
- app/src/ivarna/kotlin/com/ivarna/fluxlinux/core/install/RootfsDownloader.kt
  and app/src/ivarna/kotlin/com/ivarna/fluxlinux/core/install/PinnedReleaseArchive.kt
  are now ivarna-only source inputs.
- app/src/zenithblue/kotlin/com/ivarna/fluxlinux/core/install/ZenithbluePayloadProviders.kt
  provides the packaged/local-only Play placeholder; it contains no release
  URL or remote downloader reference.

Regression tests:

- app/src/test/java/com/ivarna/fluxlinux/core/install/PayloadProviderContractTest.kt
  locks the common installer/session/host boundary.
- app/src/zenithblueTest/java/com/ivarna/fluxlinux/core/install/ZenithbluePayloadProviderTest.kt
  proves Play wiring, verified local acceptance, cancellation, and fail-closed
  behavior.
- app/src/ivarnaTest/java/com/ivarna/fluxlinux/core/install/IvarnaPayloadProviderTest.kt
  proves non-Play release-provider wiring remains selected.
- Existing downloader tests moved with the ivarna-only downloader to
  app/src/ivarnaTest/java/com/ivarna/fluxlinux/core/install/RootfsDownloaderTest.kt.

## Tests

| Command | Result | Evidence / warnings |
|---|---|---|
| git submodule update --init --recursive | PASS | The 16 pinned X11 submodules were already initialized at the v2 baseline commits. |
| ./gradlew test | PASS | Both flavors and debug/release unit-test variants completed; BUILD SUCCESSFUL, 155 actionable tasks. |
| ./gradlew assembleZenithblueDebug assembleIvarnaDebug bundleZenithblueDebug | PASS | Both APKs and the Play debug AAB built from the final provider layout. |
| ./gradlew :app:dependencies | PASS | BUILD SUCCESSFUL; resolved output retains termux-app, OkHttp, Guava listenablefuture, and project :termux-x11. Metadata-only configurations include Gradle (n) markers, but the resolved runtime tree is present. |
| git diff --check | PASS | No whitespace errors. |

Build output retained pre-existing environment warnings: Android SDK XML
version mismatch/inconsistent android-37.0 package location, native-library
strip warnings, and existing Kotlin/Compose deprecation warnings. No test or
build failure was introduced by Worker 02.

## Artifact inspection

Final Play artifacts inspected:

- app/build/outputs/apk/zenithblue/debug/app-zenithblue-debug.apk
  - package com.zenithblue.fluxlinux;
  - versionCode 12, versionName 2.0.0;
  - compile/target SDK 36, min SDK 26;
  - contains assets/bootstrap.tar and all six arm64 host libraries:
    libbash.so, libloader.so, libloader32.so, libpactl.so,
    libproot.so, and libpulseaudio.so;
  - dex inspection contains PlayFeatureRootfsProvider and
    PlayFeatureHostRuntimeProvider, but no IvarnaPayloadProviders,
    RootfsDownloader, or PinnedReleaseArchive classes.
- app/build/outputs/bundle/zenithblueDebug/app-zenithblue-debug.aab
  - contains the expected base module and no dynamic feature module;
  - this is intentional because Worker 03 owns the approved PFD implementation.

Final artifact SHA-256 values:

- APK: a3019dbb1a2c84f6260a9f32fdb5b518a25b4e50e3e51072e2e2ba9cc246a1ab
- AAB: c33729bcd614fcd242e24de3275333b08309e5f825efa81bb8e5d3722443dff6

## Acceptance criteria

| Criterion | Result | Evidence |
|---|---|---|
| both flavors compile | PASS | assembleZenithblueDebug, assembleIvarnaDebug, and aggregate test pass. |
| Play still uses the embedded PRoot/terminal/X11/Pulse backend | PASS | Existing common launch/session code remains; dependency report retains embedded termux-app and :termux-x11; APK retains libproot.so, libbash.so, Pulse libraries, and loader libraries. |
| installer accepts a local payload from an abstraction | PASS | OnboardingInstallRunner calls PayloadProviders.rootfs; PlayFeatureRootfsProvider accepts only VerifiedPayloadStore results. |
| Play cannot select the non-Play remote provider | PASS | Flavor tests assert Play provider identity; final Play dex has no ivarna provider/downloader/archive classes. |
| non-Play behavior remains available behind its source/dependency boundary | PASS | Ivarna provider and downloader compile, test, and retain release URL construction only under src/ivarna. |
| no major installer/UI rewrite was introduced | PASS | Changes are limited to provider contracts, source-set wiring, URL-contract removal, and focused tests; UI and backend behavior remain common. |

## Regressions

- ivarna: release-backed rootfs and host-bootstrap behavior remains available
  through the ivarna provider; downloader tests remain green.
- Terminal, native PRoot, extraction/configuration, X11, Pulse, session state,
  logging, and distro setup code were preserved.
- Android-root/chroot code was not removed in this worker; the roadmap assigns
  Play removal to Worker 05.
- Play Feature Delivery/dynamic features were not added; Worker 03 owns that
  follow-up.

## Remaining issues

- The Play rootfs provider is intentionally local-only until Worker 03 supplies
  the approved Play delivery implementation.
- Common shell assets still contain legacy FLUX_ROOTFS_URL fallback logic;
  removing that executable fallback is Worker 03 scope.
- Android-root/chroot and related Play manifest/policy cleanup remain assigned
  to later workers.

## Next worker

docs/playstore/workers/03_remove_remote_executable_delivery.md

Worker 03 must implement the approved Play executable delivery path. Do not
start Worker 04 until Worker 03 reports PASS.
