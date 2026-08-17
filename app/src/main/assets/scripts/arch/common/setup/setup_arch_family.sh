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

# setup_arch_family.sh — Arch Linux ARM (pacman, glibc).
# Never rewrite mirrors to Manjaro arm-stable or x86_64 Arch.
# Keep ALARM: Server = http://mirror.archlinuxarm.org/$arch/$repo
# HoldPkg stays pacman glibc. User alarm occupies uid 1000 — rename to flux.

DISTRO_NAME="${1:-archlinux}"
echo "FluxLinux: Configuring ${DISTRO_NAME} (Arch Family)..."

if [ "$(id -u)" -ne 0 ]; then
    echo "FluxLinux: ERROR: must run as root inside the guest (got uid=$(id -u))."
    exit 1
fi

export SYSTEMD_OFFLINE=1
unset LC_ALL
export LANG=C

_flux_ensure_tmp
_flux_ensure_dns

if ! command -v pacman >/dev/null 2>&1; then
    echo "FluxLinux: ERROR: pacman not found"
    exit 1
fi

# POSIX rewrites — slim ALARM may not have GNU sed.
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

# CheckSpace + Android bind mounts lie about free space (Manjaro lesson).
_flux_disable_checkspace() {
    if grep -q '^CheckSpace' /etc/pacman.conf 2>/dev/null; then
        _flux_comment_matching /etc/pacman.conf CheckSpace
        _flux_log "CheckSpace disabled (Android bind mounts lie about free space)"
    fi
    mkdir -p /var/cache/pacman/pkg /var/lib/pacman
}

_flux_disable_checkspace

# Pacman 7 on Android/proot: Landlock is missing and DownloadUser=alpm
# cannot switch sandbox user → "failed to synchronize all databases".
# Disable sandbox for this guest (same as legacy setup_arch_chroot.sh).
if ! grep -q '^DisableSandbox' /etc/pacman.conf 2>/dev/null; then
    echo 'DisableSandbox' >> /etc/pacman.conf
    _flux_log "DisableSandbox enabled (Android kernel has no Landlock / alpm)"
fi
_flux_comment_matching /etc/pacman.conf DownloadUser

# Keep ALARM mirrorlist. Do not write Manjaro arm-stable.
if [ ! -s /etc/pacman.d/mirrorlist ] || ! grep -q 'archlinuxarm' /etc/pacman.d/mirrorlist 2>/dev/null; then
    printf 'Server = http://mirror.archlinuxarm.org/$arch/$repo\n' > /etc/pacman.d/mirrorlist
fi

# pacman-key is the #1 landmine: /etc/pacman.d/gnupg is empty after slim.
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
if pacman-key --populate archlinuxarm 2>/dev/null; then
    _keyring_ok=1
fi
pacman-key --populate archlinux 2>/dev/null || true

if [ "$_keyring_ok" = 0 ]; then
    # Temporary SigLevel=Never, refresh keyring packages, restore Required
    # DatabaseOptional. NEVER leave SigLevel=Never permanent.
    _flux_log "keyring populate failed — temporary SigLevel=Never keyring refresh"
    _had_sig=""
    if grep -q '^SigLevel' /etc/pacman.conf 2>/dev/null; then
        _had_sig="$(grep '^SigLevel' /etc/pacman.conf | head -1)"
    fi
    _flux_replace_matching /etc/pacman.conf SigLevel 'SigLevel = Never'
    pacman -Sy --noconfirm archlinuxarm-keyring 2>/dev/null || true
    pacman -S --noconfirm --needed archlinux-keyring 2>/dev/null || true
    if [ -n "$_had_sig" ]; then
        _flux_replace_matching /etc/pacman.conf 'SigLevel = Never' "$_had_sig"
    else
        _flux_delete_matching /etc/pacman.conf 'SigLevel = Never'
        echo 'SigLevel = Required DatabaseOptional' >> /etc/pacman.conf
    fi
    pacman-key --populate archlinuxarm 2>/dev/null || true
    pacman-key --populate archlinux 2>/dev/null || true
fi

_flux_ensure_groups

_flux_log "pacman -Sy..."
if ! pacman -Sy --noconfirm; then
    echo "FluxLinux: pacman -Sy failed (repos/network)"
    exit 1
fi

_flux_log "Installing base + XFCE packages (targeted — no xfce4 group first)..."
if ! pacman -S --noconfirm --needed \
    sudo \
    git zsh python sed gzip \
    xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
    xfdesktop xfwm4 thunar \
    ttf-dejavu adwaita-icon-theme \
    mesa mesa-utils \
    dbus xz; then
    echo "FluxLinux: XFCE install failed"
    exit 1
fi

if ! command -v startxfce4 >/dev/null 2>&1 && [ ! -e /usr/bin/startxfce4 ]; then
    _flux_log "startxfce4 missing — falling back to xfce4 group"
    pacman -S --noconfirm --needed xfce4 || true
fi

_flux_ensure_dbus

# User alarm occupies uid 1000. Prefer rename so home/files stay.
if id alarm >/dev/null 2>&1 && ! id flux >/dev/null 2>&1; then
    _flux_log "Renaming alarm → flux (uid 1000)"
    usermod -l flux -d /home/flux -m alarm 2>/dev/null \
        || { userdel -r alarm 2>/dev/null || true; }
fi
# Chroot can leave both (usermod -l no-op + useradd flux=1001). One uid 1000.
if id alarm >/dev/null 2>&1 && id flux >/dev/null 2>&1; then
    _flux_log "Removing leftover alarm (flux already exists)"
    userdel -r alarm 2>/dev/null || userdel alarm 2>/dev/null || true
fi
if id flux >/dev/null 2>&1 && ! id -u 1000 >/dev/null 2>&1; then
    usermod -u 1000 -d /home/flux flux 2>/dev/null || true
fi

_flux_require_startxfce4
_flux_ensure_user
echo "flux:flux" | chpasswd 2>/dev/null || true
if command -v usermod >/dev/null 2>&1; then
    usermod -aG wheel,audio,video,input,users flux 2>/dev/null || true
fi
_flux_ensure_sudo
_flux_ensure_home
_flux_setup_pulse
_flux_ensure_en_us_locale || exit 1
_flux_write_gpu_mode virgl
_flux_fix_pm_writable

echo "FluxLinux: ${DISTRO_NAME} Arch setup complete!"
exit 0
