#!/bin/sh
# uninstall_guest_chroot.sh — remove a FluxLinux guest chroot (Requires Root)
# Target from FLUX_CHROOT (must be set). Does not remove shared fluxlinux_chroot.sh.

GUESTPATH="${FLUX_CHROOT:-}"
LAUNCH_SCRIPTS="/data/local/tmp/start_guest_gui.sh /data/local/tmp/stop_guest_gui.sh"

error()    { printf "\033[1;31m[!] %s\033[0m\n" "$1"; }
success()  { printf "\033[1;32m[✓] %s\033[0m\n" "$1"; }
progress() { printf "\033[1;36m[+] %s\033[0m\n" "$1"; }

if [ "$(id -u)" != "0" ]; then
    error "This script must be run as root."
    exit 1
fi

if [ -z "$GUESTPATH" ]; then
    error "FLUX_CHROOT is not set."
    exit 1
fi

case "$GUESTPATH" in
    /data/local/tmp/chrootDebian13|/data/local/tmp/chrootAlpine|\
    /data/local/tmp/chrootFedora|/data/local/tmp/chrootVoid|\
    /data/local/tmp/chrootOpenSUSE|/data/local/tmp/chrootDeepin|\
    /data/local/tmp/chrootChimera|/data/local/tmp/chrootManjaro|\
    /data/local/tmp/chrootUbuntu|/data/local/tmp/chrootKali|\
    /data/local/tmp/chrootParrot|/data/local/tmp/chrootArch) ;;
    *)
        error "Refusing to remove unexpected path: $GUESTPATH"
        exit 1
        ;;
esac

progress "Starting Uninstallation of guest chroot..."
progress "Target: $GUESTPATH"

if command -v busybox >/dev/null 2>&1; then
    BB="busybox"
else
    BB=""
fi

progress "Checking for stalled processes..."
HELPER="$(dirname "$0")/chroot_processes.sh"
if [ -f "$HELPER" ]; then
    sh "$HELPER" kill "$GUESTPATH" || true
else
    for pid_dir in /proc/[0-9]*; do
        if [ -d "$pid_dir" ]; then
            PID=$(basename "$pid_dir")
            ROOT=$(readlink "$pid_dir/root" 2>/dev/null)
            if [ "$ROOT" = "$GUESTPATH" ]; then
                progress "Killing stuck process $PID..."
                kill -9 "$PID" 2>/dev/null
            fi
        fi
    done
fi

progress "Unmounting filesystems under $GUESTPATH..."
MOUNTS=$(grep "$GUESTPATH" /proc/mounts 2>/dev/null | awk '{print $2}' | sort -r)
if [ -z "$MOUNTS" ]; then
    progress "No mounts found (Clean)."
else
    for mnt in $MOUNTS; do
        progress "Unmounting: $mnt"
        $BB umount -l "$mnt" 2>/dev/null || umount -l "$mnt" 2>/dev/null
    done
fi

if grep -q "$GUESTPATH" /proc/mounts 2>/dev/null; then
    error "Filesystems still mounted — forcing lazy unmount..."
    grep "$GUESTPATH" /proc/mounts | awk '{print $2}' | xargs -r $BB umount -l 2>/dev/null
    if grep -q "$GUESTPATH" /proc/mounts 2>/dev/null; then
        error "CRITICAL: Could not unmount. Reboot device."
        exit 1
    fi
fi

if [ -d "$GUESTPATH" ]; then
    progress "Removing RootFS directory..."
    rm -rf "$GUESTPATH" && success "RootFS removed." || error "Failed to remove directory."
else
    progress "RootFS directory not found (already removed?)"
fi

success "Uninstallation Complete!"

_cb="${FLUX_DISTRO_ID:-guest_chroot}"
am start -a android.intent.action.VIEW \
    -d "fluxlinux://callback?result=success&name=distro_uninstall_${_cb}" \
    >/dev/null 2>&1 || true
