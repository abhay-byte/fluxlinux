# Worker 04 — Package Host Executables Safely and Remove Nested APK

## Goal
Keep the embedded host backend, but ensure Android-host executables are installed by Google Play in executable native-library locations instead of copied from writable assets.

## Tasks
1. Inventory every executable/ELF/native library currently shipped under `assets`, scripts, bootstrap tarballs, or writable host-prefix setup.
2. Classify each file as:
   - Android host executable/native library;
   - guest/rootfs binary;
   - interpreted script/config/data;
   - obsolete.
3. Move Android-host executables required by Play into `jniLibs/<abi>` or a proper native module. Use `applicationInfo.nativeLibraryDir` as the canonical runtime location.
4. Where existing scripts expect stable paths such as `$PREFIX/axs`/PRoot helpers, create symlinks to `nativeLibraryDir`; **do not copy the executable bytes into `$filesDir`**.
5. Preserve the existing PRoot environment variables/loaders, adapting only their source paths.
6. Remove `assets/loader.apk` and `loader.bin` if it contains the same APK bytes.
7. Integrate required X11 loader code as a normal Android dependency/module or replace the nested-loader mechanism with an in-process implementation.
8. Split `bootstrap.tar` contents:
   - host executable -> `jniLibs`;
   - non-executable scripts/config -> normal Play assets;
   - large guest data -> PAD if appropriate.
9. Remove Play code that `chmod +x` copies of host ELF/`.so` content from generic assets.
10. Add tests/audit for ELF/APK magic under generic assets.

## Tests
```bash
./gradlew test
./gradlew bundleZenithblueRelease
unzip -l <aab>
```

Inspect generated APKs/AAB:
- expected host `.so` files are under native library locations;
- no nested `.apk` exists;
- no APK disguised as `.bin`;
- no unexpected host ELF exists under generic assets.

Device smoke test:
- embedded shell starts;
- PRoot starts;
- one PAD-delivered distro launches;
- X11/Pulse startup still works.

## Acceptance
- Android host executables are Play-installed native code;
- no writable executable-copy path remains for host binaries;
- nested loader APK is gone;
- existing backend uses native-library paths/symlinks successfully;
- rootfs guest binaries remain handled by PRoot after PAD extraction.

## Do not
Do not remove the embedded host simply to make the audit easier. Do not convert same-architecture PRoot execution to QEMU.