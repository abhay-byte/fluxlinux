#!/bin/bash
# debian_setup.sh
# Post-install configuration for FluxLinux Debian

export DEBIAN_FRONTEND=noninteractive

echo "FluxLinux: Configuring Debian..."

# 1. Update and Install Core Packages
apt update -y
apt install -y sudo xfce4 xfce4-goodies dbus-x11

# 2. Create User 'flux'
if id "flux" &>/dev/null; then
    echo "User 'flux' already exists."
else
    useradd -m -s /bin/bash flux
    echo "flux:flux" | chpasswd
    echo "User 'flux' created with password 'flux'."
fi

# 3. Grant Sudo Privileges
usermod -aG sudo flux
mkdir -p /etc/sudoers.d
echo "flux ALL=(ALL:ALL) NOPASSWD:ALL" > /etc/sudoers.d/flux

echo "FluxLinux: Configuration Complete!"
