#!/data/data/com.ivarna.fluxlinux/files/usr/bin/bash
# stop_gui_chroot.sh — app-uid: stop Pulse + root stop_debian13_gui.sh
# Does NOT pkill proot.

PKG="${TERMUX_APP__PACKAGE_NAME:-com.ivarna.fluxlinux}"
_HOST_ENV="${TERMUX__PREFIX:-/data/data/${PKG}/files/usr}/etc/fluxlinux-host.env"
[ -r "$_HOST_ENV" ] && . "$_HOST_ENV"

PKG="${TERMUX_APP__PACKAGE_NAME:-$PKG}"
TERMUX_PREFIX="${TERMUX__PREFIX:-/data/data/$PKG/files/usr}"
TERMUX_HOME="${TERMUX__HOME:-/data/data/$PKG/files/home}"
export HOME="$TERMUX_HOME"
export PATH="$TERMUX_PREFIX/bin:$TERMUX_PREFIX/bin/applets:/system/bin:/system/xbin:$PATH"

ROOT_STOP_SCRIPT="$TERMUX_HOME/stop_debian13_gui.sh"
ROOT_STOP_TMP="/data/local/tmp/stop_debian13_gui.sh"

echo "========================================"
echo "FluxLinux: STOP XFCE (chroot mode)"
echo "========================================"

if [ ! -f "$ROOT_STOP_SCRIPT" ]; then
  echo "FluxLinux: [WARN] missing $ROOT_STOP_SCRIPT — best-effort kill only"
else
  SU_BIN=su
  for s in /system/bin/su /system/xbin/su /sbin/su; do
    if [ -x "$s" ]; then SU_BIN="$s"; break; fi
  done
  "$SU_BIN" -c "cp -f '$ROOT_STOP_SCRIPT' '$ROOT_STOP_TMP' 2>/dev/null; chmod 755 '$ROOT_STOP_TMP'; TARGET_PREFIX='$TERMUX_PREFIX' sh '$ROOT_STOP_TMP'"
fi

echo "Stopping PulseAudio + VirGL (app uid)..."
pkill -f "virgl_test_server" 2>/dev/null || true
pulseaudio --kill 2>/dev/null || true
pkill -f pulseaudio 2>/dev/null || true

# Also stop X11 from app context (broadcast handled by app; pkill backup)
pkill -9 -f "termux-x11" 2>/dev/null || true
pkill -9 -f "app_process.*termux-x11" 2>/dev/null || true

echo "Chroot GUI stop complete."
echo "========================================"
exit 0
