#!/data/data/com.ivarna.fluxlinux/files/usr/bin/bash
# stop_gui.sh - Stop XFCE4 Desktop Environment in PRoot Distro
# Paths: TermuxHostPaths via fluxlinux-host.env (SSOT)

DISTRO=${1:-debian}
PKG="${TERMUX_APP__PACKAGE_NAME:-com.ivarna.fluxlinux}"
_HOST_ENV="${TERMUX__PREFIX:-/data/data/${PKG}/files/usr}/etc/fluxlinux-host.env"
[ -r "$_HOST_ENV" ] && . "$_HOST_ENV"

export TERMUX_APP__PACKAGE_NAME="${TERMUX_APP__PACKAGE_NAME:-$PKG}"
export TERMUX__PREFIX="${TERMUX__PREFIX:-/data/data/${TERMUX_APP__PACKAGE_NAME}/files/usr}"
export TERMUX__HOME="${TERMUX__HOME:-/data/data/${TERMUX_APP__PACKAGE_NAME}/files/home}"
export HOME="${HOME:-$TERMUX__HOME}"
export PROOT_TMP_DIR="${PROOT_TMP_DIR:-$(dirname "${TERMUX__PREFIX:-/data/data/com.ivarna.fluxlinux/files/usr}")/proot-tmp}"
mkdir -p "$PROOT_TMP_DIR" 2>/dev/null || true
PKG="$TERMUX_APP__PACKAGE_NAME"

echo "========================================"
echo "FluxLinux: Stopping GUI for $DISTRO"
echo "========================================"

# Step 1: Kill XFCE inside the guest (targeted login — does NOT pkill proot).
# Host-only pkill often misses proot-namespaced guest DE processes.
echo "[1/4] Stopping XFCE4 in guest ($DISTRO)..."
if command -v python >/dev/null 2>&1 && [ -x "$TERMUX__PREFIX/bin/proot-distro" ] || [ -f "$TERMUX__PREFIX/bin/proot-distro" ]; then
  python "$TERMUX__PREFIX/bin/proot-distro" login "$DISTRO" -- \
    /bin/bash -c 'killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel startxfce4 2>/dev/null; true' \
    >/dev/null 2>&1 || true
fi
# Best-effort host-side names too (some proot trees expose them)
pkill -9 -f "xfce4-session" 2>/dev/null || true
pkill -9 -f "xfwm4" 2>/dev/null || true
pkill -9 -f "xfdesktop" 2>/dev/null || true
pkill -9 -f "xfce4-panel" 2>/dev/null || true
pkill -9 -f "startxfce4" 2>/dev/null || true

# Step 2: Leave proot-distro / generic proot shells alone
echo "[2/4] Skipping proot shell kill (terminal sessions stay alive)"

# Step 3: Stop Termux X11 server
echo "[3/4] Stopping Termux X11..."
# Restore write permissions to allow clean updates/deployments
chmod 0700 "$TERMUX__PREFIX/libexec/termux-x11" 2>/dev/null || true
chmod 0600 "$TERMUX__PREFIX/libexec/termux-x11/loader.apk" 2>/dev/null || true
# Send ACTION_STOP broadcast to close the X11 activity in our app
am broadcast -a com.termux.x11.ACTION_STOP -p "$PKG" >/dev/null 2>&1
# Kill the app_process X server
pkill -9 -f "termux-x11" 2>/dev/null || true
pkill -9 -f "app_process.*termux-x11" 2>/dev/null || true
killall -9 Xwayland 2>/dev/null || true

# Step 4: Stop PulseAudio + VirGL
echo "[4/4] Stopping PulseAudio + VirGL..."
pulseaudio --kill 2>/dev/null || true
pkill -f "virgl_test_server" 2>/dev/null || true

echo ""
echo "✅ GUI stopped successfully!"
echo "========================================"
exit 0
