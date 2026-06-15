#!/bin/bash
# setup_gamedev_debian.sh
# Installs Game Development stack (Godot, Ren'Py, LÖVE, Python Libs, C++ Libs)
# Target: Debian 13 (Trixie) ARM64
# Usage: setup_gamedev_debian.sh [uninstall]

# Game-dev-specific apt packages. Shared base tools (build-essential, git,
# curl, wget, python3, python3-pip) intentionally excluded.
PKGS=(
    libsdl2-dev
    libopenal-dev
    libvorbis-dev
    libflac-dev
    libjpeg-dev
    libpng-dev
    libfreetype6-dev
    libtiff-dev
    libwebp-dev
    renpy
    love
    python3-pygame
    libbox2d-dev
    libraylib-dev
)

# ─── UNINSTALL MODE ──────────────────────────────────────────────────────
if [ "$1" = "uninstall" ]; then
    echo "FluxLinux: Uninstalling Game Development Environment..."

    export DEBIAN_FRONTEND=noninteractive
    apt remove -y --purge "${PKGS[@]}" 2>/dev/null || true
    apt autoremove -y 2>/dev/null || true

    # Remove Godot Engine (manual install + symlink + .desktop + icon)
    rm -rf /opt/godot
    rm -f /usr/local/bin/godot
    rm -f /usr/share/applications/godot.desktop
    rm -f /usr/share/icons/hicolor/256x256/apps/godot.png

    # Remove Python venv with game libs
    rm -rf /home/flux/gamedev_env

    # Revert .bashrc / .zshrc Godot PATH entries
    if [ -f /home/flux/.bashrc ]; then
        sed -i '/# Godot Engine/d; /\/opt\/godot/d' /home/flux/.bashrc 2>/dev/null || true
    fi
    if [ -f /home/flux/.zshrc ]; then
        sed -i '/# Godot Engine/d; /\/opt\/godot/d' /home/flux/.zshrc 2>/dev/null || true
    fi

    # If Raylib was built from source (no apt package), remove the installed files
    if [ ! -f /usr/share/doc/libraylib-dev/copyright ] 2>/dev/null; then
        rm -f /usr/local/include/raylib.h /usr/local/include/raymath.h
        rm -f /usr/local/lib/libraylib.so*
        ldconfig 2>/dev/null || true
    fi

    echo "FluxLinux: Game Development Environment Uninstalled."
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
            :
        fi
    fi
}

echo "FluxLinux: Setting up Game Development Environment..."
TARGET_USER="flux"
TARGET_GROUP="users"

# 1. Install System Dependencies & Build Tools
echo "FluxLinux: Installing System Dependencies..."
export DEBIAN_FRONTEND=noninteractive
apt update -y
apt install -y curl wget unzip zip xz-utils tar build-essential git \
    libsdl2-dev libopenal-dev libvorbis-dev libflac-dev \
    libjpeg-dev libpng-dev libfreetype6-dev libtiff-dev libwebp-dev \
    python3 python3-pip python3-venv python3-full \
    || handle_error "System Dependencies"

# 2. Install Godot Engine (ARM64 Linux)
echo "FluxLinux: Installing Godot Engine..."
GODOT_DIR="/opt/godot"
if [ ! -d "$GODOT_DIR" ]; then
    mkdir -p "$GODOT_DIR"
    # Dynamic fetching logic for stable releases could be complex, hardcoding known stable for ARM64 or using comprehensive search
    # Godot 4.3 Stable is reliable.
    # URL structure: https://github.com/godotengine/godot/releases/download/4.3-stable/Godot_v4.3-stable_linux.arm64.zip
    
    GODOT_VAL="4.3"
    GODOT_URL="https://github.com/godotengine/godot/releases/download/${GODOT_VAL}-stable/Godot_v${GODOT_VAL}-stable_linux.arm64.zip"
    
    echo " - Downloading Godot ${GODOT_VAL}..."
    wget -q --show-progress "$GODOT_URL" -O /tmp/godot.zip || handle_error "Godot Download"
    
    unzip -o /tmp/godot.zip -d "$GODOT_DIR"
    rm /tmp/godot.zip
    
    # Identify binary
    GODOT_BIN=$(find "$GODOT_DIR" -name "Godot*arm64" | head -n 1)
    mv "$GODOT_BIN" "$GODOT_DIR/godot"
    chmod +x "$GODOT_DIR/godot"
    
    # Symlink
    ln -sf "$GODOT_DIR/godot" /usr/local/bin/godot
    
    # Desktop Entry
    echo " - Creating Godot Desktop Entry..."
    mkdir -p /usr/share/applications
    cat <<EOF > /usr/share/applications/godot.desktop
[Desktop Entry]
Name=Godot Engine
Comment=Multi-platform 2D and 3D game engine
Exec=/opt/godot/godot %f
Icon=godot
Terminal=false
Type=Application
Categories=Development;IDE;
StartupNotify=true
MimeType=application/x-godot-project;
EOF
    
    # Icon (Fetch generic icon)
    mkdir -p /usr/share/icons/hicolor/256x256/apps
    wget -q "https://godotengine.org/assets/press/icon_color.png" -O /usr/share/icons/hicolor/256x256/apps/godot.png
    
    echo " [✅] Godot Engine installed"
    
    # Add Godot to PATH in user shell configs
    update_shell_path "/home/$TARGET_USER/.bashrc" "/opt/godot" "Godot Engine"
    update_shell_path "/home/$TARGET_USER/.zshrc" "/opt/godot" "Godot Engine"
else
    echo " [ℹ️] Godot Engine already installed"
fi

# Ensure Godot symlink and PATH exist even if already installed
ln -sf "$GODOT_DIR/godot" /usr/local/bin/godot 2>/dev/null || true
update_shell_path "/home/$TARGET_USER/.bashrc" "/opt/godot" "Godot Engine"
update_shell_path "/home/$TARGET_USER/.zshrc" "/opt/godot" "Godot Engine"

# 3. Install Ren'Py SDK
echo "FluxLinux: Installing Ren'Py SDK..."

# CLEANUP: Remove broken manual install from previous script runs
if [ -d "/opt/renpy" ] || [ -L "/usr/local/bin/renpy" ]; then
    echo " - [Fix] Cleaning up legacy/broken Ren'Py installation..."
    rm -rf /opt/renpy
    rm -f /usr/local/bin/renpy
    rm -f /usr/share/applications/renpy.desktop
fi

# Ren'Py is best installed via apt on ARM64 to ensure matching platform binaries
if ! command -v renpy >/dev/null; then
    echo " - Installing Ren'Py via apt..."
    apt install -y renpy || handle_error "Ren'Py Installation"
    # Note: version might be older (e.g. 8.1/8.2) than latest, but it works on ARM64.
else
    echo " [ℹ️] Ren'Py already installed"
fi

# 4. Install LÖVE (Love2D)
echo "FluxLinux: Installing LÖVE..."
apt install -y love || handle_error "Love2D Install"

# 5. Python Game Dev Libraries (Pygame-ce, Arcade, Panda3D)
echo "FluxLinux: Installing Python Game Libraries..."

# Create a 'gamedev' virtual environment for the user, or install system-wide?
# System-wide allows 'python3 mygame.py' to work easily, but risks breakage.
# However, for a "Dev Environment" setup, creating a standard venv in home is best practice.
venv_dir="/home/$TARGET_USER/gamedev_env"

if [ ! -d "$venv_dir" ]; then
    echo " - Creating Python Virtual Environment at ~/gamedev_env..."
    su - "$TARGET_USER" -c "python3 -m venv $venv_dir"
fi


echo " - Installing Libraries (pygame-ce, panda3d) into venv..."
# We use pip inside the venv
su - "$TARGET_USER" -c "$venv_dir/bin/pip install --upgrade pip"
# Ren'Py is not a pip package. Arcade is often problematic on ARM64/newer Python.
# We stick to pygame-ce and panda3d which are verified.
su - "$TARGET_USER" -c "$venv_dir/bin/pip install pygame-ce panda3d" || echo " [⚠️] Some Python libs failed to install."

# Add venv to aliases for easy activation?
# Or just install pygame via apt for global usage?
# Let's also install python3-pygame globally if available for simplicity
apt install -y python3-pygame || true

# 6. C/C++ Libraries (Raylib, Box2D)
echo "FluxLinux: Installing C/C++ Game Libraries..."

# Helper check for Raylib
if [ -f "/usr/local/include/raylib.h" ] || [ -f "/usr/include/raylib.h" ]; then
    echo " [ℹ️] Raylib already installed (headers found)."
else
    # Debian 13 might not have libraylib-dev in main repo yet. We build from source.
    if ! apt install -y libraylib-dev; then
        echo " [⚠️] libraylib-dev not found in apt. Building from source..."
        
        RAYLIB_SRC="/tmp/raylib"
        git clone --depth 1 https://github.com/raysan5/raylib.git "$RAYLIB_SRC"
        
        mkdir -p "$RAYLIB_SRC/build"
        cd "$RAYLIB_SRC/build"
        cmake -DBUILD_SHARED_LIBS=ON ..
        make -j$(nproc)
        make install
        cd /
        rm -rf "$RAYLIB_SRC"
        ldconfig
        echo " [✅] Raylib built and installed."
    else
        echo " [✅] Raylib installed via apt."
    fi
fi

apt install -y libbox2d-dev || echo " [⚠️] libbox2d-dev not found."

# 7. Final Permissions
echo "FluxLinux: Applying permissions..."
chown -R $TARGET_USER:$TARGET_GROUP "/home/$TARGET_USER"

# 8. Verification
verify_installation() {
    echo ""
    echo "🔎 FluxLinux: Verifying Installations..."
    echo "------------------------------------------------"
    MISSING=0
    
    # Godot
    if command -v godot >/dev/null; then echo " [✅] Godot Engine"; else echo " [❌] Godot Missing"; MISSING=1; fi
    
    # Ren'Py (APT installs to /usr/games/renpy)
    if command -v renpy >/dev/null || [ -x "/usr/games/renpy" ]; then 
        echo " [✅] Ren'Py"
    else 
        echo " [❌] Ren'Py Missing"
        MISSING=1
    fi
    
    # Love
    if command -v love >/dev/null || [ -x "/usr/games/love" ]; then 
        echo " [✅] LÖVE (Love2D)"
    else 
        echo " [❌] LÖVE Missing"
        MISSING=1
    fi
    
    # Python (Check venv)
    if [ -f "$venv_dir/bin/python" ]; then
        SUCC_MSG=" [✅] Python GameDev Env (~/gamedev_env)"
        echo "$SUCC_MSG"
        # Check packages
        su - "$TARGET_USER" -c "$venv_dir/bin/pip list" | grep -E "pygame|panda3d" | awk '{print "    - " $1 " " $2}'
    else
        echo " [❌] Python Venv Missing"
        MISSING=1
    fi
    
    echo "------------------------------------------------"
    if [ $MISSING -eq 1 ]; then
        echo "⚠️  Some components failed to install."
    else
        echo "🎉 Game Development Environment Setup Complete!"
    fi
}

verify_installation

echo "Note: To use Python libraries, activate the environment: source ~/gamedev_env/bin/activate"
read -p "Press Enter to close..."
