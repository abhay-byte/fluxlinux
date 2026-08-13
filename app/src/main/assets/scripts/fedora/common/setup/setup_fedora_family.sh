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

# --- end common ---
# setup_fedora_family.sh — Fedora 43 XFCE (proot + chroot). dnf/dnf5.
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

export LANG="${LANG:-C.UTF-8}"
export LC_ALL="${LC_ALL:-C.UTF-8}"
# Scriptlets (gtk-update-icon-cache, udev, systemd) hang under proot.
export SYSTEMD_OFFLINE=1
export SYSTEMD_SKIP_UNMOUNTS=1
DNF_OPTS="-y --setopt=install_weak_deps=False --setopt=tsflags=nodocs,noscripts"

_flux_log "dnf makecache ($DNF)..."
$DNF -y --setopt=install_weak_deps=False makecache || {
    echo "FluxLinux: dnf makecache failed (network / repos)"
    exit 1
}

_flux_log "Installing base packages..."
$DNF $DNF_OPTS install \
    bash sudo shadow-utils passwd ca-certificates curl wget unzip tar \
    dbus dbus-daemon \
    || {
        echo "FluxLinux: base package install failed"
        exit 1
    }
# dbus-x11 is optional on F43 (some images use dbus-daemon only)
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
    _flux_ensure_en_us_locale
fi

_flux_require_startxfce4
_flux_ensure_user
_flux_ensure_sudo
_flux_ensure_home
_flux_write_gpu_mode virgl
_flux_fix_pm_writable

echo "FluxLinux: ${DISTRO_NAME} Fedora setup complete!"
exit 0
