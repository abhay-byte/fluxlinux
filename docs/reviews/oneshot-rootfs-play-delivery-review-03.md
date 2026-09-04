# Plan Review: oneshot-rootfs-play-delivery — PASS 1 ITER 3

## Verdict: REVISE
## Counts: CRITICAL 0 MAJOR 1 MINOR 1 SUGGESTIONS 0

### Prior findings (review-02) — disposition

| ID | Title | Status |
|----|-------|--------|
| MAJOR flavor-gate | `supports()` / runner / UI discriminator | **FIXED** — `isZenithblue(ctx) = packageName == HostBootstrap.ZENITHBLUE_PACKAGE`; ivarna `supports`=all / filter skipped; zenithblue registry-hit-only; same gate for runner Step 2.4a–b |
| MAJOR SplitCompat | ApplicationContext asset-open | **FIXED** — minimal `FluxLinuxApp` + manifest `android:name`; Activity defense-in-depth; named `appCtx.assets.open` + `createPackageContext` fallback |
| MINOR helper ownership | shared helper vs provider | **FIXED** — helper MUST call `RootfsPayloadProvider.ensurePresent`; runner verify-only |
| MINOR HomeScreen | install-pick vs installed-list | **FIXED** in Step 3.3 (OPTIONAL); residual test-text lag → new MINOR below |

Review-01 CRITICAL/MAJOR/MINOR/SUGGESTION items remain FIXED (no reopen). No regression reopening those.

### Findings

#### [MAJOR] Feature asset path: packaging uses module name, open uses distroId
- Location: plan Step 1.5 / Step 1.6 / Step 2.4 `ZenithbluePayloadProviders.ensurePresent`
- Problem: Packaging layout (spec + Step 1.5) is `…/assets/payloads/distro_alpine/<file>` (module / split name). Asset-open contract (Step 1.6 / 2.4) is `appCtx.assets.open("payloads/<distroId>/<file>")`. For all 7 ids these disagree (`alpine` ≠ `distro_alpine`; `archlinux` ≠ `distro_arch`). Worker has two contradictory paths; wrong open → materialization fails after `INSTALLED`.
- Evidence:
  - Source spec §2 example: `distro_alpine/src/zenithblue/assets/payloads/distro_alpine/<existing-rootfs-file>`
  - Plan Step 1.5: same `payloads/distro_alpine/…`
  - Plan Step 1.6 + 2.4: `payloads/<distroId>/<file>`
  - Registry maps `archlinux` → module `distro_arch` (Step 0 / Step 1 includes)
- Impact: Criteria 8/15 fail (SHA/materialize never sees bytes); rediscovery of path contract.
- Required planner change: Bind ONE asset-relative path for stage + open. Prefer registry `moduleName`: stage and open `payloads/<moduleName>/<archiveFileName>` (matches spec layout). Replace every `payloads/<distroId>/…` in Step 1.6 / 2.4 / handoff with that. Note arch: distroId `archlinux`, module `distro_arch`.

#### [MINOR] TEST_STRATEGY / Step 5 still say “three” filter call sites
- Location: plan Step 5 / §6 TEST_STRATEGY
- Problem: Step 3.3 correctly marks `HomeScreen.kt:148` OPTIONAL; Step 5 and TEST_STRATEGY still require “three call sites” / “three filter call sites”.
- Evidence: Step 3.3 OPTIONAL HomeScreen vs Step 5 line “filter at the three call sites”; §6 “three filter call sites”.
- Impact: Worker over-scopes HomeScreen tests or thinks mandatory filter incomplete.
- Required planner change: Align Step 5 + TEST_STRATEGY with Step 3.3 — mandatory DistroScreen + Onboarding only; HomeScreen optional.

### Axes checked
1. Architecture/ownership — SSOT, provider, runner, FluxLinuxApp OK; asset-path ownership gap.
2. Data/control flow — SplitInstall → ensurePresent → verify-only runner; HTTP suppression intact; path string mismatch.
3. Lifecycle/threading — Application SplitCompat + named open OK once path fixed.
4. Persistence/errors — `TermuxHostPaths.homeDir`; `isValid`/`sha256` reuse; fail-closed OK.
5. Compatibility/testing/scope — DFM checklist, Rule A/B, AC 1–16, flavor gate OK; asset path blocks APPROVE.

## Next Agent: Planner
## Next Action: Revise `docs/plans/oneshot-rootfs-play-delivery-plan.md` — bind asset path to `payloads/<moduleName>/<file>` everywhere (Step 1.6/2.4/handoff); align Step 5 + TEST_STRATEGY with optional HomeScreen; resubmit PASS 1 ITER 4 plan review.
