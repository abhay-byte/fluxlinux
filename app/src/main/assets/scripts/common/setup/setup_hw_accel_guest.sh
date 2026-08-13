#!/bin/sh
# setup_hw_accel_guest.sh — Mesa / VirGL / optional Fedora Turnip.
# Detects dnf, xbps, or zypper. Writes /etc/fluxlinux/gpu_mode.

if [ "$(id -u)" -ne 0 ]; then
    echo "FluxLinux: ERROR: must run as root (got uid=$(id -u))."
    exit 1
fi

export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin${PATH:+:$PATH}"
mkdir -p /tmp /var/tmp /etc/fluxlinux
chmod 1777 /tmp /var/tmp 2>/dev/null || true

echo "FluxLinux: Hardware acceleration (guest)..."

MODE="${FLUX_GPU:-virgl}"
case "$MODE" in
    turnip|virgl) ;;
    *) MODE=virgl ;;
esac

_pkg_add() {
    if command -v dnf5 >/dev/null 2>&1; then
        dnf5 -y --setopt=install_weak_deps=False --setopt=tsflags=nodocs,noscripts install "$@"
    elif command -v dnf >/dev/null 2>&1; then
        dnf -y --setopt=install_weak_deps=False --setopt=tsflags=nodocs,noscripts install "$@"
    elif command -v xbps-install >/dev/null 2>&1; then
        xbps-install -Sy >/dev/null 2>&1 || true
        xbps-install -y "$@"
    elif command -v zypper >/dev/null 2>&1; then
        zypper --non-interactive --gpg-auto-import-keys refresh >/dev/null 2>&1 || true
        zypper --non-interactive install --no-recommends --auto-agree-with-licenses "$@"
    else
        echo "FluxLinux: no supported package manager for hw accel"
        return 1
    fi
}

if command -v dnf >/dev/null 2>&1 || command -v dnf5 >/dev/null 2>&1; then
    _pkg_add mesa-dri-drivers mesa-libGL mesa-libEGL mesa-vulkan-drivers mesa-utils vulkan-tools \
        || _pkg_add mesa-dri-drivers mesa-libGL mesa-utils || true
elif command -v xbps-install >/dev/null 2>&1; then
    _pkg_add mesa mesa-dri MesaLib 2>/dev/null || _pkg_add mesa mesa-dri || true
elif command -v zypper >/dev/null 2>&1; then
    _pkg_add Mesa-dri Mesa-libGL1 Mesa-libEGL1 Mesa-demo-x 2>/dev/null \
        || _pkg_add Mesa-dri Mesa-libGL1 || true
fi

TURNIP_OK=0
if [ "$MODE" = "turnip" ] && [ -f /etc/fedora-release ]; then
    TURNIP_VERSION="26.2.0-devel-20260610"
    DISTRO="fedora_43"
    URL="https://github.com/lfdevs/mesa-for-android-container/releases/download/turnip-${TURNIP_VERSION}/turnip_${TURNIP_VERSION}_${DISTRO}_arm64.tar.gz"
    echo "FluxLinux: Trying Turnip $TURNIP_VERSION for $DISTRO..."
    if command -v curl >/dev/null 2>&1 \
        && curl -L --fail --connect-timeout 15 --max-time 180 -o /tmp/turnip.tar.gz "$URL"; then
        tar -zxf /tmp/turnip.tar.gz -C / && TURNIP_OK=1
        command -v ldconfig >/dev/null 2>&1 && ldconfig || true
        rm -f /tmp/turnip.tar.gz
        echo "FluxLinux: Turnip extracted"
    else
        echo "FluxLinux: Turnip download skipped/failed — staying on VirGL"
        rm -f /tmp/turnip.tar.gz
        MODE=virgl
    fi
fi

if [ "$TURNIP_OK" = "1" ]; then
    MODE=turnip
fi

printf '%s\n' "$MODE" > /etc/fluxlinux/gpu_mode
echo "FluxLinux: gpu_mode=$MODE"

mkdir -p /usr/local/bin
cat > /usr/local/bin/gpu-launch <<'EOF'
#!/bin/sh
MODE="virgl"
if [ -r /etc/fluxlinux/gpu_mode ]; then
    MODE=$(tr -d '[:space:]' </etc/fluxlinux/gpu_mode)
fi
case "$MODE" in
    turnip)
        export MESA_LOADER_DRIVER_OVERRIDE=zink
        export GALLIUM_DRIVER=zink
        export TU_DEBUG=noconform
        ;;
    virgl)
        export GALLIUM_DRIVER=virpipe
        export GALLIUM_DRIVER=virpipe
        ;;
    *)
        export LIBGL_ALWAYS_SOFTWARE=1
        export GALLIUM_DRIVER=llvmpipe
        ;;
esac
exec "$@"
EOF
chmod 755 /usr/local/bin/gpu-launch

echo "FluxLinux: Hardware acceleration setup complete."
exit 0
