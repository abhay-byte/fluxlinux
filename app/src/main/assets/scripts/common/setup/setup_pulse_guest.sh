#!/bin/sh
# setup_pulse_guest.sh — one-shot guest Pulse *client* repair (existing installs).
# Host Pulse stays in the app PREFIX. Do not start a guest daemon / PipeWire-Pulse.
# Prefers flux_guest_common.sh when staged next to this script.

export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
unset LD_LIBRARY_PATH
unset LD_PRELOAD

_here=$(CDPATH= cd -- "$(dirname -- "$0")" 2>/dev/null && pwd)
for _common in \
    "${_here}/flux_guest_common.sh" \
    /tmp/flux_guest_common.sh \
    /usr/local/lib/fluxlinux/flux_guest_common.sh
do
    if [ -f "$_common" ]; then
        # shellcheck source=/dev/null
        . "$_common"
        break
    fi
done

if ! command -v _flux_setup_pulse >/dev/null 2>&1; then
    echo "FluxLinux: [AUDIO] ERROR flux_guest_common.sh not staged — cannot repair guest"
    exit 0
fi

_flux_setup_pulse
_have=$(_flux_guest_pactl || true)
if [ -n "$_have" ]; then
    echo "FluxLinux: [AUDIO] guest pactl=$_have"
    exit 0
fi
echo "FluxLinux: [AUDIO] WARN guest pactl still missing"
exit 0
