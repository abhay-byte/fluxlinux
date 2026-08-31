#!/bin/sh
# Play flavor legacy Termux theme setup is local-only.

set -eu
mkdir -p "$HOME/.fluxlinux"
printf '%s\n' play-customization-v1 > "$HOME/.fluxlinux/theme-disabled"
echo "FluxLinux: Play Termux theme setup is local-only; no remote download run"
