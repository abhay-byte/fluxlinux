#!/bin/bash
# scripts/common/setup_hw_accel_debian.sh
# Hardware Acceleration Setup for Debian (PRoot)
# Based on termux-desktop implementation by sabamdarif

# Check if running as root
if [ "$(id -u)" != "0" ]; then
    echo "This script must be run as root."
    exit 1
fi

echo "FluxLinux Pro: Setting up Hardware Acceleration (Debian)..."

# 1. Install Dependencies
# 1. Install Dependencies & Upgrade System
echo "FluxLinux Pro: Detecting Package Manager..."

if command -v pacman &> /dev/null; then
    # --- ARCH LINUX DETECTED ---
    echo "Arch Linux detected (pacman). Running System Update..."
    pacman -Syu --noconfirm
    
    echo "FluxLinux Pro: Installing Arch Dependencies..."
    pacman -S --noconfirm \
        mesa \
        vulkan-radeon \
        vulkan-swrast \
        vulkan-tools \
        mesa-utils \
        curl \
        unzip \
        xdg-desktop-portal
        
    echo "FluxLinux Pro: Arch Setup Complete." 

elif command -v apt-get &> /dev/null; then
    # --- DEBIAN/UBUNTU DETECTED ---
    echo "Debian/Ubuntu detected (apt)."
    echo "FluxLinux Pro: Installing Vulkan/Mesa dependencies..."
    apt-get update
    apt-get install -y \
        mesa-utils \
        libgl1-mesa-dri \
        mesa-vulkan-drivers \
        vulkan-tools \
        curl \
        unzip \
        libvulkan1 \
        libgl1 \
        libglx0 \
        xdg-desktop-portal
else
    echo "Error: Neither apt nor pacman found. Cannot install dependencies."
    exit 1
fi

# 2. Detect Architecture
ARCH=$(dpkg --print-architecture)
if [ "$ARCH" != "arm64" ]; then
    echo "Warning: Drivers are optimized for arm64. Your arch is $ARCH."
fi



# 3. GPU Selection Menu
if [ -n "$FLUX_GPU" ] && [ "$FLUX_GPU" != "manual" ] && [ "$FLUX_GPU" != "ask" ]; then
    echo "FluxLinux Pro: Auto-detected GPU preference: $FLUX_GPU"
    if [ "$FLUX_GPU" == "turnip" ]; then
        GPU_CHOICE="1"
    else
        GPU_CHOICE="2"
    fi
else
    echo "============================================"
    echo "      Select your GPU / Acceleration Mode"
    echo "============================================"
    echo "1) Adreno (Turnip + Zink)"
    echo "   - Best for Snapdragon devices"
    echo "   - Installs custom Mesa/Turnip drivers"
    echo "   - Highest performance for Adreno GPUs"
    echo ""
    echo "2) VirGL (Universal)"
    echo "   - Works with ALL GPU types (Adreno, Mali, etc.)"
    echo "   - Requires 'virgl_test_server' running in Termux"
    echo "   - Good compatibility, moderate performance"
    echo ""
    echo "============================================"
    echo "Note: Mali/Exynos users should use VirGL (option 2)"
    echo "============================================"
    read -r -p "Enter choice [1-2]: " GPU_CHOICE
fi

case "$GPU_CHOICE" in
    1)
        MODE="turnip"
        ;;
    2)
        MODE="virgl"
        ;;
    *)
        echo "Invalid choice. Defaulting to VirGL."
        MODE="virgl"
        ;;
esac

echo "FluxLinux Pro: Configuring for $MODE..."

if [ "$MODE" = "turnip" ]; then
    # Install Turnip (Mesa Turnip for Adreno - KGSL-based, proot compatible)
    # Reference: https://github.com/lfdevs/mesa-for-android-container
    # This driver uses KGSL directly, no /dev/dri needed
    TURNIP_VERSION="26.2.0-devel-20260610"
    
    # Detect distro for correct package
    if [ -f /etc/debian_version ]; then
        DISTRO="debian_trixie"
    elif [ -f /etc/lsb-release ] && grep -q "Ubuntu" /etc/lsb-release; then
        DISTRO="ubuntu_noble"
    elif [ -f /etc/fedora-release ]; then
        DISTRO="fedora_43"
    else
        DISTRO="debian_trixie"  # Default to Debian
    fi
    
    URL="https://github.com/lfdevs/mesa-for-android-container/releases/download/turnip-${TURNIP_VERSION}/turnip_${TURNIP_VERSION}_${DISTRO}_arm64.tar.gz"

    echo "FluxLinux Pro: Downloading Turnip drivers v${TURNIP_VERSION} for ${DISTRO}..."
    curl -L --fail -o /tmp/turnip.tar.gz "$URL" || { echo "Error: Failed to download Turnip (HTTP error)."; exit 1; }

    echo "FluxLinux Pro: Installing Turnip..."
    tar -zxvf /tmp/turnip.tar.gz -C /
    ldconfig
    rm /tmp/turnip.tar.gz
    echo "FluxLinux Pro: Turnip installed successfully!"

    # Disable XFCE4 compositor (causes black screen with Turnip)
    # Must modify config files directly since desktop may not be running
    echo "FluxLinux Pro: Disabling XFCE4 compositor for Turnip compatibility..."

    # For all users with XFCE4 config
    for userdir in /home/* /root; do
        if [ -d "$userdir" ]; then
            XFCE_CONF_DIR="$userdir/.config/xfce4/xfconf/xfce-perchannel-xml"
            mkdir -p "$XFCE_CONF_DIR" 2>/dev/null || true

            # Create or modify xfwm4.xml to disable compositor
            cat > "$XFCE_CONF_DIR/xfwm4.xml" << 'XFCEXML'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfwm4" version="1.0">
  <property name="general" type="empty">
    <property name="use_compositing" type="bool" value="false"/>
  </property>
</channel>
XFCEXML
            chown -R $(stat -c '%U:%G' "$userdir") "$userdir/.config" 2>/dev/null || true
        fi
    done
    echo "FluxLinux Pro: XFCE4 compositor disabled."

    # Create fake /dev/dri for apps that check it (like vkmark)
    # Turnip uses /dev/kgsl-3d0 but some apps iterate /dev/dri
    echo "FluxLinux Pro: Creating /dev/dri compatibility layer..."
    mkdir -p /dev/dri 2>/dev/null || true
    if [ ! -e /dev/dri/card0 ]; then
        ln -sf /dev/null /dev/dri/card0 2>/dev/null || true
    fi
    if [ ! -e /dev/dri/renderD128 ]; then
        ln -sf /dev/null /dev/dri/renderD128 2>/dev/null || true
    fi
    chmod 755 /dev/dri 2>/dev/null || true
    echo "FluxLinux Pro: /dev/dri compatibility layer created."

    # Upgrade system Mesa to match Turnip version (26.2.0-devel)
    # Debian Trixie ships Mesa 25.0.7-2; upgrade to 26.2.0-devel for matching
    # OpenGL/Zink/EGL/GBM libraries from the same mesa-for-android-container build.
    MESA_VERSION="26.2.0-devel-20260610"
    MESA_URL="https://github.com/lfdevs/mesa-for-android-container/releases/download/mesa-${MESA_VERSION}/mesa-for-android-container_${MESA_VERSION}_${DISTRO}_arm64.tar.gz"

    echo "FluxLinux Pro: Upgrading system Mesa to ${MESA_VERSION}..."
    if curl -L --fail -o /tmp/mesa-upgrade.tar.gz "$MESA_URL"; then
        tar -zxf /tmp/mesa-upgrade.tar.gz -C /
        ldconfig
        rm /tmp/mesa-upgrade.tar.gz
        echo "FluxLinux Pro: Mesa upgraded to ${MESA_VERSION}!"

        # Pin Mesa packages so 'apt upgrade' does not downgrade them back to 25.x
        echo "FluxLinux Pro: Pinning Mesa packages to prevent apt downgrade..."
        cat > /etc/apt/preferences.d/pin-mesa << 'PINEOF'
# FluxLinux Pro: Mesa pinned - runtime upgraded to 26.2.0-devel via mesa-for-android-container
# Prevent apt from downgrading back to Debian's packaged version (25.0.7-2)
Package: libgl1-mesa-dri
Pin: version *
Pin-Priority: -1

Package: mesa-libgallium
Pin: version *
Pin-Priority: -1

Package: libglx-mesa0
Pin: version *
Pin-Priority: -1

Package: libegl-mesa0
Pin: version *
Pin-Priority: -1

Package: mesa-va-drivers
Pin: version *
Pin-Priority: -1

Package: mesa-vdpau-drivers
Pin: version *
Pin-Priority: -1

Package: mesa-vulkan-drivers
Pin: version *
Pin-Priority: -1
PINEOF
        echo "FluxLinux Pro: Mesa packages pinned."
    else
        rm -f /tmp/mesa-upgrade.tar.gz
        echo "[WARN] Failed to download Mesa upgrade — system Mesa remains at stock version."
    fi
fi


# 4. Create Launch Wrapper
echo "FluxLinux Pro: Creating 'gpu-launch' wrapper..."

cat <<'EOF' > /usr/local/bin/gpu-launch
#!/bin/bash
# FluxLinux Pro GPU Launcher
# Automatically detects and applies the correct GPU configuration

MODE="MODE_PLACEHOLDER"

# Reset environment
unset GALLIUM_DRIVER
unset MESA_LOADER_DRIVER_OVERRIDE
unset VK_ICD_FILENAMES

if [ "$MODE" = "turnip" ]; then
    # Turnip (Adreno Vulkan) + Zink
    export MESA_LOADER_DRIVER_OVERRIDE=zink
    export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json
    export TU_DEBUG=noconform
    export MESA_VK_WSI_DEBUG=sw
    export MESA_GL_VERSION_OVERRIDE=4.6
    export MESA_GLES_VERSION_OVERRIDE=3.2
    export MESA_NO_ERROR=1

elif [ "$MODE" = "virgl" ]; then
    # VirGL (Universal - works with all GPUs)
    export GALLIUM_DRIVER=virpipe
    export MESA_GL_VERSION_OVERRIDE=4.0
    export MESA_GLES_VERSION_OVERRIDE=3.1
    export MESA_NO_ERROR=1
    
    # Set socket path explicitly for chroot environments
    # Default socket is /tmp/.virgl_test (created by virgl_test_server)
    export VTEST_SOCKET_NAME=${VTEST_SOCKET_NAME:-/tmp/.virgl_test}
    
    # Diagnostic logging
    if [ "$FLUX_GPU_DEBUG" = "1" ]; then
        echo "[DEBUG] GALLIUM_DRIVER=$GALLIUM_DRIVER"
        echo "[DEBUG] VTEST_SOCKET_NAME=$VTEST_SOCKET_NAME"
        echo "[DEBUG] XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR"
        ls -la /tmp/.virgl_test 2>/dev/null && echo "[DEBUG] VirGL socket exists" || echo "[DEBUG] VirGL socket NOT FOUND at /tmp/.virgl_test"
    fi
fi

# Execute Application
exec "$@"
EOF

# Replace placeholder with actual mode
sed -i "s/MODE_PLACEHOLDER/$MODE/g" /usr/local/bin/gpu-launch
chmod +x /usr/local/bin/gpu-launch

echo ""
echo "============================================"
echo "  Hardware Acceleration Setup Complete!"
echo "============================================"
echo "Mode: $MODE"
echo ""
echo "Usage: gpu-launch <application>"
echo "Example: gpu-launch glmark2"
echo ""

if [ "$MODE" = "virgl" ]; then
    echo "IMPORTANT: VirGL requires virgl_test_server running in Termux!"
    echo "The server should start automatically when you launch GUI."
    echo ""
fi

if [ "$MODE" = "turnip" ]; then
    echo "Turnip is configured for Adreno GPUs."
    echo "If you have a different GPU, re-run this script and select VirGL."
    echo ""
fi

echo "Test your setup:"
echo "  gpu-launch glmark2"
echo "  gpu-launch glxinfo | grep 'OpenGL renderer'"
echo "============================================"
