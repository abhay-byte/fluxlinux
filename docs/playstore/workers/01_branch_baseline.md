# Worker 01 — Preserve Play Baseline and Start v2 Integration

## Goal
Create a safe v2 Play work branch without losing the last known Play-safe v1.8 state.

## Required outcome
- preserve `playstore@816371bba62535fc3fc3b433fba47e5dcf9bda74` as a permanent rollback ref;
- create/use `playstore-v2-compliance` from `v2.0.0` (`c83cb17e7a5d4713f8e0b931761061902e9dd345`);
- preserve Play package identity `com.zenithblue.fluxlinux`;
- next Play `versionCode` must remain greater than 11;
- do **not** move production `playstore` to v2 yet.

## Tasks
1. Fetch tags/branches and confirm both baseline SHAs.
2. Create and push `backup/playstore-v1.8p` at the old Play code commit if it does not already exist.
3. Create `playstore-v2-compliance` from the exact v2 tag/commit.
4. Confirm the v2 tree builds before policy refactoring.
5. Record current `zenithblue` application ID, version code/name, target SDK, min SDK, supported ABIs, and Gradle/AGP versions in the worker commit/notes.
6. Confirm no code from the old Play branch is wholesale-merged. Future workers should port only still-required policy fixes.
7. Add a short `docs/playstore/implementation_status.md` if no equivalent exists, listing workers 01–10 and statuses (`pending/in-progress/done`).

## Tests
```bash
git merge-base --is-ancestor v2.0.0 playstore-v2-compliance
git log --oneline --decorate -n 10
./gradlew tasks
./gradlew test
./gradlew assembleZenithblueDebug
```

Inspect Gradle output/config and verify `com.zenithblue.fluxlinux` remains the Play application ID.

## Acceptance
- rollback branch exists remotely;
- integration branch is based on v2.0.0;
- Play package continuity is documented;
- baseline tests/build complete or any pre-existing failures are documented precisely;
- production `playstore` has not been force-moved to v2.

## Do not
Do not implement PAD, remove features, change UI architecture, or perform unrelated cleanup in this worker.