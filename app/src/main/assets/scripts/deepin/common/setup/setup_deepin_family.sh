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

# setup_deepin_family.sh — Deepin 25 (crimson/beige) XFCE (proot + chroot).
# Debian-LIKE, not Debian: keep the beige/crimson sources, NEVER add debian.org.
# Never install dde-*/deepin-desktop-environment*/Treeland.

DISTRO_NAME="${1:-deepin}"
echo "FluxLinux: Configuring ${DISTRO_NAME} (Deepin Family)..."

if [ "$(id -u)" -ne 0 ]; then
    echo "FluxLinux: ERROR: must run as root inside the guest (got uid=$(id -u))."
    exit 1
fi

export DEBIAN_FRONTEND=noninteractive
export DEBCONF_NONINTERACTIVE_SEEN=true
export SYSTEMD_OFFLINE=1
unset LC_ALL
export LANG=C

_flux_ensure_tmp
_flux_ensure_dns
_flux_disable_guest_selinux

if ! command -v apt-get >/dev/null 2>&1; then
    echo "FluxLinux: ERROR: apt-get not found"
    exit 1
fi

# proot/chroot apt sandbox (copy Debian lesson) — _apt user probe breaks under proot.
mkdir -p /etc/apt/apt.conf.d
printf 'APT::Sandbox::User "root";\nDPkg::Use-Pty "false";\n' \
    > /etc/apt/apt.conf.d/99flux-nosandbox

_flux_log "apt-get update..."
if ! apt-get update; then
    _flux_log "apt-get update failed once — retrying (beige/crimson repos only)"
    apt-get update || {
        echo "FluxLinux: apt-get update failed (repos/network)"
        exit 1
    }
fi

_flux_log "Installing base packages..."
apt-get install -y --no-install-recommends \
    bash sudo passwd adduser ca-certificates curl wget unzip tar xz-utils \
    git zsh python3 \
    dbus dbus-x11 || {
        echo "FluxLinux: base package install failed"
        exit 1
    }

_flux_ensure_dbus

_flux_log "Installing XFCE4 (targeted list — no xfce4 metapackage first)..."
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

echo "FluxLinux: ${DISTRO_NAME} Deepin setup complete!"
exit 0
