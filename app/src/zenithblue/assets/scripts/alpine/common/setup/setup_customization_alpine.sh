#!/bin/sh
# Play flavor Alpine customization is local-only; optional shell themes and
# plugins are intentionally omitted from the Play baseline.

set -eu
[ "$(id -u)" -eq 0 ] || exit 1
[ -f /etc/fluxlinux/play-baseline-v1 ] || {
    echo "FluxLinux: Alpine Play baseline missing; customization refused" >&2
    exit 1
}

mkdir -p /home/flux/.config/xfce4 /home/flux/.cache /home/flux/.local/share
chown -R flux:flux /home/flux 2>/dev/null || true
mkdir -p /etc/fluxlinux
printf '%s\n' play-customization-v1 > /etc/fluxlinux/play-customization-v1
echo "FluxLinux: Alpine Play customization complete (local assets only)"
