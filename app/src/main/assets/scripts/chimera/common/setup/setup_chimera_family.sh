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

# setup_chimera_family.sh — Chimera Linux (musl, apk v3, BSD userland).
# New family, NOT "Alpine with a different tarball":
#   - apk-tools v3: interactive by default, /usr/lib/apk DB, no --no-cache
#   - XFCE lives in the `user` repo (chimera-repo-user), not main
#   - never `apk add xfce4` metapackage (pulls gvfs + udisks → udev/proot hangs)
#   - no bash/useradd/sudo in bootstrap — install shadow+bash BEFORE user creation

DISTRO_NAME="${1:-chimera}"
echo "FluxLinux: Configuring ${DISTRO_NAME} (Chimera Family)..."

if [ "$(id -u)" -ne 0 ]; then
    echo "FluxLinux: ERROR: must run as root inside the guest (got uid=$(id -u))."
    exit 1
fi

_flux_ensure_tmp
_flux_ensure_dns

if ! command -v apk >/dev/null 2>&1; then
    echo "FluxLinux: ERROR: apk not found"
    exit 1
fi

# apk v3 is interactive by default (/usr/lib/apk/config).
# User /etc/apk/config overrides it; list only what we want — omit interactive.
mkdir -p /etc/apk
printf 'cache-packages\n' > /etc/apk/config

_flux_log "apk update..."
apk update || {
    echo "FluxLinux: apk update failed (repos/network)"
    exit 1
}

_flux_log "Installing base packages (bash/sudo/shadow BEFORE user)..."
# Chimera pkg names: no sudo (opendoas), tar=gtar, wget=wget2, font-dejavu=fonts-dejavu.
if ! apk add bash shadow opendoas ca-certificates curl wget2 unzip gtar xz \
        git zsh python \
        dbus fonts-dejavu adwaita-icon-theme; then
    _flux_log "base pkg names differ — retrying via cmd: virtual providers"
    apk add cmd:bash cmd:useradd cmd:doas ca-certificates cmd:curl \
        cmd:wget2 cmd:unzip cmd:gtar cmd:xz cmd:git cmd:zsh cmd:python \
        cmd:dbus-run-session \
        fonts-dejavu || {
            echo "FluxLinux: base package install failed"
            exit 1
        }
fi

# GNU tool aliases: Flux scripts call `wget` / `tar`. wget2 provides
# /usr/bin/wget2, gtar provides /usr/bin/gtar — link the conventional names.
if [ ! -e /usr/local/bin/wget ] && command -v wget2 >/dev/null 2>&1; then
    mkdir -p /usr/local/bin
    ln -sf "$(command -v wget2)" /usr/local/bin/wget
fi
if [ ! -e /usr/local/bin/tar ] && [ ! -e /usr/bin/tar ] && command -v gtar >/dev/null 2>&1; then
    mkdir -p /usr/local/bin
    ln -sf "$(command -v gtar)" /usr/local/bin/tar
fi

# user repo — XFCE lives here, not in main
_flux_log "Enabling user repo (chimera-repo-user)..."
apk add chimera-repo-user 2>/dev/null || true
apk update || true

_flux_log "Installing XFCE4 (targeted — never the xfce4 metapackage)..."
if ! apk add xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
        xfdesktop xfwm4 thunar \
        mesa mesa-dri; then
    _flux_log "XFCE pkg names differ — retrying via cmd: virtual providers"
    apk add cmd:startxfce4 cmd:xfce4-panel cmd:xfce4-settings \
        cmd:xfce4-terminal cmd:xfdesktop cmd:xfwm4 cmd:thunar \
        mesa mesa-dri || {
            echo "FluxLinux: XFCE install failed"
            exit 1
        }
fi

# startxfce4 may land outside /usr/bin (e.g. /usr/libexec) — pin a known path
# so host probes (Kotlin + start_guest_gui.sh) find it.
if [ ! -e /usr/bin/startxfce4 ] && command -v startxfce4 >/dev/null 2>&1; then
    _sx="$(command -v startxfce4)"
    ln -sf "$_sx" /usr/bin/startxfce4 2>/dev/null || true
    _flux_log "linked startxfce4: $_sx → /usr/bin/startxfce4"
fi

# dbus machine-id: dbus-uuidgen if present; else write a hex id.
_flux_ensure_dbus
if [ ! -s /etc/machine-id ] && command -v dbus-uuidgen >/dev/null 2>&1; then
    dbus-uuidgen --ensure=/etc/machine-id 2>/dev/null || true
fi
if [ ! -s /etc/machine-id ]; then
    _mid=$(tr -d '-' < /proc/sys/kernel/random/uuid 2>/dev/null | tr -d '\n')
    [ "${#_mid}" = "32" ] || _mid="0123456789abcdef0123456789abcdef"
    printf '%s\n' "$_mid" > /etc/machine-id
    _flux_log "wrote hex /etc/machine-id"
fi
mkdir -p /var/lib/dbus
if [ ! -e /var/lib/dbus/machine-id ]; then
    ln -sf /etc/machine-id /var/lib/dbus/machine-id 2>/dev/null \
        || cp -f /etc/machine-id /var/lib/dbus/machine-id 2>/dev/null || true
fi

_flux_require_startxfce4
_flux_ensure_groups
_flux_ensure_user
_flux_ensure_sudo
_flux_ensure_home
_flux_setup_pulse
_flux_ensure_en_us_locale || exit 1
_flux_write_gpu_mode virgl
_flux_fix_pm_writable

echo "FluxLinux: ${DISTRO_NAME} Chimera setup complete!"
exit 0
