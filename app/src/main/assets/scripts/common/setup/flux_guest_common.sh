#!/bin/sh
# flux_guest_common.sh — shared guest helpers (sourced by family / customization).
# Safe to concatenate in front of a family payload. Idempotent functions.

export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin${PATH:+:$PATH}"
# Host Termux libs (OpenSSL/LDAP) must not leak into glibc guests.
unset LD_LIBRARY_PATH
unset LD_PRELOAD

_flux_log() { echo "FluxLinux: $*"; }

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

_flux_ensure_user() {
    if ! id flux >/dev/null 2>&1; then
        _flux_log "Creating user flux..."
        if command -v useradd >/dev/null 2>&1; then
            if ! id -u 1000 >/dev/null 2>&1; then
                useradd -m -u 1000 -s /bin/bash flux 2>/dev/null \
                    || useradd -m -s /bin/bash flux
            else
                useradd -m -s /bin/bash flux
            fi
        else
            adduser -D -s /bin/bash flux 2>/dev/null || true
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
    mkdir -p /etc/sudoers.d
    echo "flux ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/flux
    chmod 0440 /etc/sudoers.d/flux
    chmod 0755 /etc/sudoers.d 2>/dev/null || true
    if [ -f /etc/sudoers ] && ! grep -qE '@includedir[[:space:]]+/etc/sudoers\.d' /etc/sudoers 2>/dev/null; then
        echo '@includedir /etc/sudoers.d' >> /etc/sudoers
    fi
    chmod 0440 /etc/sudoers 2>/dev/null || true
    # openSUSE TW also reads /usr/etc/sudo.conf; both must be root-owned.
    for _sc in /etc/sudo.conf /usr/etc/sudo.conf; do
        [ -e "$_sc" ] || continue
        chown root:root "$_sc" 2>/dev/null || true
        chmod 0644 "$_sc" 2>/dev/null || true
    done
    chown root:root /etc/sudoers /etc/sudoers.d /etc/sudoers.d/flux 2>/dev/null || true
    chmod 4755 /usr/bin/sudo 2>/dev/null || chmod 4755 /usr/sbin/sudo 2>/dev/null || true
}

_flux_ensure_home() {
    if [ ! -d /home/flux ]; then
        mkdir -p /home/flux
    fi
    _home_uid=$(stat -c %u /home 2>/dev/null || true)
    _home_gid=$(stat -c %g /home 2>/dev/null || true)
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
    _ref_u=$(stat -c %u /etc 2>/dev/null || true)
    _ref_g=$(stat -c %g /etc 2>/dev/null || true)
    [ -n "$_ref_u" ] || return 0
    for p in \
        /var/lib/dnf /var/cache/dnf /var/lib/rpm /var/cache/libdnf5 \
        /var/db/xbps /var/cache/xbps \
        /var/cache/zypp /var/lib/zypp /var/lib/rpm \
        /lib/apk /var/cache/apk /etc/apk
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
        && [ ! -e /usr/local/bin/startxfce4 ]; then
        echo "FluxLinux: ERROR: startxfce4 missing after desktop install"
        exit 1
    fi
}
