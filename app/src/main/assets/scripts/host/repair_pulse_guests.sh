#!/bin/bash
# repair_pulse_guests.sh — run setup_pulse_guest.sh inside installed guests.
# Host Pulse stays in the app PREFIX. This only writes guest client config
# and installs named Pulse *client* packages when pactl is missing.

PKG="${TERMUX_APP__PACKAGE_NAME:-com.ivarna.fluxlinux}"
_HOST_ENV="${TERMUX__PREFIX:-/data/data/${PKG}/files/usr}/etc/fluxlinux-host.env"
[ -r "$_HOST_ENV" ] && . "$_HOST_ENV"

PKG="${TERMUX_APP__PACKAGE_NAME:-$PKG}"
export TERMUX_APP__PACKAGE_NAME="$PKG"
export TERMUX__PREFIX="${TERMUX__PREFIX:-/data/data/${PKG}/files/usr}"
export TERMUX__HOME="${TERMUX__HOME:-/data/data/${PKG}/files/home}"
export PREFIX="${PREFIX:-$TERMUX__PREFIX}"
export HOME="${TERMUX__HOME}"
export TMPDIR="${TMPDIR:-$PREFIX/tmp}"
export PATH="$PREFIX/bin:$PREFIX/bin/applets:/system/bin:/system/xbin${PATH:+:$PATH}"

COMMON="${HOME}/flux_guest_common.sh"
REPAIR="${HOME}/setup_pulse_guest.sh"
MODE="${1:-all}"

if [ ! -f "$COMMON" ] || [ ! -f "$REPAIR" ]; then
  echo "FluxLinux: [AUDIO] FAIL guest repair scripts not deployed"
  exit 0
fi

mkdir -p "$TMPDIR" 2>/dev/null || true
cp -f "$COMMON" "$TMPDIR/flux_guest_common.sh"
cp -f "$REPAIR" "$TMPDIR/setup_pulse_guest.sh"
chmod 755 "$TMPDIR/setup_pulse_guest.sh" 2>/dev/null || true

repair_proot() {
  _pd="$PREFIX/var/lib/proot-distro"
  command -v proot-distro >/dev/null 2>&1 || {
    echo "FluxLinux: [AUDIO] skip proot repair (proot-distro missing)"
    return 0
  }
  for _layout in containers installed-rootfs; do
    [ -d "$_pd/$_layout" ] || continue
    for _dir in "$_pd/$_layout"/*; do
      [ -d "$_dir" ] || continue
      _name=$(basename "$_dir")
      case "$_name" in
        .*|"") continue ;;
      esac
      if [ ! -d "$_dir/rootfs/etc" ] && [ ! -d "$_dir/etc" ]; then
        continue
      fi
      echo "FluxLinux: [AUDIO] repair proot $_name"
      # Same env -i as ProotCommandBuilder.guestLoginEnv — no host PATH/PREFIX.
      _gp="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
      proot-distro login "$_name" --shared-tmp --user root -- \
        env -i HOME=/root USER=root LOGNAME=root \
          TERM="${TERM:-xterm-256color}" LANG=C \
          TMPDIR=/tmp XDG_RUNTIME_DIR=/tmp PATH="$_gp" \
          PULSE_SERVER=tcp:127.0.0.1 \
          /bin/sh /tmp/setup_pulse_guest.sh \
        || echo "FluxLinux: [AUDIO] WARN repair failed proot $_name"
    done
  done
}

_CHROOT_PATHS="
/data/local/tmp/chrootDebian13
/data/local/tmp/chrootAlpine
/data/local/tmp/chrootFedora
/data/local/tmp/chrootVoid
/data/local/tmp/chrootOpenSUSE
/data/local/tmp/chrootDeepin
/data/local/tmp/chrootChimera
/data/local/tmp/chrootManjaro
/data/local/tmp/chrootUbuntu
/data/local/tmp/chrootKali
/data/local/tmp/chrootParrot
/data/local/tmp/chrootArch
"

repair_chroot_as_root() {
  _helper=/data/local/tmp/fluxlinux_chroot.sh
  if [ ! -f "$_helper" ]; then
    echo "FluxLinux: [AUDIO] skip chroot repair (helper missing)"
    return 0
  fi
  for _path in $_CHROOT_PATHS; do
    [ -d "$_path/etc" ] || continue
    mkdir -p "$_path/tmp" 2>/dev/null || true
    cp -f "$COMMON" "$_path/tmp/flux_guest_common.sh"
    cp -f "$REPAIR" "$_path/tmp/setup_pulse_guest.sh"
    chmod 755 "$_path/tmp/setup_pulse_guest.sh" 2>/dev/null || true
    echo "FluxLinux: [AUDIO] repair chroot $_path"
    FLUX_CHROOT="$_path" FLUX_PACKAGE="$PKG" \
      /system/bin/sh "$_helper" sh --user root -- '/bin/sh /tmp/setup_pulse_guest.sh' \
      || echo "FluxLinux: [AUDIO] WARN repair failed chroot $_path"
  done
}

if [ "$MODE" != "chroot-only" ]; then
  repair_proot
fi

if [ "$MODE" = "chroot-only" ] || [ "$(id -u)" = "0" ]; then
  if [ "$(id -u)" = "0" ]; then
    repair_chroot_as_root
  fi
  exit 0
fi

_su=""
for _c in /system/bin/su /sbin/su /system/xbin/su; do
  if [ -x "$_c" ]; then
    _su="$_c"
    break
  fi
done
if [ -z "$_su" ] && command -v su >/dev/null 2>&1; then
  _su=$(command -v su)
fi

if [ -n "$_su" ]; then
  echo "FluxLinux: [AUDIO] chroot repair via su"
  "$_su" -c "HOME='$HOME' PREFIX='$PREFIX' TERMUX__PREFIX='$PREFIX' TERMUX__HOME='$HOME' TERMUX_APP__PACKAGE_NAME='$PKG' /system/bin/sh '$0' chroot-only" \
    || echo "FluxLinux: [AUDIO] WARN chroot repair su failed"
else
  echo "FluxLinux: [AUDIO] skip chroot repair (no su)"
fi

exit 0
