# Worker 10 — Internal Play Validation and Release Decision

## Goal

Validate the complete compliant-runtime architecture through Google Play delivery before production.

## Required setup

Use Internal App Sharing and/or an Internal Testing track.

The app must be installed from a Google Play-generated artifact, not `adb install` of a locally built APK for the final validation.

## Test matrix

API 33, 34, 35 and 36 where available.

Test:

1. fresh Play install
2. on-demand engine/module availability
3. on-demand distro installation
4. shell startup
5. package repository update
6. install a package not present in base distro
7. execute newly installed package under interpreter
8. guest root/sudo behavior
9. network
10. terminal resize/session lifecycle
11. XFCE/software rendering
12. audio if implemented
13. GPU if implemented
14. background/foreground lifecycle
15. no-network/error/retry
16. module uninstall/reinstall
17. app update with installed distro state

## Policy negative validation

Confirm Play build cannot:

- execute guest ELF directly
- download/update QEMU/engine outside Play
- request Android root
- run Android chroot
- install APKs
- load guest `.so` as host native code
- fall back to GitHub rootfs delivery when module install fails

## Play support/review package

Prepare a concise architecture explanation and request written clarification/support review for the exact VM/interpreter architecture before broad production rollout.

Include:

- architecture diagram
- Play Feature Delivery flow
- QEMU/interpreter boundary
- package-manager flow
- permissions
- reviewer demo video
- artifact scan results

Do not claim approval until written confirmation/release acceptance exists.

## Release decision

PASS only when:

- Workers 01–09 are PASS
- real Play delivery works
- device matrix is acceptable
- policy guards pass
- evidence is complete
- no blocker/high compliance finding remains

If QEMU-user boundary is questioned by Play, stop production and execute the documented **full-system QEMU fallback** investigation rather than restoring native PRoot execution.

## Acceptance

- [ ] Real Play-hosted install validated.
- [ ] PFD distro delivery validated.
- [ ] externally installed guest package runs under interpreter.
- [ ] negative policy tests pass.
- [ ] API 34–36 primary matrix passes.
- [ ] reviewer evidence ready.
- [ ] release decision documented.
