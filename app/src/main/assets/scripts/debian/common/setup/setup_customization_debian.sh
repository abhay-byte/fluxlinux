#!/bin/bash
# setup_customization_debian.sh
# Applies "FluxLinux" branding and customization to Debian XFCE4 Desktop
# Works for both Chroot and Proot environments (run as root, switches to user 'flux')

CUSTOM_USER="flux"
CUSTOM_GROUP="users"
USER_HOME="/home/$CUSTOM_USER"
ASSETS_DIR="$(dirname "$0")/../../../assets"
THEME_DIR="/usr/share/themes"
ICON_DIR="/usr/share/icons"

# Error Handler — never block on stdin (onboarding / ProcessBuilder has no TTY)
handle_error() {
    echo ""
    echo "❌ FluxLinux Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    echo "Please check the error message above for details."
    echo "---------------------------------------------------"
    if [ -t 0 ]; then
        read -r -p "Press Enter to acknowledge error and exit..."
    fi
    exit 1
}

echo "FluxLinux: Starting XFCE4 Customization..."

# Setup scripts must run as root inside proot/chroot (apt/dpkg).
if [ "$(id -u)" -ne 0 ]; then
    echo "FluxLinux: ERROR: must run as root inside the guest (got uid=$(id -u))."
    echo "FluxLinux: Component sessions should use --user root; re-run from Distro Settings."
    handle_error "Not Root"
fi

# Best-effort ownership (proot often cannot chown to flux:users — never spam/fail)
_flux_chown() { chown "$@" 2>/dev/null || true; }
_flux_chown_r() { chown -R "$@" 2>/dev/null || true; }

# 1. Install Dependencies
echo "FluxLinux: Installing customization tools (as root)..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -y || handle_error "Dependency Installation (apt update)"
# git: guest fallback when host did not pre-stage Oh My Zsh; zsh: terminal shell
apt-get install -y -o Dpkg::Use-Pty=0 \
    xfce4-goodies curl fastfetch wget unzip fontconfig locales git zsh \
    || handle_error "Dependency Installation"

# Setup Locales for proper font rendering in ZSH/Terminal
echo "FluxLinux: Setting up locales..."
echo "en_US.UTF-8 UTF-8" > /etc/locale.gen
locale-gen
update-locale LANG=en_US.UTF-8

# 2. Deploy Assets (From GitHub Release debian-v1)
ASSET_REPO="abhay-byte/fluxlinux"
ASSET_TAG="debian-v1"
BASE_URL="https://github.com/$ASSET_REPO/releases/download/$ASSET_TAG"

# Local asset dirs (host stages into shared /tmp for proot; optional offline paths)
FLUX_ASSET_DIR="${FLUX_ASSET_DIR:-/tmp/flux_xfce_assets}"

theme_is_installed() {
    local name="$1"
    local d="$THEME_DIR/$name"
    [ -d "$d" ] && { [ -f "$d/index.theme" ] || [ -d "$d/gtk-3.0" ] || [ -d "$d/xfwm4" ]; }
}

icon_is_installed() {
    local name="$1"
    local d="$ICON_DIR/$name"
    [ -d "$d" ] && { [ -f "$d/index.theme" ] || [ -n "$(ls -A "$d" 2>/dev/null)" ]; }
}

cursor_is_installed() {
    local name="$1"
    local d="$ICON_DIR/$name"
    [ -d "$d" ] && { [ -f "$d/index.theme" ] || [ -d "$d/cursors" ]; }
}

# Fast path: extract a single archive into TARGET (optional path filter as $3+)
extract_local_tar() {
    local archive="$1"
    local target="$2"
    shift 2
    [ -f "$archive" ] || return 1
    mkdir -p "$target"
    echo " - Extracting $(basename "$archive") → $target $*"
    case "$archive" in
        *.tar.xz|*.txz) tar -xJf "$archive" -C "$target" "$@" ;;
        *.tar.gz|*.tgz) tar -xzf "$archive" -C "$target" "$@" ;;
        *.tar)          tar -xf  "$archive" -C "$target" "$@" ;;
        *) return 1 ;;
    esac
}

# Resolve archive from FLUX_ASSET_DIR / common names; download zip only as last resort.
find_asset() {
    # find_asset <basename-or-glob-pattern>
    local name="$1"
    local f
    for f in \
        "$FLUX_ASSET_DIR/$name" \
        "/tmp/flux_xfce_assets/$name" \
        "$ASSETS_DIR/xfce4/theme/$name" \
        "$ASSETS_DIR/xfce4/icons/$name" \
        "$ASSETS_DIR/xfce4/cursor/$name" \
        "$ASSETS_DIR/xfce4/wallpaper/$name"
    do
        if [ -f "$f" ]; then
            echo "$f"
            return 0
        fi
    done
    return 1
}

download_if_needed() {
    local url="$1"
    local out="$2"
    if [ -f "$out" ] && [ -s "$out" ]; then
        return 0
    fi
    mkdir -p "$(dirname "$out")"
    echo " - Downloading $(basename "$out")..."
    wget -q --show-progress "$url" -O "$out" || curl -fL --progress-bar "$url" -o "$out" || return 1
    [ -s "$out" ]
}

# 3. Theme Selection Prompt
if [ -n "$FLUX_THEME" ]; then
    echo "FluxLinux: Auto-applying Theme: $FLUX_THEME"
    if [ "$FLUX_THEME" == "light" ]; then
        THEME_CHOICE="2"
    else
        THEME_CHOICE="1"
    fi
else
    if [ -t 0 ]; then
        echo "------------------------------------------------"
        echo "Select Theme Preference:"
        echo "1) Dark (Default)"
        echo "2) Light"
        read -r -p "Enter choice [1-2]: " THEME_CHOICE
        echo "------------------------------------------------"
    else
        echo "FluxLinux: No TTY / FLUX_THEME — defaulting to dark"
        THEME_CHOICE="1"
    fi
fi

# Icons: Papirus-Dark only (no full Papirus / Light / ePapirus packs)
SEL_ICON="Papirus-Dark"
ICON_TAR="papirus-dark-only.tar.gz"

if [ "$THEME_CHOICE" == "2" ]; then
    echo "FluxLinux: Light Mode Selected (icons: Papirus-Dark only)."
    SEL_THEME="Space-light"
    SEL_CURSOR="Vimix-cursors" # Dark cursor for light theme (better contrast)
    SEL_WALLPAPER="fluxlinux-light.png"
    THEME_TAR="Space-light.tar.xz"
    CURSOR_TAR="01-Vimix-cursors.tar.xz"
else
    echo "FluxLinux: Dark Mode Selected (icons: Papirus-Dark only)."
    SEL_THEME="Space-transparency"
    SEL_CURSOR="Vimix-white-cursors" # White cursor for dark theme (better contrast)
    SEL_WALLPAPER="fluxlinux-dark.png"
    THEME_TAR="Space-transparency.tar.xz"
    CURSOR_TAR="02-Vimix-white-cursors.tar.xz"
fi

mkdir -p "$THEME_DIR" "$ICON_DIR"

# Host may have already extracted into the proot rootfs (native tar — fast).
# FLUX_SKIP_THEME_ICONS=1 → only apply configs / wallpaper ownership.
if [ "${FLUX_SKIP_THEME_ICONS:-0}" = "1" ]; then
    echo "FluxLinux: Themes/icons pre-installed on host — skip guest extract"
elif theme_is_installed "$SEL_THEME" && icon_is_installed "$SEL_ICON" && cursor_is_installed "$SEL_CURSOR"; then
    echo "FluxLinux: Theme+icons+cursor already installed ($SEL_THEME / $SEL_ICON / $SEL_CURSOR) — skip extract"
else
    # ── Theme (selected only) ───────────────────────────────────────────────
    if theme_is_installed "$SEL_THEME"; then
        echo "FluxLinux: Theme $SEL_THEME already installed — skip"
    else
        echo "FluxLinux: Installing theme $SEL_THEME only..."
        TFILE="$(find_asset "$THEME_TAR" || true)"
        if [ -z "$TFILE" ]; then
            download_if_needed "$BASE_URL/theme.zip" "/tmp/theme.zip" || handle_error "Theme Download"
            unzip -q -o /tmp/theme.zip -d /tmp/flux_theme_zip
            TFILE="$(find /tmp/flux_theme_zip -name "$THEME_TAR" | head -1)"
            [ -n "$TFILE" ] || TFILE="$(find /tmp/flux_theme_zip -name "${SEL_THEME}*.tar.xz" | head -1)"
        fi
        extract_local_tar "$TFILE" "$THEME_DIR" || handle_error "Theme Extract"
        theme_is_installed "$SEL_THEME" || handle_error "Theme Missing After Extract"
    fi

    # ── Icons: Papirus-Dark only ────────────────────────────────────────────
    if icon_is_installed "$SEL_ICON"; then
        echo "FluxLinux: Icons $SEL_ICON already installed — skip"
    else
        echo "FluxLinux: Installing icons $SEL_ICON only..."
        IFILE="$(find_asset "$ICON_TAR" || find_asset "papirus-dark-only.tar.gz" || true)"
        if [ -z "$IFILE" ]; then
            # Legacy fallback: full pack but still extract only Papirus-Dark/
            IFILE="$(find_asset "papirus-icon-theme-20250501.tar.gz" || true)"
        fi
        if [ -z "$IFILE" ]; then
            echo "FluxLinux: ERROR: Papirus-Dark archive missing (expected $ICON_TAR in assets)"
            handle_error "Icons Archive Missing"
        fi
        extract_local_tar "$IFILE" "$ICON_DIR" "Papirus-Dark" || \
            extract_local_tar "$IFILE" "$ICON_DIR" || handle_error "Icons Extract"
        icon_is_installed "$SEL_ICON" || handle_error "Icons Missing After Extract"
    fi

    # ── Cursor (selected only) ──────────────────────────────────────────────
    if cursor_is_installed "$SEL_CURSOR"; then
        echo "FluxLinux: Cursor $SEL_CURSOR already installed — skip"
    else
        echo "FluxLinux: Installing cursor $SEL_CURSOR only..."
        CFILE="$(find_asset "$CURSOR_TAR" || true)"
        if [ -z "$CFILE" ]; then
            download_if_needed "$BASE_URL/cursor.zip" "/tmp/cursor.zip" || handle_error "Cursor Download"
            unzip -q -o /tmp/cursor.zip -d /tmp/flux_cursor_zip
            CFILE="$(find /tmp/flux_cursor_zip -name "$CURSOR_TAR" -o -name "*${SEL_CURSOR}*.tar.xz" | head -1)"
        fi
        extract_local_tar "$CFILE" "$ICON_DIR" || handle_error "Cursor Extract"
        cursor_is_installed "$SEL_CURSOR" || handle_error "Cursor Missing After Extract"
    fi
fi

# Wallpaper Setup (skip download if selected file already present)
WALLPAPER_DIR="$USER_HOME/Pictures/Wallpapers"
mkdir -p "$WALLPAPER_DIR"
_flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/Pictures"

if [ -f "$WALLPAPER_DIR/$SEL_WALLPAPER" ]; then
    echo "FluxLinux: Wallpaper $SEL_WALLPAPER already present — skip"
else
    echo "FluxLinux: Installing wallpaper..."
    WFILE="$(find_asset "$SEL_WALLPAPER" || true)"
    if [ -n "$WFILE" ]; then
        cp -f "$WFILE" "$WALLPAPER_DIR/$SEL_WALLPAPER"
    else
        TEMP_WP_ZIP="/tmp/wallpaper.zip"
        download_if_needed "$BASE_URL/wallpaper.zip" "$TEMP_WP_ZIP" || handle_error "Wallpaper Download"
        unzip -o -j "$TEMP_WP_ZIP" -d "$WALLPAPER_DIR"
        [ -f "$WALLPAPER_DIR/dark.png" ] && mv -f "$WALLPAPER_DIR/dark.png" "$WALLPAPER_DIR/fluxlinux-dark.png"
        [ -f "$WALLPAPER_DIR/light.png" ] && mv -f "$WALLPAPER_DIR/light.png" "$WALLPAPER_DIR/fluxlinux-light.png"
    fi
fi
_flux_chown "$CUSTOM_USER:$CUSTOM_GROUP" "$WALLPAPER_DIR"/*


# Install JetBrains Mono Nerd Font
# Using proper Debian font location: /usr/share/fonts/truetype/
FONT_DIR="/usr/share/fonts/truetype/jetbrains-mono-nerd"
FONT_INSTALLED=false

# Check if font already installed
if fc-list | grep -qi "JetBrainsMono Nerd"; then
    echo "FluxLinux: JetBrains Mono Nerd Font already installed."
    FONT_INSTALLED=true
fi

if [ "$FONT_INSTALLED" = false ]; then
    echo "FluxLinux: Installing JetBrains Mono Nerd Font..."
    
    # Create font directory
    mkdir -p "$FONT_DIR"
    
    # Download from official Nerd Fonts GitHub releases
    NERD_FONT_URL="https://github.com/ryanoasis/nerd-fonts/releases/latest/download/JetBrainsMono.zip"
    TEMP_ZIP="/tmp/JetBrainsMono.zip"
    
    echo " - Downloading JetBrains Mono Nerd Font..."
    wget -q --show-progress "$NERD_FONT_URL" -O "$TEMP_ZIP" || {
        echo "FluxLinux: Direct download failed, trying from release..."
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
        echo "FluxLinux: ✓ JetBrains Mono Nerd Font installed successfully!"
    else
        echo "FluxLinux: ⚠ Font may not be properly registered. Checking installed files..."
        ls -la "$FONT_DIR"
    fi
fi
# 4. Apply Settings for User 'flux'
# Write directly to XML config files (dbus-launch creates ephemeral sessions that don't persist)
echo "FluxLinux: Applying XFCE4 Settings..."

XFCONF_DIR="$USER_HOME/.config/xfce4/xfconf/xfce-perchannel-xml"
mkdir -p "$XFCONF_DIR"

# Generate xsettings.xml (Theme, Icons, Cursor, Fonts, Scaling)
echo "FluxLinux: Writing xsettings.xml..."
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
echo "FluxLinux: Writing xfwm4.xml..."
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
echo "FluxLinux: Writing xfce4-desktop.xml..."
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
_flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$XFCONF_DIR"
echo "FluxLinux: XFCE4 settings applied successfully!"

# Generate xfce4-keyboard-shortcuts.xml (Custom Keyboard Shortcuts)
# Note: Angle brackets in key names must be XML-escaped as &lt; and &gt;
echo "FluxLinux: Writing keyboard shortcuts..."
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

echo "FluxLinux: Keyboard shortcuts configured!"


# 5. Configure XFCE4 Panel
echo "FluxLinux: Configuring Panel..."
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

_flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$PANEL_CONFIG_DIR"

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

_flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$PLUGIN_CONFIG_DIR"
_flux_chown "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/.config/info.sh"


# 6. Configure Terminal (Direct Config File)
echo "FluxLinux: Configuring Terminal..."
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
_flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/.config"


# 7. Configure Zsh and Terminal Enhancements
echo "FluxLinux: Configuring Zsh and Terminal..."

# Bounded network helpers (proot hangs forever on bare curl/git)
_flux_have_timeout() { command -v timeout >/dev/null 2>&1; }
_flux_run() {
    # _flux_run <seconds> <cmd...>
    local sec="$1"; shift
    if _flux_have_timeout; then
        timeout "$sec" "$@"
    else
        "$@"
    fi
}
_flux_git_clone() {
    # _flux_git_clone <url> <dest> [timeout_sec]
    local url="$1" dest="$2" sec="${3:-90}"
    if ! command -v git >/dev/null 2>&1; then
        echo "FluxLinux: git not in guest PATH — skip clone $url"
        return 1
    fi
    export GIT_TERMINAL_PROMPT=0
    export GIT_HTTP_LOW_SPEED_LIMIT=1000
    export GIT_HTTP_LOW_SPEED_TIME=30
    if [ -d "$dest/.git" ] || [ -n "$(ls -A "$dest" 2>/dev/null)" ]; then
        return 0
    fi
    rm -rf "$dest"
    mkdir -p "$(dirname "$dest")"
    _flux_run "$sec" git clone --depth 1 --single-branch --quiet "$url" "$dest"
}

# Safe remove: rename first (instant), delete in background — never block forever on proot unlink
_flux_safe_rm_tree() {
    local d="$1"
    [ -e "$d" ] || return 0
    local trash="${d}.trash.$$"
    if mv "$d" "$trash" 2>/dev/null; then
        if _flux_have_timeout; then
            timeout 45 rm -rf "$trash" 2>/dev/null || (rm -rf "$trash" >/dev/null 2>&1 &)
        else
            (rm -rf "$trash" >/dev/null 2>&1 &)
        fi
    else
        if _flux_have_timeout; then
            timeout 45 rm -rf "$d" 2>/dev/null || true
        else
            rm -rf "$d" 2>/dev/null || true
        fi
    fi
}

# zsh/git should already be present from apt above; re-check for older rootfs
if ! command -v zsh >/dev/null 2>&1; then
    echo "FluxLinux: Installing zsh..."
    DEBIAN_FRONTEND=noninteractive apt-get install -y -o Dpkg::Use-Pty=0 zsh 2>/dev/null || true
else
    echo "FluxLinux: zsh already installed — skip apt"
fi
if ! command -v git >/dev/null 2>&1; then
    echo "FluxLinux: Installing git (needed for Oh My Zsh fallback)..."
    DEBIAN_FRONTEND=noninteractive apt-get install -y -o Dpkg::Use-Pty=0 git 2>/dev/null || true
fi

# ── Oh My Zsh ────────────────────────────────────────────────────────────────
# Prefer host pre-install (FLUX_SKIP_OMZ=1 + oh-my-zsh.sh present).
# Never use curl|sh under proot — hangs. Never mkdir empty OMZ after a failed clone
# (that re-triggers plugin clones into a broken tree).
echo "FluxLinux: Installing Oh My Zsh..."

OMZ_OK=0
if [ -f "$USER_HOME/.oh-my-zsh/oh-my-zsh.sh" ]; then
    if [ "${FLUX_SKIP_OMZ:-0}" = "1" ]; then
        echo "FluxLinux: Oh My Zsh pre-installed on host — skip guest install"
    else
        echo "FluxLinux: Oh My Zsh already valid — skip install"
    fi
    OMZ_OK=1
else
    # Partial / corrupt tree — remove with bounded rename+rm (not bare rm -rf hang)
    if [ -e "$USER_HOME/.oh-my-zsh" ]; then
        echo "FluxLinux: Incomplete Oh My Zsh detected — removing (bounded)…"
        _flux_safe_rm_tree "$USER_HOME/.oh-my-zsh"
    fi
    if [ "${FLUX_SKIP_OMZ:-0}" = "1" ]; then
        echo "FluxLinux: FLUX_SKIP_OMZ set but oh-my-zsh.sh missing — guest fallback"
    fi
    if command -v git >/dev/null 2>&1; then
        echo "FluxLinux: Shallow git clone Oh My Zsh (timeout 120s)…"
        if _flux_git_clone "https://github.com/ohmyzsh/ohmyzsh.git" "$USER_HOME/.oh-my-zsh" 120 \
            && [ -f "$USER_HOME/.oh-my-zsh/oh-my-zsh.sh" ]; then
            echo "FluxLinux: Oh My Zsh installed"
            OMZ_OK=1
        else
            echo "FluxLinux: WARNING: Oh My Zsh install failed/timed out — continuing without it"
            _flux_safe_rm_tree "$USER_HOME/.oh-my-zsh"
        fi
    else
        echo "FluxLinux: WARNING: git unavailable — Oh My Zsh skipped (host stage or install git)"
        _flux_safe_rm_tree "$USER_HOME/.oh-my-zsh"
    fi
fi

# Plugins/themes only when OMZ is actually valid — do not recreate empty tree
if [ "$OMZ_OK" = "1" ]; then
    ZSH_CUSTOM="$USER_HOME/.oh-my-zsh/custom"
    mkdir -p "$ZSH_CUSTOM/plugins" "$ZSH_CUSTOM/themes" 2>/dev/null || true

    echo "FluxLinux: Installing Zsh plugins (if missing)…"
    if [ ! -d "$ZSH_CUSTOM/plugins/zsh-autosuggestions/.git" ] \
        && [ ! -f "$ZSH_CUSTOM/plugins/zsh-autosuggestions/zsh-autosuggestions.zsh" ]; then
        if command -v git >/dev/null 2>&1; then
            _flux_git_clone "https://github.com/zsh-users/zsh-autosuggestions.git" \
                "$ZSH_CUSTOM/plugins/zsh-autosuggestions" 60 || true
        else
            echo "FluxLinux: git missing — skip zsh-autosuggestions"
        fi
    else
        echo "FluxLinux: zsh-autosuggestions already present — skip"
    fi
    if [ ! -d "$ZSH_CUSTOM/plugins/zsh-syntax-highlighting/.git" ] \
        && [ ! -f "$ZSH_CUSTOM/plugins/zsh-syntax-highlighting/zsh-syntax-highlighting.zsh" ]; then
        if command -v git >/dev/null 2>&1; then
            _flux_git_clone "https://github.com/zsh-users/zsh-syntax-highlighting.git" \
                "$ZSH_CUSTOM/plugins/zsh-syntax-highlighting" 60 || true
        else
            echo "FluxLinux: git missing — skip zsh-syntax-highlighting"
        fi
    else
        echo "FluxLinux: zsh-syntax-highlighting already present — skip"
    fi
    # Do NOT install zsh-autocomplete

    if [ ! -s "$ZSH_CUSTOM/themes/agnosterzak.zsh-theme" ]; then
        echo "FluxLinux: Installing agnosterzak theme…"
        _flux_run 30 curl -fsSL --connect-timeout 10 --max-time 25 \
            "https://raw.githubusercontent.com/zakaziko99/agnosterzak-ohmyzsh-theme/master/agnosterzak.zsh-theme" \
            -o "$ZSH_CUSTOM/themes/agnosterzak.zsh-theme" 2>/dev/null || true
    else
        echo "FluxLinux: agnosterzak theme already present — skip"
    fi
    _flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/.oh-my-zsh"
else
    echo "FluxLinux: Skipping Zsh plugins (Oh My Zsh not installed)"
fi

# pokemon-colorscripts: optional; default skip under proot (gitlab stalls; needs git)
if [ "${FLUX_SKIP_POKEMON:-1}" = "1" ]; then
    echo "FluxLinux: pokemon-colorscripts skip (disabled by default)"
elif command -v pokemon-colorscripts >/dev/null 2>&1; then
    echo "FluxLinux: pokemon-colorscripts already present — skip"
elif ! command -v git >/dev/null 2>&1; then
    echo "FluxLinux: pokemon-colorscripts skip (git missing)"
else
    echo "FluxLinux: Installing pokemon-colorscripts (optional, 60s budget)…"
    POKEMON_TEMP="/tmp/pokemon-colorscripts.$$"
    _flux_safe_rm_tree "$POKEMON_TEMP"
    if _flux_git_clone "https://gitlab.com/phoneybadger/pokemon-colorscripts.git" "$POKEMON_TEMP" 60 \
        && [ -f "$POKEMON_TEMP/install.sh" ]; then
        (cd "$POKEMON_TEMP" && _flux_run 30 sh ./install.sh) 2>/dev/null \
            || echo "FluxLinux: pokemon-colorscripts install skipped"
    else
        echo "FluxLinux: pokemon-colorscripts skipped (clone timeout/fail)"
    fi
    _flux_safe_rm_tree "$POKEMON_TEMP"
fi

# Configure .zshrc
echo "FluxLinux: Configuring .zshrc..."
ZSHRC="$USER_HOME/.zshrc"

# Write complete optimized .zshrc (performance fixes)
# - Removed zsh-autocomplete (extremely slow on PRoot, 35s+ startup)
# - Backgrounded visuals with &! (async, don't block shell startup)
# - DISABLE_AUTO_UPDATE / DISABLE_UPDATE_PROMPT (no prompts on launch)
# - ZSH_DISABLE_COMPFIX (no compaudit, faster init)
# Defensive: never hard-fail on missing oh-my-zsh / pokemon
echo "FluxLinux: Writing optimized .zshrc..."
cat > "$ZSHRC" << 'ZSHEOF'
# PATH setup - local bin, npm global modules
export PATH="$HOME/.local/bin:/opt/nodejs/bin:$PATH"

# Setup Locales
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

# Fix XDG_RUNTIME_DIR (not set in PRoot/chroot — no systemd-logind)
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp}"

# Background visuals - don't block shell startup; skip missing tools (no error spam)
{
  if command -v fastfetch >/dev/null 2>&1; then
    fastfetch --config termux 2>/dev/null || fastfetch 2>/dev/null || true
  fi
  if command -v pokemon-colorscripts >/dev/null 2>&1; then
    pokemon-colorscripts --no-title -r 1,2,3 2>/dev/null || true
  fi
} &!

# oh-my-zsh (optional — install may fail offline; shell still usable without it)
export ZSH="${ZSH:-$HOME/.oh-my-zsh}"
if [ -f "$ZSH/oh-my-zsh.sh" ]; then
  ZSH_THEME="agnosterzak"
  DISABLE_UPDATE_PROMPT=true
  DISABLE_AUTO_UPDATE=true
  ZSH_DISABLE_COMPFIX=true
  # Removed zsh-autocomplete (very slow), kept essential plugins
  plugins=(git zsh-autosuggestions zsh-syntax-highlighting)
  source "$ZSH/oh-my-zsh.sh"
fi
ZSHEOF
_flux_chown "$CUSTOM_USER:$CUSTOM_GROUP" "$ZSHRC"

# Download fastfetch config (bounded)
mkdir -p "$USER_HOME/.local/share/fastfetch/presets"
_flux_run 20 curl -fsSL --connect-timeout 8 --max-time 15 \
    https://raw.githubusercontent.com/abhay-byte/Linux_Setup/dev/config/termux.jsonc \
    -o "$USER_HOME/.local/share/fastfetch/presets/termux.jsonc" 2>/dev/null || true

# Set zsh as default shell for flux user
chsh -s /bin/zsh "$CUSTOM_USER" 2>/dev/null || true

# Ownership (best-effort; proot cannot always chown)
if [ -f "$USER_HOME/.oh-my-zsh/oh-my-zsh.sh" ]; then
    _flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/.oh-my-zsh"
fi
_flux_chown "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/.zshrc"
_flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/.local"

echo "FluxLinux: Terminal configuration complete!"

# 8. Reload XFCE Daemons — only if a session is actually running (don't hang install)
echo "FluxLinux: Reloading Desktop (if running)..."
if pgrep -x xfdesktop >/dev/null 2>&1 || pgrep -x xfwm4 >/dev/null 2>&1; then
    su -s /bin/bash - "$CUSTOM_USER" -c "killall -9 xfdesktop xfwm4 xfsettingsd" 2>/dev/null || true
    sleep 1
    su -s /bin/bash - "$CUSTOM_USER" -c "DISPLAY=:0 nohup xfdesktop >/dev/null 2>&1 &" 2>/dev/null || true
    su -s /bin/bash - "$CUSTOM_USER" -c "DISPLAY=:0 nohup xfwm4 --replace >/dev/null 2>&1 &" 2>/dev/null || true
else
    echo "FluxLinux: No XFCE session running — skip daemon reload"
fi
su -s /bin/bash - "$CUSTOM_USER" -c "DISPLAY=:0 nohup xfsettingsd > /dev/null 2>&1 &" 2>/dev/null
sleep 1

echo "FluxLinux: Customization Complete!"
echo "------------------------------------------------"
# Interactive pause only in a real terminal — hangs forever under onboarding
# (ProcessBuilder / proot-distro with no stdin TTY).
if [ -t 0 ]; then
    read -r -p "Press Enter to close..."
fi
exit 0
