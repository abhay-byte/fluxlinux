#!/bin/sh
# Play flavor legacy Termux customization is local-only. The ivarna asset
# retains the existing optional remote customization workflow.

set -eu
mkdir -p "$HOME/.fluxlinux"
printf '%s\n' play-customization-v1 > "$HOME/.fluxlinux/termux-customization-disabled"
echo "FluxLinux: Play Termux customization is local-only; no remote assets run"
