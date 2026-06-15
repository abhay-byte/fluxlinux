#!/bin/bash
# setup_appdev_debian.sh
# Installs App Development stack (Android SDK, Flutter, React Native)
# Target: Debian 13 (Trixie) ARM64
# Usage: setup_appdev_debian.sh [uninstall]

# App-dev-specific apt packages. Shared tools (adb, fastboot, chromium, cmake,
# ninja-build, clang, git, curl, wget) intentionally excluded — used by
# other components (gengdev, webdev, etc.).
PKGS=(
    libgtk-3-dev
    liblzma-dev
    libstdc++-12-dev
    aapt
    mesa-utils
    openjdk-21-jdk
    openjdk-21-jre
    openjdk-21-jre-headless
    openjdk-21-jdk-headless
    default-jre-headless
    default-jre
    default-jdk-headless
    default-jdk
)

# ─── UNINSTALL MODE ──────────────────────────────────────────────────────
if [ "$1" = "uninstall" ]; then
    echo "FluxLinux: Uninstalling App Development Environment..."

    export DEBIAN_FRONTEND=noninteractive

    # Remove the JDK first (it was the workaround install — clean up
    # broken postinst files if present, then purge)
    dpkg -l | grep -q "^iU.*openjdk\|^iF.*openjdk" && \
        dpkg --remove --force-all openjdk-21-jre-headless openjdk-21-jre openjdk-21-jdk-headless openjdk-21-jdk default-jre-headless default-jre default-jdk-headless default-jdk 2>/dev/null || true

    apt remove -y --purge "${PKGS[@]}" 2>/dev/null || true
    apt autoremove -y 2>/dev/null || true

    # Remove Android SDK
    rm -rf /opt/android-sdk

    # Remove Flutter
    rm -rf /opt/flutter
    rm -f /usr/local/bin/flutter /usr/local/bin/dart
    rm -rf /home/flux/.config/flutter 2>/dev/null || true

    # Remove Kotlin
    rm -rf /opt/kotlin /opt/kotlinc
    rm -f /usr/local/bin/kotlin /usr/local/bin/kotlinc

    # Remove Gradle (manual install, not the legacy apt one)
    rm -rf /opt/gradle
    rm -f /usr/bin/gradle /usr/local/bin/gradle

    # Revert .bashrc / .zshrc env vars added by this script
    for shell_rc in /home/flux/.bashrc /home/flux/.zshrc; do
        if [ -f "$shell_rc" ]; then
            sed -i '/^export PATH="\/opt\/flutter\/bin:\$PATH"$/d' "$shell_rc" 2>/dev/null || true
            sed -i '/^export ANDROID_SDK_ROOT="\/opt\/android-sdk"$/d' "$shell_rc" 2>/dev/null || true
            sed -i '/^export ANDROID_HOME="\/opt\/android-sdk"$/d' "$shell_rc" 2>/dev/null || true
            sed -i '/^export PATH="\$ANDROID_HOME\/cmdline-tools\/latest\/bin:\$PATH"$/d' "$shell_rc" 2>/dev/null || true
            sed -i '/^export CHROME_EXECUTABLE=\/usr\/bin\/chromium$/d' "$shell_rc" 2>/dev/null || true
        fi
    done

    # Remove system-wide profile.d entry
    rm -f /etc/profile.d/android-sdk.sh

    # Remove Flutter user config (regen'd on next install)
    rm -rf /home/flux/.flutter

    echo "FluxLinux: App Development Environment Uninstalled."
    exit 0
fi
# ─── END UNINSTALL MODE ──────────────────────────────────────────────────

# Error Handler
handle_error() {
    echo ""
    echo "❌ FluxLinux Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    echo "Please check the error message above for details."
    echo "---------------------------------------------------"
    read -p "Press Enter to acknowledge error and exit..."
    exit 1
}

echo "FluxLinux: Setting up App Development Environment (Android + Flutter + React Native)..."
# Ensure we set ownership to the 'flux' user (since we verify using sudo/root)
TARGET_USER="flux"
TARGET_GROUP="users"

# CRITICAL: Remove broken Java packages FIRST (before any apt operations)
# If Java packages are in a broken state, apt will try to configure them during
# any apt operation, which will fail if /proc is not mounted
echo "FluxLinux: Checking for broken Java packages..."
if dpkg -l | grep -q "^iU.*openjdk\|^iF.*openjdk"; then
    echo "FluxLinux: Found broken Java packages, removing..."
    dpkg --remove --force-all openjdk-21-jre-headless openjdk-21-jre openjdk-21-jdk-headless openjdk-21-jdk default-jre-headless default-jre default-jdk-headless default-jdk 2>/dev/null || true
    apt autoremove -y 2>/dev/null || true
    echo "FluxLinux: Broken packages removed"
fi

# 1. Install Dependencies
echo "FluxLinux: Installing System Dependencies..."
export DEBIAN_FRONTEND=noninteractive
apt update -y
# Core deps + Flutter Linux deps + React Native deps
apt install -y git wget curl unzip zip xz-utils file \
    libgtk-3-dev liblzma-dev libstdc++-12-dev \
    libgtk-3-dev liblzma-dev libstdc++-12-dev \
    adb fastboot aapt cmake ninja-build \
    clang mesa-utils chromium \
    || handle_error "Dependencies Installation"

# Remove legacy Gradle (Debian ships ancient 4.4.1)
apt remove -y gradle >/dev/null 2>&1 || true

# 1b. Install Java (Workaround for chroot /proc issue)
# Standard OpenJDK installation requires /proc to be mounted, which may fail in chroot
# This workaround removes the postinst scripts that check for /proc
echo "FluxLinux: Installing Java Development Kit..."
export DEBIAN_FRONTEND=noninteractive

# Try standard installation first (works if /proc is properly mounted)
if apt-get install -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" openjdk-21-jdk 2>/dev/null; then
    JAVA_VERSION="21"
    echo "FluxLinux: OpenJDK 21 installed successfully"
else
    # Fallback: Install without postinst scripts (workaround for /proc issue)
    echo "FluxLinux: Standard installation failed, using workaround method..."
    
    # Download packages
    apt-get download openjdk-21-jdk openjdk-21-jre openjdk-21-jre-headless openjdk-21-jdk-headless 2>/dev/null || handle_error "Java package download"
    
    # Unpack without configuring
    dpkg --unpack openjdk-*.deb 2>/dev/null || true
    
    # Remove problematic postinst scripts
    rm -f /var/lib/dpkg/info/openjdk-21-*.postinst 2>/dev/null || true
    rm -f /var/lib/dpkg/info/default-*.postinst 2>/dev/null || true
    
    # Configure without postinst
    dpkg --configure -a 2>/dev/null || true
    
    # Install dependencies
    apt-get install -f -y 2>/dev/null || true
    
    # Clean up downloaded debs
    rm -f openjdk-*.deb
    
    # Manually set up alternatives (postinst scripts were removed)
    echo "FluxLinux: Configuring Java alternatives..."
    JAVA_HOME="/usr/lib/jvm/java-21-openjdk-arm64"
    
    if [ -d "$JAVA_HOME" ]; then
        # Create alternatives for java, javac, jar, and javadoc
        update-alternatives --install /usr/bin/java java $JAVA_HOME/bin/java 2111 \
            --slave /usr/bin/jexec jexec $JAVA_HOME/lib/jexec 2>/dev/null || true
        
        update-alternatives --install /usr/bin/javac javac $JAVA_HOME/bin/javac 2111 2>/dev/null || true
        update-alternatives --install /usr/bin/jar jar $JAVA_HOME/bin/jar 2111 2>/dev/null || true
        update-alternatives --install /usr/bin/javadoc javadoc $JAVA_HOME/bin/javadoc 2111 2>/dev/null || true
        
        echo "FluxLinux: Java alternatives configured"
    else
        echo "FluxLinux: Warning - Could not find Java installation at $JAVA_HOME"
    fi
    
    JAVA_VERSION="21"
    echo "FluxLinux: OpenJDK 21 installed via workaround method"
fi

# Fix libjli.so path issue (Common in fresh/broken Trixie installs)
LIBJLI_PATH=$(find /usr/lib/jvm -name "libjli.so" 2>/dev/null | head -1)
if [ -n "$LIBJLI_PATH" ]; then
    LIBJLI_DIR=$(dirname "$LIBJLI_PATH")
    export LD_LIBRARY_PATH="$LIBJLI_DIR:$LD_LIBRARY_PATH"
    echo "FluxLinux: Added $LIBJLI_DIR to LD_LIBRARY_PATH"
fi

# Verify Java installation
if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -q "openjdk"; then
    echo "FluxLinux: Java Status: $(java -version 2>&1 | head -1)"
elif [ -f "/usr/lib/jvm/java-21-openjdk-arm64/bin/java" ]; then
    echo "FluxLinux: Java binaries installed but alternatives not configured"
    echo "FluxLinux: Run 'update-alternatives --config java' to configure"
else
    echo "FluxLinux: Warning - Java installation may have failed"
fi

# 2. Android SDK Setup
SDK_ROOT="/opt/android-sdk"
# Check for sdkmanager binary to confirm valid install
if [ ! -f "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "FluxLinux: Installing Android SDK..."
    # Clean partial install
    if [ -d "$SDK_ROOT" ]; then
        echo "FluxLinux: Found partial/corrupt Android SDK. Cleaning..."
        rm -rf "$SDK_ROOT"
    fi
    
    mkdir -p "$SDK_ROOT/cmdline-tools"
    cd "$SDK_ROOT/cmdline-tools"
    
    # Download Command Line Tools (verified AArch64 compatible)
    # Using version 11076708 (stable)
    echo "FluxLinux: Downloading Android Command Line Tools..."
    wget --show-progress https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O tools.zip || handle_error "Android Tools Download"
    unzip tools.zip || handle_error "Android Tools Unzip"
    mv cmdline-tools latest
    rm tools.zip
    
    # Environment Variables for Session
    export ANDROID_HOME="$SDK_ROOT"
    export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
    
    # Accept Licenses (Silence is golden)
    echo "FluxLinux: Accepting Android Licenses..."
    yes | sdkmanager --licenses
fi

# Unconditionally Update/Patch SDK (Allows re-run to fix corruption/paths)
# Environment Variables for Session (Ensure they are set even if existing)
export ANDROID_HOME="$SDK_ROOT"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Install Components
echo "FluxLinux: Installing Platform Tools, SDK & NDK..."
# NOTE: We now use ARM64 native build-tools from lzhiyong/android-sdk-tools (35.0.2)
# This provides ARM64-compatible aapt, aapt2, aidl, zipalign for SDK 35/36 support

# Clean cmdline-tools path inconsistency (latest-2 vs latest)
if [ -d "$SDK_ROOT/cmdline-tools/latest-2" ]; then
    echo "FluxLinux: Fixing cmdline-tools path..."
    rm -rf "$SDK_ROOT/cmdline-tools/latest"
    mv "$SDK_ROOT/cmdline-tools/latest-2" "$SDK_ROOT/cmdline-tools/latest"
fi

# Clean up inconsistent NDK directories before sdkmanager (only backups and duplicates)
echo "FluxLinux: Cleaning up inconsistent NDK installations..."
for ndk_dir in "$SDK_ROOT/ndk/"*; do
    if [ -d "$ndk_dir" ]; then
        ndk_name=$(basename "$ndk_dir")
        # Only remove backup directories and numbered duplicates
        if [[ "$ndk_name" == *".x86_backup"* ]] || [[ "$ndk_name" == *"-2"* ]]; then
            echo " - Removing inconsistent NDK: $ndk_name"
            rm -rf "$ndk_dir"
        fi
    fi
done

echo "FluxLinux: Installing Platform Tools, SDK 34/35/36..."
# NOTE: NDK is installed separately as ARM64 native versions
# DO NOT install NDK via sdkmanager - it downloads x86 binaries
sdkmanager "platform-tools" \
           "cmdline-tools;latest" \
           "platforms;android-34" \
           "platforms;android-35" \
           "platforms;android-36" \
           "build-tools;35.0.0" \
           "build-tools;36.0.0" \
           "cmake;3.22.1" \
           || handle_error "Android SDK Components"
           
# Fix CMake & Ninja (Android SDK bundles x86 binaries)
# Strategy: SHELL WRAPPER.
# We create a script that execs /usr/bin/cmake. This preserves CMAKE_ROOT resolution (which Hard Copy breaks).
echo "FluxLinux: Patching Android SDK CMake/Ninja (Wrapper Script)..."

find "$SDK_ROOT/cmake" -name "cmake" -type f | while read -r binary; do
    echo " - Wrapping $binary -> /usr/bin/cmake"
    rm -f "$binary"
    cat <<EOF > "$binary"
#!/bin/sh
exec /usr/bin/cmake "\$@"
EOF
    chmod +x "$binary"
done

find "$SDK_ROOT/cmake" -name "ninja" -type f | while read -r binary; do
    echo " - Wrapping $binary -> /usr/bin/ninja"
    rm -f "$binary"
    cat <<EOF > "$binary"
#!/bin/sh
exec /usr/bin/ninja "\$@"
EOF
    chmod +x "$binary"
done

# Verify Patch Content
echo "FluxLinux: Verifying CMake Wrapper Content:"
find "$SDK_ROOT/cmake" -name "cmake" -type f -print -exec cat {} \; -quit

# Project-level Fix: Inject cmake.dir and ndk.dir into found local.properties
echo "FluxLinux: Configuring local projects..."
find /home/$TARGET_USER -name "local.properties" 2>/dev/null | while read -r prop; do
    # CMake fix
    if ! grep -q "cmake.dir" "$prop"; then
        echo "cmake.dir=/usr" >> "$prop"
        echo " - Patched $prop with cmake.dir=/usr"
    fi
    # NDK path fix (ensure ARM64 NDK is used)
    if ! grep -q "ndk.dir" "$prop"; then
        echo "ndk.dir=$SDK_ROOT/ndk/29.0.14206865" >> "$prop"
        echo " - Patched $prop with ndk.dir"
    else
        # Update existing ndk.dir to point to correct version
        sed -i "s|ndk.dir=.*|ndk.dir=$SDK_ROOT/ndk/29.0.14206865|" "$prop"
    fi
done

# ADB/Fastboot: Replace SDK x86 binaries with wrapper scripts to apt's ARM64 binaries
echo "FluxLinux: Patching Android SDK ADB/Fastboot (Wrapper Script)..."

PLATFORM_TOOLS="$SDK_ROOT/platform-tools"
mkdir -p "$PLATFORM_TOOLS"

# Create adb wrapper
if [ -f "$PLATFORM_TOOLS/adb" ]; then
    rm -f "$PLATFORM_TOOLS/adb"
fi
cat <<EOF > "$PLATFORM_TOOLS/adb"
#!/bin/sh
exec /usr/bin/adb "\$@"
EOF
chmod +x "$PLATFORM_TOOLS/adb"
echo " - Wrapped $PLATFORM_TOOLS/adb -> /usr/bin/adb"

# Create fastboot wrapper
if [ -f "$PLATFORM_TOOLS/fastboot" ]; then
    rm -f "$PLATFORM_TOOLS/fastboot"
fi
cat <<EOF > "$PLATFORM_TOOLS/fastboot"
#!/bin/sh
exec /usr/bin/fastboot "\$@"
EOF
chmod +x "$PLATFORM_TOOLS/fastboot"
echo " - Wrapped $PLATFORM_TOOLS/fastboot -> /usr/bin/fastboot"

# Fix NDK (SDK bundles x86_64 binaries - need ARM64)
# Install ARM64 NDK from HomuHomu833/android-ndk-custom (musl-based, statically linked)
# NOTE: termux-ndk uses Bionic libc and fails on glibc systems

# Function to install ARM64 NDK for a specific version
install_arm64_ndk() {
    local NDK_VER="$1"
    local NDK_RELEASE="$2"      # GitHub release tag (e.g., r27, r29)
    local NDK_SOURCE_DIR="$3"   # Tarball name prefix (e.g., r27d, r29)
    
    local NDK_DIR="$SDK_ROOT/ndk/$NDK_VER"
    # Note: GitHub uses $NDK_RELEASE for tag, but tarball contains $NDK_SOURCE_DIR
    local ARM64_NDK_URL="https://github.com/HomuHomu833/android-ndk-custom/releases/download/$NDK_RELEASE/android-ndk-$NDK_SOURCE_DIR-aarch64-linux-musl.tar.xz"
    local ARM64_NDK_TAR="/tmp/android-ndk-$NDK_SOURCE_DIR-aarch64-linux-musl.tar.xz"
    local ARM64_NDK_EXTRACTED="/tmp/android-ndk-$NDK_SOURCE_DIR"
    
    echo "FluxLinux: Checking NDK $NDK_VER ($NDK_RELEASE)..."
    
    # Check if NDK needs installation or replacement
    local NEEDS_INSTALL=true
    if [ -d "$NDK_DIR" ]; then
        # Check if it's already ARM64 native
        if [ -d "$NDK_DIR/toolchains/llvm/prebuilt/linux-arm64" ]; then
            if [ -f "$NDK_DIR/toolchains/llvm/prebuilt/linux-arm64/bin/clang" ]; then
                echo "   [✅] ARM64 NDK $NDK_RELEASE already installed"
                NEEDS_INSTALL=false
            fi
        elif [ -L "$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64" ]; then
            # Symlink exists - likely ARM64 version with compatibility link
            echo "   [✅] ARM64 NDK $NDK_RELEASE already configured"
            NEEDS_INSTALL=false
        fi
        # If x86_64 directory exists but is not a symlink, we need to replace
    fi
    
    if [ "$NEEDS_INSTALL" = true ]; then
        echo "   Installing ARM64 native NDK $NDK_RELEASE..."
        
        # Download ARM64 NDK (force redownload to avoid corrupt files)
        if [ -f "$ARM64_NDK_TAR" ]; then
            # Verify the file is a valid xz archive
            if ! xz -t "$ARM64_NDK_TAR" 2>/dev/null; then
                echo "   Existing download corrupt - removing..."
                rm -f "$ARM64_NDK_TAR"
            fi
        fi
        
        if [ ! -f "$ARM64_NDK_TAR" ]; then
            echo "   Downloading NDK $NDK_RELEASE..."
            wget -q --show-progress "$ARM64_NDK_URL" -O "$ARM64_NDK_TAR" || { echo "   [⚠️] Download failed"; rm -f "$ARM64_NDK_TAR"; return; }
        fi
        
        # Extract
        rm -rf "$ARM64_NDK_EXTRACTED" 2>/dev/null || true
        rm -rf /tmp/ndk-extract-$NDK_RELEASE 2>/dev/null || true
        mkdir -p /tmp/ndk-extract-$NDK_RELEASE
        tar -xf "$ARM64_NDK_TAR" -C /tmp/ndk-extract-$NDK_RELEASE || { echo "   [⚠️] Extract failed - removing corrupt file"; rm -f "$ARM64_NDK_TAR"; return; }
        
        # Find the extracted directory (could be android-ndk-r27, android-ndk-r27d, etc.)
        EXTRACTED_DIR=$(find /tmp/ndk-extract-$NDK_RELEASE -maxdepth 1 -type d -name "android-ndk-*" | head -1)
        if [ -z "$EXTRACTED_DIR" ]; then
            echo "   [⚠️] Could not find extracted NDK directory"
            ls -la /tmp/ndk-extract-$NDK_RELEASE/
            return
        fi
        mv "$EXTRACTED_DIR" "$ARM64_NDK_EXTRACTED" 2>/dev/null || true
        
        # Backup old NDK and install ARM64 version
        if [ -d "$NDK_DIR" ]; then
            rm -rf "${NDK_DIR}.x86_backup" 2>/dev/null || true
            mv "$NDK_DIR" "${NDK_DIR}.x86_backup" 2>/dev/null || true
        fi
        
        # Create parent directory if needed
        mkdir -p "$(dirname "$NDK_DIR")"
        mv "$ARM64_NDK_EXTRACTED" "$NDK_DIR"
        
        # Verify
        local PREBUILT_DIR="$NDK_DIR/toolchains/llvm/prebuilt"
        if [ -d "$PREBUILT_DIR/linux-arm64" ] || [ -L "$PREBUILT_DIR/linux-x86_64" ]; then
            if [ -f "$PREBUILT_DIR/linux-arm64/bin/clang" ] || [ -f "$PREBUILT_DIR/linux-x86_64/bin/clang" ]; then
                echo "   [✅] ARM64 NDK $NDK_RELEASE installed successfully"
            else
                echo "   [⚠️] clang binary not found"
            fi
        else
            echo "   [⚠️] Installation verification failed"
        fi
    fi
}

echo "FluxLinux: Installing ARM64 NDKs (static/musl)..."

# Install ARM64 NDK 27 (r27d) - GitHub tag is "r27", tarball is "r27d", version is 27.3.13750724
install_arm64_ndk "27.3.13750724" "r27" "r27d"

# Install ARM64 NDK 29 (r29)
install_arm64_ndk "29.0.14206865" "r29" "r29"
           
# Set Permissions
chown -R $TARGET_USER:$TARGET_GROUP "$SDK_ROOT"
chmod -R 777 "$SDK_ROOT" # Wide permissions for ease of use in Chroot



# 3. Flutter Setup
FLUTTER_ROOT="/opt/flutter"
# Check for flutter binary
if [ ! -f "$FLUTTER_ROOT/bin/flutter" ]; then
    echo "FluxLinux: Installing Flutter SDK (Stable)..."
    # Clean partial
    if [ -d "$FLUTTER_ROOT" ]; then
        echo "FluxLinux: Found partial Flutter. Cleaning..."
        rm -rf "$FLUTTER_ROOT"
    fi
    
    # Git clone is the official way for ARM64
    git clone https://github.com/flutter/flutter.git -b stable "$FLUTTER_ROOT" || handle_error "Flutter Clone"
    
else
    echo "FluxLinux: Flutter already installed."
fi # End of initial Flutter installation check

# Minimal Flutter Configuration
# 1. Essential Permissions & Git Config
git config --system --add safe.directory '*'
chown -R $TARGET_USER:$TARGET_GROUP "$FLUTTER_ROOT"
chmod -R 775 "$FLUTTER_ROOT"
# Fix /dev/null for non-root git usage
chmod 666 /dev/null 2>/dev/null || true

# 2. Configure Android SDK (Single requested command)
echo "FluxLinux: Setting Flutter Android SDK..."

ACTUAL_USER="flux"
USER_GROUP="users"

echo "FluxLinux: Flutter already installed."
echo "FluxLinux: Setting Flutter Android SDK..."
echo "FluxLinux: Fixing permissions..."

# Fix permissions with correct group
chown -R $ACTUAL_USER:$USER_GROUP /opt/flutter /opt/android-sdk
chmod -R 755 /opt/flutter/bin

# Create Flutter config directory
mkdir -p /home/$ACTUAL_USER/.config/flutter
chown -R $ACTUAL_USER:$USER_GROUP /home/$ACTUAL_USER/.config

# Write settings to ~/.config/flutter/settings (legacy format)
cat > /home/$ACTUAL_USER/.config/flutter/settings << EOF
android-sdk=/opt/android-sdk
android-sdk-path=/opt/android-sdk
EOF
chown $ACTUAL_USER:$USER_GROUP /home/$ACTUAL_USER/.config/flutter/settings

# Write settings to ~/.flutter (Flutter's primary config file - JSON format)
# Use /usr/lib/android-sdk where apt installs adb
cat > /home/$ACTUAL_USER/.flutter << EOF
{
  "android-sdk": "/usr/lib/android-sdk",
  "android-studio-dir": "/usr/lib/android-sdk"
}
EOF
chown $ACTUAL_USER:$USER_GROUP /home/$ACTUAL_USER/.flutter

# Add environment variables to .bashrc
if ! grep -q "/opt/flutter/bin" /home/$ACTUAL_USER/.bashrc 2>/dev/null; then
    echo 'export PATH="/opt/flutter/bin:$PATH"' >> /home/$ACTUAL_USER/.bashrc
    echo 'export ANDROID_SDK_ROOT="/opt/android-sdk"' >> /home/$ACTUAL_USER/.bashrc
    echo 'export ANDROID_HOME="/opt/android-sdk"' >> /home/$ACTUAL_USER/.bashrc
    echo 'export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"' >> /home/$ACTUAL_USER/.bashrc
    echo 'export CHROME_EXECUTABLE=/usr/bin/chromium' >> /home/$ACTUAL_USER/.bashrc
    chown $ACTUAL_USER:$USER_GROUP /home/$ACTUAL_USER/.bashrc
fi

# Add environment variables to .zshrc (if zsh is installed)
if command -v zsh >/dev/null 2>&1; then
    ZSHRC="/home/$ACTUAL_USER/.zshrc"
    # Create .zshrc if it doesn't exist
    [ ! -f "$ZSHRC" ] && touch "$ZSHRC" && chown $ACTUAL_USER:$USER_GROUP "$ZSHRC"
    
    if ! grep -q "/opt/flutter/bin" "$ZSHRC" 2>/dev/null; then
        echo 'export PATH="/opt/flutter/bin:$PATH"' >> "$ZSHRC"
        echo 'export ANDROID_SDK_ROOT="/opt/android-sdk"' >> "$ZSHRC"
        echo 'export ANDROID_HOME="/opt/android-sdk"' >> "$ZSHRC"
        echo 'export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"' >> "$ZSHRC"
        echo 'export CHROME_EXECUTABLE=/usr/bin/chromium' >> "$ZSHRC"
        chown $ACTUAL_USER:$USER_GROUP "$ZSHRC"
        echo "FluxLinux: Added Flutter/Android SDK paths to .zshrc"
    fi
fi

# Set system-wide environment variables (for all users)
cat > /etc/profile.d/android-sdk.sh << 'ENVEOF'
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
ENVEOF
chmod 644 /etc/profile.d/android-sdk.sh

echo "FluxLinux: Flutter Android SDK configured."

# 4. Kotlin (Manual Install for shared access)
KOTLIN_ROOT="/opt/kotlin"
if [ ! -f "$KOTLIN_ROOT/bin/kotlinc" ]; then
    echo "FluxLinux: Installing Kotlin Compiler..."
    # Clean partial
    [ -d "$KOTLIN_ROOT" ] && rm -rf "$KOTLIN_ROOT"
    
    # Download 2.1.0 compiler
    wget --show-progress https://github.com/JetBrains/kotlin/releases/download/v2.1.0/kotlin-compiler-2.1.0.zip -O /tmp/kotlin.zip || handle_error "Kotlin Download"
    unzip -q /tmp/kotlin.zip -d /opt
    mv /opt/kotlinc "$KOTLIN_ROOT"
    rm /tmp/kotlin.zip
fi

# 4b. Gradle (Manual Install, System version is too old)
# Using Gradle 9.2.1 - latest stable version
GRADLE_VER="9.2.1"

# Check if already installed with correct version
if [ -f "/opt/gradle/bin/gradle" ]; then
    INSTALLED_VER=$(/opt/gradle/bin/gradle --version 2>/dev/null | grep "Gradle" | head -1 | awk '{print $2}')
    if [ "$INSTALLED_VER" = "$GRADLE_VER" ]; then
        echo "FluxLinux: Gradle $GRADLE_VER already installed."
    else
        echo "FluxLinux: Updating Gradle from $INSTALLED_VER to $GRADLE_VER..."
        rm -rf /opt/gradle
        rm -f /tmp/gradle.zip
    fi
fi

if [ ! -f "/opt/gradle/bin/gradle" ] || [ "$(/opt/gradle/bin/gradle --version 2>/dev/null | grep 'Gradle' | head -1 | awk '{print $2}')" != "$GRADLE_VER" ]; then
    echo "FluxLinux: Installing Gradle $GRADLE_VER..."
    
    # Download with retry
    GRADLE_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VER}-bin.zip"
    if [ ! -f "/tmp/gradle.zip" ]; then
        wget -q --show-progress "$GRADLE_URL" -O /tmp/gradle.zip || \
        wget -q --show-progress "$GRADLE_URL" -O /tmp/gradle.zip || \
        handle_error "Gradle Download"
    fi
    
    # Verify download is valid zip
    if ! unzip -t /tmp/gradle.zip >/dev/null 2>&1; then
        echo "FluxLinux: Downloaded file corrupt, retrying..."
        rm -f /tmp/gradle.zip
        wget -q --show-progress "$GRADLE_URL" -O /tmp/gradle.zip || handle_error "Gradle Download"
    fi
    
    unzip -q /tmp/gradle.zip -d /opt
    rm /tmp/gradle.zip
    [ -d "/opt/gradle" ] && rm -rf /opt/gradle
    mv "/opt/gradle-${GRADLE_VER}" /opt/gradle
    ln -sf /opt/gradle/bin/gradle /usr/local/bin/gradle
    # Fix for caching issues (user reported /usr/bin/gradle not found)
    ln -sf /opt/gradle/bin/gradle /usr/bin/gradle
    echo "FluxLinux: Gradle $GRADLE_VER installed."
fi

# 4c. ARM64 Build Tools (aapt, aapt2, aidl, zipalign, dexdump)
# Install native ARM64 build-tools from:
# - SDK 35: lzhiyong/android-sdk-tools (v35.0.2)
# - SDK 36: HomuHomu833/android-sdk-custom (v36.1.0)
# This fixes SDK 35/36 resource compilation on ARM64 systems
echo "FluxLinux: Installing ARM64 Native Build Tools..."

# --- SDK 35 Build Tools (from lzhiyong) ---
ARM64_35_URL="https://github.com/lzhiyong/android-sdk-tools/releases/download/35.0.2/android-sdk-tools-static-aarch64.zip"
ARM64_35_ZIP="/tmp/android-sdk-35-aarch64.zip"
# Use unique temp directory with timestamp to avoid leftovers from previous runs
ARM64_35_DIR="/tmp/android-sdk-35-$$"

echo "FluxLinux: Installing SDK 35 ARM64 Build Tools (35.0.2)..."
if [ ! -f "$ARM64_35_ZIP" ]; then
    wget -q --show-progress "$ARM64_35_URL" -O "$ARM64_35_ZIP" || handle_error "ARM64 SDK 35 Build Tools Download"
fi

# Use fresh directory to avoid permission issues from previous runs
rm -rf "$ARM64_35_DIR" 2>/dev/null || true
mkdir -p "$ARM64_35_DIR"
unzip -o -q "$ARM64_35_ZIP" -d "$ARM64_35_DIR" || handle_error "ARM64 SDK 35 Build Tools Extract"

BUILD_TOOLS_35="$SDK_ROOT/build-tools/35.0.0"
mkdir -p "$BUILD_TOOLS_35"

for tool in aapt aapt2 aidl zipalign dexdump split-select; do
    if [ -f "$ARM64_35_DIR/build-tools/$tool" ]; then
        cp -f "$ARM64_35_DIR/build-tools/$tool" "$BUILD_TOOLS_35/$tool"
        chmod +x "$BUILD_TOOLS_35/$tool"
        echo " - SDK 35: Installed $tool (ARM64)"
    fi
done

# Verify SDK 35 aapt2
if file "$BUILD_TOOLS_35/aapt2" 2>/dev/null | grep -q "aarch64"; then
    echo " [✅] SDK 35 aapt2 is ARM64 native"
else
    echo " [⚠️] SDK 35 aapt2 verification skipped"
fi

# --- SDK 36 Build Tools (from HomuHomu833) ---
ARM64_36_URL="https://github.com/HomuHomu833/android-sdk-custom/releases/download/36.0.0/android-sdk-aarch64-linux-musl.tar.xz"
ARM64_36_TAR="/tmp/android-sdk-36-aarch64.tar.xz"
# Use unique temp directory with PID to avoid leftovers from previous runs
ARM64_36_DIR="/tmp/android-sdk-36-$$"

echo "FluxLinux: Installing SDK 36 ARM64 Build Tools (36.1.0)..."
if [ ! -f "$ARM64_36_TAR" ]; then
    wget -q --show-progress "$ARM64_36_URL" -O "$ARM64_36_TAR" || handle_error "ARM64 SDK 36 Build Tools Download"
fi

# Use fresh directory to avoid permission issues from previous runs
rm -rf "$ARM64_36_DIR" 2>/dev/null || true
mkdir -p "$ARM64_36_DIR"
tar -xf "$ARM64_36_TAR" -C "$ARM64_36_DIR" || handle_error "ARM64 SDK 36 Build Tools Extract"

BUILD_TOOLS_36="$SDK_ROOT/build-tools/36.0.0"
mkdir -p "$BUILD_TOOLS_36"

# SDK 36 has build-tools in android-sdk/build-tools/36.1.0/ subdirectory
SDK36_SRC="$ARM64_36_DIR/android-sdk/build-tools/36.1.0"
for tool in aapt aapt2 aidl zipalign dexdump split-select d8 apksigner; do
    if [ -f "$SDK36_SRC/$tool" ]; then
        cp -f "$SDK36_SRC/$tool" "$BUILD_TOOLS_36/$tool"
        chmod +x "$BUILD_TOOLS_36/$tool"
        echo " - SDK 36: Installed $tool (ARM64)"
    fi
done
# Copy lib directory and other files
[ -d "$SDK36_SRC/lib" ] && cp -rf "$SDK36_SRC/lib" "$BUILD_TOOLS_36/"
[ -f "$SDK36_SRC/core-lambda-stubs.jar" ] && cp -f "$SDK36_SRC/core-lambda-stubs.jar" "$BUILD_TOOLS_36/"

# Also install latest aapt2 (SDK 36) to /usr/local/bin for global access
cp -f "$SDK36_SRC/aapt2" /usr/local/bin/aapt2
chmod +x /usr/local/bin/aapt2

# Configure Gradle to use this override globally
GRADLE_USER_HOME="/home/$TARGET_USER/.gradle"
mkdir -p "$GRADLE_USER_HOME"

# Update or add aapt2 override in gradle.properties
if grep -q "android.aapt2FromMavenOverride" "$GRADLE_USER_HOME/gradle.properties" 2>/dev/null; then
    sed -i "s|android.aapt2FromMavenOverride=.*|android.aapt2FromMavenOverride=/usr/local/bin/aapt2|" "$GRADLE_USER_HOME/gradle.properties"
else
    echo "android.aapt2FromMavenOverride=/usr/local/bin/aapt2" >> "$GRADLE_USER_HOME/gradle.properties"
fi

# Permission fix for ~/.gradle
chown -R $TARGET_USER:$TARGET_GROUP "$GRADLE_USER_HOME"

# Verify ARM64 aapt2
echo "FluxLinux: Verifying ARM64 aapt2..."
# Try 'file' command first, fallback to readelf
if command -v file >/dev/null 2>&1; then
    if file /usr/local/bin/aapt2 | grep -q "aarch64"; then
        echo " [✅] aapt2 (SDK 36) is ARM64 native"
    else
        echo " [⚠️] aapt2 architecture verification failed"
    fi
elif command -v readelf >/dev/null 2>&1; then
    if readelf -h /usr/local/bin/aapt2 2>/dev/null | grep -q "AArch64"; then
        echo " [✅] aapt2 (SDK 36) is ARM64 native (verified via readelf)"
    else
        echo " [⚠️] aapt2 architecture could not be verified"
    fi
else
    echo " [✅] aapt2 installed (architecture verification skipped - install 'file' package to enable)"
fi



# 6. React Native Setup (Environment)
# Node is installed via webdev script. We just ensure env vars are ready.
echo "FluxLinux: Configuring Environment (React Native/Android)..."

# Update .bashrc
BASHRC="$HOME/.bashrc"
if ! grep -q "ANDROID_HOME" "$BASHRC"; then
    cat <<EOF >> "$BASHRC"

# FluxLinux App Dev Config
# Dynamic Java Home
if [ -d "/usr/lib/jvm/java-21-openjdk-arm64" ]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
elif [ -d "/usr/lib/jvm/java-17-openjdk-arm64" ]; then
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
else
    export JAVA_HOME=/usr/lib/jvm/default-java
fi

# Fix libjli.so error
LIBJLI_PATH=\$(find /usr/lib/jvm -name "libjli.so" | head -1)
if [ -n "\$LIBJLI_PATH" ]; then
    export LD_LIBRARY_PATH=\$(dirname "\$LIBJLI_PATH"):\$LD_LIBRARY_PATH
fi

export ANDROID_HOME=$SDK_ROOT
export ANDROID_NDK=$SDK_ROOT/ndk/29.0.14206865
export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=\$PATH:\$ANDROID_HOME/platform-tools
export PATH=\$PATH:$FLUTTER_ROOT/bin
export PATH=\$PATH:\$ANDROID_HOME/tools
export PATH=\$PATH:\$ANDROID_HOME/tools/bin
export PATH=\$PATH:/opt/gradle/bin

# React Native
export REACT_NATIVE_PACKAGER_HOSTNAME=localhost

# Flutter Web
export CHROME_EXECUTABLE=/usr/bin/chromium
EOF
fi

# Ensure /dev/shm is sufficient for Gradle (Should be handled by chroot setup)
# (Done in previous step with 512M update)

# 7. Global Symlinks for Immediate Usage
echo "FluxLinux: Creating global symlinks..."

ln -sf "$FLUTTER_ROOT/bin/flutter" /usr/local/bin/flutter
ln -sf "$FLUTTER_ROOT/bin/dart" /usr/local/bin/dart

# Kotlin
ln -sf "$KOTLIN_ROOT/bin/kotlin" /usr/local/bin/kotlin
ln -sf "$KOTLIN_ROOT/bin/kotlinc" /usr/local/bin/kotlinc

# 8. Final Permission Fix (Crucial for non-root usage)
echo "FluxLinux: Ensuring correct permissions for $TARGET_USER..."
chown -R $TARGET_USER:$TARGET_GROUP "$SDK_ROOT" "$FLUTTER_ROOT" "$KOTLIN_ROOT" "/opt/gradle"
chmod -R 775 "$SDK_ROOT" "$FLUTTER_ROOT" "$KOTLIN_ROOT" "/opt/gradle"

echo "FluxLinux: App Development Setup Complete!"

# Final Verification
verify_installation() {
    echo ""
    echo "🔎 FluxLinux: Verifying Installations..."
    echo "------------------------------------------------"
    MISSING=0
    
    # Check Java
    if java -version >/dev/null 2>&1; then echo " [✅] JDK Installed"; else echo " [❌] JDK Missing"; MISSING=1; fi
    
    # Check Android SDK
    if [ -f "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then echo " [✅] Android SDK Tools"; else echo " [❌] Android SDK Tools Missing"; MISSING=1; fi
    
    # Check ADB (apt)
    if command -v adb >/dev/null; then echo " [✅] ADB (Native)"; else echo " [❌] ADB Missing"; MISSING=1; fi
    
    # Check Flutter
    if [ -f "$FLUTTER_ROOT/bin/flutter" ]; then echo " [✅] Flutter SDK"; else echo " [❌] Flutter SDK Missing"; MISSING=1; fi

    # Check Gradle (Native)
    if command -v gradle >/dev/null; then 
        echo " [✅] Gradle: $(gradle -v | grep 'Gradle' | head -n 1)"
    else 
        if [ -f "/opt/gradle/bin/gradle" ]; then
            echo " [✅] Gradle: Path OK (/opt/gradle/bin/gradle)"
            ls -l /usr/bin/gradle
            echo " [⚠️] Try running 'hash -r' to reload paths."
        else
            echo " [❌] Gradle Missing (/opt/gradle not found)"
            MISSING=1
        fi
    fi
    
    # Check CMake
    if command -v cmake >/dev/null; then echo " [✅] CMake (Native)"; else echo " [❌] CMake Missing"; MISSING=1; fi
    
    # Check Kotlin
    if [ -f "$KOTLIN_ROOT/bin/kotlinc" ]; then echo " [✅] Kotlin Compiler"; else echo " [❌] Kotlin Missing"; MISSING=1; fi


    
    echo "------------------------------------------------"
    if [ $MISSING -eq 1 ]; then
        echo "⚠️  Some components failed to install."
    else
        echo "🎉 All components installed successfully!"
    fi
}

verify_installation

echo "------------------------------------------------"
echo "Stack Installed:"
echo " - JDK 21/17/Default"
echo " - Android SDK 34/35/36 (ARM64 Build Tools)"
echo " - Flutter (Stable)"
echo " - Kotlin"

echo "------------------------------------------------"
echo "Note: Restart your terminal/session for PATH changes to take effect."
read -p "Press Enter to close..."
