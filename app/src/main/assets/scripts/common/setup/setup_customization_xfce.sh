#!/bin/sh
# setup_customization_xfce.sh
# FluxLinux XFCE branding for Fedora / Void / openSUSE (proot + chroot).
# Same theme/icon/font/xfconf path as Alpine; package manager auto-detected.

CUSTOM_USER="flux"
CUSTOM_GROUP="flux"
USER_HOME="/home/$CUSTOM_USER"
THEME_DIR="/usr/share/themes"
ICON_DIR="/usr/share/icons"

handle_error() {
    echo ""
    echo "FluxLinux Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    if [ -t 0 ]; then
        printf "Press Enter to acknowledge error and exit..."
        # shellcheck disable=SC2034
        read -r _ack || true
    fi
    exit 1
}

echo "FluxLinux: Starting XFCE4 Customization..."

if [ "$(id -u)" -ne 0 ]; then
    echo "FluxLinux: ERROR: must run as root inside the guest (got uid=$(id -u))."
    handle_error "Not Root"
fi

export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin${PATH:+:$PATH}"
unset LD_LIBRARY_PATH
unset LD_PRELOAD

_flux_chown() { chown "$@" 2>/dev/null || true; }
_flux_chown_r() { chown -R "$@" 2>/dev/null || true; }

if id -gn "$CUSTOM_USER" >/dev/null 2>&1; then
    CUSTOM_GROUP="$(id -gn "$CUSTOM_USER")"
fi

if [ ! -s /etc/resolv.conf ]; then
    printf 'nameserver 8.8.8.8\nnameserver 1.1.1.1\n' > /etc/resolv.conf
fi

_flux_pkg_add() {
    if command -v dnf5 >/dev/null 2>&1; then
        dnf5 -y --setopt=install_weak_deps=False --setopt=tsflags=nodocs,noscripts install "$@"
    elif command -v dnf >/dev/null 2>&1; then
        dnf -y --setopt=install_weak_deps=False --setopt=tsflags=nodocs,noscripts install "$@"
    elif command -v xbps-install >/dev/null 2>&1; then
        xbps-install -y "$@"
    elif command -v zypper >/dev/null 2>&1; then
        # Restore libcurl-mini if a previous curl install broke zypper.
        if [ -f /usr/lib64/libcurl.so.4.8.0.flux-mini ]; then
            cp -f /usr/lib64/libcurl.so.4.8.0.flux-mini /usr/lib64/libcurl.so.4.8.0 2>/dev/null || true
        fi
        zypper --non-interactive al libcurl-mini4 libcurl4 curl libldap2 2>/dev/null || true
        zypper --non-interactive install --no-recommends --auto-agree-with-licenses "$@"
    elif command -v apk >/dev/null 2>&1; then
        apk add --no-cache "$@"
    elif command -v apt-get >/dev/null 2>&1; then
        DEBIAN_FRONTEND=noninteractive apt-get install -y "$@"
    else
        return 1
    fi
}

_flux_fetch() {
    _url="$1"
    _out="$2"
    if command -v wget >/dev/null 2>&1; then
        wget -q -O "$_out" "$_url"
    elif command -v curl >/dev/null 2>&1; then
        curl -fL --connect-timeout 10 --max-time 60 -o "$_out" "$_url"
    else
        return 1
    fi
}

echo "FluxLinux: Installing customization packages..."
# Never install `curl` on openSUSE — it replaces libcurl-mini and breaks zypper.
if command -v zypper >/dev/null 2>&1; then
    _flux_pkg_add wget unzip fontconfig git zsh bash || handle_error "customization deps"
else
    _flux_pkg_add curl wget unzip fontconfig git zsh bash || handle_error "customization deps"
fi
_flux_pkg_add xfce4-screenshooter 2>/dev/null || true
_flux_pkg_add fastfetch 2>/dev/null || _flux_pkg_add neofetch 2>/dev/null || true

export LANG="${LANG:-C.UTF-8}"
export LC_ALL="${LC_ALL:-C.UTF-8}"

ASSET_REPO="abhay-byte/fluxlinux"
ASSET_TAG="debian-v1"
BASE_URL="https://github.com/$ASSET_REPO/releases/download/$ASSET_TAG"
FLUX_ASSET_DIR="${FLUX_ASSET_DIR:-/tmp/flux_xfce_assets}"

theme_is_installed() {
    _n="$1"
    _d="$THEME_DIR/$_n"
    [ -d "$_d" ] && { [ -f "$_d/index.theme" ] || [ -d "$_d/gtk-3.0" ] || [ -d "$_d/xfwm4" ]; }
}

icon_is_installed() {
    _n="$1"
    _d="$ICON_DIR/$_n"
    [ -d "$_d" ] && { [ -f "$_d/index.theme" ] || [ -n "$(ls -A "$_d" 2>/dev/null)" ]; }
}

cursor_is_installed() {
    _n="$1"
    _d="$ICON_DIR/$_n"
    [ -d "$_d" ] && { [ -f "$_d/index.theme" ] || [ -d "$_d/cursors" ]; }
}

extract_local_tar() {
    _archive="$1"
    _target="$2"
    shift 2
    [ -f "$_archive" ] || return 1
    mkdir -p "$_target"
    chmod 755 "$(dirname "$_archive")" 2>/dev/null || true
    chmod 644 "$_archive" 2>/dev/null || true
    _copy="/var/tmp/$(basename "$_archive")"
    cp -f "$_archive" "$_copy" 2>/dev/null || _copy="$_archive"
    chmod 644 "$_copy" 2>/dev/null || true
    echo " - Extracting $(basename "$_copy") → $_target"
    case "$_copy" in
        *.tar.xz|*.txz) tar -xJf "$_copy" -C "$_target" "$@" ;;
        *.tar.gz|*.tgz) tar -xzf "$_copy" -C "$_target" "$@" ;;
        *.tar)          tar -xf  "$_copy" -C "$_target" "$@" ;;
        *) return 1 ;;
    esac
}

find_asset() {
    _name="$1"
    for f in \
        "$FLUX_ASSET_DIR/$_name" \
        "/tmp/flux_xfce_assets/$_name"
    do
        if [ -f "$f" ]; then
            echo "$f"
            return 0
        fi
    done
    return 1
}

download_if_needed() {
    _url="$1"
    _out="$2"
    if [ -f "$_out" ] && [ -s "$_out" ]; then
        return 0
    fi
    mkdir -p "$(dirname "$_out")"
    echo " - Downloading $(basename "$_out")..."
    _flux_fetch "$_url" "$_out" || return 1
    [ -s "$_out" ]
}

if [ -n "${FLUX_THEME:-}" ]; then
    echo "FluxLinux: Auto-applying Theme: $FLUX_THEME"
    if [ "$FLUX_THEME" = "light" ]; then
        THEME_CHOICE="2"
    else
        THEME_CHOICE="1"
    fi
else
    THEME_CHOICE="1"
fi

SEL_ICON="Papirus-Dark"
ICON_TAR="papirus-dark-only.tar.gz"
# Fedora 43 / Tumbleweed GTK+glycin cannot load incomplete Papirus SVG
# trees (missing 48x48@2x). Adwaita is shipped with both families.
if command -v dnf >/dev/null 2>&1 || command -v dnf5 >/dev/null 2>&1 \
    || command -v zypper >/dev/null 2>&1; then
    SEL_ICON="Adwaita"
fi

if [ "$THEME_CHOICE" = "2" ]; then
    SEL_THEME="Space-light"
    SEL_CURSOR="Vimix-cursors"
    SEL_WALLPAPER="fluxlinux-light.png"
    THEME_TAR="Space-light.tar.xz"
    CURSOR_TAR="01-Vimix-cursors.tar.xz"
else
    SEL_THEME="Space-transparency"
    SEL_CURSOR="Vimix-white-cursors"
    SEL_WALLPAPER="fluxlinux-dark.png"
    THEME_TAR="Space-transparency.tar.xz"
    CURSOR_TAR="02-Vimix-white-cursors.tar.xz"
fi

mkdir -p "$THEME_DIR" "$ICON_DIR"

if [ "${FLUX_SKIP_THEME_ICONS:-0}" = "1" ]; then
    echo "FluxLinux: Themes/icons pre-installed on host — skip guest extract"
elif theme_is_installed "$SEL_THEME" && icon_is_installed "$SEL_ICON" && cursor_is_installed "$SEL_CURSOR"; then
    echo "FluxLinux: Theme+icons+cursor already installed — skip extract"
else
    if ! theme_is_installed "$SEL_THEME"; then
        echo "FluxLinux: Installing theme $SEL_THEME..."
        TFILE="$(find_asset "$THEME_TAR" || true)"
        if [ -z "$TFILE" ]; then
            download_if_needed "$BASE_URL/theme.zip" "/tmp/theme.zip" || handle_error "Theme Download"
            unzip -q -o /tmp/theme.zip -d /tmp/flux_theme_zip
            TFILE="$(find /tmp/flux_theme_zip -name "$THEME_TAR" 2>/dev/null | head -1)"
        fi
        extract_local_tar "$TFILE" "$THEME_DIR" || handle_error "Theme Extract"
    fi
    if ! icon_is_installed "$SEL_ICON"; then
        echo "FluxLinux: Installing icons $SEL_ICON..."
        IFILE="$(find_asset "$ICON_TAR" || find_asset "papirus-dark-only.tar.gz" || true)"
        [ -n "$IFILE" ] || handle_error "Icons Archive Missing"
        extract_local_tar "$IFILE" "$ICON_DIR" "Papirus-Dark" 2>/dev/null || \
            extract_local_tar "$IFILE" "$ICON_DIR" || handle_error "Icons Extract"
    fi
    if ! cursor_is_installed "$SEL_CURSOR"; then
        echo "FluxLinux: Installing cursor $SEL_CURSOR..."
        CFILE="$(find_asset "$CURSOR_TAR" || true)"
        if [ -z "$CFILE" ]; then
            download_if_needed "$BASE_URL/cursor.zip" "/tmp/cursor.zip" || handle_error "Cursor Download"
            unzip -q -o /tmp/cursor.zip -d /tmp/flux_cursor_zip
            CFILE="$(find /tmp/flux_cursor_zip -name "$CURSOR_TAR" 2>/dev/null | head -1)"
        fi
        extract_local_tar "$CFILE" "$ICON_DIR" || handle_error "Cursor Extract"
    fi
fi

# Staged Papirus-Dark is a size-reduced tree. GTK3 aborts xfce4-panel when
# 48x48@2x/status/image-missing.svg is absent. Seed from Adwaita; if still
# incomplete, fall back so the session paints.
_src_missing=""
for _c in /usr/share/icons/Adwaita/scalable/status/image-missing.svg \
          /usr/share/icons/Adwaita/symbolic/status/image-missing-symbolic.svg \
          /usr/share/icons/hicolor/scalable/status/image-missing.svg; do
    [ -f "$_c" ] && _src_missing="$_c" && break
done
if [ -n "$_src_missing" ]; then
    for _theme in Papirus-Dark Papirus; do
        [ -d "$ICON_DIR/$_theme" ] || continue
        for _sz in 16x16 22x22 24x24 32x32 48x48 64x64 \
                   16x16@2x 22x22@2x 24x24@2x 32x32@2x 48x48@2x; do
            _dest="$ICON_DIR/$_theme/$_sz/status"
            mkdir -p "$_dest"
            [ -f "$_dest/image-missing.svg" ] || cp -f "$_src_missing" "$_dest/image-missing.svg"
        done
    done
fi
if [ "$SEL_ICON" = "Papirus-Dark" ] && \
   [ ! -f "$ICON_DIR/Papirus-Dark/48x48@2x/status/image-missing.svg" ]; then
    echo "FluxLinux: Papirus-Dark incomplete — using Adwaita icons"
    SEL_ICON="Adwaita"
fi

WALLPAPER_DIR="$USER_HOME/Pictures/Wallpapers"
mkdir -p "$WALLPAPER_DIR"
_flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/Pictures" 2>/dev/null || true

if [ ! -f "$WALLPAPER_DIR/$SEL_WALLPAPER" ]; then
    WFILE="$(find_asset "$SEL_WALLPAPER" || true)"
    if [ -n "$WFILE" ]; then
        cp -f "$WFILE" "$WALLPAPER_DIR/$SEL_WALLPAPER"
    else
        TEMP_WP_ZIP="/tmp/wallpaper.zip"
        if download_if_needed "$BASE_URL/wallpaper.zip" "$TEMP_WP_ZIP"; then
            unzip -o -j "$TEMP_WP_ZIP" -d "$WALLPAPER_DIR" 2>/dev/null || true
            [ -f "$WALLPAPER_DIR/dark.png" ] && mv -f "$WALLPAPER_DIR/dark.png" "$WALLPAPER_DIR/fluxlinux-dark.png"
            [ -f "$WALLPAPER_DIR/light.png" ] && mv -f "$WALLPAPER_DIR/light.png" "$WALLPAPER_DIR/fluxlinux-light.png"
        fi
    fi
fi
_flux_chown "$CUSTOM_USER:$CUSTOM_GROUP" "$WALLPAPER_DIR"/* 2>/dev/null || true

FONT_DIR="/usr/share/fonts/jetbrains-mono-nerd"
if ! fc-list 2>/dev/null | grep -qi "JetBrainsMono Nerd"; then
    echo "FluxLinux: Installing JetBrains Mono Nerd Font..."
    mkdir -p "$FONT_DIR"
    TEMP_ZIP="/tmp/JetBrainsMono.zip"
    NERD_FONT_URL="https://github.com/ryanoasis/nerd-fonts/releases/latest/download/JetBrainsMono.zip"
    if _flux_fetch "$NERD_FONT_URL" "$TEMP_ZIP"; then
        unzip -o -j "$TEMP_ZIP" "*.ttf" -d "$FONT_DIR" 2>/dev/null || unzip -o "$TEMP_ZIP" -d "$FONT_DIR" 2>/dev/null || true
        fc-cache -f "$FONT_DIR" 2>/dev/null || true
    fi
    rm -f "$TEMP_ZIP"
fi

echo "FluxLinux: Applying XFCE4 Settings..."
XFCONF_DIR="$USER_HOME/.config/xfce4/xfconf/xfce-perchannel-xml"
mkdir -p "$XFCONF_DIR"
WALLPAPER_PATH="$WALLPAPER_DIR/$SEL_WALLPAPER"

cat > "$XFCONF_DIR/xsettings.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xsettings" version="1.0">
  <property name="Net" type="empty">
    <property name="ThemeName" type="string" value="$SEL_THEME"/>
    <property name="IconThemeName" type="string" value="$SEL_ICON"/>
  </property>
  <property name="Gtk" type="empty">
    <property name="CursorThemeName" type="string" value="$SEL_CURSOR"/>
    <property name="CursorThemeSize" type="int" value="52"/>
    <property name="FontName" type="string" value="JetBrainsMono Nerd Font 10"/>
    <property name="MonospaceFontName" type="string" value="JetBrainsMono Nerd Font Mono 10"/>
  </property>
  <property name="Gdk" type="empty">
    <property name="WindowScalingFactor" type="int" value="2"/>
  </property>
</channel>
EOF

cat > "$XFCONF_DIR/xfwm4.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfwm4" version="1.0">
  <property name="general" type="empty">
    <property name="theme" type="string" value="$SEL_THEME"/>
    <property name="use_compositing" type="bool" value="false"/>
  </property>
</channel>
EOF

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

_flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/.config" 2>/dev/null || true
_flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME" 2>/dev/null || true

_flux_run() {
    _t="$1"; shift
    if command -v timeout >/dev/null 2>&1; then
        timeout "$_t" "$@" 2>/dev/null
    else
        "$@" 2>/dev/null
    fi
}

_flux_safe_rm_tree() {
    _p="$1"
    [ -e "$_p" ] || return 0
    _bak="${_p}.flux_rm.$$"
    mv "$_p" "$_bak" 2>/dev/null || { rm -rf "$_p" 2>/dev/null || true; return 0; }
    rm -rf "$_bak" 2>/dev/null || true
}

_flux_git_clone() {
    _url="$1"; _dest="$2"; _secs="${3:-90}"
    _flux_safe_rm_tree "$_dest"
    mkdir -p "$(dirname "$_dest")"
    _flux_run "$_secs" git clone --depth 1 --single-branch --quiet "$_url" "$_dest"
}

echo "FluxLinux: Installing Oh My Zsh..."
OMZ_OK=0
if [ -f "$USER_HOME/.oh-my-zsh/oh-my-zsh.sh" ]; then
    echo "FluxLinux: Oh My Zsh already valid — skip install"
    OMZ_OK=1
else
    if [ -e "$USER_HOME/.oh-my-zsh" ]; then
        _flux_safe_rm_tree "$USER_HOME/.oh-my-zsh"
    fi
    if [ "${FLUX_SKIP_OMZ:-0}" = "1" ]; then
        echo "FluxLinux: FLUX_SKIP_OMZ set but oh-my-zsh.sh missing — guest fallback"
    fi
    if command -v git >/dev/null 2>&1; then
        if _flux_git_clone "https://github.com/ohmyzsh/ohmyzsh.git" "$USER_HOME/.oh-my-zsh" 120 \
            && [ -f "$USER_HOME/.oh-my-zsh/oh-my-zsh.sh" ]; then
            OMZ_OK=1
        else
            echo "FluxLinux: WARNING: Oh My Zsh install failed/timed out — continuing without it"
            _flux_safe_rm_tree "$USER_HOME/.oh-my-zsh"
        fi
    fi
fi

if [ "$OMZ_OK" = "1" ]; then
    ZSH_CUSTOM="$USER_HOME/.oh-my-zsh/custom"
    mkdir -p "$ZSH_CUSTOM/plugins" "$ZSH_CUSTOM/themes" 2>/dev/null || true
    if [ ! -f "$ZSH_CUSTOM/plugins/zsh-autosuggestions/zsh-autosuggestions.zsh" ]; then
        _flux_git_clone "https://github.com/zsh-users/zsh-autosuggestions.git" \
            "$ZSH_CUSTOM/plugins/zsh-autosuggestions" 60 || true
    fi
    if [ ! -f "$ZSH_CUSTOM/plugins/zsh-syntax-highlighting/zsh-syntax-highlighting.zsh" ]; then
        _flux_git_clone "https://github.com/zsh-users/zsh-syntax-highlighting.git" \
            "$ZSH_CUSTOM/plugins/zsh-syntax-highlighting" 60 || true
    fi
    if [ ! -s "$ZSH_CUSTOM/themes/agnosterzak.zsh-theme" ]; then
        if command -v wget >/dev/null 2>&1; then
            _flux_run 30 wget -q -O "$ZSH_CUSTOM/themes/agnosterzak.zsh-theme" \
                "https://raw.githubusercontent.com/zakaziko99/agnosterzak-ohmyzsh-theme/master/agnosterzak.zsh-theme" || true
        elif command -v curl >/dev/null 2>&1; then
            _flux_run 30 curl -fsSL --connect-timeout 10 --max-time 25 \
                "https://raw.githubusercontent.com/zakaziko99/agnosterzak-ohmyzsh-theme/master/agnosterzak.zsh-theme" \
                -o "$ZSH_CUSTOM/themes/agnosterzak.zsh-theme" || true
        fi
    fi
    _flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/.oh-my-zsh"
fi

if [ "${FLUX_SKIP_POKEMON:-1}" = "1" ]; then
    echo "FluxLinux: pokemon-colorscripts skip (disabled by default)"
fi

echo "FluxLinux: Configuring .zshrc..."
ZSHRC="$USER_HOME/.zshrc"
cat > "$ZSHRC" << 'ZSHEOF'
# Guest PATH only — never inherit host PREFIX/bin (nested proot glue errors).
export PATH="$HOME/.local/bin:/opt/nodejs/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

unset PROOT_TMP_DIR
export TMPDIR="${TMPDIR:-/tmp}"
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp}"

{
  if command -v fastfetch >/dev/null 2>&1; then
    fastfetch --config termux 2>/dev/null || fastfetch 2>/dev/null || true
  fi
  if command -v pokemon-colorscripts >/dev/null 2>&1; then
    pokemon-colorscripts --no-title -r 1,2,3 2>/dev/null || true
  fi
} &!

export ZSH="${ZSH:-$HOME/.oh-my-zsh}"
if [ -f "$ZSH/oh-my-zsh.sh" ]; then
  ZSH_THEME="agnosterzak"
  DISABLE_UPDATE_PROMPT=true
  DISABLE_AUTO_UPDATE=true
  ZSH_DISABLE_COMPFIX=true
  plugins=(git zsh-autosuggestions zsh-syntax-highlighting)
  source "$ZSH/oh-my-zsh.sh"
fi

# Package managers need root. NOPASSWD sudo is configured for flux.
if command -v sudo >/dev/null 2>&1; then
  if command -v dnf >/dev/null 2>&1; then
    dnf() { command sudo dnf "$@"; }
  fi
  if command -v dnf5 >/dev/null 2>&1; then
    dnf5() { command sudo dnf5 "$@"; }
  fi
  if command -v xbps-install >/dev/null 2>&1; then
    xbps-install() { command sudo xbps-install "$@"; }
  fi
  if command -v zypper >/dev/null 2>&1; then
    zypper() { command sudo zypper "$@"; }
  fi
  if command -v apk >/dev/null 2>&1; then
    apk() { command sudo apk "$@"; }
  fi
fi
ZSHEOF
_flux_chown "$CUSTOM_USER:$CUSTOM_GROUP" "$ZSHRC"
ZPROFILE="$USER_HOME/.zprofile"
if [ ! -f "$ZPROFILE" ]; then
    printf '%s\n' '[[ -o interactive ]] || { [ -f "$HOME/.zshrc" ] && . "$HOME/.zshrc"; }' \
        > "$ZPROFILE"
    _flux_chown "$CUSTOM_USER:$CUSTOM_GROUP" "$ZPROFILE"
fi

mkdir -p "$USER_HOME/.local/share/fastfetch/presets"
if command -v wget >/dev/null 2>&1; then
    _flux_run 20 wget -q -O "$USER_HOME/.local/share/fastfetch/presets/termux.jsonc" \
        https://raw.githubusercontent.com/abhay-byte/Linux_Setup/dev/config/termux.jsonc || true
elif command -v curl >/dev/null 2>&1; then
    _flux_run 20 curl -fsSL --connect-timeout 8 --max-time 15 \
        https://raw.githubusercontent.com/abhay-byte/Linux_Setup/dev/config/termux.jsonc \
        -o "$USER_HOME/.local/share/fastfetch/presets/termux.jsonc" || true
fi

if command -v zsh >/dev/null 2>&1; then
    ZSH_PATH="$(command -v zsh)"
    if [ -f /etc/shells ] && ! grep -qxF "$ZSH_PATH" /etc/shells 2>/dev/null; then
        echo "$ZSH_PATH" >> /etc/shells
    fi
    chsh -s "$ZSH_PATH" "$CUSTOM_USER" 2>/dev/null || true
fi

_flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME" 2>/dev/null || true

# Proot-safe PM db ownership
_ref_u=$(stat -c %u /etc 2>/dev/null || true)
_ref_g=$(stat -c %g /etc 2>/dev/null || true)
if [ -n "$_ref_u" ]; then
    for p in /var/lib/dnf /var/cache/dnf /var/lib/rpm /var/db/xbps /var/cache/xbps \
             /var/cache/zypp /var/lib/zypp; do
        [ -e "$p" ] || continue
        chown -R "$_ref_u:$_ref_g" "$p" 2>/dev/null || true
    done
fi

echo "FluxLinux: XFCE4 Customization complete!"
exit 0
