#!/bin/sh
# Play flavor guest customization is local-only. The ivarna asset retains the
# existing package/remote customization workflow.

set -eu
[ "$(id -u)" -eq 0 ] || exit 1
[ -d /home/flux ] || exit 1
mkdir -p /home/flux/.config/xfce4 /home/flux/.cache /home/flux/.local/share /etc/fluxlinux
chown -R flux:flux /home/flux 2>/dev/null || true
printf '%s\n' play-customization-v1 > /etc/fluxlinux/play-customization-v1
echo "FluxLinux: Play customization complete (local assets only)"
