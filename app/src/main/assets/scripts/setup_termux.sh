#!/bin/bash
# setup_termux.sh
# Core initialization script for FluxLinux
# Installs necessary dependencies in Termux

echo "FluxLinux: Initializing Termux Environment..."

# 1. Update Packages
pkg update -y && pkg upgrade -y

# 2. Install Core Dependencies
# proot-distro: For rootless containers
# x11-repo: For graphical support
# pulseaudio: For sound
pkg install -y proot-distro x11-repo pulseaudio

# 3. Install Termux:X11
# (Note: Users usually need to install the companion app manually, 
# but the package is needed for the command line tools)
pkg install -y termux-x11-nightly

# 4. Success Marker
echo "FluxLinux: Setup Complete"
