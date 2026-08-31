# Worker 02 — Build-Time Play Flavor and Payload-Provider Boundary

## Goal
Keep FluxLinux v2's embedded terminal/PRoot/X11/Pulse backend while making executable/runtime delivery and Android-root behavior impossible to select incorrectly in the Play flavor.

## Architecture rule

- `zenithblue` remains embedded; do **not** fall back to external Termux.
- same-architecture PRoot remains common.
- policy-sensitive **providers and root/chroot integrations** are separated at build time.
- Worker 03 will implement Play Feature Delivery; this worker creates the seam only.

## Code to inspect first

- `DistroInstallProfile`;
- `RootfsDownloader`;
- `OnboardingInstallRunner`;
- `HostBootstrap`;
- `TerminalLauncher` / `TermuxHostPaths`;
- root/chroot managers;
- Gradle flavor/source-set configuration.

## Tasks

1. Inventory v2 classes/resources/dependencies and classify each as `common`, `zenithblue`, or `ivarna`.
2. Introduce a narrow `RootfsPayloadProvider` that returns/observes a **local verified/materializable payload**, rather than teaching the whole installer about Play APIs.
3. Introduce an equivalent `HostRuntimePayloadProvider` if `HostBootstrap` currently mixes bundled and remote executable repair sources.
4. Keep extraction, configuration, PRoot launch, X11, Pulse, session state, logging, and UI/business logic common where safe.
5. `zenithblue` must resolve only Play/local provider implementations. Add a placeholder `PlayFeatureRootfsProvider` / `PlayFeatureHostRuntimeProvider` if Worker 03 has not implemented them yet.
6. `ivarna` may retain `RootfsDownloader` and current remote bootstrap behavior.
7. Split distro metadata so common identity/setup information is not coupled to Play-visible GitHub rootfs URLs.
8. Remove the need for Play code to export/use `FLUX_ROOTFS_URL`; retain path/name/SHA metadata.
9. Move Android-root/chroot implementation behind the non-Play boundary. Worker 05 performs the full removal from the Play artifact.
10. Keep embedded terminal/X11 dependencies required by the v2 Play backend. Do not remove them because of old v1.8 assumptions.
11. Add unit tests proving a `zenithblue` build cannot instantiate the HTTP rootfs/bootstrap provider.
12. Add compile coverage for both flavors.

## Tests

```bash
./gradlew test
./gradlew assembleZenithblueDebug
./gradlew assembleIvarnaDebug
./gradlew :app:dependencies
```

Static verification for `zenithblue` must show:

- no provider wiring to `RootfsDownloader`;
- no executable bootstrap HTTP fallback selected;
- no `FLUX_ROOTFS_URL` dependency in the Play install contract.

## Acceptance

- both flavors compile;
- Play still uses the embedded PRoot/terminal/X11/Pulse backend;
- installer accepts a local payload from an abstraction;
- Play cannot select the non-Play remote provider;
- non-Play behavior remains available behind its source/dependency boundary;
- no major installer/UI rewrite was introduced.

## Do not

Do not implement PAD. Do not implement the dynamic-feature modules yet. Do not remove PRoot or convert it to QEMU.