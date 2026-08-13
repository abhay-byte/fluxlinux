#!/bin/sh

# setup_debian13_chroot.sh
# Installs a Debian 13 (Trixie) Chroot environment (Requires Root)
# Based on LinuxDroidMaster/Termux-Desktops Guide

# Global Variables
DEBIANPATH="/data/local/tmp/chrootDebian13"
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

# Download Helper
download_file() {
    progress "Downloading file..."
    if [ -e "$1/$2" ]; then
        printf "\033[1;33m[!] File already exists: %s\033[0m\n" "$2"
        printf "\033[1;33m[!] Skipping download...\033[0m\n"
    else
        # Try standard wget first (if available and working)
        wget -O "$1/$2" "$3"
        if [ $? -eq 0 ]; then
            success "File downloaded successfully: $2"
        else
            error "Standard wget failed: $2."
            progress "Trying fallback to Busybox wget..."
            $BB wget -O "$1/$2" "$3"
            if [ $? -eq 0 ]; then
                 success "File downloaded successfully (Fallback)"
            else
                 goodbye
            fi
        fi
    fi
}

# Extraction Helper
extract_file() {
    progress "Extracting file..."
    if [ -f "$1/bin/bash" ]; then
        printf "\033[1;33m[!] Rootfs appears populated: %s/bin/bash\033[0m\n" "$1"
        printf "\033[1;33m[!] Skipping extraction...\033[0m\n"
    else
        # Trixie Rootfs is .tar.xz
        # Try 1: Busybox tar auto-detect
        if tar xpvf "$1/rootfs.tar.xz" -C "$1" --numeric-owner >/dev/null 2>&1; then
            success "Rootfs extracted successfully."
            return 0
        fi
        
        # Try 2: System/Busybox unxz pipe (Fix for minimal Busybox versions)
        progress "Standard extract failed. Trying unxz pipe..."
        
        # Determine unxz command
        UNXZ_CMD="unxz"
        if ! command -v unxz >/dev/null 2>&1; then
            if "$BB" unxz --help >/dev/null 2>&1; then
                UNXZ_CMD="$BB unxz"
            else
                error "No 'unxz' tool found. Cannot extract .tar.xz file."
                goodbye
            fi
        fi
        
        if $UNXZ_CMD -c "$1/rootfs.tar.xz" | tar xpv -C "$1" --numeric-owner >/dev/null 2>&1; then
             success "Rootfs extracted successfully (via unxz pipe)."
             return 0
        fi
        
        # Try 3: Last ditch attempt with explicit flags
        if tar xJvf "$1/rootfs.tar.xz" -C "$1" --numeric-owner >/dev/null 2>&1; then
             success "Rootfs extracted successfully (Fallback flags)."
             return 0
        fi

        error "Extraction Failed! Your Busybox/Tar does not support XZ compression."
        goodbye
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
    
    $BB mount --bind /dev "$DEBIANPATH/dev" || goodbye
    $BB mount --bind /sys "$DEBIANPATH/sys" || goodbye
    $BB mount -t proc proc "$DEBIANPATH/proc" || goodbye
    $BB mount -t devpts devpts "$DEBIANPATH/dev/pts" || goodbye

    # /dev/shm for Electron apps
    mkdir -p "$DEBIANPATH/dev/shm"
    $BB mount -t tmpfs -o size=512M tmpfs "$DEBIANPATH/dev/shm" || goodbye

    # Mount sdcard

# Mount Termux tmp to chroot tmp (for X11 sockets)
mkdir -p \$DEBIANPATH/tmp
\$BB mount --bind /data/data/com.termux/files/usr/tmp \$DEBIANPATH/tmp
    mkdir -p "$DEBIANPATH/sdcard"
    $BB mount --bind /sdcard "$DEBIANPATH/sdcard" || goodbye

    # --- CHROOT CONFIGURAION ---
    # We use non-interactive mode (-c) to automate the guide's interactive steps
    
    progress "Configuring Network and Groups..."
    # FIX: Use /bin/bash instead of 'su -' to preserve inherited Android GIDs (aid_inet)
    $BB chroot "$DEBIANPATH" /bin/bash -c '
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        export TMPDIR=/tmp
        # DNS & Hosts (Force Remove to avoid symlink issues)
        rm -f /etc/resolv.conf
        echo "nameserver 8.8.8.8" > /etc/resolv.conf
        echo "127.0.0.1 localhost" > /etc/hosts

        # Android IDs
        # We try groupadd, but if groups exist (standard in some rootfs), we suppress error
        groupadd -g 3003 aid_inet 2>/dev/null
        groupadd -g 3004 aid_net_raw 2>/dev/null
        groupadd -g 1003 aid_graphics 2>/dev/null
        
        # Permissions
        usermod -g 3003 -G 3003,3004 -a _apt 2>/dev/null || true
        usermod -G 3003 -a root 2>/dev/null || true
        
        # Verify Network
        echo "Testing Network..."
        if ping -c 1 google.com >/dev/null 2>&1; then
            echo " [OK] Network is working."
        else
            echo " [!] Network check failed. Apt might fail."
        fi
    ' || goodbye
    
    progress "Updating packages (apt update/upgrade)..."
    $BB chroot "$DEBIANPATH" /bin/bash -c '
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        export TMPDIR=/tmp
        export DEBIAN_FRONTEND=noninteractive
        apt update
        apt upgrade -y
        apt install -y nano vim net-tools sudo git dbus-x11 wget unzip
    ' || goodbye

    # --- USER CREATION (Matches Guide) ---
    progress "Creating User ($USERNAME)..."
    $BB chroot "$DEBIANPATH" /bin/bash -c "
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        export TMPDIR=/tmp
        groupadd storage 2>/dev/null
        groupadd wheel 2>/dev/null
        # Guide: useradd -m -g users -G wheel,audio,video,storage,aid_inet -s /bin/bash USER
        id -u $USERNAME >/dev/null 2>&1 || useradd -m -g users -G wheel,audio,video,storage,aid_inet -s /bin/bash $USERNAME
        # Set default password
        echo '$USERNAME:flux' | chpasswd
    " || goodbye

    # --- SUDOERS ---
    progress "Configuring Sudoers..."
    $BB chroot "$DEBIANPATH" /bin/bash -c "
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        export TMPDIR=/tmp
        # Guide: user ALL=(ALL:ALL) ALL
        # We use NOPASSWD for better mobile experience, but format matches guide intent
        echo '$USERNAME ALL=(ALL:ALL) NOPASSWD:ALL' > /etc/sudoers.d/$USERNAME
        chmod 0440 /etc/sudoers.d/$USERNAME
    " || goodbye

    # --- DESKTOP INSTALL ---
    progress "Installing XFCE4..."
    $BB chroot "$DEBIANPATH" /bin/bash -c '
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        export TMPDIR=/tmp
        export DEBIAN_FRONTEND=noninteractive
        apt install -y xfce4 xfce4-terminal
    ' || goodbye

    # Mark configuration as complete to prevent re-runs
    touch "$DEBIANPATH/.flux_configured"

    success "Debian Environment Configured!"

    # --- LAUNCH SCRIPT GENERATION ---
    LAUNCH_SCRIPT="/data/local/tmp/start_debian13.sh"
    progress "Creating launch script at $LAUNCH_SCRIPT..."
    
    # Matches the guide's 'start_debian.sh' content
    cat <<EOF > "$LAUNCH_SCRIPT"
#!/bin/sh

# Path of DEBIAN rootfs
DEBIANPATH="/data/local/tmp/chrootDebian13"
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
\$BB mount -t tmpfs -o size=512M tmpfs \$DEBIANPATH/dev/shm

# Mount sdcard

# Mount Termux tmp to chroot tmp (for X11 sockets)
mkdir -p \$DEBIANPATH/tmp
\$BB mount --bind /data/data/com.termux/files/usr/tmp \$DEBIANPATH/tmp
mkdir -p \$DEBIANPATH/sdcard
\$BB mount --bind /sdcard \$DEBIANPATH/sdcard

# Launch GUI as user
# Guide line: busybox chroot \$DEBIANPATH /bin/su - droidmaster -c 'export DISPLAY=:0 ...'
echo "Cleaning internal XFCE4 session..."
\$BB chroot \$DEBIANPATH /bin/su - root -c "killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon" >/dev/null 2>&1

echo "Starting Debian 13 Chroot GUI ($USERNAME)..."
\$BB chroot \$DEBIANPATH /bin/su - $USERNAME -c 'export DISPLAY=:0 && export PULSE_SERVER=tcp:127.0.0.1 && export XDG_RUNTIME_DIR=/tmp && xfconf-query -c xfwm4 -p /general/use_compositing -s false 2>/dev/null; dbus-launch --exit-with-session startxfce4'
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

    # --- BUSYBOX DETECTION (Robust/Root-Only) ---
    BB=""
    
    # 1. Check PATH first (Smart Fallback)
    if command -v busybox >/dev/null 2>&1; then
        DETECTED_BB=$(command -v busybox)
        # Check if it's the Termux one (which we want to avoid for root actions)
        case "$DETECTED_BB" in
            *"com.termux"*)
                # Matches termux path, ignore it per user request
                ;;
            *)
                # Path doesn't look like termux, assume it's system/magisk
                if [ -x "$DETECTED_BB" ]; then
                    BB="$DETECTED_BB"
                fi
                ;;
        esac
    fi
    
    # 2. If not found in PATH (or was Termux), scan hardcoded Magisk paths
    if [ -z "$BB" ]; then
        # Priority: Magisk/System Busybox
        CANDIDATES="/data/adb/magisk/busybox \
        /data/adb/modules/busybox-ndk/system/bin/busybox \
        /sbin/busybox \
        /system/xbin/busybox \
        /system/bin/busybox \
        /debug_ramdisk/busybox"
        
        for path in $CANDIDATES; do
            if [ -x "$path" ]; then
                BB="$path"
                break
            fi
        done
    fi
    
    if [ -z "$BB" ]; then
         error "Root-capable Busybox not found!"
         printf "\033[1;33m[!] Scanned paths:\n$CANDIDATES\033[0m\n"
         exit 1
    else
         # Test Busybox
         if ! "$BB" true >/dev/null 2>&1; then
            printf "\033[1;33m[!] Found Busybox at $BB but it seems broken.\033[0m\n"
         fi
         progress "Using Root Busybox: $BB"
    fi

    DEBIANPATH="/data/local/tmp/chrootDebian13"
    
    # --- CHECK EXISTING INSTALLATION ---
    # Check for marker file indicating SUCCESSFUL configuration
    if [ -f "$DEBIANPATH/.flux_configured" ]; then
        success "Debian 13 Chroot appears to be already installed."
        progress "Skipping Download/Extraction/Config..."
        progress "Regenerating launch scripts..."
        
        # We assume dependencies are met if installed.
        # Just creating the launch scripts again to be safe.
        
        # --- RE-GENERATE LAUNCH SCRIPT (Core) ---
        LAUNCH_SCRIPT="/data/local/tmp/start_debian13.sh"
        cat <<EOF > "$LAUNCH_SCRIPT"
#!/bin/sh

# Path of DEBIAN rootfs
DEBIANPATH="/data/local/tmp/chrootDebian13"
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
\$BB mount -t tmpfs -o size=512M tmpfs \$DEBIANPATH/dev/shm

# Mount sdcard

# Mount Termux tmp to chroot tmp (for X11 sockets)
mkdir -p \$DEBIANPATH/tmp
\$BB mount --bind /data/data/com.termux/files/usr/tmp \$DEBIANPATH/tmp
mkdir -p \$DEBIANPATH/sdcard
\$BB mount --bind /sdcard \$DEBIANPATH/sdcard

# Launch GUI as user
echo "Starting Debian 13 Chroot GUI ($USERNAME)..."
\$BB chroot \$DEBIANPATH /bin/su - $USERNAME -c 'export DISPLAY=:0 && export PULSE_SERVER=tcp:127.0.0.1 && export XDG_RUNTIME_DIR=/tmp && xfconf-query -c xfwm4 -p /general/use_compositing -s false 2>/dev/null; dbus-launch --exit-with-session startxfce4'
EOF
        chmod +x "$LAUNCH_SCRIPT"
        success "Core launch script updated: $LAUNCH_SCRIPT"
    else
        # --- NEW INSTALLATION FLOW ---
        if [ ! -d "$DEBIANPATH" ]; then
            mkdir -p "$DEBIANPATH"
            success "Created directory: $DEBIANPATH"
        fi

        # Check for manual download in /sdcard/Download
        MANUAL_FILE="/sdcard/Download/rootfs.tar.xz"
        if [ -f "$MANUAL_FILE" ]; then
            progress "Found manual file: $MANUAL_FILE"
            progress "Copying..."
            cp "$MANUAL_FILE" "$DEBIANPATH/rootfs.tar.xz"
            if [ $? -eq 0 ]; then
                success "File copied successfully."
            else
                printf "\033[1;31m[!] Error copying file. Will attempt download.\033[0m\n"
            fi
        fi
        
        # Download RootFS (Debian 13 Trixie)
        # URL provided by update request
        download_file "$DEBIANPATH" "rootfs.tar.xz" "https://github.com/abhay-byte/fluxlinux/releases/download/rootfs/debian_13_rootfs.tar.xz"
        
        # Extract
        extract_file "$DEBIANPATH"
        
        # Configure (Includes generating start_debian.sh)
        configure_debian_chroot

        # --- LAUNCH SCRIPT GENERATION ---
        LAUNCH_SCRIPT="/data/local/tmp/start_debian13.sh"
        progress "Creating launch script at $LAUNCH_SCRIPT..."
        
        # Matches the guide's 'start_debian.sh' content
        cat <<EOF > "$LAUNCH_SCRIPT"
#!/bin/sh

# Path of DEBIAN rootfs
DEBIANPATH="/data/local/tmp/chrootDebian13"
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
\$BB mount -t tmpfs -o size=512M tmpfs \$DEBIANPATH/dev/shm

# Mount sdcard

# Mount Termux tmp to chroot tmp (for X11 sockets)
mkdir -p \$DEBIANPATH/tmp
\$BB mount --bind /data/data/com.termux/files/usr/tmp \$DEBIANPATH/tmp
mkdir -p \$DEBIANPATH/sdcard
\$BB mount --bind /sdcard \$DEBIANPATH/sdcard

# Launch GUI as user
# Guide line: busybox chroot \$DEBIANPATH /bin/su - droidmaster -c 'export DISPLAY=:0 ...'
echo "Cleaning internal XFCE4 session..."
\$BB chroot \$DEBIANPATH /bin/su - root -c "killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon" >/dev/null 2>&1

echo "Starting Debian 13 Chroot GUI ($USERNAME)..."
\$BB chroot \$DEBIANPATH /bin/su - $USERNAME -c 'export DISPLAY=:0 && export PULSE_SERVER=tcp:127.0.0.1 && export XDG_RUNTIME_DIR=/tmp && xfconf-query -c xfwm4 -p /general/use_compositing -s false 2>/dev/null; dbus-launch --exit-with-session startxfce4'
EOF
        chmod +x "$LAUNCH_SCRIPT"
        success "Launch script created."
    fi

    # --- GENERATE GUI LAUNCHER (For X11 from Root) ---
    GUI_LAUNCHER="/data/local/tmp/start_debian13_gui.sh"
    progress "Creating X11 GUI Launcher at $GUI_LAUNCHER..."
    
    TARGET_TERMUX_PREFIX="/data/data/com.termux/files/usr"
    
    cat <<EOFGUI > "$GUI_LAUNCHER"
#!/bin/sh
# HyperOS-Compatible X11 Launcher with Debug Logging
# FluxLinux - Debian 13 Chroot GUI Starter

echo "========================================"
echo "FluxLinux: Starting Debian 13 GUI"
echo "HyperOS Compatibility Mode"
echo "========================================"

TARGET_TERMUX_PREFIX="/data/data/com.termux/files/usr"
BB="\$BB"

# Debug: Check SELinux
SELINUX_STATUS=\$(getenforce)
echo "[INFO] SELinux Status: \$SELINUX_STATUS"

if [ "\$SELINUX_STATUS" = "Enforcing" ]; then
    echo "[FIX] Setting SELinux to Permissive for X11..."
    setenforce 0
    if [ "\$(getenforce)" = "Permissive" ]; then
        echo "[OK] SELinux set to Permissive"
        echo "[NOTE] Will reset to Enforcing on reboot"
    else
        echo "[WARN] SELinux change failed - continuing anyway"
    fi
fi

# 1. Kill old X11 processes
echo "[1/7] Cleaning up old X11 processes..."
killall -9 termux-x11 Xwayland >/dev/null 2>&1
pkill -f com.termux.x11 >/dev/null 2>&1
sleep 1

# 2. Clean sockets and locks
echo "[2/7] Cleaning X11 sockets..."
rm -rf \$TARGET_TERMUX_PREFIX/tmp/.X11-unix
rm -rf \$TARGET_TERMUX_PREFIX/tmp/.X0-lock
rm -rf \$TARGET_TERMUX_PREFIX/tmp/.X1-lock

# Create X11 socket directory with correct permissions
mkdir -p \$TARGET_TERMUX_PREFIX/tmp/.X11-unix
chmod 1777 \$TARGET_TERMUX_PREFIX/tmp/.X11-unix

# 3. Restore PREFIX/tmp to the app data label (never tmpfs:s0 — that
#    blocks termux-x11 mkdir/lock/dbus under enforcing SELinux).
if command -v chcon >/dev/null 2>&1; then
    echo "[3/7] Restoring host tmp SELinux context..."
    _ctx=\$(ls -Zd \$TARGET_TERMUX_PREFIX 2>/dev/null | awk '{print \$1}')
    if [ -n "\$_ctx" ]; then
      chcon -R "\$_ctx" \$TARGET_TERMUX_PREFIX/tmp 2>/dev/null || true
    fi
fi
restorecon -RF \$TARGET_TERMUX_PREFIX/tmp 2>/dev/null || true

# 4. Start Termux X11 app
echo "[4/7] Starting Termux X11 app..."
am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity >/dev/null 2>&1

# 5. Mount /tmp to chroot
echo "[5/7] Mounting /tmp to chroot..."
\$BB mount --bind \$TARGET_TERMUX_PREFIX/tmp /data/local/tmp/chrootDebian13/tmp 2>/dev/null
chmod -R 1777 \$TARGET_TERMUX_PREFIX/tmp

# 6. Start X server with debug logging
echo "[6/7] Starting X server on :0..."
export XDG_RUNTIME_DIR="\$TARGET_TERMUX_PREFIX/tmp"
export TMPDIR="\$XDG_RUNTIME_DIR"
export LD_LIBRARY_PATH=\$TARGET_TERMUX_PREFIX/lib
export TERMUX_X11_DEBUG=1

\$TARGET_TERMUX_PREFIX/bin/termux-x11 :0 -ac > /tmp/termux-x11.log 2>&1 &
X11_PID=\$!
echo "[INFO] X11 PID: \$X11_PID"

sleep 3

# Verify X11 socket creation
if [ -S "\$TARGET_TERMUX_PREFIX/tmp/.X11-unix/X0" ]; then
    echo "[OK] X11 socket created successfully"
    echo "[INFO] Socket details:"
    ls -laZ \$TARGET_TERMUX_PREFIX/tmp/.X11-unix/ 2>/dev/null | head -3 || ls -la \$TARGET_TERMUX_PREFIX/tmp/.X11-unix/ | head -3
else
    echo "[ERROR] X11 socket NOT created!"
    echo ""
    echo "[DEBUG] X11 process status:"
    ps aux | grep termux-x11 | grep -v grep
    echo ""
    echo "[DEBUG] /tmp contents:"
    ls -la \$TARGET_TERMUX_PREFIX/tmp/ | head -10
    echo ""
    echo "[DEBUG] Recent SELinux denials:"
    dmesg | grep "avc.*denied.*termux" | tail -3
    echo ""
    echo "Check detailed logs: cat /tmp/termux-x11.log"
    echo ""
fi

# Verify X11 process is running
if ps -p \$X11_PID >/dev/null 2>&1; then
    echo "[OK] X11 server running (PID: \$X11_PID)"
else
    echo "[ERROR] X11 server process died!"
    echo "Check logs: cat /tmp/termux-x11.log"
fi

# 7. Verify services
echo "[7/7] Checking services..."
pgrep -f pulseaudio >/dev/null && echo "[OK] PulseAudio running" || echo "[!] PulseAudio not running - audio may fail"

if pgrep -f virgl_test_server >/dev/null; then
    echo "[OK] VirGL server running"
    ls -la \$TARGET_TERMUX_PREFIX/tmp/.virgl_test 2>/dev/null || echo "[WARN] VirGL socket not visible"
else
    echo "[!] VirGL not running - GPU acceleration will NOT work"
    echo "[!] Please restart the GUI from FluxLinux app"
fi

echo ""
echo "Entering chroot..."
sh /data/local/tmp/start_debian13.sh
EOFGUI
    chmod +x "$GUI_LAUNCHER"
    success "GUI Launcher created: $GUI_LAUNCHER"
    
    # --- GENERATE GUI STOP SCRIPT ---
    STOP_LAUNCHER="/data/local/tmp/stop_debian13_gui.sh"
    progress "Creating GUI Stop Script at $STOP_LAUNCHER..."
    
    cat <<EOF > "$STOP_LAUNCHER"
#!/bin/sh
# stop_debian13_gui.sh - Stop Debian 13 Chroot GUI

echo "========================================"
echo "FluxLinux: Stopping Debian 13 Chroot GUI"
echo "========================================"

DEBIANPATH="/data/local/tmp/chrootDebian13"
TARGET_TERMUX_PREFIX="/data/data/com.termux/files/usr"
BB="$BB"

# Step 1: Kill XFCE processes inside chroot
echo "[1/4] Stopping XFCE4 processes in chroot..."
\\$BB chroot \\$DEBIANPATH /bin/su - root -c "killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon" >/dev/null 2>&1

# Step 2: Stop Termux X11
echo "[2/4] Stopping Termux X11..."
killall -9 termux-x11 Xwayland >/dev/null 2>&1
pkill -f com.termux.x11 >/dev/null 2>&1

# Clean up X11 sockets
rm -rf \\$TARGET_TERMUX_PREFIX/tmp/.X11-unix
rm -rf \\$TARGET_TERMUX_PREFIX/tmp/.X0-lock

# Step 3: Unmount filesystems
echo "[3/4] Unmounting filesystems..."
\\$BB umount "\\$DEBIANPATH/sdcard" 2>/dev/null
\\$BB umount "\\$DEBIANPATH/dev/shm" 2>/dev/null
\\$BB umount "\\$DEBIANPATH/dev/pts" 2>/dev/null
\\$BB umount "\\$DEBIANPATH/proc" 2>/dev/null
\\$BB umount "\\$DEBIANPATH/sys" 2>/dev/null
\\$BB umount "\\$DEBIANPATH/dev" 2>/dev/null
\\$BB umount "\\$DEBIANPATH/tmp" 2>/dev/null

# Step 4: Done
echo "[4/4] Cleanup complete"

echo ""
echo "✅ Chroot GUI stopped successfully!"
echo "========================================"
exit 0
EOF
    chmod +x "$STOP_LAUNCHER"
    success "GUI Stop Script created: $STOP_LAUNCHER"
    
    # --- GENERATE ROOT COMMAND RUNNER (For App Scripts) ---
    ROOT_RUNNER="/data/local/tmp/run_debian13_root.sh"
    progress "Creating Root Runner at $ROOT_RUNNER..."
    
    cat <<EOF > "$ROOT_RUNNER"
#!/bin/sh
# Wrapper to run a command inside Chroot as Root (with mounts)

DEBIANPATH="/data/local/tmp/chrootDebian13"
BB="$BB"
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH

# 1. Mounts (Idempotent)
\$BB mount -o remount,dev,suid /data >/dev/null 2>&1
\$BB mount --bind /dev \$DEBIANPATH/dev >/dev/null 2>&1
\$BB mount --bind /sys \$DEBIANPATH/sys >/dev/null 2>&1
\$BB mount -t proc proc \$DEBIANPATH/proc >/dev/null 2>&1
\$BB mount -t devpts devpts \$DEBIANPATH/dev/pts >/dev/null 2>&1
mkdir -p \$DEBIANPATH/dev/shm
\$BB mount -t tmpfs -o size=512M tmpfs \$DEBIANPATH/dev/shm >/dev/null 2>&1
mkdir -p \$DEBIANPATH/tmp
\$BB mount --bind /data/data/com.termux/files/usr/tmp \$DEBIANPATH/tmp >/dev/null 2>&1
mkdir -p \$DEBIANPATH/sdcard
\$BB mount --bind /sdcard \$DEBIANPATH/sdcard >/dev/null 2>&1

# 2. Execute Command
CMD="\$@"
if [ -z "\$CMD" ]; then
    echo "Usage: \$0 <command>"
    exit 1
fi

\$BB chroot \$DEBIANPATH /bin/su - root -c "\$CMD"
EOF
    chmod +x "$ROOT_RUNNER"
    success "Root runner created."

    # --- GENERATE CLI LAUNCHER (Shell Only) ---
    CLI_SCRIPT="/data/local/tmp/enter_debian13.sh"
    progress "Creating CLI Launcher at $CLI_SCRIPT..."
    
    cat <<EOF > "$CLI_SCRIPT"
#!/bin/sh
# CLI Entry for Debian 13 Chroot

# Path of DEBIAN rootfs
DEBIANPATH="/data/local/tmp/chrootDebian13"
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
\$BB mount -t tmpfs -o size=512M tmpfs \$DEBIANPATH/dev/shm

# Mount sdcard

# Mount Termux tmp to chroot tmp (for X11 sockets)
mkdir -p \$DEBIANPATH/tmp
\$BB mount --bind /data/data/com.termux/files/usr/tmp \$DEBIANPATH/tmp
mkdir -p \$DEBIANPATH/sdcard
\$BB mount --bind /sdcard \$DEBIANPATH/sdcard

# Enter Shell
echo "Entering Debian 13 Chroot (CLI)..."
\$BB chroot \$DEBIANPATH /bin/su - $USERNAME
EOF
    chmod +x "$CLI_SCRIPT"
    success "CLI Launcher created: $CLI_SCRIPT"
    
    # --- GENERATE ROOT CLI LAUNCHER (Shell as Root) ---
    ROOT_CLI_SCRIPT="/data/local/tmp/enter_debian13_root.sh"
    progress "Creating Root CLI Launcher at $ROOT_CLI_SCRIPT..."
    
    cat <<EOF > "$ROOT_CLI_SCRIPT"
#!/bin/sh
# Root CLI Entry for Debian 13 Chroot

# Path of DEBIAN rootfs
DEBIANPATH="/data/local/tmp/chrootDebian13"
BB="$BB"
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH

# Fix setuid issue
\$BB mount -o remount,dev,suid /data 2>/dev/null

\$BB mount --bind /dev \$DEBIANPATH/dev 2>/dev/null
\$BB mount --bind /sys \$DEBIANPATH/sys 2>/dev/null
\$BB mount -t proc proc \$DEBIANPATH/proc 2>/dev/null
\$BB mount -t devpts devpts \$DEBIANPATH/dev/pts 2>/dev/null

# /dev/shm for Electron apps
mkdir -p \$DEBIANPATH/dev/shm
\$BB mount -t tmpfs -o size=512M tmpfs \$DEBIANPATH/dev/shm 2>/dev/null

# Mount Termux tmp to chroot tmp (for X11 sockets)
mkdir -p \$DEBIANPATH/tmp
\$BB mount --bind /data/data/com.termux/files/usr/tmp \$DEBIANPATH/tmp 2>/dev/null
mkdir -p \$DEBIANPATH/sdcard
\$BB mount --bind /sdcard \$DEBIANPATH/sdcard 2>/dev/null

# Enter Shell as Root with proper PATH
echo "Entering Debian 13 Chroot as ROOT..."
\$BB chroot \$DEBIANPATH /bin/bash -c "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin && exec /bin/bash --login"
EOF
    chmod +x "$ROOT_CLI_SCRIPT"
    success "Root CLI Launcher created: $ROOT_CLI_SCRIPT"
    
    echo "FluxLinux: Chroot Setup Complete!"
    
    # --- NOTIFY APP ---
    progress "Notifying FluxLinux App..."
    am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=distro_install_debian13_chroot" >/dev/null 2>&1
}

main
