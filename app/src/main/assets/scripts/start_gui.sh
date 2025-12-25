#!/data/data/com.termux/files/usr/bin/bash
# start_gui.sh
# Launch XFCE4 Desktop Environment in PRoot Distro
# Based on LinuxDroidMaster reference implementation

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
sleep 2

# Apply stored X11 preferences
# Using direct restore method: termux-x11-preference < file
if [ -f "$HOME/.fluxlinux/x11_preferences.list" ]; then
    echo "Applying X11 Preferences..."
    termux-x11-preference < "$HOME/.fluxlinux/x11_preferences.list"
fi

# Login in PRoot Environment with proper environment setup
# Usage: ./start_gui.sh <distro_alias>
DISTRO=${1:-debian}
echo "Launching GUI for $DISTRO..."

proot-distro login $DISTRO --shared-tmp -- /bin/bash -c 'export PULSE_SERVER=127.0.0.1 && export XDG_RUNTIME_DIR=${TMPDIR} && su - flux -c "env DISPLAY=:0 startxfce4"'

exit 0
