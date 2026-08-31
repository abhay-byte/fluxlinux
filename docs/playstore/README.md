# Play Store Documentation

Documents related to publishing and maintaining FluxLinux on the Google Play Store.

## Current v2 plans

| File | Purpose |
|------|---------|
| [`v2_0_compliance_roadmap.md`](v2_0_compliance_roadmap.md) | Immediate v2 Play compliance roadmap: protect the old Play build, isolate risky v2 features, clean permissions/FGS/metadata, and build release gates. |
| [`full_v2_compliant_delivery_execution_roadmap.md`](full_v2_compliant_delivery_execution_roadmap.md) | Long-term/full-parity architecture: Google Play Feature Delivery for distro payloads plus VM/interpreter-only guest execution so package managers and Linux guest code can remain usable without direct Android execution. |
| [`workers/`](workers/) | Small workers for the immediate conservative Play compliance migration. |
| [`compliant-runtime-workers/`](compliant-runtime-workers/) | Small workers for implementing and proving the full v2 Play-compliant delivery/execution architecture. |

## Policy/reference documents

| File | Purpose |
|------|---------|
| [`policies_and_violations.md`](policies_and_violations.md) | Historical/current repo-specific Google Play findings and remediation guidance. |
| `privacy_policy.md` | Play-specific privacy policy. Must always match the actual Play artifact and Play Console declarations. |

## Recommended workflow

### A. Immediate safe Play release

Use `v2_0_compliance_roadmap.md` and `workers/01...10` to create a conservative v2 Play artifact without carrying policy-risky direct guest execution into production.

### B. Restore full v2 Linux functionality

Use `full_v2_compliant_delivery_execution_roadmap.md` and `compliant-runtime-workers/01...10`.

The critical proof sequence is:

1. QEMU/interpreter POC.
2. Engine abstraction.
3. Real Play Feature Delivery POC.
4. Interpreter-only/fail-closed sandbox.
5. Install a package from a normal Linux repository and prove the new executable still runs only inside the interpreter.

Do not convert all distros or optimize GPU before these five gates pass.

## Core Play invariant

For the full-parity Play architecture:

> Google Play delivers FluxLinux's Android-side execution engine. Linux guest executables are never directly executed or loaded by Android; they execute only as guest code inside the VM/interpreter boundary.

Do not weaken this invariant for performance or compatibility fallbacks.

## Related

- Fastlane metadata lives at `fastlane/metadata/android/en-US/`.
- Play package identity must remain `com.zenithblue.fluxlinux`.
- Live Google Play policy always overrides repository snapshots when policies change.
