# FluxLinux Play v2 implementation status

Last updated: 2026-09-01

## Baseline

- Integration branch: `playstore-v2-compliance`
- Exact v2.0.0 baseline: `c83cb17e7a5d4713f8e0b931761061902e9dd345`
- Rollback branch: `backup/playstore-v1.8p` at `816371bba62535fc3fc3b433fba47e5dcf9bda74`
- Play package: `com.zenithblue.fluxlinux`
- v2 version: `versionCode 12`, `versionName 2.0.0`
- SDK baseline: `compileSdk 36`, `targetSdk 36`, `minSdk 26`

Worker 01 established the branch and recorded the prepared v2 baseline. Worker
02 isolated payload acquisition behind flavor-specific providers. Worker 03
now supplies the Play executable payload path through on-demand dynamic
features; later policy-removal workers remain unchanged.

## Worker sequence

| Worker | State | Notes |
|---|---|---|
| 01 `branch_baseline` | pass | Exact v2 baseline prepared with pinned submodules and generated ignored bootstrap input; Play/non-Play builds and tests pass. |
| 02 `play_flavor_boundary` | pass | Common installers consume verified payload abstractions; zenithblue is packaged/local-only with rooted paths gated off, and ivarna retains release-backed providers. |
| 03 `remove_remote_executable_delivery` | partial | PFD delivery, provenance, atomic app-private staging, and Play AAB boundaries are implemented; no physical Play/device E2E was available in this worker. |
| 04 `remove_nested_and_writable_executables` | partial / 04R closure | Play release boundary is closed to seven staged distro modules plus `runtime_host`; Fedora, Void, openSUSE, Deepin, and Parrot are deferred and excluded from the Play graph. Common/Ivarna raw hashes remain separate from Play hashes. Play provenance, size, AAB/APKS structure, host-artifact, runtime-script, 16 KiB, Gradle test, and debug/release gates pass. Poco X6 Pro smoke evidence covers host delivery, Alpine install, seven-card terminal filtering, and offline PRoot; native X11 server startup with shared TMPDIR/XKB is verified, while a complete visible XFCE session remains unclaimed. Worker 05 was not started. See `worker_reports/04_remove_nested_and_writable_executables_report.md`. |
| 05 `remove_root_chroot_from_play` | pending |  |
| 06 `links_permissions_and_callbacks` | pending |  |
| 07 `foreground_services` | pending |  |
| 08 `privacy_and_store_metadata` | pending |  |
| 09 `ci_policy_gate` | pending |  |
| 10 `release_validation` | pending |  |

See `worker_reports/01_branch_baseline_report.md` and
`worker_reports/02_play_flavor_boundary_report.md` for the evidence and
acceptance-criteria reviews.
