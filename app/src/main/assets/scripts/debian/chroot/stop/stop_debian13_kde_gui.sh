#!/bin/sh
# stop_debian13_kde_gui.sh - Stop KDE Plasma in Debian 13 Chroot
# Run this from Android Root Shell

echo "========================================"
echo "FluxLinux Pro: Stopping Debian 13 KDE GUI"
echo "========================================"

DEBIANPATH="/data/local/tmp/chrootDebian13"
TARGET_TERMUX_PREFIX="/data/data/com.termux/files/usr"

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
        if [ -x "$path" ]; then
            BB="$path"
            break
        fi
    done
fi

if [ -z "$BB" ]; then
    echo "Error: Root-capable Busybox not found!"
    exit 1
fi

# Step 1: Kill KDE processes inside chroot
echo "[1/4] Stopping KDE Plasma processes in chroot..."
$BB chroot "$DEBIANPATH" /bin/su - root -c "killall -9 plasmashell kwin_x11 kded5 kded6 plasma_session startplasma-x11 dbus-launch dbus-daemon" >/dev/null 2>&1

# Step 2: Stop Termux X11
echo "[2/4] Stopping Termux X11..."
killall -9 termux-x11 Xwayland >/dev/null 2>&1
pkill -f com.termux.x11 >/dev/null 2>&1

# Clean up X11 sockets
rm -rf "$TARGET_TERMUX_PREFIX/tmp/.X11-unix"
rm -rf "$TARGET_TERMUX_PREFIX/tmp/.X0-lock"
rm -rf "$TARGET_TERMUX_PREFIX/tmp/.X1-lock"

# Step 3: Unmount filesystems
echo "[3/4] Unmounting filesystems..."
$BB umount "$DEBIANPATH/sdcard" 2>/dev/null
$BB umount "$DEBIANPATH/dev/shm" 2>/dev/null
$BB umount "$DEBIANPATH/dev/pts" 2>/dev/null
$BB umount "$DEBIANPATH/proc" 2>/dev/null
$BB umount "$DEBIANPATH/sys" 2>/dev/null
$BB umount "$DEBIANPATH/dev" 2>/dev/null
$BB umount "$DEBIANPATH/tmp" 2>/dev/null

# Step 4: Done
echo "[4/4] Cleanup complete"

echo ""
echo "✅ KDE Plasma stopped successfully!"
echo "========================================"
exit 0