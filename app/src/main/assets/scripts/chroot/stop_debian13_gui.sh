#!/system/bin/sh
# stop_debian13_gui.sh — root: stop XFCE in Debian chroot + host X11 sockets
# Does NOT pkill proot (proot stop is stop_gui.sh only).

DEBIANPATH="${DEBIANPATH:-/data/local/tmp/chrootDebian13}"
TARGET_PREFIX="${TARGET_PREFIX:-/data/data/com.ivarna.fluxlinux/files/usr}"

echo "========================================"
echo "FluxLinux: Stopping Chroot XFCE"
echo "========================================"

BB=""
if command -v busybox >/dev/null 2>&1; then
  DETECTED_BB=$(command -v busybox)
  case "$DETECTED_BB" in
    *"com.termux"*|*"fluxlinux"*|*"nativecode"*) ;;
    *) BB="$DETECTED_BB" ;;
  esac
fi
if [ -z "$BB" ]; then
  for path in /data/adb/magisk/busybox /data/adb/modules/busybox-ndk/system/bin/busybox \
    /sbin/busybox /system/xbin/busybox /system/bin/busybox /debug_ramdisk/busybox; do
    if [ -x "$path" ]; then BB="$path"; break; fi
  done
fi

echo "[1/4] Kill XFCE in chroot..."
if [ -n "$BB" ] && [ -d "$DEBIANPATH" ]; then
  $BB chroot "$DEBIANPATH" /bin/su - root -c \
    "killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon 2>/dev/null; true" \
    >/dev/null 2>&1
fi

echo "[2/4] Stop Termux X11 host processes..."
# Restore loader write bits for redeploy
chmod 0700 "$TARGET_PREFIX/libexec/termux-x11" 2>/dev/null || true
chmod 0600 "$TARGET_PREFIX/libexec/termux-x11/loader.apk" 2>/dev/null || true
pkill -9 -f "termux-x11" 2>/dev/null || true
pkill -9 -f "app_process.*termux-x11" 2>/dev/null || true
killall -9 Xwayland 2>/dev/null || true
rm -rf "$TARGET_PREFIX/tmp/.X11-unix" "$TARGET_PREFIX/tmp/.X0-lock" "$TARGET_PREFIX/tmp/.X1-lock" 2>/dev/null || true

echo "[3/4] Unmount chroot binds (best-effort)..."
if [ -n "$BB" ] && [ -d "$DEBIANPATH" ]; then
  for m in \
    "$DEBIANPATH/tmp/.X11-unix" \
    "$DEBIANPATH/mnt/host-tmp" \
    "$DEBIANPATH/sdcard" \
    "$DEBIANPATH/dev/shm" \
    "$DEBIANPATH/dev/pts" \
    "$DEBIANPATH/proc" \
    "$DEBIANPATH/sys" \
    "$DEBIANPATH/dev"
  do
    $BB umount "$m" 2>/dev/null || $BB umount -l "$m" 2>/dev/null || true
  done
fi

echo "[4/4] Done (PulseAudio stopped from app-uid wrapper if used)"
echo "========================================"
exit 0
