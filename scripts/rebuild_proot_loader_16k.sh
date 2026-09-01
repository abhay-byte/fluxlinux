#!/usr/bin/env bash
set -euo pipefail

# Rebuild the standalone ARM32 PRoot loader from the pinned upstream source.
# It is intentionally kept as a separate source build: the loader is an ELF
# executable copied into nativeLibraryDir, not a shared library that can be
# repaired with zip alignment or post-link byte edits.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT/native/third_party/proot/proot-5.1.107.84.zip"
SOURCE_SHA="a44ddbf18bc72c9780d56948b03aeda6d285392503ece0cae17cfc02e7bc7928"
NDK="${ANDROID_NDK_HOME:-/opt/android-sdk/ndk/29.0.14206865}"
EXPECTED_NDK="29.0.14206865"
WORK="$ROOT/build/proot-loader-16k"

if [[ ! -f "$SOURCE" ]]; then
  echo "ERROR: pinned PRoot source archive is missing: $SOURCE" >&2
  exit 2
fi
if [[ "$(sha256sum "$SOURCE" | awk '{print $1}')" != "$SOURCE_SHA" ]]; then
  echo "ERROR: PRoot source archive SHA-256 mismatch" >&2
  exit 2
fi
if [[ ! -f "$NDK/source.properties" ]] || ! grep -Eq "Pkg.Revision[[:space:]]*=[[:space:]]*$EXPECTED_NDK([[:space:]]|$)" "$NDK/source.properties"; then
  echo "ERROR: exact Android NDK $EXPECTED_NDK is required (got $NDK)" >&2
  exit 2
fi

rm -rf "$WORK"
mkdir -p "$WORK"
unzip -q "$SOURCE" -d "$WORK"
src="$WORK/proot-5.1.107.84"
arm32="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi24-clang"
strip="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
readelf="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
for tool in "$arm32" "$strip" "$readelf"; do
  [[ -x "$tool" ]] || { echo "ERROR: required NDK tool missing: $tool" >&2; exit 2; }
done

# On an ARM32 compiler the ordinary `loader/loader` make target is the
# loader32 implementation (same source as PRoot's -m32 target when PRoot is
# built for aarch64). No libc or guest package is involved.
make -C "$src/src" V=0 loader/loader \
  CC="$arm32" \
  CPPFLAGS='-D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -I. -I.' \
  CFLAGS='-Wall -Wextra -O2 -fPIC -ffreestanding' \
  LOADER_LDFLAGS='-static -nostdlib -Wl,-Ttext=0x20000000,--rosegment,-z,noexecstack,-z,max-page-size=16384'

output="$WORK/libloader32.so"
cp "$src/src/loader/loader" "$output"
"$strip" --strip-unneeded "$output"

file_output=$(file "$output")
if [[ "$file_output" != *"ELF 32-bit"* || "$file_output" != *"ARM"* ]]; then
  echo "ERROR: rebuilt loader is not a 32-bit ARM executable" >&2
  exit 1
fi
while IFS= read -r alignment; do
  [[ -n "$alignment" ]] || continue
  if (( alignment < 0x4000 )); then
    echo "ERROR: loader32 LOAD alignment $alignment is below 0x4000" >&2
    exit 1
  fi
done < <("$readelf" -lW "$output" | awk '$1 == "LOAD" {print $NF}')

for app_id in com.ivarna.fluxlinux com.zenithblue.fluxlinux; do
  destination="$ROOT/native/bootstrap/$app_id/jniLibs/arm64-v8a/libloader32.so"
  install -m 0755 "$output" "$destination"
done

echo "PASS: rebuilt and installed 16 KB-compatible libloader32.so"
sha256sum "$output" "$ROOT/native/bootstrap/com.ivarna.fluxlinux/jniLibs/arm64-v8a/libloader32.so" "$ROOT/native/bootstrap/com.zenithblue.fluxlinux/jniLibs/arm64-v8a/libloader32.so"
