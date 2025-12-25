#!/data/data/com.termux/files/usr/bin/bash
# start_gui.sh
# Launch XFCE4 Desktop Environment in PRoot Distro
# Based on LinuxDroidMaster reference implementation

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
