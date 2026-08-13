#!/bin/sh
# Compatibility wrapper — Debian component id still points here.
# Implementation lives in setup_hw_accel_guest.sh + flux_gpu_common.sh
for f in \
    /tmp/setup_hw_accel_guest.sh \
    /usr/local/lib/fluxlinux/setup_hw_accel_guest.sh \
    "$(dirname "$0")/setup_hw_accel_guest.sh"
do
    if [ -r "$f" ]; then
        exec /bin/sh "$f" "$@"
    fi
done
echo "FluxLinux: setup_hw_accel_guest.sh not found — writing virgl"
mkdir -p /etc/fluxlinux
printf '%s\n' virgl > /etc/fluxlinux/gpu_mode
exit 0
