#!/bin/sh
# start_debian13_kde_gui_software.sh - Launch KDE Plasma in Debian 13 Chroot (LLVMpipe)
# Software rendering — no GPU required, works on ALL devices
# Run this from Android Root Shell

echo "========================================"
echo "FluxLinux: Starting Debian 13 KDE GUI"
echo "  Renderer: Software (LLVMpipe)"
echo "========================================"

DEBIANPATH="/data/local/tmp/chrootDebian13"
TARGET_TERMUX_PREFIX="/data/data/com.termux/files/usr"
USERNAME="flux"
FLUX_UID=1000

# resolve root BusyBox (manager built-in; NDK module not required)
_rr=""
for _c in \
  "${FLUX_RESOLVE_BB:-}" \
  "$(dirname "$0")/resolve_bb.sh" \
  /data/local/tmp/fluxlinux_resolve_bb.sh
do
  [ -n "$_c" ] && [ -f "$_c" ] && _rr="$_c" && break
done
if [ -n "$_rr" ]; then
  # shellcheck disable=SC1090
  . "$_rr"
  resolve_bb || true
fi
if [ -z "${BB:-}" ]; then
  # sidecar missing (desktop/uninstall/staged setup) — same B1 walk as resolve_bb
  if [ -n "${FLUX_BB:-}" ] && [ -x "$FLUX_BB" ] &&
     "$FLUX_BB" --list >/dev/null 2>&1; then BB="$FLUX_BB"; fi
  if [ -z "${BB:-}" ] && [ -x /data/local/tmp/flux_busybox ] &&
     /data/local/tmp/flux_busybox --list >/dev/null 2>&1; then
    BB=/data/local/tmp/flux_busybox
  fi
  if [ -z "${BB:-}" ]; then
    for path in \
      /data/adb/ksu/bin/busybox \
      /data/adb/ap/bin/busybox \
      /data/adb/magisk/busybox \
      /data/adb/modules/busybox-ndk/system/xbin/busybox \
      /data/adb/modules/busybox-ndk/system/bin/busybox \
      /debug_ramdisk/busybox \
      /sbin/busybox \
      /system/xbin/busybox \
      /system/bin/busybox
    do
      if [ -x "$path" ]; then BB="$path"; break; fi
    done
  fi
fi
if [ -z "${BB:-}" ]; then
  echo "FluxLinux: ERROR — root-capable busybox not found" >&2
  exit 1
fi


# 1. Fix setuid
$BB mount -o remount,dev,suid /data >/dev/null 2>&1

# 2. Mount filesystems
echo "[1/6] Mounting filesystems..."
$BB mount --bind /dev "$DEBIANPATH/dev" >/dev/null 2>&1
$BB mount --bind /sys "$DEBIANPATH/sys" >/dev/null 2>&1
$BB mount -t proc proc "$DEBIANPATH/proc" >/dev/null 2>&1
$BB mount -t devpts devpts "$DEBIANPATH/dev/pts" >/dev/null 2>&1
mkdir -p "$DEBIANPATH/dev/shm"
$BB mount -t tmpfs -o size=512M tmpfs "$DEBIANPATH/dev/shm" >/dev/null 2>&1
mkdir -p "$DEBIANPATH/tmp"
$BB mount --bind "$TARGET_TERMUX_PREFIX/tmp" "$DEBIANPATH/tmp" >/dev/null 2>&1
mkdir -p "$DEBIANPATH/sdcard"
$BB mount --bind /sdcard "$DEBIANPATH/sdcard" >/dev/null 2>&1

# 3. Set up XDG_RUNTIME_DIR owned by flux user (uid 1000)
echo "[2/6] Setting up XDG runtime dir for flux user..."
mkdir -p "$DEBIANPATH/run/user/$FLUX_UID"
chown ${FLUX_UID}:${FLUX_UID} "$DEBIANPATH/run/user/$FLUX_UID" 2>/dev/null || \
    $BB chroot "$DEBIANPATH" /bin/sh -c "chown ${FLUX_UID}:${FLUX_UID} /run/user/${FLUX_UID}"
chmod 700 "$DEBIANPATH/run/user/$FLUX_UID"
echo "[OK] XDG_RUNTIME_DIR = /run/user/$FLUX_UID"

# 4. Force kwinrc: disable compositing
echo "[3/6] Pre-configuring KWin (disabling compositing)..."
KDE_CONFIG_DIR="$DEBIANPATH/home/$USERNAME/.config"
KWINRC="$KDE_CONFIG_DIR/kwinrc"
mkdir -p "$KDE_CONFIG_DIR"

KWINRC_REST=""
if [ -f "$KWINRC" ]; then
    KWINRC_REST=$(awk '
        /^\[Compositing\]/ { skip=1; next }
        /^\[/ { skip=0 }
        !skip { print }
    ' "$KWINRC")
fi

cat > "$KWINRC" <<KWINEOF
[Compositing]
Enabled=false
Backend=QPainter
OpenGLIsUnsafe=true

KWINEOF
[ -n "$KWINRC_REST" ] && printf '%s\n' "$KWINRC_REST" >> "$KWINRC"
chown ${FLUX_UID}:${FLUX_UID} "$KWINRC" 2>/dev/null || \
    $BB chroot "$DEBIANPATH" /bin/sh -c "chown ${FLUX_UID}:${FLUX_UID} /home/$USERNAME/.config/kwinrc"
echo "[OK] kwinrc: Compositing disabled"

# 5. Kill old processes
echo "[4/6] Cleaning up old processes..."
killall -9 termux-x11 Xwayland >/dev/null 2>&1
pkill -f com.termux.x11 >/dev/null 2>&1
$BB chroot "$DEBIANPATH" /bin/su - root -c \
    "killall -9 plasmashell kwin_x11 kded5 kded6 plasma_session startplasma-x11 dbus-launch dbus-daemon xdg-desktop-portal" \
    >/dev/null 2>&1
sleep 1

# 6. X11 setup
echo "[5/6] Setting up X11..."
rm -rf "$TARGET_TERMUX_PREFIX/tmp/.X11-unix"
rm -rf "$TARGET_TERMUX_PREFIX/tmp/.X0-lock"
rm -rf "$TARGET_TERMUX_PREFIX/tmp/.X1-lock"
mkdir -p "$TARGET_TERMUX_PREFIX/tmp/.X11-unix"
chmod 1777 "$TARGET_TERMUX_PREFIX/tmp/.X11-unix"

am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity >/dev/null 2>&1
sleep 2

export XDG_RUNTIME_DIR="$TARGET_TERMUX_PREFIX/tmp"
export TMPDIR="$XDG_RUNTIME_DIR"
export LD_LIBRARY_PATH="$TARGET_TERMUX_PREFIX/lib"

$TARGET_TERMUX_PREFIX/bin/termux-x11 :0 -ac >/dev/null 2>&1 &
sleep 3

if [ -S "$TARGET_TERMUX_PREFIX/tmp/.X11-unix/X0" ]; then
    echo "[OK] X11 socket ready"
else
    echo "[WARN] X11 socket may not be ready, continuing..."
fi

# 7. Launch with pure software rendering
echo "[6/6] Launching KDE Plasma (LLVMpipe software rendering)..."

$BB chroot "$DEBIANPATH" /bin/su - "$USERNAME" -c '
    export DISPLAY=:0
    export PULSE_SERVER=tcp:127.0.0.1
    export XDG_RUNTIME_DIR=/run/user/1000

    # Software rendering: bypass all GPU drivers — pure CPU LLVMpipe
    export LIBGL_ALWAYS_SOFTWARE=1
    export GALLIUM_DRIVER=llvmpipe
    export LP_NO_RAST=false
    export MESA_GL_VERSION_OVERRIDE=3.3
    export MESA_GLSL_CACHE_DIR=/run/user/1000

    # KWin: no compositing (software rendering + compositing = very slow)
    export KWIN_COMPOSE=N
    export KWIN_OPENGL_INTERFACE=egl

    # Qt / KDE
    export QT_QPA_PLATFORMTHEME=kde
    export QT_SCALE_FACTOR=1
    export GDK_BACKEND=x11
    export QT_X11_NO_MITSHM=1

    dbus-run-session -- startplasma-x11 &
    PLASMA_PID=$!

    # Force kwin restart after boot to ensure compositing stays off
    sleep 8
    DISPLAY=:0 kwin_x11 --replace >/dev/null 2>&1 &

    wait $PLASMA_PID
'

echo ""
echo "KDE Plasma (Software) session ended."
echo "========================================"
exit 0
