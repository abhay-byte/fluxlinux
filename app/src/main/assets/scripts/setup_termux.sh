#!/bin/bash
# setup_termux.sh
# Core initialization script for FluxLinux
# Installs necessary dependencies in Termux

echo "FluxLinux: Initializing Termux Environment..."

# 1. Update Packages
pkg update -y && pkg upgrade -y

# 2. Install Core Dependencies
# proot-distro: For rootless containers
# x11-repo: For graphical support
# pulseaudio: For sound
# wget: For downloading scripts
# zsh: Enhanced shell (for Oh My Zsh)
# fastfetch: System info display
# git: Version control (for Oh My Zsh plugins)
# unzip: For extracting fonts
pkg install -y proot-distro x11-repo pulseaudio wget zsh fastfetch git unzip

# 3. Install Termux:X11
pkg install -y termux-x11-nightly

# 4. Create GUI Launch Script (Based on LinuxDroidMaster Reference)
cat <<'EOF' > $HOME/start_gui.sh
#!/data/data/com.termux/files/usr/bin/bash

# Kill open X11 processes
kill -9 $(pgrep -f "termux.x11") 2>/dev/null

# Enable PulseAudio over Network
pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1

# Prepare termux-x11 session
export XDG_RUNTIME_DIR=${TMPDIR}
termux-x11 :0 >/dev/null &

# Wait a bit until termux-x11 gets started
sleep 3

# Launch Termux X11 main activity (CRITICAL for display)
am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity > /dev/null 2>&1
sleep 1

# Login in PRoot Environment with proper environment setup
# Usage: ./start_gui.sh <distro_alias>
DISTRO=${1:-debian}
echo "Launching GUI for $DISTRO..."

proot-distro login $DISTRO --shared-tmp -- /bin/bash -c 'export PULSE_SERVER=127.0.0.1 && export XDG_RUNTIME_DIR=${TMPDIR} && su - flux -c "env DISPLAY=:0 startxfce4"'

exit 0
EOF

chmod +x $HOME/start_gui.sh

# 5. Create Install Helper Script (Manual/Intent Friendly)
cat <<'EOF' > $HOME/flux_install.sh
#!/bin/bash
DISTRO=$1
SETUP_B64=$2

# Load full Termux environment
source /data/data/com.termux/files/usr/etc/profile

echo "FluxLinux: Installing $DISTRO..."

# Use reset to handle both fresh install and reinstall cases
# This automatically removes existing installation if present
proot-distro reset $DISTRO 2>/dev/null || proot-distro install $DISTRO

EXIT_CODE=$?
if [ $EXIT_CODE -eq 0 ]; then
    echo "FluxLinux: Install Successful!"
    if [ ! -z "$SETUP_B64" ] && [ "$SETUP_B64" != "null" ]; then
        echo "FluxLinux: Configuring..."
        # Decode setup script
        echo "$SETUP_B64" | base64 -d > $HOME/flux_setup_temp.sh
        chmod +x $HOME/flux_setup_temp.sh
        
        # Move it to a shared location readable by proot
        proot-distro login $DISTRO --shared-tmp -- bash -c "bash /data/data/com.termux/files/home/flux_setup_temp.sh"
        
        rm $HOME/flux_setup_temp.sh
        echo "FluxLinux: Configuration Complete!"
    fi
    
    # Return to FluxLinux app
    echo "Returning to FluxLinux..."
    am start -n com.fluxlinux.app/.MainActivity
else
    echo "FluxLinux: Install Failed with code $EXIT_CODE!"
    exit 1
fi
EOF
chmod +x $HOME/flux_install.sh

echo "FluxLinux: Setup Complete"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎨 Optional: Enhance your Termux experience?"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "This will install:"
echo "  • Oh My Zsh with plugins (autosuggestions, syntax-highlighting)"
echo "  • Custom color schemes (GitHub Dark, Dracula, Gruvbox)"
echo "  • Nerd Fonts (Meslo, FiraCode, JetBrainsMono)"
echo "  • Fastfetch system info display"
echo ""
printf "Apply Termux tweaks? [y/N]: "
read -r apply_tweaks

if [ "$apply_tweaks" = "y" ] || [ "$apply_tweaks" = "Y" ]; then
    echo ""
    echo "🚀 Launching Termux Tweaks..."
    # Copy the tweaks script from app to Termux if available
    # Otherwise download from GitHub
    if [ -f "/data/data/com.termux/files/home/termux_tweaks.sh" ]; then
        bash /data/data/com.termux/files/home/termux_tweaks.sh
    else
        echo "📥 Downloading termux_tweaks.sh..."
        curl -fsSL https://raw.githubusercontent.com/abhay-byte/fluxlinux/main/app/src/main/assets/scripts/termux_tweaks.sh -o $HOME/termux_tweaks.sh
        chmod +x $HOME/termux_tweaks.sh
        bash $HOME/termux_tweaks.sh
    fi
else
    echo ""
    echo "⏭️  Skipping tweaks. You can run them later with:"
    echo "   bash ~/termux_tweaks.sh"
fi

echo ""
echo "✅ FluxLinux initialization complete!"
