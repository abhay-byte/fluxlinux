#!/bin/sh

# Helpers live in flux_guest_common.sh (prepended by flux_install /
# familySetupPayload). Source it only when this file is run standalone.
if ! command -v _flux_setup_pulse >/dev/null 2>&1; then
    _here=$(CDPATH= cd -- "$(dirname -- "$0")" 2>/dev/null && pwd)
    for _common in \
        "${_here}/flux_guest_common.sh" \
        "${HOME:-}/flux_guest_common.sh" \
        /tmp/flux_guest_common.sh
    do
        if [ -f "$_common" ]; then
            # shellcheck source=/dev/null
            . "$_common"
            break
        fi
    done
fi

# setup_void_family.sh — Void Linux glibc aarch64 XFCE (proot + chroot). xbps.

DISTRO_NAME="${1:-void}"
echo "FluxLinux: Configuring ${DISTRO_NAME} (Void Family)..."

if [ "$(id -u)" -ne 0 ]; then
    echo "FluxLinux: ERROR: must run as root inside the guest (got uid=$(id -u))."
    exit 1
fi


_flux_ensure_tmp
_flux_ensure_dns

if ! command -v xbps-install >/dev/null 2>&1; then
    echo "FluxLinux: ERROR: xbps-install not found"
    exit 1
fi

# Never use musl repos on this glibc aarch64 rootfs
mkdir -p /usr/share/xbps.d /etc/xbps.d
if [ ! -s /usr/share/xbps.d/xbps-arch.conf ]; then
    echo "architecture=aarch64" > /usr/share/xbps.d/xbps-arch.conf
fi
if ! grep -q 'current/aarch64' /usr/share/xbps.d/00-repository-main.conf 2>/dev/null \
    && ! grep -q 'current/aarch64' /etc/xbps.d/*.conf 2>/dev/null; then
    echo "repository=https://repo-default.voidlinux.org/current/aarch64" \
        > /usr/share/xbps.d/00-repository-main.conf
fi

unset LC_ALL
export LANG=C
export SYSTEMD_OFFLINE=1

_flux_log "xbps-install sync..."
xbps-install -Sy || {
    echo "FluxLinux: xbps sync failed (network / repos)"
    exit 1
}
# Rolling Void often requires updating xbps itself before any other transaction.
# Do NOT -Syu the whole system (huge/slow under proot).
_flux_log "Updating xbps package manager if needed..."
xbps-install -yu xbps || true

_flux_log "Installing base packages..."
# Ignore already-installed names (xbps treats them as errors if mixed).
for _p in bash sudo shadow ca-certificates curl wget unzip dbus; do
    xbps-install -y "$_p" 2>/dev/null || true
done
xbps-install -y dbus-x11 2>/dev/null || true

_flux_ensure_dbus

# Minimal XFCE only — do NOT install the `xfce4` metapackage (pulls ffmpeg,
# gstreamer plugins, parole, … hundreds of extras under proot/chroot).
_flux_log "Installing XFCE4 (minimal session)..."
xbps-install -y \
    xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
    xfdesktop xfwm4 \
    || {
        echo "FluxLinux: XFCE package install failed"
        exit 1
    }
xbps-install -y Thunar 2>/dev/null || xbps-install -y thunar 2>/dev/null || true
xbps-install -y adwaita-icon-theme dejavu-fonts-ttf mesa mesa-dri 2>/dev/null || true

if ! ls /usr/lib/locale 2>/dev/null | grep -qi 'en_US'; then
    xbps-install -y glibc-locales 2>/dev/null || true
fi
printf 'LANG=en_US.UTF-8\n' > /etc/locale.conf
if command -v _flux_ensure_en_us_locale >/dev/null 2>&1; then
    _flux_ensure_en_us_locale || exit 1
fi

_flux_require_startxfce4
_flux_ensure_user
_flux_ensure_sudo
_flux_ensure_home
_flux_setup_pulse
_flux_write_gpu_mode virgl
_flux_fix_pm_writable

echo "FluxLinux: ${DISTRO_NAME} Void setup complete!"
exit 0
