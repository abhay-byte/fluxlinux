# Worker 02 — Build-Time Play Capability Boundary

## Goal
Make prohibited/non-Play v2 functionality physically absent from the `zenithblue` artifact, not merely runtime-disabled.

## Do
1. Keep universally safe APIs/UI primitives in `src/main`.
2. Move embedded-host/rootfs/chroot/root implementations to `src/ivarna`.
3. Add Play-safe implementations in `src/zenithblue` for shared interfaces.
4. Preserve `applicationId = com.zenithblue.fluxlinux` for Play.
5. Flavor-scope embedded host dependencies where possible (`ivarnaImplementation`).
6. Keep Play UI coherent: hide excluded actions or mark them unavailable in the Google Play build.
7. Do not use a runtime-only `BuildConfig` guard for blocker-class features.

## Tests
```bash
./gradlew :app:dependencies --configuration zenithblueReleaseRuntimeClasspath
./gradlew :app:dependencies --configuration ivarnaReleaseRuntimeClasspath
./gradlew :app:testZenithblueDebugUnitTest
./gradlew :app:assembleZenithblueDebug
```
Inspect the Play runtime classpath and APK for embedded-host-only modules/classes.

## Acceptance
- Play and non-Play implementations are source-set separated.
- Play dependency graph does not include runtime engines used only by F-Droid/GitHub.
- Excluded features are not reachable or packaged in Play.
- Both flavors compile after the split.
