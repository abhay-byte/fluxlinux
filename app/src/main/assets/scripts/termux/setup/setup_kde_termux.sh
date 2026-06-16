#!/bin/bash
# ============================================================
# FluxLinux — Native Termux KDE Plasma Desktop Setup
# Location: termux/setup/setup_kde_termux.sh
# Runs on: HOST Termux (native, no container)
# Root required: no
# Status: EXPERIMENTAL — KDE in native Termux has known issues
#         (KWin compositing, some D-Bus services). See docs/termux/
# ============================================================

CALLBACK_NAME="kde_plasma"

handle_error() {
    echo ""
    echo "❌ FluxLinux Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    echo "Please check the error message above for details."
    echo "---------------------------------------------------"
    read -p "Press Enter to acknowledge error and exit..."
    exit 1
}

# ── Uninstall mode (T9) ───────────────────────────────────
# Reverse of install path above. Shared deps (x11-repo, termux-x11,
# pulseaudio, dbus, etc.) intentionally kept.
if [ "$1" = "uninstall" ]; then
    echo "FluxLinux: Uninstalling KDE Plasma Desktop..."

    pkg uninstall -y plasma konsole dolphin || true

    rm -f "$HOME/.config/kwinrc"
    rm -f "$HOME/.config/kdeglobals"

    echo "FluxLinux: KDE Plasma Desktop Uninstalled."
    exit 0
fi

echo ""
echo "══════════════════════════════════════════════"
echo "  FluxLinux — Native KDE Plasma Desktop Setup"
echo "  ⚠️  EXPERIMENTAL — See docs/termux/ for notes"
echo "══════════════════════════════════════════════"
echo ""

# ── Step 1: Repositories ──────────────────────────────────
echo "FluxLinux: Enabling required repositories..."
pkg install x11-repo -y || handle_error "x11-repo install"
pkg install tur-repo -y || handle_error "tur-repo install"

# ── Step 2: Update ───────────────────────────────────────
echo "FluxLinux: Updating package lists..."
pkg update -y || handle_error "pkg update"

# ── Step 3: KDE Plasma packages ──────────────────────────
echo ""
echo "===== Installing KDE Plasma Desktop ====="
echo "Note: KDE is large (~1.5 GB). This may take several minutes."
echo ""
pkg install -y \
    plasma \
    || handle_error "KDE Plasma core"

# ── Step 4: KDE Applications ─────────────────────────────
echo ""
echo "===== Installing KDE Applications ====="
pkg install -y \
    konsole \
    dolphin \
    || handle_error "KDE applications"

# Optional KDE apps (best-effort)
pkg install -y kate spectacle krita 2>/dev/null || true

# ── Step 5: Display server ───────────────────────────────
echo ""
echo "===== Installing Termux:X11 Display Server ====="
pkg install -y termux-x11-nightly || handle_error "Termux:X11 install"

# ── Step 6: Session dependencies ─────────────────────────
echo ""
echo "===== Installing Session Dependencies ====="
pkg install -y \
    dbus \
    at-spi2-core \
    || handle_error "Session dependencies"

# ── Step 7: Audio ────────────────────────────────────────
echo ""
echo "===== Installing PulseAudio ====="
pkg install -y pulseaudio || handle_error "PulseAudio install"

# ── Step 8: Fonts ────────────────────────────────────
echo ""
echo "===== Installing Fonts ====="
# fontconfig for font management (noto-fonts are not available in Termux repos)
pkg install -y fontconfig || true
fc-cache -fv 2>/dev/null || true

# ── Step 9: KWin workaround config ───────────────────────
echo ""
echo "===== Applying KWin Stability Workarounds ====="
# Pre-disable KWin compositing (causes crashes on many devices)
mkdir -p "$HOME/.config"
cat > "$HOME/.config/kwinrc" << 'EOF'
[Compositing]
Enabled=false
Backend=OpenGL
GLCore=false
EOF
echo " [✅] KWin compositing pre-disabled (stability workaround)"

# Pre-configure KDE for mobile-friendly defaults
cat > "$HOME/.config/kdeglobals" << 'EOF'
[KDE]
SingleClick=false

[General]
XftDPI=192
EOF
echo " [✅] KDE defaults configured for Android screens"

# ── Verification ─────────────────────────────────────────
verify_installation() {
    echo ""
    echo "🔎 FluxLinux: Verifying KDE Plasma Installation..."
    echo "------------------------------------------------"
    MISSING=0

    if command -v startplasma-x11 >/dev/null 2>&1; then
        echo " [✅] KDE Plasma (startplasma-x11)"
    else
        echo " [❌] KDE Plasma (startplasma-x11) Missing"
        MISSING=1
    fi

    if command -v konsole >/dev/null 2>&1; then
        echo " [✅] Konsole Terminal"
    else
        echo " [⚠️] Konsole not found"
    fi

    if command -v dolphin >/dev/null 2>&1; then
        echo " [✅] Dolphin File Manager"
    else
        echo " [⚠️] Dolphin not found"
    fi

    if command -v termux-x11 >/dev/null 2>&1; then
        echo " [✅] Termux:X11"
    else
        echo " [❌] Termux:X11 Missing"
        MISSING=1
    fi

    if command -v pulseaudio >/dev/null 2>&1; then
        echo " [✅] PulseAudio"
    else
        echo " [⚠️] PulseAudio not found (audio may not work)"
    fi

    if command -v kwin_x11 >/dev/null 2>&1; then
        echo " [✅] KWin Window Manager"
    else
        echo " [⚠️] KWin not found — some window management may be unavailable"
    fi

    echo "------------------------------------------------"
    if [ $MISSING -eq 1 ]; then
        echo "⚠️  Some components failed. Re-run or check errors above."
    else
        echo "🎉 KDE Plasma installed successfully!"
        echo "   KWin compositing is pre-disabled for stability."
        echo "   Run 'start_kde_termux.sh' to launch."
    fi
}

verify_installation

# ── Callback to app ──────────────────────────────────────
am start -a android.intent.action.VIEW \
  -d "fluxlinux://callback?result=success&name=${CALLBACK_NAME}" \
  --flags 0x10000000 2>/dev/null || true

echo ""
echo "Note: KDE Plasma in native Termux is experimental."
echo "      For a more stable KDE experience, use Debian (Chroot) instead."
echo "      See docs/termux/native_gui_research.md for known issues."
read -p "Press Enter to close..."
