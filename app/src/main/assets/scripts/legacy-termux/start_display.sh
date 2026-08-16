#!/data/data/com.termux/files/usr/bin/bash
# start_gui.sh - Launch XFCE4 Desktop Environment in PRoot Distro
# Based on LinuxDroidMaster: https://github.com/LinuxDroidMaster/Termux-Desktops

DISTRO=${1:-debian}

# Kill open X11 processes
kill -9 $(pgrep -f "termux.x11") 2>/dev/null

# Kill any stale VirGL server
pkill -f "virgl_test_server" 2>/dev/null
sleep 1

# Enable PulseAudio over Network
pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1

# Start VirGL server (required for GPU acceleration inside PRoot)
echo "FluxLinux: Starting VirGL server..."
virgl_test_server_android &
VIRGL_PID=$!
sleep 2

# Verify VirGL socket appeared (--shared-tmp exposes Termux $TMPDIR as /tmp inside proot)
if [ -S "${TMPDIR}/.virgl_test" ]; then
    echo "FluxLinux: VirGL socket ready at /tmp/.virgl_test"
else
    echo "FluxLinux: [WARN] VirGL socket not found — GPU acceleration may not work"
fi

# Prepare termux-x11 session
export XDG_RUNTIME_DIR=${TMPDIR}
termux-x11 :0 >/dev/null &

# Wait a bit until termux-x11 gets started.
sleep 3

# Launch Termux X11 main activity
am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity > /dev/null 2>&1
sleep 1

# Login in PRoot Environment
if [ "$DISTRO" == "termux" ]; then
    export PULSE_SERVER=127.0.0.1
    env DISPLAY=:0 startxfce4
else
    proot-distro login $DISTRO --shared-tmp -- /bin/bash -c '
      export DISPLAY=:0
      export PULSE_SERVER=tcp:127.0.0.1
      export XDG_RUNTIME_DIR=/tmp
      export VTEST_SOCKET_NAME=/tmp/.virgl_test
      su - flux -c "
        export DISPLAY=:0
        export PULSE_SERVER=tcp:127.0.0.1
        export XDG_RUNTIME_DIR=/tmp
        export VTEST_SOCKET_NAME=/tmp/.virgl_test
        # Disable compositor to fix black screen with Turnip GPU driver
        xfconf-query -c xfwm4 -p /general/use_compositing -s false 2>/dev/null
        dbus-launch --exit-with-session startxfce4
      "
    '
fi

exit 0
