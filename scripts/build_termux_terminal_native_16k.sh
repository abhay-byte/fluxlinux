#!/usr/bin/env bash
set -euo pipefail

# Build the tiny JNI native library from the pinned Termux terminal-emulator
# source. The Maven AAR remains the source of Java classes; this local output
# is placed in app/src/main/jniLibs so the application source set wins the
# duplicate-native merge deterministically.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NDK="${ANDROID_NDK_HOME:-/opt/android-sdk/ndk/29.0.14206865}"
EXPECTED_NDK="29.0.14206865"
SOURCE_DIR="$ROOT/native/third_party/termux-terminal-emulator"
WORK="$ROOT/build/termux-terminal-native-16k"
LIBS="$WORK/libs"
DEST="$ROOT/app/src/main/jniLibs"

if [[ ! -f "$NDK/source.properties" ]] || ! grep -Eq "Pkg.Revision[[:space:]]*=[[:space:]]*$EXPECTED_NDK([[:space:]]|$)" "$NDK/source.properties"; then
  echo "ERROR: exact Android NDK $EXPECTED_NDK is required (got $NDK)" >&2
  exit 2
fi
if [[ ! -x "$NDK/ndk-build" ]]; then
  echo "ERROR: ndk-build missing under $NDK" >&2
  exit 2
fi

rm -rf "$WORK"
mkdir -p "$LIBS" "$DEST"

"$NDK/ndk-build" \
  NDK_PROJECT_PATH="$WORK" \
  APP_BUILD_SCRIPT="$SOURCE_DIR/Android.mk" \
  APP_ABI='arm64-v8a armeabi-v7a x86 x86_64' \
  APP_PLATFORM=android-24 \
  NDK_LIBS_OUT="$LIBS" \
  NDK_OUT="$WORK/obj" \
  V=0

readelf="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
[[ -x "$readelf" ]] || { echo "ERROR: llvm-readelf missing under $NDK" >&2; exit 2; }

for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  src="$LIBS/$abi/libtermux.so"
  [[ -f "$src" ]] || { echo "ERROR: ndk-build did not produce $src" >&2; exit 1; }
  mkdir -p "$DEST/$abi"
  install -m 0755 "$src" "$DEST/$abi/libtermux.so"
done

# The Play release gate scans arm64. Keep the other ABI copies for devices
# supported by the upstream terminal classes, but require the 64-bit copies to
# carry the same 16 KB LOAD contract as the arm64 Play artifact.
for abi in arm64-v8a x86_64; do
  while IFS= read -r alignment; do
    [[ -n "$alignment" ]] || continue
    if (( alignment < 0x4000 )); then
      echo "ERROR: $abi/libtermux.so LOAD alignment $alignment is below 0x4000" >&2
      exit 1
    fi
  done < <("$readelf" -lW "$DEST/$abi/libtermux.so" | awk '$1 == "LOAD" {print $NF}')
done

echo "PASS: built 16 KB-compatible libtermux.so copies from pinned source"
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  sha256sum "$DEST/$abi/libtermux.so"
done
