#!/bin/bash
# setup_termux.sh
# Core initialization script for FluxLinux
# Installs necessary dependencies in Termux

MARKER_FILE="$HOME/.fluxlinux/setup_termux.done"
mkdir -p "$HOME/.fluxlinux"

if [ -f "$MARKER_FILE" ]; then
    echo "FluxLinux: Termux Setup already completed. Skipping."
    am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=setup_termux"
    exit 0
fi

# Trap errors
set -e
trap 'am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=failure&name=setup_termux"' ERR

echo "FluxLinux: Initializing Termux Environment..."

# 1. Update Packages
pkg update -y && pkg upgrade -y

# 2. Install Core Dependencies
# proot-distro: For rootless containers
# x11-repo: For graphical support
# pulseaudio: For sound
# wget: For downloading scripts
# zsh: Enhanced shell (for Oh My Zsh)
# fastfetch: System info display
# git: Version control (for Oh My Zsh plugins)
# unzip: For extracting fonts
pkg install -y proot-distro x11-repo pulseaudio wget zsh fastfetch git unzip

# 3. Install Termux:X11
pkg install -y termux-x11-nightly

# 4. (Scripts now deployed separately via app logic)
# - start_gui.sh (Specific to distro family)
# - flux_install.sh (Common installer)

echo "FluxLinux: Setup Complete"
echo ""
echo "📝 Optional: Run 'bash ~/termux_tweaks.sh' for enhanced terminal experience"

# Create marker file to track initialization
touch "$MARKER_FILE"
am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=setup_termux"
