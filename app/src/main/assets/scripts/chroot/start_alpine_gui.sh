#!/system/bin/sh
# start_alpine_gui.sh — root: mount Alpine chroot + launch XFCE4
# Called by start_gui_chroot.sh after host Pulse/VirGL/X11 are up.
# Paths: app package via FLUX_PACKAGE / TARGET_PREFIX (ivarna or zenithblue). Sticky guest /tmp preserved.

CHROOT_ROOT="${CHROOT_ROOT:-/data/local/tmp/chrootAlpine}"
if [ -z "${TARGET_PREFIX:-}" ]; then
  if [ -n "${FLUX_PREFIX:-}" ]; then
    TARGET_PREFIX="$FLUX_PREFIX"
  elif [ -n "${FLUX_PACKAGE:-}" ]; then
    TARGET_PREFIX="/data/data/${FLUX_PACKAGE}/files/usr"
  elif [ -d /data/data/com.zenithblue.fluxlinux/files/usr ]; then
    TARGET_PREFIX="/data/data/com.zenithblue.fluxlinux/files/usr"
  else
    TARGET_PREFIX="/data/data/com.ivarna.fluxlinux/files/usr"
  fi
fi
USERNAME="${USERNAME:-flux}"

echo "========================================"
echo "FluxLinux: Chroot XFCE (root stage)"
echo "  rootfs=$CHROOT_ROOT"
echo "========================================"

# Busybox: Magisk/system only (skip Termux/app busybox for chroot mounts)
BB=""
if command -v busybox >/dev/null 2>&1; then
  DETECTED_BB=$(command -v busybox)
  case "$DETECTED_BB" in
    *"com.termux"*|*"fluxlinux"*|*"nativecode"*) ;;
    *) BB="$DETECTED_BB" ;;
  esac
fi
if [ -z "$BB" ]; then
  for path in /data/adb/magisk/busybox /data/adb/modules/busybox-ndk/system/bin/busybox \
    /sbin/busybox /system/xbin/busybox /system/bin/busybox /debug_ramdisk/busybox; do
    if [ -x "$path" ]; then BB="$path"; break; fi
  done
fi
if [ -z "$BB" ]; then
  echo "FluxLinux: ERROR — root-capable busybox not found"
  exit 1
fi
echo "FluxLinux: busybox=$BB"

if [ ! -d "$CHROOT_ROOT" ]; then
  echo "FluxLinux: ERROR — chroot missing: $CHROOT_ROOT"
  exit 1
fi
if [ ! -x "$CHROOT_ROOT/usr/bin/startxfce4" ] && [ ! -f "$CHROOT_ROOT/usr/bin/startxfce4" ]; then
  echo "FluxLinux: ERROR — startxfce4 missing. Re-run chroot environment setup."
  exit 1
fi

# Soft SELinux (HyperOS / enforcing) — flux pattern; fail soft
if command -v getenforce >/dev/null 2>&1; then
  SELINUX_STATUS=$(getenforce 2>/dev/null || true)
  echo "FluxLinux: SELinux=$SELINUX_STATUS"
  if [ "$SELINUX_STATUS" = "Enforcing" ]; then
    setenforce 0 2>/dev/null && echo "FluxLinux: SELinux → Permissive (until reboot)" \
      || echo "FluxLinux: [WARN] setenforce 0 failed"
  fi
fi
# PREFIX/tmp must stay app_data_file. Labeling it tmpfs:s0 makes termux-x11
# fail to create .X11-unix / .tX0-lock / dbus sockets when SELinux is enforcing.
if command -v chcon >/dev/null 2>&1; then
  _ctx=$(ls -Zd "$TARGET_PREFIX" 2>/dev/null | awk '{print $1}')
  if [ -n "$_ctx" ]; then
    chcon -R "$_ctx" "$TARGET_PREFIX/tmp" 2>/dev/null || true
  fi
fi
restorecon -RF "$TARGET_PREFIX/tmp" 2>/dev/null || true

HELPER="${HELPER:-/data/local/tmp/fluxlinux_chroot.sh}"
echo "[1/5] Mounts (SSOT if available)..."
# Wait for host Loader to create X0 before --x11 bind
mkdir -p "$TARGET_PREFIX/tmp/.X11-unix" 2>/dev/null || true
chmod 1777 "$TARGET_PREFIX/tmp/.X11-unix" 2>/dev/null || true
i=0
while [ $i -lt 15 ]; do
  if [ -S "$TARGET_PREFIX/tmp/.X11-unix/X0" ]; then
    echo "FluxLinux: host X0 socket ready"
    break
  fi
  i=$((i + 1))
  sleep 1
done
if [ ! -S "$TARGET_PREFIX/tmp/.X11-unix/X0" ]; then
  echo "FluxLinux: [WARN] host X0 not seen yet — continuing"
fi

if [ -f "$HELPER" ]; then
  export FLUX_CHROOT="$CHROOT_ROOT"
  export FLUX_PREFIX="$TARGET_PREFIX"
  export FLUX_HOST_TMP="${TARGET_PREFIX}/tmp"
  # Legacy aliases (if an older helper still reads NC_*)
  export NC_CHROOT="$CHROOT_ROOT"
  export NC_PREFIX="$TARGET_PREFIX"
  export NC_HOST_TMP="${TARGET_PREFIX}/tmp"
  sh "$HELPER" mount --x11 || true
  echo "[2/5] X11 via fluxlinux_chroot mount --x11"
else
  /system/bin/mount -o remount,dev,suid /data 2>/dev/null \
    || $BB mount -o remount,dev,suid /data 2>/dev/null || true
  $BB mount --bind /dev "$CHROOT_ROOT/dev" 2>/dev/null || true
  $BB mount --bind /sys "$CHROOT_ROOT/sys" 2>/dev/null || true
  $BB mount -t proc proc "$CHROOT_ROOT/proc" 2>/dev/null || true
  $BB mount -t devpts devpts "$CHROOT_ROOT/dev/pts" 2>/dev/null || true
  mkdir -p "$CHROOT_ROOT/dev/shm"
  $BB mount -t tmpfs -o size=512M,mode=1777 tmpfs "$CHROOT_ROOT/dev/shm" 2>/dev/null || true
  mkdir -p "$CHROOT_ROOT/tmp" "$CHROOT_ROOT/mnt/host-tmp"
  if grep -q " $CHROOT_ROOT/tmp " /proc/mounts 2>/dev/null; then
    $BB umount "$CHROOT_ROOT/tmp" 2>/dev/null || $BB umount -l "$CHROOT_ROOT/tmp" 2>/dev/null || true
  fi
  chmod 1777 "$CHROOT_ROOT/tmp" 2>/dev/null || true
  $BB mount --bind "$TARGET_PREFIX/tmp" "$CHROOT_ROOT/mnt/host-tmp" 2>/dev/null || true
  mkdir -p "$CHROOT_ROOT/sdcard"
  $BB mount --bind /sdcard "$CHROOT_ROOT/sdcard" 2>/dev/null || true
  echo "[2/5] X11 socket bind (legacy)..."
  mkdir -p "$CHROOT_ROOT/tmp/.X11-unix"
  if grep -q " $CHROOT_ROOT/tmp/.X11-unix " /proc/mounts 2>/dev/null; then
    $BB umount "$CHROOT_ROOT/tmp/.X11-unix" 2>/dev/null || $BB umount -l "$CHROOT_ROOT/tmp/.X11-unix" 2>/dev/null || true
  fi
  $BB mount --bind "$TARGET_PREFIX/tmp/.X11-unix" "$CHROOT_ROOT/tmp/.X11-unix" 2>/dev/null \
    || mount --bind "$TARGET_PREFIX/tmp/.X11-unix" "$CHROOT_ROOT/tmp/.X11-unix" 2>/dev/null || true
fi

echo "[3/5] Kill stale XFCE in chroot..."
if [ -f "$HELPER" ]; then
  sh "$HELPER" sh --user root -- \
    "killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon 2>/dev/null; true" \
    >/dev/null 2>&1 || true
else
  $BB chroot "$CHROOT_ROOT" /bin/su - root -c \
    "killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon 2>/dev/null; true" \
    >/dev/null 2>&1
fi

echo "[4/5] GPU mode + launch XFCE as $USERNAME..."
# Guest script: sticky /tmp X11 + host-tmp VirGL + gpu_mode file
$BB chroot "$CHROOT_ROOT" /bin/sh -c "
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export TMPDIR=/tmp
# Clear host-leaked bus; failsafe needs XDG_CONFIG_DIRS including /etc
unset DBUS_SESSION_BUS_ADDRESS DBUS_SESSION_BUS_PID DBUS_SESSION_BUS_WINDOWID
export XDG_CONFIG_DIRS=/etc/xdg
export XDG_DATA_DIRS=/usr/local/share:/usr/share
export XDG_RUNTIME_DIR=/home/$USERNAME/.cache/runtime
mkdir -p \"\$XDG_RUNTIME_DIR\" && chmod 700 \"\$XDG_RUNTIME_DIR\"
mkdir -p /tmp/.ICE-unix && chmod 1777 /tmp/.ICE-unix
if id $USERNAME >/dev/null 2>&1; then
  chown -R $USERNAME:$USERNAME /home/$USERNAME 2>/dev/null || true
  mkdir -p /home/$USERNAME/.config /home/$USERNAME/.cache /home/$USERNAME/.local/share
  chmod 755 /home/$USERNAME 2>/dev/null || true
  chmod -R u+rwX /home/$USERNAME/.config /home/$USERNAME/.cache /home/$USERNAME/.local 2>/dev/null || true
  chown $USERNAME:$USERNAME \"\$XDG_RUNTIME_DIR\" 2>/dev/null || true
fi
if command -v dbus-uuidgen >/dev/null 2>&1; then
  dbus-uuidgen --ensure=/etc/machine-id 2>/dev/null || true
  mkdir -p /var/lib/dbus
  if [ ! -e /var/lib/dbus/machine-id ]; then
    ln -sf /etc/machine-id /var/lib/dbus/machine-id 2>/dev/null || \
      cp -f /etc/machine-id /var/lib/dbus/machine-id 2>/dev/null || true
  fi
fi
FLUX_SU_SHELL=/bin/bash
if [ ! -x /bin/bash ] && [ ! -x /usr/bin/bash ]; then
  FLUX_SU_SHELL=/bin/sh
fi
su -s \"\$FLUX_SU_SHELL\" - $USERNAME -c '
  unset DBUS_SESSION_BUS_ADDRESS DBUS_SESSION_BUS_PID DBUS_SESSION_BUS_WINDOWID
  export DISPLAY=:0
  export PULSE_SERVER=tcp:127.0.0.1
  export XDG_CONFIG_DIRS=/etc/xdg
  export XDG_DATA_DIRS=/usr/local/share:/usr/share
  export HOME=/home/$USERNAME
  export XDG_CONFIG_HOME=/home/$USERNAME/.config
  export XDG_CACHE_HOME=/home/$USERNAME/.cache
  export XDG_DATA_HOME=/home/$USERNAME/.local/share
  export XDG_RUNTIME_DIR=/home/$USERNAME/.cache/runtime
  mkdir -p /home/$USERNAME/.config /home/$USERNAME/.cache /home/$USERNAME/.local/share
  mkdir -p \"\$XDG_RUNTIME_DIR\" && chmod 700 \"\$XDG_RUNTIME_DIR\"
  export VTEST_SOCKET_NAME=/mnt/host-tmp/.virgl_test
  export GLYCIN_DISABLE_SANDBOX=1
  export GDK_DEBUG=no-glycin
  export GSK_RENDERER=cairo

  if [ -r /usr/local/lib/fluxlinux/apply_gpu_env.sh ]; then
    . /usr/local/lib/fluxlinux/apply_gpu_env.sh
    flux_gpu_apply_runtime
  else
    GPU_MODE=virgl
    if [ -r /etc/fluxlinux/gpu_mode ]; then
      GPU_MODE=\$(tr -d \"[:space:]\" </etc/fluxlinux/gpu_mode)
    fi
    case \"\$GPU_MODE\" in turnip|virgl) ;; *) GPU_MODE=virgl ;; esac
    if [ \"\$GPU_MODE\" = turnip ]; then
      export MESA_LOADER_DRIVER_OVERRIDE=zink
      export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json
      export TU_DEBUG=noconform
      export MESA_VK_WSI_DEBUG=sw
      export MESA_GL_VERSION_OVERRIDE=4.6
      export MESA_GLES_VERSION_OVERRIDE=3.2
    elif [ \"\$GPU_MODE\" = virgl ] && [ -S /mnt/host-tmp/.virgl_test ]; then
      export GALLIUM_DRIVER=virpipe
    else
      export LIBGL_ALWAYS_SOFTWARE=1
      export GALLIUM_DRIVER=llvmpipe
      echo \"FluxLinux(guest): software GL fallback\"
    fi
    export GPU_MODE
  fi
  echo \"FluxLinux(guest): GPU mode=\$GPU_MODE\"

  if command -v dbus-run-session >/dev/null 2>&1; then
    exec dbus-run-session -- startxfce4
  else
    exec dbus-launch --exit-with-session startxfce4
  fi
'
"
rc=$?
echo "[5/5] XFCE session ended (exit $rc)"
echo "========================================"
exit $rc
