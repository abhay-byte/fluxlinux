#!/bin/sh
# uninstall_alpine_chroot.sh — remove Alpine chroot (Requires Root)

ALPINEPATH="${FLUX_CHROOT:-/data/local/tmp/chrootAlpine}"
LAUNCH_SCRIPTS="/data/local/tmp/start_alpine_gui.sh /data/local/tmp/stop_alpine_gui.sh /data/local/tmp/enter_alpine.sh /data/local/tmp/uninstall_alpine_chroot.sh"

error()    { printf "\033[1;31m[!] %s\033[0m\n" "$1"; }
success()  { printf "\033[1;32m[✓] %s\033[0m\n" "$1"; }
progress() { printf "\033[1;36m[+] %s\033[0m\n" "$1"; }

if [ "$(id -u)" != "0" ]; then
    error "This script must be run as root."
    exit 1
fi

progress "Starting Uninstallation of Alpine Chroot..."
progress "Target: $ALPINEPATH"

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


progress "Checking for stalled processes..."
HELPER="$(dirname "$0")/chroot_processes.sh"
if [ -f "$HELPER" ]; then
    sh "$HELPER" kill "$ALPINEPATH" || true
else
    for pid_dir in /proc/[0-9]*; do
        if [ -d "$pid_dir" ]; then
            PID=$(basename "$pid_dir")
            ROOT=$(readlink "$pid_dir/root" 2>/dev/null)
            if [ "$ROOT" = "$ALPINEPATH" ]; then
                progress "Killing stuck process $PID..."
                kill -9 "$PID" 2>/dev/null
            fi
        fi
    done
fi

progress "Unmounting filesystems under $ALPINEPATH..."
MOUNTS=$(grep "$ALPINEPATH" /proc/mounts 2>/dev/null | awk '{print $2}' | sort -r)
if [ -z "$MOUNTS" ]; then
    progress "No mounts found (Clean)."
else
    for mnt in $MOUNTS; do
        progress "Unmounting: $mnt"
        $BB umount -l "$mnt" 2>/dev/null || /system/bin/umount -l "$mnt" 2>/dev/null
    done
fi

if grep -q "$ALPINEPATH" /proc/mounts 2>/dev/null; then
    error "Filesystems still mounted — forcing lazy unmount..."
    grep "$ALPINEPATH" /proc/mounts | awk '{print $2}' | xargs -r $BB umount -l 2>/dev/null
    if grep -q "$ALPINEPATH" /proc/mounts 2>/dev/null; then
        error "CRITICAL: Could not unmount. Reboot device."
        exit 1
    fi
fi

if [ -d "$ALPINEPATH" ]; then
    progress "Removing RootFS directory..."
    rm -rf "$ALPINEPATH" && success "RootFS removed." || error "Failed to remove directory."
else
    progress "RootFS directory not found (already removed?)"
fi

progress "Removing launcher scripts..."
for script in $LAUNCH_SCRIPTS; do
    if [ -f "$script" ]; then
        rm -f "$script"
        success "Removed: $script"
    fi
done

# Do NOT remove shared fluxlinux_chroot.sh (used by Debian too)
success "Uninstallation Complete!"

mount -t tracefs tracefs /sys/kernel/tracing 2>/dev/null || true

progress "Notifying FluxLinux App..."
/system/bin/am start -a android.intent.action.VIEW \
    -d "fluxlinux://callback?result=success&name=distro_uninstall_alpine_chroot" \
    >/dev/null 2>&1 || true
