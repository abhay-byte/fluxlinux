#!/bin/bash
# setup_gengdev_debian.sh
# Installs General Software Engineering stack (Rust, Go, C/C++, Editors)
# Target: Debian 13 (Trixie) ARM64
# Usage: setup_gengdev_debian.sh [uninstall]

# Gen-dev-specific apt packages. Shared base tools (build-essential, git,
# curl, python3, python3-pip, python3-venv, nodejs) and /opt/nodejs
# (shared with webdev) intentionally excluded.
PKGS=(
    gdb
    cmake
    clang
    lldb
    valgrind
    neovim
    geany
    vim
    emacs-nox
    ripgrep
    python3-pynvim
)

# ─── UNINSTALL MODE ──────────────────────────────────────────────────────
if [ "$1" = "uninstall" ]; then
    echo "FluxLinux: Uninstalling General Software Engineering Environment..."

    export DEBIAN_FRONTEND=noninteractive
    apt remove -y --purge "${PKGS[@]}" 2>/dev/null || true
    apt autoremove -y 2>/dev/null || true

    # Remove NodeSource repo (added by this script)
    rm -f /etc/apt/sources.list.d/nodesource.list
    rm -f /etc/apt/sources.list.d/nodesource.list.save
    rm -f /etc/apt/keyrings/nodesource.gpg
    apt update -y 2>/dev/null || true

    # Remove Rust (user-level)
    rm -rf /home/flux/.cargo /home/flux/.rustup
    rm -f /usr/local/bin/cargo /usr/local/bin/rustup /usr/local/bin/rustc

    # Remove Go
    rm -rf /opt/go
    rm -f /usr/local/bin/go /usr/local/bin/gofmt

    # Remove Lazygit
    rm -f /usr/local/bin/lazygit

    # Remove Micro
    rm -f /usr/local/bin/micro

    # Remove VS Code (tarball install + symlink + .desktop + user config)
    rm -rf /usr/share/code
    rm -f /usr/bin/code
    rm -f /usr/share/applications/code.desktop
    rm -rf /home/flux/.config/Code

    # Remove LunarVim
    rm -rf /home/flux/.local/share/lunarvim
    rm -f /home/flux/.local/bin/lvim
    rm -f /usr/share/applications/lunarvim.desktop
    rm -rf /home/flux/.lvim* 2>/dev/null || true

    # Remove NPM global prefix
    rm -rf /home/flux/.npm-global

    # Revert .bashrc / .zshrc PATH and config entries
    for shell_rc in /home/flux/.bashrc /home/flux/.zshrc /etc/profile; do
        if [ -f "$shell_rc" ]; then
            sed -i '/# Rust Cargo/d; /\$HOME\/.cargo\/bin/d' "$shell_rc" 2>/dev/null || true
            sed -i '/# Go Language/d; /Go Root Bin/d; /Go User Bin/d; /\/opt\/go\/bin/d; /GOPATH=\$HOME\/go/d' "$shell_rc" 2>/dev/null || true
            sed -i '/# LunarVim/d; /\$HOME\/.local\/bin/d' "$shell_rc" 2>/dev/null || true
            sed -i '/# NPM Global/d; /npm-global\/bin/d' "$shell_rc" 2>/dev/null || true
            sed -i "/^alias code='code --no-sandbox/d" "$shell_rc" 2>/dev/null || true
        fi
    done

    echo "FluxLinux: General Software Engineering Environment Uninstalled."
    exit 0
fi
# ─── END UNINSTALL MODE ──────────────────────────────────────────────────

# Error Handler
handle_error() {
    echo ""
    echo "❌ FluxLinux Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    echo "Please check the error message above for details."
    echo "---------------------------------------------------"
    read -p "Press Enter to acknowledge error and exit..."
    exit 1
}

# Helper: Safely update shell configuration (Bash & Zsh)
update_shell_path() {
    local shell_rc="$1"
    local path_entry="$2"
    local comment="$3"
    
    # Create file if missing
    if [ ! -f "$shell_rc" ]; then
        touch "$shell_rc" 2>/dev/null || return
    fi
    
    if [ -w "$shell_rc" ]; then
        if ! grep -q "$path_entry" "$shell_rc"; then
            echo "" >> "$shell_rc"
            [ -n "$comment" ] && echo "# $comment" >> "$shell_rc"
            echo "export PATH=$path_entry:\$PATH" >> "$shell_rc"
            echo " [✅] Added to $shell_rc: $path_entry"
        else
            # echo " [ℹ️] Already in $shell_rc: $path_entry"
            :
        fi
    fi
}

echo "FluxLinux: Setting up General Software Engineering Environment..."
TARGET_USER="flux"
TARGET_GROUP=$(id -gn $TARGET_USER 2>/dev/null || echo "flux")

# 1. Install System Dependencies & C/C++ Stack
echo "FluxLinux: Installing System Dependencies & C/C++ Tools..."
export DEBIAN_FRONTEND=noninteractive
# Core dev tools + Dependencies for LunarVim (git, make, pip, python3, ripgrep)
# We use NodeSource for newer Node.js (v23) avoids conflicts with Debian's split npm package

# Ensure prerequisites for repo setup are present
apt install -y curl gnupg ca-certificates || handle_error "Prerequisites"

mkdir -p /etc/apt/keyrings
curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor --yes -o /etc/apt/keyrings/nodesource.gpg
echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_23.x nodistro main" | tee /etc/apt/sources.list.d/nodesource.list

apt update -y

apt install -y git wget unzip zip xz-utils tar build-essential \
    gdb cmake clang lldb valgrind \
    man-db \
    python3 python3-pip python3-venv \
    nodejs \
    ripgrep \
    neovim \
    || handle_error "Dependencies Installation"

# 2. Install Rust (Rustup)
echo "FluxLinux: Installing Rust (via rustup)..."
if ! command -v rustup >/dev/null; then
    # Install for the target user
    echo " - Installing Rust for $TARGET_USER..."
    su - "$TARGET_USER" -c "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y" || handle_error "Rust Installation"
else
    echo "FluxLinux: Rust already installed."
fi
# Ensure Cargo bin is in path (Rustup usually handles profile, but zsh check)
update_shell_path "/home/$TARGET_USER/.zshrc" "\$HOME/.cargo/bin" "Rust Cargo"

# 3. Install Go (Golang)
GO_ROOT="/opt/go"
GO_VER="1.23.4"
if [ ! -d "$GO_ROOT" ]; then
    echo "FluxLinux: Installing Go $GO_VER..."
    
    GO_URL="https://go.dev/dl/go${GO_VER}.linux-arm64.tar.gz"
    wget -q --show-progress "$GO_URL" -O /tmp/go.tar.gz || handle_error "Go Download"
    
    rm -rf "$GO_ROOT"
    tar -C /opt -xzf /tmp/go.tar.gz
    rm /tmp/go.tar.gz
    
    # Ensure binary is executable (permissions can sometimes be lost)
    chmod +x /opt/go/bin/go
    
    # Add to PATH (Global)
    if ! grep -q "/opt/go/bin" /etc/profile; then
        echo 'export PATH=$PATH:/opt/go/bin' >> /etc/profile
    fi
    
    # Add to PATH (User)
    BASHRC="/home/$TARGET_USER/.bashrc"
    if ! grep -q "/opt/go/bin" "$BASHRC"; then
        echo "" >> "$BASHRC"
        echo "# Go Language" >> "$BASHRC"
        echo 'export GOPATH=$HOME/go' >> "$BASHRC"
        echo 'export PATH=$PATH:/opt/go/bin:$GOPATH/bin' >> "$BASHRC"
    fi
    # Also export for current root session so we can install lazygit
    export PATH=$PATH:/opt/go/bin
    
    echo " [✅] Go installed"
else
    echo "FluxLinux: Go already installed."
fi

# Ensure Go env is persistent for both shells (Idempotent)
for shell_rc in "/home/$TARGET_USER/.bashrc" "/home/$TARGET_USER/.zshrc"; do
    if [ -f "$shell_rc" ]; then
        if ! grep -q "GOPATH=" "$shell_rc"; then
             echo "" >> "$shell_rc"
             echo "# Go Language" >> "$shell_rc"
             echo 'export GOPATH=$HOME/go' >> "$shell_rc"
        fi
        update_shell_path "$shell_rc" "/opt/go/bin" "Go Root Bin"
        update_shell_path "$shell_rc" "\$GOPATH/bin" "Go User Bin"
    fi
done

# Export for current session
export PATH=$PATH:/opt/go/bin

# 4. Install Lazygit (Go)
echo "FluxLinux: Installing Lazygit (for LunarVim)..."
# We install it to standard GOPATH or /usr/local/bin
if ! command -v lazygit >/dev/null; then
    # Use Go to install it to /opt/go/bin or GOBIN
    # We'll install it globally using standard go install, then move user binary or just rely on path
    # Ideally install as user to avoid root permissions mess in GOPATH, or install to /usr/local/bin
    
    # Let's install binary directly to /usr/local/bin for simplicity and speed
    LAZYGIT_VER=$(curl -s "https://api.github.com/repos/jesseduffield/lazygit/releases/latest" | grep -Po '"tag_name": "v\K[^"]*')
    if [ -n "$LAZYGIT_VER" ]; then
        echo " - Fetching Lazygit v${LAZYGIT_VER}..."
        curl -Lo /tmp/lazygit.tar.gz "https://github.com/jesseduffield/lazygit/releases/latest/download/lazygit_${LAZYGIT_VER}_Linux_arm64.tar.gz"
        tar xf /tmp/lazygit.tar.gz -C /usr/local/bin lazygit
        chmod +x /usr/local/bin/lazygit
        rm /tmp/lazygit.tar.gz
        echo " [✅] Lazygit installed"
    else
        echo " [⚠️] Failed to fetch latest Lazygit version. Installing via Go (slower)..."
        go install github.com/jesseduffield/lazygit@latest
        # Move from root's go/bin to /usr/local/bin
        [ -f "$HOME/go/bin/lazygit" ] && mv "$HOME/go/bin/lazygit" /usr/local/bin/
    fi
else
    echo " - Lazygit already installed"
fi

# 5. Install Editors
echo "FluxLinux: Installing Editors..."

# 5a. Micro
if ! command -v micro >/dev/null; then
    echo " - Installing Micro..."
    curl https://getmic.ro | bash
    mv micro /usr/local/bin/
else
    echo " - Micro already installed"
fi

# 5b. Geany & others
echo " - Installing Geany, Vim, Emacs..."
apt install -y geany vim emacs-nox || handle_error "Editors Installation"

# 5c. VS Code (Official Tarball Method)
echo "FluxLinux: Checking VS Code..."
if ! command -v code &> /dev/null; then
    echo " - Installing VS Code (ARM64 Stable)..."
    rm -f /etc/apt/sources.list.d/vscode.list
    apt install -y libx11-xcb1 libxcb-dri3-0 libdrm2 libgbm1 libasound2 dbus-x11 gnome-keyring libtcmalloc-minimal4 || handle_error "VS Code Deps"

    curl -L 'https://update.code.visualstudio.com/latest/linux-arm64/stable' -o /tmp/vscode.tar.gz || handle_error "VS Code Download"
    mkdir -p /usr/share/code
    tar -xzf /tmp/vscode.tar.gz -C /usr/share/code --strip-components=1 || handle_error "VS Code Extraction"
    ln -sf /usr/share/code/bin/code /usr/bin/code
    rm -f /tmp/vscode.tar.gz
    
    if ! grep -q "alias code=" "/home/$TARGET_USER/.bashrc"; then
         echo "alias code='code --no-sandbox --unity-launch'" >> "/home/$TARGET_USER/.bashrc"
    fi
    
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
    echo " [✅] VS Code installed"
else
    echo " - VS Code already installed."
fi

# Configure VS Code settings to disable extension signature verification
# This runs every time to ensure settings are always applied
echo " - Configuring VS Code settings..."
mkdir -p /home/$TARGET_USER/.config/Code/User
cat <<'VSCODE_SETTINGS' > /home/$TARGET_USER/.config/Code/User/settings.json
{
    "extensions.verifySignature": false
}
VSCODE_SETTINGS
chown -R $TARGET_USER:$TARGET_GROUP /home/$TARGET_USER/.config



# 5d. LunarVim (Requires Neovim installed via apt above)
echo "FluxLinux: Installing LunarVim..."
# Verify Neovim version
NVIM_VER=$(nvim --version | head -n 1)
echo " - Found Neovim: $NVIM_VER"

# Check if already installed
if [ -d "/home/$TARGET_USER/.local/share/lunarvim" ]; then
    echo " - LunarVim seems to be installed."
else
    echo " - Running LunarVim Installer (Release Branch)..."
    
    # FIX: PEP 668 (Externally Managed Environment) & NPM Permissions
    # 1. Install python3-pynvim system-wide via apt (avoids pip break-system-packages)
    echo " - Installing python3-pynvim (Neovim Python Client)..."
    apt install -y python3-pynvim || echo " [⚠️] python3-pynvim apt install failed. Fallback to pip might fail due to PEP 668."

    # 2. Configure NPM Local Prefix for the user (avoids EACCES)
    echo " - Configuring NPM local prefix..."
    su - "$TARGET_USER" -c "mkdir -p ~/.npm-global"
    su - "$TARGET_USER" -c "npm config set prefix '~/.npm-global'"
    
    # 3. Add NPM bin to PATH in bashrc if not present
    if ! grep -q "npm-global/bin" "/home/$TARGET_USER/.bashrc"; then
         # Add to top of file or end? End is fine.
         echo 'export PATH=~/.npm-global/bin:$PATH' >> "/home/$TARGET_USER/.bashrc"
    fi
     
    # Run installer
    # We use --no-install-dependencies because we manage them manually (python3-pynvim, nodejs, git, etc.)
    # preventing it from trying to run pip/npm global installs that fail.
    
    
    su - "$TARGET_USER" -c "LV_BRANCH='release-1.4/neovim-0.9' bash <(curl -s https://raw.githubusercontent.com/LunarVim/LunarVim/release-1.4/neovim-0.9/utils/installer/install.sh) -y --no-install-dependencies" || echo " [⚠️] LunarVim installation returned error/warning (check logs)."
fi

# Create Desktop Entry for LunarVim
echo " - Creating LunarVim Desktop Entry..."
cat <<EOF > /usr/share/applications/lunarvim.desktop
[Desktop Entry]
Name=LunarVim
Comment=An IDE layer for Neovim
GenericName=Text Editor
Exec=/home/$TARGET_USER/.local/bin/lvim %F
Icon=nvim
Type=Application
Terminal=true
StartupNotify=false
Categories=Development;TextEditor;IDE;
Keywords=vim;neovim;ide;
EOF

# ENSURE PATHS ARE CORRECT (Unconditional)
echo " - Updating Shell PATHs (Bash & Zsh) for LunarVim/NPM..."
update_shell_path "/home/$TARGET_USER/.bashrc" "\$HOME/.local/bin" "LunarVim"
update_shell_path "/home/$TARGET_USER/.zshrc" "\$HOME/.local/bin" "LunarVim"

update_shell_path "/home/$TARGET_USER/.bashrc" "~/.npm-global/bin" "NPM Global"
update_shell_path "/home/$TARGET_USER/.zshrc" "~/.npm-global/bin" "NPM Global"

# 6. Final Permissions
echo "FluxLinux: Applying permissions..."
chown -R $TARGET_USER:$TARGET_GROUP "/home/$TARGET_USER" || handle_error "Final Permissions"
chmod -R 755 /opt/go || handle_error "Go Permissions"

# 7. Verification
verify_installation() {
    echo ""
    echo "🔎 FluxLinux: Verifying Installations..."
    echo "------------------------------------------------"
    MISSING=0
    
    # Rust
    if su - "$TARGET_USER" -c "command -v cargo" >/dev/null 2>&1; then 
        echo " [✅] Rust (Cargo)"
    else 
        echo " [❌] Rust Missing"
        MISSING=1
    fi
    
    # Go (Check binary directly with path)
    if [ -x "/opt/go/bin/go" ]; then 
        GO_V=$(/opt/go/bin/go version 2>/dev/null)
        echo " [✅] Go ($GO_V)"
    else 
        echo " [❌] Go Missing (Checked /opt/go/bin/go)"
        ls -l /opt/go/bin/go 2>/dev/null || echo "     - Binary not found or not executable"
        MISSING=1
    fi
    
    # C/C++
    if command -v gcc >/dev/null; then echo " [✅] GCC"; else echo " [❌] GCC Missing"; MISSING=1; fi
    if command -v clang >/dev/null; then echo " [✅] Clang"; else echo " [❌] Clang Missing"; MISSING=1; fi
    
    # Editors
    if command -v vim >/dev/null; then echo " [✅] Vim"; else echo " [❌] Vim Missing"; MISSING=1; fi
    if command -v micro >/dev/null; then echo " [✅] Micro"; else echo " [❌] Micro Missing"; MISSING=1; fi
    if command -v geany >/dev/null; then echo " [✅] Geany"; else echo " [❌] Geany Missing"; MISSING=1; fi
    if [ -f "/usr/bin/code" ]; then echo " [✅] VS Code"; else echo " [❌] VS Code Missing"; MISSING=1; fi
    
    # LunarVim
    # Verify by checking file existence first (more robust than path expansion in su)
    if [ -f "/home/$TARGET_USER/.local/bin/lvim" ]; then
        echo " [✅] LunarVim (Found binary)"
    elif su - "$TARGET_USER" -c "export PATH=\$HOME/.local/bin:\$PATH; command -v lvim" >/dev/null 2>&1; then 
        echo " [✅] LunarVim (Found in PATH)"
    else 
        echo " [❌] LunarVim Missing (Checked ~/.local/bin/lvim)"
        MISSING=1
    fi
    
    echo "------------------------------------------------"
    if [ $MISSING -eq 1 ]; then
        echo "⚠️  Some components failed to install."
    else
        echo "🎉 General Software Engineering Stack Complete!"
    fi
}

verify_installation

echo "Note: Restart your terminal/session for PATH changes to take effect."
read -p "Press Enter to close..."
