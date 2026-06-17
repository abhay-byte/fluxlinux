#!/bin/bash
# scripts/termux/install.sh
# Native XFCE4 installation for Termux

# Enable strict error handling
set -e

echo "FluxLinux Pro: Installing XFCE4 for Termux Native..."

# Update packages
echo "FluxLinux Pro: Updating packages..."
pkg update -y

# Install XFCE4 and essential tools
echo "FluxLinux Pro: Installing XFCE4..."
pkg install -y xfce4 xfce4-terminal tigervnc

echo "FluxLinux Pro: XFCE4 Installed Successfully."
