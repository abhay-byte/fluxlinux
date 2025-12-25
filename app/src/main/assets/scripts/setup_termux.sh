#!/bin/bash
# setup_termux.sh
# Core initialization script for FluxLinux
# Installs necessary dependencies in Termux

MARKER_FILE="$HOME/.fluxlinux/setup_termux.done"
mkdir -p "$HOME/.fluxlinux"

if [ -f "$MARKER_FILE" ]; then
    echo "FluxLinux: Termux Setup already completed. Skipping."
    am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=setup_termux"
    exit 0
fi

# Trap errors
set -e
trap 'am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=failure&name=setup_termux"' ERR

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
pkill -f com.termux.x11 2>/dev/null

# Kill previous XFCE sessions to prevent zombies/conflicts
pkill -f startxfce4 2>/dev/null
pkill -f xfce4-session 2>/dev/null

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

# Apply stored X11 preferences if they exist
if [ -f "$HOME/.fluxlinux/x11_preferences.sh" ]; then
    echo "Applying X11 Preferences..."
    bash "$HOME/.fluxlinux/x11_preferences.sh"
fi

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
    am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=distro_install_${DISTRO}"
else
    echo "FluxLinux: Install Failed with code $EXIT_CODE!"
    am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=failure&name=distro_install_${DISTRO}"
    exit 1
fi
EOF
chmod +x $HOME/flux_install.sh

echo "FluxLinux: Setup Complete"
echo ""
echo "📝 Optional: Run 'bash ~/termux_tweaks.sh' for enhanced terminal experience"

# Create marker file to track initialization
touch "$MARKER_FILE"
am start -a android.intent.action.VIEW -d "fluxlinux://callback?result=success&name=setup_termux"
