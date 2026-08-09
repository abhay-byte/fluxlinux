#!/system/bin/sh
# start_debian13_gui.sh — root: mount Debian chroot + launch XFCE4
# Called by start_gui_chroot.sh after host Pulse/VirGL/X11 are up.
# Paths: com.ivarna.fluxlinux (not com.termux). Sticky guest /tmp preserved.

DEBIANPATH="${DEBIANPATH:-/data/local/tmp/chrootDebian13}"
TARGET_PREFIX="${TARGET_PREFIX:-/data/data/com.ivarna.fluxlinux/files/usr}"
USERNAME="${USERNAME:-flux}"

echo "========================================"
echo "FluxLinux: Chroot XFCE (root stage)"
echo "  rootfs=$DEBIANPATH"
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

if [ ! -d "$DEBIANPATH" ]; then
  echo "FluxLinux: ERROR — chroot missing: $DEBIANPATH"
  exit 1
fi
if [ ! -x "$DEBIANPATH/usr/bin/startxfce4" ] && [ ! -f "$DEBIANPATH/usr/bin/startxfce4" ]; then
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
if command -v chcon >/dev/null 2>&1; then
  chcon -R u:object_r:tmpfs:s0 "$TARGET_PREFIX/tmp" 2>/dev/null || true
fi

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
  export FLUX_CHROOT="$DEBIANPATH"
  export FLUX_PREFIX="$TARGET_PREFIX"
  export FLUX_HOST_TMP="${TARGET_PREFIX}/tmp"
  # Legacy aliases (if an older helper still reads NC_*)
  export NC_CHROOT="$DEBIANPATH"
  export NC_PREFIX="$TARGET_PREFIX"
  export NC_HOST_TMP="${TARGET_PREFIX}/tmp"
  sh "$HELPER" mount --x11 || true
  echo "[2/5] X11 via fluxlinux_chroot mount --x11"
else
  /system/bin/mount -o remount,dev,suid /data 2>/dev/null \
    || $BB mount -o remount,dev,suid /data 2>/dev/null || true
  $BB mount --bind /dev "$DEBIANPATH/dev" 2>/dev/null || true
  $BB mount --bind /sys "$DEBIANPATH/sys" 2>/dev/null || true
  $BB mount -t proc proc "$DEBIANPATH/proc" 2>/dev/null || true
  $BB mount -t devpts devpts "$DEBIANPATH/dev/pts" 2>/dev/null || true
  mkdir -p "$DEBIANPATH/dev/shm"
  $BB mount -t tmpfs -o size=512M,mode=1777 tmpfs "$DEBIANPATH/dev/shm" 2>/dev/null || true
  mkdir -p "$DEBIANPATH/tmp" "$DEBIANPATH/mnt/host-tmp"
  if grep -q " $DEBIANPATH/tmp " /proc/mounts 2>/dev/null; then
    $BB umount "$DEBIANPATH/tmp" 2>/dev/null || $BB umount -l "$DEBIANPATH/tmp" 2>/dev/null || true
  fi
  chmod 1777 "$DEBIANPATH/tmp" 2>/dev/null || true
  $BB mount --bind "$TARGET_PREFIX/tmp" "$DEBIANPATH/mnt/host-tmp" 2>/dev/null || true
  mkdir -p "$DEBIANPATH/sdcard"
  $BB mount --bind /sdcard "$DEBIANPATH/sdcard" 2>/dev/null || true
  echo "[2/5] X11 socket bind (legacy)..."
  mkdir -p "$DEBIANPATH/tmp/.X11-unix"
  if grep -q " $DEBIANPATH/tmp/.X11-unix " /proc/mounts 2>/dev/null; then
    $BB umount "$DEBIANPATH/tmp/.X11-unix" 2>/dev/null || $BB umount -l "$DEBIANPATH/tmp/.X11-unix" 2>/dev/null || true
  fi
  $BB mount --bind "$TARGET_PREFIX/tmp/.X11-unix" "$DEBIANPATH/tmp/.X11-unix" 2>/dev/null \
    || mount --bind "$TARGET_PREFIX/tmp/.X11-unix" "$DEBIANPATH/tmp/.X11-unix" 2>/dev/null || true
fi

echo "[3/5] Kill stale XFCE in chroot..."
if [ -f "$HELPER" ]; then
  sh "$HELPER" sh --user root -- \
    "killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon 2>/dev/null; true" \
    >/dev/null 2>&1 || true
else
  $BB chroot "$DEBIANPATH" /bin/su - root -c \
    "killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon 2>/dev/null; true" \
    >/dev/null 2>&1
fi

echo "[4/5] GPU mode + launch XFCE as $USERNAME..."
# Guest script: sticky /tmp X11 + host-tmp VirGL + gpu_mode file
$BB chroot "$DEBIANPATH" /bin/bash -c "
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export TMPDIR=/tmp
su - $USERNAME -c '
  export DISPLAY=:0
  export PULSE_SERVER=tcp:127.0.0.1
  export XDG_RUNTIME_DIR=/tmp
  export VTEST_SOCKET_NAME=/mnt/host-tmp/.virgl_test

  GPU_MODE=virgl
  if [ -r /etc/fluxlinux/gpu_mode ]; then
    GPU_MODE=\$(tr -d \"[:space:]\" </etc/fluxlinux/gpu_mode)
  fi
  case \"\$GPU_MODE\" in turnip|virgl) ;; *) GPU_MODE=virgl ;; esac
  echo \"FluxLinux(guest): GPU mode=\$GPU_MODE\"

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

  xfconf-query -c xfwm4 -p /general/use_compositing -s false 2>/dev/null || true
  exec dbus-launch --exit-with-session startxfce4
'
"
rc=$?
echo "[5/5] XFCE session ended (exit $rc)"
echo "========================================"
exit $rc
