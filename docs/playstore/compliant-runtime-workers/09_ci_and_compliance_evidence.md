# DEPRECATED — Use Canonical Worker 09

This file belongs to the superseded QEMU-first worker sequence.

Use:

- `../workers/09_ci_policy_gate.md`
- `../v2_0_compliance_roadmap.md`

The canonical CI gate validates the `zenithblue` source/wiring and final AAB, checks Play Feature Delivery modules/provenance, distinguishes expected guest ELF in rootfs archives from unexpected Android-host executable payloads, and blocks remote Play rootfs/bootstrap fallbacks, root/install permissions, nested APKs and obsolete external-Termux integration.