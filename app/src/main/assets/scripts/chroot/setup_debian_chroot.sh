#!/bin/sh

# setup_debian_chroot.sh
# Installs a Debian Chroot environment (Requires Root)
# Based on LinuxDroidMaster/Termux-Desktops

# Function to show farewell message
# Function to unmount everything safely on failure
cleanup_mounts() {
    progress "Safety Check: Unmounting filesystems..."
    # Unmount in reverse order of mounting to avoid dependencies
    busybox umount "$DEBIANPATH/sdcard" 2>/dev/null
    busybox umount "$DEBIANPATH/dev/shm" 2>/dev/null
    busybox umount "$DEBIANPATH/dev/pts" 2>/dev/null
    busybox umount "$DEBIANPATH/proc" 2>/dev/null
    busybox umount "$DEBIANPATH/sys" 2>/dev/null
    busybox umount "$DEBIANPATH/dev" 2>/dev/null
}

# Function to show farewell message
goodbye() {
    echo -e "\e[1;31m[!] Something went wrong.\e[0m"
    cleanup_mounts
    echo -e "\e[1;31m[!] Exiting...\e[0m"
    exit 1
}

# Function to show progress message
progress() {
    echo -e "\e[1;36m[+] $1\e[0m"
}

# Function to show success message
success() {
    echo -e "\e[1;32m[✓] $1\e[0m"
}

# Function to download file
download_file() {
    progress "Downloading file..."
    if [ -e "$1/$2" ]; then
        echo -e "\e[1;33m[!] File already exists: $2\e[0m"
        echo -e "\e[1;33m[!] Skipping download...\e[0m"
    else
        wget -O "$1/$2" "$3"
        if [ $? -eq 0 ]; then
            success "File downloaded successfully: $2"
        else
            echo -e "\e[1;31m[!] Error downloading file: $2.\e[0m"
            goodbye
        fi
    fi
}

# Function to extract file
extract_file() {
    progress "Extracting file..."
    if [ -d "$1/debian12-arm64" ]; then
        echo -e "\e[1;33m[!] Directory already exists: $1/debian12-arm64\e[0m"
        echo -e "\e[1;33m[!] Skipping extraction...\e[0m"
    else
        tar xpvf "$1/debian12-arm64.tar.gz" -C "$1" --numeric-owner >/dev/null 2>&1
        if [ $? -eq 0 ]; then
            success "File extracted successfully: $1/debian12-arm64"
        else
            echo -e "\e[1;31m[!] Error extracting file.\e[0m"
            goodbye
        fi
    fi
}

# Function to configure Debian chroot environment
configure_debian_chroot() {
    progress "Configuring Debian chroot environment..."
    DEBIANPATH="/data/local/tmp/chrootDebian"

    # Check if DEBIANPATH directory exists
    if [ ! -d "$DEBIANPATH" ]; then
        mkdir -p "$DEBIANPATH"
        if [ $? -eq 0 ]; then
            success "Created directory: $DEBIANPATH"
        else
            echo -e "\e[1;31m[!] Error creating directory: $DEBIANPATH.\e[0m"
            goodbye
        fi
    fi

    # Mount necessary filesystems
    progress "Mounting filesystems..."
    busybox mount -o remount,dev,suid /data
    busybox mount --bind /dev $DEBIANPATH/dev || goodbye
    busybox mount --bind /sys $DEBIANPATH/sys || goodbye
    busybox mount --bind /proc $DEBIANPATH/proc || goodbye
    busybox mount -t devpts devpts $DEBIANPATH/dev/pts || goodbye

    mkdir -p $DEBIANPATH/dev/shm
    busybox mount -t tmpfs -o size=256M tmpfs $DEBIANPATH/dev/shm || goodbye

    mkdir -p $DEBIANPATH/sdcard
    busybox mount --bind /sdcard $DEBIANPATH/sdcard || goodbye
    
    # Configure networking and users inside chroot
    busybox chroot $DEBIANPATH /bin/su - root -c 'apt update -y && apt upgrade -y'
    busybox chroot $DEBIANPATH /bin/su - root -c 'echo "nameserver 8.8.8.8" > /etc/resolv.conf; \
    echo "127.0.0.1 localhost" > /etc/hosts; \
    groupadd -g 3003 aid_inet; \
    groupadd -g 3004 aid_net_raw; \
    groupadd -g 1003 aid_graphics; \
    usermod -g 3003 -G 3003,3004 -a _apt; \
    usermod -G 3003 -a root; \
    apt update; \
    apt upgrade; \
    apt install -y nano vim net-tools sudo git dbus-x11 xfce4 xfce4-terminal; \
    echo "Debian chroot environment configured"'

    if [ $? -eq 0 ]; then
        success "Debian chroot environment configured"
    else
        echo -e "\e[1;31m[!] Error configuring Debian chroot environment.\e[0m"
        goodbye
    fi

    # Setup default user 'flux'
    USERNAME="flux"
    progress "Setting up user account ($USERNAME)..."

    # Add the user if not exists
    busybox chroot $DEBIANPATH /bin/su - root -c "id -u $USERNAME >/dev/null 2>&1 || adduser --disabled-password --gecos \"\" $USERNAME"

    # Add user to sudoers
    progress "Configuring sudo permissions..."
    busybox chroot $DEBIANPATH /bin/su - root -c "echo '$USERNAME ALL=(ALL:ALL) NOPASSWD:ALL' >> /etc/sudoers"
    busybox chroot $DEBIANPATH /bin/su - root -c "usermod -aG aid_inet $USERNAME"

    success "User account set up and sudo permissions configured"
    
    # Create Launch Script for future use
    LAUNCH_SCRIPT="/data/local/tmp/start_debian.sh"
    progress "Creating launch script at $LAUNCH_SCRIPT..."
    
    cat <<EOF > "$LAUNCH_SCRIPT"
#!/bin/sh
DEBIANPATH="/data/local/tmp/chrootDebian"

# Mounts
busybox mount -o remount,dev,suid /data
busybox mount --bind /dev \$DEBIANPATH/dev
busybox mount --bind /sys \$DEBIANPATH/sys
busybox mount --bind /proc \$DEBIANPATH/proc
busybox mount -t devpts devpts \$DEBIANPATH/dev/pts

mkdir -p \$DEBIANPATH/dev/shm
busybox mount -t tmpfs -o size=256M tmpfs \$DEBIANPATH/dev/shm

mkdir -p \$DEBIANPATH/sdcard
busybox mount --bind /sdcard \$DEBIANPATH/sdcard

# Launch GUI as user
echo "Starting Debian Chroot GUI..."
busybox chroot \$DEBIANPATH /bin/su - $USERNAME -c 'export DISPLAY=:0 && export PULSE_SERVER=127.0.0.1 && dbus-launch --exit-with-session startxfce4'
EOF
    chmod +x "$LAUNCH_SCRIPT"
    success "Launch script created."
    
    # Unmount after setup is done (User will mount again when launching)
    cleanup_mounts
    success "Setup complete. Unmounted filesystems for safety."
}

# Main function
main() {
    if [ "$(id -u)" != "0" ]; then
        echo -e "\e[1;31m[!] This script must be run as root. Exiting...\e[0m"
        exit 1
    fi
    
    # Check for busybox
    if ! command -v busybox >/dev/null; then
         echo -e "\e[1;31m[!] Busybox not found! Install Busybox and retry.\e[0m"
         exit 1
    fi

    download_dir="/data/local/tmp/chrootDebian"
    if [ ! -d "$download_dir" ]; then
        mkdir -p "$download_dir"
        success "Created directory: $download_dir"
    fi
    
    # Download RootFS
    download_file "$download_dir" "debian12-arm64.tar.gz" "https://github.com/LinuxDroidMaster/Termux-Desktops/releases/download/Debian/debian12-arm64.tar.gz"
    
    # Extract
    extract_file "$download_dir"
    
    # Configure
    configure_debian_chroot
    
    echo "FluxLinux: Chroot Setup Complete!"
}

main
