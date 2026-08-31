#!/usr/bin/env bash
# Verify Worker 04 Play host-runtime artifact boundaries.
#
# Usage:
#   ./scripts/verify_play_host_artifacts.sh app/build/outputs/bundle/zenithblueDebug/app-zenithblue-debug.aab
#   ./scripts/verify_play_host_artifacts.sh app/build/outputs/apk/zenithblue/debug/app-zenithblue-debug.apk
#
# Generic writable assets may contain guest data and interpreted scripts, but
# directly executed Android-host ELF belongs in lib/arm64-v8a/. The two
# explicitly allowlisted ELF files below are guest-only setup helpers copied
# into a guest rootfs; they are never Android argv0 launchers.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACT="${1:-}"
if [[ -z "$ARTIFACT" || ! -f "$ARTIFACT" ]]; then
  echo "Usage: $0 <APK-or-AAB>" >&2
  exit 2
fi

case "$ARTIFACT" in
  *.apk|*.aab) ;;
  *) echo "error: expected .apk or .aab: $ARTIFACT" >&2; exit 2 ;;
esac

fail=0
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

allow_guest_elf() {
  case "$1" in
    scripts/common/setup/bwrap-proot-shim|scripts/opensuse/common/libevp_md2.so|assets/scripts/common/setup/bwrap-proot-shim|assets/scripts/opensuse/common/libevp_md2.so|*/assets/scripts/common/setup/bwrap-proot-shim|*/assets/scripts/opensuse/common/libevp_md2.so)
      return 0 ;;
    *) return 1 ;;
  esac
}

check_file() {
  local path="$1" label="$2" magic nested
  magic="$(od -An -tx1 -N4 "$path" | tr -d ' \n')"
  if [[ "$magic" == "7f454c46" ]]; then
    if allow_guest_elf "$label"; then
      echo "  [OK] guest-only ELF asset: $label"
    else
      echo "  [FAIL] unexpected ELF in generic writable asset: $label"
      fail=1
    fi
  elif [[ "$magic" == "504b0304" ]]; then
    nested="$(unzip -l "$path" 2>/dev/null | grep -E 'AndroidManifest.xml|classes\.dex' || true)"
    if [[ -n "$nested" ]]; then
      echo "  [FAIL] nested APK bytes in generic asset: $label"
      fail=1
    fi
  fi
}

echo "=== verifying source generic assets ==="
while IFS= read -r -d '' path; do
  rel="${path#"$ROOT/"}"
  case "$rel" in
    app/src/*/assets/payloads/*) continue ;;
  esac
  check_file "$path" "${rel#app/src/*/assets/}"
done < <(find "$ROOT/app/src/main/assets" "$ROOT/app/src/zenithblue/assets" "$ROOT/app/src/ivarna/assets" -type f -print0 2>/dev/null)

entries="$(unzip -Z1 "$ARTIFACT")"
if [[ "$ARTIFACT" == *.apk ]]; then
  for required in \
    "lib/arm64-v8a/libbash.so" "lib/arm64-v8a/libproot.so" \
    "lib/arm64-v8a/libloader.so" "lib/arm64-v8a/libloader32.so" \
    "lib/arm64-v8a/libpulseaudio.so" "lib/arm64-v8a/libpactl.so"; do
    if grep -Fxq "$required" <<<"$entries"; then
      echo "  [OK] $required"
    else
      echo "  [FAIL] missing native launcher: $required"
      fail=1
    fi
  done
  if grep -Eq '(^|/)assets/(loader\.apk|loader\.bin)$' <<<"$entries"; then
    echo "  [FAIL] nested loader asset is present"
    fail=1
  else
    echo "  [OK] no loader.apk/loader.bin"
  fi
  if grep -Eq '(^|/)assets/bootstrap\.tar$|(^|/)assets/rootfs/' <<<"$entries"; then
    echo "  [FAIL] base APK contains a PFD-owned archive"
    fail=1
  else
    echo "  [OK] no base bootstrap/rootfs archive"
  fi
else
  for required in \
    "runtime_host/assets/payloads/runtime_host/bootstrap.tar" \
    "runtime_host/assets/payloads/runtime_host/provenance.json"; do
    if grep -Fxq "$required" <<<"$entries"; then
      echo "  [OK] $required"
    else
      echo "  [FAIL] missing runtime_host payload: $required"
      fail=1
    fi
  done
  if grep -Eq '(^|/)(base/)?assets/(loader\.apk|loader\.bin)$' <<<"$entries"; then
    echo "  [FAIL] nested loader asset is present"
    fail=1
  else
    echo "  [OK] no loader.apk/loader.bin"
  fi
  if grep -Eq '^base/assets/bootstrap\.tar$|^base/assets/rootfs/' <<<"$entries"; then
    echo "  [FAIL] base bundle module contains a PFD-owned archive"
    fail=1
  else
    echo "  [OK] no base bootstrap/rootfs archive"
  fi
fi

echo "=== verifying artifact generic assets ==="
while IFS= read -r entry; do
  [[ -n "$entry" ]] || continue
  case "$entry" in
    */assets/*) ;;
    *) continue ;;
  esac
  case "$entry" in
    */assets/payloads/*) continue ;;
  esac
  out="$tmp_dir/asset"
  unzip -p "$ARTIFACT" "$entry" > "$out"
  check_file "$out" "${entry#*/assets/}"
done <<<"$entries"

if (( fail )); then
  echo FAIL
  exit 1
fi
echo PASS
