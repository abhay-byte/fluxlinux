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

# Materialize en_US.UTF-8 so launchers (LANG=en_US.UTF-8) do not print
# "cannot change locale" and agnosterzak can paint @flux. Musl/Chimera
# keeps C.UTF-8. Idempotent; safe after every glibc family package pass.
_flux_write_locale_profile() {
    mkdir -p /etc/profile.d
    cat > /etc/profile.d/flux-locale.sh <<'EOF'
if locale -a 2>/dev/null | grep -qiE 'en_US\.(utf8|UTF-8)'; then
  export LANG=en_US.UTF-8
  export LC_ALL=en_US.UTF-8
else
  export LANG=C.UTF-8
  export LC_ALL=C.UTF-8
fi
EOF
}

_flux_locale_has_en_us() {
    ls /usr/lib/locale 2>/dev/null | grep -qi 'en_US' && return 0
    locale -a 2>/dev/null | grep -qiE 'en_US\.(utf8|UTF-8)' && return 0
    return 1
}

_flux_ensure_en_us_locale() {
    if _flux_is_chimera 2>/dev/null; then
        _flux_log "Chimera/musl — skipping glibc locale generation"
        printf 'LANG=C.UTF-8\n' > /etc/locale.conf
        _flux_write_locale_profile
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
    fi
    if [ -f /etc/locale.gen ]; then
        if ! grep -q '^en_US.UTF-8 UTF-8' /etc/locale.gen 2>/dev/null; then
            printf 'en_US.UTF-8 UTF-8\n' >> /etc/locale.gen
        fi
    fi
    _ok=0
    _flux_locale_has_en_us && _ok=1
    if [ "$_ok" != 1 ] && command -v locale-gen >/dev/null 2>&1; then
        locale-gen en_US.UTF-8 2>/dev/null || locale-gen || true
        _flux_locale_has_en_us && _ok=1
    fi
    if [ "$_ok" != 1 ] && command -v localedef >/dev/null 2>&1; then
        _map=/tmp/flux-UTF-8
        if [ -f /usr/share/i18n/charmaps/UTF-8.gz ] && command -v gzip >/dev/null 2>&1; then
            gzip -dc /usr/share/i18n/charmaps/UTF-8.gz > "$_map" || true
        elif [ -f /usr/share/i18n/charmaps/UTF-8 ]; then
            cp -f /usr/share/i18n/charmaps/UTF-8 "$_map"
        fi
        if [ -s "$_map" ]; then
            localedef -i en_US -f "$_map" en_US.UTF-8 && _ok=1
        else
            localedef -i en_US -f UTF-8 en_US.UTF-8 && _ok=1
        fi
        rm -f "$_map"
    fi
    _flux_locale_has_en_us && _ok=1
    if [ "$_ok" != 1 ]; then
        _flux_log "WARNING: en_US.UTF-8 locale missing after generation"
    fi
    printf 'LANG=en_US.UTF-8\n' > /etc/locale.conf
    _flux_write_locale_profile
}
