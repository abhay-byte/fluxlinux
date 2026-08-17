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
    bash sudo shadow-utils util-linux passwd ca-certificates curl wget unzip tar \
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
_flux_setup_pulse
_flux_write_gpu_mode virgl
_flux_fix_pm_writable

echo "FluxLinux: ${DISTRO_NAME} Fedora setup complete!"
exit 0
