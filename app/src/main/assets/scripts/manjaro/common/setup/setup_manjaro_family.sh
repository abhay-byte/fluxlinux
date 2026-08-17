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

# setup_manjaro_family.sh — Manjaro ARM (pacman, glibc).
# Never touch arm-stable mirrors, never write ALARM mirrorlists, never reuse
# setup_arch_family.sh. Empty pacman keyring → pacman-key --init is mandatory.

DISTRO_NAME="${1:-manjaro}"
echo "FluxLinux: Configuring ${DISTRO_NAME} (Manjaro Family)..."

if [ "$(id -u)" -ne 0 ]; then
    echo "FluxLinux: ERROR: must run as root inside the guest (got uid=$(id -u))."
    exit 1
fi

export SYSTEMD_OFFLINE=1
# POSIX C until _flux_ensure_en_us_locale materializes UTF-8 (bootstrap has none).
unset LC_ALL
export LANG=C

_flux_ensure_tmp
_flux_ensure_dns

if ! command -v pacman >/dev/null 2>&1; then
    echo "FluxLinux: ERROR: pacman not found"
    exit 1
fi

# POSIX rewrites — Manjaro bootstrap has no GNU sed (separate package).
_flux_comment_matching() {
    _cf="$1"
    _px="$2"
    [ -f "$_cf" ] || return 0
    _tmp="${_cf}.fluxnew.$$"
    while IFS= read -r _ln || [ -n "$_ln" ]; do
        case "$_ln" in
            "${_px}"*) printf '#%s\n' "$_ln" ;;
            *) printf '%s\n' "$_ln" ;;
        esac
    done < "$_cf" > "$_tmp" && mv -f "$_tmp" "$_cf"
}

_flux_replace_matching() {
    _cf="$1"
    _px="$2"
    _nw="$3"
    [ -f "$_cf" ] || return 0
    _tmp="${_cf}.fluxnew.$$"
    _hit=0
    while IFS= read -r _ln || [ -n "$_ln" ]; do
        case "$_ln" in
            "${_px}"*)
                printf '%s\n' "$_nw"
                _hit=1
                ;;
            *) printf '%s\n' "$_ln" ;;
        esac
    done < "$_cf" > "$_tmp"
    [ "$_hit" = 1 ] || printf '%s\n' "$_nw" >> "$_tmp"
    mv -f "$_tmp" "$_cf"
}

_flux_delete_matching() {
    _cf="$1"
    _px="$2"
    [ -f "$_cf" ] || return 0
    _tmp="${_cf}.fluxnew.$$"
    while IFS= read -r _ln || [ -n "$_ln" ]; do
        case "$_ln" in
            "${_px}"*) ;;
            *) printf '%s\n' "$_ln" ;;
        esac
    done < "$_cf" > "$_tmp" && mv -f "$_tmp" "$_cf"
}

_flux_comment_community_repo() {
    _cf=/etc/pacman.conf
    [ -f "$_cf" ] || return 0
    _tmp="${_cf}.fluxnew.$$"
    _in=0
    while IFS= read -r _ln || [ -n "$_ln" ]; do
        case "$_ln" in
            '[community]')
                printf '#[community]\n'
                _in=1
                ;;
            '['*)
                _in=0
                printf '%s\n' "$_ln"
                ;;
            Include*)
                if [ "$_in" = 1 ]; then
                    printf '#%s\n' "$_ln"
                else
                    printf '%s\n' "$_ln"
                fi
                ;;
            *) printf '%s\n' "$_ln" ;;
        esac
    done < "$_cf" > "$_tmp" && mv -f "$_tmp" "$_cf"
}

# CheckSpace + Android bind mounts: pacman reports
# "could not determine cachedir mount point /var/cache/pacman/pkg"
# then "not enough free disk space" even when /data has tens of GiB.
# Disable BEFORE the first -S — bootstrap has no sed for a retry rewrite.
_flux_disable_checkspace() {
    if grep -q '^CheckSpace' /etc/pacman.conf 2>/dev/null; then
        _flux_comment_matching /etc/pacman.conf CheckSpace
        _flux_log "CheckSpace disabled (Android bind mounts lie about free space)"
    fi
    mkdir -p /var/cache/pacman/pkg /var/lib/pacman
}

_flux_disable_checkspace

# ── pacman-key is the #1 landmine: /etc/pacman.d/gnupg is empty. ──
# proot binds /dev/urandom so entropy exists, but --init is still slow.
_flux_log "pacman-key --init (needs entropy — this can take a while)..."
mkdir -p /etc/pacman.d/gnupg
pacman-key --init || {
    _flux_log "pacman-key --init failed once — retrying"
    if command -v rngd >/dev/null 2>&1; then
        rngd -r /dev/urandom 2>/dev/null &
        sleep 2
    fi
    pacman-key --init || true
}
_keyring_ok=0
if pacman-key --populate archlinuxarm manjaro-arm manjaro 2>/dev/null \
    || pacman-key --populate archlinuxarm manjaro-arm 2>/dev/null; then
    _keyring_ok=1
fi

if [ "$_keyring_ok" = 0 ]; then
    # Temporary SigLevel=Never, refresh keyring packages, restore Required
    # DatabaseOptional, retry populate. NEVER leave SigLevel=Never permanent.
    _flux_log "keyring populate failed — temporary SigLevel=Never keyring refresh"
    _had_sig=""
    if grep -q '^SigLevel' /etc/pacman.conf 2>/dev/null; then
        _had_sig="$(grep '^SigLevel' /etc/pacman.conf | head -1)"
    fi
    _flux_replace_matching /etc/pacman.conf SigLevel 'SigLevel = Never'
    pacman -Sy --noconfirm archlinuxarm-keyring manjaro-arm-keyring manjaro-keyring 2>/dev/null || true
    if [ -n "$_had_sig" ]; then
        _flux_replace_matching /etc/pacman.conf 'SigLevel = Never' "$_had_sig"
    else
        _flux_delete_matching /etc/pacman.conf 'SigLevel = Never'
        echo 'SigLevel = Required DatabaseOptional' >> /etc/pacman.conf
    fi
    pacman-key --populate archlinuxarm manjaro-arm manjaro 2>/dev/null || true
fi

# Create standard groups (rootfs group file is only root).
_flux_ensure_groups

_flux_log "pacman -Sy..."
if ! pacman -Sy --noconfirm; then
    _flux_log "pacman -Sy failed — retry with community disabled (do not rewrite to ALARM)"
    cp -f /etc/pacman.conf /etc/pacman.conf.fluxbak 2>/dev/null || true
    if grep -q '^\[community\]' /etc/pacman.conf; then
        _flux_comment_community_repo
    fi
    pacman -Sy --noconfirm || {
        echo "FluxLinux: pacman -Sy failed (repos/network)"
        exit 1
    }
fi

_flux_log "Installing base + XFCE packages (targeted — no xfce4 group first)..."
if ! pacman -S --noconfirm --needed \
    shadow sudo git zsh python sed gzip \
    dbus-broker-units \
    xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
    xfdesktop xfwm4 thunar \
    ttf-dejavu adwaita-icon-theme \
    mesa mesa-utils \
    fastfetch \
    dbus; then
    echo "FluxLinux: XFCE install failed"
    exit 1
fi

# Fallback: full xfce4 group only if startxfce4 is still missing.
if ! command -v startxfce4 >/dev/null 2>&1 && [ ! -e /usr/bin/startxfce4 ]; then
    _flux_log "startxfce4 missing — falling back to xfce4 group"
    pacman -S --noconfirm --needed xfce4 || true
fi

_flux_ensure_dbus

# zshrc + Proot/ChrootCommandBuilder export LANG=en_US.UTF-8.
# Bootstrap ships locale.gen fully commented and no locale-archive, so
# setlocale fails and agnosterzak dies: "prompt_segment:5: character not in range"
_flux_ensure_locale() {
    _flux_ensure_en_us_locale || return 1
}

_flux_ensure_hostname() {
    printf 'manjaro\n' > /etc/hostname
    if [ -f /etc/hosts ]; then
        if ! grep -q '[[:space:]]manjaro' /etc/hosts 2>/dev/null; then
            printf '127.0.0.1\tmanjaro\n' >> /etc/hosts
        fi
    else
        printf '127.0.0.1\tlocalhost\n127.0.0.1\tmanjaro\n' > /etc/hosts
    fi
    hostname manjaro 2>/dev/null || true
}

_flux_require_startxfce4
_flux_ensure_user
_flux_ensure_sudo
_flux_ensure_home
_flux_setup_pulse
_flux_ensure_locale || exit 1
_flux_ensure_hostname
_flux_write_gpu_mode virgl
_flux_fix_pm_writable

echo "FluxLinux: ${DISTRO_NAME} Manjaro setup complete!"
exit 0
