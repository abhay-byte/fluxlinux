# Worker 08 — Mediated GPU Acceleration

## Goal

Restore GPU acceleration without letting guest code directly load arbitrary Android host drivers or escape the VM/interpreter boundary.

## Preconditions

Terminal/XFCE software rendering from Worker 07 must work first.

## Phase 1 — VirGL-style mediation

Investigate/build a Play-delivered host renderer path:

```text
Guest Mesa/virgl
 -> controlled protocol/socket
 -> Play-delivered virglrenderer host component
 -> Android GL/Vulkan
```

The host renderer is part of the Play artifact/module and is not replaceable by guest package management.

## Phase 2 — Vulkan

After VirGL is stable, investigate Venus/virtio-style Vulkan mediation or an equivalent explicit protocol.

Do not reuse the current Turnip path if it requires guest code to directly load host Android driver `.so` files or access sensitive device nodes outside a documented safe boundary.

## Security rules

- no guest library becomes host JNI
- no runtime host GPU driver download
- no arbitrary host `.so` path accepted from guest
- no broad `/vendor` bind
- no direct guest access to app `nativeLibraryDir`
- renderer protocol inputs treated as untrusted

## Benchmarks

Compare software vs mediated GPU:

- glmark2 or suitable guest benchmark
- XFCE responsiveness
- simple browser/UI workload
- RAM
- CPU
- thermal load

## Acceptance

- [ ] GPU transport is mediated through a fixed Play-delivered host component.
- [ ] Guest cannot choose/load arbitrary host driver files.
- [ ] At least one graphical workload accelerates over software path, or blocker is documented with evidence.
- [ ] Sandbox/anti-escape tests remain green.
