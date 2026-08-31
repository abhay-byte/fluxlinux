# FluxLinux v2.0.0 — Google Play Compliance Roadmap

> **Target:** update the existing `playstore` track from the v1.8-era implementation to a policy-conservative v2.0 release without carrying the new v2 behaviors that create Google Play policy risk.
>
> **Baseline:** `v2.0.0` (`c83cb17e7a5d4713f8e0b931761061902e9dd345`).
>
> **Existing Play branch:** `playstore` currently ends at `816371bba62535fc3fc3b433fba47e5dcf9bda74`, five commits after `v1.8.0`. Preserve it as the last known Play-safe rollback point until all gates in this document pass.
>
> **Policy source of truth:** `abhay-byte/abhay-kb/Google_Play_Store_Policy_Compliance_Guide.md`, plus the live Google Play policy pages linked in §15. The guide itself says the live Policy Center wins if policy text changes.

---

## 1. Executive decision

Do **not** merge `main` or `v2.0.0` wholesale into `playstore` and then try to remove violations afterward.

v2.0 changes the execution model in a way that crosses the most sensitive Play policy boundary:

- v1.8 Play: FluxLinux primarily orchestrated an externally installed Termux environment.
- v2.0: FluxLinux embeds the host/runtime, downloads Linux root filesystems from GitHub, extracts them into app-controlled storage, and executes the resulting guest binaries through PRoot/chroot.

The Play policy guide explicitly prohibits downloading/executing executable code from outside Google Play, with a narrow VM/interpreter exception. A native ARM64 Linux rootfs running through PRoot/chroot is not a safe assumption for that exception. Treat it as **prohibited for the Play build unless Google provides written approval for this exact architecture**.

### Recommended Play architecture

Use the v2.0 codebase and UI as the source, but enforce a **build-time Play capability boundary**:

| Capability | `ivarna` / F-Droid-GitHub | `zenithblue` / Google Play |
|---|---:|---:|
| Embedded Termux-prefix host | Yes | **No** for initial Play release |
| GitHub rootfs downloads | Yes | **No** |
| Runtime bootstrap repair download | Yes | **No** |
| Native PRoot guest downloaded by FluxLinux | Yes | **No** |
| Chroot / root shell | Yes | **No** |
| `ACCESS_SUPERUSER` | Yes if needed | **No** |
| Nested `loader.apk` asset | Current v2 behavior | **No** |
| Executable `.so` copied from assets to writable storage | Current v2 behavior | **No** |
| External Termux orchestration | Optional legacy | **Yes — conservative Play path** |
| Direct APK download/install | No | **No** |
| `REQUEST_INSTALL_PACKAGES` | No | **No** |
| Direct `.apk` / `.zip` download links | Avoid | **No** |
| Play-safe support/docs links | Yes | Yes |
| API 36 target | Yes | Yes |

This intentionally sacrifices some v2 feature parity on Play. That is preferable to risking app suspension or account enforcement.

---

## 2. Why v2.0 is materially different from the v1.8 Play branch

`v2.0.0` is 40 commits ahead of `v1.8.0`. The release added an embedded host, embedded X11, twelve on-demand distros, host PulseAudio, broad chroot support, and new terminal/session services.

The existing `playstore` branch was built for the old architecture and its policy document even states that downloads inside Termux were outside FluxLinux because Termux was a separate process. That assumption must **not** be reused for v2: the v2 host and guest execution path is inside FluxLinux itself.

### Critical v2 evidence

1. `RootfsDownloader.kt` performs network GETs for distro archives, including resume, SHA verification, and writes into app/local storage.
2. `DistroInstallProfile.kt` hard-codes GitHub release URLs for Debian, Alpine, Fedora, Void, openSUSE, Chimera, Deepin, Manjaro, Ubuntu, Kali, Parrot, and Arch rootfs archives.
3. `app/build.gradle.kts` states that rootfs archives are not packaged and are downloaded on demand. The Play `zenithblue` flavor packages `bootstrap.tar`, but the distro rootfs path is still network-delivered.
4. `HostScriptDeployer.kt` deploys `loader.apk` from app assets into writable app storage.
5. `HostScriptDeployer.kt` copies Pulse `.so` files from assets into `$filesDir/usr/lib` and marks them executable.
6. `AndroidManifest.xml` adds `ACCESS_SUPERUSER`, `FOREGROUND_SERVICE_SPECIAL_USE`, and multiple special-use services.
7. The v2 Play-facing Fastlane description explicitly advertises an embedded host, twelve distros, rooted chroot, and rootfs downloads from GitHub.
8. The v2 privacy policy is stale: it still describes external Termux/Termux:X11 orchestration and permissions that no longer match the v2 manifest.

---

## 3. Severity matrix

| ID | Finding | Severity | Play action |
|---|---|---|---|
| P0-1 | App downloads Linux rootfs containing executable binaries from GitHub | **Blocker** | Remove from Play artifact; source-set isolate |
| P0-2 | Rootfs is executed natively through PRoot/chroot | **Blocker** | Play path must not execute network-delivered native guest code |
| P0-3 | Nested `assets/loader.apk` / `loader.bin` | **Blocker / scanner risk** | Remove from Play AAB; integrate helper code normally |
| P0-4 | Executable `.so` files copied from assets to writable storage | **High** | Move Play-required native libs to `jniLibs` / `nativeLibraryDir`; no writable executable overlay |
| P0-5 | Root/chroot capability and `ACCESS_SUPERUSER` in Play manifest | **High** | Compile out and remove permission |
| P1-1 | Bootstrap has GitHub repair URL even though Play flavor packages it | **High** | Remove download-capable implementation/URL from Play artifact |
| P1-2 | Broad special-use FGS surface | **High** | Remove services no longer needed; declare any retained FGS accurately |
| P1-3 | v2 store description advertises noncompliant Play capabilities | **High** | Maintain Play-specific metadata |
| P1-4 | Privacy policy no longer matches v2 behavior/manifest | **High** | Rewrite and publish before release |
| P1-5 | Direct third-party binary/download links can regress | **High** | Add static and artifact-level CI guards |
| P2-1 | Deep-link callback is browsable | Medium | Validate input, no arbitrary command execution |
| P2-2 | `queries` exposes Termux/Termux:X11 package visibility | Low/expected | Keep only if Play path truly needs it |
| P2-3 | Data safety says no collection | Medium | Re-audit SDKs/network traffic and keep form synchronized |
| P2-4 | API 36 / Android 16 behavior | Required | Already targeted; run device/FGS/storage validation |

---

## 4. Branch strategy — protect the known-good Play build

### 4.1 Never destroy the existing rollback point first

Create a permanent backup ref before v2 work:

```bash
git fetch origin --tags
git branch backup/playstore-v1.8p 816371bba62535fc3fc3b433fba47e5dcf9bda74
git push origin backup/playstore-v1.8p
```

### 4.2 Build v2 Play work on a separate integration branch

```bash
git switch -c playstore-v2-compliance v2.0.0
```

Do **not** merge the old `playstore` branch wholesale. Port only the old policy-safe behavior that is still required:

- existing Play application ID: `com.zenithblue.fluxlinux`
- version continuity (next `versionCode` > 11; v2 already uses 12)
- external-Termux prerequisite/orchestration behavior
- no in-app APK installer
- no `REQUEST_INSTALL_PACKAGES`
- no direct downloadable APK/ZIP CTA
- Play-specific privacy/store text

### 4.3 Promote only after all gates pass

When workers 01–10 are complete and the final AAB passes artifact inspection/device testing:

```bash
git push origin playstore-v2-compliance
# review/PR first
# only then move/merge into playstore
```

Keep `backup/playstore-v1.8p` indefinitely through at least one successful production rollout.

---

## 5. Build-time policy boundary — mandatory design rule

A runtime boolean such as `if (BuildConfig.PLAY_STORE)` is **not sufficient** for blocker-class behavior. If prohibited downloader/root/chroot code remains in `classes.dex`, native assets, resources, or strings, Play's scanners can still detect it.

### Required layout direction

- `src/main`: only code/assets safe for every distribution channel.
- `src/ivarna`: embedded host, remote rootfs/bootstrap downloader, chroot/root integration, non-Play executable overlays, F-Droid/GitHub-only capabilities.
- `src/zenithblue`: Play-safe implementations, external Termux bridge, Play-safe onboarding, Play metadata/resources.

Where both flavors need the same API, define a small interface in `main` and provide flavor-specific implementations.

### Dependency isolation

Move dependencies that exist only for the embedded host to flavor-scoped configurations where possible:

```kotlin
ivarnaImplementation(libs.termux.app)
ivarnaImplementation(project(":termux-x11")) // if Play no longer embeds X11
```

Do not ship unused high-risk libraries in the Play AAB merely because the UI does not call them.

---

## 6. P0 remediation — external executable code

### 6.1 Remove `RootfsDownloader` from the Play artifact

For `zenithblue`:

- no OkHttp rootfs GET
- no GitHub `releases/download/rootfs` constants
- no Range/resume rootfs logic
- no rootfs fallback from `/sdcard/Download`
- no extraction of network-delivered rootfs archives
- no app-owned execution of those archives

The implementation may remain in `src/ivarna`.

### 6.2 Prevent indirect reintroduction

The Play release AAB must not contain these strings/classes:

- `RootfsDownloader`
- `ROOTFS_RELEASE_BASE`
- `releases/download/rootfs`
- `bootstrap_com.zenithblue.fluxlinux.tar` download URL
- network repair/fallback path for executable bootstrap content

### 6.3 Play feature behavior

When a v2 UI path requires a feature removed from Play:

- hide it, or
- show a clear “Not available in the Google Play build” explanation, without linking to an APK/direct binary download.

Do not silently fail or keep dead buttons.

### 6.4 Optional future parity paths — not part of initial release

Only investigate these after the conservative Play build is live:

1. **Play Asset Delivery** for immutable guest payloads delivered by Google Play.
2. A real **VM/interpreter** execution architecture for user-downloaded guest code, with no direct Android API escape.
3. Written policy clarification/approval from Google for the exact Linux execution model.

Do not assume SHA256 pinning makes a remote executable download policy-compliant; integrity verification and distribution-policy compliance are different concerns.

---

## 7. P0 remediation — nested APK and writable executable assets

### 7.1 Remove nested APK

The Play AAB must not include:

- `app/src/main/assets/loader.apk`
- `app/src/main/assets/loader.bin` when it is byte-identical APK content
- any other nested `.apk`

Integrate required Termux:X11 loader classes through the normal Android module/dependency build so they are part of the signed app's own dex/resources instead of a second APK asset.

### 7.2 Native libraries

Audit every binary under assets/scripts with `file` and magic-byte checks.

Known v2 examples include:

- Pulse runtime `.so` files
- `scripts/opensuse/common/libevp_md2.so`
- `scripts/common/setup/bwrap-proot-shim`

For the Play flavor:

- required native libraries belong in `jniLibs/<abi>` or a normal native module;
- load/reference them from `applicationInfo.nativeLibraryDir`;
- do not copy `.so`/ELF files to writable app storage and chmod them executable;
- remove binaries used only by embedded host/chroot from `zenithblue` entirely.

### 7.3 Artifact rule

An AAB scan must prove there are no nested APKs and no unexpected ELF/native binaries under `assets/`.

---

## 8. P0/P1 remediation — root/chroot capability

For the initial Play release, compile out:

- chroot cards
- chroot storage management
- `RootShell`
- BusyBox/Magisk/KernelSU/APatch integration
- root process management
- root-only setup/start/stop scripts
- `ACCESS_SUPERUSER`

The app must not prompt for root in the Play build.

If root support is ever restored to Play, treat it as a new policy/security review, not a routine feature toggle.

---

## 9. Permissions and foreground services

### 9.1 Desired Play manifest

Expected minimal permissions should be justified by actual Play behavior. Likely candidates:

- `INTERNET`
- `POST_NOTIFICATIONS` if required by retained FGS behavior
- `FOREGROUND_SERVICE` plus only the exact type-specific permission still used
- `com.termux.permission.RUN_COMMAND` only if the external Termux bridge is retained

Remove:

- `ACCESS_SUPERUSER`
- `REQUEST_INSTALL_PACKAGES` if it ever reappears
- `FOREGROUND_SERVICE_SPECIAL_USE` if embedded terminal/desktop services are removed
- unused package queries

### 9.2 FGS minimization

Review each service independently:

- `InstallServerService`
- `BaseInstallService`
- `AppTerminalService`
- `DesktopSessionService`

The conservative Play build should need fewer services than v2 F-Droid/GitHub.

Any retained FGS must be:

- user initiated or clearly user perceptible
- necessary for core functionality
- visible through an accurate ongoing notification
- stoppable by the user
- stopped immediately when work ends
- declared using the correct type in both manifest and Play Console

Do not keep a `specialUse` declaration “just in case.”

---

## 10. External links, downloads, deep links, and package visibility

### 10.1 Never regress the v1.8 fixes

Play build must not:

- download another app's APK
- call `PackageInstaller` for third-party APKs
- request “Install unknown apps” permission
- link directly to `.apk`, `.apks`, `.xapk`, `.zip`, Magisk module ZIP, or similar binary payload as an install CTA
- tell users to disable Play Protect/device security

### 10.2 External Termux prerequisites

If the conservative Play path depends on Termux/Termux:X11:

- detect installed packages;
- explain prerequisites clearly;
- open only official product/project landing pages or Play listings;
- do not auto-download binaries;
- do not initiate installers;
- do not make an external binary download look like a built-in app update.

### 10.3 Deep-link callback

The `fluxlinux://callback` activity is `BROWSABLE`. Validate every callback parameter against an active, app-created task/session. Never allow a URL to inject an arbitrary shell command, script path, file path, or URL.

Add negative tests for malformed/spoofed callbacks.

---

## 11. Privacy, Data Safety, disclosures, and store metadata

### 11.1 Privacy policy must be rewritten

The v2 policy is inconsistent with the actual app. The Play policy must accurately state:

- developer/app identity: FluxLinux / `com.zenithblue.fluxlinux`
- no analytics/ads/telemetry if still true
- what network connections are made and why
- external Termux interaction if retained
- local logs and retention/deletion behavior
- foreground service behavior
- exactly which permissions the Play manifest requests
- no claim for `SYSTEM_ALERT_WINDOW` unless the Play manifest actually requests/uses it
- no claim that the app itself downloads rootfs if Play no longer does
- a contact method

Publish it at a public HTTPS URL and link it inside the app and Play Console.

### 11.2 Data Safety

Re-audit all SDKs before selecting “No data collected.” Confirm:

- no analytics SDK
- no crash-report upload
- no remote logging
- no advertising ID
- no account system
- no hidden telemetry from dependencies

User-initiated navigation/download requests to public services do not automatically mean the developer collects data, but the form and privacy policy must describe the app truthfully.

### 11.3 Play-specific Fastlane metadata

Do not reuse the v2 F-Droid/GitHub description. It currently advertises:

- embedded host
- no external Termux
- twelve on-demand distros
- rooted chroot
- GitHub rootfs downloads

Create Play-specific metadata that describes only features actually shipped in `zenithblue`.

Also update screenshots/changelogs so they do not show hidden Play-excluded features.

---

## 12. CI compliance gate — make policy regressions build failures

Create a dedicated task/script such as:

```text
scripts/verify_play_policy.sh
```

Run it in CI after `bundleZenithblueRelease`.

### 12.1 Source checks

Fail when the Play source set/manifest introduces:

- `REQUEST_INSTALL_PACKAGES`
- `ACCESS_SUPERUSER`
- direct APK/ZIP installer URLs
- `PackageInstaller`
- `ACTION_INSTALL_PACKAGE`
- `canRequestPackageInstalls`
- Play Protect disabling instructions

### 12.2 Built-artifact checks

Inspect the **release AAB**, not only source code. Fail if it contains:

- nested `*.apk`
- unexpected `*.dex`/`*.jar` in assets
- unexpected ELF/`.so` files under assets instead of native lib locations
- `RootfsDownloader` / rootfs release URLs
- prohibited permissions in merged manifest
- root/chroot code/resources that should have been flavor-excluded

R8/minification means source presence and artifact presence can differ; the AAB is the real submission boundary.

### 12.3 Dependency check

Produce and review:

```bash
./gradlew :app:dependencies --configuration zenithblueReleaseRuntimeClasspath
```

Ensure non-Play runtime engines are not accidentally inherited into the Play variant.

---

## 13. Build and test matrix

### 13.1 Required Gradle gates

First enumerate actual task names:

```bash
./gradlew tasks --all | grep -i zenithblue
```

Then run the equivalent of:

```bash
./gradlew clean
./gradlew :app:testZenithblueDebugUnitTest
./gradlew :app:lintZenithblueRelease
./gradlew :app:bundleZenithblueRelease
```

No lint baseline should hide new permission/security/policy problems.

### 13.2 AAB inspection

Validate:

- application ID remains `com.zenithblue.fluxlinux`
- `versionCode` > 11
- `targetSdkVersion = 36`
- only expected permissions/services/providers/queries
- no nested APK
- no remote rootfs URL strings
- no root capability
- correct 64-bit native libraries

Use `bundletool` to build a universal/device APK set and inspect the installed artifact too.

### 13.3 Device matrix

At minimum test:

| Android | API | Required |
|---|---:|---:|
| Android 13 | 33 | Yes |
| Android 14 | 34 | Yes |
| Android 15 | 35 | Yes |
| Android 16 | 36 | **Yes / primary** |

Test both fresh install and upgrade from the last Play build (`versionCode 11`).

### 13.4 Functional scenarios

- cold start / onboarding
- prerequisite detection
- dependency-not-installed UX
- dependency-installed UX
- distro install/start/stop for every Play-supported path
- background/foreground transition during an active user operation
- notification permission denied
- network unavailable / captive network / interrupted task
- app process recreation
- rotation / configuration change
- deep-link callback spoof attempts
- uninstall/reinstall
- upgrade from 1.8p with existing external Termux data

### 13.5 Negative policy tests

On the Play build, verify there is **no UI path** that:

- downloads rootfs/bootstrap/native binaries into FluxLinux
- installs APKs
- requests unknown-app installation access
- requests root
- opens a direct binary attachment
- starts an undeclared FGS

---

## 14. Play Console release checklist

Before uploading:

- [ ] Play App Signing configured; signing key/package continuity verified.
- [ ] Android 16 / API 36 target confirmed.
- [ ] AAB, not raw APK, uploaded.
- [ ] 64-bit support verified for native code.
- [ ] Privacy policy public HTTPS URL set and reachable.
- [ ] Privacy policy linked in-app.
- [ ] Data Safety form matches the exact Play AAB.
- [ ] Foreground Service declaration updated for every retained type.
- [ ] FGS demo video prepared if requested by Play Console.
- [ ] Target audience declaration reviewed; do not target children unless the app genuinely meets Families requirements.
- [ ] IARC content rating updated for actual Play features.
- [ ] Ads declaration accurate (no ads if none).
- [ ] App access declaration accurate (no login if none).
- [ ] Store listing and screenshots show only Play-shipped behavior.
- [ ] No F-Droid/GitHub APK download CTA in Play-facing metadata.
- [ ] Pre-launch report reviewed.
- [ ] Closed/internal track smoke test completed before production rollout.
- [ ] Android Developer Verification/signing-key registration requirements reviewed for the September 2026 rollout.

---

## 15. Official policy references

Primary guide in this account:

- `abhay-byte/abhay-kb/Google_Play_Store_Policy_Compliance_Guide.md`

Live Google sources to re-check immediately before submission:

- Developer Program Policies: https://support.google.com/googleplay/android-developer/answer/17105854
- Device and Network Abuse: https://support.google.com/googleplay/android-developer/answer/16559646
- REQUEST_INSTALL_PACKAGES: https://support.google.com/googleplay/android-developer/answer/12085295
- Hostile Downloaders: https://support.google.com/googleplay/android-developer/answer/11189134
- User Data / Privacy: https://support.google.com/googleplay/android-developer/answer/10144311
- Data Safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- Policy Center: https://support.google.com/googleplay/android-developer/topic/9858052
- Policy announcements: https://support.google.com/googleplay/android-developer/announcements/13412212
- Android Developer Verification: https://developer.android.com/developer-verification

---

## 16. Worker execution order

Workers are intentionally small and should be completed in order:

1. `workers/01_branch_baseline.md` — create safe v2 integration baseline and preserve rollback.
2. `workers/02_play_flavor_boundary.md` — establish build-time Play/non-Play capability split.
3. `workers/03_remove_remote_executable_delivery.md` — eliminate Play rootfs/bootstrap executable downloads.
4. `workers/04_remove_nested_and_writable_executables.md` — loader APK + executable asset cleanup.
5. `workers/05_remove_root_chroot_from_play.md` — root/chroot compile-out and manifest cleanup.
6. `workers/06_links_permissions_and_callbacks.md` — external links, APK-install regression guards, callback hardening.
7. `workers/07_foreground_services.md` — minimize/validate FGS behavior and declarations.
8. `workers/08_privacy_and_store_metadata.md` — privacy/Data Safety/store-facing truthfulness.
9. `workers/09_ci_policy_gate.md` — source + AAB policy scanner in CI.
10. `workers/10_release_validation.md` — build, artifact inspection, device matrix, Play Console handoff.

Each worker must leave the branch buildable and attach command output/results to its PR or handoff note.

---

## 17. Final release gates — all must be green

### Code gate

- [ ] Prohibited features are source-set excluded, not only runtime-hidden.
- [ ] No Play rootfs/bootstrap executable download path.
- [ ] No nested APK.
- [ ] No unexpected executable asset overlay.
- [ ] No root/chroot in Play build.
- [ ] No APK installer/unknown-sources permission.

### Artifact gate

- [ ] `bundleZenithblueRelease` succeeds.
- [ ] AAB policy scan succeeds.
- [ ] Merged manifest permission/service review succeeds.
- [ ] Dependency review succeeds.
- [ ] Universal/device APK smoke test succeeds.

### Policy/documentation gate

- [ ] Privacy policy accurate and public.
- [ ] Data Safety accurate.
- [ ] FGS declaration accurate.
- [ ] Store metadata/screenshots accurate.
- [ ] API 36 / verification requirements satisfied.

### Rollout gate

- [ ] Upgrade from versionCode 11 tested.
- [ ] Internal/closed testing clean.
- [ ] Pre-launch report reviewed.
- [ ] No blocker/high policy finding open.

**Do not promote `playstore-v2-compliance` into `playstore` until every final gate above is checked.**
