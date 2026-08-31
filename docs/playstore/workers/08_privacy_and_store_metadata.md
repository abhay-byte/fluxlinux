# Worker 08 — Privacy, Data Safety & Store Metadata

## Goal
Make every Play-facing claim match the exact `zenithblue` artifact.

## Do
1. Rewrite the Play privacy policy from the actual final manifest/network behavior.
2. Remove stale v2 claims about permissions/services not used by Play.
3. State accurately whether analytics, crash reporting, accounts, ads, telemetry, or developer-side collection exist.
4. Add public-URL deployment instructions and in-app Privacy Policy link.
5. Re-audit Data Safety from dependencies + network calls; document the answers to enter in Play Console.
6. Create Play-specific Fastlane/store metadata. Do not advertise features compiled out of Play.
7. Review screenshots/changelogs for embedded host, remote rootfs, root/chroot, F-Droid/GitHub APK install wording.
8. Review target audience, content rating, ads and app-access declarations.

## Tests
```bash
rg -n 'embedded host|no external Termux|rootfs.*GitHub|chroot|rooted|ACCESS_SUPERUSER|SYSTEM_ALERT_WINDOW' fastlane docs/playstore app/src/zenithblue || true
./gradlew :app:dependencies --configuration zenithblueReleaseRuntimeClasspath
```
Manually compare privacy-policy permission list against the merged Play manifest line by line.

## Acceptance
- Privacy policy and Data Safety describe the actual Play build.
- Store title/short/full description describe only shipped Play features.
- Screenshots/changelogs contain no excluded-feature promise.
- Public HTTPS privacy URL + in-app path are documented.
- Play Console declaration worksheet is ready for submission.
