#!/bin/bash
# setup_graphic_design_debian.sh
# Installs Graphic Design & Digital Art Stack
# Target: Debian 13 (Trixie) ARM64
# Usage: setup_graphic_design_debian.sh [uninstall]

# Graphic-design-specific packages. Shared system deps (dbus-x11, fonts) and
# apt sources.list (contrib/non-free) are intentionally not reverted — they're
# used by other components (gamedev, cybersec, etc.).
PKGS=(
    gimp
    inkscape
    krita
    darktable
    scribus
    blender
    imagemagick
    fontforge
)

# ─── UNINSTALL MODE ──────────────────────────────────────────────────────
if [ "$1" = "uninstall" ]; then
    echo "FluxLinux: Uninstalling Graphic Design Environment..."

    export DEBIAN_FRONTEND=noninteractive
    apt remove -y --purge "${PKGS[@]}" 2>/dev/null || true
    apt autoremove -y 2>/dev/null || true

    echo "FluxLinux: Graphic Design Environment Uninstalled."
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

echo "FluxLinux: Setting up Graphic Design Environment..."
echo "Target: Debian 13 (Trixie) - ARM64"

# 1. System Dependencies
echo "FluxLinux: Installing Dependencies..."
export DEBIAN_FRONTEND=noninteractive
apt update -y

# Enable contrib/non-free for fonts and extra assets
sed -i 's/main$/main contrib non-free non-free-firmware/g' /etc/apt/sources.list
sed -i 's/main contrib$/main contrib non-free non-free-firmware/g' /etc/apt/sources.list
apt update -y

apt install -y \
    dbus-x11 \
    fonts-noto \
    fonts-liberation \
    fonts-dejavu \
    || handle_error "Dependencies & Fonts"

# 2. Raster & Vector Editors
echo "FluxLinux: Installing Design Tools..."

# GIMP: The GNU Image Manipulation Program (Raster)
# Inkscape: Professional Vector Graphics Editor
# Krita: Digital Painting & Animation Suite
apt install -y \
    gimp \
    inkscape \
    krita \
    || handle_error "GIMP/Inkscape/Krita Installation"

# 3. Photography & Publishing
echo "FluxLinux: Installing Photo & Publishing Tools..."

# Darktable: RAW Developer & Lightroom alternative
# Scribus: Desktop Publishing (Indesign alternative)
apt install -y \
    darktable \
    scribus \
    || handle_error "Darktable/Scribus Installation"

# 4. 3D & Utilities
echo "FluxLinux: Installing 3D & Utility Tools..."

# Blender: 3D Creation Suite (heavy but powerful)
# ImageMagick: CLI image manipulation (convert, mogrify)
# FontForge: Font Editor
apt install -y \
    blender \
    imagemagick \
    fontforge \
    || handle_error "Blender/Utils Installation"

# 5. Verification
verify_installation() {
    echo ""
    echo "🔎 FluxLinux: Verifying Installations..."
    echo "------------------------------------------------"
    
    # Editors
    if command -v gimp >/dev/null; then echo " [✅] GIMP"; else echo " [❌] GIMP Missing"; fi
    if command -v inkscape >/dev/null; then echo " [✅] Inkscape"; else echo " [❌] Inkscape Missing"; fi
    if command -v krita >/dev/null; then echo " [✅] Krita"; else echo " [❌] Krita Missing"; fi
    
    # Photo/Layout
    if command -v darktable >/dev/null; then echo " [✅] Darktable"; else echo " [❌] Darktable Missing"; fi
    if command -v scribus >/dev/null; then echo " [✅] Scribus"; else echo " [❌] Scribus Missing"; fi
    
    # 3D/Utils
    if command -v blender >/dev/null; then echo " [✅] Blender"; else echo " [❌] Blender Missing"; fi
    if command -v convert >/dev/null; then echo " [✅] ImageMagick"; else echo " [❌] ImageMagick Missing"; fi

    echo "------------------------------------------------"
    echo "🎉 Graphic Design Setup Complete!"
}

verify_installation

echo "Note: For best performance with Blender/Krita, enable Hardware Acceleration (VirGL) in FluxLinux settings."
read -p "Press Enter to close..."
