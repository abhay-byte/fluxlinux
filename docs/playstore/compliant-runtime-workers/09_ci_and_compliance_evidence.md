# Worker 09 — CI Policy Gates and Compliance Evidence

## Goal

Turn the execution/delivery architecture into enforceable release gates and reviewer evidence.

## CI source checks

Fail Play builds if they introduce/reintroduce:

- `REQUEST_INSTALL_PACKAGES`
- `ACCESS_SUPERUSER`
- Android chroot/root integration
- direct third-party APK/ZIP installer paths
- Play host rootfs GitHub downloader
- host execution-engine download/update URL
- guest path passed to direct exec APIs
- guest `.so` passed to host loader APIs
- native fallback from interpreted engine

## Artifact checks

Build `zenithblueRelease` AAB and inspect generated APK/splits using bundletool.

Verify:

- expected dynamic feature modules
- expected app id/version
- no nested APK
- only approved native engine libs
- exact permissions/services
- no external engine updater
- no direct native guest executor in Play runtime graph where it should be flavor-excluded

## Dependency check

Review `zenithblueReleaseRuntimeClasspath` and native dependencies.

## Evidence docs

Create/update:

```text
docs/playstore/evidence/architecture.md
docs/playstore/evidence/execution-boundary.md
docs/playstore/evidence/delivery-flow.md
docs/playstore/evidence/package-manager-flow.md
docs/playstore/evidence/permissions.md
docs/playstore/evidence/artifact-scan.md
docs/playstore/evidence/reviewer-notes.md
```

Reviewer notes must explain the VM/interpreter exception accurately without claiming Google approval that has not been granted.

## Acceptance

- [ ] CI fails on simulated direct-exec regression.
- [ ] CI fails on simulated remote engine download regression.
- [ ] AAB/split inspection passes.
- [ ] Evidence package describes actual implementation.
- [ ] No evidence statement overclaims compliance approval.
