# Worker 02 — Play Flavor Boundary Without Removing Embedded Backend

## Goal
Keep FluxLinux v2's embedded terminal/PRoot/X11/Pulse backend in Google Play while isolating **delivery and Android-root behavior** by flavor.

## Architecture rule
Do not create an external-Termux Play fallback. The Play build should remain embedded.

Common backend stays in `main` where safe. Policy-sensitive providers get flavor implementations.

## Tasks
1. Inventory these v2 components and classify `common`, `play-only adapter`, or `non-play-only`:
   - distro installer/state machine;
   - rootfs downloader/source;
   - host bootstrap source;
   - PRoot host runtime;
   - terminal/session services;
   - X11/Pulse integration;
   - chroot/root managers.
2. Introduce a narrow payload-source abstraction, e.g. `RootfsPayloadProvider`.
3. Keep extraction/configuration/start logic common; it should receive a **local archive** and not care whether it came from PAD or HTTP.
4. `zenithblue`: wire a placeholder/real `PlayAssetRootfsProvider` interface for worker 03.
5. `ivarna`: retain the current remote provider if desired.
6. Separate bootstrap source the same way if current common code contains a remote repair URL.
7. Move real Android-root/chroot dependencies behind the non-Play source set; worker 05 completes removal.
8. Ensure the embedded Termux/terminal/X11 modules required by Play remain dependencies. Do not flavor-scope them out merely because the old roadmap said so.
9. Add unit tests proving the Play flavor cannot select the remote rootfs provider.

## Tests
```bash
./gradlew test
./gradlew assembleZenithblueDebug
./gradlew assembleIvarnaDebug
./gradlew dependencies
```

Search Play sources/artifact for provider wiring that points to HTTP/GitHub rootfs delivery.

## Acceptance
- both flavors compile;
- Play still contains embedded terminal/PRoot backend wiring;
- distro installer consumes a local payload through an abstraction;
- Play cannot instantiate the non-Play HTTP rootfs provider;
- no major UI/backend rewrite was introduced.

## Do not
Do not implement the asset packs themselves here. Do not remove PRoot, X11, PulseAudio, terminal services, or same-architecture guest execution.