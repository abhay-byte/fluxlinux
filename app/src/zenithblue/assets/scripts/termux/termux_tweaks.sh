#!/bin/sh
# Play flavor legacy Termux tweaks are intentionally local-only. The ivarna
# asset retains the existing optional remote Termux customization workflow.

set -eu
mkdir -p "$HOME/.fluxlinux"
printf '%s\n' play-customization-v1 > "$HOME/.fluxlinux/termux-tweaks-disabled"
echo "FluxLinux: Play Termux tweaks are local-only; no remote customization run"
