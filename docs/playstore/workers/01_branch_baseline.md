# Worker 01 — Preserve Play Baseline and Start v2 Integration

## Goal
Create a safe v2 Play implementation branch without losing the last known Play-safe v1.8 state.

## Inputs

- old Play-safe code: `816371bba62535fc3fc3b433fba47e5dcf9bda74`;
- v2 baseline: `c83cb17e7a5d4713f8e0b931761061902e9dd345` (`v2.0.0`);
- canonical plan: `docs/playstore/v2_0_compliance_roadmap.md`;
- Play package: `com.zenithblue.fluxlinux`;
- old Play `versionCode`: `11`.

## Required outcome

- preserve the v1.8 Play state as a permanent rollback ref;
- create/use `playstore-v2-compliance` from the exact v2.0.0 commit;
- bring the current `docs/playstore/**` planning documents onto that integration branch **without merging old Play application code**;
- confirm the untouched v2 baseline build status before compliance edits;
- record package/version/SDK/ABI/Gradle baseline.

## Tasks

1. Fetch tags/branches and verify both baseline SHAs.
2. Create/push `backup/playstore-v1.8p` at `816371...` if it does not exist.
3. Create `playstore-v2-compliance` from `c83cb17...`.
4. Copy/cherry-pick only the current Play planning documentation into the integration branch. Do not merge the old `playstore` code tree wholesale.
5. Run the unmodified v2 build/tests and record pre-existing failures.
6. Record:
   - `zenithblue` application ID;
   - versionCode/versionName;
   - compileSdk/targetSdk/minSdk;
   - supported ABIs;
   - AGP/Gradle/Kotlin versions;
   - current flavor/dependency layout.
7. Create/update `docs/playstore/implementation_status.md` on the integration branch with Workers 01–10 and `pending/in-progress/pass/blocked` states.
8. Record the exact starting commit in the Worker 01 completion report.

## Tests

```bash
git rev-parse v2.0.0^{commit}
git show -s --oneline 816371bba62535fc3fc3b433fba47e5dcf9bda74
git merge-base --is-ancestor c83cb17e7a5d4713f8e0b931761061902e9dd345 HEAD
./gradlew tasks
./gradlew test
./gradlew assembleZenithblueDebug
```

Also inspect `app/build.gradle.kts` and the merged manifest inputs.

## Acceptance

- rollback branch exists remotely;
- integration branch is based on the exact v2 baseline;
- planning docs are available on the integration branch;
- no wholesale old-Play code merge occurred;
- Play package continuity is documented;
- next Play versionCode is planned as `> 11`;
- baseline build/test status is recorded honestly.

## Do not

Do not implement Play Feature Delivery, PAD, QEMU, root removal, loader changes, or unrelated cleanup in this worker.