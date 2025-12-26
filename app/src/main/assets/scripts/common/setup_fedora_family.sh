#!/bin/bash
# setup_fedora_family.sh
# Generic Post-install configuration for Fedoa-based Distros

DISTRO_NAME="${1:-fedora}"

echo "FluxLinux: Configuring ${DISTRO_NAME} (Fedora Family)..."

# 1. Update and Install Core Packages
dnf update -y || exit 1
dnf groupinstall -y "Xfce Desktop" || exit 1
dnf install -y tigervnc-server dbus-x11 useradd sudo || exit 1

# 2. Create User 'flux'
if ! id "flux" &>/dev/null; then
    useradd -m -s /bin/bash flux
    echo "flux:flux" | chpasswd
    usermod -aG wheel flux
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
