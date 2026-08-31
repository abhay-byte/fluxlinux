# Worker 09 — CI Policy Gate

## Goal
Turn Play compliance regressions into deterministic CI/build failures.

## Do
1. Add `scripts/verify_play_policy.sh` (or equivalent).
2. Source-scan Play/main code for prohibited permissions/install APIs/direct binary URLs.
3. Build `bundleZenithblueRelease` before artifact checks.
4. Extract the AAB and reject nested APK/DEX/JAR under assets and unexpected ELF under assets.
5. Inspect merged manifest for prohibited/unused permissions/services.
6. Scan built dex/resources for rootfs release URLs, `RootfsDownloader`, root/chroot-only markers and installer markers.
7. Review `zenithblueReleaseRuntimeClasspath`; fail on explicitly forbidden non-Play dependencies.
8. Add the gate to GitHub Actions for PRs touching Android code, manifests, assets, dependencies, Fastlane or Play docs.
9. Print actionable file/match errors, not a generic failure.

## Minimum deny patterns
- `REQUEST_INSTALL_PACKAGES`
- `ACCESS_SUPERUSER`
- `canRequestPackageInstalls`
- `PackageInstaller`
- `ACTION_INSTALL_PACKAGE`
- `application/vnd.android.package-archive`
- direct `.apk/.apks/.xapk/.zip` install URLs
- `releases/download/rootfs`
- nested APK assets

## Tests
1. Gate passes on compliant branch.
2. Temporarily add each prohibited marker and prove CI fails.
3. Place a fake nested `test.apk` under Play assets and prove artifact gate fails.
4. Remove fixtures and rerun green.

## Acceptance
- One command validates Play source + final AAB.
- CI runs automatically.
- Negative fixture tests prove the gate actually catches regressions.
- Exit code is non-zero for every blocker rule.
