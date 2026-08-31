#!/bin/sh
# Play flavor Alpine baseline finalization.
# Package installation is a maintainer/CI build step; the Play runtime only
# verifies the pre-provisioned image and performs local user/session setup.

set -eu

if [ "$(id -u)" -ne 0 ]; then
    echo "FluxLinux: ERROR: Alpine Play setup must run as guest root" >&2
    exit 1
fi

MARKER=/etc/fluxlinux/play-baseline-v1
if [ ! -f "$MARKER" ]; then
    echo "FluxLinux: ERROR: Alpine Play baseline marker missing" >&2
    echo "FluxLinux: refusing runtime package-network customization" >&2
    exit 1
fi

for required in /bin/sh /sbin/apk /usr/bin/startxfce4 /usr/bin/dbus-daemon; do
    if [ ! -e "$required" ]; then
        echo "FluxLinux: ERROR: Alpine Play baseline missing $required" >&2
        exit 1
    fi
done

export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
mkdir -p /tmp /var/tmp /var/lib/dbus /etc/profile.d /etc/pulse/client.conf.d
chmod 1777 /tmp /var/tmp 2>/dev/null || true
unset PROOT_TMP_DIR
export TMPDIR=/tmp

if command -v dbus-uuidgen >/dev/null 2>&1; then
    dbus-uuidgen --ensure=/etc/machine-id 2>/dev/null || true
fi
if [ ! -e /var/lib/dbus/machine-id ] && [ -f /etc/machine-id ]; then
    ln -sf /etc/machine-id /var/lib/dbus/machine-id 2>/dev/null ||
        cp -f /etc/machine-id /var/lib/dbus/machine-id 2>/dev/null || true
fi

if ! id flux >/dev/null 2>&1; then
    echo "FluxLinux: ERROR: Alpine Play baseline has no flux user" >&2
    exit 1
fi
mkdir -p /home/flux/.config /home/flux/.cache /home/flux/.local/share
chown -R flux:flux /home/flux 2>/dev/null || true
chmod 755 /home/flux 2>/dev/null || true

printf 'export PULSE_SERVER=tcp:127.0.0.1\n' > /etc/profile.d/flux-pulse.sh
printf 'default-server = tcp:127.0.0.1\nautospawn = no\n' > /etc/pulse/client.conf.d/99-fluxlinux.conf
chmod 0644 /etc/profile.d/flux-pulse.sh /etc/pulse/client.conf.d/99-fluxlinux.conf

mkdir -p /etc/fluxlinux
printf '%s\n' play-customization-v1 > /etc/fluxlinux/play-customization-v1
echo "FluxLinux: Alpine Play baseline verified; local setup complete"
