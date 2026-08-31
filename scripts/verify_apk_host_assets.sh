#!/usr/bin/env bash
# verify_apk_host_assets.sh — unzip checks that the built base APK contains the
# directly executed host jniLibs and no executable payload archives. The Play
# host/rootfs archives are delivered by runtime_host and distro_* dynamic
# features and are checked separately in the final AAB.
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
  "lib/arm64-v8a/libbash.so" \
  "lib/arm64-v8a/libproot.so" \
  "lib/arm64-v8a/libloader.so" \
  "lib/arm64-v8a/libloader32.so" \
  "lib/arm64-v8a/libpulseaudio.so" \
  "lib/arm64-v8a/libpactl.so"; do
  # grep -c (not -q): -q closes the pipe early → SIGPIPE on unzip under pipefail
  if [ "$(unzip -l "$APK" | grep -c " $entry$")" -gt 0 ]; then
    echo "  [OK] $entry"
  else
    echo "  [MISSING] $entry"
    fail=1
  fi
done

bootstrap_count="$(unzip -l "$APK" | grep -c " assets/bootstrap.tar$" || true)"
if [ "$bootstrap_count" -eq 0 ]; then
  echo "  [OK] zero assets/bootstrap.tar (delivered by PFD runtime_host)"
else
  echo "  [FAIL] base APK contains assets/bootstrap.tar — it belongs in runtime_host"
  fail=1
fi

# Negative rootfs gate: rootfs archives belong in distro_* dynamic features,
# never in the base APK.
rootfs_entries=$(unzip -l "$APK" | grep -c "assets/rootfs/" || true)
if [ "$rootfs_entries" -eq 0 ]; then
  echo "  [OK] zero assets/rootfs/* entries (delivered by PFD distro features)"
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
