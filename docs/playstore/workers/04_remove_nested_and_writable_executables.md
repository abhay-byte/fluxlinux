# Worker 04 — Remove Nested APKs & Writable Executables

## Goal
Make the Play AAB free of nested APK payloads and executable binaries copied into writable app storage.

## Do
1. Remove `assets/loader.apk` from the Play source set.
2. Remove `assets/loader.bin` if it is APK-identical content.
3. Replace loader deployment with normal Android module/dependency integration, or compile it out of Play.
4. Audit `assets/` with `file`/magic checks for ELF, APK, DEX, JAR, and executable payloads.
5. Move any Play-required `.so` to `jniLibs/<abi>` / native module and access through `nativeLibraryDir`.
6. Remove Play paths that copy `.so`/ELF to `$filesDir` and chmod executable.
7. Exclude non-Play binaries such as chroot helpers from `zenithblue`.

## Tests
```bash
./gradlew :app:bundleZenithblueRelease
unzip -l app/build/outputs/bundle/zenithblueRelease/*.aab | grep -Ei '\.(apk|dex|jar)$' || true
# scan extracted assets with file(1)
rm -rf /tmp/flux-aab && mkdir /tmp/flux-aab
unzip -q app/build/outputs/bundle/zenithblueRelease/*.aab -d /tmp/flux-aab
find /tmp/flux-aab -path '*/assets/*' -type f -print0 | xargs -0 file | grep -Ei 'ELF|Android package|Dalvik|Java archive' && exit 1 || true
```
Allow only explicitly reviewed artifacts and document every exception.

## Acceptance
- No nested APK in Play AAB.
- No APK-disguised `.bin`.
- No unexpected ELF under assets.
- Required Play native code is packaged via Android native-library mechanisms.
- App starts and required Play functionality still works.
