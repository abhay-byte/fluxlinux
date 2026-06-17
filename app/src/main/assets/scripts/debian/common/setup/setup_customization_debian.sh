#!/bin/bash
# setup_customization_debian.sh
# Applies "FluxLinux Pro" branding and customization to Debian XFCE4 Desktop
# Works for both Chroot and Proot environments (run as root, switches to user 'flux')

CUSTOM_USER="flux"
CUSTOM_GROUP="users"
USER_HOME="/home/$CUSTOM_USER"
ASSETS_DIR="$(dirname "$0")/../../../assets"
THEME_DIR="/usr/share/themes"
ICON_DIR="/usr/share/icons"

# Error Handler
handle_error() {
    echo ""
    echo "❌ FluxLinux Pro Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    echo "Please check the error message above for details."
    echo "---------------------------------------------------"
    read -p "Press Enter to acknowledge error and exit..."
    exit 1
}

echo "FluxLinux Pro: Starting XFCE4 Customization..."

# 1. Install Dependencies
echo "FluxLinux Pro: Installing customization tools..."
export DEBIAN_FRONTEND=noninteractive
apt update -y
apt install -y xfce4-goodies curl fastfetch wget unzip fontconfig locales || handle_error "Dependency Installation"

# Setup Locales for proper font rendering in ZSH/Terminal
echo "FluxLinux Pro: Setting up locales..."
echo "en_US.UTF-8 UTF-8" > /etc/locale.gen
locale-gen
update-locale LANG=en_US.UTF-8

# 2. Deploy Assets (From GitHub Release debian-v1)
ASSET_REPO="abhay-byte/fluxlinux"
ASSET_TAG="debian-v1"
BASE_URL="https://github.com/$ASSET_REPO/releases/download/$ASSET_TAG"

echo "FluxLinux Pro: Downloading assets from $BASE_URL..."

# Helper to extract all contents
extract_all_assets() {
    local URL="$1"
    local TARGET_DIR="$2"
    local TEMP_ZIP="/tmp/$(basename "$URL")"
    
    echo " - Downloading $(basename "$URL")..."
    wget -q --show-progress "$URL" -O "$TEMP_ZIP"
    
    echo " - Extracting to $TARGET_DIR..."
    unzip -q -o "$TEMP_ZIP" -d "$TARGET_DIR"
    rm "$TEMP_ZIP"

    # Extract any nested tarballs found in the target dir
    find "$TARGET_DIR" -maxdepth 1 -name "*.tar.xz" -exec tar -xf {} -C "$TARGET_DIR" \;
    find "$TARGET_DIR" -maxdepth 1 -name "*.tar.gz" -exec tar -xzf {} -C "$TARGET_DIR" \;
    
    # Cleanup tars
    rm -f "$TARGET_DIR"/*.tar.xz "$TARGET_DIR"/*.tar.gz
}

# 3. Theme Selection Prompt
if [ -n "$FLUX_THEME" ]; then
    echo "FluxLinux Pro: Auto-applying Theme: $FLUX_THEME"
    if [ "$FLUX_THEME" == "light" ]; then
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

if [ "$THEME_CHOICE" == "2" ]; then
    echo "FluxLinux Pro: Light Mode Selected."
    SEL_THEME="Space-light"
    SEL_ICON="Papirus" # Light icons
    SEL_CURSOR="Vimix-cursors" # Dark cursor for light theme (better contrast)
    SEL_WALLPAPER="fluxlinux-light.png"
else
    echo "FluxLinux Pro: Dark Mode Selected."
    SEL_THEME="Space-transparency"
    SEL_ICON="Papirus-Dark" # Dark icons
    SEL_CURSOR="Vimix-white-cursors" # White cursor for dark theme (better contrast)
    SEL_WALLPAPER="fluxlinux-dark.png"
fi

# Install Themes (Both)
echo "FluxLinux Pro: Installing Themes..."
mkdir -p "$THEME_DIR"
extract_all_assets "$BASE_URL/theme.zip" "$THEME_DIR"

# Install Icons
echo "FluxLinux Pro: Installing Icons..."
mkdir -p "$ICON_DIR"
extract_all_assets "$BASE_URL/icons.zip" "$ICON_DIR"
# Icons are assumed to have known names or we use the selected one directly.
# SEL_ICON is already set based on theme choice.

# Install Cursors (Both variants)
echo "FluxLinux Pro: Installing Cursors..."
extract_all_assets "$BASE_URL/cursor.zip" "$ICON_DIR"

# Wallpaper Setup
WALLPAPER_DIR="$USER_HOME/Pictures/Wallpapers"
mkdir -p "$WALLPAPER_DIR"
chown -R "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/Pictures" 2>/dev/null

echo "FluxLinux Pro: Downloading Wallpaper..."
TEMP_WP_ZIP="/tmp/wallpaper.zip"
wget -q --show-progress "$BASE_URL/wallpaper.zip" -O "$TEMP_WP_ZIP"
unzip -o -j "$TEMP_WP_ZIP" -d "$WALLPAPER_DIR"
rm "$TEMP_WP_ZIP"
[ -f "$WALLPAPER_DIR/dark.png" ] && mv "$WALLPAPER_DIR/dark.png" "$WALLPAPER_DIR/fluxlinux-dark.png"
[ -f "$WALLPAPER_DIR/light.png" ] && mv "$WALLPAPER_DIR/light.png" "$WALLPAPER_DIR/fluxlinux-light.png"
chown "$CUSTOM_USER:$CUSTOM_GROUP" "$WALLPAPER_DIR"/*


# Install JetBrains Mono Nerd Font
# Using proper Debian font location: /usr/share/fonts/truetype/
FONT_DIR="/usr/share/fonts/truetype/jetbrains-mono-nerd"
FONT_INSTALLED=false

# Check if font already installed
if fc-list | grep -qi "JetBrainsMono Nerd"; then
    echo "FluxLinux Pro: JetBrains Mono Nerd Font already installed."
    FONT_INSTALLED=true
fi

if [ "$FONT_INSTALLED" = false ]; then
    echo "FluxLinux Pro: Installing JetBrains Mono Nerd Font..."
    
    # Create font directory
    mkdir -p "$FONT_DIR"
    
    # Download from official Nerd Fonts GitHub releases
    NERD_FONT_URL="https://github.com/ryanoasis/nerd-fonts/releases/latest/download/JetBrainsMono.zip"
    TEMP_ZIP="/tmp/JetBrainsMono.zip"
    
    echo " - Downloading JetBrains Mono Nerd Font..."
    wget -q --show-progress "$NERD_FONT_URL" -O "$TEMP_ZIP" || {
        echo "FluxLinux Pro: Direct download failed, trying from release..."
        wget -q --show-progress "$BASE_URL/font.zip" -O "$TEMP_ZIP" || handle_error "Font Download"
    }
    
    # Extract only .ttf files (ignore nested folders, Windows-only formats)
    echo " - Extracting font files..."
    unzip -o -j "$TEMP_ZIP" "*.ttf" -d "$FONT_DIR" 2>/dev/null || \
    unzip -o "$TEMP_ZIP" -d "$FONT_DIR" 2>/dev/null
    
    # Clean up any non-font files that might have been extracted
    find "$FONT_DIR" -type f ! -name "*.ttf" ! -name "*.otf" -delete 2>/dev/null
    
    # Remove temp file
    rm -f "$TEMP_ZIP"
    
    # Set correct permissions
    chmod 644 "$FONT_DIR"/*.ttf 2>/dev/null
    chmod 644 "$FONT_DIR"/*.otf 2>/dev/null
    
    # Rebuild font cache (system-wide, verbose)
    echo " - Rebuilding font cache..."
    fc-cache -fv "$FONT_DIR"
    
    # Also rebuild user cache
    su -s /bin/bash - "$CUSTOM_USER" -c "fc-cache -f" 2>/dev/null
    
    # Verify installation
    if fc-list | grep -qi "JetBrainsMono Nerd"; then
        echo "FluxLinux Pro: ✓ JetBrains Mono Nerd Font installed successfully!"
    else
        echo "FluxLinux Pro: ⚠ Font may not be properly registered. Checking installed files..."
        ls -la "$FONT_DIR"
    fi
fi
# 4. Apply Settings for User 'flux'
# Write directly to XML config files (dbus-launch creates ephemeral sessions that don't persist)
echo "FluxLinux Pro: Applying XFCE4 Settings..."

XFCONF_DIR="$USER_HOME/.config/xfce4/xfconf/xfce-perchannel-xml"
mkdir -p "$XFCONF_DIR"

# Generate xsettings.xml (Theme, Icons, Cursor, Fonts, Scaling)
echo "FluxLinux Pro: Writing xsettings.xml..."
cat <<EOF > "$XFCONF_DIR/xsettings.xml"
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
    <property name="MonospaceFontName" type="string" value="JetBrainsMono Nerd Font Mono 10"/>
    <property name="DecorationLayout" type="string" value="menu:minimize,maximize,close"/>
  </property>
  <property name="Gdk" type="empty">
    <property name="WindowScalingFactor" type="int" value="2"/>
  </property>
  <property name="Xft" type="empty">
    <property name="Antialias" type="int" value="1"/>
    <property name="HintStyle" type="string" value="hintslight"/>
    <property name="RGBA" type="string" value="rgb"/>
  </property>
</channel>
EOF

# Generate xfwm4.xml (Window Manager Theme and Title Font)
echo "FluxLinux Pro: Writing xfwm4.xml..."
cat <<EOF > "$XFCONF_DIR/xfwm4.xml"
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

# Generate xfce4-desktop.xml (Wallpaper)
echo "FluxLinux Pro: Writing xfce4-desktop.xml..."
WALLPAPER_PATH="$WALLPAPER_DIR/$SEL_WALLPAPER"
MONITORS="monitor0 monitor1 monitorVNC-0 monitorbuiltin builtin monitorHDMI-A-0 monitorVirtual-0 monitorVirtual1"

# Build monitor properties dynamically
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

cat <<EOF > "$XFCONF_DIR/xfce4-desktop.xml"
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

# Fix ownership
chown -R "$CUSTOM_USER:$CUSTOM_GROUP" "$XFCONF_DIR"
echo "FluxLinux Pro: XFCE4 settings applied successfully!"

# Generate xfce4-keyboard-shortcuts.xml (Custom Keyboard Shortcuts)
# Note: Angle brackets in key names must be XML-escaped as &lt; and &gt;
echo "FluxLinux Pro: Writing keyboard shortcuts..."
cat <<'SHORTCUTEOF' > "$XFCONF_DIR/xfce4-keyboard-shortcuts.xml"
<?xml version="1.1" encoding="UTF-8"?>

<channel name="xfce4-keyboard-shortcuts" version="1.0">
  <property name="commands" type="empty">
    <property name="default" type="empty">
      <property name="&lt;Alt&gt;F1" type="empty"/>
      <property name="&lt;Alt&gt;F2" type="empty">
        <property name="startup-notify" type="empty"/>
      </property>
      <property name="&lt;Alt&gt;F3" type="empty">
        <property name="startup-notify" type="empty"/>
      </property>
      <property name="&lt;Primary&gt;&lt;Alt&gt;Delete" type="empty"/>
      <property name="&lt;Primary&gt;&lt;Alt&gt;l" type="empty"/>
      <property name="&lt;Primary&gt;&lt;Alt&gt;t" type="empty"/>
      <property name="XF86Display" type="empty"/>
      <property name="&lt;Super&gt;p" type="empty"/>
      <property name="&lt;Primary&gt;Escape" type="empty"/>
      <property name="XF86WWW" type="empty"/>
      <property name="HomePage" type="empty"/>
      <property name="XF86Mail" type="empty"/>
      <property name="Print" type="empty"/>
      <property name="&lt;Alt&gt;Print" type="empty"/>
      <property name="&lt;Shift&gt;Print" type="empty"/>
      <property name="&lt;Super&gt;e" type="empty"/>
      <property name="&lt;Primary&gt;&lt;Alt&gt;f" type="empty"/>
      <property name="&lt;Primary&gt;&lt;Alt&gt;Escape" type="empty"/>
      <property name="&lt;Primary&gt;&lt;Shift&gt;Escape" type="empty"/>
      <property name="&lt;Super&gt;r" type="empty">
        <property name="startup-notify" type="empty"/>
      </property>
      <property name="&lt;Alt&gt;&lt;Super&gt;s" type="empty"/>
    </property>
    <property name="custom" type="empty">
      <property name="&lt;Primary&gt;w" type="string" value="xfce4-appfinder"/>
      <property name="&lt;Primary&gt;b" type="string" value="exo-open --launch WebBrowser"/>
      <property name="&lt;Primary&gt;e" type="string" value="thunar"/>
      <property name="&lt;Primary&gt;t" type="string" value="exo-open --launch TerminalEmulator"/>
      <property name="&lt;Primary&gt;&lt;Shift&gt;s" type="string" value="xfce4-screenshooter -r"/>
      <property name="&lt;Primary&gt;q" type="string" value="xfce4-session-logout"/>
      <property name="&lt;Primary&gt;at" type="string" value="xfce4-screenshooter -r"/>
      <property name="&lt;Alt&gt;F2" type="string" value="xfce4-appfinder --collapsed">
        <property name="startup-notify" type="bool" value="true"/>
      </property>
      <property name="&lt;Alt&gt;Print" type="string" value="xfce4-screenshooter -w"/>
      <property name="&lt;Super&gt;r" type="string" value="xfce4-appfinder -c">
        <property name="startup-notify" type="bool" value="true"/>
      </property>
      <property name="XF86WWW" type="string" value="exo-open --launch WebBrowser"/>
      <property name="XF86Mail" type="string" value="exo-open --launch MailReader"/>
      <property name="&lt;Alt&gt;F3" type="string" value="xfce4-appfinder">
        <property name="startup-notify" type="bool" value="true"/>
      </property>
      <property name="Print" type="string" value="xfce4-screenshooter"/>
      <property name="&lt;Primary&gt;Escape" type="string" value="xfdesktop --menu"/>
      <property name="&lt;Shift&gt;Print" type="string" value="xfce4-screenshooter -r"/>
      <property name="&lt;Primary&gt;&lt;Alt&gt;Delete" type="string" value="xfce4-session-logout"/>
      <property name="&lt;Alt&gt;&lt;Super&gt;s" type="string" value="orca"/>
      <property name="&lt;Primary&gt;&lt;Alt&gt;t" type="string" value="exo-open --launch TerminalEmulator"/>
      <property name="&lt;Primary&gt;&lt;Alt&gt;f" type="string" value="thunar"/>
      <property name="&lt;Primary&gt;&lt;Alt&gt;l" type="string" value="xflock4"/>
      <property name="&lt;Alt&gt;F1" type="string" value="xfce4-popup-applicationsmenu"/>
      <property name="&lt;Super&gt;p" type="string" value="xfce4-display-settings --minimal"/>
      <property name="&lt;Primary&gt;&lt;Shift&gt;Escape" type="string" value="xfce4-taskmanager"/>
      <property name="&lt;Super&gt;e" type="string" value="thunar"/>
      <property name="&lt;Primary&gt;&lt;Alt&gt;Escape" type="string" value="xkill"/>
      <property name="HomePage" type="string" value="exo-open --launch WebBrowser"/>
      <property name="XF86Display" type="string" value="xfce4-display-settings --minimal"/>
      <property name="override" type="bool" value="true"/>
    </property>
  </property>
  <property name="xfwm4" type="empty">
    <property name="default" type="empty">
      <property name="&lt;Alt&gt;Insert" type="empty"/>
      <property name="Escape" type="empty"/>
      <property name="Left" type="empty"/>
      <property name="Right" type="empty"/>
      <property name="Up" type="empty"/>
      <property name="Down" type="empty"/>
      <property name="&lt;Alt&gt;Tab" type="empty"/>
      <property name="&lt;Alt&gt;&lt;Shift&gt;Tab" type="empty"/>
      <property name="&lt;Alt&gt;Delete" type="empty"/>
      <property name="&lt;Alt&gt;F4" type="empty"/>
      <property name="&lt;Alt&gt;F6" type="empty"/>
      <property name="&lt;Alt&gt;F7" type="empty"/>
      <property name="&lt;Alt&gt;F8" type="empty"/>
      <property name="&lt;Alt&gt;F9" type="empty"/>
      <property name="&lt;Alt&gt;F10" type="empty"/>
      <property name="&lt;Alt&gt;F11" type="empty"/>
      <property name="&lt;Alt&gt;F12" type="empty"/>
      <property name="&lt;Primary&gt;&lt;Alt&gt;d" type="empty"/>
      <property name="&lt;Super&gt;Tab" type="empty"/>
      <property name="&lt;Super&gt;KP_Left" type="empty"/>
      <property name="&lt;Super&gt;KP_Right" type="empty"/>
      <property name="&lt;Super&gt;KP_Down" type="empty"/>
      <property name="&lt;Super&gt;KP_Up" type="empty"/>
    </property>
    <property name="custom" type="empty">
      <property name="&lt;Alt&gt;F4" type="string" value="close_window_key"/>
      <property name="&lt;Super&gt;KP_Down" type="string" value="tile_down_key"/>
      <property name="&lt;Super&gt;KP_Up" type="string" value="tile_up_key"/>
      <property name="&lt;Super&gt;KP_Right" type="string" value="tile_right_key"/>
      <property name="&lt;Super&gt;KP_Left" type="string" value="tile_left_key"/>
      <property name="Right" type="string" value="right_key"/>
      <property name="Down" type="string" value="down_key"/>
      <property name="&lt;Alt&gt;Tab" type="string" value="cycle_windows_key"/>
      <property name="&lt;Alt&gt;F6" type="string" value="stick_window_key"/>
      <property name="&lt;Alt&gt;F10" type="string" value="maximize_window_key"/>
      <property name="&lt;Alt&gt;Delete" type="string" value="del_workspace_key"/>
      <property name="&lt;Super&gt;Tab" type="string" value="switch_window_key"/>
      <property name="&lt;Primary&gt;&lt;Alt&gt;d" type="string" value="show_desktop_key"/>
      <property name="&lt;Alt&gt;F7" type="string" value="move_window_key"/>
      <property name="Up" type="string" value="up_key"/>
      <property name="&lt;Alt&gt;F11" type="string" value="fullscreen_key"/>
      <property name="Escape" type="string" value="cancel_key"/>
      <property name="&lt;Alt&gt;&lt;Shift&gt;Tab" type="string" value="cycle_reverse_windows_key"/>
      <property name="&lt;Alt&gt;F12" type="string" value="above_key"/>
      <property name="&lt;Alt&gt;F8" type="string" value="resize_window_key"/>
      <property name="&lt;Alt&gt;F9" type="string" value="hide_window_key"/>
      <property name="Left" type="string" value="left_key"/>
      <property name="&lt;Alt&gt;Insert" type="string" value="add_workspace_key"/>
      <property name="override" type="bool" value="true"/>
    </property>
  </property>
  <property name="providers" type="array">
    <value type="string" value="xfwm4"/>
    <value type="string" value="commands"/>
  </property>
</channel>
SHORTCUTEOF

echo "FluxLinux Pro: Keyboard shortcuts configured!"


# 5. Configure XFCE4 Panel
echo "FluxLinux Pro: Configuring Panel..."
PANEL_CONFIG_DIR="$USER_HOME/.config/xfce4/xfconf/xfce-perchannel-xml"
mkdir -p "$PANEL_CONFIG_DIR"

cat <<'EOF' > "$PANEL_CONFIG_DIR/xfce4-panel.xml"
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
        <value type="int" value="33"/>
        <value type="int" value="21"/>
        <value type="int" value="32"/>
        <value type="int" value="23"/>
        <value type="int" value="31"/>
        <value type="int" value="24"/>
        <value type="int" value="34"/>
        <value type="int" value="5"/>
        <value type="int" value="6"/>
        <value type="int" value="7"/>
        <value type="int" value="8"/>
        <value type="int" value="9"/>
        <value type="int" value="10"/>
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
    <property name="plugin-5" type="string" value="separator">
      <property name="style" type="uint" value="2"/>
    </property>
    <property name="plugin-6" type="string" value="systray">
      <property name="square-icons" type="bool" value="true"/>
    </property>
    <property name="plugin-7" type="string" value="separator">
      <property name="style" type="uint" value="0"/>
    </property>
    <property name="plugin-8" type="string" value="clock">
      <property name="digital-layout" type="uint" value="1"/>
      <property name="mode" type="uint" value="4"/>
      <property name="show-seconds" type="bool" value="true"/>
      <property name="show-inactive" type="bool" value="true"/>
      <property name="show-meridiem" type="bool" value="false"/>
      <property name="timezone" type="string" value="Asia/Kolkata"/>
    </property>
    <property name="plugin-9" type="string" value="separator">
      <property name="style" type="uint" value="0"/>
    </property>
    <property name="plugin-10" type="string" value="actions"/>
    <property name="plugin-21" type="string" value="cpugraph">
      <property name="update-interval" type="int" value="2"/>
      <property name="time-scale" type="int" value="0"/>
      <property name="size" type="int" value="16"/>
      <property name="mode" type="int" value="0"/>
      <property name="color-mode" type="int" value="0"/>
      <property name="frame" type="int" value="1"/>
      <property name="border" type="int" value="1"/>
      <property name="bars" type="int" value="1"/>
      <property name="per-core" type="int" value="0"/>
      <property name="tracked-core" type="int" value="0"/>
      <property name="in-terminal" type="int" value="1"/>
      <property name="startup-notification" type="int" value="0"/>
      <property name="load-threshold" type="int" value="0"/>
      <property name="smt-stats" type="int" value="1"/>
      <property name="smt-issues" type="int" value="1"/>
      <property name="per-core-spacing" type="int" value="1"/>
      <property name="command" type="string" value=""/>
      <property name="background" type="array">
        <value type="double" value="1"/>
        <value type="double" value="1"/>
        <value type="double" value="1"/>
        <value type="double" value="0"/>
      </property>
      <property name="foreground-1" type="array">
        <value type="double" value="0"/>
        <value type="double" value="1"/>
        <value type="double" value="0"/>
        <value type="double" value="1"/>
      </property>
      <property name="foreground-2" type="array">
        <value type="double" value="1"/>
        <value type="double" value="0"/>
        <value type="double" value="0"/>
        <value type="double" value="1"/>
      </property>
      <property name="foreground-3" type="array">
        <value type="double" value="0"/>
        <value type="double" value="0"/>
        <value type="double" value="1"/>
        <value type="double" value="1"/>
      </property>
      <property name="smt-issues-color" type="array">
        <value type="double" value="0.90000000000000002"/>
        <value type="double" value="0"/>
        <value type="double" value="0"/>
        <value type="double" value="1"/>
      </property>
      <property name="foreground-system" type="array">
        <value type="double" value="0.90000000000000002"/>
        <value type="double" value="0.10000000000000001"/>
        <value type="double" value="0.10000000000000001"/>
        <value type="double" value="1"/>
      </property>
      <property name="foreground-user" type="array">
        <value type="double" value="0.10000000000000001"/>
        <value type="double" value="0.40000000000000002"/>
        <value type="double" value="0.90000000000000002"/>
        <value type="double" value="1"/>
      </property>
      <property name="foreground-nice" type="array">
        <value type="double" value="0.90000000000000002"/>
        <value type="double" value="0.80000000000000004"/>
        <value type="double" value="0.20000000000000001"/>
        <value type="double" value="1"/>
      </property>
      <property name="foreground-iowait" type="array">
        <value type="double" value="0.20000000000000001"/>
        <value type="double" value="0.90000000000000002"/>
        <value type="double" value="0.40000000000000002"/>
        <value type="double" value="1"/>
      </property>
    </property>
    <property name="plugin-23" type="string" value="fsguard">
      <property name="display-meter" type="bool" value="false"/>
      <property name="show-size" type="bool" value="true"/>
    </property>
    <property name="plugin-24" type="string" value="genmon">
      <property name="command" type="string" value="/bin/bash -c &quot;free -m | awk '/Mem:/ {r=\$3/1024; t=\$2/1024} /Swap:/ {s=\$3/1024; st=\$2/1024} END {printf \&quot;&lt;txt&gt;RAM %.1f/%.1fGB | SWAP %.1f/%.1fGB&lt;/txt&gt;\&quot;, r, t, s, st}'&quot;"/>
      <property name="update-interval" type="uint" value="2000"/>
      <property name="use-label" type="bool" value="false"/>
      <property name="font" type="string" value="JetBrainsMono Nerd Font 10"/>
    </property>
    <property name="plugin-31" type="string" value="separator"/>
    <property name="plugin-32" type="string" value="separator"/>
    <property name="plugin-33" type="string" value="separator"/>
    <property name="plugin-34" type="string" value="separator"/>
  </property>
</channel>
EOF

chown -R "$CUSTOM_USER:$CUSTOM_GROUP" "$PANEL_CONFIG_DIR"

# Create plugin configuration files
PLUGIN_CONFIG_DIR="$USER_HOME/.config/xfce4/panel"
mkdir -p "$PLUGIN_CONFIG_DIR"

# Create cpufreq plugin configuration
cat <<'EOF' > "$PLUGIN_CONFIG_DIR/cpufreq-20.rc"
show_icon=false
show_label_governor=false
keep_compact=true
one_line=true
EOF

# Create info.sh script (RAM, SWAP, and Battery)
cat <<'EOF' > "$USER_HOME/.config/info.sh"
#!/bin/bash

# Get combined memory percentage (RAM + SWAP)
MEM_PERCENT=$(free -m | awk '
/Mem:/ {
    mem_used = $3
    mem_total = $2
}
/Swap:/ {
    swap_used = $3
    swap_total = $2
}
END {
    total = mem_total + swap_total
    used = mem_used + swap_used
    if (total > 0) {
        percent = (used / total) * 100
        printf "%.0f", percent
    } else {
        print "0"
    }
}')

# Get battery info from sysfs
BATTERY_PATH="/sys/class/power_supply/battery"
if [ -f "$BATTERY_PATH/capacity" ] && [ -f "$BATTERY_PATH/status" ]; then
    CAPACITY=$(cat "$BATTERY_PATH/capacity" 2>/dev/null || echo "0")
    STATUS=$(cat "$BATTERY_PATH/status" 2>/dev/null || echo "Unknown")
    
    # Choose indicator based on status
    if [ "$STATUS" = "Charging" ]; then
        INDICATOR="CHG"
    elif [ "$STATUS" = "Full" ]; then
        INDICATOR="FULL"
    else
        INDICATOR="BAT"
    fi
    
    BATTERY_INFO=" | ${INDICATOR} ${CAPACITY}%"
else
    BATTERY_INFO=""
fi

# Output in genmon XML format
echo "<txt>MEM ${MEM_PERCENT}%${BATTERY_INFO}</txt>"
EOF

chmod +x "$USER_HOME/.config/info.sh"

# Create genmon plugin configuration (both 19 and 24)
cat <<EOF > "$PLUGIN_CONFIG_DIR/genmon-19.rc"
Command=$USER_HOME/.config/info.sh
UseLabel=0
Text=(genmon)
UpdatePeriod=1000
Font=JetBrainsMono Nerd Font 10
EOF

cat <<EOF > "$PLUGIN_CONFIG_DIR/genmon-24.rc"
Command=$USER_HOME/.config/info.sh
UseLabel=0
Text=(genmon)
UpdatePeriod=1000
Font=JetBrainsMono Nerd Font 10
EOF

chown -R "$CUSTOM_USER:$CUSTOM_GROUP" "$PLUGIN_CONFIG_DIR"
chown "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/.config/info.sh"


# 6. Configure Terminal (Direct Config File)
echo "FluxLinux Pro: Configuring Terminal..."
TERM_CONFIG_DIR="$USER_HOME/.config/xfce4/terminal"
mkdir -p "$TERM_CONFIG_DIR"
cat <<EOF > "$TERM_CONFIG_DIR/terminalrc"
[Configuration]
FontUseSystem=TRUE
FontName=JetBrainsMono Nerd Font 12
MiscAlwaysShowTabs=FALSE
MiscBell=FALSE
MiscBordersDefault=TRUE
MiscCursorBlinks=FALSE
MiscCursorShape=TERMINAL_CURSOR_SHAPE_IBEAM
MiscDefaultGeometry=80x24
MiscInheritGeometry=FALSE
MiscMenubarDefault=FALSE
MiscMouseAutohide=FALSE
MiscToolbarDefault=FALSE
MiscConfirmClose=TRUE
MiscCycleTabs=TRUE
MiscTabCloseButtons=TRUE
MiscTabCloseMiddleClick=TRUE
MiscTabPosition=TERMINAL_TAB_POSITION_TOP
MiscHighlightUrls=TRUE
MiscScrollAlternateScreen=TRUE
ScrollingLines=1000
BackgroundMode=TERMINAL_BACKGROUND_TRANSPARENT
BackgroundDarkness=0.7
EOF
chown -R "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/.config"


# 7. Configure Zsh and Terminal Enhancements
echo "FluxLinux Pro: Configuring Zsh and Terminal..."

# Install zsh if not already installed
echo "FluxLinux Pro: Installing zsh..."
apt-get install -y zsh 2>/dev/null

# Install Oh My Zsh for flux user
# Install Oh My Zsh for flux user
echo "FluxLinux Pro: Installing Oh My Zsh..."

# 1. Check for corrupt installation (folder exists but missing core script)
if [ -d "$USER_HOME/.oh-my-zsh" ] && [ ! -f "$USER_HOME/.oh-my-zsh/oh-my-zsh.sh" ]; then
    echo "FluxLinux Pro: Detected corrupt Oh My Zsh installation. Removing..."
    rm -rf "$USER_HOME/.oh-my-zsh"
fi

# 2. Install if missing
if [ ! -d "$USER_HOME/.oh-my-zsh" ]; then
    su -s /bin/bash - "$CUSTOM_USER" -c 'RUNZSH=no CHSH=no sh -c "$(curl -fsSL https://raw.githubusercontent.com/ohmyzsh/ohmyzsh/master/tools/install.sh)"' 2>/dev/null
fi

# Set ZSH_CUSTOM path
ZSH_CUSTOM="$USER_HOME/.oh-my-zsh/custom"

# Install zsh plugins
echo "FluxLinux Pro: Installing Zsh plugins..."
su -s /bin/bash - "$CUSTOM_USER" -c "git clone https://github.com/zsh-users/zsh-autosuggestions '$ZSH_CUSTOM/plugins/zsh-autosuggestions'" 2>/dev/null
su -s /bin/bash - "$CUSTOM_USER" -c "git clone https://github.com/zsh-users/zsh-syntax-highlighting '$ZSH_CUSTOM/plugins/zsh-syntax-highlighting'" 2>/dev/null
su -s /bin/bash - "$CUSTOM_USER" -c "git clone --depth 1 https://github.com/marlonrichert/zsh-autocomplete.git '$ZSH_CUSTOM/plugins/zsh-autocomplete'" 2>/dev/null

# Install agnosterzak theme
echo "FluxLinux Pro: Installing agnosterzak theme..."
su -s /bin/bash - "$CUSTOM_USER" -c "mkdir -p '$ZSH_CUSTOM/themes'"
su -s /bin/bash - "$CUSTOM_USER" -c "curl -fsSL https://raw.githubusercontent.com/zakaziko99/agnosterzak-ohmyzsh-theme/master/agnosterzak.zsh-theme -o '$ZSH_CUSTOM/themes/agnosterzak.zsh-theme'" 2>/dev/null

# Install pokemon-colorscripts
echo "FluxLinux Pro: Installing pokemon-colorscripts..."
POKEMON_TEMP="/tmp/pokemon-colorscripts"
rm -rf "$POKEMON_TEMP"
git clone https://gitlab.com/phoneybadger/pokemon-colorscripts.git "$POKEMON_TEMP" 2>/dev/null
cd "$POKEMON_TEMP" && ./install.sh 2>/dev/null
cd - > /dev/null
rm -rf "$POKEMON_TEMP"

# Configure .zshrc
echo "FluxLinux Pro: Configuring .zshrc..."
ZSHRC="$USER_HOME/.zshrc"

# Check if .zshrc is valid (loading oh-my-zsh)
# Write complete optimized .zshrc (performance fixes from screenshot)
# - Removed zsh-autocomplete (extremely slow on PRoot, 35s+ startup)
# - Backgrounded visuals with &! (async, don't block shell startup)
# - DISABLE_AUTO_UPDATE / DISABLE_UPDATE_PROMPT (no prompts on launch)
# - ZSH_DISABLE_COMPFIX (no compaudit, faster init)
echo "FluxLinux Pro: Writing optimized .zshrc..."
cat > "$ZSHRC" << 'ZSHEOF'
# PATH setup - local bin, npm global modules
export PATH="$HOME/.local/bin:/opt/nodejs/bin:$PATH"

# Setup Locales
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

# Fix XDG_RUNTIME_DIR (not set in PRoot/chroot — no systemd-logind)
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp}"

# Background visuals - don't block shell startup
{ fastfetch --config termux; pokemon-colorscripts --no-title -r 1,2,3 } &!

# oh-my-zsh optimizations
export ZSH="$HOME/.oh-my-zsh"
ZSH_THEME="agnosterzak"
DISABLE_UPDATE_PROMPT=true
DISABLE_AUTO_UPDATE=true
ZSH_DISABLE_COMPFIX=true

# Removed zsh-autocomplete (very slow), kept essential plugins
plugins=(git zsh-autosuggestions zsh-syntax-highlighting)

source $ZSH/oh-my-zsh.sh
ZSHEOF
chown "$CUSTOM_USER:$CUSTOM_GROUP" "$ZSHRC"

# Download fastfetch config
mkdir -p "$USER_HOME/.local/share/fastfetch/presets"
curl -fsSL https://raw.githubusercontent.com/abhay-byte/Linux_Setup/dev/config/termux.jsonc \
    -o "$USER_HOME/.local/share/fastfetch/presets/termux.jsonc" 2>/dev/null

# Fastfetch and pokemon are already included in the optimized .zshrc above (backgrounded)

# Set zsh as default shell for flux user
chsh -s /bin/zsh "$CUSTOM_USER" 2>/dev/null

# Fix ownership
chown -R "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/.oh-my-zsh" "$USER_HOME/.zshrc" "$USER_HOME/.local" 2>/dev/null

echo "FluxLinux Pro: Terminal configuration complete!"


# 8. Reload XFCE Daemons (Force restart like chroot script does)
echo "FluxLinux Pro: Reloading Desktop..."

# Kill existing XFCE processes to force reload (matches chroot pattern)
su -s /bin/bash - "$CUSTOM_USER" -c "killall -9 xfdesktop xfwm4 xfsettingsd" 2>/dev/null
sleep 2

# Restart daemons with updated settings (run in background but wait a bit for each)
su -s /bin/bash - "$CUSTOM_USER" -c "DISPLAY=:0 nohup xfdesktop > /dev/null 2>&1 &" 2>/dev/null
sleep 0.5
su -s /bin/bash - "$CUSTOM_USER" -c "DISPLAY=:0 nohup xfwm4 --replace > /dev/null 2>&1 &" 2>/dev/null
sleep 0.5
su -s /bin/bash - "$CUSTOM_USER" -c "DISPLAY=:0 nohup xfsettingsd > /dev/null 2>&1 &" 2>/dev/null
sleep 1

echo "FluxLinux Pro: Customization Complete!"
echo "------------------------------------------------"
read -p "Press Enter to close..."
