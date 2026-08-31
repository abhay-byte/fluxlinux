# FluxLinux Pro Privacy Policy

> **Release applicability:** this text describes the existing v1.8-era Play implementation and must **not** be copied unchanged to the v2 Play release. The reviewed v2 plan moves to an embedded PRoot runtime with Google Play Feature Delivery modules and removes the external-Termux/root/chroot Play architecture. `workers/08_privacy_and_store_metadata.md` must rewrite and reconcile this policy against the exact final v2 AAB before submission.

_Last updated: 2026-06-17._

FluxLinux Pro ("the App") is a desktop-environment installer for Android.
This page describes what data the App handles so that the Google Play
Console "Data safety" form and the in-app disclosures can stay
consistent with the actual code.

## Summary

**FluxLinux Pro does not collect, transmit, sell, or share any personal
data.** All operations described below happen on-device. No analytics,
no crash reporting, no third-party SDKs, no remote logging.

## Data the App accesses

| Data | Source | Stored? | Transmitted? |
|------|--------|---------|--------------|
| Termux / Termux:X11 install state | `PackageManager` queries | Locally (SharedPreferences, optional) | No |
| Distro install script names | Bundled in APK assets | Read-only | No |
| Linux container install / start scripts | Bundled in APK assets; user-initiated run in Termux | Cached locally | No |
| Optional overlay-permission state | `Settings.canDrawOverlays()` | Read once per screen, not stored | No |
| Optional root detection | `su -c id` (root path only) | Not stored | No |
| Foreground-service install-server port | In-process `StateFlow` | Not persisted | No |
| `fluxlinux://callback?...` deep link | Android intent | Read once, not stored | No |

## Permissions, plain-English

| Android permission | Why it's requested | Personal data? |
|---|---|---|
| `com.termux.permission.RUN_COMMAND` | Send shell commands to a locally-installed Termux so it can run the bundled Debian install scripts. | No |
| `INTERNET` | Reserved for future use by Linux containers; the Android app itself does not open network sockets. | No |
| `POST_NOTIFICATIONS` | Show the foreground-service install-progress notification. | No |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | Keep the local install HTTP server alive while the user switches away mid-install. | No |

The app **does not** request `READ_EXTERNAL_STORAGE`,
`WRITE_EXTERNAL_STORAGE`, `READ_CONTACTS`, `ACCESS_FINE_LOCATION`,
`CAMERA`, `RECORD_AUDIO`, `READ_PHONE_STATE`, or any other
sensitive permission.

## Children

The App is not directed at children under 13 and the Play Console
"Designed for Families" programme is **not** opted into. The App
contains no advertising, no tracking, and no user-generated-content
surface.

## Changes to this policy

Material changes will be posted here with a new "Last updated" date
and announced in the App's Play Store listing "What's new" section
before they ship.

## Contact

Questions about this policy or the App's data practices: open an
issue at <https://github.com/abhay-byte/fluxlinux/issues>.

---

This document is hosted at the URL declared in the Google Play
Console "App content → Privacy policy" field. Keep the URL stable —
Google indexes it during review.