#!/bin/sh
# setup_alpine_chroot.sh
# Installs Alpine 3.24 minirootfs chroot (Requires Root).
# Family/XFCE install is owned by onboarding family phase (setup_alpine_family.sh).

ALPINEPATH="${FLUX_CHROOT:-/data/local/tmp/chrootAlpine}"
USERNAME="flux"

PKG="${TERMUX_APP__PACKAGE_NAME:-com.ivarna.fluxlinux}"
APP_HOME="${TERMUX__HOME:-/data/data/${PKG}/files/home}"
APP_PREFIX="${TERMUX__PREFIX:-/data/data/${PKG}/files/usr}"
ROOTFS_NAME="alpine_3.24_rootfs.tar.gz"
ROOTFS_URL="${FLUX_ROOTFS_URL:-https://github.com/abhay-byte/fluxlinux/releases/download/rootfs/alpine_3.24_rootfs.tar.gz}"
ROOTFS_SHA256="${FLUX_ROOTFS_SHA256:-f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259}"

progress() { printf "\033[1;36m[+] %s\033[0m\n" "$1"; }
success()  { printf "\033[1;32m[✓] %s\033[0m\n" "$1"; }
error()    { printf "\033[1;31m[!] %s\033[0m\n" "$1"; }

cleanup_mounts() {
    progress "Safety Check: Unmounting filesystems..."
    $BB umount "$ALPINEPATH/sdcard" 2>/dev/null || true
    $BB umount "$ALPINEPATH/mnt/host-tmp" 2>/dev/null || true
    $BB umount "$ALPINEPATH/dev/shm" 2>/dev/null || true
    $BB umount "$ALPINEPATH/dev/pts" 2>/dev/null || true
    $BB umount "$ALPINEPATH/proc" 2>/dev/null || true
    $BB umount "$ALPINEPATH/sys" 2>/dev/null || true
    $BB umount "$ALPINEPATH/dev" 2>/dev/null || true
    $BB umount "$ALPINEPATH/tmp" 2>/dev/null || true
    return 0
}

goodbye() {
    error "Something went wrong."
    cleanup_mounts
    error "Exiting..."
    exit 1
}

resolve_rootfs_archive() {
    ROOTFS_ARCHIVE=""
    if [ -n "${FLUX_ROOTFS_PATH:-}" ] && [ -f "$FLUX_ROOTFS_PATH" ] && [ -s "$FLUX_ROOTFS_PATH" ]; then
        ROOTFS_ARCHIVE="$FLUX_ROOTFS_PATH"
        progress "rootfs from FLUX_ROOTFS_PATH=$ROOTFS_ARCHIVE"
        return 0
    fi
    for candidate in \
        "$APP_HOME/$ROOTFS_NAME" \
        "$APP_HOME/rootfs/$ROOTFS_NAME" \
        "$APP_PREFIX/var/lib/proot-distro/cache/rootfs/$ROOTFS_NAME" \
        "/sdcard/Download/$ROOTFS_NAME" \
        "/storage/emulated/0/Download/$ROOTFS_NAME" \
        "$ALPINEPATH/$ROOTFS_NAME"
    do
        if [ -f "$candidate" ] && [ -s "$candidate" ]; then
            ROOTFS_ARCHIVE="$candidate"
            progress "rootfs found: $ROOTFS_ARCHIVE"
            return 0
        fi
    done
    return 1
}

download_file() {
    # $1=dir $2=filename $3=url
    progress "Downloading file..."
    if [ -e "$1/$2" ] && [ -s "$1/$2" ]; then
        printf "\033[1;33m[!] File already exists: %s\033[0m\n" "$2"
        return 0
    fi
    mkdir -p "$1" 2>/dev/null || true
    if command -v wget >/dev/null 2>&1; then
        wget -O "$1/$2" "$3" && success "File downloaded: $2" && return 0
    fi
    progress "Trying Busybox wget..."
    $BB wget -O "$1/$2" "$3" && success "File downloaded (Fallback)" && return 0
    goodbye
}

extract_file() {
    _dest="$1"
    _archive="${2:-$_dest/$ROOTFS_NAME}"
    progress "Extracting file from $_archive ..."
    # bin/sh may be absolute symlink to /bin/busybox — host -e is false.
    if [ -L "$_dest/bin/sh" ] || [ -e "$_dest/bin/sh" ] || \
       [ -x "$_dest/bin/busybox" ] || [ -e "$_dest/sbin/apk" ]; then
        printf "\033[1;33m[!] Rootfs appears populated: %s\033[0m\n" "$_dest"
        return 0
    fi
    if [ ! -f "$_archive" ] || [ ! -s "$_archive" ]; then
        error "Rootfs archive missing: $_archive"
        goodbye
    fi
    mkdir -p "$_dest"
    if tar xpf "$_archive" -C "$_dest" --numeric-owner >/dev/null 2>&1; then
        success "Rootfs extracted successfully."
        return 0
    fi
    if tar xzpf "$_archive" -C "$_dest" --numeric-owner >/dev/null 2>&1; then
        success "Rootfs extracted successfully (gzip)."
        return 0
    fi
    error "Extraction Failed!"
    goodbye
}

ensure_chroot_tmp() {
    HOST_TMP="/data/data/${PKG}/files/usr/tmp"
    mkdir -p "$ALPINEPATH/tmp" "$HOST_TMP" "$ALPINEPATH/mnt/host-tmp" "$ALPINEPATH/var/tmp"
    if grep -q " $ALPINEPATH/tmp " /proc/mounts 2>/dev/null; then
        $BB umount "$ALPINEPATH/tmp" 2>/dev/null || $BB umount -l "$ALPINEPATH/tmp" 2>/dev/null || true
    fi
    chmod 1777 "$ALPINEPATH/tmp" 2>/dev/null || true
    chmod 1777 "$ALPINEPATH/var/tmp" 2>/dev/null || true
    progress "Sticky /tmp ready at $ALPINEPATH/tmp"
    chmod 1777 "$HOST_TMP" 2>/dev/null || true
    $BB mount --bind "$HOST_TMP" "$ALPINEPATH/mnt/host-tmp" 2>/dev/null || true
}

guest_sh() {
    # Run command string as root in chroot with ash (pre-bash)
    $BB chroot "$ALPINEPATH" /bin/sh -c "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; $1"
}

configure_alpine_chroot() {
    progress "Configuring Alpine chroot environment..."

    if [ ! -d "$ALPINEPATH" ]; then
        mkdir -p "$ALPINEPATH" || goodbye
    fi

    progress "Mounting filesystems..."
    /system/bin/mount -o remount,dev,suid /data 2>/dev/null \
        || $BB mount -o remount,dev,suid /data 2>/dev/null \
        || $BB mount -o remount,dev,suid / 2>/dev/null || true

    $BB mount --bind /dev "$ALPINEPATH/dev" || goodbye
    $BB mount --bind /sys "$ALPINEPATH/sys" || goodbye
    $BB mount -t proc proc "$ALPINEPATH/proc" || goodbye
    $BB mount -t devpts devpts "$ALPINEPATH/dev/pts" || goodbye

    mkdir -p "$ALPINEPATH/dev/shm"
    $BB mount -t tmpfs -o size=512M,mode=1777 tmpfs "$ALPINEPATH/dev/shm" || goodbye

    ensure_chroot_tmp

    mkdir -p "$ALPINEPATH/sdcard"
    $BB mount --bind /sdcard "$ALPINEPATH/sdcard" || goodbye

    progress "Configuring Network and Android groups..."
    guest_sh '
        rm -f /etc/resolv.conf
        echo "nameserver 8.8.8.8" > /etc/resolv.conf
        echo "nameserver 1.1.1.1" >> /etc/resolv.conf
        echo "127.0.0.1 localhost" > /etc/hosts

        # Android GIDs (busybox addgroup -g)
        addgroup -g 3003 aid_inet 2>/dev/null || true
        addgroup -g 3004 aid_net_raw 2>/dev/null || true
        addgroup -g 1003 aid_graphics 2>/dev/null || true
        addgroup root aid_inet 2>/dev/null || true

        echo "Testing Network..."
        if ping -c 1 8.8.8.8 >/dev/null 2>&1; then
            echo " [OK] Network is working."
        else
            echo " [!] Network check failed. apk might fail."
        fi
    ' || goodbye

    progress "Installing bootstrap packages (bash sudo shadow)..."
    guest_sh '
        chmod 1777 /tmp /var/tmp 2>/dev/null || true
        apk update || exit 1
        apk add --no-cache bash zsh sudo shadow ca-certificates curl wget nano || exit 1
    ' || goodbye

    progress "Creating User ($USERNAME)..."
    guest_sh "
        if ! id $USERNAME >/dev/null 2>&1; then
            if command -v useradd >/dev/null 2>&1; then
                useradd -m -s /bin/zsh $USERNAME 2>/dev/null || \
                    useradd -m -s /bin/bash $USERNAME 2>/dev/null || \
                    useradd -m -s /bin/sh $USERNAME
            else
                adduser -D -s /bin/sh $USERNAME
            fi
            echo '$USERNAME:flux' | chpasswd 2>/dev/null || true
        fi
        for g in wheel audio video netdev aid_inet; do
            addgroup $USERNAME \$g 2>/dev/null || true
        done
        mkdir -p /etc/sudoers.d
        echo '$USERNAME ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/$USERNAME
        chmod 0440 /etc/sudoers.d/$USERNAME
    " || goodbye

    # XFCE is installed by setup_alpine_family.sh in onboarding (cleaner single owner)
    touch "$ALPINEPATH/.flux_configured"
    success "Alpine Environment Configured (rootfs + user; XFCE via family phase)!"

    # Stage SSOT helper
    HELPER="/data/local/tmp/fluxlinux_chroot.sh"
    progress "Installing chroot SSOT helper at $HELPER..."
    HELPER_SRC=""
    for cand in \
        "/data/data/${PKG}/files/home/fluxlinux_chroot.sh" \
        "/data/data/${PKG}/files/staged_scripts/fluxlinux_chroot.sh" \
        "$(dirname "$0")/fluxlinux_chroot.sh"
    do
        if [ -f "$cand" ]; then
            HELPER_SRC="$cand"
            break
        fi
    done
    if [ -n "$HELPER_SRC" ]; then
        cp -f "$HELPER_SRC" "$HELPER"
        chmod 755 "$HELPER"
        success "SSOT helper installed from $HELPER_SRC"
    else
        error "fluxlinux_chroot.sh not found — app will stage on first session"
    fi

    # Stage Alpine GUI scripts if present in app home
    for s in start_alpine_gui.sh stop_alpine_gui.sh; do
        src="/data/data/${PKG}/files/home/$s"
        if [ -f "$src" ]; then
            cp -f "$src" "/data/local/tmp/$s"
            chmod +x "/data/local/tmp/$s"
            success "Staged $s"
        fi
    done

    # Thin CLI launcher
    CLI_SCRIPT="/data/local/tmp/enter_alpine.sh"
    cat > "$CLI_SCRIPT" <<'EOF'
#!/system/bin/sh
HELPER=/data/local/tmp/fluxlinux_chroot.sh
[ -f "$HELPER" ] || { echo "fluxlinux_chroot.sh missing" >&2; exit 127; }
export FLUX_CHROOT="${FLUX_CHROOT:-/data/local/tmp/chrootAlpine}"
echo "Entering Alpine Chroot (CLI)..."
exec sh "$HELPER" login --user flux --shell zsh
EOF
    chmod 755 "$CLI_SCRIPT"

    cleanup_mounts
    success "FluxLinux: Alpine Chroot Setup Complete!"

    am start -a android.intent.action.VIEW \
        -d "fluxlinux://callback?result=success&name=distro_install_alpine_chroot" \
        >/dev/null 2>&1 || true
    return 0
}

main() {
    export LD_LIBRARY_PATH="${APP_PREFIX}/lib"

    if [ "$(id -u)" != "0" ]; then
        error "This script must be run as root. Exiting."
        exit 1
    fi

    BB=""
    if command -v busybox >/dev/null 2>&1; then
        DETECTED_BB=$(command -v busybox)
        case "$DETECTED_BB" in
            *com.termux*|*fluxlinux*|*nativecode*) ;;
            *) [ -x "$DETECTED_BB" ] && BB="$DETECTED_BB" ;;
        esac
    fi
    if [ -z "$BB" ]; then
        for path in \
            /data/adb/ksu/bin/busybox \
            /data/adb/magisk/busybox \
            /data/adb/modules/busybox-ndk/system/bin/busybox \
            /sbin/busybox /system/xbin/busybox /system/bin/busybox /debug_ramdisk/busybox
        do
            if [ -x "$path" ]; then BB="$path"; break; fi
        done
    fi
    if [ -z "$BB" ]; then
        error "Root-capable Busybox not found!"
        exit 1
    fi
    progress "Using Root Busybox: $BB"

    ALPINEPATH="${FLUX_CHROOT:-/data/local/tmp/chrootAlpine}"

    # Only treat as fully installed when marker + bash + flux user exist.
    # Minirootfs always has bin/sh + sbin/apk — those alone must NOT skip configure.
    if [ -f "$ALPINEPATH/.flux_configured" ] && \
       { [ -e "$ALPINEPATH/bin/bash" ] || [ -e "$ALPINEPATH/usr/bin/bash" ]; } && \
       grep -q '^flux:' "$ALPINEPATH/etc/passwd" 2>/dev/null; then
        success "Alpine Chroot already installed."
        progress "VERIFY: chroot present at $ALPINEPATH"
        am start -a android.intent.action.VIEW \
            -d "fluxlinux://callback?result=success&name=distro_install_alpine_chroot" \
            >/dev/null 2>&1 || true
        exit 0
    fi

    mkdir -p "$ALPINEPATH" || goodbye

    if resolve_rootfs_archive; then
        :
    else
        progress "No local rootfs — downloading $ROOTFS_URL"
        download_file "$ALPINEPATH" "$ROOTFS_NAME" "$ROOTFS_URL"
        ROOTFS_ARCHIVE="$ALPINEPATH/$ROOTFS_NAME"
    fi

    if [ -n "$ROOTFS_SHA256" ] && command -v sha256sum >/dev/null 2>&1; then
        _got="$(sha256sum "$ROOTFS_ARCHIVE" | awk '{print $1}')"
        if [ "$_got" != "$ROOTFS_SHA256" ]; then
            error "SHA256 mismatch for $ROOTFS_ARCHIVE"
            error "  expected $ROOTFS_SHA256"
            error "  got      $_got"
            goodbye
        fi
        success "SHA256 OK"
    fi

    extract_file "$ALPINEPATH" "$ROOTFS_ARCHIVE"
    configure_alpine_chroot
    exit 0
}

main "$@"
