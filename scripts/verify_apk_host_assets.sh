#!/usr/bin/env bash
# verify_apk_host_assets.sh — unzip checks that the built APK contains the host
# jniLibs and — since rootfs (and ivarna bootstrap) moved to GitHub release
# download — contains ZERO assets/rootfs/* entries. Ivarna must not package
# assets/bootstrap.tar; zenithblue still must (STORED).
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

# Ivarna downloads bootstrap.tar from GitHub; zenithblue still packages it.
case "$APK" in
  *ivarna*) expect_bootstrap=0 ;;
  *) expect_bootstrap=1 ;;
esac

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
if [ "$expect_bootstrap" -eq 1 ]; then
  if [ "$bootstrap_count" -gt 0 ]; then
    echo "  [OK] assets/bootstrap.tar"
    method=$(unzip -v "$APK" | grep " assets/bootstrap.tar$" | awk '{print $2}')
    if [ "$method" = "Stored" ]; then
      echo "  [OK] assets/bootstrap.tar stored uncompressed"
    else
      echo "  [FAIL] assets/bootstrap.tar compressed ($method) — check androidResources.noCompress"
      fail=1
    fi
  else
    echo "  [MISSING] assets/bootstrap.tar"
    fail=1
  fi
else
  if [ "$bootstrap_count" -eq 0 ]; then
    echo "  [OK] zero assets/bootstrap.tar (ivarna downloads from GitHub tag rootfs)"
  else
    echo "  [FAIL] ivarna APK must not package assets/bootstrap.tar"
    fail=1
  fi
fi

# Negative rootfs gate: rootfs archives ship via the GitHub release tag `rootfs` (ivarna)
# or via Play dynamic feature modules (zenithblue), never inside base APK.
rootfs_entries=$(unzip -l "$APK" | grep -c "assets/rootfs/" || true)
payloads_entries=$(unzip -l "$APK" | grep -c "payloads/" || true)
if [ "$rootfs_entries" -eq 0 ] && [ "$payloads_entries" -eq 0 ]; then
  echo "  [OK] zero assets/rootfs/* and zero payloads/* entries in base APK"
else
  if [ "$rootfs_entries" -ne 0 ]; then
    echo "  [FAIL] APK contains $rootfs_entries assets/rootfs/* entries — must be zero"
    unzip -l "$APK" | grep "assets/rootfs/" || true
  fi
  if [ "$payloads_entries" -ne 0 ]; then
    echo "  [FAIL] APK contains $payloads_entries payloads/* entries — must be zero"
    unzip -l "$APK" | grep "payloads/" || true
  fi
  fail=1
fi

if [ "$fail" -eq 0 ]; then
  echo "PASS"
else
  echo "FAIL"
  exit 1
fi
