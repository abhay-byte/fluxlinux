#!/bin/bash
# scripts/termux/setup_theme.sh
# Installs Nerd Fonts for terminal icons

echo "FluxLinux: Installing Nerd Fonts..."

# Install dependencies
pkg install -y curl ncurses-utils zip

# Run automatic installer from termux-nf repo
curl -fsSL https://raw.githubusercontent.com/arnavgr/termux-nf/main/install.sh | bash

echo "FluxLinux: Nerd Fonts Installed!"
