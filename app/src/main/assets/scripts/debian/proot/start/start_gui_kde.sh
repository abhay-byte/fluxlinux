#!/data/data/com.termux/files/usr/bin/bash
# start_gui_kde.sh - Launch KDE Plasma Desktop Environment in PRoot Distro

DISTRO=${1:-debian}

# Kill any open X11 processes
kill -9 $(pgrep -f "termux.x11") 2>/dev/null
sleep 1

# Host Pulse (app uid). Not a guest daemon.
if [ -x "${TERMUX__HOME:-$HOME}/start_pulse_host.sh" ]; then
  bash "${TERMUX__HOME:-$HOME}/start_pulse_host.sh" || true
fi

# Fix XDG_RUNTIME_DIR: Qt/KDE reject /tmp (world-writable 0777)
FLUX_RUNTIME_DIR="${TMPDIR:-/data/data/com.termux/files/usr/tmp}/runtime-flux-kde"
mkdir -p "$FLUX_RUNTIME_DIR"
chmod 700 "$FLUX_RUNTIME_DIR"
export XDG_RUNTIME_DIR="$FLUX_RUNTIME_DIR"

# X11 is started in-process by DesktopLauncher through the compiled module.
if [ "${FLUX_EMBEDDED_X11:-0}" != "1" ]; then
    echo "FluxLinux: embedded X11 server was not started by the app" >&2
    exit 1
fi
echo "FluxLinux: Embedded X11 server is owned by the FluxLinux app process"

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
