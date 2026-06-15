#!/bin/bash
# setup_office_debian.sh
# Installs Office Productivity Stack
# Target: Debian 13 (Trixie) ARM64
# Compatible with: Chroot and Proot
# Usage: setup_office_debian.sh [uninstall]

# Office-specific packages. Shared system deps (dbus-x11, fonts) intentionally
# excluded — they're used by XFCE, browsers, and other components.
PKGS=(
    libreoffice
    libreoffice-gtk3
    libreoffice-writer
    libreoffice-calc
    libreoffice-impress
    thunderbird
    evince
    xournalpp
    fonts-noto
)

# ─── UNINSTALL MODE ──────────────────────────────────────────────────────
if [ "$1" = "uninstall" ]; then
    echo "FluxLinux: Uninstalling Office Productivity Environment..."

    export DEBIAN_FRONTEND=noninteractive
    apt remove -y --purge "${PKGS[@]}" 2>/dev/null || true
    apt autoremove -y 2>/dev/null || true

    echo "FluxLinux: Office Productivity Environment Uninstalled."
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

echo "FluxLinux: Setting up Office Productivity Environment..."
echo "Target: Debian 13 (Trixie) - ARM64"

# 1. System Dependencies
echo "FluxLinux: Installing Dependencies..."
export DEBIAN_FRONTEND=noninteractive
apt update -y

# Install essential fonts first (these are small and reliable)
apt install -y \
    dbus-x11 \
    fonts-noto-core \
    fonts-liberation \
    fonts-dejavu \
    || handle_error "Dependencies & Fonts"

# Conditional NPM Install (Fix for NodeSource Conflict)
# If setup_webdev_debian.sh ran, nodejs includes npm.
# If not, Debian split packages might need explicit npm.
if ! command -v npm >/dev/null; then
    echo "FluxLinux: NPM not found (bundled), installing explicitly..."
    apt install -y npm || echo " [⚠️] NPM install warning (might be bundled)"
fi

# 2. LibreOffice Suite
echo "FluxLinux: Installing LibreOffice Suite..."
# Use --no-install-recommends to avoid large optional packages like fonts-noto-extra
# which can fail in proot environments due to resource constraints
apt install -y --no-install-recommends \
    libreoffice \
    libreoffice-gtk3 \
    || {
        echo "⚠️ LibreOffice install had issues, attempting to fix..."
        # Fix any broken packages (common in proot with large fonts)
        apt --fix-broken install -y
        dpkg --configure -a
        # Retry with just the core package
        apt install -y --no-install-recommends libreoffice-writer libreoffice-calc libreoffice-impress libreoffice-gtk3 \
            || handle_error "LibreOffice Installation"
    }

# 3. Email & PIM
echo "FluxLinux: Installing Email & Organization Tools..."
# Thunderbird: Email client
apt install -y --no-install-recommends \
    thunderbird \
    || handle_error "Thunderbird Installation"

# 4. PDF Tools
echo "FluxLinux: Installing PDF Tools..."
# Evince: Document Viewer
# Xournal++: Note taking & PDF Annotation
# Non-fatal: on Debian Trixie + backports, evince transitively pulls
# libgnome-desktop-3 which conflicts with backports' libxkbcommon0.
# Try, fix, retry; if still fails, warn and continue — LibreOffice can
# open PDFs anyway.
apt install -y --no-install-recommends \
    evince \
    xournalpp \
    2>/dev/null || {
        echo " [⚠️] PDF Tools install failed, attempting to fix and retry individually..."
        apt --fix-broken install -y 2>/dev/null || true
        dpkg --configure -a 2>/dev/null || true
        apt install -y --no-install-recommends evince 2>/dev/null || \
            echo " [⚠️] evince not installed (likely libgnome-desktop-3 / libxkbcommon0 conflict). LibreOffice can still open PDFs."
        apt install -y --no-install-recommends xournalpp 2>/dev/null || \
            echo " [⚠️] xournalpp not installed."
    }

# 5. Optional: Try to install extra fonts (non-fatal if fails)
echo "FluxLinux: Installing additional fonts (optional)..."
apt install -y fonts-noto 2>/dev/null || echo " [⚠️] Optional fonts skipped (proot limitation)"

# 6. Verification
verify_installation() {
    echo ""
    echo "🔎 FluxLinux: Verifying Installations..."
    echo "------------------------------------------------"
    
    if command -v libreoffice >/dev/null; then echo " [✅] LibreOffice"; else echo " [❌] LibreOffice Missing"; fi
    if command -v thunderbird >/dev/null; then echo " [✅] Thunderbird"; else echo " [❌] Thunderbird Missing"; fi
    if command -v evince >/dev/null; then echo " [✅] Evince"; else echo " [❌] Evince Missing"; fi
    if command -v xournalpp >/dev/null; then echo " [✅] Xournal++"; else echo " [❌] Xournal++ Missing"; fi

    echo "------------------------------------------------"
    echo "🎉 Office Setup Complete!"
}

verify_installation

echo "Note: Check your Applications menu for installed tools."
read -p "Press Enter to close..."
