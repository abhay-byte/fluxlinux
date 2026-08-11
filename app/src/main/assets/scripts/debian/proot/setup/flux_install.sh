#!/bin/bash
# flux_install.sh — install/configure proot-distro guest from local rootfs archive
# One-click (scripts page): no args → debian + local setup_debian_family.sh
# Onboarding: flux_install.sh debian <base64 setup script>
# Paths: TermuxHostPaths via fluxlinux-host.env (SSOT), never stock com.termux.

set -u

DISTRO="${1:-debian}"
SETUP_B64="${2:-}"

# Pinned rootfs (packaged as assets/rootfs/, deployed to $HOME)
ROOTFS_NAME="debian_13_rootfs.tar.xz"
ROOTFS_URL="${FLUX_ROOTFS_URL:-https://github.com/abhay-byte/fluxlinux/releases/download/rootfs/debian_13_rootfs.tar.xz}"
ROOTFS_SHA256="${FLUX_ROOTFS_SHA256:-13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803}"

# Resolve script directory (deployed to $HOME or $HOME/scripts)
SCRIPT_DIR="$(cd "$(dirname "$0")" 2>/dev/null && pwd)" || SCRIPT_DIR=""
DEFAULT_SETUP=""
for candidate in \
    "${SCRIPT_DIR}/setup_debian_family.sh" \
    "${HOME:-}/setup_debian_family.sh" \
    "${HOME:-}/scripts/setup_debian_family.sh"
do
    if [ -n "$candidate" ] && [ -f "$candidate" ]; then
        DEFAULT_SETUP="$candidate"
        break
    fi
done

PKG="${TERMUX_APP__PACKAGE_NAME:-com.ivarna.fluxlinux}"
PREFIX_DEFAULT="${TERMUX__PREFIX:-/data/data/${PKG}/files/usr}"

# SSOT env from Kotlin TermuxHostPaths
_HOST_ENV="${PREFIX_DEFAULT}/etc/fluxlinux-host.env"
if [ -r "$_HOST_ENV" ]; then
    # shellcheck source=/dev/null
    . "$_HOST_ENV"
fi

export TERMUX_APP__PACKAGE_NAME="${TERMUX_APP__PACKAGE_NAME:-$PKG}"
export TERMUX__PREFIX="${TERMUX__PREFIX:-/data/data/${TERMUX_APP__PACKAGE_NAME}/files/usr}"
export TERMUX__HOME="${TERMUX__HOME:-/data/data/${TERMUX_APP__PACKAGE_NAME}/files/home}"
export PREFIX="${PREFIX:-$TERMUX__PREFIX}"
export HOME="${HOME:-$TERMUX__HOME}"
export TMPDIR="${TMPDIR:-$PREFIX/tmp}"
export PROOT_TMP_DIR="${PROOT_TMP_DIR:-$TMPDIR}"
export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:-$PREFIX/lib}"
export PATH="$PREFIX/bin:$PREFIX/bin/applets:/system/bin:/system/xbin${PATH:+:$PATH}"

# Snapshot W^X proot paths from the ProcessBuilder / host env before profile can clobber.
# Without PD_PROOT_BIN + PROOT_LOADER (jniLibs), proot cannot exec guest bash on targetSdk 36.
_SAVED_PD_PROOT_BIN="${PD_PROOT_BIN:-}"
_SAVED_PROOT_LOADER="${PROOT_LOADER:-}"
_SAVED_PROOT_LOADER_32="${PROOT_LOADER_32:-}"

# Load rewritten host profile (paths must match PREFIX)
if [ -r "$PREFIX/etc/profile" ]; then
    # shellcheck source=/dev/null
    . "$PREFIX/etc/profile" || true
    export TERMUX_APP__PACKAGE_NAME="${TERMUX_APP__PACKAGE_NAME}"
    export TERMUX__PREFIX="$PREFIX"
    export TERMUX__HOME="$HOME"
    export PREFIX
    export HOME
    export TMPDIR="$PREFIX/tmp"
    export PROOT_TMP_DIR="$TMPDIR"
    export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:-$PREFIX/lib}"
    export PATH="$PREFIX/bin:$PREFIX/bin/applets:/system/bin:/system/xbin${PATH:+:$PATH}"
fi

# Re-pin W^X proot binaries after profile (profile must not win over jniLibs).
if [ -n "$_SAVED_PD_PROOT_BIN" ]; then
    export PD_PROOT_BIN="$_SAVED_PD_PROOT_BIN"
fi
if [ -n "$_SAVED_PROOT_LOADER" ]; then
    export PROOT_LOADER="$_SAVED_PROOT_LOADER"
fi
if [ -n "$_SAVED_PROOT_LOADER_32" ]; then
    export PROOT_LOADER_32="$_SAVED_PROOT_LOADER_32"
fi

mkdir -p "$TMPDIR" 2>/dev/null || true

PYTHON="${PREFIX}/bin/python"
PROOT_DISTRO="${PREFIX}/bin/proot-distro"
if [ ! -x "$PYTHON" ]; then
    echo "FluxLinux: missing $PYTHON"
    exit 1
fi
if [ ! -f "$PROOT_DISTRO" ]; then
    echo "FluxLinux: missing $PROOT_DISTRO"
    exit 1
fi

# Fail early with a clear message if W^X proot paths are unset (host ProcessBuilder bug).
if [ -z "${PD_PROOT_BIN:-}" ] || [ ! -e "${PD_PROOT_BIN}" ]; then
    echo "FluxLinux: PD_PROOT_BIN missing or not found (${PD_PROOT_BIN:-unset})"
    echo "FluxLinux: Host env must set PD_PROOT_BIN to libproot.so under nativeLibraryDir."
    exit 1
fi
if [ -z "${PROOT_LOADER:-}" ] || [ ! -e "${PROOT_LOADER}" ]; then
    echo "FluxLinux: PROOT_LOADER missing or not found (${PROOT_LOADER:-unset})"
    echo "FluxLinux: Host env must set PROOT_LOADER to libloader.so under nativeLibraryDir."
    exit 1
fi

echo "FluxLinux: Debugging Environment:"
echo "HOME=$HOME"
echo "PREFIX=$PREFIX"
echo "TERMUX__HOME=$TERMUX__HOME"
echo "TERMUX__PREFIX=$TERMUX__PREFIX"
echo "TERMUX_APP__PACKAGE_NAME=$TERMUX_APP__PACKAGE_NAME"
echo "LD_LIBRARY_PATH=$LD_LIBRARY_PATH"
echo "PD_PROOT_BIN=$PD_PROOT_BIN"
echo "PROOT_LOADER=$PROOT_LOADER"
echo "PROOT_LOADER_32=${PROOT_LOADER_32:-}"
echo "TMPDIR=$TMPDIR"
echo "DISTRO=$DISTRO"
echo "----------------------------------------"

# Resolve local rootfs archive for proot-distro install ./path --name <distro>
# Path must start with / ./ ../ or ~ so proot-distro treats it as a file, not registry.
resolve_rootfs_archive() {
    ROOTFS_ARCHIVE=""

    if [ -n "${FLUX_ROOTFS_PATH:-}" ] && [ -f "$FLUX_ROOTFS_PATH" ] && [ -s "$FLUX_ROOTFS_PATH" ]; then
        ROOTFS_ARCHIVE="$FLUX_ROOTFS_PATH"
        echo "FluxLinux: rootfs from FLUX_ROOTFS_PATH=$ROOTFS_ARCHIVE"
        return 0
    fi

    # Prefer absolute paths under HOME/PREFIX (app-deployed asset)
    for candidate in \
        "$HOME/$ROOTFS_NAME" \
        "$HOME/rootfs/$ROOTFS_NAME" \
        "$PREFIX/var/lib/proot-distro/cache/rootfs/$ROOTFS_NAME" \
        "/sdcard/Download/$ROOTFS_NAME" \
        "/sdcard/Download/rootfs.tar.xz" \
        "/storage/emulated/0/Download/$ROOTFS_NAME" \
        "/storage/emulated/0/Download/rootfs.tar.xz"
    do
        if [ -f "$candidate" ] && [ -s "$candidate" ]; then
            ROOTFS_ARCHIVE="$candidate"
            echo "FluxLinux: rootfs found: $ROOTFS_ARCHIVE"
            return 0
        fi
    done

    # Optional download into cache (escape hatch if asset not deployed)
    if [ "${FLUX_PD_INSTALL_MODE:-file}" = "registry" ]; then
        return 1
    fi

    CACHE_DIR="$PREFIX/var/lib/proot-distro/cache/rootfs"
    mkdir -p "$CACHE_DIR" 2>/dev/null || true
    DEST="$CACHE_DIR/$ROOTFS_NAME"
    echo "FluxLinux: rootfs not in app paths — downloading $ROOTFS_URL"
    if command -v curl >/dev/null 2>&1; then
        if curl -fL --retry 3 --retry-delay 2 -o "$DEST.partial" "$ROOTFS_URL" \
            && mv -f "$DEST.partial" "$DEST"; then
            ROOTFS_ARCHIVE="$DEST"
            echo "FluxLinux: rootfs downloaded: $ROOTFS_ARCHIVE"
            return 0
        fi
        rm -f "$DEST.partial" 2>/dev/null || true
    elif command -v wget >/dev/null 2>&1; then
        if wget -O "$DEST.partial" "$ROOTFS_URL" && mv -f "$DEST.partial" "$DEST"; then
            ROOTFS_ARCHIVE="$DEST"
            echo "FluxLinux: rootfs downloaded: $ROOTFS_ARCHIVE"
            return 0
        fi
        rm -f "$DEST.partial" 2>/dev/null || true
    fi

    echo "FluxLinux: no rootfs archive found (expected $HOME/$ROOTFS_NAME from app assets)"
    return 1
}

verify_rootfs_sha() {
    _file="$1"
    [ -z "$ROOTFS_SHA256" ] && return 0
    if command -v sha256sum >/dev/null 2>&1; then
        _got="$(sha256sum "$_file" | awk '{print $1}')"
        if [ "$_got" != "$ROOTFS_SHA256" ]; then
            echo "FluxLinux: SHA256 mismatch for $_file"
            echo "  expected $ROOTFS_SHA256"
            echo "  got      $_got"
            return 1
        fi
        echo "FluxLinux: SHA256 OK"
    fi
    return 0
}

echo "FluxLinux: Installing $DISTRO..."

if [ "$DISTRO" = "termux" ]; then
    echo "FluxLinux: Native Termux Mode"
    EXIT_CODE=0
else
    DISTRO_ROOTFS="$PREFIX/var/lib/proot-distro/containers/$DISTRO/rootfs"

    if [ -d "$DISTRO_ROOTFS" ] && [ -e "$DISTRO_ROOTFS/bin/sh" ]; then
        echo "FluxLinux: $DISTRO already installed with valid rootfs. Skipping base installation."
        EXIT_CODE=0
    else
        if [ "${FLUX_PD_INSTALL_MODE:-file}" = "registry" ]; then
            echo "FluxLinux: FLUX_PD_INSTALL_MODE=registry — installing $DISTRO from registry"
            rm -rf "$PREFIX/var/lib/proot-distro/containers/$DISTRO"
            "$PYTHON" "$PROOT_DISTRO" install "$DISTRO"
            EXIT_CODE=$?
        else
            if ! resolve_rootfs_archive; then
                echo "FluxLinux: Install Failed — no local rootfs archive"
                exit 1
            fi
            # Ensure absolute path so proot-distro never treats name as registry image
            case "$ROOTFS_ARCHIVE" in
                /*|~*) ;;
                ./*|../*) ;;
                *) ROOTFS_ARCHIVE="$(cd "$(dirname "$ROOTFS_ARCHIVE")" && pwd)/$(basename "$ROOTFS_ARCHIVE")" ;;
            esac
            if ! verify_rootfs_sha "$ROOTFS_ARCHIVE"; then
                exit 1
            fi
            echo "FluxLinux: Installing $DISTRO from local archive..."
            echo "FluxLinux: install $ROOTFS_ARCHIVE --name $DISTRO"
            rm -rf "$PREFIX/var/lib/proot-distro/containers/$DISTRO"
            "$PYTHON" "$PROOT_DISTRO" install "$ROOTFS_ARCHIVE" --name "$DISTRO"
            EXIT_CODE=$?
            if [ "$EXIT_CODE" -eq 0 ] && [ ! -e "$DISTRO_ROOTFS/bin/sh" ]; then
                echo "FluxLinux: install reported OK but $DISTRO_ROOTFS/bin/sh missing"
                EXIT_CODE=1
            fi
        fi
    fi
fi

if [ "$EXIT_CODE" -ne 0 ]; then
    echo "FluxLinux: Install Failed with code $EXIT_CODE!"
    exit 1
fi

echo "FluxLinux: Install Successful!"

# Resolve setup script: base64 payload (onboarding) OR local setup_debian_family.sh (one-click)
SETUP_HOST_PATH="$TMPDIR/flux_setup_temp.sh"
SETUP_MODE=""

if [ -n "$SETUP_B64" ] && [ "$SETUP_B64" != "null" ]; then
    echo "FluxLinux: Configuring from base64 payload..."
    if ! echo "$SETUP_B64" | base64 -d > "$SETUP_HOST_PATH"; then
        echo "FluxLinux: base64 decode failed"
        exit 1
    fi
    SETUP_MODE="b64"
elif [ -n "$DEFAULT_SETUP" ]; then
    echo "FluxLinux: Configuring from local setup: $DEFAULT_SETUP"
    cp "$DEFAULT_SETUP" "$SETUP_HOST_PATH" || {
        echo "FluxLinux: failed to copy $DEFAULT_SETUP"
        exit 1
    }
    SETUP_MODE="local"
else
    echo "FluxLinux: No setup payload and no setup_debian_family.sh found — install only."
    SETUP_MODE=""
fi

if [ -n "$SETUP_MODE" ]; then
    chmod +x "$SETUP_HOST_PATH"

    if [ "$DISTRO" = "termux" ]; then
        bash "$SETUP_HOST_PATH"
        SETUP_EXIT=$?
    else
        # Ensure shared tmp exists and is writable before proot binds it as guest /tmp
        mkdir -p "$TMPDIR" 2>/dev/null || true
        # --shared-tmp: host $PREFIX/tmp → guest /tmp (setup script lands at /tmp/flux_setup_temp.sh)
        # Explicit /bin/bash in guest avoids PATH issues inside a fresh rootfs.
        echo "FluxLinux: Running setup inside proot (shared-tmp)…"
        echo "FluxLinux: PD_PROOT_BIN=$PD_PROOT_BIN PROOT_LOADER=$PROOT_LOADER"
        "$PYTHON" "$PROOT_DISTRO" login "$DISTRO" --shared-tmp -- \
            /bin/bash -c "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; bash /tmp/flux_setup_temp.sh $DISTRO"
        SETUP_EXIT=$?
    fi

    rm -f "$SETUP_HOST_PATH"

    if [ "$SETUP_EXIT" -ne 0 ]; then
        echo "FluxLinux: Configuration/Setup Script Failed! (exit $SETUP_EXIT)"
        echo "FluxLinux: Hint: if you saw 'execve(...): Permission denied' or 'loader was not found',"
        echo "  ensure PROOT_LOADER points at libloader.so (jniLibs) and proot-distro was patched"
        echo "  to pass PROOT_LOADER through (TermuxHostPaths.patchProotDistroLoaderPassThrough)."
        exit 1
    fi
    echo "FluxLinux: Configuration Complete!"
fi

touch "$HOME/.fluxlinux_distro_${DISTRO}_installed"
echo "Distro installation and configuration completed successfully!"
exit 0
