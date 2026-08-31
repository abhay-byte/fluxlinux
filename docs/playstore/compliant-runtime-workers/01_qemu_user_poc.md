# Worker 01 — QEMU User-Mode Interpreter POC

## Goal

Prove that an ARM64 Linux guest executable can run on Android API 33–36 **only through a Play-packageable QEMU user-mode TCG engine**, never through direct Android guest `execve`.

Read first:

- `docs/playstore/full_v2_compliant_delivery_execution_roadmap.md`
- current v2 PRoot/W^X implementation

## Scope

Build an isolated experimental path only. Do not migrate product UI or all distros.

Use Alpine or another tiny AArch64 rootfs fixture.

## Requirements

1. Build QEMU AArch64 user-mode for Android/arm64 as a Play-packageable native component.
2. Do not download QEMU at runtime.
3. Run at minimum:
   - `/bin/sh -c 'echo FLUX_QEMU_OK'`
   - `uname -a`
   - a dynamically linked utility
   - file create/read/delete
4. Guest ELF must be opened/translated by QEMU, not directly passed to Android `execve`.
5. If QEMU fails, fail closed. No native fallback.
6. Record startup time, CPU usage, RAM and obvious compatibility issues.

## Tests

Add a POC test/harness proving:

- QEMU engine exists in Play-packageable native location.
- direct guest execution path is not used.
- missing engine returns failure.
- malformed guest path cannot execute host binary.

Device-test API 33, 34, 35 and 36 where available.

## Deliverable

Create:

`docs/playstore/evidence/qemu_user_poc.md`

Include commands, device/API, outputs, performance numbers and limitations.

## Acceptance

- [ ] QEMU user-mode engine builds for Android arm64.
- [ ] Alpine/fixture shell runs through QEMU.
- [ ] Dynamic guest ELF works.
- [ ] No direct guest `execve` fallback exists in POC.
- [ ] API 34–36 primary tests pass.
- [ ] Evidence report committed.

Do not start Worker 02 unless this worker is PASS.
