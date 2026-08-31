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

# Generic writable assets must not carry Android-host ELF or nested APK bytes.
# Native host launchers are allowed only below lib/arm64-v8a/.
asset_entries="$(unzip -Z1 "$APK" | awk '/(^|\/)assets\// {print}')"
while IFS= read -r entry; do
  [ -n "$entry" ] || continue
  case "$entry" in
    */assets/payloads/*) continue ;;
  esac
  # Read the complete generic entry before converting its prefix. Stopping
  # od after four bytes closes unzip early and returns SIGPIPE under pipefail.
  magic="$(unzip -p "$APK" "$entry" 2>/dev/null | od -An -tx1 | tr -d ' \n')"
  if [[ "$magic" == 7f454c46* ]]; then
    case "$entry" in
      assets/scripts/common/setup/bwrap-proot-shim|assets/scripts/opensuse/common/libevp_md2.so)
        echo "  [OK] guest-only ELF asset: $entry" ;;
      *)
        echo "  [FAIL] ELF in generic asset: $entry"
        fail=1 ;;
    esac
  elif [[ "$magic" == 504b0304* ]]; then
    # A ZIP is an APK only when it carries Android package entries. Ordinary
    # guest/theme ZIPs remain data and are not rejected by this gate.
    nested_listing="$(unzip -p "$APK" "$entry" 2>/dev/null | unzip -l - 2>/dev/null || true)"
    if grep -Eq 'AndroidManifest.xml|classes\.dex' <<<"$nested_listing"; then
      echo "  [FAIL] nested APK bytes in generic asset: $entry"
      fail=1
    fi
  fi
done <<< "$asset_entries"

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
