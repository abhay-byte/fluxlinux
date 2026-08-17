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

# setup_opensuse_family.sh — openSUSE Tumbleweed XFCE (proot + chroot). zypper.

DISTRO_NAME="${1:-opensuse}"
echo "FluxLinux: Configuring ${DISTRO_NAME} (openSUSE Family)..."

if [ "$(id -u)" -ne 0 ]; then
    echo "FluxLinux: ERROR: must run as root inside the guest (got uid=$(id -u))."
    exit 1
fi


_flux_ensure_tmp
_flux_ensure_dns

if ! command -v zypper >/dev/null 2>&1; then
    echo "FluxLinux: ERROR: zypper not found"
    exit 1
fi

unset LC_ALL
export LANG=C
export ZYPP_LOCK_TIMEOUT=300
export SYSTEMD_OFFLINE=1
export SYSTEMD_SKIP_UNMOUNTS=1
export YAST_IS_RUNNING=instsys
unset LD_LIBRARY_PATH LD_PRELOAD

# Minirootfs ships libcurl-mini (no LDAP). Installing `curl`/`libcurl4` pulls
# libldap2, which still needs EVP_md2@OPENSSL_3.0.0 — removed from OpenSSL 3.5.3
# already in this rootfs. zypper then dies on every invocation.
_CURL_SO=""
for _c in /usr/lib64/libcurl.so.4.8.0 /usr/lib64/libcurl.so.4 /usr/lib/libcurl.so.4; do
    if [ -f "$_c" ] && [ ! -L "$_c" ]; then
        _CURL_SO="$_c"
        break
    fi
done
[ -n "$_CURL_SO" ] || _CURL_SO=/usr/lib64/libcurl.so.4.8.0
_CURL_STASH="${_CURL_SO}.flux-mini"

_flux_curl_needs_ldap() {
    [ -f "$1" ] || return 1
    if command -v readelf >/dev/null 2>&1; then
        readelf -d "$1" 2>/dev/null | grep -q 'libldap'
        return $?
    fi
    if command -v objdump >/dev/null 2>&1; then
        objdump -p "$1" 2>/dev/null | grep -q 'libldap'
        return $?
    fi
    return 1
}

_flux_opensuse_protect_zypper() {
    if [ -f "$_CURL_SO" ] && [ ! -f "$_CURL_STASH" ]; then
        if ! _flux_curl_needs_ldap "$_CURL_SO"; then
            cp -f "$_CURL_SO" "$_CURL_STASH" 2>/dev/null || true
        fi
    fi
    if [ -f "$_CURL_STASH" ]; then
        cp -f "$_CURL_STASH" "$_CURL_SO" 2>/dev/null || true
        command -v ldconfig >/dev/null 2>&1 && ldconfig 2>/dev/null || true
    fi
}

_flux_opensuse_install_md2_stub() {
    # libldap (pulled by sudo) needs EVP_md2@OPENSSL_3.0.0, removed from
    # OpenSSL 3.5.3 in this rootfs. A tiny stub satisfies the loader;
    # /etc/ld.so.preload applies to setuid sudo.
    _stub=""
    for _c in /tmp/libevp_md2.so /usr/lib64/libevp_md2.so /root/libevp_md2.so; do
        if [ -f "$_c" ] && [ -s "$_c" ]; then
            _stub="$_c"
            break
        fi
    done
    if [ -z "$_stub" ]; then
        return 0
    fi
    cp -f "$_stub" /usr/lib64/libevp_md2.so 2>/dev/null || return 0
    chmod 755 /usr/lib64/libevp_md2.so
    if [ ! -f /etc/ld.so.preload ] || ! grep -q libevp_md2 /etc/ld.so.preload 2>/dev/null; then
        echo /usr/lib64/libevp_md2.so >> /etc/ld.so.preload
    fi
    _flux_log "installed EVP_md2 stub for libldap/sudo"
}

_flux_opensuse_protect_zypper
_flux_opensuse_install_md2_stub

if ! zypper --non-interactive --version >/dev/null 2>&1; then
    echo "FluxLinux: ERROR: zypper unusable (libcurl/libldap vs OpenSSL 3.5)."
    echo "FluxLinux: Reinstall the openSUSE rootfs (libcurl-mini must be restored)."
    exit 1
fi

# Never let zypper replace mini curl. sudo *does* need libldap2 — drop
# any leftover lock from earlier runs, then lock only curl/OpenSSL.
zypper --non-interactive rl libldap2 openldap2-client 2>/dev/null || true
zypper --non-interactive al libcurl-mini4 libcurl4 curl libopenssl3 2>/dev/null || true

if [ -f /etc/zypp/zypp.conf ]; then
    grep -q '^rpm.install.excludedocs' /etc/zypp/zypp.conf 2>/dev/null \
        || echo 'rpm.install.excludedocs = yes' >> /etc/zypp/zypp.conf
fi

_flux_log "zypper refresh..."
zypper --non-interactive --gpg-auto-import-keys refresh || {
    echo "FluxLinux: zypper refresh failed (network / repos)"
    exit 1
}

_flux_log "Installing base packages (no curl/libcurl4)..."
zypper --non-interactive install --no-recommends --auto-agree-with-licenses \
    bash sudo shadow wget unzip tar \
    dbus-1 \
    || {
        echo "FluxLinux: base package install failed"
        exit 1
    }
zypper --non-interactive install --no-recommends --auto-agree-with-licenses dbus-1-daemon 2>/dev/null || true
_flux_opensuse_protect_zypper

_flux_ensure_dbus

_flux_log "Installing XFCE4..."
zypper --non-interactive install --no-recommends --auto-agree-with-licenses \
    xfce4-session xfce4-panel xfce4-settings xfce4-terminal \
    xfdesktop xfwm4 thunar \
    adwaita-icon-theme \
    Mesa-dri Mesa-libGL1 \
    || {
        echo "FluxLinux: XFCE package install failed"
        exit 1
    }
zypper --non-interactive install --no-recommends --auto-agree-with-licenses \
    dejavu-fonts Mesa-libEGL1 2>/dev/null || true
_flux_opensuse_protect_zypper

# TW GTK/glycin uses bwrap; replace with Alpine-style proot-safe shim.
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

if ! ls /usr/lib/locale 2>/dev/null | grep -qi 'en_US'; then
    zypper --non-interactive install --no-recommends --auto-agree-with-licenses \
        glibc-locale glibc-locale-base 2>/dev/null || true
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

echo "FluxLinux: ${DISTRO_NAME} openSUSE setup complete!"
exit 0
