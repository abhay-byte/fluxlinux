#!/usr/bin/env bash
# Verify a .deb was built for the given applicationId prefix.
# Usage: verify_deb_prefix.sh <file.deb> <applicationId>
set -euo pipefail

DEB="${1:?usage: $0 <file.deb> <applicationId>}"
PKG="${2:?usage: $0 <file.deb> <applicationId>}"
EXPECT="data/data/${PKG}/files"
STOCK="data/data/com.termux/files"

if [[ ! -f "$DEB" ]]; then
  echo "error: deb not found: $DEB" >&2
  exit 1
fi
DEB="$(cd "$(dirname "$DEB")" && pwd)/$(basename "$DEB")"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

(
  cd "$tmp"
  ar x "$DEB"
  data="$(ls data.tar* 2>/dev/null | head -1)"
  if [[ -z "$data" ]]; then
    echo "error: no data.tar* in deb" >&2
    exit 1
  fi
  tar -tf "$data" > paths.txt
)

total="$(wc -l < "$tmp/paths.txt")"
good="$(grep -c "$EXPECT" "$tmp/paths.txt" || true)"
stock="$(grep -c "$STOCK" "$tmp/paths.txt" || true)"
sample="$(grep "$EXPECT" "$tmp/paths.txt" | head -5 || true)"

echo "  deb:    $(basename "$DEB")"
echo "  paths:  $total"
echo "  match:  $good  ($EXPECT)"
echo "  stock:  $stock  ($STOCK)"
if [[ -n "$sample" ]]; then
  echo "  sample:"
  echo "$sample" | sed 's/^/    /'
fi

if (( good < 1 )); then
  echo "FAIL: no paths under $EXPECT" >&2
  echo "  first paths:" >&2
  head -10 "$tmp/paths.txt" | sed 's/^/    /' >&2
  exit 1
fi
if (( stock > 0 )); then
  echo "FAIL: found stock com.termux paths in deb" >&2
  grep "$STOCK" "$tmp/paths.txt" | head -10 | sed 's/^/    /' >&2
  exit 1
fi

# Optional ELF RUNPATH check if readelf available and deb has bash-like bin
if command -v readelf >/dev/null 2>&1; then
  (
    cd "$tmp"
    tar -xf data.tar* 2>/dev/null || tar -xf "$(ls data.tar*)"
  )
  elf="$(find "$tmp/$EXPECT" -type f \( -name bash -o -name proot -o -name curl \) 2>/dev/null | head -1 || true)"
  if [[ -n "$elf" && -f "$elf" ]]; then
    echo "  elf:    $elf"
    rpath="$(readelf -d "$elf" 2>/dev/null | grep -E 'RPATH|RUNPATH' || true)"
    if [[ -n "$rpath" ]]; then
      echo "  rpath:  $rpath"
      if echo "$rpath" | grep -q "/data/data/${PKG}/files/usr"; then
        echo "  rpath:  OK (contains app prefix)"
      elif echo "$rpath" | grep -q "com.termux"; then
        echo "FAIL: RUNPATH still points at com.termux" >&2
        exit 1
      else
        echo "  rpath:  (present but not app-prefixed — inspect manually)"
      fi
    else
      echo "  rpath:  (none / not dynamic)"
    fi
  fi
fi

echo "PASS: prefix OK for $PKG"
