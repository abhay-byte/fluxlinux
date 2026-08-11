#!/data/data/com.termux/files/usr/bin/bash
# start_gui_kde.sh - Launch KDE Plasma Desktop Environment in PRoot Distro

DISTRO=${1:-debian}

# Kill any open X11 processes
kill -9 $(pgrep -f "termux.x11") 2>/dev/null
sleep 1

# Enable PulseAudio over Network
pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1

# Fix XDG_RUNTIME_DIR: Qt/KDE reject /tmp (world-writable 0777)
FLUX_RUNTIME_DIR="${TMPDIR:-/data/data/com.termux/files/usr/tmp}/runtime-flux-kde"
mkdir -p "$FLUX_RUNTIME_DIR"
chmod 700 "$FLUX_RUNTIME_DIR"
export XDG_RUNTIME_DIR="$FLUX_RUNTIME_DIR"

# Prepare embedded termux-x11 session (X11 server runs in-process with the host app).
# The host app launches the X11 activity; here we spawn the X server entry point
# from the app's own APK (TERMUX_X11_APK_PATH is set by the app's host env).
if [ -n "$TERMUX_X11_APK_PATH" ] && [ -f "$TERMUX_X11_APK_PATH" ]; then
    CLASSPATH="$TERMUX_X11_APK_PATH" app_process / com.termux.x11.CmdEntryPoint :0 -legacy-drawing >/dev/null 2>&1 &
    echo "FluxLinux: Embedded X11 server started from app APK"
else
    termux-x11 :0 >/dev/null &
fi

# Wait until the X11 session gets started
sleep 3

# Login to PRoot and start KDE Plasma
proot-distro login $DISTRO --shared-tmp -- /bin/bash -c '
  mkdir -p /tmp/runtime-flux-kde
  chmod 700 /tmp/runtime-flux-kde
  export DISPLAY=:0
  export PULSE_SERVER=tcp:127.0.0.1
  export XDG_RUNTIME_DIR=/tmp/runtime-flux-kde
  export GALLIUM_DRIVER=zink
  export MESA_LOADER_DRIVER_OVERRIDE=zink
  export TU_DEBUG=noconform
  export ZINK_NO_TIMELINES=1
  export KWIN_OPENGL_INTERFACE=egl
  export KWIN_COMPOSE=N
  su - flux -c "
    export DISPLAY=:0
    export PULSE_SERVER=tcp:127.0.0.1
    export XDG_RUNTIME_DIR=/tmp/runtime-flux-kde
    export GALLIUM_DRIVER=zink
    export MESA_LOADER_DRIVER_OVERRIDE=zink
    export TU_DEBUG=noconform
    export ZINK_NO_TIMELINES=1
    export KWIN_OPENGL_INTERFACE=egl
    export KWIN_COMPOSE=N
    export QT_QPA_PLATFORMTHEME=kde
    export QT_SCALE_FACTOR=1
    dbus-run-session -- startplasma-x11
  "
'

exit 0
