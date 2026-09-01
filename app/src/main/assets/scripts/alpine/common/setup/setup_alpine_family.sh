#!/bin/sh
# setup_alpine_family.sh
# Post-install base desktop for Alpine (proot + chroot). apk + musl.
# Creates user flux, installs XFCE4. Must run as root inside the guest.

DISTRO_NAME="${1:-alpine}"

echo "FluxLinux: Configuring ${DISTRO_NAME} (Alpine Family)..."

if [ "$(id -u)" -ne 0 ]; then
    echo "FluxLinux: ERROR: must run as root inside the guest (got uid=$(id -u))."
    exit 1
fi

export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin${PATH:+:$PATH}"

# Shared /tmp (--shared-tmp) must be sticky-writable for guest uid 1000.
mkdir -p /tmp /var/tmp
chmod 1777 /tmp /var/tmp 2>/dev/null || true
unset PROOT_TMP_DIR
export TMPDIR=/tmp

# 1. DNS. The common helper is prepended by flux_install.sh; keep this
# fallback for direct/chroot invocation too. Android DNS wins when supplied;
# otherwise a usable guest resolver is preserved before public fallback.
if command -v _flux_ensure_dns >/dev/null 2>&1; then
    _flux_ensure_dns
elif [ -n "${FLUX_DNS_SERVERS:-}" ]; then
    _dns_tmp=/etc/resolv.conf.flux.$$
    : > "$_dns_tmp"
    _old_ifs=$IFS
    IFS=,
    for _dns in $FLUX_DNS_SERVERS; do
        case "$_dns" in ''|*[!0-9A-Fa-f:.%]*) continue ;; esac
        printf 'nameserver %s\n' "$_dns" >> "$_dns_tmp"
    done
    IFS=$_old_ifs
    if [ -s "$_dns_tmp" ]; then
        mv -f "$_dns_tmp" /etc/resolv.conf
    else
        rm -f "$_dns_tmp"
        if [ ! -s /etc/resolv.conf ] || ! grep -qE '^[[:space:]]*nameserver[[:space:]]+[0-9A-Fa-f:.%]+([[:space:]]|$)' /etc/resolv.conf 2>/dev/null; then
            printf 'nameserver 8.8.8.8\nnameserver 1.1.1.1\nnameserver 8.8.4.4\n' > /etc/resolv.conf
        fi
    fi
else
    if [ ! -s /etc/resolv.conf ] || ! grep -qE '^[[:space:]]*nameserver[[:space:]]+[0-9A-Fa-f:.%]+([[:space:]]|$)' /etc/resolv.conf 2>/dev/null; then
        printf 'nameserver 8.8.8.8\nnameserver 1.1.1.1\nnameserver 8.8.4.4\n' > /etc/resolv.conf
    fi
fi

# 2. Repositories sanity (keep pin to 3.24 if present)
if [ -f /etc/apk/repositories ]; then
    if ! grep -q 'community' /etc/apk/repositories 2>/dev/null; then
        echo "https://dl-cdn.alpinelinux.org/alpine/v3.24/community" >> /etc/apk/repositories
    fi
fi

echo "FluxLinux: apk update..."
apk update || {
    echo "FluxLinux: apk update failed (network / repos)"
    exit 1
}

# 3. Bootstrap tools (bash for later scripts; shadow for useradd; sudo)
echo "FluxLinux: Installing base packages..."
apk add --no-cache \
    bash \
    sudo \
    shadow \
    ca-certificates \
    curl \
    wget \
    unzip \
    tar \
    tzdata \
    musl-locales \
    dbus \
    dbus-x11 \
    || {
        echo "FluxLinux: base package install failed"
        exit 1
    }

# Session bus soft-depends on machine-id (empty /var/lib/dbus breaks some stacks)
if command -v dbus-uuidgen >/dev/null 2>&1; then
    dbus-uuidgen --ensure=/etc/machine-id 2>/dev/null || true
fi
mkdir -p /var/lib/dbus
if [ ! -e /var/lib/dbus/machine-id ]; then
    if [ -f /etc/machine-id ]; then
        ln -sf /etc/machine-id /var/lib/dbus/machine-id 2>/dev/null || \
            cp -f /etc/machine-id /var/lib/dbus/machine-id 2>/dev/null || true
    elif command -v dbus-uuidgen >/dev/null 2>&1; then
        dbus-uuidgen --ensure=/var/lib/dbus/machine-id 2>/dev/null || true
    fi
fi

# 4. XFCE desktop (always include xfce4-session for failsafe session name)
echo "FluxLinux: Installing XFCE4..."
apk add --no-cache \
    xfce4 \
    xfce4-session \
    xfce4-settings \
    xfce4-panel \
    xfce4-terminal \
    xfce4-screensaver \
    xfdesktop \
    xfwm4 \
    thunar \
    adwaita-icon-theme \
    ttf-dejavu \
    mesa-dri-gallium \
    mesa-gl \
    || {
        echo "FluxLinux: XFCE package install failed"
        exit 1
    }
# Named Pulse *client* only — do not pull the xfce4 metapackage for audio.
apk add --no-cache libpulse pulseaudio-utils xfce4-pulseaudio 2>/dev/null \
    || apk add --no-cache libpulse pulseaudio-utils 2>/dev/null || true

if [ ! -e /usr/bin/startxfce4 ]; then
    echo "FluxLinux: ERROR: startxfce4 missing after apk install"
    exit 1
fi

# Glycin image loaders invoke bubblewrap; real bwrap fails under proot and aborts
# GTK (exit 134). Install a shim that drops sandbox flags but keeps --dbus-fd.
echo "FluxLinux: Installing proot-safe bwrap shim for glycin loaders..."
apk add --no-cache glycin-image-rs glycin-svg gdk-pixbuf 2>/dev/null || true
if [ -x /usr/bin/bwrap ] && [ ! -e /usr/bin/bwrap.real ] && \
   ! grep -q 'FluxLinux proot' /usr/bin/bwrap 2>/dev/null; then
    cp -a /usr/bin/bwrap /usr/bin/bwrap.real 2>/dev/null || true
fi
cat > /usr/bin/bwrap <<'BWRAP_EOF'
#!/bin/sh
# FluxLinux proot: skip sandbox; run glycin loader (regular file only — not /).
while [ $# -gt 0 ]; do
  case "$1" in
    /*)
      if [ -f "$1" ] && [ -x "$1" ]; then
        exec "$@"
      fi
      ;;
  esac
  shift
done
exit 127
BWRAP_EOF
chmod 755 /usr/bin/bwrap

# 5. User flux (prefer uid 1000 when free — matches common XFCE defaults)
if ! id flux >/dev/null 2>&1; then
    echo "FluxLinux: Creating user flux..."
    if command -v useradd >/dev/null 2>&1; then
        if ! id -u 1000 >/dev/null 2>&1; then
            useradd -m -u 1000 -s /bin/bash flux 2>/dev/null \
                || useradd -m -s /bin/bash flux 2>/dev/null \
                || useradd -m -s /bin/sh flux
        else
            useradd -m -s /bin/bash flux 2>/dev/null || useradd -m -s /bin/sh flux
        fi
    else
        adduser -D -s /bin/sh flux
    fi
    echo "flux:flux" | chpasswd 2>/dev/null || true
fi

# Groups (best-effort — names differ slightly across Alpine releases)
for g in wheel audio video netdev input; do
    addgroup flux "$g" 2>/dev/null || true
done

# 6. Sudo NOPASSWD
mkdir -p /etc/sudoers.d
echo "flux ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/flux
chmod 0440 /etc/sudoers.d/flux
# Alpine ships @includedir; re-assert if a minimal sudoers was replaced
if [ -f /etc/sudoers ] && ! grep -qE '@includedir[[:space:]]+/etc/sudoers\.d' /etc/sudoers 2>/dev/null; then
    echo '@includedir /etc/sudoers.d' >> /etc/sudoers
fi
chmod 4755 /usr/bin/sudo 2>/dev/null || true

# 7. Optional VNC xstartup parity with Debian family
if id flux >/dev/null 2>&1; then
    mkdir -p /home/flux/.vnc
    cat > /home/flux/.vnc/xstartup <<'EOF'
#!/bin/sh
export PULSE_SERVER=tcp:127.0.0.1
[ -f "$HOME/.Xresources" ] && xrdb "$HOME/.Xresources"
exec startxfce4
EOF
    chmod +x /home/flux/.vnc/xstartup
fi
if command -v _flux_write_pulse_client >/dev/null 2>&1; then
    _flux_write_pulse_client
else
    mkdir -p /etc/profile.d /etc/pulse/client.conf.d
    printf 'export PULSE_SERVER=tcp:127.0.0.1\n' > /etc/profile.d/flux-pulse.sh
    chmod 644 /etc/profile.d/flux-pulse.sh 2>/dev/null || true
    if [ -f /etc/environment ]; then
        if grep -q '^PULSE_SERVER=' /etc/environment 2>/dev/null; then
            sed -i 's|^PULSE_SERVER=.*|PULSE_SERVER=tcp:127.0.0.1|' /etc/environment
        else
            printf 'PULSE_SERVER=tcp:127.0.0.1\n' >> /etc/environment
        fi
    else
        printf 'PULSE_SERVER=tcp:127.0.0.1\n' > /etc/environment
    fi
    cat > /etc/pulse/client.conf.d/99-fluxlinux.conf <<'EOF'
default-server = tcp:127.0.0.1
autospawn = no
EOF
fi

# Ensure home ownership / writability.
# Proot: host process is the Android app uid; chown to flux's guest uid (e.g.
# 10302) can make mode-700 dirs unwritable on disk → xfconfd failsafe.
# Prefer matching /home owner (app uid under proot), then best-effort flux:flux.
if [ -d /home/flux ]; then
    _home_uid=$(stat -c %u /home 2>/dev/null || true)
    _home_gid=$(stat -c %g /home 2>/dev/null || true)
    if [ -n "$_home_uid" ]; then
        chown -R "$_home_uid:$_home_gid" /home/flux 2>/dev/null || true
    fi
    chown -R flux:flux /home/flux 2>/dev/null || true
    chmod 755 /home/flux 2>/dev/null || true
    mkdir -p /home/flux/.config /home/flux/.cache /home/flux/.local/share
    chmod -R u+rwX /home/flux/.config /home/flux/.cache /home/flux/.local 2>/dev/null || true
fi

unset LC_ALL
export LANG="${LANG:-C}"

# Proot: host process is the Android app uid. If apk db/lock were written as
# real host root (uid 0), guest "root"/sudo still cannot lock the DB →
# "Unable to lock database: Permission denied" on `sudo apk`.
_flux_fix_apk_writable() {
    _ref_u=$(stat -c %u /etc 2>/dev/null || true)
    _ref_g=$(stat -c %g /etc 2>/dev/null || true)
    [ -n "$_ref_u" ] || return 0
    for p in /lib/apk /var/cache/apk /var/log /etc/apk; do
        [ -e "$p" ] || continue
        chown -R "$_ref_u:$_ref_g" "$p" 2>/dev/null || true
    done
    mkdir -p /lib/apk/db /var/cache/apk /var/log
    rm -f /lib/apk/db/lock 2>/dev/null || true
    : > /lib/apk/db/lock 2>/dev/null || true
    chmod 666 /lib/apk/db/lock 2>/dev/null || true
    touch /var/log/apk.log 2>/dev/null || true
    chmod 666 /var/log/apk.log 2>/dev/null || true
}
echo "FluxLinux: Ensuring apk database is writable (proot-safe)…"
_flux_fix_apk_writable

mkdir -p /etc/fluxlinux
printf '%s\n' virgl > /etc/fluxlinux/gpu_mode
echo "FluxLinux: gpu_mode=virgl (first-paint; hw-accel may upgrade)"

echo "FluxLinux: ${DISTRO_NAME} Alpine setup complete!"
exit 0
