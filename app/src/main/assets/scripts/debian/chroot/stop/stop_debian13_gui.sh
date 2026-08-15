#!/bin/sh
# stop_debian13_gui.sh - Stop Debian 13 Chroot GUI
# Run this from Android Root Shell

echo "========================================"
echo "FluxLinux: Stopping Debian 13 Chroot GUI"
echo "========================================"

DEBIANPATH="/data/local/tmp/chrootDebian13"
TARGET_TERMUX_PREFIX="/data/data/com.termux/files/usr"

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


# Step 1: Kill XFCE processes inside chroot
echo "[1/4] Stopping XFCE4 processes in chroot..."
$BB chroot $DEBIANPATH /bin/su - root -c "killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon" >/dev/null 2>&1

# Step 2: Stop Termux X11
echo "[2/4] Stopping Termux X11..."
killall -9 termux-x11 Xwayland >/dev/null 2>&1
pkill -f com.termux.x11 >/dev/null 2>&1

# Clean up X11 sockets
rm -rf $TARGET_TERMUX_PREFIX/tmp/.X11-unix
rm -rf $TARGET_TERMUX_PREFIX/tmp/.X0-lock

# Step 3: Unmount filesystems
echo "[3/4] Unmounting filesystems..."
$BB umount "$DEBIANPATH/sdcard" 2>/dev/null
$BB umount "$DEBIANPATH/dev/shm" 2>/dev/null
$BB umount "$DEBIANPATH/dev/pts" 2>/dev/null
$BB umount "$DEBIANPATH/proc" 2>/dev/null
$BB umount "$DEBIANPATH/sys" 2>/dev/null
$BB umount "$DEBIANPATH/dev" 2>/dev/null
$BB umount "$DEBIANPATH/tmp" 2>/dev/null

# Step 4: Stop PulseAudio (optional)
echo "[4/4] Stopping PulseAudio..."
# PulseAudio is started in Termux context, so we don't kill it from root

echo ""
echo "✅ Chroot GUI stopped successfully!"
echo "========================================"
exit 0
