# Plan Review: oneshot-rootfs-play-delivery — PASS 1 ITER 2

## Verdict: REVISE
## Counts: CRITICAL 0 MAJOR 2 MINOR 2 SUGGESTIONS 0

### Prior findings (review-01) — disposition

| ID | Title | Status |
|----|-------|--------|
| CRITICAL-1 | Zenithblue HTTP + shared-storage fallback live | **FIXED** — Step 2.4a–c mandatory verify-only DL + omit/unset `FLUX_ROOTFS_URL`; no `ensurePresent` on Play path; `flux_install.sh` untouched |
| CRITICAL-2 | DFM Gradle contracts incomplete | **FIXED** — Step 1.3a–f checklist (plugin, flavors, `:app` dep, per-feature `noCompress xz/tar/gz`, `dist:onDemand`) |
| CRITICAL-3 | Packaging source missing on clean tree | **FIXED** — Step 0 Rule A/B + N6 honesty; Rule B packaging-only |
| MAJOR-1 | `OnboardingFlowScreen` unwired | **FIXED** — Step 3.1 Entry 2 + §4 allowlist |
| MAJOR-2 | UI filter call sites unspecified | **FIXED** — three named sites; residual flavor-gate contract gap → new MAJOR below |
| MAJOR-3 | SplitCompat site underspecified | **PARTIALLY FIXED** — `MainActivity.attachBaseContext` chosen; ApplicationContext/asset-open gap → new MAJOR below |
| MAJOR-4 | Feature-level `noCompress gz` | **FIXED** — Step 1.3e |
| MINOR-1 | Play artifact preference | **FIXED** — N1/Step 1 docs-current Feature Delivery family |
| MINOR-2 | §4 allowlist incomplete | **FIXED** — refreshed mandatory list |
| SUGGESTION-1 | Reuse `isValid`/`sha256` | **FIXED** — Step 2.2 default |
| SUGGESTION-2 | `payloads/` negative gate | **FIXED** — Step 4 explicit |

No regressions that reopen FIXED items. New gaps below.

### Findings

#### [MAJOR] Flavor-gate contract missing for runner + UI `supports()`
- Location: plan Step 2.4a / Step 3.3 / `ZenithbluePayloadProviders` / `RootfsPayloadProvider.supports`
- Problem: Plan requires zenithblue-only verify-only + filter, and ivarna unchanged with **no scattered `BuildConfig.FLAVOR` in Compose** — UI asks provider/registry. But `supports(profile) = registry contains distroId` (Step 2.3/2.4) is flavor-blind if registry lives in `main`. Runner Step 2.4a also says "AND flavor is zenithblue" without naming the discriminator. Worker must invent how ivarna keeps full card list / `ensurePresent` while Compose never sees FLAVOR.
- Evidence:
  - Plan Step 2.4: `supports(profile)` = registry contains id; Step 2.4a adds "flavor is zenithblue" without symbol
  - Plan Step 3.3: "No scattered `BuildConfig.FLAVOR` checks in Compose — UI asks the provider/registry" + "ivarna keeps current cards/behavior (no filter applied)"
  - Repo flavor precedent: `BuildConfig.APPLICATION_ID` (`TermuxHostPaths`, `HostBootstrap.ZENITHBLUE_PACKAGE` / `forApplicationId`); `buildConfig = true` in `app/build.gradle.kts:124-126`
  - Code is `main` source set for both flavors
- Impact: Worker guesses wrong gate → either ivarna cards filtered / HTTP path broken on ivarna, or zenithblue still shows non-Play cards → criteria 7/10 fail.
- Required planner change: Bind one flavor discriminator (prefer `BuildConfig.APPLICATION_ID == HostBootstrap.ZENITHBLUE_PACKAGE` / `ctx.packageName`, matching existing install code — not Compose-scattered FLAVOR). State explicitly: on ivarna, `RootfsPayloadProvider.supports` returns true for all current cards (or UI skips provider filter); on zenithblue, supports = registry hit only. Same discriminator for runner Step 2.4a–b.

#### [MAJOR] SplitCompat Activity-only install underspecified for ApplicationContext asset open
- Location: plan Step 1.6 / Step 2.4 `ZenithbluePayloadProviders.ensurePresent`
- Problem: Plan mandates `MainActivity.attachBaseContext` + `SplitCompat.install(this)` and forbids a custom Application. Provider/runner materialize via `applicationContext` (`OnboardingInstallRunner(context.applicationContext)`; `TermuxHostPaths.homeDir(appCtx)`). Play SplitCompat Activity install does not reliably patch `Application`/`applicationContext` AssetManagers; feature assets after `INSTALLED` are often opened via Application-scoped `SplitCompat.install` or `createPackageContext` + split AssetManager. Plan never names how `ensurePresent` opens the feature asset from a non-Activity context.
- Evidence:
  - Manifest `:20-29` — no `android:name` Application; `MainActivity.kt:71` plain `ComponentActivity`
  - `OnboardingFlowScreen.kt:133` / InstallConfig — `OnboardingInstallRunner(context.applicationContext)`
  - Plan Step 2.4: "open archive from feature context/split assets" — no API
  - Plan R1 notes SplitCompat required for module resources
- Impact: SplitInstall succeeds; materialization fails to open payload → criterion 8/15 fail; worker rediscovers Application vs Activity SplitCompat.
- Required planner change: Specify asset-open contract for `ZenithbluePayloadProviders.ensurePresent`: e.g. (1) if docs require Application `SplitCompat.install`, allow minimal Application subclass + manifest `android:name` as the SplitCompat site (override Step 1.6), **or** (2) keep Activity install and require `createPackageContext` / docs-current split AssetManager API with Context that SplitCompat has installed — name the exact call. Update §4 allowlist if Application is chosen.

#### [MINOR] Shared helper vs provider materialization ownership ambiguous
- Location: plan Step 3.1 / Step 2.4
- Problem: Step 3.1 shared helper "request module … on INSTALLED+verified materialization → runner.start"; Step 2.4 provider `ensurePresent` also request→copy→verify. Unclear whether helper wraps `provider.ensurePresent` or only SplitInstall (leaving runner verify-only with missing file).
- Evidence: Step 3.1 vs Step 2.4 wording; runner verify-only fails closed if `$HOME` file absent.
- Impact: Double SplitInstall or skip materialization → flaky install.
- Required planner change: One sentence — shared UI helper **calls** `RootfsPayloadProvider.ensurePresent` (request+materialize+verify); runner stays verify-only.

#### [MINOR] `HomeScreen.kt:148` is installed-list, not install-pick list
- Location: plan Step 3.3 / R7c
- Problem: `:148` filters already-installed cards for home UI; primary install-pick surfaces are `DistroScreen.kt:67-82` and `OnboardingFlowScreen.kt:819-822`. Gating home installed list is optional for criteria 5/§10.
- Evidence: `HomeScreen.kt:144-151` filesystem-installed filter; install CTA only on those cards.
- Impact: Low — extra allowlist edit; not wrong to gate, but mislabeled as equivalent pick-list site.
- Required planner change: Keep DistroScreen + Onboarding pick as mandatory filter sites; mark HomeScreen as optional/secondary (or gate only if an install CTA can reach non-registry ids).

### Axes checked
1. Architecture/ownership — SSOT/runner/UI/Play types OK; flavor ownership of `supports()` gap.
2. Data/control flow — SplitInstall → materialize → verify → runner; HTTP suppression now concrete; materialization ownership minor ambiguity.
3. Lifecycle/threading — SplitCompat ApplicationContext gap; foreground SplitInstall OK.
4. Persistence/errors — app-private `TermuxHostPaths.homeDir`; SHA reuse OK; fail-closed OK.
5. Compatibility/testing/scope — DFM checklist + Rule A/B + AC 1–16 OK; flavor gate + SplitCompat asset open block APPROVE.

## Next Agent: Planner
## Next Action: Revise `docs/plans/oneshot-rootfs-play-delivery-plan.md` to fix both MAJOR items (bind flavor discriminator for `supports()`/runner/UI filter; specify SplitCompat + feature-asset open for ApplicationContext, updating Step 1.6/§4 if Application required), plus the two MINOR clarifications; resubmit PASS 1 ITER 3 plan review.
