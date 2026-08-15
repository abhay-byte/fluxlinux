#!/bin/sh
# uninstall_debian13_chroot.sh
# Uninstalls Debian 13 (Trixie) Chroot environment (Requires Root)

# Paths (Must match setup_debian13_chroot.sh)
DEBIANPATH="/data/local/tmp/chrootDebian13"
LAUNCH_SCRIPTS="/data/local/tmp/start_debian13.sh /data/local/tmp/start_debian13_gui.sh /data/local/tmp/enter_debian13.sh /data/local/tmp/enter_debian13_root.sh /data/local/tmp/run_debian13_root.sh /data/local/tmp/stop_debian13_gui.sh /data/local/tmp/uninstall_debian13_chroot.sh /data/local/tmp/fluxlinux_chroot.sh"

# Error Handler
error() {
    printf "\033[1;31m[!] %s\033[0m\n" "$1"
}

success() {
    printf "\033[1;32m[✓] %s\033[0m\n" "$1"
}

progress() {
    printf "\033[1;36m[+] %s\033[0m\n" "$1"
}

# Check Root
if [ "$(id -u)" != "0" ]; then
    error "This script must be run as root."
    exit 1
fi

progress "Starting Uninstallation of Debian 13 Chroot..."
progress "Target: $DEBIANPATH"

# Detect Busybox
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


# 1. Kill Stale Processes (prefer shared SSOT helper)
progress "Checking for stalled processes..."
HELPER="$(dirname "$0")/chroot_processes.sh"
if [ -f "$HELPER" ]; then
    progress "Killing chroot processes via chroot_processes.sh..."
    sh "$HELPER" kill "$DEBIANPATH" || true
else
    # Fallback: inline loop for standalone adb runs without helper staged
    for pid_dir in /proc/[0-9]*; do
        if [ -d "$pid_dir" ]; then
            PID=$(basename "$pid_dir")
            ROOT=$(readlink "$pid_dir/root" 2>/dev/null)
            if [ "$ROOT" = "$DEBIANPATH" ]; then
                progress "Killing stuck process $PID..."
                kill -9 "$PID" 2>/dev/null
            fi
        fi
    done
fi

# 2. Dynamic Unmount (Deepest First)
progress "Unmounting any filesystems under $DEBIANPATH..."

MOUNTS=$(grep "$DEBIANPATH" /proc/mounts | awk '{print $2}' | sort -r)

if [ -z "$MOUNTS" ]; then
    progress "No mounts found (Clean)."
else
    for mnt in $MOUNTS; do
        progress "Unmounting: $mnt"
        $BB umount -l "$mnt" 2>/dev/null || /system/bin/umount -l "$mnt" 2>/dev/null
    done
fi

# Double check
if grep -q "$DEBIANPATH" /proc/mounts; then
    error "Filesystems still mounted:"
    grep "$DEBIANPATH" /proc/mounts
    error "Forcing lazy unmount on remaining..."
    grep "$DEBIANPATH" /proc/mounts | awk '{print $2}' | xargs -r $BB umount -l 2>/dev/null
    
    if grep -q "$DEBIANPATH" /proc/mounts; then
         error "CRITICAL: Could not unmount. Reboot device."
         exit 1
    fi
fi

# 3. Remove RootFS
if [ -d "$DEBIANPATH" ]; then
    progress "Removing RootFS directory..."
    rm -rf "$DEBIANPATH"
    if [ $? -eq 0 ]; then
        success "RootFS removed."
    else
        error "Failed to remove directory. Check permissions."
    fi
else
    progress "RootFS directory not found (already removed?)"
fi

# 4. Remove Scripts
progress "Removing launcher scripts..."
for script in $LAUNCH_SCRIPTS; do
    if [ -f "$script" ]; then
        rm -f "$script"
        success "Removed: $script"
    fi
done

success "Uninstallation Complete!"

# 5. Ensure Host TraceFS is Mounted (Prevents Android Zygote Trace Crash)
mount -t tracefs tracefs /sys/kernel/tracing 2>/dev/null || true

# 6. Notify NativeCode App
progress "Notifying NativeCode App..."
/system/bin/am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=distro_uninstall_debian13_chroot" >/dev/null 2>&1
