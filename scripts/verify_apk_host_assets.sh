#!/usr/bin/env bash
# verify_apk_host_assets.sh — unzip checks that the built APK contains the host
# assets for the given flavor (bootstrap.tar STORED, jniLibs) and — since rootfs
# moved to GitHub release download — contains ZERO assets/rootfs/* entries
# (negative gate, docs/plans/rootfs-github-release-no-apk-bloat.md P5-T3).
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
for entry in "assets/bootstrap.tar"; do
  method=$(unzip -v "$APK" | grep " $entry$" | awk '{print $2}')
  if [ "$method" = "Stored" ]; then
    echo "  [OK] $entry stored uncompressed"
  else
    echo "  [FAIL] $entry compressed ($method) — check androidResources.noCompress"
    fail=1
  fi
done

# Negative rootfs gate: rootfs archives ship via the GitHub release tag `rootfs`,
# never inside the APK.
rootfs_entries=$(unzip -l "$APK" | grep -c "assets/rootfs/" || true)
if [ "$rootfs_entries" -eq 0 ]; then
  echo "  [OK] zero assets/rootfs/* entries (rootfs downloads at install time)"
else
  echo "  [FAIL] APK contains $rootfs_entries assets/rootfs/* entries — must be zero"
  unzip -l "$APK" | grep "assets/rootfs/" || true
  fail=1
fi

if [ "$fail" -eq 0 ]; then
  echo "PASS"
else
  echo "FAIL"
  exit 1
fi
