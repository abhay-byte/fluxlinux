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
mkdir -p "$HOME/.local/share/konsole"

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

# ── Step 3: Theme selection ───────────────────────────────
if [ -n "$FLUX_THEME" ]; then
    echo "FluxLinux: Auto-applying Theme: $FLUX_THEME"
    if [ "$FLUX_THEME" = "light" ]; then
        THEME_CHOICE="2"
    else
        THEME_CHOICE="1"
    fi
else
    echo "------------------------------------------------"
    echo "Select Theme Preference:"
    echo "1) Dark (Default)"
    echo "2) Light"
    read -p "Enter choice [1-2]: " THEME_CHOICE
    echo "------------------------------------------------"
fi

if [ "$THEME_CHOICE" = "2" ]; then
    KDE_COLOR_SCHEME="BreezeLight"
    KDE_PLASMA_THEME="breeze"
    KDE_LOOK_AND_FEEL="org.kde.breeze.desktop"
    SEL_ICON="Papirus"
    GTK_DARK="0"
else
    KDE_COLOR_SCHEME="BreezeDark"
    KDE_PLASMA_THEME="breeze-dark"
    KDE_LOOK_AND_FEEL="org.kde.breezedark.desktop"
    SEL_ICON="Papirus-Dark"
    GTK_DARK="1"
fi

# ── Step 4: KDE config files (Debian-compatible layout) ───
echo ""
echo "===== Applying KDE Plasma Settings ====="
KDE_CONFIG="$HOME/.config"
WALLPAPER_DESKTOP_PATH="$HOME/.local/share/wallpapers/FluxLinux/contents.jpg"
mkdir -p "$KDE_CONFIG" "$HOME/.local/share/wallpapers/FluxLinux"
cp "$WALLPAPER_PATH" "$WALLPAPER_DESKTOP_PATH" 2>/dev/null || WALLPAPER_DESKTOP_PATH="$WALLPAPER_PATH"

echo "FluxLinux: Writing kdeglobals..."
cat > "$KDE_CONFIG/kdeglobals" << EOF
[General]
ColorScheme=$KDE_COLOR_SCHEME
Name=$KDE_COLOR_SCHEME
shadeSortColumn=true
fixed=Noto Sans Mono,10,-1,5,50,0,0,0,0,0
font=Noto Sans,10,-1,5,50,0,0,0,0,0
menuFont=Noto Sans,10,-1,5,50,0,0,0,0,0
smallestReadableFont=Noto Sans,8,-1,5,50,0,0,0,0,0
toolBarFont=Noto Sans,10,-1,5,50,0,0,0,0,0
XftDPI=192

[Icons]
Theme=$SEL_ICON

[KDE]
LookAndFeelPackage=$KDE_LOOK_AND_FEEL
ShowDeleteCommand=false
SingleClick=false
widgetStyle=Breeze

[KScreen]
ScaleFactor=2

[WM]
activeFont=Noto Sans,10,-1,5,700,0,0,0,0,0
EOF

echo "FluxLinux: Writing kcminputrc..."
cat > "$KDE_CONFIG/kcminputrc" << EOF
[Mouse]
cursorSize=24
cursorTheme=breeze_cursors
EOF

echo "FluxLinux: Writing kwinrc..."
cat > "$KDE_CONFIG/kwinrc" << 'EOF'
[Compositing]
Backend=QPainter
Enabled=false
OpenGLIsUnsafe=true

[Plugins]
blurEnabled=false
desktopgridEnabled=false
kwin4_effect_fadingpopupsEnabled=false
kwin4_effect_frozenappEnabled=false
kwin4_effect_loginEnabled=false
kwin4_effect_logoutEnabled=false
kwin4_effect_morphingpopupsEnabled=false
kwin4_effect_squashEnabled=false
kwin4_effect_windowapertureEnabled=false

[Windows]
BorderlessMaximizedWindows=false
FocusPolicy=ClickToFocus
TitlebarDoubleClickCommand=Maximize
SnapAgainst=1

[org.kde.kdecoration2]
ButtonsOnLeft=M
ButtonsOnRight=HIA
library=org.kde.breeze
theme=Breeze
EOF

echo "FluxLinux: Writing plasmarc..."
cat > "$KDE_CONFIG/plasmarc" << EOF
[Theme]
name=$KDE_PLASMA_THEME
EOF

echo "FluxLinux: Writing Plasma desktop config..."
cat > "$KDE_CONFIG/plasma-org.kde.plasma.desktop-appletsrc" << EOF
[Containments][1]
ItemGeometriesHorizontal=
activityId=
formfactor=0
immutability=1
lastScreen=0
location=0
plugin=org.kde.plasma.folder
wallpaperplugin=org.kde.image

[Containments][1][General]
ToolBoxButtonState=topcenter

[Containments][1][Wallpaper][org.kde.image][General]
Image=$WALLPAPER_DESKTOP_PATH
PreviewImage=$WALLPAPER_DESKTOP_PATH

[Containments][2]
activityId=
formfactor=2
immutability=1
lastScreen=0
location=4
plugin=org.kde.panel
wallpaperplugin=org.kde.image

[Containments][2][Applets][3]
immutability=1
plugin=org.kde.plasma.kickoff

[Containments][2][Applets][4]
immutability=1
plugin=org.kde.plasma.taskmanager

[Containments][2][Applets][5]
immutability=1
plugin=org.kde.plasma.systemtray

[Containments][2][Applets][6]
immutability=1
plugin=org.kde.plasma.digitalclock

[Containments][2][Applets][7]
immutability=1
plugin=org.kde.plasma.showdesktop

[Containments][2][General]
AppletOrder=3;4;5;6;7
EOF

echo "FluxLinux: Writing keyboard shortcuts..."
cat > "$KDE_CONFIG/kglobalshortcutsrc" << 'EOF'
[kwin]
Show Desktop=Meta+D,Meta+D,Show Desktop
Window Maximize=Meta+Up,Meta+Up,Maximize Window
Window Close=Alt+F4,Alt+F4,Close Window
Walk Through Windows=Alt+Tab,Alt+Tab,Walk Through Windows
Reverse Walk Through Windows=Alt+Shift+Tab,Alt+Shift+Tab,Reverse Walk Through Windows
Window Fullscreen=Meta+F,none,Make Window Fullscreen
Window Minimize=Meta+M,none,Minimize Window
Window Move=Meta+W,none,Move Window
Window Resize=none,none,Resize Window

[org.kde.konsole.desktop]
NewTab=Ctrl+T,Ctrl+T,New Tab
NewWindow=Ctrl+N,Ctrl+N,New Window

[plasma-desktop]
_launch=none,none,KDE Plasma Desktop
EOF

echo "FluxLinux: Writing font DPI config..."
cat > "$KDE_CONFIG/kcmfonts" << 'EOF'
[General]
forceFontDPI=192
EOF

echo "FluxLinux: Configuring Konsole..."
cat > "$HOME/.local/share/konsole/FluxLinux.profile" << 'EOF'
[Appearance]
ColorScheme=Breeze
Font=Noto Sans Mono,12,-1,5,400,0,0,0,0,0,0,0,0,0,0,1
antialias=true

[General]
Command=/data/data/com.termux/files/usr/bin/bash
Name=FluxLinux
Parent=FALLBACK/

[Interaction Options]
AutoCopySelectedText=true
TrimLeadingWhitespacesInSelectedText=true
TrimTrailingWhitespacesInSelectedText=true

[Scrolling]
HistoryMode=2
HistorySize=10000

[Terminal Features]
BidiRenderingEnabled=true
BlinkingCursorEnabled=false
EOF

cat > "$KDE_CONFIG/konsolerc" << 'EOF'
[Desktop Entry]
DefaultProfile=FluxLinux.profile

[KonsoleWindow]
RememberWindowSize=true

[TabBar]
CloseTabOnMiddleMouseButton=true
TabBarVisibility=ShowTabBarWhenNeeded
EOF

# Set live wallpaper when Plasma is already running; config files handle next launch.
if command -v plasma-apply-wallpaperimage >/dev/null 2>&1; then
    plasma-apply-wallpaperimage "$WALLPAPER_DESKTOP_PATH" 2>/dev/null || true
fi

echo " [✅] KDE settings written"

# ── Step 5: GTK theming (for GTK apps in KDE) ────────────
echo ""
echo "===== Configuring GTK Apps in KDE ====="
mkdir -p "$HOME/.config/gtk-3.0"
cat > "$HOME/.config/gtk-3.0/settings.ini" << EOF
[Settings]
gtk-theme-name=Adwaita-dark
gtk-icon-theme-name=$SEL_ICON
gtk-font-name=Noto Sans 10
gtk-application-prefer-dark-theme=$GTK_DARK
EOF
echo " [✅] GTK theme configured"

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
