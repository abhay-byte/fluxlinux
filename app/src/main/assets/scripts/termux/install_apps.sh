#!/bin/bash
# scripts/termux/install_apps.sh
# Installs Firefox and VSCode (Code OSS) via tur-repo

echo "FluxLinux Pro: Installing Utilities..."

# Install tur-repo (Termux User Repository)
pkg install -y tur-repo

# Install Firefox
echo "Installing Firefox..."
pkg install -y firefox

# Install VSCode (Code OSS)
echo "Installing VS Code..."
pkg install -y code-oss

echo "FluxLinux Pro: Apps Installed Successfully!"
