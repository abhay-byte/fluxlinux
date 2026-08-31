# Worker 10 — Final Release Validation

## Goal
Prove the final Play AAB is technically stable, policy-clean, upgrade-safe, and ready for Play Console submission.

## Do
1. Run clean unit/lint/release-bundle gates for `zenithblue`.
2. Run `scripts/verify_play_policy.sh` against the final release AAB.
3. Inspect application ID, versionCode, target SDK, ABI/native libs, permissions, services, providers and queries.
4. Use bundletool to generate device/universal APKs and install them.
5. Test API 33, 34, 35 and 36; API 36 is mandatory.
6. Test fresh install and upgrade from Play v1.8p/versionCode 11.
7. Test onboarding, prerequisites, every Play-supported install/start/stop flow, background transitions, cancel, no-network, notification denied, process recreation and callback spoof cases.
8. Confirm no Play UI can download remote executable rootfs/bootstrap, install APKs, request root, or open binary install links.
9. Reconcile final AAB with privacy/Data Safety/FGS/store metadata one last time.
10. Submit first to internal/closed testing and review Play pre-launch report before production.

## Commands
```bash
./gradlew clean
./gradlew :app:testZenithblueDebugUnitTest
./gradlew :app:lintZenithblueRelease
./gradlew :app:bundleZenithblueRelease
./scripts/verify_play_policy.sh
```
Record exact bundle SHA256 and versionCode in the release report.

## Acceptance
- All automated gates green.
- Device matrix green with evidence.
- Upgrade from versionCode 11 preserves expected user path.
- No blocker/high compliance finding remains.
- Privacy/Data Safety/FGS/store declarations match final AAB.
- Pre-launch report reviewed with no unresolved policy/security blocker.
- Release report identifies the exact AAB SHA256 submitted to Play.
