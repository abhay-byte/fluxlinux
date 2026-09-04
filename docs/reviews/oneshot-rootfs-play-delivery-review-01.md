# Plan Review: oneshot-rootfs-play-delivery — PASS 1 ITER 1

## Verdict: REVISE
## Counts: CRITICAL 3 MAJOR 4 MINOR 2 SUGGESTIONS 2

### Findings

#### [CRITICAL] Zenithblue HTTP + shared-storage fallback remains live
- Location: `docs/plans/oneshot-rootfs-play-delivery-plan.md` Step 2.4 / Step 3.1 / §4 allowlist (`OnboardingInstallRunner.kt` "only if")
- Problem: Plan assumes UI pre-materialize + "DL phase stays no-op/confirmer" is enough. Repo always calls `RootfsDownloader.ensurePresent` (HTTP + `/sdcard/Download` candidates) in both proot and chroot paths, and always exports `FLUX_ROOTFS_URL` so `flux_install.sh` curl/wget can still fetch GitHub. Spec §13.7 / hard scope forbid any Play HTTP or shared-storage fallback in zenithblue. Soft "only if required" leaves worker room to ship a path that still falls back.
- Evidence:
  - `OnboardingInstallRunner.kt:206-217` and `:385-395` → `RootfsDownloader.ensurePresent`
  - `RootfsDownloader.kt:121-128,356-368` → `/sdcard/Download` + emulated Download candidates then network
  - `OnboardingInstallRunner.kt:239` → `put("FLUX_ROOTFS_URL", profile.rootfsUrl)`
  - `flux_install.sh:226-247` → curl/wget download when archive missing
  - Source spec §13.7 + hard scope "HTTP executable fallback in Play" / "shared-storage fallback in Play"
- Impact: Acceptance criterion 7 fails even if SplitInstall works; silent GitHub/Download rescue on zenithblue.
- Required planner change: Make zenithblue suppression **mandatory and concrete** in the plan (not optional): (1) Play provider path must not call `RootfsDownloader.ensurePresent` network/localCandidates; (2) runner DL branch for Play-supported ids must verify-only (reuse `isValid`/`isDeployed`) and fail closed with retry — never HTTP; (3) on zenithblue Play path unset/omit `FLUX_ROOTFS_URL` (or equivalent) so scripts cannot curl GitHub — without editing `flux_install.sh` (scope-forbidden). State exact symbols/call sites.

#### [CRITICAL] Feature-module Gradle contracts incomplete (flavors + plugin + `:app` dep)
- Location: plan Step 1 / §4 / Risk N2
- Problem: Plan creates 7 `com.android.dynamic-feature` modules with `src/zenithblue/assets/...` but does not require each feature to mirror base `flavorDimensions += "store"` + `ivarna`/`zenithblue` productFlavors, declare `implementation(project(":app"))`, or add the dynamic-feature plugin to the version catalog. Base has `flavorDimensions "store"` (`app/build.gradle.kts:34-47`). AGP 8.7.3 rejects mismatched flavor dimensions; without `:app` dependency feature modules do not build as DFMs. Catalog today only has `android-application` (`gradle/libs.versions.toml:42-44`).
- Evidence: `settings.gradle.kts` only `:app,:termux-x11,:stub`; no `dynamicFeatures`; `libs.versions.toml` no `android-dynamic-feature` / Play libs; N2 acknowledges flavor interop but falls back to `src/main` without spelling required Gradle surface.
- Impact: Worker rediscovers AGP failures; wrong fallback may ship rootfs into non-zenithblue feature variants or break assemble.
- Required planner change: Expand Step 1 with exact per-module Gradle checklist: plugin id (catalog entry `com.android.dynamic-feature` AGP 8.7.3), `namespace`, `compileSdk`/`minSdk` match base, `flavorDimensions += "store"` + both productFlavors, `implementation(project(":app"))`, `dist:onDemand="true"` + split name = module name, feature-level `noCompress` for `xz`/`tar`/`gz`, and base `dynamicFeatures += …`. Keep N2 fallback only after that checklist fails, still verifying ivarna artifact has no rootfs/`payloads`.

#### [CRITICAL] Step 0 packaging source missing on clean tree — no allowed fetch rule
- Location: plan Step 0 / Step 1.4
- Problem: Plan copies pinned bytes into feature assets from `assets/rootfs/` and/or release tag, but STOP-gates on missing files and forbids download/replace. Repo gitignores `assets/rootfs/`; workspace has **no** `assets/rootfs/**` files. Worker on this branch cannot package modules without an allowed way to obtain the exact pinned release bytes for *build-time packaging*.
- Evidence: `.gitignore` `assets/rootfs/` + `app/src/*/assets/rootfs/`; glob `**/assets/rootfs/**` → none; `DistroInstallProfile` pins + `ROOTFS_RELEASE_BASE` URLs exist; source spec §1 STOP if missing.
- Impact: Implementation blocked immediately at Step 0 on clean checkout; or worker invents an out-of-spec download.
- Required planner change: Add explicit precondition / allowed packaging-source rule: e.g. (A) require local `assets/rootfs/<file>` present with matching SHA before work continues, **or** (B) allow build-time fetch of the **exact** `DistroInstallProfile.rootfsUrl` bytes solely to stage into `:distro_*` assets (not a runtime fallback), with SHA gate — distinguish packaging fetch from zenithblue runtime HTTP ban. Record which rule binds the worker.

#### [MAJOR] Second install entry `OnboardingFlowScreen` not wired / not allowlisted
- Location: plan Step 3 / §4 allowlist
- Problem: Plan wires `InstallConfigScreen.startInstall` (+ DistroScreen nav) but onboarding also calls `runner.start` with the same DL/HTTP path. Allowlist omits `OnboardingFlowScreen.kt`. Spec requires existing install UI request/materialize the module with progress/retry.
- Evidence: `OnboardingFlowScreen.kt:138-162,191,202` → `startInstall()` → `runner.start`; `MainActivity.kt:799` hosts onboarding; allowlist §4 lists only `InstallConfigScreen.kt` (+ caller if needed).
- Impact: Fresh zenithblue users hit onboarding HTTP/GitHub path; criteria 5–7 incomplete.
- Required planner change: Add `OnboardingFlowScreen.startInstall` to Step 3 and §4 allowlist with the same provider request / progress / retry / no-fallback behavior as `InstallConfigScreen` (shared helper preferred to avoid duplicate FLAVOR logic).

#### [MAJOR] UI capability filter call sites unspecified
- Location: plan Step 3.3 / FEATURE_SET §10 UI tests
- Problem: "unsupported → hidden/disabled" via registry/`supports()` stated, but DistroScreen / DistroPickPage / HomeScreen still list full `DistroRepository.supportedDistros` (12 proot + chroots). Plan does not name which files filter to the 7 Play ids, nor that non-registry cards (fedora/void/opensuse/deepin/parrot + all chroots) must be hidden/disabled on zenithblue. Allowlist may need `DistroScreen.kt`, onboarding pick page, possibly `HomeScreen.kt`.
- Evidence: `DistroScreen.kt:67-82` filters only installed/method; `OnboardingFlowScreen.kt:819-822` same; registry planned for 7 proot ids only; source spec §6 capability rule.
- Impact: Worker leaves non-Play distros installable → HTTP path or hard fail; UI tests in §10 unimplementable without guessing.
- Required planner change: Name exact UI list call sites + filter rule (`RootfsPayloadProvider.supports` / registry contains id). State zenithblue hides non-registry cards (including chroots); ivarna unchanged. Add those files to §4 allowlist.

#### [MAJOR] SplitCompat install site underspecified (no Application class)
- Location: plan Step 1.3 / §4 (`Application/MainActivity`)
- Problem: Manifest `<application>` has no custom `android:name` Application class. Plan offers "Application subclass or MainActivity.attachBaseContext" without picking one. Feature assets after `INSTALLED` need `SplitCompat.install`.
- Evidence: `app/src/main/AndroidManifest.xml:20-29` — no Application class; `MainActivity.kt:71` extends `ComponentActivity` with no `attachBaseContext`; glob `**/*Application*.kt` → none.
- Impact: Worker invents Application (manifest + new file) or misses SplitCompat → cannot open feature assets.
- Required planner change: Pick one approach. Prefer minimal: `MainActivity.attachBaseContext` + `SplitCompat.install(this)` (and Application only if docs require both). List exact new/modified files in §4.

#### [MAJOR] Feature-level `noCompress gz` not required on `:distro_*`
- Location: plan Step 1.2 / N4 / §4 (`app/build.gradle.kts` noCompress gz)
- Problem: Alpine ships as real `.tar.gz`. Plan extends base `noCompress` with `gz` but packaging lives in feature modules; aapt2 for the feature must also keep gz STORED or SHA drifts.
- Evidence: base `app/build.gradle.kts:57` `noCompress += listOf("xz", "tar")` only; `ALPINE_ROOTFS_NAME = "alpine_3.24_rootfs.tar.gz"`; R3/D8 alpine gzip bytes; N4 notes risk but Step 1 only mandates feature noCompress generically without binding `gz` on the alpine module.
- Impact: Alpine SHA gate fails after packaging; STOP/rework.
- Required planner change: Require `noCompress` including `gz` (and `xz`/`tar`) on **each** `:distro_*` `build.gradle.kts` (especially `:distro_alpine`), not only base.

#### [MINOR] Play artifact resolution rule OK but should name preferred modern coordinate
- Location: plan R10 / N1 / Step 1.3
- Problem: Deferring exact version is fine; still points training-data `com.google.android.play:core:1.10.3` as default-to-avoid without stating preferred docs-current family (`feature-delivery` / Play Feature Delivery KTX) as first choice.
- Evidence: plan R10/N1; no Play entries in `libs.versions.toml`; R1 on-demand docs.
- Impact: Worker may still pick deprecated `play:core` first.
- Required planner change: Resolution rule: prefer docs-current Feature Delivery artifact from R1 (not deprecated monolithic Play Core); record chosen `group:name:version` + URL in worker report.

#### [MINOR] §4 allowlist incomplete vs mandatory edits
- Location: plan §4
- Problem: After fixing CRITICAL/MAJOR items, allowlist must include `OnboardingFlowScreen.kt`, UI filter files, possibly new Application (if chosen), feature `noCompress`, and runner DL/`FLUX_ROOTFS_URL` changes as first-class — not "only if".
- Evidence: §4 vs findings above.
- Impact: Scope fights required correctness edits.
- Required planner change: Refresh allowlist to match mandatory call sites; keep ABSOLUTE SCOPE RULE otherwise intact.

#### [SUGGESTION] Reuse `RootfsDownloader.isValid`/`sha256` instead of new `VerifiedPayloadStore`
- Location: plan Step 2.2
- Problem: Spec says "existing VerifiedPayloadStore"; repo has none — plan already allows reuse. Prefer reuse-only to cut files.
- Evidence: grep `VerifiedPayloadStore` → only plan; `RootfsDownloader.isValid`/`sha256` exist.
- Impact: Extra type without need.
- Required planner change: Optional — default to reuse; create `VerifiedPayloadStore` only if reuse cannot express reject/delete semantics.

#### [SUGGESTION] Size-gate script: extend negative check for `payloads/`
- Location: plan Step 4; `scripts/verify_apk_host_assets.sh:70-77`
- Problem: Existing gate checks `assets/rootfs/` only. Plan correctly asks `payloads/` too — keep that explicit in the script extension snippet.
- Evidence: `verify_apk_host_assets.sh` rootfs negative gate only.
- Impact: Low — already in plan Step 4.
- Required planner change: None blocking; keep `payloads/` in the negative gate text.

## Axes checked
1. Architecture/ownership — install SSOT, downloader, runner, UI seams verified; Play types do not exist yet (plan correct).
2. Data/control flow — SplitInstall → materialize → verify → runner; HTTP/script fallback holes found.
3. Lifecycle/threading — SplitInstall foreground + listener noted; SplitCompat site gap.
4. Persistence/errors — app-private `TermuxHostPaths.homeDir` correct; SHA reuse path OK; fallback ban incomplete.
5. Compatibility/testing/scope — AGP 8.7.3 / minSdk 26 OK; AC 1–16 + §10 present; module Gradle + clean-tree packaging + UI/onboarding gaps block APPROVE.

## Next Agent: Planner
## Next Action: Revise `docs/plans/oneshot-rootfs-play-delivery-plan.md` to fix all CRITICAL and MAJOR findings (mandatory zenithblue HTTP/script suppression, complete DFM Gradle checklist, packaging-source rule, onboarding + UI filter allowlist/call sites, SplitCompat site, feature `noCompress gz`), then resubmit for PASS 1 ITER 2 plan review.
