#!/bin/bash
# setup_webdev_debian.sh
# Installs Web Development stack (Node, Python, VS Code, Browsers) on Debian-based distros.

# Error Handler Function to pause and let user read logs
handle_error() {
    echo ""
    echo "❌ FluxLinux Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    echo "Please check the error message above."
    echo "You can copy the error output to share with support."
    echo "---------------------------------------------------"
    read -p "Press Enter to exit..."
    exit 1
}

echo "FluxLinux: Setting up Web Development Environment..."

# 1. Update & Install Basic Tools
apt update -y || handle_error "System Update"
apt install -y curl wget git build-essential gnupg || handle_error "Basic Tools Installation"

# 2. Install Browsers (Firefox & Chromium)
echo "FluxLinux: Installing Browsers..."
apt install -y firefox-esr chromium || handle_error "Browser Installation"

# 3. Install Node.js (LTS v20)
if ! command -v node &> /dev/null; then
    echo "FluxLinux: Installing Node.js 20.x..."
    mkdir -p /etc/apt/keyrings
    curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg
    echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_20.x nodistro main" | tee /etc/apt/sources.list.d/nodesource.list
    apt update -y
    apt install -y nodejs || handle_error "Node.js Installation"
else
    echo "FluxLinux: Node.js already installed."
fi

# 4. Install Python
echo "FluxLinux: Installing Python..."
apt install -y python3 python3-pip python3-venv || handle_error "Python Installation"

# 5. Install VS Code (Official)
if ! command -v code &> /dev/null; then
    echo "FluxLinux: Installing VS Code..."
    wget -qO- https://packages.microsoft.com/keys/microsoft.asc | gpg --dearmor > packages.microsoft.gpg
    install -D -o root -g root -m 644 packages.microsoft.gpg /etc/apt/keyrings/packages.microsoft.gpg
    sh -c 'echo "deb [arch=amd64,arm64,armhf] [signed-by=/etc/apt/keyrings/packages.microsoft.gpg] https://packages.microsoft.com/repos/code stable main" > /etc/apt/sources.list.d/vscode.list'
    rm -f packages.microsoft.gpg
    
    apt update -y
    apt install -y code || handle_error "VS Code Installation"
    
    # Fix for running VS Code in Proot (requires --no-sandbox)
    # We create a wrapper alias for the 'flux' user
    echo "alias code='code --no-sandbox --unity-launch'" >> /home/flux/.bashrc
    
    # Also patch the desktop entry if it exists so the menu icon works
    if [ -f /usr/share/applications/code.desktop ]; then
        sed -i 's|Exec=/usr/share/code/code|Exec=/usr/share/code/code --no-sandbox|g' /usr/share/applications/code.desktop
    fi
else
    echo "FluxLinux: VS Code already installed."
fi

echo "FluxLinux: Web Development Setup Complete!"
echo "Note: Launch VS Code with 'code' in terminal (alias added)."
