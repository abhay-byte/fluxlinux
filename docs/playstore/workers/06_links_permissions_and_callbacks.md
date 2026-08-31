# Worker 06 — Remove Obsolete External-Termux Surface and Harden PRoot Bridges

## Goal
Remove legacy external-Termux/install integration from the embedded Play build, eliminate unsafe callback state mutation, and reduce unnecessary Android filesystem/API exposure to the PRoot guest without breaking normal Linux workflows.

## Code paths to audit first

- merged Play manifest;
- `InstallServerService` and any callers;
- `com.termux.permission.RUN_COMMAND` usage;
- Termux/Termux:X11 package queries and prerequisite UI;
- `fluxlinux://callback` intent filter/handler;
- APK/download/install helpers;
- PRoot bind-mount construction;
- guest-to-Android command bridges.

## Tasks

1. Confirm the Play manifest does not contain or regain:
   - `REQUEST_INSTALL_PACKAGES`;
   - `ACCESS_SUPERUSER`;
   - unknown-source/package-installer permissions.
2. Because the v2 Play architecture is embedded, remove from `zenithblue` unless a code-level dependency proves they are still required:
   - `com.termux.permission.RUN_COMMAND`;
   - external Termux/Termux:X11 package queries;
   - external-Termux prerequisite/onboarding UI;
   - deprecated `InstallServerService` external bridge;
   - legacy external install-server callbacks.
3. Remove APK/APKS/XAPK download/install flows, installer APIs and CTAs from the Play variant.
4. Audit the existing `fluxlinux://callback` BROWSABLE surface.
5. **Preferred result:** remove the browsable callback entirely if its purpose was external-Termux coordination.
6. If a callback is genuinely still required, replace legacy behavior with a fail-closed protocol:
   - unpredictable app-created operation nonce;
   - exact current operation/session binding;
   - fixed allowlisted enum/field values only;
   - single use + expiry;
   - reject stale/replayed/missing/malformed tokens;
   - never accept arbitrary command/script/path/URL input;
   - never pass callback data to a shell;
   - remove any fallback that simply marks a script/distro/install complete.
7. Audit all PRoot bind mounts and document the reason for every retained broad bind.
8. Prefer only required guest rootfs, `/proc`, required `/dev`, temp/shared-memory, app-controlled home, X11/Pulse sockets and explicit user-file access.
9. Avoid exposing unrelated app-private directories, `/system`, `/vendor`, or whole storage without a proven requirement.
10. Where practical, use a controlled shared-folder/SAF bridge for user files while preserving command-line usability.
11. Ensure guest processes remain under the FluxLinux Android UID and cannot invoke privileged Android app APIs through an unauthenticated command bridge.
12. Add negative callback/bridge tests.

## Tests

```bash
./gradlew test
./gradlew lintZenithblueRelease
./gradlew bundleZenithblueRelease
```

Static/artifact searches must cover installer APIs/permissions, old external-Termux strings/services/queries, browsable callback handlers and direct APK links.

ADB negative tests, if any callback remains:

- missing nonce;
- random/stale nonce;
- replayed valid nonce;
- arbitrary command;
- shell metacharacters;
- arbitrary path;
- arbitrary URL;
- unexpected enum/field;
- malformed URI.

All must fail without mutating install state or starting a command.

Device guest tests:

- home/temp/networking;
- required shared/user files;
- X11 and Pulse sockets;
- terminal/desktop lifecycle;
- no external Termux dependency.

## Acceptance

- obsolete external-Termux permission/query/service/prerequisite surface is gone from Play;
- no external Android package installer path exists;
- callback is removed or nonce-bound/fail-closed with negative tests;
- guest cannot use an unsafe Android command bridge;
- PRoot binds are documented/minimized without breaking normal workflows;
- terminal/desktop/file/network flows remain functional.

## Do not

Do not weaken validation for compatibility. Do not break ordinary guest networking/user files just to minimize bindings; replace broad access with a controlled mechanism when necessary.