# Worker 01 — Branch Baseline & Rollback

## Goal
Create a safe v2 Play integration baseline without destroying the last known compliant v1.8p state.

## Do
1. Preserve `playstore@816371bba62535fc3fc3b433fba47e5dcf9bda74` as `backup/playstore-v1.8p`.
2. Create `playstore-v2-compliance` from tag `v2.0.0` / commit `c83cb17e7a5d4713f8e0b931761061902e9dd345`.
3. Confirm Play package continuity: `com.zenithblue.fluxlinux` and next `versionCode > 11`.
4. Do **not** merge the old `playstore` branch wholesale. Record v1.8p changes that must be selectively ported.
5. Add a short branch README/status note pointing to `docs/playstore/v2_0_compliance_roadmap.md`.

## Tests
```bash
git merge-base --is-ancestor c83cb17e7a5d4713f8e0b931761061902e9dd345 HEAD
git log -1 --oneline backup/playstore-v1.8p
grep -R 'com.zenithblue.fluxlinux' app/build.gradle.kts app/src || true
./gradlew tasks --all | grep -i zenithblue
```

## Acceptance
- Backup branch exists remotely at the exact old Play commit.
- Integration branch is based on v2.0.0.
- No force-push/move of `playstore` occurred.
- Package/version migration strategy is documented.
- Repository still configures successfully with Gradle.
