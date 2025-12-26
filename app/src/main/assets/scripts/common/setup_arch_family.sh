#!/bin/bash
# setup_arch_family.sh
# Installs XFCE4 and VNC on Arch Linux (via Proot)

DISTRO=$1
echo "FluxLinux: Setting up Arch Linux ($DISTRO)..."

# 1. Optimize Pacman (Speed Boost)
# Enable Parallel Downloads
sed -i 's/^#ParallelDownloads/ParallelDownloads/' /etc/pacman.conf

# 1. Optimize Pacman (Speed Boost & Dynamic Mirror Selection)
# Enable Parallel Downloads
sed -i 's/^#ParallelDownloads/ParallelDownloads/' /etc/pacman.conf

echo "FluxLinux: Finding fastest mirrors (this may take a moment)..."

# List of potential high-speed mirrors (Global spread)
MIRRORS=(
    "http://mirror.archlinuxarm.org"        # Official Redirector
    "http://mirror.leaseweb.com/archlinuxarm" # US/EU
    "http://mirrors.dotsrc.org/archlinuxarm"  # EU
    "http://ftp.halifax.rwth-aachen.de/archlinuxarm" # DE
    "http://mirrors.ustc.edu.cn/archlinuxarm" # CN
    "http://mirror.umd.edu/archlinuxarm"      # US
    "http://ftp.jaist.ac.jp/pub/archlinuxarm" # JP
    "http://mirror.csclub.uwaterloo.ca/archlinuxarm" # CA
    "http://mirror.nl.leaseweb.net/archlinuxarm" # NL
)

# Test mirrors and find the fastest
BEST_MIRROR=""
BEST_TIME=9999

for url in "${MIRRORS[@]}"; do
    # Test connection time (only headers)
    TIME=$(curl -o /dev/null -s -w "%{time_total}" --connect-timeout 2 --head "$url/aarch64/core/core.db")
    if [ $? -eq 0 ]; then
        # echo "Mirror: $url - Time: $TIME" # Debug
        is_faster=$(echo "$TIME < $BEST_TIME" | bc -l)
        if [ "$is_faster" -eq 1 ]; then
            BEST_TIME=$TIME
            BEST_MIRROR=$url
        fi
    fi
done

if [ -n "$BEST_MIRROR" ]; then
    echo "FluxLinux: Fastest mirror selected: $BEST_MIRROR ($BEST_TIME s)"
    echo "Server = $BEST_MIRROR/\$arch/\$repo" > /etc/pacman.d/mirrorlist
    # Add official redirector as backup
    echo "Server = http://mirror.archlinuxarm.org/\$arch/\$repo" >> /etc/pacman.d/mirrorlist
    echo "Server = http://mirror.leaseweb.com/archlinuxarm/\$arch/\$repo" >> /etc/pacman.d/mirrorlist
else
    echo "FluxLinux: Could not rank mirrors, using defaults."
    echo "Server = http://mirror.archlinuxarm.org/\$arch/\$repo" > /etc/pacman.d/mirrorlist
fi

# 2. Update Pacman
pacman -Sy --noconfirm || exit 1

# 2. Install XFCE4 and Essentials
# xfce4: Desktop Environment
# tigervnc: VNC Server
# dbus: Required for XFCE
# ttf-dejavu: Basic fonts
# sudo: For user privileges
pacman -S --noconfirm xfce4 tigervnc dbus ttf-dejavu sudo || exit 1

# 3. Create User if not exists (flux)
if ! id "flux" &>/dev/null; then
    useradd -m -s /bin/bash flux
    echo "flux:flux" | chpasswd
    echo "flux ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers
fi

# 4. Configure VNC for User
mkdir -p /home/flux/.vnc
echo "#!/bin/bash
export PULSE_SERVER=127.0.0.1
xrdb $HOME/.Xresources
startxfce4" > /home/flux/.vnc/xstartup
chmod +x /home/flux/.vnc/xstartup
chown -R flux:flux /home/flux/.vnc

echo "FluxLinux: Arch Setup Complete!"
