#!/bin/bash
# debian_setup.sh
# Post-install configuration for FluxLinux Debian

MARKER_FILE="$HOME/.fluxlinux/debian_setup.done"
mkdir -p "$HOME/.fluxlinux"

if [ -f "$MARKER_FILE" ]; then
    echo "FluxLinux: Debian Setup already completed. Skipping."
    am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=debian_setup"
    exit 0
fi

# Trap errors
set -e
trap 'am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=failure&name=debian_setup"' ERR

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
touch "$MARKER_FILE"
am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=debian_setup"
