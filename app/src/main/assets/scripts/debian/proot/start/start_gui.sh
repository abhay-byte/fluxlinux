#!/data/data/com.ivarna.fluxlinux/files/usr/bin/bash
# start_gui.sh - Launch XFCE4 Desktop Environment in PRoot Distro
# Paths: TermuxHostPaths via fluxlinux-host.env (SSOT)

DISTRO=${1:-debian}
PKG="${TERMUX_APP__PACKAGE_NAME:-com.ivarna.fluxlinux}"
_HOST_ENV="${TERMUX__PREFIX:-/data/data/${PKG}/files/usr}/etc/fluxlinux-host.env"
[ -r "$_HOST_ENV" ] && . "$_HOST_ENV"

# Detect how we're running
IS_ROOT=false
if [ "$(id -u)" = "0" ]; then IS_ROOT=true; fi

# Termux paths (from SSOT env, with safe fallbacks)
PKG="${TERMUX_APP__PACKAGE_NAME:-$PKG}"
TERMUX_PREFIX="${TERMUX__PREFIX:-/data/data/$PKG/files/usr}"
TERMUX_HOME="${TERMUX__HOME:-/data/data/$PKG/files/home}"
export HOME="$TERMUX_HOME"
export TMPDIR="${TMPDIR:-$TERMUX_PREFIX/tmp}"
export PROOT_TMP_DIR="${PROOT_TMP_DIR:-$TMPDIR}"
export PATH="$TERMUX_PREFIX/bin:$TERMUX_PREFIX/bin/applets:/system/bin:/system/xbin:$PATH"
export LD_LIBRARY_PATH="$TERMUX_PREFIX/lib:$TERMUX_PREFIX/opt/virglrenderer-android/lib"
export TERMUX_APP__PACKAGE_NAME="$PKG"
export TERMUX_X11_OVERRIDE_PACKAGE="$PKG"
export TERMUX__PREFIX="$TERMUX_PREFIX"
export TERMUX__HOME="$TERMUX_HOME"
export XKB_CONFIG_ROOT="$TERMUX_PREFIX/share/X11/xkb"

# Configure PulseAudio (use home to avoid root-owned stale tmp dirs)
export PULSE_RUNTIME_PATH="${HOME}/.pulse"
mkdir -p "$PULSE_RUNTIME_PATH" 2>/dev/null

# Kill stale host graphics services only — NEVER am force-stop own package
# (that would kill FluxLinux itself if the package name matches).
pkill -f "virgl_test_server" 2>/dev/null || true
pkill -f pulseaudio 2>/dev/null || true
pkill -f termux-x11 2>/dev/null || true
pkill -f "app_process.*termux-x11" 2>/dev/null || true
sleep 2

# Start PulseAudio over TCP
if $IS_ROOT; then
  pulseaudio --system --start --dl-search-path="$TERMUX_PREFIX/lib/pulseaudio/modules" \
    --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" \
    --exit-idle-time=-1 2>/dev/null || \
  pulseaudio --start --dl-search-path="$TERMUX_PREFIX/lib/pulseaudio/modules" \
    --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" \
    --exit-idle-time=-1 2>/dev/null
else
  pulseaudio --start --dl-search-path="$TERMUX_PREFIX/lib/pulseaudio/modules" \
    --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" \
    --exit-idle-time=-1 2>/dev/null
fi

# VirGL is optional. Custom package builds currently omit it; XFCE runs
# with software rendering until a compatible GPU bundle is available.
if command -v virgl_test_server_android >/dev/null; then
  echo "FluxLinux: Starting VirGL server..."
  virgl_test_server_android --socket-path "$TERMUX_PREFIX/tmp/.virgl_test" >/dev/null 2>&1 &
  sleep 2
  test -S "${TMPDIR}/.virgl_test" && echo "FluxLinux: VirGL socket ready" || \
    echo "FluxLinux: [WARN] VirGL socket not found"
else
  echo "FluxLinux: VirGL unavailable; using software rendering"
fi

# Start X server via app_process (needs system context)
echo "FluxLinux: Starting termux-x11 server..."
export XDG_RUNTIME_DIR="$TMPDIR"
export DISPLAY=:0

# Fix Android 14 SecurityException for writable dex files (loader.apk must be read-only)
chmod 0400 "$TERMUX_PREFIX/libexec/termux-x11/loader.apk" 2>/dev/null || true
chmod 0500 "$TERMUX_PREFIX/libexec/termux-x11" 2>/dev/null || true

# Fix broken xkb symlink if pointing to old com.termux prefix
if [ -L "$TERMUX_PREFIX/share/X11/xkb" ] && [ ! -e "$TERMUX_PREFIX/share/X11/xkb" ]; then
  rm -f "$TERMUX_PREFIX/share/X11/xkb"
  ln -s "$TERMUX_PREFIX/share/xkeyboard-config-2" "$TERMUX_PREFIX/share/X11/xkb"
fi

# Resolve our installed APK path so Loader can dlopen CmdEntryPoint classes
# tr -d '\r' strips Windows carriage returns from pm path output
if [ -z "$TERMUX_X11_APK_PATH" ]; then
  TERMUX_X11_APK_PATH=$(pm path "$PKG" 2>/dev/null | tr -d '\r' | sed 's/^package://')
fi
if [ -z "$TERMUX_X11_APK_PATH" ] || [ ! -f "$TERMUX_X11_APK_PATH" ]; then
  TERMUX_X11_APK_PATH=$(find /data/app -name "base.apk" -path "*$PKG*" 2>/dev/null | head -1)
fi
export TERMUX_X11_APK_PATH
echo "FluxLinux: APK path = $TERMUX_X11_APK_PATH"

# Extract libXlorie.so from our APK to /data/data app lib dir
# This is the only location app_process can dlopen without corrupting its linker namespace
APP_LIB_DIR="/data/data/$PKG/lib"
mkdir -p "$APP_LIB_DIR" 2>/dev/null
if [ ! -f "$APP_LIB_DIR/libXlorie.so" ] && [ -n "$TERMUX_X11_APK_PATH" ]; then
  echo "FluxLinux: Extracting libXlorie.so from APK..."
  # Try arm64 first, then armeabi-v7a
  ( cd "$APP_LIB_DIR" && \
    unzip -o "$TERMUX_X11_APK_PATH" 'lib/arm64-v8a/libXlorie.so' 2>/dev/null && \
    mv -f lib/arm64-v8a/libXlorie.so . && rm -rf lib ) || \
  ( cd "$APP_LIB_DIR" && \
    unzip -o "$TERMUX_X11_APK_PATH" 'lib/armeabi-v7a/libXlorie.so' 2>/dev/null && \
    mv -f lib/armeabi-v7a/libXlorie.so . && rm -rf lib )
  ls -la "$APP_LIB_DIR/libXlorie.so" 2>/dev/null && \
    echo "FluxLinux: libXlorie.so ready in $APP_LIB_DIR" || \
    echo "FluxLinux: [WARN] libXlorie.so extraction failed"
fi

# Launch the X server via app_process
# CLEAR LD_LIBRARY_PATH — setting it to Termux libs breaks system linker (libunwindstack.so)
# CmdEntryPoint finds libXlorie.so via ClassLoader resource lookup inside our APK
LD_LIBRARY_PATH="" LD_PRELOAD="" \
CLASSPATH="$TERMUX_PREFIX/libexec/termux-x11/loader.apk" \
TERMUX_X11_APK_PATH="$TERMUX_X11_APK_PATH" \
TERMUX_X11_OVERRIDE_PACKAGE="$PKG" \
LANG=en_US.UTF-8 \
/system/bin/app_process / \
  --nice-name="termux-x11" com.termux.x11.Loader :0 -legacy-drawing &
XSERVER_PID=$!
echo "FluxLinux: X server PID=$XSERVER_PID"
sleep 3

# Open X11 display activity in our app
echo "FluxLinux: Launching X11 display activity..."
am start -n "$PKG/com.termux.x11.MainActivity" \
  --activity-single-top \
  --activity-clear-top 2>/dev/null || \
am start -n "$PKG/com.termux.x11.MainActivity" 2>/dev/null
sleep 1

# Verify guest setup
ROOTFS="$TERMUX_PREFIX/var/lib/proot-distro/containers/$DISTRO/rootfs"
if [ ! -f "$ROOTFS/usr/bin/startxfce4" ]; then
  echo "FluxLinux: XFCE setup incomplete. Re-run environment setup."
  exit 1
fi

echo "FluxLinux: startxfce4=READY"

# Guest GPU mode from setup_hw_accel_debian.sh (/etc/fluxlinux/gpu_mode)
# turnip → Adreno/Zink; virgl → host virgl_test_server; else softpipe
GUEST_ROOTFS="$TERMUX_PREFIX/var/lib/proot-distro/containers/$DISTRO/rootfs"
GPU_MODE="virgl"
if [ -r "$GUEST_ROOTFS/etc/fluxlinux/gpu_mode" ]; then
  GPU_MODE=$(tr -d '[:space:]' <"$GUEST_ROOTFS/etc/fluxlinux/gpu_mode")
fi
# sanitize for embedding in guest shell
case "$GPU_MODE" in
  turnip|virgl) ;;
  *) GPU_MODE=virgl ;;
esac
echo "FluxLinux: Guest GPU mode=$GPU_MODE"

# Launch XFCE in proot — propagate guest exit code (never force exit 0)
GUEST_RC=0
if [ "$DISTRO" = "termux" ]; then
  export PULSE_SERVER=127.0.0.1
  env DISPLAY=:0 startxfce4
  GUEST_RC=$?
else
  # Single guest script: read mode file inside rootfs (no host quote hell)
  python "$TERMUX_PREFIX/bin/proot-distro" login "$DISTRO" --shared-tmp -- /bin/bash -c '
    export DISPLAY=:0
    export PULSE_SERVER=tcp:127.0.0.1
    export XDG_RUNTIME_DIR=/tmp
    export VTEST_SOCKET_NAME=/tmp/.virgl_test

    GPU_MODE=virgl
    if [ -r /etc/fluxlinux/gpu_mode ]; then
      GPU_MODE=$(tr -d "[:space:]" </etc/fluxlinux/gpu_mode)
    fi
    case "$GPU_MODE" in turnip|virgl) ;; *) GPU_MODE=virgl ;; esac
    echo "FluxLinux(guest): GPU mode=$GPU_MODE"

    apply_gpu_env() {
      unset GALLIUM_DRIVER MESA_LOADER_DRIVER_OVERRIDE VK_ICD_FILENAMES
      unset LIBGL_ALWAYS_SOFTWARE TU_DEBUG MESA_VK_WSI_DEBUG
      case "$GPU_MODE" in
        turnip)
          export MESA_LOADER_DRIVER_OVERRIDE=zink
          export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json
          export TU_DEBUG=noconform
          export MESA_VK_WSI_DEBUG=sw
          export MESA_GL_VERSION_OVERRIDE=4.6
          export MESA_GLES_VERSION_OVERRIDE=3.2
          ;;
        virgl)
          if [ -S /tmp/.virgl_test ]; then
            export GALLIUM_DRIVER=virpipe
          else
            export LIBGL_ALWAYS_SOFTWARE=1
            export GALLIUM_DRIVER=llvmpipe
            echo "FluxLinux(guest): VirGL socket missing — llvmpipe fallback"
          fi
          ;;
        *)
          export LIBGL_ALWAYS_SOFTWARE=1
          export GALLIUM_DRIVER=llvmpipe
          ;;
      esac
    }

    apply_gpu_env
    # Preserve exported GPU vars across su - (login shell clears some env)
    export GPU_MODE
    su - flux -c "
      export DISPLAY=:0
      export PULSE_SERVER=tcp:127.0.0.1
      export XDG_RUNTIME_DIR=/tmp
      export VTEST_SOCKET_NAME=/tmp/.virgl_test
      export GPU_MODE=$GPU_MODE
      if [ \"\$GPU_MODE\" = turnip ]; then
        export MESA_LOADER_DRIVER_OVERRIDE=zink
        export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json
        export TU_DEBUG=noconform
        export MESA_VK_WSI_DEBUG=sw
        export MESA_GL_VERSION_OVERRIDE=4.6
        export MESA_GLES_VERSION_OVERRIDE=3.2
      elif [ \"\$GPU_MODE\" = virgl ] && [ -S /tmp/.virgl_test ]; then
        export GALLIUM_DRIVER=virpipe
      else
        export LIBGL_ALWAYS_SOFTWARE=1
        export GALLIUM_DRIVER=llvmpipe
      fi
      xfconf-query -c xfwm4 -p /general/use_compositing -s false 2>/dev/null
      exec dbus-launch --exit-with-session startxfce4
    "
  '
  GUEST_RC=$?
fi

echo "FluxLinux: XFCE session ended (exit $GUEST_RC)"
exit $GUEST_RC
