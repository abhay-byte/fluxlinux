#!/bin/bash
# setup_webdev_debian.sh
# Installs Web Development stack (Node, Python, VS Code, Browsers) on Debian-based distros.
# Usage: setup_webdev_debian.sh [uninstall]

# Packages installed by this component. Used for uninstall.
PKGS=(
    firefox
    chromium
)

# Other artifacts to remove on uninstall (not apt packages):
#   /usr/local/bin/firefox, /usr/local/bin/chromium (wrappers)
#   /usr/share/applications/firefox.desktop, chromium.desktop
#   /home/flux/.local/share/applications/chromium.desktop
#   /opt/nodejs (Node.js tarball install)
#   /usr/local/bin/{node,npm,npx,corepack} (Node.js symlinks)
#   /etc/profile.d/nodejs.sh
#   /usr/share/code (VS Code tarball install)
#   /usr/bin/code (VS Code symlink)
#   /usr/share/applications/code.desktop
#   /home/flux/.config/Code (VS Code user config)
#   /etc/apt/sources.list.d/mozilla.list
#   /etc/apt/preferences.d/mozilla
#   /etc/apt/keyrings/packages.mozilla.org.asc

# ─── UNINSTALL MODE ──────────────────────────────────────────────────────
# If invoked with "uninstall" as the first argument, remove what we installed
# and exit. Called by FluxLinux app from DistroSettings → Component → Uninstall.
if [ "$1" = "uninstall" ]; then
    echo "FluxLinux: Uninstalling Web Development Environment..."

    export DEBIAN_FRONTEND=noninteractive

    # 1. Remove apt packages
    apt remove -y --purge "${PKGS[@]}" 2>/dev/null || true
    apt autoremove -y 2>/dev/null || true

    # 2. Remove Firefox/Chromium wrappers + .desktop files
    rm -f /usr/local/bin/firefox
    rm -f /usr/local/bin/chromium
    rm -f /usr/share/applications/firefox.desktop
    rm -f /usr/share/applications/chromium.desktop
    rm -f /home/flux/.local/share/applications/chromium.desktop
    # (No firefox.desktop in ~/.local/share/applications/ — never created by installer)

    # 3. Remove Node.js (manual tarball install + symlinks)
    rm -rf /opt/nodejs
    rm -f /usr/local/bin/node /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack
    rm -f /etc/profile.d/nodejs.sh

    # 4. Remove VS Code (tarball install + symlink + .desktop + user config)
    rm -rf /usr/share/code
    rm -f /usr/bin/code
    rm -f /usr/share/applications/code.desktop
    rm -rf /home/flux/.config/Code

    # 5. Remove Mozilla apt repo (added by this script)
    rm -f /etc/apt/sources.list.d/mozilla.list
    rm -f /etc/apt/preferences.d/mozilla
    rm -f /etc/apt/keyrings/packages.mozilla.org.asc
    rm -f /etc/apt/keyrings/packages.mozilla.org.asc.sha256
    apt update -y 2>/dev/null || true

    # 6. Revert .bashrc / .zshrc entries added by the installer
    for shell_rc in /home/flux/.bashrc /home/flux/.zshrc; do
        if [ -f "$shell_rc" ]; then
            sed -i '/^# Node.js Global Path$/d' "$shell_rc" 2>/dev/null || true
            sed -i '/^export PATH=\$PATH:\/opt\/nodejs\/bin$/d' "$shell_rc" 2>/dev/null || true
            sed -i "/^alias code='code --no-sandbox --unity-launch'/d" "$shell_rc" 2>/dev/null || true
        fi
    done

    echo "FluxLinux: Web Development Environment Uninstalled."
    exit 0
fi
# ─── END UNINSTALL MODE ──────────────────────────────────────────────────

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

# PRE-FLIGHT CHECK: Clean up broken VS Code repo if present
# This prevents 'apt update' from failing immediately due to parsing errors
rm -f /etc/apt/sources.list.d/vscode.list

# PRE-FLIGHT CHECK: Clean up old NodeSource repo and keys
# The NodeSource repository signature is invalid and causes apt update to fail
echo "FluxLinux: Cleaning up old NodeSource repository..."
rm -f /etc/apt/sources.list.d/nodesource.list
rm -f /etc/apt/sources.list.d/nodesource.list.save
rm -f /usr/share/keyrings/nodesource.gpg
rm -f /etc/apt/keyrings/nodesource.gpg
# Remove any nodesource entries from main sources.list
sed -i '/nodesource/d' /etc/apt/sources.list 2>/dev/null || true

# 1. Update & Install Basic Tools
apt update -y || handle_error "System Update"
apt install -y curl wget git build-essential gnupg || handle_error "Basic Tools Installation"

# 2. Install Browsers (Firefox Latest & Chromium)
echo "FluxLinux: Installing Latest Firefox (Mozilla Repo)..."

# Setup Mozilla Official Repo (Supports ARM64)
mkdir -p /etc/apt/keyrings
wget -q https://packages.mozilla.org/apt/repo-signing-key.gpg -O- | tee /etc/apt/keyrings/packages.mozilla.org.asc > /dev/null

echo "deb [signed-by=/etc/apt/keyrings/packages.mozilla.org.asc] https://packages.mozilla.org/apt mozilla main" | tee /etc/apt/sources.list.d/mozilla.list > /dev/null

# Prioritize Mozilla Repo
echo '
Package: *
Pin: origin packages.mozilla.org
Pin-Priority: 1000
' | tee /etc/apt/preferences.d/mozilla

apt update -y
apt install -y "${PKGS[@]}" || handle_error "Browser Installation"

# Fix Firefox sandbox crashes in PRoot (no user namespaces, /dev is Android bind-mount)
# Wrapper at /usr/local/bin/firefox takes priority over /usr/bin/firefox via PATH
echo "FluxLinux: Applying Firefox proot sandbox fix..."
cat > /usr/local/bin/firefox << 'EOF'
#!/bin/bash
# FluxLinux: Firefox sandbox wrapper for PRoot/chroot on Android
# Firefox's sandbox requires user namespaces + a real /dev — neither available in proot.
# Disabling all sandbox layers prevents child process SIGSEGV (signal 11) crashes.
export MOZ_DISABLE_CONTENT_SANDBOX=1
export MOZ_DISABLE_GMP_SANDBOX=1
export MOZ_DISABLE_RDD_SANDBOX=1
export MOZ_DISABLE_SOCKET_PROCESS_SANDBOX=1
exec /usr/bin/firefox "$@"
EOF
chmod +x /usr/local/bin/firefox

# Update desktop entry to point to wrapper
mkdir -p /usr/share/applications
cat > /usr/share/applications/firefox.desktop << 'EOF'
[Desktop Entry]
Name=Firefox Web Browser
Comment=Browse the World Wide Web
GenericName=Web Browser
Exec=/usr/local/bin/firefox %u
Icon=firefox
Terminal=false
Type=Application
MimeType=text/html;text/xml;application/xhtml+xml;application/vnd.mozilla.xul+xml;x-scheme-handler/http;x-scheme-handler/https;
StartupNotify=true
Categories=Network;WebBrowser;
EOF
echo "FluxLinux: Firefox wrapper applied."

# Fix Chromium GPU/sandbox crashes in PRoot
# --no-sandbox: no user namespaces in proot
# --disable-gpu: GPU process crashes (no /proc/bus/pci, no udev, no usable GPU device)
# --use-gl=swiftshader: software WebGL renderer so pages still render correctly
echo "FluxLinux: Applying Chromium proot sandbox/GPU fix..."
cat > /usr/local/bin/chromium << 'EOF'
#!/bin/bash
# FluxLinux: Chromium wrapper for PRoot/chroot on Android
# Chromium's GPU process exits with SIGABRT (exit code 256) inside proot because
# /proc/bus/pci/devices is missing, no udev, and no user namespaces for sandbox.
exec /usr/bin/chromium --no-sandbox --disable-gpu --use-gl=swiftshader "$@"
EOF
chmod +x /usr/local/bin/chromium

# Override system .desktop so the XFCE app menu also uses the wrapper flags
# User-level overrides take priority over /usr/share/applications/
FLUX_LOCAL_APPS="/home/flux/.local/share/applications"
mkdir -p "$FLUX_LOCAL_APPS"
cat > "$FLUX_LOCAL_APPS/chromium.desktop" << 'EOF'
[Desktop Entry]
Name=Chromium Web Browser
Comment=Access the Internet
GenericName=Web Browser
Exec=/usr/local/bin/chromium %U
Icon=chromium
Terminal=false
Type=Application
MimeType=text/html;text/xml;application/xhtml+xml;x-scheme-handler/http;x-scheme-handler/https;
StartupNotify=true
Categories=Network;WebBrowser;
EOF
chown -R flux:users "$FLUX_LOCAL_APPS" 2>/dev/null || true
echo "FluxLinux: Chromium wrapper applied."


# 3. Install Node.js (v25) -- Manual Install for ARM64 & Global Path fix
NODE_ROOT="/opt/nodejs"
NODE_VER="v25.5.0"
NODE_DIST="node-${NODE_VER}-linux-arm64"
NODE_URL="https://nodejs.org/dist/${NODE_VER}/${NODE_DIST}.tar.xz"

echo "FluxLinux: Installing/Checking Node.js ${NODE_VER}..."
INSTALL_NODE=false

# Check if installed
if [ ! -f "$NODE_ROOT/bin/node" ]; then
    INSTALL_NODE=true
else
    INSTALLED_VER=$("$NODE_ROOT/bin/node" -v 2>/dev/null)
    if [ "$INSTALLED_VER" != "$NODE_VER" ]; then
        echo " - Updating Node.js from $INSTALLED_VER to $NODE_VER..."
        INSTALL_NODE=true
    else
        echo " - Node.js $NODE_VER already installed."
    fi
fi

if [ "$INSTALL_NODE" = true ]; then
    # Clean destination
    rm -rf "$NODE_ROOT"
    mkdir -p "$NODE_ROOT"
    
    # Download & Extract
    echo " - Downloading Node.js..."
    wget -q --show-progress "$NODE_URL" -O /tmp/node.tar.xz || handle_error "Node.js Download"
    tar -xJvf /tmp/node.tar.xz -C "$NODE_ROOT" --strip-components=1 >/dev/null 2>&1 || handle_error "Node.js Extraction"
    rm -f /tmp/node.tar.xz
    
    # Symlinks
    echo " - Creating symlinks..."
    ln -sf "$NODE_ROOT/bin/node" /usr/local/bin/node
    ln -sf "$NODE_ROOT/bin/npm" /usr/local/bin/npm
    ln -sf "$NODE_ROOT/bin/npx" /usr/local/bin/npx
    ln -sf "$NODE_ROOT/bin/corepack" /usr/local/bin/corepack
    
    echo " [✅] Node.js ${NODE_VER} Installed"
fi

# Fix Global NPM Path (Ensure modules are found)
echo "FluxLinux: Configuring Node.js Environment..."

# Update .bashrc for current user
BASHRC="/home/flux/.bashrc"
if ! grep -q "/opt/nodejs/bin" "$BASHRC"; then
    echo "" >> "$BASHRC"
    echo "# Node.js Global Path" >> "$BASHRC"
    echo 'export PATH=$PATH:/opt/nodejs/bin' >> "$BASHRC"
    echo " - Added Node.js to .bashrc"
fi

# System-wide profile
echo 'export PATH=$PATH:/opt/nodejs/bin' > /etc/profile.d/nodejs.sh
chmod 644 /etc/profile.d/nodejs.sh

# 4. Install Python
echo "FluxLinux: Installing Python..."
apt install -y python3 python3-pip python3-venv || handle_error "Python Installation"

# 5. Install VS Code (Official Tarball)
# We use the tarball method to avoid 'dpkg' crashes (double free) likely caused by 
# Debian Trixie's new glibc/dpkg version running under Proot.
if ! command -v code &> /dev/null; then
    echo "FluxLinux: Installing VS Code (Tarball Method)..."
    
    # Clean up broken repo config/files
    rm -f /etc/apt/sources.list.d/vscode.list
    rm -f /tmp/code_arm64.deb
    
    # Install dependencies for the remote cli/electron + DBus/Keyring + Memory Allocator Fix
    apt install -y libx11-xcb1 libxcb-dri3-0 libdrm2 libgbm1 libasound2 dbus-x11 gnome-keyring libtcmalloc-minimal4 || handle_error "VS Code Deps"

    # Download ARM64 Tarball
    # Use the stable link for linux-arm64 archive
    curl -L 'https://update.code.visualstudio.com/latest/linux-arm64/stable' -o /tmp/vscode.tar.gz || handle_error "VS Code Download"
    
    # Create install directory
    mkdir -p /usr/share/code
    
    # Extract
    echo "FluxLinux: Extracting VS Code..."
    tar -xzf /tmp/vscode.tar.gz -C /usr/share/code --strip-components=1 || handle_error "VS Code Extraction"
    
    # Link binary
    ln -sf /usr/share/code/bin/code /usr/bin/code
    
    # Cleanup
    rm -f /tmp/vscode.tar.gz
    
    # Fix for running VS Code in Proot (--no-sandbox wrapper)
    # We append the alias to .bashrc for the flux user
    echo "alias code='code --no-sandbox --unity-launch'" >> /home/flux/.bashrc
    
    # Create Desktop Entry (so it appears in the menu)
    mkdir -p /usr/share/applications
    cat <<EOF > /usr/share/applications/code.desktop
[Desktop Entry]
Name=Visual Studio Code
Comment=Code Editing. Redefined.
GenericName=Text Editor
Exec=/usr/share/code/bin/code --no-sandbox --unity-launch %F
Icon=com.visualstudio.code
Type=Application
StartupNotify=false
StartupWMClass=Code
Categories=TextEditor;Development;IDE;
MimeType=text/plain;inode/directory;application/x-code-workspace;
EOF

    # Download Icon (Optional, keeps UI nice)
    # We'll just rely on system fallback or generic icon if missing, downloading icons manually is flaky.
    
    # Download Icon (Optional, keeps UI nice)
    # We'll just rely on system fallback or generic icon if missing, downloading icons manually is flaky.
    
else
    echo "FluxLinux: VS Code already installed."
fi

# Configure VS Code settings to disable extension signature verification
# This runs every time to ensure settings are always applied
echo "FluxLinux: Configuring VS Code settings..."
mkdir -p /home/flux/.config/Code/User
cat <<'VSCODE_SETTINGS' > /home/flux/.config/Code/User/settings.json
{
    "extensions.verifySignature": false
}
VSCODE_SETTINGS
chown -R flux:$(id -gn flux 2>/dev/null || echo "flux") /home/flux/.config



echo "FluxLinux: Web Development Setup Complete!"
echo "Note: Launch VS Code with 'code' in terminal (alias added)."
