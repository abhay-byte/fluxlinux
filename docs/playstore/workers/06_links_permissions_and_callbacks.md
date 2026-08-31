# Worker 06 — Clean Permissions, External Links, Callbacks, and PRoot Bridges

## Goal
Remove obsolete external-Termux/install permissions and reduce the Android surface exposed to the embedded Linux guest.

## Tasks
1. Confirm Play manifest does **not** contain:
   - `REQUEST_INSTALL_PACKAGES`;
   - `ACCESS_SUPERUSER`;
   - unknown-source installer permissions/APIs.
2. Because Play now uses the embedded runtime, remove `com.termux.permission.RUN_COMMAND`, Termux/Termux:X11 package queries, and prerequisite UI **if no remaining embedded component truly needs them**.
3. Remove APK download/install flows, direct `.apk/.apks/.xapk` installation CTAs, and package-installer code.
4. Audit browsable intents/deep links, especially `fluxlinux://callback`:
   - accept only fixed expected fields;
   - bind to active app-created operation/session IDs;
   - reject arbitrary command/script/path/URL injection;
   - reject replay/stale callbacks.
5. Audit PRoot bind mounts. Keep only what is needed for Linux functionality.
6. Avoid broad `/data`, `/system`, `/vendor`, and whole-storage exposure unless a documented requirement/test proves it is necessary.
7. Prefer an explicit shared folder/SAF bridge for user files where practical.
8. Ensure guest processes remain under the FluxLinux Android UID and cannot call privileged Android app APIs through an unsafe command bridge.
9. Add negative callback/bridge tests.

## Tests
```bash
./gradlew test
./gradlew lintZenithblueRelease
./gradlew bundleZenithblueRelease
```

Run static searches for installer APIs, direct APK URLs, old Termux prerequisite strings, and dangerous callback command execution.

ADB negative tests should attempt malformed callbacks, arbitrary commands, stale IDs, shell metacharacters, and untrusted URLs; all must fail safely.

Device test required guest access: home, temp, networking, selected/shared files, X11/Pulse sockets.

## Acceptance
- obsolete external-Termux permissions/queries are gone when not needed;
- no external app installer path exists;
- callbacks cannot inject arbitrary commands;
- PRoot bridge/binds are documented and minimized;
- normal distro/desktop/file workflows still work.

## Do not
Do not break ordinary guest networking or user file access merely to minimize binds; replace broad access with a controlled bridge where necessary.