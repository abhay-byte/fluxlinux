# DEPRECATED — Do Not Execute

This QEMU-first worker was superseded after the FluxLinux v2 code and Play delivery architecture were reviewed.

The current Play design keeps the existing same-architecture native PRoot backend and uses **Play Feature Delivery dynamic-feature modules** for the Flux-managed host runtime and distro/rootfs payloads.

Use the canonical plan instead:

- `../v2_0_compliance_roadmap.md`
- `../workers/01_branch_baseline.md`
- then continue `../workers/02...10` in order.

QEMU is not the default Play backend. Reconsider it only if a future explicit platform/policy requirement makes the reviewed PRoot design unsuitable.