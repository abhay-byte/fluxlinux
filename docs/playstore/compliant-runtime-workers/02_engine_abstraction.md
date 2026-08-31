# Worker 02 — Guest Execution Engine Abstraction

## Goal

Refactor FluxLinux so terminal/product code no longer assumes direct PRoot/chroot execution. Introduce an engine boundary that supports:

- native PRoot for non-Play `ivarna`
- QEMU-user interpreter for Play `zenithblue`
- future full-system QEMU without UI rewrite

Read:

- `docs/playstore/full_v2_compliant_delivery_execution_roadmap.md`
- Worker 01 evidence
- `LinuxCommandBuilder.kt`
- `GuestSessionFactory.kt`
- `ProotCommandBuilder.kt`
- `ChrootCommandBuilder.kt`

## Implement

Introduce small APIs such as:

```kotlin
interface GuestExecutionEngine
interface GuestTerminalChannel
interface GuestEngineProvider
```

Exact names may vary, but keep them focused.

Move selection behind flavor-aware providers:

- `ivarna` -> existing native PRoot/chroot engine
- `zenithblue` -> QEMU-user engine

The Play path must not silently instantiate native guest execution.

Refactor terminal/session creation so Compose/UI does not care which engine is used.

## Rules

- Preserve `ivarna` behavior.
- Do not port all distro install code yet.
- Do not use a runtime boolean as the only compliance boundary.
- QEMU missing/incompatible -> explicit failure, not native fallback.

## Tests

- provider chooses correct engine by flavor
- Play provider cannot return native PRoot engine
- engine failure propagates to UI/session layer
- native `ivarna` guest shell regression tests remain green

## Acceptance

- [ ] Engine-neutral interface exists.
- [ ] Play and non-Play providers are source/build separated.
- [ ] `GuestSessionFactory` no longer hardcodes native guest execution as the only model.
- [ ] No Play native fallback.
- [ ] Existing `ivarna` tests/build remain green.
- [ ] `zenithblue` debug build uses QEMU POC engine.
