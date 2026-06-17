#!/bin/sh
# start_debian13_kde_gui.sh - Launch KDE Plasma in Debian 13 Chroot (VirGL)
# Run this from Android Root Shell

echo "========================================"
echo "FluxLinux Pro: Starting Debian 13 KDE GUI"
echo "  Renderer: VirGL (virpipe)"
echo "========================================"

DEBIANPATH="/data/local/tmp/chrootDebian13"
TARGET_TERMUX_PREFIX="/data/data/com.termux/files/usr"
USERNAME="flux"
FLUX_UID=1000

# Detect Busybox
BB=""
if command -v busybox >/dev/null 2>&1; then
    DETECTED_BB=$(command -v busybox)
    case "$DETECTED_BB" in
        *"com.termux"*) ;;
        *) BB="$DETECTED_BB" ;;
    esac
fi
if [ -z "$BB" ]; then
    for path in /data/adb/magisk/busybox /data/adb/modules/busybox-ndk/system/bin/busybox /sbin/busybox /system/xbin/busybox /system/bin/busybox; do
        if [ -x "$path" ]; then BB="$path"; break; fi
    done
fi
if [ -z "$BB" ]; then echo "Error: Root-capable Busybox not found!"; exit 1; fi

# 1. Fix setuid
$BB mount -o remount,dev,suid /data >/dev/null 2>&1

# 2. Mount filesystems
echo "[1/7] Mounting filesystems..."
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
# /tmp is Termux-owned (uid 10266) — KDE/dbus REJECT it for uid 1000
echo "[2/7] Setting up XDG runtime dir for flux user..."
mkdir -p "$DEBIANPATH/run/user/$FLUX_UID"
chown ${FLUX_UID}:${FLUX_UID} "$DEBIANPATH/run/user/$FLUX_UID" 2>/dev/null || \
    $BB chroot "$DEBIANPATH" /bin/sh -c "chown ${FLUX_UID}:${FLUX_UID} /run/user/${FLUX_UID}"
chmod 700 "$DEBIANPATH/run/user/$FLUX_UID"

# VirGL socket lives in /tmp (Termux tmp bind-mount) — create symlink so virpipe finds it
# virpipe looks for socket at VIRGL_VTEST_SOCKET_NAME or defaults to /tmp/.virgl_test
# /tmp is correct since it IS the Termux tmp directory bind-mounted
if [ -S "$TARGET_TERMUX_PREFIX/tmp/.virgl_test" ]; then
    echo "[OK] VirGL socket found at /tmp/.virgl_test (inside chroot)"
else
    echo "[WARN] VirGL socket not found yet — server may still be starting"
fi
echo "[OK] XDG_RUNTIME_DIR = /run/user/$FLUX_UID"

# 4. Force kwinrc to disable compositing EVERY LAUNCH
# This is belt-and-suspenders: env var KWIN_COMPOSE=N + kwinrc file
echo "[3/7] Pre-configuring KWin (disabling compositing)..."
KDE_CONFIG_DIR="$DEBIANPATH/home/$USERNAME/.config"
KWINRC="$KDE_CONFIG_DIR/kwinrc"
mkdir -p "$KDE_CONFIG_DIR"

# Strip old [Compositing] block and write a fresh one at top
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

# 5. Kill old X11 / KDE processes
echo "[4/7] Cleaning up old processes..."
killall -9 termux-x11 Xwayland >/dev/null 2>&1
pkill -f com.termux.x11 >/dev/null 2>&1
$BB chroot "$DEBIANPATH" /bin/su - root -c \
    "killall -9 plasmashell kwin_x11 kded5 kded6 plasma_session startplasma-x11 dbus-launch dbus-daemon kwin_wayland xdg-desktop-portal" \
    >/dev/null 2>&1
sleep 1

# 6. Clean and create X11 sockets
echo "[5/7] Setting up X11..."
rm -rf "$TARGET_TERMUX_PREFIX/tmp/.X11-unix"
rm -rf "$TARGET_TERMUX_PREFIX/tmp/.X0-lock"
rm -rf "$TARGET_TERMUX_PREFIX/tmp/.X1-lock"
mkdir -p "$TARGET_TERMUX_PREFIX/tmp/.X11-unix"
chmod 1777 "$TARGET_TERMUX_PREFIX/tmp/.X11-unix"

# 7. Start Termux X11 app and server
echo "[6/7] Starting Termux X11..."
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

# 8. Launch KDE Plasma inside chroot with VirGL
echo "[7/7] Launching KDE Plasma (VirGL)..."

$BB chroot "$DEBIANPATH" /bin/su - "$USERNAME" -c '
    export DISPLAY=:0
    export PULSE_SERVER=tcp:127.0.0.1

    # Proper per-user runtime dir (NOT /tmp — owned by Termux uid 10266)
    export XDG_RUNTIME_DIR=/run/user/1000

    # VirGL: virpipe driver; socket is at /tmp/.virgl_test (Termux tmp bind-mount)
    export GALLIUM_DRIVER=virpipe
    export VIRGL_VTEST_SOCKET_NAME=/tmp/.virgl_test
    export MESA_GL_VERSION_OVERRIDE=3.3
    export MESA_GLSL_CACHE_DIR=/run/user/1000

    # KWin compositing: N = none (no compositing, no OpenGL)
    export KWIN_COMPOSE=N
    # egl interface still needed for Qt rendering even without compositing
    export KWIN_OPENGL_INTERFACE=egl

    # Qt / KDE platform
    export QT_QPA_PLATFORMTHEME=kde
    export QT_SCALE_FACTOR=1

    # Disable xdg-desktop-portal GL rendering (prevents signal 6 crashes)
    export GDK_BACKEND=x11
    export QT_X11_NO_MITSHM=1

    # Start KDE Plasma (dbus-run-session creates the session bus)
    dbus-run-session -- startplasma-x11 &
    PLASMA_PID=$!

    # After plasma starts, force kwin to restart without compositing
    # This is the same trick as KDE customization script — ensures it sticks
    sleep 8
    DISPLAY=:0 kwin_x11 --replace >/dev/null 2>&1 &

    wait $PLASMA_PID
'

echo ""
echo "KDE Plasma session ended."
echo "========================================"
exit 0