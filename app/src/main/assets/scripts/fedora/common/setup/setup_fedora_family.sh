#!/bin/sh
# --- inlined flux_guest_common.sh ---
#!/bin/sh
# flux_guest_common.sh — shared guest helpers (sourced by family / customization).
# Safe to concatenate in front of a family payload. Idempotent functions.

export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin${PATH:+:$PATH}"
# Host Termux libs (OpenSSL/LDAP) must not leak into glibc guests.
unset LD_LIBRARY_PATH
unset LD_PRELOAD

_flux_log() { echo "FluxLinux: $*"; }

# Portable stat: GNU coreutils uses `-c %u`, BSD userland (Chimera) uses `-f %u`.
_flux_stat_u() { stat -c %u "$1" 2>/dev/null || stat -f %u "$1" 2>/dev/null || true; }
_flux_stat_g() { stat -c %g "$1" 2>/dev/null || stat -f %g "$1" 2>/dev/null || true; }

# Detect Chimera apk v3 (musl, /usr/lib/apk DB, no --no-cache).
_flux_is_chimera() {
    if grep -q '^ID="chimera"' /usr/lib/os-release 2>/dev/null \
        || grep -q '^ID=chimera' /etc/os-release 2>/dev/null; then
        return 0
    fi
    [ -d /usr/lib/apk ] && [ ! -d /lib/apk ]
}

# Detect Manjaro (arch family, must not rewrite mirrors to ALARM).
_flux_is_manjaro() {
    grep -q 'ID="manjaro-arm"' /usr/lib/os-release 2>/dev/null \
        || grep -q 'manjaro' /etc/os-release 2>/dev/null
}

_flux_ensure_tmp() {
    mkdir -p /tmp /var/tmp
    chmod 1777 /tmp /var/tmp 2>/dev/null || true
    unset PROOT_TMP_DIR
    export TMPDIR=/tmp
}

_flux_ensure_dns() {
    if [ ! -s /etc/resolv.conf ] || ! grep -q nameserver /etc/resolv.conf 2>/dev/null; then
        _flux_log "Writing /etc/resolv.conf"
        printf 'nameserver 8.8.8.8\nnameserver 1.1.1.1\nnameserver 8.8.4.4\n' > /etc/resolv.conf
    fi
}

_flux_ensure_dbus() {
    if command -v dbus-uuidgen >/dev/null 2>&1; then
        dbus-uuidgen --ensure=/etc/machine-id 2>/dev/null || true
    fi
    mkdir -p /var/lib/dbus
    if [ ! -e /var/lib/dbus/machine-id ]; then
        if [ -f /etc/machine-id ]; then
            ln -sf /etc/machine-id /var/lib/dbus/machine-id 2>/dev/null \
                || cp -f /etc/machine-id /var/lib/dbus/machine-id 2>/dev/null || true
        elif command -v dbus-uuidgen >/dev/null 2>&1; then
            dbus-uuidgen --ensure=/var/lib/dbus/machine-id 2>/dev/null || true
        fi
    fi
}

_flux_ensure_groups() {
    # Manjaro bootstrap ships a root-only group file. Create before useradd.
    if command -v groupadd >/dev/null 2>&1; then
        for g in wheel audio video input users netdev storage optical; do
            if ! grep -q "^$g:" /etc/group 2>/dev/null; then
                groupadd "$g" 2>/dev/null || true
            fi
        done
    elif command -v addgroup >/dev/null 2>&1; then
        for g in wheel audio video input users netdev; do
            if ! grep -q "^$g:" /etc/group 2>/dev/null; then
                addgroup "$g" 2>/dev/null || true
            fi
        done
    fi
}

_flux_ensure_user() {
    # bash may not exist yet (Chimera bootstrap). Fall back to /bin/sh.
    _flux_shell="/bin/bash"
    if [ ! -e /bin/bash ] && [ ! -e /usr/bin/bash ]; then
        _flux_shell="/bin/sh"
    fi
    if ! id flux >/dev/null 2>&1; then
        _flux_log "Creating user flux (shell $_flux_shell)..."
        if command -v useradd >/dev/null 2>&1; then
            if ! id -u 1000 >/dev/null 2>&1; then
                useradd -m -u 1000 -s "$_flux_shell" flux 2>/dev/null \
                    || useradd -m -s "$_flux_shell" flux
            else
                useradd -m -s "$_flux_shell" flux
            fi
        else
            adduser -D -s "$_flux_shell" flux 2>/dev/null || true
        fi
        echo "flux:flux" | chpasswd 2>/dev/null || true
    fi
    for g in wheel audio video netdev input users; do
        if command -v usermod >/dev/null 2>&1; then
            usermod -aG "$g" flux 2>/dev/null || true
        else
            addgroup flux "$g" 2>/dev/null || true
        fi
    done
}

# Fedora/sudo under proot still runs PAM account mgmt. pam_unix + audit
# fail → "password is required" even with NOPASSWD.
_flux_repair_sudo_pam() {
    command -v sudo >/dev/null 2>&1 || return 0
    if [ -f /etc/sudoers ]; then
        grep -q '^Defaults !authenticate' /etc/sudoers 2>/dev/null \
            || echo 'Defaults !authenticate' >> /etc/sudoers
        grep -q '^Defaults !pam_session' /etc/sudoers 2>/dev/null \
            || echo 'Defaults !pam_session' >> /etc/sudoers
        chmod 0440 /etc/sudoers 2>/dev/null || true
    fi
    if [ -d /etc/pam.d ]; then
        _permit=""
        for _m in \
            /usr/lib64/security/pam_permit.so \
            /lib64/security/pam_permit.so \
            /usr/lib/security/pam_permit.so \
            /lib/security/pam_permit.so
        do
            if [ -f "$_m" ]; then _permit=1; break; fi
        done
        if [ -n "$_permit" ]; then
            for _pam in /etc/pam.d/sudo /etc/pam.d/sudo-i; do
                cat > "$_pam" <<'PAM'
#%PAM-1.0
# FluxLinux proot: pam_unix/audit cannot run → sudo asks for a password.
auth       sufficient pam_permit.so
account    sufficient pam_permit.so
password   sufficient pam_permit.so
session    sufficient pam_permit.so
PAM
                chmod 0644 "$_pam" 2>/dev/null || true
            done
        fi
    fi
}

_flux_ensure_sudo() {
    if command -v sudo >/dev/null 2>&1; then
        mkdir -p /etc/sudoers.d
        echo "flux ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/flux
        chmod 0440 /etc/sudoers.d/flux
        chmod 0755 /etc/sudoers.d 2>/dev/null || true
        if [ -f /etc/sudoers ] && ! grep -qE '@includedir[[:space:]]+/etc/sudoers\.d' /etc/sudoers 2>/dev/null; then
            echo '@includedir /etc/sudoers.d' >> /etc/sudoers
        fi
        chmod 0440 /etc/sudoers 2>/dev/null || true
        for _sc in /etc/sudo.conf /usr/etc/sudo.conf; do
            [ -e "$_sc" ] || continue
            chown root:root "$_sc" 2>/dev/null || true
            chmod 0644 "$_sc" 2>/dev/null || true
        done
        chown root:root /etc/sudoers /etc/sudoers.d /etc/sudoers.d/flux 2>/dev/null || true
        chmod 4755 /usr/bin/sudo 2>/dev/null || chmod 4755 /usr/sbin/sudo 2>/dev/null || true
        _flux_repair_sudo_pam
    elif command -v doas >/dev/null 2>&1; then
        # Chimera ships doas (opendoas), not sudo.
        printf 'permit nopass flux\n' > /etc/doas.conf
        # OpenDoas reads the config as the calling user before elevate.
        # 0400 root:root → "doas is not enabled, Permission denied" under proot.
        chmod 0644 /etc/doas.conf
        chown root:root /etc/doas.conf 2>/dev/null || true
        # sudo shim so Flux wrappers (zshrc / scripts) keep working.
        if ! command -v sudo >/dev/null 2>&1; then
            mkdir -p /usr/local/bin
            cat > /usr/local/bin/sudo <<'EOF'
#!/bin/sh
# FluxLinux shim: sudo -> doas (nopass flux).
exec doas "$@"
EOF
            chmod 755 /usr/local/bin/sudo
        fi
    fi
}

_flux_ensure_home() {
    if [ ! -d /home/flux ]; then
        mkdir -p /home/flux
    fi
    _home_uid=$(_flux_stat_u /home)
    _home_gid=$(_flux_stat_g /home)
    if [ -n "$_home_uid" ]; then
        chown -R "$_home_uid:$_home_gid" /home/flux 2>/dev/null || true
        # Proot: /home is the Android app uid. chown flux:flux (uid 1000) makes
        # xfconfd unable to write ~/.config → failsafe / "cannot create /home/flux".
        # Only apply guest flux:flux ownership in real-root chroot (home owner 0).
        if [ "$_home_uid" = "0" ]; then
            chown -R flux:flux /home/flux 2>/dev/null || true
        fi
    else
        chown -R flux:flux /home/flux 2>/dev/null || true
    fi
    chmod 755 /home/flux 2>/dev/null || true
    mkdir -p /home/flux/.config /home/flux/.cache /home/flux/.local/share /home/flux/.vnc
    chmod -R u+rwX /home/flux/.config /home/flux/.cache /home/flux/.local 2>/dev/null || true
    cat > /home/flux/.vnc/xstartup <<'EOF'
#!/bin/sh
export PULSE_SERVER=127.0.0.1
[ -f "$HOME/.Xresources" ] && xrdb "$HOME/.Xresources"
exec startxfce4
EOF
    chmod +x /home/flux/.vnc/xstartup
}

_flux_write_gpu_mode() {
    _mode="${1:-virgl}"
    mkdir -p /etc/fluxlinux
    printf '%s\n' "$_mode" > /etc/fluxlinux/gpu_mode
    _flux_log "gpu_mode=$_mode"
}

_flux_fix_pm_writable() {
    _ref_u=$(_flux_stat_u /etc)
    _ref_g=$(_flux_stat_g /etc)
    [ -n "$_ref_u" ] || return 0
    for p in \
        /var/lib/dnf /var/cache/dnf /var/lib/rpm /var/cache/libdnf5 \
        /var/db/xbps /var/cache/xbps \
        /var/cache/zypp /var/lib/zypp /var/lib/rpm \
        /lib/apk /usr/lib/apk /usr/lib/apk/db /var/cache/apk /etc/apk \
        /var/lib/pacman /var/cache/pacman /etc/pacman.d \
        /var/lib/apt /var/cache/apt /var/lib/dpkg
    do
        [ -e "$p" ] || continue
        chown -R "$_ref_u:$_ref_g" "$p" 2>/dev/null || true
    done
}

_flux_disable_guest_selinux() {
    if [ -f /etc/selinux/config ]; then
        sed -i 's/^SELINUX=.*/SELINUX=disabled/' /etc/selinux/config 2>/dev/null || true
    fi
    setenforce 0 2>/dev/null || true
}

_flux_require_startxfce4() {
    if [ ! -e /usr/bin/startxfce4 ] && [ ! -e /usr/sbin/startxfce4 ] \
        && [ ! -e /usr/local/bin/startxfce4 ] \
        && ! command -v startxfce4 >/dev/null 2>&1; then
        echo "FluxLinux: ERROR: startxfce4 missing after desktop install"
        exit 1
    fi
}

# Materialize a UTF-8 locale so launchers / agnosterzak / pokemon work.
# Manjaro ARM bootstrap has a fully-commented locale.gen and no
# locale-archive; localedef *into the archive* often fails under proot.
# Directory locales (--no-archive) work. Never export a name glibc cannot
# load (setlocale warnings + zsh "prompt_segment: character not in range").
# Presence is locale -a only — leftover /usr/lib/locale dir names lie.
_flux_locale_listed() {
    # locale -a prints en_US.utf8; callers may pass en_US.UTF-8. Equate them.
    _want="$1"
    locale -a 2>/dev/null | grep -qxFi "$_want" && return 0
    case "$_want" in
        *.UTF-8)
            locale -a 2>/dev/null | grep -qxFi "${_want%.UTF-8}.utf8" && return 0
            ;;
        *.utf8)
            locale -a 2>/dev/null | grep -qxFi "${_want%.utf8}.UTF-8" && return 0
            ;;
    esac
    return 1
}

_flux_locale_has_en_us() {
    _flux_locale_listed en_US.UTF-8
}

_flux_locale_has_c_utf8() {
    _flux_locale_listed C.UTF-8
}

# Drop inherited LANG/LC_ALL when glibc cannot load them (install + login).
_flux_sanitize_lang() {
    _cur="${LC_ALL:-${LANG:-}}"
    if [ -z "$_cur" ] || [ "$_cur" = "C" ] || [ "$_cur" = "POSIX" ]; then
        unset LC_ALL
        export LANG=C
        unset _cur
        return 0
    fi
    if _flux_locale_listed "$_cur"; then
        unset _cur
        return 0
    fi
    unset LC_ALL
    export LANG=C
    unset _cur
}

_flux_sanitize_lang

_flux_write_locale_profile() {
    mkdir -p /etc/profile.d
    cat > /etc/profile.d/flux-locale.sh <<'EOF'
# Never export a locale glibc cannot load.
_have=$(locale -a 2>/dev/null || true)
_pick=""
for _c in en_US.UTF-8 en_US.utf8 C.UTF-8 C.utf8; do
  echo "$_have" | grep -qxFi "$_c" && { _pick="$_c"; break; }
done
if [ -n "$_pick" ]; then
  export LANG="$_pick" LC_ALL="$_pick"
else
  unset LC_ALL
  export LANG=C
  echo "FluxLinux: WARNING: no UTF-8 locale in locale -a; zsh staying LANG=C" >&2
fi
unset _have _c _pick
EOF
}

_flux_charmap_utf8() {
    _map=/tmp/flux-UTF-8
    if [ -s "$_map" ]; then
        printf '%s' "$_map"
        return 0
    fi
    if [ -f /usr/share/i18n/charmaps/UTF-8.gz ] && command -v gzip >/dev/null 2>&1; then
        gzip -dc /usr/share/i18n/charmaps/UTF-8.gz > "$_map" || true
    elif [ -f /usr/share/i18n/charmaps/UTF-8 ]; then
        cp -f /usr/share/i18n/charmaps/UTF-8 "$_map" || true
    fi
    if [ -s "$_map" ]; then
        printf '%s' "$_map"
        return 0
    fi
    return 1
}

# Directory locale under /usr/lib/locale — works when locale-archive is
# missing or unwritable (Manjaro/Arch under proot).
_flux_localedef_dir() {
    _in="$1"
    _dest="$2"
    command -v localedef >/dev/null 2>&1 || return 1
    mkdir -p /usr/lib/locale
    _map=$(_flux_charmap_utf8 || true)
    if [ -n "$_map" ] && [ -s "$_map" ]; then
        localedef --no-archive -c -i "$_in" -f "$_map" "$_dest"
    else
        localedef --no-archive -c -i "$_in" -f UTF-8 "$_dest"
    fi
}

_flux_ensure_en_us_locale() {
    if _flux_is_chimera 2>/dev/null; then
        _flux_log "Chimera/musl — skipping glibc locale generation"
        printf 'LANG=C.UTF-8\n' > /etc/locale.conf
        _flux_write_locale_profile
        _flux_sanitize_lang
        return 0
    fi
    _flux_log "Ensuring en_US.UTF-8 locale"
    if command -v dnf5 >/dev/null 2>&1; then
        dnf5 -y --setopt=install_weak_deps=False --setopt=tsflags=nodocs,noscripts \
            install glibc-langpack-en 2>/dev/null || true
    elif command -v dnf >/dev/null 2>&1; then
        dnf -y --setopt=install_weak_deps=False --setopt=tsflags=nodocs,noscripts \
            install glibc-langpack-en 2>/dev/null || true
    elif command -v zypper >/dev/null 2>&1; then
        zypper --non-interactive install --no-recommends --auto-agree-with-licenses \
            glibc-locale glibc-locale-base 2>/dev/null || true
    elif command -v xbps-install >/dev/null 2>&1; then
        xbps-install -y glibc-locales 2>/dev/null || true
    elif command -v apt-get >/dev/null 2>&1; then
        DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
            locales 2>/dev/null || true
    elif command -v pacman >/dev/null 2>&1; then
        # gzip unpacks UTF-8.gz for localedef. --needed glibc is a no-op on
        # ALARM (already in the db) and does not restore stripped i18n.
        command -v gzip >/dev/null 2>&1 \
            || pacman -S --noconfirm gzip 2>/dev/null || true
        command -v sed >/dev/null 2>&1 \
            || pacman -S --noconfirm sed 2>/dev/null || true
        if [ ! -f /usr/share/i18n/locales/en_US ] \
            || { [ ! -f /usr/share/i18n/charmaps/UTF-8 ] \
                 && [ ! -f /usr/share/i18n/charmaps/UTF-8.gz ]; }; then
            _flux_log "Reinstalling glibc to restore /usr/share/i18n"
            pacman -S --noconfirm glibc 2>/dev/null || true
        fi
    fi
    if [ -f /etc/locale.gen ]; then
        if ! grep -q '^en_US.UTF-8 UTF-8' /etc/locale.gen 2>/dev/null; then
            printf 'en_US.UTF-8 UTF-8\n' >> /etc/locale.gen
        fi
        if ! grep -q '^C.UTF-8 UTF-8' /etc/locale.gen 2>/dev/null; then
            printf 'C.UTF-8 UTF-8\n' >> /etc/locale.gen
        fi
    fi
    _ok=0
    _flux_locale_has_en_us && _ok=1
    if [ "$_ok" != 1 ] && command -v locale-gen >/dev/null 2>&1; then
        locale-gen en_US.UTF-8 2>/dev/null || locale-gen || true
        _flux_locale_has_en_us && _ok=1
    fi
    # Prefer a directory locale: archive writes often fail under proot.
    if [ "$_ok" != 1 ]; then
        if _flux_localedef_dir en_US /usr/lib/locale/en_US.utf8; then
            _ok=1
        elif _flux_localedef_dir en_US en_US.UTF-8; then
            _ok=1
        fi
    fi
    if ! _flux_locale_has_c_utf8; then
        _flux_localedef_dir POSIX /usr/lib/locale/C.utf8 || \
            _flux_localedef_dir C /usr/lib/locale/C.utf8 || true
    fi
    rm -f /tmp/flux-UTF-8
    _flux_locale_has_en_us && _ok=1
    if [ "$_ok" = 1 ]; then
        printf 'LANG=en_US.UTF-8\n' > /etc/locale.conf
        export LANG=en_US.UTF-8
        export LC_ALL=en_US.UTF-8
    elif _flux_locale_has_c_utf8; then
        _flux_log "WARNING: en_US.UTF-8 missing — using C.UTF-8"
        printf 'LANG=C.UTF-8\n' > /etc/locale.conf
        export LANG=C.UTF-8
        export LC_ALL=C.UTF-8
    else
        _flux_log "ERROR: no UTF-8 locale after generation (locale -a has neither en_US.utf8 nor C.utf8)"
        _flux_write_locale_profile
        return 1
    fi
    _flux_write_locale_profile
    _flux_sanitize_lang
}
# --- end common ---

# setup_fedora_family.sh — Fedora 44 XFCE (proot + chroot). dnf/dnf5.
# Common helpers may be prepended by BaseDesktopInstallPlan.

DISTRO_NAME="${1:-fedora}"
echo "FluxLinux: Configuring ${DISTRO_NAME} (Fedora Family)..."

if [ "$(id -u)" -ne 0 ]; then
    echo "FluxLinux: ERROR: must run as root inside the guest (got uid=$(id -u))."
    exit 1
fi


_flux_ensure_tmp
_flux_ensure_dns
_flux_disable_guest_selinux

DNF="dnf"
if command -v dnf5 >/dev/null 2>&1; then
    DNF="dnf5"
fi
if ! command -v "$DNF" >/dev/null 2>&1; then
    echo "FluxLinux: ERROR: dnf/dnf5 not found"
    exit 1
fi

unset LC_ALL
export LANG=C
# Scriptlets (gtk-update-icon-cache, udev, systemd) hang under proot.
export SYSTEMD_OFFLINE=1
export SYSTEMD_SKIP_UNMOUNTS=1
DNF_OPTS="-y --setopt=install_weak_deps=False --setopt=tsflags=nodocs,noscripts"

# fedora-minimal omits the OpenSSL default bundle symlink. Restore before
# dnf talks to metalink HTTPS.
if [ ! -e /etc/pki/tls/cert.pem ] && [ -f /etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem ]; then
    mkdir -p /etc/pki/tls/certs /etc/ssl
    ln -sfn /etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem /etc/pki/tls/cert.pem
    ln -sfn /etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem /etc/pki/tls/certs/ca-bundle.crt
    ln -sfn /etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem /etc/ssl/cert.pem
fi

_flux_log "dnf makecache ($DNF)..."
$DNF -y --setopt=install_weak_deps=False makecache || {
    echo "FluxLinux: dnf makecache failed (network / repos)"
    exit 1
}

_flux_log "Installing base packages..."
$DNF $DNF_OPTS install \
    bash sudo shadow-utils passwd ca-certificates curl wget unzip tar \
    gzip xz tzdata \
    dbus dbus-daemon \
    || {
        echo "FluxLinux: base package install failed"
        exit 1
    }
# dbus-x11 is optional on F44 (some images use dbus-daemon only)
$DNF $DNF_OPTS install dbus-x11 2>/dev/null || true

_flux_ensure_dbus

_flux_log "Installing XFCE4..."
$DNF $DNF_OPTS install \
    xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
    xfdesktop xfwm4 thunar \
    adwaita-icon-theme dejavu-sans-fonts \
    gsettings-desktop-schemas \
    mesa-dri-drivers mesa-libGL mesa-libEGL \
    || {
        echo "FluxLinux: XFCE package install failed"
        exit 1
    }
$DNF $DNF_OPTS install mesa-vulkan-drivers 2>/dev/null || true
$DNF $DNF_OPTS install gdk-pixbuf2-modules librsvg2 2>/dev/null || true

# Rebuild caches skipped by tsflags=noscripts; replace bwrap so GTK/glycin
# SVG/PNG loaders work under proot (no user namespaces).
gdk-pixbuf-query-loaders-64 --update-cache 2>/dev/null \
    || gdk-pixbuf-query-loaders --update-cache 2>/dev/null || true
command -v glib-compile-schemas >/dev/null 2>&1 && \
    glib-compile-schemas /usr/share/glib-2.0/schemas 2>/dev/null || true
command -v update-mime-database >/dev/null 2>&1 && update-mime-database /usr/share/mime 2>/dev/null || true
if [ -x /usr/bin/bwrap ] && [ ! -e /usr/bin/bwrap.real ] && \
   ! grep -q 'FluxLinux proot' /usr/bin/bwrap 2>/dev/null; then
    mv /usr/bin/bwrap /usr/bin/bwrap.real 2>/dev/null || true
fi
cat > /usr/bin/bwrap <<'BWRAP_EOF'
#!/bin/sh
# FluxLinux: exec the real glycin loader, not an earlier --ro-bind source.
while [ $# -gt 0 ]; do
  case "$1" in
    /usr/libexec/glycin-loaders/*|/usr/lib/glycin-loaders/*|/usr/bin/true|/bin/true)
      if [ -f "$1" ] && [ -x "$1" ]; then
        exec "$@"
      fi
      ;;
  esac
  shift
done
echo "bwrap-shim: no command" >&2
exit 127
BWRAP_EOF
chmod 755 /usr/bin/bwrap
if [ -f /tmp/bwrap-proot-shim ] && [ "$(stat -c %s /tmp/bwrap-proot-shim 2>/dev/null || echo 0)" -gt 20000 ]; then
    cp -f /tmp/bwrap-proot-shim /usr/bin/bwrap
    chmod 755 /usr/bin/bwrap
fi

# Container image ships only C.utf8. Launchers export en_US.UTF-8.
$DNF $DNF_OPTS install glibc-langpack-en || true
if ! ls /usr/lib/locale 2>/dev/null | grep -qi 'en_US'; then
    localedef -i en_US -f UTF-8 en_US.UTF-8 || true
fi
printf 'LANG=en_US.UTF-8\n' > /etc/locale.conf
if command -v _flux_ensure_en_us_locale >/dev/null 2>&1; then
    _flux_ensure_en_us_locale || exit 1
fi

_flux_require_startxfce4
_flux_ensure_user
_flux_ensure_sudo
_flux_ensure_home
_flux_write_gpu_mode virgl
_flux_fix_pm_writable

echo "FluxLinux: ${DISTRO_NAME} Fedora setup complete!"
exit 0
