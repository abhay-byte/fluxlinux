#!/bin/sh
# start_debian13_kde_gui_turnip.sh - Launch KDE Plasma in Debian 13 Chroot (Turnip/Zink)
# Run this from Android Root Shell

echo "========================================"
echo "FluxLinux: Starting Debian 13 KDE GUI"
echo "  Renderer: Turnip / Zink (Vulkan)"
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
echo "[1/8] Mounting filesystems..."
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

# 3. Mount GPU nodes (Turnip needs access to Adreno DRI devices)
echo "[2/8] Mounting GPU device nodes for Turnip..."
if [ -d /dev/dri ]; then
    mkdir -p "$DEBIANPATH/dev/dri"
    $BB mount --bind /dev/dri "$DEBIANPATH/dev/dri" >/dev/null 2>&1
    echo "[OK] /dev/dri mounted"
else
    echo "[WARN] /dev/dri not found — Turnip may fall back to software rendering"
fi

for kgsl in /dev/kgsl-3d0 /dev/kgsl-2d0; do
    if [ -e "$kgsl" ]; then
        devname=$(basename "$kgsl")
        $BB mknod "$DEBIANPATH/dev/$devname" c $(stat -c "%t %T" "$kgsl") >/dev/null 2>&1 || true
        chmod 666 "$DEBIANPATH/dev/$devname" >/dev/null 2>&1 || true
        echo "[OK] $kgsl node created"
    fi
done

# 4. Set up XDG_RUNTIME_DIR owned by flux user (uid 1000)
# /tmp is bind-mounted from Termux (owned by uid 10266) — dbus/KDE REJECTS it for uid 1000
echo "[3/8] Setting up XDG runtime dir for flux user..."
mkdir -p "$DEBIANPATH/run/user/$FLUX_UID"
chown ${FLUX_UID}:${FLUX_UID} "$DEBIANPATH/run/user/$FLUX_UID" 2>/dev/null || \
    $BB chroot "$DEBIANPATH" /bin/sh -c "chown ${FLUX_UID}:${FLUX_UID} /run/user/${FLUX_UID}"
chmod 700 "$DEBIANPATH/run/user/$FLUX_UID"
echo "[OK] XDG_RUNTIME_DIR = /run/user/$FLUX_UID (owned by uid $FLUX_UID)"

# 5. Force-disable KWin compositing BEFORE plasma starts
echo "[4/8] Pre-configuring KWin (disabling compositing)..."
KDE_CONFIG_DIR="$DEBIANPATH/home/$USERNAME/.config"
KWINRC="$KDE_CONFIG_DIR/kwinrc"
mkdir -p "$KDE_CONFIG_DIR"

if [ -f "$KWINRC" ]; then
    awk '
        /^\[Compositing\]/ { skip=1; next }
        /^\[/ { skip=0 }
        !skip { print }
    ' "$KWINRC" > "${KWINRC}.stripped"
    mv "${KWINRC}.stripped" "$KWINRC"
fi

TMP_KWINRC="${DEBIANPATH}/tmp/kwinrc_patch_$$"
printf '[Compositing]\nEnabled=false\nBackend=QPainter\nOpenGLIsUnsafe=true\n\n' > "$TMP_KWINRC"
[ -f "$KWINRC" ] && cat "$KWINRC" >> "$TMP_KWINRC"
cp "$TMP_KWINRC" "$KWINRC"
rm -f "$TMP_KWINRC"
chown ${FLUX_UID}:${FLUX_UID} "$KWINRC" 2>/dev/null || \
    $BB chroot "$DEBIANPATH" /bin/sh -c "chown ${FLUX_UID}:${FLUX_UID} /home/$USERNAME/.config/kwinrc"
echo "[OK] KWin compositing disabled in kwinrc"

# 6. Kill old X11 / KDE processes
echo "[5/8] Cleaning up old processes..."
killall -9 termux-x11 Xwayland >/dev/null 2>&1
pkill -f com.termux.x11 >/dev/null 2>&1
$BB chroot "$DEBIANPATH" /bin/su - root -c "killall -9 plasmashell kwin_x11 kded5 kded6 plasma_session startplasma-x11 dbus-launch dbus-daemon" >/dev/null 2>&1
sleep 1

# 7. Clean and create X11 sockets
echo "[6/8] Setting up X11..."
rm -rf "$TARGET_TERMUX_PREFIX/tmp/.X11-unix"
rm -rf "$TARGET_TERMUX_PREFIX/tmp/.X0-lock"
rm -rf "$TARGET_TERMUX_PREFIX/tmp/.X1-lock"
mkdir -p "$TARGET_TERMUX_PREFIX/tmp/.X11-unix"
chmod 1777 "$TARGET_TERMUX_PREFIX/tmp/.X11-unix"

# 8. Start Termux X11 app and server
echo "[7/8] Starting Termux X11..."
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

# 9. Launch KDE Plasma inside chroot with Turnip/Zink
echo "[8/8] Launching KDE Plasma (Turnip/Zink)..."

$BB chroot "$DEBIANPATH" /bin/su - "$USERNAME" -c '
    export DISPLAY=:0
    export PULSE_SERVER=tcp:127.0.0.1
    # Proper per-user runtime dir (NOT /tmp — owned by Termux uid 10266)
    export XDG_RUNTIME_DIR=/run/user/1000
    export GALLIUM_DRIVER=zink
    export MESA_LOADER_DRIVER_OVERRIDE=zink
    export TU_DEBUG=noconform
    export ZINK_NO_TIMELINES=1
    export MESA_GL_VERSION_OVERRIDE=4.3
    export MESA_GLSL_CACHE_DIR=/run/user/1000
    # Disable KWin compositing (belt-and-suspenders with kwinrc written above)
    export KWIN_COMPOSE=N
    export KWIN_OPENGL_INTERFACE=egl
    export QT_QPA_PLATFORMTHEME=kde
    export QT_SCALE_FACTOR=1
    dbus-run-session -- startplasma-x11
'

echo ""
echo "KDE Plasma (Turnip) session ended."
echo "========================================"
exit 0
