#!/bin/bash
# setup_debian_family.sh
# Generic Post-install configuration for Debian-based Distros (Debian, Ubuntu, Kali, etc.)

DISTRO_NAME="${1:-debian}" # Default to debian if no arg provided
MARKER_FILE="$HOME/.fluxlinux/${DISTRO_NAME}_setup.done"
mkdir -p "$HOME/.fluxlinux"

if [ -f "$MARKER_FILE" ]; then
    echo "FluxLinux: ${DISTRO_NAME} Setup already completed. Skipping."
    am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=${DISTRO_NAME}_setup"
    exit 0
fi

# Trap errors
set -e
trap 'am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=failure&name=${DISTRO_NAME}_setup"' ERR

echo "FluxLinux: Configuring ${DISTRO_NAME} (Debian Family)..."

# 1. Update and Install Core Packages
# DEBIAN_FRONTEND=noninteractive prevents prompts
export DEBIAN_FRONTEND=noninteractive
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

echo "FluxLinux: ${DISTRO_NAME} Configuration Complete!"
touch "$MARKER_FILE"
am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=${DISTRO_NAME}_setup"
