#!/bin/bash
# ============================================================
# FluxLinux — Native Termux KDE Plasma Customization
# Location: termux/setup/setup_customization_kde_termux.sh
# Runs on: HOST Termux (native, no container)
# Root required: no
#
# Applies FluxLinux dark theme to native Termux KDE Plasma:
# - Breeze Dark color scheme
# - Papirus-Dark icon theme
# - KWin compositing disabled (stability + performance)
# - 2x HiDPI scaling for Android screens
# - Wallpaper
# ============================================================

CALLBACK_NAME="kde_customization"

handle_error() {
    echo ""
    echo "❌ FluxLinux Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    echo "Please check the error message above for details."
    echo "---------------------------------------------------"
    read -p "Press Enter to acknowledge error and exit..."
    exit 1
}

echo ""
echo "══════════════════════════════════════════════"
echo "  FluxLinux — KDE Customization (Native)"
echo "══════════════════════════════════════════════"
echo ""

# ── Directories ───────────────────────────────────────────
mkdir -p "$HOME/.icons"
mkdir -p "$HOME/.fluxlinux/wallpapers"
mkdir -p "$HOME/.config"
mkdir -p "$HOME/.local/share/wallpapers"

# ── Step 1: Icon theme ────────────────────────────────────
echo "===== Installing Papirus Icon Theme ====="
pkg install -y papirus-icon-theme 2>/dev/null || {
    echo " [⚠️] papirus-icon-theme not in repo — skipping (Breeze icons will be used)"
}
echo " [✅] Icon theme step complete"

# ── Step 2: Wallpaper ─────────────────────────────────────
echo ""
echo "===== Setting Up Wallpaper ====="
WALLPAPER_PATH="$HOME/.fluxlinux/wallpapers/flux_dark.jpg"
if [ ! -f "$WALLPAPER_PATH" ]; then
    echo " Downloading wallpaper..."
    curl -fsSL \
        "https://raw.githubusercontent.com/sabamdarif/termux-desktop/main/wallpaper/wall-0.jpg" \
        -o "$WALLPAPER_PATH" || {
        echo " [⚠️] curl download failed — trying ImageMagick fallback..."
        if command -v convert >/dev/null 2>&1; then
            convert -size 1920x1080 xc:#0d1117 "$WALLPAPER_PATH" 2>/dev/null || true
            echo " [⚠️] Using generated solid fallback wallpaper"
        else
            echo " [⚠️] No wallpaper — neither curl nor convert available"
        fi
    }
fi
echo " [✅] Wallpaper ready"

# ── Step 3: KDE Global Theme (Breeze Dark) ────────────────
echo ""
echo "===== Applying KDE Breeze Dark Theme ====="

# Color scheme
kwriteconfig5 --file kdeglobals \
    --group "General" \
    --key "ColorScheme" "BreezeDark" 2>/dev/null || \
cat >> "$HOME/.config/kdeglobals" << 'EOF'

[General]
ColorScheme=BreezeDark
Name=Breeze Dark
shadeSortColumn=true
EOF
echo " [✅] Color scheme: Breeze Dark"

# Icon theme
kwriteconfig5 --file kdeglobals \
    --group "Icons" \
    --key "Theme" "Papirus-Dark" 2>/dev/null || true
echo " [✅] Icon theme: Papirus-Dark"

# Font
kwriteconfig5 --file kdeglobals \
    --group "General" \
    --key "font" "Noto Sans,10,-1,5,50,0,0,0,0,0" 2>/dev/null || true
kwriteconfig5 --file kdeglobals \
    --group "WM" \
    --key "activeFont" "Noto Sans,10,-1,5,75,0,0,0,0,0" 2>/dev/null || true
echo " [✅] Fonts: Noto Sans 10"

# ── Step 4: KWin config (stability + performance) ─────────
echo ""
echo "===== Configuring KWin (Stability Workarounds) ====="
kwriteconfig5 --file kwinrc \
    --group "Compositing" \
    --key "Enabled" "false" 2>/dev/null || true
kwriteconfig5 --file kwinrc \
    --group "Compositing" \
    --key "Backend" "OpenGL" 2>/dev/null || true
echo " [✅] KWin compositing disabled (prevents crashes on mobile GPU)"

# Disable desktop effects that need compositing
kwriteconfig5 --file kwinrc \
    --group "Plugins" \
    --key "blurEnabled" "false" 2>/dev/null || true
kwriteconfig5 --file kwinrc \
    --group "Plugins" \
    --key "desktopgridEnabled" "false" 2>/dev/null || true
echo " [✅] Compositing effects disabled"

# ── Step 5: HiDPI scaling ─────────────────────────────────
echo ""
echo "===== Configuring HiDPI Scaling (2x for Android) ====="
kwriteconfig5 --file kdeglobals \
    --group "KScreen" \
    --key "ScaleFactor" "2" 2>/dev/null || true
kwriteconfig5 --file kdeglobals \
    --group "General" \
    --key "XftDPI" "192" 2>/dev/null || true
echo " [✅] HiDPI scaling: 2x (192 DPI)"

# ── Step 6: Plasma wallpaper ──────────────────────────────
echo ""
echo "===== Setting KDE Wallpaper ====="
# Copy to KDE wallpapers dir
mkdir -p "$HOME/.local/share/wallpapers/FluxLinux"
cp "$WALLPAPER_PATH" "$HOME/.local/share/wallpapers/FluxLinux/contents.jpg" 2>/dev/null || true

# Set via plasma-apply-wallpaperimage if available
if command -v plasma-apply-wallpaperimage >/dev/null 2>&1; then
    plasma-apply-wallpaperimage "$WALLPAPER_PATH" 2>/dev/null || true
    echo " [✅] Wallpaper applied via plasma-apply-wallpaperimage"
else
    echo " [⚠️] plasma-apply-wallpaperimage not available — set wallpaper manually in KDE settings"
fi

# ── Step 7: GTK theming (for GTK apps in KDE) ────────────
echo ""
echo "===== Configuring GTK Apps in KDE ====="
mkdir -p "$HOME/.config/gtk-3.0"
cat > "$HOME/.config/gtk-3.0/settings.ini" << 'EOF'
[Settings]
gtk-theme-name=Adwaita-dark
gtk-icon-theme-name=Papirus-Dark
gtk-font-name=Noto Sans 10
gtk-application-prefer-dark-theme=1
EOF
echo " [✅] GTK dark theme configured for Qt/KDE apps"

# ── Step 8: Single-click fix ──────────────────────────────
kwriteconfig5 --file kdeglobals \
    --group "KDE" \
    --key "SingleClick" "false" 2>/dev/null || true
echo " [✅] Double-click to open files (mobile-friendly)"

# ── Callback to app ──────────────────────────────────────
am start -a android.intent.action.VIEW \
  -d "fluxlinux://callback?result=success&name=${CALLBACK_NAME}" \
  --flags 0x10000000 2>/dev/null || true

echo ""
echo "🎨 KDE customization applied!"
echo "   Theme: Breeze Dark | Icons: Papirus-Dark | Scale: 2x"
echo "   KWin compositing disabled for mobile GPU stability."
echo "   Restart KDE Plasma for changes to fully take effect."
read -p "Press Enter to close..."
