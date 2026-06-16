#!/bin/bash
# ============================================================
# FluxLinux — Native Termux XFCE4 Customization
# Location: termux/setup/setup_customization_termux.sh
# Runs on: HOST Termux (native, no container)
# Root required: no
#
# Mirrors the Debian XFCE4 FluxLinux customization for Termux.
# ============================================================

CALLBACK_NAME="customization"
THEME_DIR="$HOME/.themes"
ICON_DIR="$HOME/.icons"
FONT_DIR="$HOME/.fonts/JetBrainsMonoNerd"
WALLPAPER_DIR="$HOME/Pictures/Wallpapers"
XFCONF_DIR="$HOME/.config/xfce4/xfconf/xfce-perchannel-xml"
ASSET_REPO="abhay-byte/fluxlinux"
ASSET_TAG="debian-v1"
BASE_URL="https://github.com/$ASSET_REPO/releases/download/$ASSET_TAG"
TMP_BASE="${TMPDIR:-$PREFIX/tmp}"
mkdir -p "$TMP_BASE" || TMP_BASE="$HOME"

send_callback() {
    RESULT="$1"
    am start -a android.intent.action.VIEW \
      -d "fluxlinux://callback?result=${RESULT}&name=${CALLBACK_NAME}" \
      --flags 0x10000000 2>/dev/null || true
}

handle_error() {
    echo ""
    echo "❌ FluxLinux Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    echo "Please check the error message above for details."
    echo "---------------------------------------------------"
    send_callback "error"
    read -p "Press Enter to acknowledge error and exit..."
    exit 1
}

download_file() {
    URL="$1"
    OUT="$2"
    if command -v curl >/dev/null 2>&1; then
        curl -fL "$URL" -o "$OUT"
    else
        wget -O "$OUT" "$URL"
    fi
}

extract_all_assets() {
    URL="$1"
    TARGET_DIR="$2"
    TEMP_ZIP="$TMP_BASE/$(basename "$URL")"

    echo " - Downloading $(basename "$URL")..."
    download_file "$URL" "$TEMP_ZIP" || handle_error "download $(basename "$URL")"

    echo " - Extracting to $TARGET_DIR..."
    mkdir -p "$TARGET_DIR"
    unzip -q -o "$TEMP_ZIP" -d "$TARGET_DIR" || handle_error "extract $(basename "$URL")"
    rm -f "$TEMP_ZIP"

    # Some release zips wrap everything in a single top-level subdirectory
    # (e.g. theme/Space-transparency/index.theme). If extraction produced
    # exactly one subdir and no top-level files, promote its contents up so
    # the SEL_THEME/SEL_ICON/SEL_CURSOR checks below find them.
    TOP_FILES=$(find "$TARGET_DIR" -mindepth 1 -maxdepth 1 -not -type d | wc -l)
    TOP_DIRS=$(find "$TARGET_DIR" -mindepth 1 -maxdepth 1 -type d | wc -l)
    if [ "$TOP_FILES" -eq 0 ] && [ "$TOP_DIRS" -eq 1 ]; then
        INNER_DIR=$(find "$TARGET_DIR" -mindepth 1 -maxdepth 1 -type d | head -1)
        shopt -s dotglob nullglob
        for f in "$INNER_DIR"/*; do
            mv "$f" "$TARGET_DIR/" 2>/dev/null || true
        done
        shopt -u dotglob nullglob
        rmdir "$INNER_DIR" 2>/dev/null || true
    fi

    find "$TARGET_DIR" -maxdepth 1 -name "*.tar.xz" -exec tar -xf {} -C "$TARGET_DIR" \;
    find "$TARGET_DIR" -maxdepth 1 -name "*.tar.gz" -exec tar -xzf {} -C "$TARGET_DIR" \;
    rm -f "$TARGET_DIR"/*.tar.xz "$TARGET_DIR"/*.tar.gz
}

echo ""
echo "══════════════════════════════════════════════"
echo "  FluxLinux — XFCE4 Customization (Native)"
echo "══════════════════════════════════════════════"
echo ""

echo "FluxLinux: Installing customization tools..."
pkg update -y || handle_error "pkg update"
pkg install -y curl wget unzip tar fontconfig xfce4-goodies || handle_error "customization tools"

if [ -n "$FLUX_THEME" ] && [ "$FLUX_THEME" = "light" ]; then
    echo "FluxLinux: Light Mode Selected."
    SEL_THEME="Space-light"
    SEL_ICON="Papirus"
    SEL_CURSOR="Vimix-cursors"
    SEL_WALLPAPER="fluxlinux-light.png"
else
    echo "FluxLinux: Dark Mode Selected."
    SEL_THEME="Space-transparency"
    SEL_ICON="Papirus-Dark"
    SEL_CURSOR="Vimix-white-cursors"
    SEL_WALLPAPER="fluxlinux-dark.png"
fi

echo "FluxLinux: Installing Themes..."
extract_all_assets "$BASE_URL/theme.zip" "$THEME_DIR"

echo "FluxLinux: Installing Icons..."
extract_all_assets "$BASE_URL/icons.zip" "$ICON_DIR"

echo "FluxLinux: Installing Cursors..."
extract_all_assets "$BASE_URL/cursor.zip" "$ICON_DIR"

echo "FluxLinux: Installing Wallpaper..."
mkdir -p "$WALLPAPER_DIR"
TEMP_WP_ZIP="$TMP_BASE/wallpaper.zip"
download_file "$BASE_URL/wallpaper.zip" "$TEMP_WP_ZIP" || handle_error "wallpaper download"
unzip -q -o -j "$TEMP_WP_ZIP" -d "$WALLPAPER_DIR" || handle_error "wallpaper extract"
rm -f "$TEMP_WP_ZIP"
[ -f "$WALLPAPER_DIR/dark.png" ] && mv "$WALLPAPER_DIR/dark.png" "$WALLPAPER_DIR/fluxlinux-dark.png"
[ -f "$WALLPAPER_DIR/light.png" ] && mv "$WALLPAPER_DIR/light.png" "$WALLPAPER_DIR/fluxlinux-light.png"

echo "FluxLinux: Installing JetBrains Mono Nerd Font..."
mkdir -p "$FONT_DIR"
TEMP_FONT_ZIP="$TMP_BASE/JetBrainsMono.zip"
download_file "https://github.com/ryanoasis/nerd-fonts/releases/latest/download/JetBrainsMono.zip" "$TEMP_FONT_ZIP" || \
    download_file "$BASE_URL/font.zip" "$TEMP_FONT_ZIP" || handle_error "font download"
unzip -q -o "$TEMP_FONT_ZIP" -d "$FONT_DIR" || handle_error "font extract"
rm -f "$TEMP_FONT_ZIP"
fc-cache -fv "$HOME/.fonts" >/dev/null 2>&1 || true

if [ ! -d "$THEME_DIR/$SEL_THEME" ]; then
    handle_error "missing theme $SEL_THEME"
fi
if [ ! -d "$ICON_DIR/$SEL_CURSOR" ]; then
    handle_error "missing cursor $SEL_CURSOR"
fi
if [ ! -e "$WALLPAPER_DIR/$SEL_WALLPAPER" ]; then
    handle_error "missing wallpaper $SEL_WALLPAPER"
fi

WALLPAPER_PATH="$WALLPAPER_DIR/$SEL_WALLPAPER"
mkdir -p "$XFCONF_DIR" "$HOME/.config/gtk-3.0" "$HOME/.config/gtk-4.0"

PANEL_WAS_RUNNING=0
if pgrep -f "xfce4-panel" >/dev/null 2>&1; then
    PANEL_WAS_RUNNING=1
    pkill -TERM -f "xfce4-panel" 2>/dev/null || true
    sleep 1
fi
pkill -TERM -f "xfconfd" 2>/dev/null || true
sleep 1

echo "FluxLinux: Writing GTK settings..."
cat > "$HOME/.config/gtk-3.0/settings.ini" <<EOF
[Settings]
gtk-theme-name=$SEL_THEME
gtk-icon-theme-name=$SEL_ICON
gtk-font-name=JetBrainsMono Nerd Font 10
gtk-cursor-theme-name=$SEL_CURSOR
gtk-cursor-theme-size=52
gtk-application-prefer-dark-theme=1
EOF
cp "$HOME/.config/gtk-3.0/settings.ini" "$HOME/.config/gtk-4.0/settings.ini" 2>/dev/null || true

echo "FluxLinux: Writing xsettings.xml..."
cat > "$XFCONF_DIR/xsettings.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>

<channel name="xsettings" version="1.0">
  <property name="Net" type="empty">
    <property name="ThemeName" type="string" value="$SEL_THEME"/>
    <property name="IconThemeName" type="string" value="$SEL_ICON"/>
    <property name="EnableEventSounds" type="bool" value="false"/>
    <property name="EnableInputFeedbackSounds" type="bool" value="false"/>
  </property>
  <property name="Gtk" type="empty">
    <property name="CursorThemeName" type="string" value="$SEL_CURSOR"/>
    <property name="CursorThemeSize" type="int" value="52"/>
    <property name="FontName" type="string" value="JetBrainsMono Nerd Font 10"/>
    <property name="MonospaceFontName" type="string" value="JetBrainsMono Nerd Font 10"/>
    <property name="ToolbarStyle" type="string" value="icons"/>
    <property name="MenuImages" type="bool" value="true"/>
    <property name="ButtonImages" type="bool" value="true"/>
  </property>
  <property name="Gdk" type="empty">
    <property name="WindowScalingFactor" type="int" value="2"/>
  </property>
</channel>
EOF

echo "FluxLinux: Writing xfwm4.xml..."
cat > "$XFCONF_DIR/xfwm4.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>

<channel name="xfwm4" version="1.0">
  <property name="general" type="empty">
    <property name="theme" type="string" value="$SEL_THEME"/>
    <property name="title_font" type="string" value="JetBrainsMono Nerd Font Bold 10"/>
    <property name="button_layout" type="string" value="O|HMC"/>
    <property name="placement_ratio" type="int" value="20"/>
    <property name="scroll_workspaces" type="bool" value="false"/>
    <property name="show_dock_shadow" type="bool" value="true"/>
    <property name="show_frame_shadow" type="bool" value="true"/>
    <property name="snap_to_border" type="bool" value="true"/>
    <property name="snap_to_windows" type="bool" value="true"/>
    <property name="use_compositing" type="bool" value="false"/>
    <property name="tile_on_move" type="bool" value="true"/>
    <property name="wrap_windows" type="bool" value="true"/>
  </property>
</channel>
EOF

echo "FluxLinux: Writing xfce4-desktop.xml..."
MONITORS="monitor0 monitor1 monitorVNC-0 monitorbuiltin builtin monitorHDMI-A-0 monitorVirtual-0 monitorVirtual-1 monitorVirtual1"
MONITOR_PROPS=""
for M in $MONITORS; do
    MONITOR_PROPS="$MONITOR_PROPS
    <property name=\"$M\" type=\"empty\">
      <property name=\"workspace0\" type=\"empty\">
        <property name=\"last-image\" type=\"string\" value=\"$WALLPAPER_PATH\"/>
        <property name=\"image-style\" type=\"int\" value=\"5\"/>
        <property name=\"color-style\" type=\"int\" value=\"0\"/>
      </property>
    </property>"
done

cat > "$XFCONF_DIR/xfce4-desktop.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>

<channel name="xfce4-desktop" version="1.0">
  <property name="backdrop" type="empty">
    <property name="screen0" type="empty">$MONITOR_PROPS
    </property>
  </property>
  <property name="desktop-icons" type="empty">
    <property name="style" type="int" value="2"/>
    <property name="file-icons" type="empty">
      <property name="show-home" type="bool" value="true"/>
      <property name="show-filesystem" type="bool" value="false"/>
      <property name="show-trash" type="bool" value="true"/>
      <property name="show-removable" type="bool" value="true"/>
    </property>
  </property>
</channel>
EOF

echo "FluxLinux: Writing XFCE4 panel..."
cat > "$XFCONF_DIR/xfce4-panel.xml" <<'EOF'
<?xml version="1.1" encoding="UTF-8"?>
<channel name="xfce4-panel" version="1.0">
  <property name="configver" type="int" value="2"/>
  <property name="panels" type="array">
    <value type="int" value="1"/>
    <property name="dark-mode" type="bool" value="true"/>
    <property name="panel-1" type="empty">
      <property name="position" type="string" value="p=6;x=0;y=0"/>
      <property name="length" type="double" value="100"/>
      <property name="position-locked" type="bool" value="true"/>
      <property name="icon-size" type="uint" value="16"/>
      <property name="size" type="uint" value="25"/>
      <property name="plugin-ids" type="array">
        <value type="int" value="1"/>
        <value type="int" value="2"/>
        <value type="int" value="3"/>
        <value type="int" value="4"/>
        <value type="int" value="5"/>
        <value type="int" value="6"/>
        <value type="int" value="7"/>
        <value type="int" value="8"/>
        <value type="int" value="9"/>
        <value type="int" value="10"/>
        <value type="int" value="11"/>
      </property>
    </property>
  </property>
  <property name="plugins" type="empty">
    <property name="plugin-1" type="string" value="applicationsmenu">
      <property name="button-title" type="string" value="Menu"/>
      <property name="button-icon" type="string" value="open-menu"/>
      <property name="small" type="bool" value="true"/>
      <property name="show-tooltips" type="bool" value="false"/>
      <property name="show-generic-names" type="bool" value="false"/>
      <property name="custom-menu" type="bool" value="false"/>
      <property name="show-menu-icons" type="bool" value="true"/>
      <property name="show-button-title" type="bool" value="false"/>
    </property>
    <property name="plugin-2" type="string" value="tasklist">
      <property name="grouping" type="uint" value="1"/>
      <property name="flat-buttons" type="bool" value="false"/>
      <property name="show-only-minimized" type="bool" value="false"/>
      <property name="include-all-workspaces" type="bool" value="false"/>
      <property name="show-wireframes" type="bool" value="false"/>
      <property name="show-labels" type="bool" value="false"/>
    </property>
    <property name="plugin-3" type="string" value="separator">
      <property name="expand" type="bool" value="true"/>
      <property name="style" type="uint" value="0"/>
    </property>
    <property name="plugin-4" type="string" value="systray"/>
    <property name="plugin-5" type="string" value="separator">
      <property name="style" type="uint" value="2"/>
    </property>
    <property name="plugin-6" type="string" value="clock">
      <property name="digital-layout" type="uint" value="1"/>
      <property name="mode" type="uint" value="4"/>
      <property name="show-seconds" type="bool" value="true"/>
      <property name="show-inactive" type="bool" value="false"/>
    </property>
    <property name="plugin-7" type="string" value="separator">
      <property name="style" type="uint" value="0"/>
    </property>
    <property name="plugin-8" type="string" value="actions">
      <property name="items" type="array">
        <value type="string" value="+lock-screen"/>
        <value type="string" value="+logout"/>
        <value type="string" value="+separator"/>
        <value type="string" value="+shutdown"/>
      </property>
    </property>
    <property name="plugin-9" type="string" value="separator">
      <property name="style" type="uint" value="0"/>
    </property>
    <property name="plugin-10" type="string" value="pager">
      <property name="rows" type="uint" value="1"/>
    </property>
    <property name="plugin-11" type="string" value="windowmenu"/>
  </property>
</channel>
EOF

echo "FluxLinux: Checking core panel plugins..."
for PLUGIN in applicationsmenu tasklist separator systray clock actions pager windowmenu; do
    if find "$PREFIX" -path "*/xfce4/panel/plugins/$PLUGIN.desktop" -o -path "*/xfce4/panel/plugins/lib${PLUGIN}.so" 2>/dev/null | grep -q .; then
        echo " [✅] Panel plugin: $PLUGIN"
    else
        echo " [⚠️] Panel plugin not found by file scan: $PLUGIN"
    fi
done

if [ "$PANEL_WAS_RUNNING" -eq 1 ]; then
    xfce4-panel >/dev/null 2>&1 &
fi

send_callback "success"

echo ""
echo "🎨 XFCE4 customization applied!"
echo "   Theme: $SEL_THEME | Icons: $SEL_ICON | Cursor: $SEL_CURSOR"
echo "   Restart XFCE4 for changes to fully take effect."
read -p "Press Enter to close..."
