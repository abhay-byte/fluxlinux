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

# setup_parrot_family.sh — Parrot Security 7.2 (echo).
# Keep https://deb.parrot.sh/parrot. Never add deb.debian.org.
# os-release ID=debian is a lie — detection is card id parrot.
# Never install parrot-tools / parrot-interface / parrot-desktop-*.

DISTRO_NAME="${1:-parrot}"
echo "FluxLinux: Configuring ${DISTRO_NAME} (Parrot Family)..."

if [ "$(id -u)" -ne 0 ]; then
    echo "FluxLinux: ERROR: must run as root inside the guest (got uid=$(id -u))."
    exit 1
fi

unset LC_ALL
export LANG=C

_flux_ensure_tmp
_flux_ensure_dns
_flux_disable_guest_selinux
_flux_apt_prep

if ! command -v apt-get >/dev/null 2>&1; then
    echo "FluxLinux: ERROR: apt-get not found"
    exit 1
fi

_flux_log "apt-get update (deb.parrot.sh only)..."
if ! apt-get update; then
    _flux_log "apt-get update failed — installing ca-certificates and retrying"
    apt-get install -y --no-install-recommends ca-certificates || true
    apt-get update || {
        echo "FluxLinux: apt-get update failed (repos/network)"
        exit 1
    }
fi

_flux_log "Installing base packages..."
apt-get install -y --no-install-recommends \
    bash sudo passwd adduser ca-certificates curl wget unzip tar xz-utils \
    git zsh python3 locales \
    dbus dbus-x11 || {
        echo "FluxLinux: base package install failed"
        exit 1
    }

_flux_ensure_dbus

_flux_log "Installing XFCE4 (targeted — never parrot-interface / parrot-desktop)..."
if ! apt-get install -y --no-install-recommends \
    xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
    xfdesktop4 xfwm4 thunar \
    adwaita-icon-theme fonts-dejavu-core \
    libgl1-mesa-dri libegl1 mesa-utils; then
    _flux_log "targeted XFCE install failed — falling back to xfce4 metapackage"
    apt-get install -y --no-install-recommends xfce4 || {
        echo "FluxLinux: XFCE install failed"
        exit 1
    }
fi

_flux_ensure_en_us_locale || exit 1
_flux_require_startxfce4
_flux_ensure_groups
_flux_ensure_user
_flux_ensure_sudo
_flux_ensure_home
_flux_setup_pulse
_flux_write_gpu_mode virgl
_flux_fix_pm_writable

echo "FluxLinux: ${DISTRO_NAME} Parrot setup complete!"
exit 0
