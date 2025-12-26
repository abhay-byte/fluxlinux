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

# PRE-FLIGHT CHECK: Clean up broken VS Code repo if present
# This prevents 'apt update' from failing immediately due to parsing errors
rm -f /etc/apt/sources.list.d/vscode.list

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

# 6. Install Antigravity Package
echo "FluxLinux: Installing Antigravity..."
mkdir -p /etc/apt/keyrings
curl -fsSL https://us-central1-apt.pkg.dev/doc/repo-signing-key.gpg | \
  gpg --dearmor --yes -o /etc/apt/keyrings/antigravity-repo-key.gpg || handle_error "Antigravity Key"

echo "deb [signed-by=/etc/apt/keyrings/antigravity-repo-key.gpg] https://us-central1-apt.pkg.dev/projects/antigravity-auto-updater-dev/ antigravity-debian main" | \
  tee /etc/apt/sources.list.d/antigravity.list > /dev/null

apt update -y || handle_error "Antigravity Repo Update"

# Manually download OR use local file to bypass dpkg crash
echo "FluxLinux: Installing Antigravity..."
cd /tmp

# Check if user downloaded it manually (Best chance for correct ARM64 Architecture)
LOCAL_DEB=$(find /home/flux/Downloads -name "antigravity*.deb" | head -n 1)

if [ -f "$LOCAL_DEB" ]; then
    echo "FluxLinux: Found manual download: $LOCAL_DEB"
    cp "$LOCAL_DEB" .
else
    echo "FluxLinux: Downloading from repository (attempting arm64)..."
    # Try to force arm64 download in case repo defaults to amd64
    apt download antigravity:arm64 || apt download antigravity || handle_error "Antigravity Download"
fi

echo "FluxLinux: Extracting Antigravity..."
# Find the downloaded deb file
DEB_FILE=$(ls antigravity*.deb | head -n 1)

if [ -f "$DEB_FILE" ]; then
    # Extract package contents
    # binutils (ar) is installed in step 1, so this should work
    ar x "$DEB_FILE"
    
    # Extract data archive (could be .tar.gz, .tar.xz, .tar.zst)
    if [ -f data.tar.zst ]; then
         # Fallback to tar for zst (Python needs zstandard lib)
         tar -xvf data.tar.zst -C / || handle_error "Antigravity Extraction (zst)"
        
    elif [ -f data.tar.xz ]; then
        echo "FluxLinux: Using Python to extract xz archive (bypass tar crash)..."
        # Extract to temp dir first to find where the files are hiding
        mkdir -p /tmp/antigravity_pkg
        python3 -c "import tarfile; t=tarfile.open('data.tar.xz'); t.extractall('/tmp/antigravity_pkg'); t.close()" || handle_error "Antigravity Extraction"
        
        # FIND THE APP: Search for the 'resources' folder (standard Electron app structure)
        RESOURCES_DIR=$(find /tmp/antigravity_pkg -type d -name "resources" | head -n 1)
        
        if [ -z "$RESOURCES_DIR" ]; then
             echo "Error: Could not find 'resources' folder in extracted package!"
             echo "Debug Listing:"
             find /tmp/antigravity_pkg
             exit 1
        fi
        
        # The App Root is the parent of 'resources'
        APP_ROOT=$(dirname "$RESOURCES_DIR")
        echo "FluxLinux: Detected App Root at $APP_ROOT"
        
        # STANDARDIZE: Move everything to /usr/share/antigravity (User Preference)
        echo "FluxLinux: moving to /usr/share/antigravity..."
        rm -rf /usr/share/antigravity
        mkdir -p /usr/share/antigravity
        mv "$APP_ROOT"/* /usr/share/antigravity/
        
        # Cleanup
        rm -rf /tmp/antigravity_pkg
        
    elif [ -f data.tar.gz ]; then
        # Logic for gz (less likely for deb, but handled similarly if needed)
        python3 -c "import tarfile; t=tarfile.open('data.tar.gz'); t.extractall('/'); t.close()" || handle_error "Antigravity Extraction (Python/gz)"
    else
        echo "Error: Unknown data archive format in .deb"
        exit 1
    fi
    
    rm -f "$DEB_FILE" debian-binary control.tar* data.tar*
    
    # POST-INSTALL FIXES (Since we skipped dpkg)
    echo "FluxLinux: Applying Runtime Fixes..."
    
    # 1. Create Wrapper Script (Fixes path issues, libffmpeg, Sandbox, & Crash)
    cat <<EOF > /usr/bin/antigravity
#!/bin/bash

# STANDARD PATH: We moved files to /usr/share/antigravity during install
ANTIGRAVITY_PATH="/usr/share/antigravity"

export LD_LIBRARY_PATH="\$ANTIGRAVITY_PATH:\$LD_LIBRARY_PATH"

# FIX: Memory Crash (MmapAligned / SIGABRT)
if [ -f "/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4" ]; then
    export LD_PRELOAD="/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4"
fi

# WRAPPER: IPC and Auth Fixes
# We execute the 'bin/antigravity' SCRIPT (detecting it inside the path)
if [ -f "\$ANTIGRAVITY_PATH/bin/antigravity" ]; then
    TARGET_BIN="\$ANTIGRAVITY_PATH/bin/antigravity"
else
    # Fallback to main binary if script is missing (unlikely)
    TARGET_BIN="\$ANTIGRAVITY_PATH/antigravity"
fi

exec dbus-launch --exit-with-session "\$TARGET_BIN" --no-sandbox --disable-gpu --disable-dev-shm-usage --password-store=basic "\$@"
EOF
    chmod +x /usr/bin/antigravity
    
    # Ensure all potential binaries are executable
    chmod +x /opt/Antigravity/antigravity 2>/dev/null
    chmod +x /opt/Antigravity/bin/antigravity 2>/dev/null
    chmod +x /usr/share/antigravity/antigravity 2>/dev/null
    chmod +x /usr/share/antigravity/bin/antigravity 2>/dev/null
    
    # 2. (Alias no longer needed since wrapper handles --no-sandbox)
    
    # 3. Create Desktop Entry (Ensure it appears in Menu)
    # We explicitly create it ensuring it points to our wrapper and exists even if the .deb didn't provide one.
    mkdir -p /usr/share/applications
    cat <<EOF > /usr/share/applications/antigravity.desktop
[Desktop Entry]
Name=Antigravity
Comment=AI-First Code Editor
GenericName=Text Editor
Exec=/usr/bin/antigravity %U
Icon=antigravity
Type=Application
StartupNotify=true
StartupWMClass=Antigravity
Categories=TextEditor;Development;IDE;
MimeType=text/plain;inode/directory;application/x-code-workspace;
EOF

else
    handle_error "Antigravity .deb not found"
fi

echo "FluxLinux: Web Development Setup Complete!"
echo "Note: Launch VS Code with 'code' in terminal (alias added)."
