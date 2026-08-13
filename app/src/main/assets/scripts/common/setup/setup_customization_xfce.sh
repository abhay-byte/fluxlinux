#!/bin/sh
# setup_customization_xfce.sh
# FluxLinux XFCE branding for Fedora / Void / openSUSE / Deepin / Chimera / Manjaro.
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
mkdir -p /tmp /var/tmp
chmod 1777 /tmp /var/tmp 2>/dev/null || true
unset PROOT_TMP_DIR
export TMPDIR=/tmp

_flux_chown() { chown "$@" 2>/dev/null || true; }
_flux_chown_r() { chown -R "$@" 2>/dev/null || true; }

# Fedora 44 + proot: sudo PAM account fails → "a password is required".
if command -v sudo >/dev/null 2>&1; then
    mkdir -p /etc/sudoers.d
    echo "flux ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/flux
    chmod 0440 /etc/sudoers.d/flux 2>/dev/null || true
    chmod 0755 /etc/sudoers.d 2>/dev/null || true
    if [ -f /etc/sudoers ]; then
        grep -qE '@includedir[[:space:]]+/etc/sudoers\.d' /etc/sudoers 2>/dev/null \
            || echo '@includedir /etc/sudoers.d' >> /etc/sudoers
        grep -q '^Defaults !authenticate' /etc/sudoers 2>/dev/null \
            || echo 'Defaults !authenticate' >> /etc/sudoers
        grep -q '^Defaults !pam_session' /etc/sudoers 2>/dev/null \
            || echo 'Defaults !pam_session' >> /etc/sudoers
        chmod 0440 /etc/sudoers 2>/dev/null || true
    fi
    if [ -d /etc/pam.d ]; then
        for _pam in /etc/pam.d/sudo /etc/pam.d/sudo-i; do
            cat > "$_pam" <<'PAM'
#%PAM-1.0
# FluxLinux proot: pam_unix/audit cannot run → sudo asks for a password.
auth       sufficient pam_permit.so
account    sufficient pam_permit.so
password   sufficient pam_permit.so
session    sufficient pam_permit.so
PAM
            chmod 0644 "$_pam" 2>/dev/null || true
        done
    fi
    chmod 4755 /usr/bin/sudo 2>/dev/null || chmod 4755 /usr/sbin/sudo 2>/dev/null || true
fi

if id -gn "$CUSTOM_USER" >/dev/null 2>&1; then
    CUSTOM_GROUP="$(id -gn "$CUSTOM_USER")"
fi

if [ ! -s /etc/resolv.conf ]; then
    printf 'nameserver 8.8.8.8\nnameserver 1.1.1.1\n' > /etc/resolv.conf
fi

_flux_pkg_add() {
    if grep -q '^ID="chimera"' /usr/lib/os-release 2>/dev/null \
        || grep -q '^ID=chimera' /etc/os-release 2>/dev/null \
        || { [ -d /usr/lib/apk ] && [ ! -d /lib/apk ]; }; then
        # Chimera apk v3: non-interactive config, no --no-cache (Alpine v2 flag).
        # Map generic names to Chimera's (no sudo pkg; tar=gtar; wget=wget2).
        _mapped=""
        for _p in "$@"; do
            case "$_p" in
                wget) _mapped="$_mapped wget2" ;;
                tar)  _mapped="$_mapped gtar" ;;
                font-dejavu) _mapped="$_mapped fonts-dejavu" ;;
                sudo) _mapped="$_mapped opendoas" ;;
                *)    _mapped="$_mapped $_p" ;;
            esac
        done
        # shellcheck disable=SC2086
        apk add $_mapped || apk add "$@"
    elif command -v dnf5 >/dev/null 2>&1; then
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
    elif command -v pacman >/dev/null 2>&1; then
        pacman -S --noconfirm --needed "$@"
    elif command -v apk >/dev/null 2>&1; then
        apk add --no-cache "$@"                          # Alpine apk v2
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
# xz for tar -xJf of theme/icon/cursor archives (self-sufficient re-runs on
# containers whose family pre-dates xz in the base set). Best-effort.
_flux_pkg_add xz-utils 2>/dev/null || _flux_pkg_add xz 2>/dev/null || true
_flux_pkg_add xfce4-screenshooter 2>/dev/null || true
_flux_pkg_add fastfetch 2>/dev/null || _flux_pkg_add neofetch 2>/dev/null || true

# Fail closed: guest OMZ + pokemon need git and zsh. Host PREFIX/bin/git is
# not shipped, so this is the only install path. Retry via the native PM
# (Chimera apk v3 / Deepin apt / Manjaro pacman) before aborting.
if ! command -v git >/dev/null 2>&1 || ! command -v zsh >/dev/null 2>&1; then
    echo "FluxLinux: git/zsh missing after first package pass — retrying"
    if grep -q '^ID="chimera"' /usr/lib/os-release 2>/dev/null \
        || grep -q '^ID=chimera' /etc/os-release 2>/dev/null \
        || { [ -d /usr/lib/apk ] && [ ! -d /lib/apk ]; }; then
        apk add git zsh || true
    elif command -v apt-get >/dev/null 2>&1; then
        DEBIAN_FRONTEND=noninteractive apt-get install -y git zsh || true
    elif command -v pacman >/dev/null 2>&1; then
        pacman -S --noconfirm --needed git zsh || true
    else
        _flux_pkg_add git zsh || true
    fi
fi
command -v git >/dev/null 2>&1 || handle_error "git missing (required for Oh My Zsh / pokemon)"
command -v zsh >/dev/null 2>&1 || handle_error "zsh missing (required for Flux shell)"

# pokemon-colorscripts install.sh needs a python interpreter.
if ! command -v python3 >/dev/null 2>&1 && ! command -v python >/dev/null 2>&1; then
    echo "FluxLinux: Installing python for pokemon-colorscripts..."
    _flux_pkg_add python3 2>/dev/null || _flux_pkg_add python 2>/dev/null || true
fi

# Existing guests: customization-only re-run must materialize a UTF-8 locale
# so launchers stop printing "cannot change locale" and zsh can paint @flux.
_flux_pkg_add glibc-langpack-en 2>/dev/null || true
_flux_pkg_add glibc-locale 2>/dev/null || true
_flux_pkg_add glibc-locale-base 2>/dev/null || true
_flux_pkg_add glibc-locales 2>/dev/null || true
_flux_pkg_add locales 2>/dev/null || true
if command -v pacman >/dev/null 2>&1; then
    _flux_pkg_add glibc 2>/dev/null || true
fi
if command -v _flux_ensure_en_us_locale >/dev/null 2>&1; then
    _flux_ensure_en_us_locale
else
    if [ -f /etc/locale.gen ]; then
        grep -q '^en_US.UTF-8 UTF-8' /etc/locale.gen 2>/dev/null \
            || printf 'en_US.UTF-8 UTF-8\n' >> /etc/locale.gen
    fi
    if command -v locale-gen >/dev/null 2>&1; then
        locale-gen en_US.UTF-8 2>/dev/null || locale-gen 2>/dev/null || true
    fi
    if ! locale -a 2>/dev/null | grep -qxFi en_US.utf8 \
        && ! locale -a 2>/dev/null | grep -qxFi en_US.UTF-8 \
        && command -v localedef >/dev/null 2>&1; then
        mkdir -p /usr/lib/locale
        _map=/tmp/flux-UTF-8
        if [ -f /usr/share/i18n/charmaps/UTF-8.gz ] && command -v gzip >/dev/null 2>&1; then
            gzip -dc /usr/share/i18n/charmaps/UTF-8.gz > "$_map" || true
        elif [ -f /usr/share/i18n/charmaps/UTF-8 ]; then
            cp -f /usr/share/i18n/charmaps/UTF-8 "$_map"
        fi
        if [ -s "$_map" ]; then
            localedef --no-archive -c -i en_US -f "$_map" /usr/lib/locale/en_US.utf8 2>/dev/null || true
            localedef --no-archive -c -i POSIX -f "$_map" /usr/lib/locale/C.utf8 2>/dev/null || true
        else
            localedef --no-archive -c -i en_US -f UTF-8 /usr/lib/locale/en_US.utf8 2>/dev/null || true
        fi
        rm -f "$_map"
    fi
    mkdir -p /etc/profile.d
    cat > /etc/profile.d/flux-locale.sh <<'EOF'
_have=$(locale -a 2>/dev/null || true)
_pick=""
for _c in en_US.UTF-8 en_US.utf8 C.UTF-8 C.utf8; do
  echo "$_have" | grep -qxFi "$_c" && { _pick="$_c"; break; }
done
if [ -n "$_pick" ]; then
  export LANG="$_pick" LC_ALL="$_pick"
else
  unset LC_ALL
  export LANG=C
fi
unset _have _c _pick
EOF
    . /etc/profile.d/flux-locale.sh
    if [ -n "${LC_ALL:-}" ]; then
        printf 'LANG=%s\n' "$LANG" > /etc/locale.conf
    else
        printf 'LANG=C\n' > /etc/locale.conf
    fi
fi

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
ICON_TAR="papirus-dark-only.tar.xz"
# papirus-icon-theme is ~100MiB. Seed extras first; only pull the package
# if the XFCE category gate still fails (see after extract).

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

papirus_xfce_ok() {
    _p="$ICON_DIR/Papirus-Dark"
    [ -f "$_p/index.theme" ] || return 1
    for n in applications-internet applications-accessories applications-system; do
        _found=0
        for _d in "$_p/24x24/categories" "$_p/48x48/categories" "$_p/scalable/categories"; do
            [ -f "$_d/$n.svg" ] || [ -f "$_d/$n.png" ] || continue
            _found=1
            break
        done
        [ "$_found" = 1 ] || return 1
    done
    return 0
}

if [ "${FLUX_SKIP_THEME_ICONS:-0}" = "1" ]; then
    echo "FluxLinux: Themes/icons pre-installed on host — skip guest extract"
elif theme_is_installed "$SEL_THEME" && papirus_xfce_ok && cursor_is_installed "$SEL_CURSOR"; then
    echo "FluxLinux: Theme+icons+cursor already installed — skip extract"
else
    # Theme/icon/cursor failures must not abort before OMZ/pokemon.
    if ! theme_is_installed "$SEL_THEME"; then
        echo "FluxLinux: Installing theme $SEL_THEME..."
        TFILE="$(find_asset "$THEME_TAR" || true)"
        if [ -z "$TFILE" ]; then
            if download_if_needed "$BASE_URL/theme.zip" "/tmp/theme.zip"; then
                unzip -q -o /tmp/theme.zip -d /tmp/flux_theme_zip
                TFILE="$(find /tmp/flux_theme_zip -name "$THEME_TAR" 2>/dev/null | head -1)"
            else
                echo "FluxLinux: WARNING: theme download failed — continuing"
            fi
        fi
        if [ -n "$TFILE" ]; then
            extract_local_tar "$TFILE" "$THEME_DIR" \
                || echo "FluxLinux: WARNING: theme extract failed — continuing"
        fi
    fi
    if ! papirus_xfce_ok; then
        echo "FluxLinux: Installing icons $SEL_ICON..."
        IFILE="$(find_asset "$ICON_TAR" || find_asset "papirus-dark-only.tar.gz" || true)"
        if [ -z "$IFILE" ]; then
            if download_if_needed "$BASE_URL/icons.zip" "/tmp/icons.zip"; then
                unzip -q -o /tmp/icons.zip -d /tmp/flux_icons_zip
                IFILE="$(find /tmp/flux_icons_zip -name "$ICON_TAR" 2>/dev/null | head -1)"
                [ -n "$IFILE" ] || IFILE="$(find /tmp/flux_icons_zip -name "papirus-dark-only.tar.gz" 2>/dev/null | head -1)"
            else
                echo "FluxLinux: WARNING: icons download failed — continuing"
            fi
        fi
        if [ -z "$IFILE" ]; then
            echo "FluxLinux: WARNING: Papirus archive missing — continuing (OMZ/pokemon still run)"
        else
            extract_local_tar "$IFILE" "$ICON_DIR" "Papirus-Dark" 2>/dev/null || \
                extract_local_tar "$IFILE" "$ICON_DIR" || \
                echo "FluxLinux: WARNING: icons extract failed — continuing"
        fi
    fi
    if ! cursor_is_installed "$SEL_CURSOR"; then
        echo "FluxLinux: Installing cursor $SEL_CURSOR..."
        CFILE="$(find_asset "$CURSOR_TAR" || true)"
        if [ -z "$CFILE" ]; then
            if download_if_needed "$BASE_URL/cursor.zip" "/tmp/cursor.zip"; then
                unzip -q -o /tmp/cursor.zip -d /tmp/flux_cursor_zip
                CFILE="$(find /tmp/flux_cursor_zip -name "$CURSOR_TAR" 2>/dev/null | head -1)"
            else
                echo "FluxLinux: WARNING: cursor download failed — continuing"
            fi
        fi
        if [ -n "$CFILE" ]; then
            extract_local_tar "$CFILE" "$ICON_DIR" \
                || echo "FluxLinux: WARNING: cursor extract failed — continuing"
        fi
    fi
fi

# Papirus-Dark ships many dirs as symlinks into sibling Papirus/. The stub
# (and guests that never installed papirus-icon-theme) leave those dangling,
# so mkdir -p / cp print "File exists" / "No such file". Replace the link
# with a real directory before writing.
_flux_ensure_dir() {
    _p="$1"
    if [ -L "$_p" ] && [ ! -e "$_p" ]; then
        rm -f "$_p" 2>/dev/null || true
    elif [ -f "$_p" ]; then
        rm -f "$_p" 2>/dev/null || true
    elif [ -L "$_p" ]; then
        # Valid symlink (e.g. 16x16@2x -> 16x16): keep it, but if it points
        # at a file, drop it so we can mkdir.
        [ -d "$_p" ] || rm -f "$_p" 2>/dev/null || true
    fi
    mkdir -p "$_p" 2>/dev/null || true
    [ -d "$_p" ]
}

# Always repair Papirus-Dark even when extract was skipped (existing guests).
_flux_fix_papirus_dangling() {
    for _theme in Papirus-Dark Papirus; do
        [ -e "$ICON_DIR/$_theme" ] || continue
        for _sz in 16x16 22x22 24x24 32x32 48x48 64x64 \
                   16x16@2x 22x22@2x 24x24@2x 32x32@2x 48x48@2x; do
            _flux_ensure_dir "$ICON_DIR/$_theme/$_sz" || true
            for _ctx in status categories apps; do
                _flux_ensure_dir "$ICON_DIR/$_theme/$_sz/$_ctx" || true
            done
        done
    done
}

_flux_rewrite_papirus_inherits() {
    _idx="$ICON_DIR/Papirus-Dark/index.theme"
    [ -f "$_idx" ] || return 0
    _inh="Adwaita,hicolor"
    [ -d /usr/share/icons/AdwaitaLegacy ] && _inh="Adwaita,AdwaitaLegacy,hicolor"
    [ -d /usr/share/icons/adwaita-xfce ] && _inh="${_inh%,hicolor},adwaita-xfce,hicolor"
    _tmp="${_idx}.flux.$$"
    awk -v inh="$_inh" '
        BEGIN { done=0 }
        /^Inherits=/ { print "Inherits=" inh; done=1; next }
        { print }
        END { if (!done) print "Inherits=" inh }
    ' "$_idx" > "$_tmp" && mv "$_tmp" "$_idx"
    echo "FluxLinux: Papirus-Dark Inherits=$_inh"
}

_flux_find_category_icon() {
    # $1 = stem. Prints first matching file path.
    _stem="$1"
    for _c in \
        "$ICON_DIR/Papirus/24x24/categories/$_stem.svg" \
        "$ICON_DIR/Papirus/24x24/apps/$_stem.svg" \
        "$ICON_DIR/Papirus/48x48/categories/$_stem.svg" \
        "$ICON_DIR/Papirus/48x48/apps/$_stem.svg" \
        "$ICON_DIR/Papirus-Dark/24x24/apps/$_stem.svg" \
        "$ICON_DIR/Papirus-Dark/48x48/apps/$_stem.svg" \
        /usr/share/icons/adwaita-xfce/scalable/categories/"$_stem".svg \
        /usr/share/icons/AdwaitaLegacy/24x24/legacy/"$_stem".png \
        /usr/share/icons/AdwaitaLegacy/48x48/legacy/"$_stem".png \
        /usr/share/icons/Adwaita/24x24/categories/"$_stem".png \
        /usr/share/icons/Adwaita/48x48/categories/"$_stem".png \
        /usr/share/icons/Adwaita/scalable/categories/"$_stem".svg
    do
        if [ -f "$_c" ]; then
            printf '%s\n' "$_c"
            return 0
        fi
    done
    return 1
}

_flux_seed_papirus_categories() {
    _stems="applications-accessories applications-development applications-games applications-graphics applications-internet applications-multimedia applications-office applications-other applications-science applications-system applications-utilities preferences-desktop preferences-system xfce-settings"
    _flux_ensure_dir "$ICON_DIR/Papirus-Dark/24x24/categories" || true
    _flux_ensure_dir "$ICON_DIR/Papirus-Dark/48x48/categories" || true
    for _n in $_stems; do
        _src="$(_flux_find_category_icon "$_n" || true)"
        [ -n "$_src" ] || continue
        for _sz in 24x24 48x48; do
            _dest="$ICON_DIR/Papirus-Dark/$_sz/categories"
            _flux_ensure_dir "$_dest" || continue
            _ext="${_src##*.}"
            [ -f "$_dest/$_n.$_ext" ] || cp -f "$_src" "$_dest/$_n.$_ext"
        done
    done
}

_flux_fix_papirus_dangling
_flux_rewrite_papirus_inherits

# Overlay XFCE category extras only when the gate is not yet satisfied.
# Re-extracting onto a root-owned stub hangs some Android tars ("File exists").
CATFILE="$(find_asset "papirus-xfce-categories.tar.xz" || true)"
if [ -n "$CATFILE" ] && ! papirus_xfce_ok; then
    extract_local_tar "$CATFILE" "$ICON_DIR" \
        || echo "FluxLinux: WARNING: category extras extract failed — continuing"
fi

_flux_seed_papirus_categories

# Offline extras/stub usually satisfy the gate. Only then try the huge
# guest papirus-icon-theme package (Fedora ~100MiB).
if ! papirus_xfce_ok; then
    echo "FluxLinux: category icons still missing — trying papirus-icon-theme"
    _flux_pkg_add papirus-icon-theme 2>/dev/null \
        || _flux_pkg_add papirus-icon-theme-dark 2>/dev/null || true
    _flux_fix_papirus_dangling
    _flux_seed_papirus_categories
fi

# image-missing seed so GTK/glycin does not abort xfce4-panel.
_src_missing=""
for _c in /usr/share/icons/Adwaita/scalable/status/image-missing.svg \
          /usr/share/icons/Adwaita/symbolic/status/image-missing-symbolic.svg \
          /usr/share/icons/hicolor/scalable/status/image-missing.svg; do
    [ -f "$_c" ] && _src_missing="$_c" && break
done
if [ -n "$_src_missing" ]; then
    for _theme in Papirus-Dark Papirus; do
        [ -e "$ICON_DIR/$_theme" ] || continue
        for _sz in 16x16 22x22 24x24 32x32 48x48 64x64 \
                   16x16@2x 22x22@2x 24x24@2x 32x32@2x 48x48@2x; do
            _szd="$ICON_DIR/$_theme/$_sz"
            _flux_ensure_dir "$_szd" || continue
            _dest="$_szd/status"
            _flux_ensure_dir "$_dest" || continue
            [ -f "$_dest/image-missing.svg" ] || cp -f "$_src_missing" "$_dest/image-missing.svg" 2>/dev/null || true
        done
    done
fi

if papirus_xfce_ok; then
    SEL_ICON="Papirus-Dark"
    echo "FluxLinux: Icon theme Papirus-Dark (category icons present)"
else
    echo "FluxLinux: Papirus-Dark missing XFCE category icons — Adwaita fallback"
    SEL_ICON="Adwaita"
    mkdir -p "$ICON_DIR/Adwaita/24x24/categories"
    for n in applications-internet applications-accessories applications-system; do
        _src="$(_flux_find_category_icon "$n" || true)"
        [ -n "$_src" ] || continue
        _ext="${_src##*.}"
        [ -f "$ICON_DIR/Adwaita/24x24/categories/$n.$_ext" ] \
            || cp -f "$_src" "$ICON_DIR/Adwaita/24x24/categories/$n.$_ext"
    done
fi
gtk-update-icon-cache -f "$ICON_DIR/Papirus-Dark" 2>/dev/null || true

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

mkdir -p "$USER_HOME/.config/gtk-3.0"
cat > "$USER_HOME/.config/gtk-3.0/settings.ini" <<EOF
[Settings]
gtk-theme-name=$SEL_THEME
gtk-icon-theme-name=$SEL_ICON
gtk-cursor-theme-name=$SEL_CURSOR
gtk-font-name=JetBrainsMono Nerd Font 10
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

_flux_timeout_works() {
    # Chimera/BSD timeout under Android chroot dies with
    # "sigaction(32): Invalid argument" and never execs the child.
    command -v timeout >/dev/null 2>&1 || return 1
    timeout 1 true >/dev/null 2>&1
}

_flux_run() {
    _t="$1"; shift
    if _flux_timeout_works; then
        timeout "$_t" "$@"
        return $?
    fi
    # Portable watchdog — keep stderr so clone failures are visible.
    "$@" &
    _cmd=$!
    (
        _i=0
        while [ "$_i" -lt "$_t" ]; do
            kill -0 "$_cmd" 2>/dev/null || exit 0
            sleep 1
            _i=$((_i + 1))
        done
        echo "FluxLinux: command timed out after ${_t}s"
        kill "$_cmd" 2>/dev/null || true
        sleep 1
        kill -9 "$_cmd" 2>/dev/null || true
    ) &
    _wd=$!
    wait "$_cmd"
    _rc=$?
    kill "$_wd" 2>/dev/null || true
    wait "$_wd" 2>/dev/null || true
    return "$_rc"
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
    _flux_run "$_secs" git clone --depth 1 --single-branch "$_url" "$_dest"
}

echo "FluxLinux: Installing Oh My Zsh..."
OMZ_OK=0
if [ -f "$USER_HOME/.oh-my-zsh/oh-my-zsh.sh" ]; then
    echo "FluxLinux: Oh My Zsh already valid — skip install"
    OMZ_OK=1
else
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
            echo "FluxLinux: WARNING: Oh My Zsh clone failed or timed out — not treating as success"
            # Never leave a half tree, but never rm a good oh-my-zsh.sh either.
            if [ ! -f "$USER_HOME/.oh-my-zsh/oh-my-zsh.sh" ]; then
                _flux_safe_rm_tree "$USER_HOME/.oh-my-zsh"
            else
                OMZ_OK=1
            fi
        fi
    else
        echo "FluxLinux: WARNING: git unavailable — Oh My Zsh skipped"
    fi
fi

if [ "$OMZ_OK" = "1" ]; then
    ZSH_CUSTOM="$USER_HOME/.oh-my-zsh/custom"
    mkdir -p "$ZSH_CUSTOM/plugins" "$ZSH_CUSTOM/themes" 2>/dev/null || true
    echo "FluxLinux: Installing Zsh plugins (if missing)…"
    if [ ! -d "$ZSH_CUSTOM/plugins/zsh-autosuggestions/.git" ] \
        && [ ! -f "$ZSH_CUSTOM/plugins/zsh-autosuggestions/zsh-autosuggestions.zsh" ]; then
        echo "FluxLinux: Shallow clone zsh-autosuggestions (timeout 60s)…"
        if ! _flux_git_clone "https://github.com/zsh-users/zsh-autosuggestions.git" \
            "$ZSH_CUSTOM/plugins/zsh-autosuggestions" 60 \
            || [ ! -f "$ZSH_CUSTOM/plugins/zsh-autosuggestions/zsh-autosuggestions.zsh" ]; then
            echo "FluxLinux: WARNING: zsh-autosuggestions clone failed or timed out"
            [ -f "$ZSH_CUSTOM/plugins/zsh-autosuggestions/zsh-autosuggestions.zsh" ] \
                || _flux_safe_rm_tree "$ZSH_CUSTOM/plugins/zsh-autosuggestions"
        fi
    else
        echo "FluxLinux: zsh-autosuggestions already present — skip"
    fi
    if [ ! -d "$ZSH_CUSTOM/plugins/zsh-syntax-highlighting/.git" ] \
        && [ ! -f "$ZSH_CUSTOM/plugins/zsh-syntax-highlighting/zsh-syntax-highlighting.zsh" ]; then
        echo "FluxLinux: Shallow clone zsh-syntax-highlighting (timeout 60s)…"
        if ! _flux_git_clone "https://github.com/zsh-users/zsh-syntax-highlighting.git" \
            "$ZSH_CUSTOM/plugins/zsh-syntax-highlighting" 60 \
            || [ ! -f "$ZSH_CUSTOM/plugins/zsh-syntax-highlighting/zsh-syntax-highlighting.zsh" ]; then
            echo "FluxLinux: WARNING: zsh-syntax-highlighting clone failed or timed out"
            [ -f "$ZSH_CUSTOM/plugins/zsh-syntax-highlighting/zsh-syntax-highlighting.zsh" ] \
                || _flux_safe_rm_tree "$ZSH_CUSTOM/plugins/zsh-syntax-highlighting"
        fi
    else
        echo "FluxLinux: zsh-syntax-highlighting already present — skip"
    fi
    if [ ! -s "$ZSH_CUSTOM/themes/agnosterzak.zsh-theme" ]; then
        echo "FluxLinux: Installing agnosterzak theme…"
        if command -v wget >/dev/null 2>&1; then
            _flux_run 30 wget -q -O "$ZSH_CUSTOM/themes/agnosterzak.zsh-theme" \
                "https://raw.githubusercontent.com/zakaziko99/agnosterzak-ohmyzsh-theme/master/agnosterzak.zsh-theme" || true
        elif command -v curl >/dev/null 2>&1; then
            _flux_run 30 curl -fsSL --connect-timeout 10 --max-time 25 \
                "https://raw.githubusercontent.com/zakaziko99/agnosterzak-ohmyzsh-theme/master/agnosterzak.zsh-theme" \
                -o "$ZSH_CUSTOM/themes/agnosterzak.zsh-theme" || true
        fi
        if [ ! -s "$ZSH_CUSTOM/themes/agnosterzak.zsh-theme" ]; then
            echo "FluxLinux: WARNING: agnosterzak theme fetch failed"
            rm -f "$ZSH_CUSTOM/themes/agnosterzak.zsh-theme" 2>/dev/null || true
        fi
    else
        echo "FluxLinux: agnosterzak theme already present — skip"
    fi
    _flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME/.oh-my-zsh"
else
    echo "FluxLinux: Skipping Zsh plugins (Oh My Zsh not installed)"
fi

# pokemon-colorscripts: try by default (60s budget). Skip only if explicitly 1.
if [ "${FLUX_SKIP_POKEMON:-0}" = "1" ]; then
    echo "FluxLinux: pokemon-colorscripts skip (FLUX_SKIP_POKEMON=1)"
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
        (cd "$POKEMON_TEMP" && _flux_run 30 sh ./install.sh) \
            || echo "FluxLinux: WARNING: pokemon-colorscripts install.sh failed"
        if command -v pokemon-colorscripts >/dev/null 2>&1; then
            echo "FluxLinux: pokemon-colorscripts installed"
        else
            echo "FluxLinux: WARNING: pokemon-colorscripts not on PATH after install.sh"
        fi
    else
        echo "FluxLinux: WARNING: pokemon-colorscripts skipped (clone timeout/fail)"
    fi
    _flux_safe_rm_tree "$POKEMON_TEMP"
fi

echo "FluxLinux: Configuring .zshrc..."
ZSHRC="$USER_HOME/.zshrc"
cat > "$ZSHRC" << 'ZSHEOF'
# Guest PATH only — never inherit host PREFIX/bin (nested proot glue errors).
export PATH="$HOME/.local/bin:/opt/nodejs/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

if [ -x /usr/local/sbin/flux-ensure-locale ] && command -v sudo >/dev/null 2>&1; then
  sudo /usr/local/sbin/flux-ensure-locale 2>/dev/null || true
fi
_have=$(locale -a 2>/dev/null || true)
_pick=""
for _c in en_US.UTF-8 en_US.utf8 C.UTF-8 C.utf8; do
  echo "$_have" | grep -qxFi "$_c" && { _pick="$_c"; break; }
done
if [ -n "$_pick" ]; then
  export LANG="$_pick" LC_ALL="$_pick"
else
  unset LC_ALL
  export LANG=C
fi
unset _have _c _pick
export PYTHONIOENCODING=UTF-8

unset PROOT_TMP_DIR
export TMPDIR="${TMPDIR:-/tmp}"
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp}"

# proot does not implement tcsetpgrp — zsh job control would ENOSYS and the
# shell can get SIGTTIN-killed. Disable MONITOR (job control) for the guest.
setopt no_monitor

{
  if command -v fastfetch >/dev/null 2>&1; then
    _ff="$HOME/.local/share/fastfetch/presets/termux.jsonc"
    if [ -f "$_ff" ]; then
      fastfetch --config "$_ff" 2>/dev/null || true
    else
      fastfetch --config termux 2>/dev/null || true
    fi
    unset _ff
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
  if command -v apt-get >/dev/null 2>&1; then
    apt-get() { command sudo apt-get "$@"; }
  fi
  if command -v apt >/dev/null 2>&1; then
    apt() { command sudo apt "$@"; }
  fi
  if command -v pacman >/dev/null 2>&1; then
    pacman() { command sudo pacman "$@"; }
  fi
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

_flux_write_fastfetch_preset() {
    _home="$1"
    _owner="${2:-}"
    mkdir -p "$_home/.local/share/fastfetch/presets"
    cat > "$_home/.local/share/fastfetch/presets/termux.jsonc" <<'FFEOF'
{
  "logo": null,
  "display": { "separator": " ›  " },
  "modules": [
    { "type": "os", "key": "OS  " },
    { "type": "kernel", "key": "KER " },
    { "type": "cpu", "key": "CPU " },
    { "type": "gpu", "key": "GPU " },
    { "type": "packages", "key": "PKG " },
    { "type": "shell", "key": "SH  " },
    { "type": "terminal", "key": "TER " },
    {
      "type": "disk",
      "key": "DSK ",
      "folders": ["/"],
      "showRemovable": false,
      "showHidden": false,
      "showSubvolumes": false
    },
    { "type": "memory", "key": "MEM " },
    { "type": "swap", "key": "SWP " }
  ]
}
FFEOF
    if [ -n "$_owner" ]; then
        _flux_chown "$_owner" "$_home/.local/share/fastfetch/presets/termux.jsonc" 2>/dev/null || true
    fi
}
_flux_write_fastfetch_preset "$USER_HOME" "$CUSTOM_USER:$CUSTOM_GROUP"
_flux_write_fastfetch_preset /root
mkdir -p /usr/share/fastfetch/presets
if [ -f "$USER_HOME/.local/share/fastfetch/presets/termux.jsonc" ]; then
    cp -f "$USER_HOME/.local/share/fastfetch/presets/termux.jsonc" \
        /usr/share/fastfetch/presets/termux.jsonc 2>/dev/null || true
fi

if command -v zsh >/dev/null 2>&1; then
    ZSH_PATH="$(command -v zsh)"
    if [ -f /etc/shells ] && ! grep -qxF "$ZSH_PATH" /etc/shells 2>/dev/null; then
        echo "$ZSH_PATH" >> /etc/shells
    fi
    chsh -s "$ZSH_PATH" "$CUSTOM_USER" 2>/dev/null || true
fi

_flux_chown_r "$CUSTOM_USER:$CUSTOM_GROUP" "$USER_HOME" 2>/dev/null || true

# Proot-safe PM db ownership (portable stat: GNU -c / BSD -f)
_ref_u=$(stat -c %u /etc 2>/dev/null || stat -f %u /etc 2>/dev/null || true)
_ref_g=$(stat -c %g /etc 2>/dev/null || stat -f %g /etc 2>/dev/null || true)
if [ -n "$_ref_u" ]; then
    for p in /var/lib/dnf /var/cache/dnf /var/lib/rpm /var/db/xbps /var/cache/xbps \
             /var/cache/zypp /var/lib/zypp \
             /lib/apk /usr/lib/apk /var/cache/apk /etc/apk \
             /var/lib/pacman /var/cache/pacman /etc/pacman.d \
             /var/lib/apt /var/cache/apt /var/lib/dpkg; do
        [ -e "$p" ] || continue
        chown -R "$_ref_u:$_ref_g" "$p" 2>/dev/null || true
    done
fi

echo "FluxLinux: XFCE4 Customization complete!"
exit 0
