# FluxLinux Play Compliance — Worker Agent Prompt

Use this prompt together with exactly **one** file from `docs/playstore/workers/`.

## Mission

Implement the assigned worker for the FluxLinux Google Play v2 migration while preserving the reviewed architecture and proving the result with tests/artifact inspection.

Repository: `abhay-byte/fluxlinux`

Canonical roadmap: `docs/playstore/v2_0_compliance_roadmap.md`

Policy guide: `abhay-byte/abhay-kb/Google_Play_Store_Policy_Compliance_Guide.md`

## Non-negotiable architecture

- Play flavor/package: `zenithblue` / `com.zenithblue.fluxlinux`.
- Preserve the embedded v2 same-architecture **native PRoot** backend.
- Preserve terminal, rootfs extraction/configuration, X11, PulseAudio and session management unless the assigned worker proves a specific incompatible path.
- Do not replace native PRoot with QEMU as the default backend.
- Do not revert Play to an external-Termux dependency.
- Executable-bearing Flux-managed host/runtime/rootfs payloads for Play use **Play Feature Delivery dynamic-feature modules**, not Play Asset Delivery.
- Small directly executed Android-host native launchers remain in normal Play-installed native-library locations; preserve the existing targetSdk-36/W^X-safe `TermuxHostPaths` design.
- Play must have no Flux-managed GitHub/HTTP rootfs/bootstrap executable fallback.
- Preserve PRoot fake root; remove Android-level root/chroot from Play.
- Do not call PRoot a VM/interpreter.
- Do not hide behavior with renamed extensions, obfuscation, runtime flags or scanner tricks.

## Before editing

1. Confirm current branch/status/log.
2. Read `docs/playstore/v2_0_compliance_roadmap.md`.
3. Read only the assigned canonical worker file plus prerequisite worker completion notes.
4. Inspect the actual current code paths named by the worker with `rg`/IDE/code search.
5. Record the baseline build/test status before changing behavior.
6. Re-check current Android/Google Play documentation for any API/policy assumption that affects the assigned worker.

## Scope discipline

Implement exactly the assigned worker plus:

- compile fixes caused directly by the worker;
- tests required by the worker;
- narrowly necessary refactors to establish the requested boundary.

Do not opportunistically implement later workers.

Keep `ivarna` functionality available behind build-time source/dependency boundaries unless the assigned worker explicitly changes it.

## Required testing behavior

- Discover actual Gradle task names; do not assume a task exists.
- Run the worker's unit/build/lint tests.
- If common code changes, compile/test both `zenithblue` and `ivarna`.
- Add regression tests for the policy/security invariant introduced by the worker.
- Inspect the **final Play variant/artifact**, not only source files.
- Do not blanket-reject guest ELF files inside expected rootfs archives. Distinguish guest payloads from Android-host executable code.

## Play-specific deny list

The final Play artifact must not regain functional:

- `REQUEST_INSTALL_PACKAGES` / Android package installer flows;
- `ACCESS_SUPERUSER`;
- Magisk/KernelSU/APatch/real chroot;
- Flux-managed rootfs/bootstrap executable download from GitHub/HTTP;
- remote `FLUX_ROOTFS_URL` fallback;
- nested/disguised loader APK;
- deprecated external-Termux install-server/RUN_COMMAND bridge;
- unauthenticated browsable callback that mutates install state or executes commands.

## Worker completion report

Return all of the following:

### Status
`PASS`, `PARTIAL`, or `BLOCKED`.

### Changes
- files changed;
- architectural behavior changed;
- policy/security risk removed.

### Tests
For each command/test:

- exact command;
- result;
- important warnings/failures;
- whether failure was pre-existing or introduced.

### Artifact inspection
State what was inspected in the generated `zenithblue` output/AAB and the result.

### Acceptance criteria
Copy every acceptance criterion from the assigned worker and mark it `PASS/FAIL/NOT TESTED` with evidence.

### Regressions
State whether `ivarna`, terminal, PRoot, X11, Pulse, distro setup or upgrade behavior changed.

### Remaining issues
List only concrete remaining blockers/debt created or discovered by this worker.

### Next worker
Name the next canonical worker, but **do not implement it**.

## Definition of done

A worker is not `PASS` merely because code compiles. It is `PASS` only when its implementation, tests, required artifact checks and acceptance criteria all pass or any explicitly manual final-track test is clearly deferred to Worker 10 by the assigned plan.