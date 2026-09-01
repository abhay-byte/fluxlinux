#!/bin/sh
# Shared Play family finalization. The selected rootfs must already be a
# maintainer-provisioned image; this helper contains no package manager or
# network provisioning path.

set -eu

[ "$(id -u)" -eq 0 ] || {
    echo "FluxLinux: Play baseline finalization requires guest root" >&2
    exit 1
}

MARKER=/etc/fluxlinux/play-baseline-v1
[ -f "$MARKER" ] || {
    echo "FluxLinux: ERROR: $MARKER is missing" >&2
    echo "FluxLinux: refusing runtime baseline provisioning" >&2
    exit 1
}

for required in \
    /bin/sh /usr/bin/startxfce4 /usr/bin/dbus-daemon \
    /usr/bin/xfce4-session /usr/bin/xfwm4 /usr/bin/xfce4-panel \
    /usr/bin/xfdesktop /usr/bin/thunar /home/flux; do
    [ -e "$required" ] || {
        echo "FluxLinux: ERROR: Play baseline is missing $required" >&2
        exit 1
    }
done

export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
mkdir -p /tmp /var/tmp /var/lib/dbus /etc/fluxlinux \
    /etc/profile.d /etc/pulse/client.conf.d \
    /home/flux/.config /home/flux/.cache /home/flux/.local/share
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

id flux >/dev/null 2>&1 || {
    echo "FluxLinux: ERROR: Play baseline has no flux user" >&2
    exit 1
}
chown -R flux:flux /home/flux 2>/dev/null || true
chmod 0755 /home/flux 2>/dev/null || true
printf 'export PULSE_SERVER=tcp:127.0.0.1\n' > /etc/profile.d/flux-pulse.sh
printf 'default-server = tcp:127.0.0.1\nautospawn = no\n' > /etc/pulse/client.conf.d/99-fluxlinux.conf
chmod 0644 /etc/profile.d/flux-pulse.sh /etc/pulse/client.conf.d/99-fluxlinux.conf
printf '%s\n' play-customization-v1 > /etc/fluxlinux/play-customization-v1

echo "FluxLinux: Play baseline verified; local family finalization complete"
