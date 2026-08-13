#!/bin/bash
# setup_debian_family.sh
# Generic Post-install configuration for Debian-based Distros (Debian, Ubuntu, Kali, etc.)

DISTRO_NAME="${1:-debian}"

echo "FluxLinux: Configuring ${DISTRO_NAME} (Debian Family)..."

# 1. Update and Install Core Packages
export DEBIAN_FRONTEND=noninteractive
apt update -y || exit 1
apt install -y sudo xfce4 xfce4-goodies dbus-x11 tigervnc-standalone-server || exit 1

# Stock Mesa for first XFCE paint. hw-accel may replace this with Turnip later.
DEBIAN_FRONTEND=noninteractive apt install -y --no-install-recommends \
    mesa-utils libgl1-mesa-dri libegl1 mesa-vulkan-drivers \
    || DEBIAN_FRONTEND=noninteractive apt install -y --no-install-recommends \
        mesa-utils libgl1-mesa-dri libegl1 \
    || true
mkdir -p /etc/fluxlinux
printf '%s\n' virgl > /etc/fluxlinux/gpu_mode
echo "FluxLinux: gpu_mode=virgl (first-paint; hw-accel may upgrade)"

# 2. Create User 'flux'
if ! id "flux" &>/dev/null; then
    useradd -m -s /bin/bash flux
    echo "flux:flux" | chpasswd
    usermod -aG sudo flux
fi

# 3. Configure Sudo
echo "flux ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/flux

# 4. Configure VNC for User
mkdir -p /home/flux/.vnc
echo "#!/bin/bash
export PULSE_SERVER=127.0.0.1
xrdb $HOME/.Xresources
startxfce4" > /home/flux/.vnc/xstartup
chmod +x /home/flux/.vnc/xstartup
chown -R flux:flux /home/flux/.vnc

echo "FluxLinux: ${DISTRO_NAME} Setup Complete!"
