# DEPRECATED — Covered by Canonical Workers

The terminal/desktop/audio work no longer follows a separate QEMU runtime track. The reviewed architecture preserves FluxLinux v2's existing embedded terminal, X11 and PulseAudio backend while changing delivery and policy-sensitive Android integration.

Use:

- `../workers/04_remove_nested_and_writable_executables.md` for host/W^X/X11-loader compatibility;
- `../workers/07_foreground_services.md` for long-running terminal/desktop lifecycle;
- `../workers/10_release_validation.md` for end-to-end terminal/X11/Pulse testing.
