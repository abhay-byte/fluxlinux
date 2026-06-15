#!/bin/bash
# scripts/common/setup_vulkan_llamacpp_debian.sh
# Install llama.cpp with Vulkan GPU backend for FluxLinux (PRoot/Chroot)
# Uses Turnip (Adreno) or system Vulkan driver for GPU-accelerated LLM inference
# Usage: setup_vulkan_llamacpp_debian.sh [uninstall]

# llama-specific build deps. General tools (build-essential, git, pkg-config)
# intentionally excluded — used by other components.
PKGS_APT=(
    cmake
    libvulkan-dev
    glslc
    spirv-tools
)
PKGS_PACMAN=(
    cmake
    vulkan-headers
    vulkan-icd-loader
    glslc
    spirv-tools
    spirv-headers
)

# ─── UNINSTALL MODE ──────────────────────────────────────────────────────
if [ "$1" = "uninstall" ]; then
    echo "FluxLinux: Uninstalling llama.cpp Vulkan Environment..."

    # Remove source + build dir
    rm -rf /opt/llama-cpp

    # Remove all installed llama binaries + wrapper
    rm -f /usr/local/bin/llama-*
    rm -f /usr/local/bin/llama-vulkan

    # Remove SPIRV headers (installed to /usr/include/spirv/ by this script)
    rm -rf /usr/include/spirv

    # Try removing apt packages
    if command -v apt-get &> /dev/null; then
        export DEBIAN_FRONTEND=noninteractive
        apt remove -y --purge "${PKGS_APT[@]}" 2>/dev/null || true
        apt autoremove -y 2>/dev/null || true
    elif command -v pacman &> /dev/null; then
        pacman -Rns --noconfirm "${PKGS_PACMAN[@]}" 2>/dev/null || true
    fi

    echo "FluxLinux: llama.cpp Vulkan Environment Uninstalled."
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

if [ "$(id -u)" != "0" ]; then
    echo "This script must be run as root."
    exit 1
fi

echo "FluxLinux: Installing llama.cpp with Vulkan GPU backend..."

# --- 1. Install Build Dependencies ---
echo "FluxLinux: Detecting Package Manager..."

if command -v apt-get &> /dev/null; then
    echo "Debian/Ubuntu detected (apt)."
    echo "FluxLinux: Installing build dependencies..."
    apt-get update -y || handle_error "System Update"
    apt-get install -y \
        build-essential \
        cmake \
        git \
        libvulkan-dev \
        glslc \
        spirv-tools \
        pkg-config \
        || handle_error "Build Dependencies Installation"
elif command -v pacman &> /dev/null; then
    echo "Arch Linux detected (pacman)."
    echo "FluxLinux: Installing build dependencies..."
    pacman -Syu --noconfirm || handle_error "System Update"
    pacman -S --noconfirm \
        base-devel \
        cmake \
        git \
        vulkan-headers \
        vulkan-icd-loader \
        glslc \
        spirv-tools \
        spirv-headers \
        || handle_error "Build Dependencies Installation"
else
    handle_error "Package Manager Detection: Neither apt nor pacman found"
fi

echo " [✅] Build dependencies installed."

# --- 1b. Install SPIRV Headers (not packaged in Debian Trixie) ---
SPIRV_HDR_PATH="/usr/include/spirv/unified1/spirv.hpp"
if [ ! -f "$SPIRV_HDR_PATH" ]; then
    echo "FluxLinux: Installing SPIRV headers from Khronos..."
    TMP_SPIRV=$(mktemp -d)
    git clone --depth 1 https://github.com/KhronosGroup/SPIRV-Headers "$TMP_SPIRV" \
        || handle_error "Clone SPIRV-Headers"
    cp -r "$TMP_SPIRV/include/spirv" /usr/include/ \
        || handle_error "Install SPIRV headers"
    rm -rf "$TMP_SPIRV"
    echo " [✅] SPIRV headers installed."
else
    echo " [✅] SPIRV headers already present."
fi

# --- 2. Clone & Build llama.cpp ---
BUILD_DIR="/opt/llama-cpp"

# Skip build if already installed
if [ -f "/usr/local/bin/llama-cli" ]; then
    echo " [✅] llama.cpp already installed. Skipping build."
    echo "      To rebuild, run: rm -rf /usr/local/bin/llama-* $BUILD_DIR"
else
    if [ -d "$BUILD_DIR" ]; then
        echo "FluxLinux: Updating existing llama.cpp..."
        cd "$BUILD_DIR" || handle_error "Navigate to Build Directory"
        git pull --ff-only || echo " [⚠️] Git pull failed (may already be up to date)"
    else
        echo "FluxLinux: Cloning llama.cpp..."
        git clone --depth 1 https://github.com/ggml-org/llama.cpp "$BUILD_DIR" \
            || handle_error "Clone llama.cpp Repository"
        cd "$BUILD_DIR" || handle_error "Navigate to Build Directory"
    fi

    echo " [✅] llama.cpp source ready."

    echo "FluxLinux: Building llama.cpp with Vulkan backend (this may take 5-10 min)..."
    cmake -B build \
        -DCMAKE_BUILD_TYPE=Release \
        -DGGML_VULKAN=ON \
        -DBUILD_SHARED_LIBS=OFF \
        || handle_error "CMake Configure (Vulkan)"

    cmake --build build --config Release -j$(nproc) \
        || handle_error "CMake Build"

    echo " [✅] Build complete."

    # --- 3. Install Binaries ---
    echo "FluxLinux: Installing binaries to /usr/local/bin..."

    INSTALLED_COUNT=0
    for bin in build/bin/llama-*; do
        if [ -f "$bin" ] && [ -x "$bin" ]; then
            cp "$bin" /usr/local/bin/ || handle_error "Install $(basename "$bin")"
            echo "  [✅] Installed: $(basename "$bin")"
            INSTALLED_COUNT=$((INSTALLED_COUNT + 1))
        fi
    done

    if [ "$INSTALLED_COUNT" -eq 0 ]; then
        handle_error "No binaries found after build"
    fi

    echo " [✅] $INSTALLED_COUNT binaries installed."
fi

# --- 4. Create llama-vulkan wrapper ---
echo "FluxLinux: Creating 'llama-vulkan' launcher wrapper..."

cat <<'WRAPPER' > /usr/local/bin/llama-vulkan
#!/bin/bash
# FluxLinux llama.cpp Vulkan Launcher
# Sets up Turnip Vulkan environment for GPU-accelerated LLM inference
#
# Usage: llama-vulkan <llama-cli|llama-server|llama-bench> [args...]
# Examples:
#   llama-vulkan llama-cli -m model.gguf -p "Hello" -ngl 99
#   llama-vulkan llama-server -m model.gguf --port 8080 -ngl 99
#   llama-vulkan llama-bench -m model.gguf

# --- Detect Turnip (Adreno) Vulkan driver ---
TURNIP_ICD="/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json"

if [ -f "$TURNIP_ICD" ]; then
    export VK_ICD_FILENAMES="$TURNIP_ICD"
    export TU_DEBUG=noconform
    export MESA_VK_WSI_DEBUG=sw
    GPU_INFO="Turnip (Adreno)"
else
    GPU_INFO="System Vulkan"
fi

# --- Resolve binary path ---
CMD="$1"
shift

if [ -z "$CMD" ]; then
    echo "Usage: llama-vulkan <command> [args...]"
    echo ""
    echo "Commands:"
    echo "  llama-cli     Interactive LLM inference"
    echo "  llama-server  OpenAI-compatible HTTP server"
    echo "  llama-bench   Benchmark inference performance"
    echo ""
    echo "Examples:"
    echo "  llama-vulkan llama-cli -m ~/models/model.gguf -p 'Hello' -ngl 99"
    echo "  llama-vulkan llama-server -m ~/models/model.gguf --port 8080 -ngl 99"
    echo ""
    echo "Use -ngl 99 to offload all layers to GPU."
    exit 0
fi

# Resolve to /usr/local/bin path
BIN="/usr/local/bin/$CMD"
if [ ! -f "$BIN" ]; then
    BIN="$CMD"
fi

echo "[llama-vulkan] GPU: $GPU_INFO"
echo "[llama-vulkan] Running: $BIN $@"
echo ""

exec "$BIN" "$@"
WRAPPER

chmod +x /usr/local/bin/llama-vulkan || handle_error "Set llama-vulkan permissions"
echo " [✅] llama-vulkan wrapper created."

# --- 5. Verify Installation ---
echo ""
echo "============================================"
echo "  llama.cpp Vulkan Setup Complete!"
echo "============================================"
echo ""

# Check what was installed
INSTALLED=""
for cmd in llama-cli llama-server llama-bench llama-quantize; do
    if [ -f "/usr/local/bin/$cmd" ]; then
        INSTALLED="$INSTALLED $cmd"
    fi
done
echo "Installed binaries:$INSTALLED"
echo ""

# Quick Vulkan check
echo "Vulkan environment:"
if [ -f "/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json" ]; then
    echo "  GPU driver: Turnip (Adreno) — detected"
else
    echo "  GPU driver: System Vulkan (no Turnip ICD found)"
fi
echo ""

echo "Quick start:"
echo "  1. Download a GGUF model to ~/models/"
echo "  2. Run: llama-vulkan llama-cli -m ~/models/model.gguf -p 'Hello' -ngl 99"
echo ""
echo "For an OpenAI-compatible server:"
echo "  llama-vulkan llama-server -m ~/models/model.gguf --port 8080 -ngl 99"
echo ""
echo "Benchmark your GPU:"
echo "  llama-vulkan llama-bench -m ~/models/model.gguf"
echo "============================================"
