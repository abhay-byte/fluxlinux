# Play Store Documentation

Documents related to publishing and maintaining FluxLinux Pro on the
Google Play Store.

## Index

| File | Purpose |
|------|---------|
| [`policies_and_violations.md`](policies_and_violations.md) | Comprehensive reference of Google Play Developer Program Policies and the specific violations / risks that have been identified in this repo, with per-file remediation guidance. |
| `privacy_policy.md` | Privacy policy hosted at the URL declared in Play Console. (To be created when the Play Console listing is configured.) |

## Workflow

1. **Read first.** `policies_and_violations.md` §1 (TL;DR) for a
   quick view of what is at risk.
2. **Walk the §5 checklist** before every Play Console upload.
3. **Open the relevant §4 section** for any file you are changing
   near a permissions / install / external-link area.
4. **Update `policies_and_violations.md` §7** (Change Log) when you
   add a new finding or remediate an existing one.

## Related

- Fastlane metadata (store listing text & assets) lives at
  `fastlane/metadata/android/en-US/`.
- The Play Store package name is set in
  `app/build.gradle.kts` (search for `applicationId` or
  `namespace`). The repo README currently lists
  `com.zenithblue.fluxlinux` as the Play package.
