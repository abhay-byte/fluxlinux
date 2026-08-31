# DEPRECATED — Do Not Execute

The interpreter-only/QEMU sandbox is no longer the reviewed Play architecture.

The canonical design keeps same-architecture native PRoot, removes Android-level root/chroot, removes unsafe external command/callback bridges, and hardens PRoot filesystem exposure.

Use:

- `../workers/05_remove_root_chroot_from_play.md`
- `../workers/06_links_permissions_and_callbacks.md`
- `../v2_0_compliance_roadmap.md`

Do not introduce QEMU as a compatibility fallback in the Play flavor without a new architecture review.