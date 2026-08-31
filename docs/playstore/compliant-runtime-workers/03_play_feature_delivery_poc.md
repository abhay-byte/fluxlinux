# Worker 03 — Play Feature Delivery Distro POC

## Goal

Replace the Play build's GitHub rootfs delivery with one real Google Play on-demand dynamic feature module.

Start with **Alpine or Debian only**.

## Implement

1. Add one `com.android.dynamic-feature` distro module.
2. Configure it as `dist:on-demand`.
3. Store:
   - `flux-distro-manifest.json`
   - rootfs payload
4. Add Play Feature Delivery libraries/APIs to `zenithblue` as needed.
5. Implement `PlayDistroDelivery` abstraction:
   - installed check
   - user request
   - progress
   - cancellation/error
   - module-ready result
6. Extract/copy the delivered rootfs to app-private guest storage.
7. Start it only using the interpreted engine from Workers 01–02.

## Important

Do **not** use Play Asset Delivery for the rootfs. Google's PAD docs state asset packs do not contain executable code. Use Play Feature Delivery.

Do not keep a GitHub fallback in the Play implementation.

## Real Play test required

On-demand delivery must be tested using Internal App Sharing or a Play internal testing track. A local Gradle install alone is not enough.

Verify:

1. install base app from Play-hosted build
2. distro initially absent
3. request distro
4. Play downloads module
5. distro becomes available
6. interpreted shell starts

## Tests

- installed-module state
- failed request
- cancelled request
- malformed module manifest
- wrong distro id
- no remote rootfs fallback

## Evidence

Create:

`docs/playstore/evidence/play_feature_delivery_poc.md`

## Acceptance

- [ ] One distro is a true on-demand dynamic feature.
- [ ] Module is delivered by Google Play in a real test track/share flow.
- [ ] No host GitHub rootfs request occurs.
- [ ] Delivered guest starts via QEMU/interpreter only.
- [ ] Failure does not trigger remote/non-Play fallback.
- [ ] Evidence committed.
