#!/bin/sh
# Play flavor legacy Termux KDE customization is local-only. The ivarna asset
# retains the existing optional remote customization workflow.

set -eu
mkdir -p "$HOME/.fluxlinux"
printf '%s\n' play-customization-v1 > "$HOME/.fluxlinux/termux-kde-customization-disabled"
echo "FluxLinux: Play Termux KDE customization is local-only; no remote assets run"
