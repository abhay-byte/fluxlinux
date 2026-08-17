# v2.0.0

## What's Changed

### Features
- Embed the Termux-prefix host and X11 server — no external Termux APK required
- Multi-distro catalog (12 guests) with on-demand GitHub `rootfs` downloads (SHA256-gated)
- Host PulseAudio as the app uid; guests are TCP clients on 127.0.0.1
- W^X-safe exec of host bins from `nativeLibraryDir` (targetSdk 36)
- Settings → PRoot / Chroot storage list, size, and stop
- Settings → Legacy Termux leftover management for ≤ v1.8.0 containers
- Universal guest login shell toggle (bash / zsh)
- Chroot uses Magisk/KernelSU/APatch BusyBox — no NDK BusyBox module
- Compile `libXlorie.so` from source (NDK 29 + CMake, 16 submodules)
- F-Droid recipe for flavor `ivarna` (`submodules: true`, scanner-accurate `scanignore`)

### Bug Fixes
- Align `LorieView` JNI with the native per-instance API (`nativeInit()J`) so X11 display opens
- Fedora chroot flux login via staged `setuidgid` (no `chroot --userspec` on Android busybox)
- Host Pulse start/stop/query without PREFIX exec or root
- Restore `loader.apk` after the F-Droid scanner deletes `*.apk` (`loader.bin` twin)

### F-Droid
- `gradle: [ivarna]`; do not compile termux-packages on `buildserver-trixie`
- Lint + scanner 0 problems; `assembleIvarnaRelease` SUCCESS on the F-Droid image

## Migration Notes
External Termux is no longer required. Rootfs archives leave the APK and download from the GitHub `rootfs` tag. Leftover v1.8.0 Termux PRoot trees: Settings → Legacy Termux.

## Verification
- Version 2.0.0 (versionCode 12): `app/build.gradle.kts`, `com.ivarna.fluxlinux.yml`, `fastlane/README.md`, `fastlane/.../changelogs/12.txt`.
- Release APK built from the tagged commit; GitHub asset name `app-release.apk` (F-Droid `Binaries:`).
