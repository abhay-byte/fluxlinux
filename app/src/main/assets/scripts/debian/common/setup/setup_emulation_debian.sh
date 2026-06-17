#!/bin/bash
# setup_emulation_debian.sh
# Installs Gaming & Windows Emulation Stack
# Target: Debian 13 (Trixie) ARM64

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

echo "FluxLinux Pro: Setting up Gaming & Emulation Environment..."
echo "Target: Debian 13 (Trixie) - ARM64"

export DEBIAN_FRONTEND=noninteractive

# 1. Install Dependencies
echo "FluxLinux Pro: Installing Dependencies..."
apt update -y
apt install -y \
    wget \
    curl \
    git \
    gnu-which \
    gnupg \
    tar \
    debian-keyring \
    debian-archive-keyring \
    apt-transport-https \
    apt-transport-https \
    xz-utils \
    libgl1-mesa-dri \
    libglx-mesa0 \
    libgl1 \
    mesa-vulkan-drivers \
    || handle_error "Dependencies"

# Ensure 'which' command exists (required by xow64)
apt install -y debianutils || handle_error "Debian Utils"

# 2. Install Box64 (Ryan Fortner Repo)
echo "FluxLinux Pro: Installing Box64 (Ryan Fortner Repo)..."
# Add repo
wget https://ryanfortner.github.io/box86-debs/box86.list -O /etc/apt/sources.list.d/box86.list
wget -qO- https://ryanfortner.github.io/box86-debs/KEY.gpg | gpg --dearmor --yes -o /etc/apt/trusted.gpg.d/box86-debs-archive-keyring.gpg

# APT Pinning to prioritize Ryan's repo over Debian's official one
cat <<EOF > /etc/apt/preferences.d/box64
Package: *
Pin: origin ryanfortner.github.io
Pin-Priority: 1001
EOF

apt update -y
apt-cache policy box64
apt install -y box64 || handle_error "Box64 Installation"

# 3. Install xow64-wine (Windows Emulation)
echo "FluxLinux Pro: Setting up xow64-wine..."
# Cleanup old
rm -rf ~/xow64
# Download script
wget https://github.com/ar37-rs/xow64-wine/raw/refs/heads/main/proot_mode/xow64 -O ~/xow64 || handle_error "xow64 Download"
chmod +x ~/xow64

# FIX: Set TMPDIR to /tmp to prevent wineserver from trying to access Android/Termux paths
# This fixes "wineserver: mkdir /data/data/.../usr/tmp/..." error
export TMPDIR=/tmp
mkdir -p /tmp

# CRITICAL FIX: wineserver inside xow64 seemingly ignores TMPDIR or defaults to Termux path.
# We create the directory it's complaining about to pacify it.
# Wineserver error: mkdir /data/data/com.termux/files/usr/tmp/.wine-0: No such file or directory
mkdir -p /data/data/com.termux/files/usr/tmp
chmod 777 /data/data/com.termux/files/usr/tmp 2>/dev/null

echo "Configuring xow64..."
# Ensure we don't have ownership conflict (common in Proot)
mkdir -p "$HOME/xow64_prefix"
chown -R "$(whoami)" "$HOME/xow64_prefix"

# PATCH: xow64 script uses 'tar -xf' which preserves ownership, causing "not owned by you" error in Proot.
# We patch it to use '--no-same-owner' to force ownership to current user.
sed -i 's/tar -xf/tar --no-same-owner -xf/g' ~/xow64
sed -i 's/tar -xvf/tar --no-same-owner -xvf/g' ~/xow64

~/xow64 proot=true

echo "Installing xow64 components (Wine/DXVK)..."
echo "NOTE: This may take some time and might be interactive."
# Force ownership fix again just before install
chown -R "$(whoami)" "$HOME/xow64_prefix" 2>/dev/null
TMPDIR=/tmp ~/xow64 install || echo " [⚠️] xow64 install had issues (check logs)"

# 4. Install Heroic Games Launcher (Native ARM64)
echo "FluxLinux Pro: Installing Heroic Games Launcher..."

# Robust URL Fetcher: API -> HTML Scraping -> Fallback
# 1. Try GitHub API
HEROIC_URL=$(curl -s https://api.github.com/repos/Heroic-Games-Launcher/HeroicGamesLauncher/releases/latest | grep "browser_download_url" | grep "arm64.deb" | cut -d '"' -f 4 | head -n 1)

# 2. Scrape generic latest release for any arm64 .deb
# This regex looks for hrefs ending in arm64.deb case-insensitive if grep supports it, or just standard.
if [ -z "$HEROIC_URL" ]; then
    echo " [ℹ️] Heroic API fetch failed, scraping releases page..."
    # Get the HTML, look for hrefs with .deb and arm64, grab the first one.
    # We use wget to stdout, then grep.
    HEROIC_FRAGMENT=$(wget -qO- https://github.com/Heroic-Games-Launcher/HeroicGamesLauncher/releases/latest | grep -o 'href="[^"]*arm64\.deb"' | head -n 1 | cut -d '"' -f 2)
    if [ ! -z "$HEROIC_FRAGMENT" ]; then
        HEROIC_URL="https://github.com$HEROIC_FRAGMENT"
    fi
fi

# 3. Fallback: If scraper failed, try known variations of the filename for 2.15.2
if [ -z "$HEROIC_URL" ]; then
    echo " [⚠️] Could not resolve Heroic URL dynamically. Trying fallback patterns..."
    # Heroic 2.15.2 filename variations
    URLS=(
        "https://github.com/Heroic-Games-Launcher/HeroicGamesLauncher/releases/download/v2.15.2/Heroic-2.15.2-linux-arm64.deb"
        "https://github.com/Heroic-Games-Launcher/HeroicGamesLauncher/releases/download/v2.15.2/heroic_2.15.2_linux_arm64.deb"
        "https://github.com/Heroic-Games-Launcher/HeroicGamesLauncher/releases/download/v2.15.2/heroic-2.15.2-linux-arm64.deb"
    )
    for URL in "${URLS[@]}"; do
        if wget --spider -q "$URL"; then
            HEROIC_URL="$URL"
            break
        fi
    done
fi

echo "Downloading Heroic: $HEROIC_URL"
# Verify link availability
if wget --spider -q "$HEROIC_URL"; then
    rm -f /tmp/heroic.deb
    wget -O /tmp/heroic.deb "$HEROIC_URL"
    apt install -y /tmp/heroic.deb || echo " [⚠️] Heroic Install Failed (Dependency?)"
    rm -f /tmp/heroic.deb
else
    echo " [❌] Heroic Download Failed (404/Network) - URL: $HEROIC_URL"
fi

# 5. Install RetroArch & DOSBox
echo "FluxLinux Pro: Installing RetroArch & DOSBox..."
apt install -y \
    retroarch \
    dosbox \
    || handle_error "RetroArch/DOSBox Installation"


# 6. Verification
verify_installation() {
    echo ""
    echo "🔎 FluxLinux Pro: Verifying Installations..."
    echo "------------------------------------------------"
    
    if command -v box64 >/dev/null; then echo " [✅] Box64"; else echo " [❌] Box64 Missing"; fi
    if [ -f "$HOME/xow64" ]; then echo " [✅] xow64 Script"; else echo " [❌] xow64 Script Missing"; fi
    if command -v heroic >/dev/null; then echo " [✅] Heroic Launcher"; else echo " [❌] Heroic Missing"; fi
    if command -v retroarch >/dev/null; then echo " [✅] RetroArch"; else echo " [❌] RetroArch Missing"; fi
    if command -v dosbox >/dev/null; then echo " [✅] DOSBox"; else echo " [❌] DOSBox Missing"; fi

    echo "------------------------------------------------"
    echo "🎉 Gaming & Emulation Setup Complete!"
}

verify_installation

echo "Note:"
echo "1. Run Windows apps using '~/xow64 run <exe>' or via Heroic."
echo "2. Launch Heroic, RetroArch, or DOSBox from the Applications menu."
read -p "Press Enter to close..."
