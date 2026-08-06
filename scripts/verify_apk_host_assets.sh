#!/usr/bin/env bash
# verify_apk_host_assets.sh — unzip checks that the built APK contains the host
# assets for the given flavor (bootstrap.tar STORED, rootfs, jniLibs).
#
# Usage:
#   ./scripts/verify_apk_host_assets.sh app/build/outputs/apk/ivarna/debug/app-ivarna-debug.apk
#   ./scripts/verify_apk_host_assets.sh app/build/outputs/apk/zenithblue/debug/app-zenithblue-debug.apk
set -euo pipefail

APK="${1:-}"
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  echo "Usage: $0 <path-to-apk>" >&2
  exit 2
fi

fail=0

echo "=== verifying host assets in $APK ==="

for entry in \
  "assets/bootstrap.tar" \
  "assets/rootfs/debian_13_rootfs.tar.xz" \
  "lib/arm64-v8a/libbash.so" \
  "lib/arm64-v8a/libproot.so" \
  "lib/arm64-v8a/libloader.so" \
  "lib/arm64-v8a/libloader32.so"; do
  # grep -c (not -q): -q closes the pipe early → SIGPIPE on unzip under pipefail
  if [ "$(unzip -l "$APK" | grep -c " $entry$")" -gt 0 ]; then
    echo "  [OK] $entry"
  else
    echo "  [MISSING] $entry"
    fail=1
  fi
done

# noCompress gate: archives must be STORED, not DEFLATE
for entry in "assets/bootstrap.tar" "assets/rootfs/debian_13_rootfs.tar.xz"; do
  method=$(unzip -v "$APK" | grep " $entry$" | awk '{print $2}')
  if [ "$method" = "Stored" ]; then
    echo "  [OK] $entry stored uncompressed"
  else
    echo "  [FAIL] $entry compressed ($method) — check androidResources.noCompress"
    fail=1
  fi
done

if [ "$fail" -eq 0 ]; then
  echo "PASS"
else
  echo "FAIL"
  exit 1
fi
