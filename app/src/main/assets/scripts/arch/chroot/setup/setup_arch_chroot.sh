#!/bin/sh
# setup_arch_chroot.sh
# Installs Arch Linux Chroot (ARM64) with Hyprland
# Requires Root (su)

# Global Variables
ARCHPATH="/data/local/tmp/chrootArch"
USERNAME="flux"
# Used for Hyprland 3D acceleration
VIRGL_SERVER="virgl_test_server_android"

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
    $BB umount "$ARCHPATH/media/sdcard" 2>/dev/null
    $BB umount "$ARCHPATH/dev/shm" 2>/dev/null
    $BB umount "$ARCHPATH/dev/pts" 2>/dev/null
    $BB umount "$ARCHPATH/proc" 2>/dev/null
    $BB umount "$ARCHPATH/sys" 2>/dev/null
    $BB umount "$ARCHPATH/dev" 2>/dev/null
    $BB umount "$ARCHPATH/tmp" 2>/dev/null
}

# Exit handler
goodbye() {
    error "Something went wrong."
    cleanup_mounts
    error "Exiting..."
    exit 1
}

main() {
    # Busybox Detection
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

    
    progress "Using Root Busybox: $BB"
    
    # 1. Directory Setup
    if [ ! -d "$ARCHPATH" ]; then
        mkdir -p "$ARCHPATH"
        success "Created directory: $ARCHPATH"
    fi
    
    # 2. Download RootFS
    URL="http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
    FILE="ArchLinuxARM-aarch64-latest.tar.gz"
    
    if [ ! -f "$ARCHPATH/bin/bash" ]; then
        if [ -f "/sdcard/Download/$FILE" ]; then
            progress "Found manual file, copying..."
            cp "/sdcard/Download/$FILE" "$ARCHPATH/$FILE"
        elif [ ! -f "$ARCHPATH/$FILE" ]; then
            progress "Downloading Arch Linux ARM64 RootFS..."
            wget -O "$ARCHPATH/$FILE" "$URL" || $BB wget -O "$ARCHPATH/$FILE" "$URL" || goodbye
        fi
        
        # 3. Extract
        if [ -f "$ARCHPATH/$FILE" ]; then
             progress "Extracting RootFS (This may take a while)..."
             tar xpvf "$ARCHPATH/$FILE" -C "$ARCHPATH" --numeric-owner >/dev/null 2>&1 || goodbye
             rm "$ARCHPATH/$FILE"
             success "Extraction Complete."
        fi
    else
        success "Arch Linux appears to be installed. Skipping download/extract."
    fi
    
    # 4. Mounts
    progress "Mounting filesystems..."
    $BB mount -o remount,dev,suid /data

    # Create mount points if missing
    mkdir -p "$ARCHPATH/dev" "$ARCHPATH/sys" "$ARCHPATH/proc" "$ARCHPATH/dev/pts" "$ARCHPATH/dev/shm" "$ARCHPATH/media/sdcard" "$ARCHPATH/tmp"

    $BB mount --bind /dev "$ARCHPATH/dev" || /system/bin/mount --bind /dev "$ARCHPATH/dev" || goodbye
    $BB mount --bind /sys "$ARCHPATH/sys" || /system/bin/mount --bind /sys "$ARCHPATH/sys" || goodbye
    $BB mount -t proc proc "$ARCHPATH/proc" || /system/bin/mount -t proc proc "$ARCHPATH/proc" || goodbye
    $BB mount -t devpts devpts "$ARCHPATH/dev/pts" || /system/bin/mount -t devpts devpts "$ARCHPATH/dev/pts" || goodbye
    $BB mount -t tmpfs -o size=256M tmpfs "$ARCHPATH/dev/shm" || /system/bin/mount -t tmpfs -o size=256M tmpfs "$ARCHPATH/dev/shm" || goodbye
    $BB mount --bind /sdcard "$ARCHPATH/media/sdcard" || /system/bin/mount --bind /sdcard "$ARCHPATH/media/sdcard" || goodbye

    # 5. Network & Config
    mkdir -p "$ARCHPATH/etc"
    chmod 755 "$ARCHPATH/etc"
    
    echo "nameserver 8.8.8.8" > "$ARCHPATH/etc/resolv.conf"
    echo "nameserver 1.1.1.1" >> "$ARCHPATH/etc/resolv.conf"
    echo "nameserver 9.9.9.9" >> "$ARCHPATH/etc/resolv.conf"
    echo "127.0.0.1 localhost" > "$ARCHPATH/etc/hosts"
    
    chmod 644 "$ARCHPATH/etc/resolv.conf"
    chmod 644 "$ARCHPATH/etc/hosts"
    
    # 6. Chroot Configuration
    progress "Configuring Arch Linux..."
    
    # Disable CheckSpace in pacman.conf (Required for chroot)
    sed -i 's/^CheckSpace/#CheckSpace/g' "$ARCHPATH/etc/pacman.conf"

    # [CRITICAL Fix] Disable Pacman Sandbox (Landlock not supported on Android Kernels)
    sed -i 's/^#DisableSandbox/DisableSandbox/g' "$ARCHPATH/etc/pacman.conf"
    
    # [Fix] Dynamic Mirror Selection (Rank and choose fastest/available)
    echo "Finding best Arch Linux ARM mirror (checking 15+ mirrors)..."
    
    # Extensive List of Mirrors (Global, US, EU, Asia, Kernel.org)
    MIRRORS="
    http://mirrors.kernel.org/archlinuxarm
    http://fl.us.mirror.archlinuxarm.org
    http://ca.us.mirror.archlinuxarm.org
    http://nj.us.mirror.archlinuxarm.org
    http://mn.us.mirror.archlinuxarm.org
    http://de.mirror.archlinuxarm.org
    http://de3.mirror.archlinuxarm.org
    http://eu.mirror.archlinuxarm.org
    http://dk.mirror.archlinuxarm.org
    http://mirrors.tuna.tsinghua.edu.cn/archlinuxarm
    http://mirrors.ustc.edu.cn/archlinuxarm
    http://mirror.csclub.uwaterloo.ca/archlinuxarm
    http://ftp.jaist.ac.jp/pub/Linux/ArchLinuxARM
    http://mirror.archlinuxarm.org
    "
    
    BEST_MIRROR=""
    
    # Simple race: Pick first one that responds to a HEAD request within 1 second
    for m in $MIRRORS; do
        printf "."
        if $BB timeout 1 $BB wget -q --spider "$m/aarch64/core/core.db"; then
            echo " Found: $m"
            BEST_MIRROR="$m"
            break
        elif timeout 1 wget -q --spider "$m/aarch64/core/core.db"; then
             # Standard wget fallback
            echo " Found: $m"
            BEST_MIRROR="$m"
            break
        fi
    done
    echo ""
    
    if [ -z "$BEST_MIRROR" ]; then
        echo "Warning: No fast mirrors found. Defaulting to global redirector."
        BEST_MIRROR="http://mirror.archlinuxarm.org"
    fi
    
    echo "Selected Mirror: $BEST_MIRROR"
    
    # Write to mirrorlist (Best first, then others (randomized/original order) as backup)
    echo "Server = $BEST_MIRROR/\$arch/\$repo" > "$ARCHPATH/etc/pacman.d/mirrorlist"
    for m in $MIRRORS; do
        if [ "$m" != "$BEST_MIRROR" ]; then
            echo "Server = $m/\$arch/\$repo" >> "$ARCHPATH/etc/pacman.d/mirrorlist"
        fi
    done
    
    
    # 7. Execute Setup (Using bash to inherit aid_inet groups from parent)
    $BB chroot "$ARCHPATH" /bin/bash -c '
        # Groups Setup (CRITICAL for Android Network)
        echo "Configuring Android Permissions..."
        groupadd -g 3003 aid_inet 2>/dev/null
        groupadd -g 3004 aid_net_raw 2>/dev/null
        groupadd -g 1003 aid_graphics 2>/dev/null
        
        # Add root to these groups so future "su -" works
        usermod -a -G aid_inet,aid_net_raw,aid_graphics root
        
        # Verify permissions
        echo "Current User Groups:"
        id
        
        # Network Debug
        echo "Checking Network inside Chroot..."
        ping -c 1 8.8.8.8 >/dev/null 2>&1 && echo " - Ping 8.8.8.8: OK" || echo " - Ping 8.8.8.8: FAIL"
        ping -c 1 google.com >/dev/null 2>&1 && echo " - DNS Resolve (google.com): OK" || echo " - DNS Resolve: FAIL (Using mirrors anyway)"


        
        # Pacman Init
        echo "Initializing Pacman Keys..."
        rm -rf /etc/pacman.d/gnupg
        pacman-key --init
        pacman-key --populate archlinuxarm
        
        # Update & Install Tools
        # We assume network is working via binds
        echo "Updating System..."
        pacman -Syu --noconfirm
        
        echo "Installing Essential Tools..."
        pacman -S --noconfirm vim net-tools sudo git base-devel
        
        # Create User
        echo "Creating user $USERNAME..."
        groupadd storage
        groupadd wheel
        id -u flux >/dev/null 2>&1 || useradd -m -g users -G wheel,audio,video,storage,aid_inet -s /bin/bash flux
        echo "flux:flux" | chpasswd
        
        # Sudoers
        echo "flux ALL=(ALL:ALL) NOPASSWD:ALL" > /etc/sudoers.d/flux
        chmod 0440 /etc/sudoers.d/flux
        
        # Locales
        echo "en_US.UTF-8 UTF-8" > /etc/locale.gen
        locale-gen
        echo "LANG=en_US.UTF-8" > /etc/locale.conf
        
        # KDE PLASMA INSTALLATION
        echo "Installing KDE Plasma..."
        # Installing minimal Plasma Desktop + tools requested
        pacman -S --noconfirm plasma-desktop kwin-x11 kde-graphics kde-utilities konsole thunar dbus xorg-xinit
    ' || goodbye
    
    success "Arch Environment Configured!"
    
    # --- LAUNCH SCRIPTS ---
    
    # 1. CLI Launcher (enter_arch.sh)
    CLI_SCRIPT="/data/local/tmp/enter_arch.sh"
    cat <<EOF > "$CLI_SCRIPT"
#!/bin/sh
ARCHPATH="/data/local/tmp/chrootArch"
BB="$BB"

\$BB mount -o remount,dev,suid /data
\$BB mount --bind /dev \$ARCHPATH/dev
\$BB mount --bind /sys \$ARCHPATH/sys
\$BB mount -t proc proc \$ARCHPATH/proc
\$BB mount -t devpts devpts \$ARCHPATH/dev/pts
mkdir -p \$ARCHPATH/dev/shm
\$BB mount -t tmpfs -o size=256M tmpfs \$ARCHPATH/dev/shm
mkdir -p \$ARCHPATH/media/sdcard
\$BB mount --bind /sdcard \$ARCHPATH/media/sdcard

echo "Entering Arch (CLI)..."
\$BB chroot \$ARCHPATH /bin/su - flux
EOF
    chmod +x "$CLI_SCRIPT"
    success "CLI Launcher Created: $CLI_SCRIPT"

    # 2. KDE Launcher (start_arch_gui.sh)
    GUI_SCRIPT="/data/local/tmp/start_arch_gui.sh"
    TERMUX_PREFIX="/data/data/com.termux/files/usr"
    
    cat <<EOF > "$GUI_SCRIPT"
#!/bin/sh
# Starts KDE Plasma via Termux-X11 and VirGL
# Based on LinuxDroidMaster guide

# Kill old processes
killall -9 termux-x11 Xwayland pulseaudio virgl_test_server_android 2>/dev/null

# Start Termux-X11
am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity >/dev/null

# Mount Tmp for VirGL socket sharing
$BB mount --bind $TERMUX_PREFIX/tmp /data/local/tmp/chrootArch/tmp 2>/dev/null

# Fix Permissions for VirGL
chmod -R 1777 $TERMUX_PREFIX/tmp

# Start XServer
export XDG_RUNTIME_DIR="$TERMUX_PREFIX/tmp"
export TMPDIR="\$XDG_RUNTIME_DIR"
$TERMUX_PREFIX/bin/termux-x11 :0 -ac &

sleep 3

# Start Audio
$TERMUX_PREFIX/bin/pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1
$TERMUX_PREFIX/bin/pacmd load-module module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1

# Start VirGL (3D Acceleration)
echo "Starting 3D Acceleration (VirGL)..."
$TERMUX_PREFIX/bin/virgl_test_server_android &
sleep 2

# Launch KDE Plasma
echo "Starting KDE Plasma..."
$BB chroot /data/local/tmp/chrootArch /bin/su - flux -c '
    export DISPLAY=:0 
    export PULSE_SERVER=tcp:127.0.0.1
    export GALLIUM_DRIVER=virpipe 
    export MESA_GL_VERSION_OVERRIDE=4.0
    
    dbus-launch --exit-with-session startplasma-x11
'
EOF
    chmod +x "$GUI_SCRIPT"
    success "GUI Launcher Created: $GUI_SCRIPT"
    
    cleanup_mounts
    
    # Notify App
    am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=distro_install_arch_chroot" >/dev/null 2>&1
}

main
