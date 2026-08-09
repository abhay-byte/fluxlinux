#!/data/data/com.ivarna.fluxlinux/files/usr/bin/bash
# start_gui_chroot.sh — Launch XFCE4 in Debian chroot (app-uid host + root guest)
# Host stack mirrors start_gui.sh (Pulse/VirGL/embedded X11). Guest via start_debian13_gui.sh.
# NEVER am force-stop own package.

PKG="${TERMUX_APP__PACKAGE_NAME:-com.ivarna.fluxlinux}"
_HOST_ENV="${TERMUX__PREFIX:-/data/data/${PKG}/files/usr}/etc/fluxlinux-host.env"
[ -r "$_HOST_ENV" ] && . "$_HOST_ENV"

PKG="${TERMUX_APP__PACKAGE_NAME:-$PKG}"
TERMUX_PREFIX="${TERMUX__PREFIX:-/data/data/$PKG/files/usr}"
TERMUX_HOME="${TERMUX__HOME:-/data/data/$PKG/files/home}"
export HOME="$TERMUX_HOME"
export TMPDIR="${TMPDIR:-$TERMUX_PREFIX/tmp}"
export PATH="$TERMUX_PREFIX/bin:$TERMUX_PREFIX/bin/applets:/system/bin:/system/xbin:$PATH"
export LD_LIBRARY_PATH="$TERMUX_PREFIX/lib:$TERMUX_PREFIX/opt/virglrenderer-android/lib"
export TERMUX_APP__PACKAGE_NAME="$PKG"
export TERMUX_X11_OVERRIDE_PACKAGE="$PKG"
export TERMUX__PREFIX="$TERMUX_PREFIX"
export TERMUX__HOME="$TERMUX_HOME"
export XKB_CONFIG_ROOT="$TERMUX_PREFIX/share/X11/xkb"

CHROOT_PATH="${CHROOT_PATH:-/data/local/tmp/chrootDebian13}"
ROOT_GUI_SCRIPT="$TERMUX_HOME/start_debian13_gui.sh"
ROOT_GUI_TMP="/data/local/tmp/start_debian13_gui.sh"
# Optional SSOT helper used by start_debian13_gui.sh for mounts
HELPER_SRC="$TERMUX_HOME/fluxlinux_chroot.sh"
HELPER_TMP="/data/local/tmp/fluxlinux_chroot.sh"

echo "========================================"
echo "FluxLinux: START XFCE (chroot mode)"
echo "========================================"

# Preflight chroot
if [ ! -d "$CHROOT_PATH" ]; then
  echo "FluxLinux: ERROR — chroot not found at $CHROOT_PATH"
  echo "Install Debian Rooted from onboarding or Distros."
  exit 1
fi
if [ ! -f "$CHROOT_PATH/.flux_configured" ] && [ ! -f "$CHROOT_PATH/usr/bin/startxfce4" ]; then
  echo "FluxLinux: ERROR — chroot incomplete (no .flux_configured / startxfce4)."
  echo "Re-run base desktop install for Debian Rooted."
  exit 1
fi
if [ ! -f "$ROOT_GUI_SCRIPT" ]; then
  echo "FluxLinux: ERROR — missing $ROOT_GUI_SCRIPT (HostScriptDeployer failed?)"
  exit 1
fi
# Stage SSOT helper for root guest script (if present)
if [ -f "$HELPER_SRC" ]; then
  cp -f "$HELPER_SRC" "$HELPER_TMP" 2>/dev/null || true
  chmod 755 "$HELPER_TMP" 2>/dev/null || true
fi

export PULSE_RUNTIME_PATH="${HOME}/.pulse"
mkdir -p "$PULSE_RUNTIME_PATH" "$TMPDIR" 2>/dev/null

# Stale host services only — do NOT force-stop $PKG
pkill -f "virgl_test_server" 2>/dev/null || true
pkill -f pulseaudio 2>/dev/null || true
pkill -f termux-x11 2>/dev/null || true
pkill -f "app_process.*termux-x11" 2>/dev/null || true
sleep 1

# PulseAudio (app uid — flux requirement)
echo "FluxLinux: Starting PulseAudio..."
pulseaudio --start --dl-search-path="$TERMUX_PREFIX/lib/pulseaudio/modules" \
  --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" \
  --exit-idle-time=-1 2>/dev/null || true
pacmd load-module module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1 >/dev/null 2>&1 || true

# VirGL optional
if command -v virgl_test_server_android >/dev/null; then
  echo "FluxLinux: Starting VirGL server..."
  virgl_test_server_android --socket-path "$TERMUX_PREFIX/tmp/.virgl_test" >/dev/null 2>&1 &
  sleep 2
  test -S "${TMPDIR}/.virgl_test" && echo "FluxLinux: VirGL socket ready" \
    || echo "FluxLinux: [WARN] VirGL socket not found"
else
  echo "FluxLinux: VirGL unavailable; software GL in guest"
fi

# Embedded termux-x11 (same as proot start_gui.sh)
echo "FluxLinux: Starting termux-x11 server..."
export XDG_RUNTIME_DIR="$TMPDIR"
export DISPLAY=:0

chmod 0400 "$TERMUX_PREFIX/libexec/termux-x11/loader.apk" 2>/dev/null || true
chmod 0500 "$TERMUX_PREFIX/libexec/termux-x11" 2>/dev/null || true

if [ -L "$TERMUX_PREFIX/share/X11/xkb" ] && [ ! -e "$TERMUX_PREFIX/share/X11/xkb" ]; then
  rm -f "$TERMUX_PREFIX/share/X11/xkb"
  ln -s "$TERMUX_PREFIX/share/xkeyboard-config-2" "$TERMUX_PREFIX/share/X11/xkb"
fi

if [ -z "$TERMUX_X11_APK_PATH" ]; then
  TERMUX_X11_APK_PATH=$(pm path "$PKG" 2>/dev/null | tr -d '\r' | sed 's/^package://')
fi
if [ -z "$TERMUX_X11_APK_PATH" ] || [ ! -f "$TERMUX_X11_APK_PATH" ]; then
  TERMUX_X11_APK_PATH=$(find /data/app -name "base.apk" -path "*$PKG*" 2>/dev/null | head -1)
fi
export TERMUX_X11_APK_PATH
echo "FluxLinux: APK path = $TERMUX_X11_APK_PATH"

APP_LIB_DIR="/data/data/$PKG/lib"
mkdir -p "$APP_LIB_DIR" 2>/dev/null
if [ ! -f "$APP_LIB_DIR/libXlorie.so" ] && [ -n "$TERMUX_X11_APK_PATH" ]; then
  echo "FluxLinux: Extracting libXlorie.so..."
  ( cd "$APP_LIB_DIR" && \
    unzip -o "$TERMUX_X11_APK_PATH" 'lib/arm64-v8a/libXlorie.so' 2>/dev/null && \
    mv -f lib/arm64-v8a/libXlorie.so . && rm -rf lib ) || \
  ( cd "$APP_LIB_DIR" && \
    unzip -o "$TERMUX_X11_APK_PATH" 'lib/armeabi-v7a/libXlorie.so' 2>/dev/null && \
    mv -f lib/armeabi-v7a/libXlorie.so . && rm -rf lib )
fi

mkdir -p "$TMPDIR/.X11-unix"
chmod 1777 "$TMPDIR/.X11-unix" 2>/dev/null || true
rm -f "$TMPDIR/.X0-lock" 2>/dev/null || true

LD_LIBRARY_PATH="" LD_PRELOAD="" \
CLASSPATH="$TERMUX_PREFIX/libexec/termux-x11/loader.apk" \
TERMUX_X11_APK_PATH="$TERMUX_X11_APK_PATH" \
TERMUX_X11_OVERRIDE_PACKAGE="$PKG" \
LANG=en_US.UTF-8 \
/system/bin/app_process / \
  --nice-name="termux-x11" com.termux.x11.Loader :0 -legacy-drawing &
XSERVER_PID=$!
echo "FluxLinux: X server PID=$XSERVER_PID"
sleep 3

echo "FluxLinux: Opening X11 activity..."
am start -n "$PKG/com.termux.x11.MainActivity" \
  --activity-single-top \
  --activity-clear-top 2>/dev/null || \
am start -n "$PKG/com.termux.x11.MainActivity" 2>/dev/null
sleep 1

# Root stage: copy to /data/local/tmp (SELinux / exec-safe) then su
echo "FluxLinux: Entering chroot as root..."
cp -f "$ROOT_GUI_SCRIPT" "$ROOT_GUI_TMP" 2>/dev/null || true
if [ -f "$HELPER_SRC" ]; then
  cp -f "$HELPER_SRC" "$HELPER_TMP" 2>/dev/null || true
  chmod 755 "$HELPER_TMP" 2>/dev/null || true
fi
SU_BIN=""
for s in /system/bin/su /system/xbin/su /sbin/su; do
  if [ -x "$s" ]; then SU_BIN="$s"; break; fi
done
if [ -z "$SU_BIN" ]; then
  # still try su from PATH (KernelSU may hide path)
  SU_BIN=su
fi

# Pass HELPER so start_debian13_gui prefers fluxlinux_chroot SSOT mounts
if [ -f "$ROOT_GUI_TMP" ] || cp -f "$ROOT_GUI_SCRIPT" "$ROOT_GUI_TMP" 2>/dev/null; then
  "$SU_BIN" -c "cp -f '$ROOT_GUI_SCRIPT' '$ROOT_GUI_TMP' 2>/dev/null; \
    [ -f '$HELPER_SRC' ] && cp -f '$HELPER_SRC' '$HELPER_TMP' 2>/dev/null; \
    chmod 755 '$ROOT_GUI_TMP' '$HELPER_TMP' 2>/dev/null; \
    DEBIANPATH='$CHROOT_PATH' TARGET_PREFIX='$TERMUX_PREFIX' HELPER='$HELPER_TMP' sh '$ROOT_GUI_TMP'"
else
  "$SU_BIN" -c "cp -f '$ROOT_GUI_SCRIPT' '$ROOT_GUI_TMP' && chmod 755 '$ROOT_GUI_TMP' && \
    DEBIANPATH='$CHROOT_PATH' TARGET_PREFIX='$TERMUX_PREFIX' HELPER='$HELPER_TMP' sh '$ROOT_GUI_TMP'"
fi
rc=$?
echo "FluxLinux: chroot GUI exit=$rc"
exit $rc
