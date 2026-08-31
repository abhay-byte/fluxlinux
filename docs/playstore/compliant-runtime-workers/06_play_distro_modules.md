# DEPRECATED — Use Canonical Worker 03

This older worker is superseded by the reviewed Play Feature Delivery plan.

Use:

- `../workers/03_remove_remote_executable_delivery.md`
- `../v2_0_compliance_roadmap.md`

Canonical Worker 03 creates `:runtime_host` and one on-demand **dynamic-feature module** per supported distro, verifies/materializes each payload, and then reuses the existing FluxLinux PRoot installer.

Do not implement distro rootfs payloads as Play Asset Delivery asset packs.