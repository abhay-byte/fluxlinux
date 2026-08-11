#!/usr/bin/env bash
# Set TERMUX_APP__PACKAGE_NAME in termux-packages/scripts/properties.sh
set -euo pipefail

CUSTOM_PACKAGE="${1:?usage: $0 <applicationId>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROPS="${TERMUX_PACKAGES_DIR:-$ROOT/native/termux-packages}/scripts/properties.sh"

if [[ ! -f "$PROPS" ]]; then
  echo "error: properties.sh not found at $PROPS" >&2
  exit 1
fi

if [[ ! "$CUSTOM_PACKAGE" =~ ^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$ ]]; then
  echo "error: invalid applicationId: $CUSTOM_PACKAGE" >&2
  exit 1
fi

# Length check (TERMUX__ROOTFS max 86 including /data/data/<pkg>/files)
root_path="/data/data/${CUSTOM_PACKAGE}/files"
if (( ${#root_path} >= 86 )); then
  echo "error: TERMUX__ROOTFS path too long (${#root_path} >= 86): $root_path" >&2
  exit 1
fi

if grep -qE '^TERMUX_APP__PACKAGE_NAME=' "$PROPS"; then
  sed -i -E "s|^TERMUX_APP__PACKAGE_NAME=.*|TERMUX_APP__PACKAGE_NAME=\"${CUSTOM_PACKAGE}\"|" "$PROPS"
else
  echo "error: TERMUX_APP__PACKAGE_NAME assignment not found in $PROPS" >&2
  exit 1
fi

current="$(grep -E '^TERMUX_APP__PACKAGE_NAME=' "$PROPS" | head -1)"
echo "[*] $current"
echo "[*] PREFIX will be: /data/data/${CUSTOM_PACKAGE}/files/usr"
