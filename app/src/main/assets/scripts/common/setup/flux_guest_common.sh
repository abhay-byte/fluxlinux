#!/bin/sh
# flux_guest_common.sh — shared guest helpers (sourced by family / customization).
# Safe to concatenate in front of a family payload. Idempotent functions.

# Guest bins only — never append host $PATH (PRoot repair used to see host pactl).
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
# Host Termux libs (OpenSSL/LDAP) must not leak into glibc guests.
unset LD_LIBRARY_PATH
unset LD_PRELOAD

_flux_log() { echo "FluxLinux: $*"; }

# Portable stat: GNU coreutils uses `-c %u`, BSD userland (Chimera) uses `-f %u`.
_flux_stat_u() { stat -c %u "$1" 2>/dev/null || stat -f %u "$1" 2>/dev/null || true; }
_flux_stat_g() { stat -c %g "$1" 2>/dev/null || stat -f %g "$1" 2>/dev/null || true; }

# GNU sed -i '' edits a backup named ''; BSD sed -i requires a suffix (or '').
_flux_sed_i() {
    _expr=$1
    _file=$2
    [ -n "$_file" ] && [ -f "$_file" ] || return 1
    _tmp="${_file}.fluxsed.$$"
    if sed "$_expr" "$_file" > "$_tmp"; then
        mv "$_tmp" "$_file"
    else
        rm -f "$_tmp"
        return 1
    fi
}

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
    # Android supplies a comma-separated list from the active validated
    # LinkProperties. Prefer it over rootfs/public DNS and write only numeric
    # addresses so malformed inherited environment cannot become shell code.
    if [ -n "${FLUX_DNS_SERVERS:-}" ]; then
        _flux_dns_tmp="/etc/resolv.conf.flux.$$"
        : > "$_flux_dns_tmp"
        _flux_old_ifs=$IFS
        IFS=,
        for _flux_dns in $FLUX_DNS_SERVERS; do
            case "$_flux_dns" in
                ''|*[!0-9A-Fa-f:.%]*) continue ;;
            esac
            printf 'nameserver %s\n' "$_flux_dns" >> "$_flux_dns_tmp"
        done
        IFS=$_flux_old_ifs
        if [ -s "$_flux_dns_tmp" ]; then
            if ! cmp -s "$_flux_dns_tmp" /etc/resolv.conf 2>/dev/null; then
                _flux_log "Writing Android active-network DNS to /etc/resolv.conf"
                mv -f "$_flux_dns_tmp" /etc/resolv.conf
            else
                rm -f "$_flux_dns_tmp"
            fi
            return 0
        fi
        rm -f "$_flux_dns_tmp"
    fi
    if [ ! -s /etc/resolv.conf ] || ! grep -q nameserver /etc/resolv.conf 2>/dev/null; then
        _flux_log "Android DNS unavailable; using final public DNS fallback"
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
export PULSE_SERVER=tcp:127.0.0.1
[ -f "$HOME/.Xresources" ] && xrdb "$HOME/.Xresources"
exec startxfce4
EOF
    chmod +x /home/flux/.vnc/xstartup
    if command -v _flux_write_pulse_client >/dev/null 2>&1; then
        _flux_write_pulse_client
    fi
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

# Shared apt sandbox for Ubuntu / Kali / Parrot (and reusable by Deepin).
# One helper — do not copy-paste three slightly different sandboxes.
_flux_apt_prep() {
    export DEBIAN_FRONTEND=noninteractive
    export DEBCONF_NONINTERACTIVE_SEEN=true
    export SYSTEMD_OFFLINE=1
    mkdir -p /etc/apt/apt.conf.d
    printf 'APT::Sandbox::User "root";\nDPkg::Use-Pty "false";\n' \
        > /etc/apt/apt.conf.d/99flux-nosandbox
}

# Host Pulse in the app PREFIX. Guests are clients only (no guest daemon).
_flux_write_pulse_client() {
    mkdir -p /etc/profile.d
    printf 'export PULSE_SERVER=tcp:127.0.0.1\n' > /etc/profile.d/flux-pulse.sh
    chmod 644 /etc/profile.d/flux-pulse.sh 2>/dev/null || true
    if [ -f /etc/environment ]; then
        if grep -q '^PULSE_SERVER=' /etc/environment 2>/dev/null; then
            _flux_sed_i 's|^PULSE_SERVER=.*|PULSE_SERVER=tcp:127.0.0.1|' /etc/environment
        else
            printf 'PULSE_SERVER=tcp:127.0.0.1\n' >> /etc/environment
        fi
    else
        printf 'PULSE_SERVER=tcp:127.0.0.1\n' > /etc/environment
    fi
    mkdir -p /etc/pulse/client.conf.d 2>/dev/null || true
    if [ -d /etc/pulse/client.conf.d ]; then
        cat > /etc/pulse/client.conf.d/99-fluxlinux.conf <<'EOF'
default-server = tcp:127.0.0.1
autospawn = no
EOF
    fi
    if [ -f /etc/pulse/client.conf ]; then
        if grep -qE '^;?[[:space:]]*default-server' /etc/pulse/client.conf 2>/dev/null; then
            _flux_sed_i 's/^;*[[:space:]]*default-server.*/default-server = tcp:127.0.0.1/' /etc/pulse/client.conf
        else
            printf '\ndefault-server = tcp:127.0.0.1\n' >> /etc/pulse/client.conf
        fi
        if grep -qE '^;?[[:space:]]*autospawn' /etc/pulse/client.conf 2>/dev/null; then
            _flux_sed_i 's/^;*[[:space:]]*autospawn.*/autospawn = no/' /etc/pulse/client.conf
        else
            printf 'autospawn = no\n' >> /etc/pulse/client.conf
        fi
    fi
    if [ -f /home/flux/.vnc/xstartup ]; then
        _flux_sed_i 's|^export PULSE_SERVER=.*|export PULSE_SERVER=tcp:127.0.0.1|' /home/flux/.vnc/xstartup
    fi
}

_flux_mask_pipewire_pulse() {
    # Do not remove PipeWire — only stop pipewire-pulse stealing 4713 / the default.
    if command -v systemctl >/dev/null 2>&1; then
        systemctl --user mask --now pipewire-pulse.socket pipewire-pulse.service 2>/dev/null || true
        systemctl mask pipewire-pulse.socket pipewire-pulse.service 2>/dev/null || true
    fi
    for _unit in pipewire-pulse.socket pipewire-pulse.service; do
        for _dir in /etc/systemd/user /etc/systemd/system; do
            mkdir -p "$_dir"
            if [ ! -e "$_dir/$_unit" ]; then
                ln -sf /dev/null "$_dir/$_unit" 2>/dev/null || true
            fi
        done
    done
}

_flux_try_install() {
    # Best-effort named packages. Never handle_error the family if a plugin is missing.
    _flux_log "Installing Pulse client: $*"
    if command -v apt-get >/dev/null 2>&1; then
        _flux_apt_prep
        DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "$@"
    elif command -v apk >/dev/null 2>&1; then
        if _flux_is_chimera 2>/dev/null; then
            apk add "$@"
        else
            apk add --no-cache "$@"
        fi
    elif command -v dnf5 >/dev/null 2>&1; then
        dnf5 -y --setopt=install_weak_deps=False --setopt=tsflags=nodocs install "$@"
    elif command -v dnf >/dev/null 2>&1; then
        dnf -y --setopt=install_weak_deps=False --setopt=tsflags=nodocs install "$@"
    elif command -v zypper >/dev/null 2>&1; then
        zypper --non-interactive install --no-recommends --auto-agree-with-licenses "$@"
    elif command -v xbps-install >/dev/null 2>&1; then
        xbps-install -y "$@"
    elif command -v pacman >/dev/null 2>&1; then
        pacman -S --noconfirm --needed "$@"
    else
        return 1
    fi
}

# Host $PREFIX/bin/pactl must not count. Only a guest-tree binary.
_flux_guest_pactl() {
    for _p in /usr/bin/pactl /usr/sbin/pactl /bin/pactl; do
        if [ -x "$_p" ]; then
            echo "$_p"
            return 0
        fi
    done
    return 1
}

_flux_install_pulse_client() {
    _have=$(_flux_guest_pactl || true)
    if [ -n "$_have" ]; then
        _flux_log "pulse client already present ($_have)"
        if command -v dnf5 >/dev/null 2>&1 || command -v dnf >/dev/null 2>&1 \
            || command -v zypper >/dev/null 2>&1; then
            _flux_mask_pipewire_pulse
        fi
        return 0
    fi
    if command -v apt-get >/dev/null 2>&1; then
        _flux_try_install libpulse0 pulseaudio-utils xfce4-pulseaudio-plugin \
            || _flux_try_install libpulse0 pulseaudio-utils || true
    elif _flux_is_chimera 2>/dev/null; then
        _flux_try_install libpulse libpulse-progs xfce4-pulseaudio-plugin \
            || _flux_try_install libpulse libpulse-progs || true
    elif command -v apk >/dev/null 2>&1; then
        _flux_try_install libpulse pulseaudio-utils xfce4-pulseaudio \
            || _flux_try_install libpulse pulseaudio-utils || true
    elif command -v dnf5 >/dev/null 2>&1 || command -v dnf >/dev/null 2>&1; then
        _flux_try_install pulseaudio-libs pulseaudio-utils xfce4-pulseaudio-plugin \
            || _flux_try_install pulseaudio-libs pulseaudio-utils || true
        _flux_mask_pipewire_pulse
    elif command -v zypper >/dev/null 2>&1; then
        _flux_try_install libpulse0 pulseaudio-utils xfce4-pulseaudio-plugin \
            || _flux_try_install libpulse0 pulseaudio-utils \
            || _flux_try_install libpulse0 || true
        _flux_mask_pipewire_pulse
    elif command -v xbps-install >/dev/null 2>&1; then
        # Never fall back to the `pulseaudio` server package.
        _flux_try_install pulseaudio-utils xfce4-pulseaudio-plugin \
            || _flux_try_install pulseaudio-utils \
            || _flux_try_install libpulseaudio || true
    elif command -v pacman >/dev/null 2>&1; then
        _flux_try_install libpulse xfce4-pulseaudio-plugin \
            || _flux_try_install libpulse || true
    fi
    _have=$(_flux_guest_pactl || true)
    if [ -n "$_have" ]; then
        _flux_log "pulse client ready ($_have)"
    else
        _flux_log "WARN pulse client (pactl) still missing"
    fi
}

_flux_setup_pulse() {
    _flux_write_pulse_client
    _flux_install_pulse_client
}
