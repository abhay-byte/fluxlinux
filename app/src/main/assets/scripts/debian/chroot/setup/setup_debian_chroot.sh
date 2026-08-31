#!/bin/sh

# setup_debian_chroot.sh
# Installs a Debian Chroot environment (Requires Root)
# Based on LinuxDroidMaster/Termux-Desktops Guide

# Global Variables
DEBIANPATH="/data/local/tmp/chrootDebian"
USERNAME="flux"

# Function to show progress message
progress() {
    printf "\033[1;36m[+] %s\033[0m\n" "$1"
}

# Function to show success message
success() {
    printf "\033[1;32m[✓] %s\033[0m\n" "$1"
}

# Function to show error message
error() {
    printf "\033[1;31m[!] %s\033[0m\n" "$1"
}

# Cleanup function
cleanup_mounts() {
    printf "\033[1;36m[+] Safety Check: Unmounting filesystems...\033[0m\n"
    # Unmount in reverse order to avoid busy targets
    $BB umount "$DEBIANPATH/sdcard" 2>/dev/null
    $BB umount "$DEBIANPATH/dev/shm" 2>/dev/null
    $BB umount "$DEBIANPATH/dev/pts" 2>/dev/null
    $BB umount "$DEBIANPATH/proc" 2>/dev/null
    $BB umount "$DEBIANPATH/sys" 2>/dev/null
    $BB umount "$DEBIANPATH/dev" 2>/dev/null
}

# Exit handler
goodbye() {
    error "Something went wrong."
    cleanup_mounts
    error "Exiting..."
    exit 1
}

# Extraction Helper
extract_file() {
    progress "Extracting file..."
    if [ -d "$1/debian12-arm64" ]; then
        printf "\033[1;33m[!] Directory already exists: %s/debian12-arm64\033[0m\n" "$1"
        printf "\033[1;33m[!] Skipping extraction...\033[0m\n"
    else
        # Guide: tar xpvf debian12-arm64.tar.gz --numeric-owner
        tar xpvf "$1/debian12-arm64.tar.gz" -C "$1" --numeric-owner >/dev/null 2>&1
        if [ $? -eq 0 ]; then
            success "File extracted successfully: $1/debian12-arm64"
        else
            error "Error extracting file."
            goodbye
        fi
    fi
}

# Main Configuration Logic
configure_debian_chroot() {
    progress "Configuring Debian chroot environment..."
    
    # Ensure directory exists
    if [ ! -d "$DEBIANPATH" ]; then
        mkdir -p "$DEBIANPATH"
        [ $? -ne 0 ] && goodbye
    fi

    # --- MOUNTS (Matches Guide) ---
    progress "Mounting filesystems..."
    $BB mount -o remount,dev,suid /data
    
    $BB mount --bind /dev "$DEBIANPATH/dev" || /system/bin/mount --bind /dev "$DEBIANPATH/dev" || goodbye
    $BB mount --bind /sys "$DEBIANPATH/sys" || /system/bin/mount --bind /sys "$DEBIANPATH/sys" || goodbye
    $BB mount -t proc proc "$DEBIANPATH/proc" || /system/bin/mount -t proc proc "$DEBIANPATH/proc" || goodbye
    $BB mount -t devpts devpts "$DEBIANPATH/dev/pts" || /system/bin/mount -t devpts devpts "$DEBIANPATH/dev/pts" || goodbye

    # /dev/shm for Electron apps
    mkdir -p "$DEBIANPATH/dev/shm"
    $BB mount -t tmpfs -o size=256M tmpfs "$DEBIANPATH/dev/shm" || /system/bin/mount -t tmpfs -o size=256M tmpfs "$DEBIANPATH/dev/shm" || goodbye

    # Mount sdcard
    mkdir -p "$DEBIANPATH/sdcard"
    $BB mount --bind /sdcard "$DEBIANPATH/sdcard" || /system/bin/mount --bind /sdcard "$DEBIANPATH/sdcard" || goodbye

    # --- CHROOT CONFIGURAION ---
    # We use non-interactive mode (-c) to automate the guide's interactive steps
    
    progress "Configuring Network and Groups..."
    $BB chroot "$DEBIANPATH" /bin/su - root -c '
        # DNS & Hosts
        echo "nameserver 8.8.8.8" > /etc/resolv.conf
        echo "127.0.0.1 localhost" > /etc/hosts

        # Android IDs
        groupadd -g 3003 aid_inet
        groupadd -g 3004 aid_net_raw
        groupadd -g 1003 aid_graphics
        
        # Permissions
        usermod -g 3003 -G 3003,3004 -a _apt
        usermod -G 3003 -a root
    ' || goodbye
    
    progress "Updating packages (apt update/upgrade)..."
    $BB chroot "$DEBIANPATH" /bin/su - root -c '
        apt update
        apt upgrade -y
        apt install -y nano vim net-tools sudo git dbus-x11
    ' || goodbye

    # --- USER CREATION (Matches Guide) ---
    progress "Creating User ($USERNAME)..."
    $BB chroot "$DEBIANPATH" /bin/su - root -c "
        groupadd storage
        groupadd wheel
        # Guide: useradd -m -g users -G wheel,audio,video,storage,aid_inet -s /bin/bash USER
        id -u $USERNAME >/dev/null 2>&1 || useradd -m -g users -G wheel,audio,video,storage,aid_inet -s /bin/bash $USERNAME
        # Set default password to 'flux' (Guide uses passwd interactive, we default it for automation)
        echo '$USERNAME:flux' | chpasswd
    " || goodbye

    # --- SUDOERS ---
    progress "Configuring Sudoers..."
    $BB chroot "$DEBIANPATH" /bin/su - root -c "
        # Guide: user ALL=(ALL:ALL) ALL
        # We use NOPASSWD for better mobile experience, but format matches guide intent
        echo '$USERNAME ALL=(ALL:ALL) NOPASSWD:ALL' > /etc/sudoers.d/$USERNAME
        chmod 0440 /etc/sudoers.d/$USERNAME
    " || goodbye

    # --- DESKTOP INSTALL ---
    progress "Installing XFCE4..."
    $BB chroot "$DEBIANPATH" /bin/su - root -c '
        export DEBIAN_FRONTEND=noninteractive
        apt install -y xfce4 xfce4-terminal
    ' || goodbye

    success "Debian Environment Configured!"

    # --- LAUNCH SCRIPT GENERATION ---
    LAUNCH_SCRIPT="/data/local/tmp/start_debian.sh"
    progress "Creating launch script at $LAUNCH_SCRIPT..."
    
    # Matches the guide's 'start_debian.sh' content
    cat <<EOF > "$LAUNCH_SCRIPT"
#!/bin/sh

# Path of DEBIAN rootfs
DEBIANPATH="/data/local/tmp/chrootDebian"
BB="$BB"
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH

# Fix setuid issue
\$BB mount -o remount,dev,suid /data

\$BB mount --bind /dev \$DEBIANPATH/dev
\$BB mount --bind /sys \$DEBIANPATH/sys
\$BB mount -t proc proc \$DEBIANPATH/proc
\$BB mount -t devpts devpts \$DEBIANPATH/dev/pts

# /dev/shm for Electron apps
mkdir -p \$DEBIANPATH/dev/shm
\$BB mount -t tmpfs -o size=256M tmpfs \$DEBIANPATH/dev/shm

# Mount sdcard
mkdir -p \$DEBIANPATH/sdcard
\$BB mount --bind /sdcard \$DEBIANPATH/sdcard

# Launch GUI as user
# Guide line: busybox chroot \$DEBIANPATH /bin/su - droidmaster -c 'export DISPLAY=:0 ...'
echo "Starting Debian Chroot GUI ($USERNAME)..."
\$BB chroot \$DEBIANPATH /bin/su - $USERNAME -c 'export DISPLAY=:0 && export PULSE_SERVER=127.0.0.1 && dbus-launch --exit-with-session startxfce4'
EOF
    chmod +x "$LAUNCH_SCRIPT"
    success "Launch script created."
    
    # Unmount after setup
    cleanup_mounts
    success "Setup Complete!"
}

# Main Execution Flow
main() {
    # Fix environment for Termux binaries running as root
    export LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib
    
    if [ "$(id -u)" != "0" ]; then
        error "This script must be run as root. Exiting."
        exit 1
    fi

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

    if [ -n "$BB" ]; then
         # Test Busybox
         if ! "$BB" true >/dev/null 2>&1; then
            printf "\033[1;33m[!] Found Busybox at $BB but it seems broken.\033[0m\n"
         fi
         progress "Using Root Busybox: $BB"
    fi

    DEBIANPATH="/data/local/tmp/chrootDebian"
    
    # --- CHECK EXISTING INSTALLATION ---
    if [ -f "$DEBIANPATH/bin/bash" ]; then
        success "Debian Chroot appears to be already installed."
        progress "Skipping Download/Extraction/Config..."
        progress "Regenerating launch scripts..."
        
        # We assume dependencies are met if installed.
        # Just creating the launch scripts again to be safe.
        
        # --- RE-GENERATE LAUNCH SCRIPT (Core) ---
        LAUNCH_SCRIPT="/data/local/tmp/start_debian.sh"
        cat <<EOF > "$LAUNCH_SCRIPT"
#!/bin/sh

# Path of DEBIAN rootfs
DEBIANPATH="/data/local/tmp/chrootDebian"
BB="$BB"
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH

# Fix setuid issue
\$BB mount -o remount,dev,suid /data

\$BB mount --bind /dev \$DEBIANPATH/dev
\$BB mount --bind /sys \$DEBIANPATH/sys
\$BB mount --bind /proc \$DEBIANPATH/proc
\$BB mount -t devpts devpts \$DEBIANPATH/dev/pts

# Mount Termux Tmp (For X11 Socket Sharing)
mkdir -p \$DEBIANPATH/tmp
\$BB mount --bind /data/data/com.termux/files/usr/tmp \$DEBIANPATH/tmp

# /dev/shm for Electron apps
mkdir -p \$DEBIANPATH/dev/shm
\$BB mount -t tmpfs -o size=256M tmpfs \$DEBIANPATH/dev/shm

# Mount sdcard
mkdir -p \$DEBIANPATH/sdcard
\$BB mount --bind /sdcard \$DEBIANPATH/sdcard

# Launch GUI as user
echo "Starting Debian Chroot GUI ($USERNAME)..."
\$BB chroot \$DEBIANPATH /bin/su - $USERNAME -c 'export DISPLAY=:0 && export PULSE_SERVER=127.0.0.1 && dbus-launch --exit-with-session startxfce4'
EOF
        chmod +x "$LAUNCH_SCRIPT"
        success "Core launch script updated: $LAUNCH_SCRIPT"
    else
        # --- NEW INSTALLATION FLOW ---
        if [ ! -d "$DEBIANPATH" ]; then
            mkdir -p "$DEBIANPATH"
            success "Created directory: $DEBIANPATH"
        fi

        # The Kotlin payload provider supplies a verified app-private archive.
        ROOTFS_ARCHIVE="${FLUX_ROOTFS_PATH:-}"
        if [ -z "$ROOTFS_ARCHIVE" ] || [ ! -f "$ROOTFS_ARCHIVE" ] || [ ! -s "$ROOTFS_ARCHIVE" ]; then
            error "No verified local Debian rootfs supplied via FLUX_ROOTFS_PATH"
            goodbye
        fi
        cp -f "$ROOTFS_ARCHIVE" "$DEBIANPATH/debian12-arm64.tar.gz" || goodbye
        
        # Extract
        extract_file "$DEBIANPATH"
        
        # Configure (Includes generating start_debian.sh)
        configure_debian_chroot

        # --- LAUNCH SCRIPT GENERATION ---
        LAUNCH_SCRIPT="/data/local/tmp/start_debian.sh"
        progress "Creating launch script at $LAUNCH_SCRIPT..."
        
        # Matches the guide's 'start_debian.sh' content
        cat <<EOF > "$LAUNCH_SCRIPT"
#!/bin/sh

# Path of DEBIAN rootfs
DEBIANPATH="/data/local/tmp/chrootDebian"
BB="$BB"
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH

# Fix setuid issue
\$BB mount -o remount,dev,suid /data

\$BB mount --bind /dev \$DEBIANPATH/dev
\$BB mount --bind /sys \$DEBIANPATH/sys
\$BB mount --bind /proc \$DEBIANPATH/proc
\$BB mount -t devpts devpts \$DEBIANPATH/dev/pts

# /dev/shm for Electron apps
mkdir -p \$DEBIANPATH/dev/shm
\$BB mount -t tmpfs -o size=256M tmpfs \$DEBIANPATH/dev/shm

# Mount sdcard
mkdir -p \$DEBIANPATH/sdcard
\$BB mount --bind /sdcard \$DEBIANPATH/sdcard

# Launch GUI as user
# Guide line: busybox chroot \$DEBIANPATH /bin/su - droidmaster -c 'export DISPLAY=:0 ...'
echo "Cleaning internal XFCE4 session..."
\$BB chroot \$DEBIANPATH /bin/su - root -c "killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon" >/dev/null 2>&1

echo "Starting Debian Chroot GUI ($USERNAME)..."
\$BB chroot \$DEBIANPATH /bin/su - $USERNAME -c 'export DISPLAY=:0 && export PULSE_SERVER=127.0.0.1 && dbus-launch --exit-with-session startxfce4'
EOF
        chmod +x "$LAUNCH_SCRIPT"
        success "Launch script created."
    fi

    # --- GENERATE GUI LAUNCHER (For X11 from Root) ---
    GUI_LAUNCHER="/data/local/tmp/start_debian_gui.sh"
    progress "Creating X11 GUI Launcher at $GUI_LAUNCHER..."
    
    TARGET_TERMUX_PREFIX="/data/data/com.termux/files/usr"
    
    cat <<EOF > "$GUI_LAUNCHER"
#!/bin/sh
# Wrapper to start X11/Pulse and then the Chroot
# Run this from Termux User Shell (via App)

PREFIX="/data/data/com.termux/files/usr"

# 1. Cleanup Stale Root Processes (Fixes Persistence)
# We use su to ensure any previous Root-owned X11 sessions are killed.
# Also clean the socket dir to ensure we can bind to it.
su -c "$BB pkill -9 -f termux-x11; $BB rm -rf \$PREFIX/tmp/.X11-unix \$PREFIX/tmp/.X0-lock" >/dev/null 2>&1

# 2. Cleanup User Processes
pkill -9 -f termux-x11 >/dev/null 2>&1
pkill -9 -f com.termux.x11 >/dev/null 2>&1
killall -9 termux-x11 Xwayland pulseaudio virgl_test_server_android >/dev/null 2>&1

# 3. Start Termux:X11 App
echo "Starting X11 App..."
am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity >/dev/null

# 4. Start XServer (Xwayland) in background (AS USER)
export XDG_RUNTIME_DIR="\$PREFIX/tmp"
export TMPDIR="\$XDG_RUNTIME_DIR"
export LD_LIBRARY_PATH=\$PREFIX/lib

echo "Starting XServer (User)..."
\$PREFIX/bin/termux-x11 :0 -ac &

sleep 3

# 5. Start PulseAudio (AS USER)
echo "Starting PulseAudio..."
\$PREFIX/bin/pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1
\$PREFIX/bin/pacmd load-module module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1

# 6. Launch Chroot (Requires Root for Mounts)
echo "Entering Chroot..."
su -c "sh /data/local/tmp/start_debian.sh"
EOF
    chmod +x "$GUI_LAUNCHER"
    success "GUI Launcher created: $GUI_LAUNCHER"
    
    # --- GENERATE CLI LAUNCHER (Shell Only) ---
    CLI_SCRIPT="/data/local/tmp/enter_debian.sh"
    progress "Creating CLI Launcher at $CLI_SCRIPT..."
    
    cat <<EOF > "$CLI_SCRIPT"
#!/bin/sh
# CLI Entry for Debian Chroot

# Path of DEBIAN rootfs
DEBIANPATH="/data/local/tmp/chrootDebian"
BB="$BB"
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH

# Fix setuid issue
\$BB mount -o remount,dev,suid /data

\$BB mount --bind /dev \$DEBIANPATH/dev
\$BB mount --bind /sys \$DEBIANPATH/sys
\$BB mount --bind /proc \$DEBIANPATH/proc
\$BB mount -t devpts devpts \$DEBIANPATH/dev/pts

# /dev/shm for Electron apps
mkdir -p \$DEBIANPATH/dev/shm
\$BB mount -t tmpfs -o size=256M tmpfs \$DEBIANPATH/dev/shm

# Mount sdcard
mkdir -p \$DEBIANPATH/sdcard
\$BB mount --bind /sdcard \$DEBIANPATH/sdcard

# Enter Shell
echo "Entering Debian Chroot (CLI)..."
\$BB chroot \$DEBIANPATH /bin/su - $USERNAME
EOF
    chmod +x "$CLI_SCRIPT"
    success "CLI Launcher created: $CLI_SCRIPT"
    
    echo "FluxLinux: Chroot Setup Complete!"
    
    # --- NOTIFY APP ---
    progress "Notifying FluxLinux App..."
    am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=distro_install_debian_chroot" >/dev/null 2>&1
}

main
