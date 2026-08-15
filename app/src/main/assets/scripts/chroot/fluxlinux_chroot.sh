#!/system/bin/sh
# fluxlinux-chroot v2.8
# SSOT chroot runner for NativeCode Debian 13 (requires root).
# Do not nest this under run_debian13_root.sh — it already owns mounts + one chroot.
# Guest entry always uses env -i + Debian PATH (never Android /system PATH).
# v2.2: TTY-safe b64 (no stdin pipe), login --workdir, devpts ptmx heal.
#
# Usage:
#   fluxlinux_chroot.sh version
#   fluxlinux_chroot.sh mount [--x11]
#   fluxlinux_chroot.sh login [--user flux|root] [--shell zsh|bash] [--workdir PATH]
#   fluxlinux_chroot.sh sh    [--user flux|root] -- 'shell string'
#   fluxlinux_chroot.sh exec  [--user flux|root] -- CMD [ARGS...]
#   fluxlinux_chroot.sh b64   [--user flux|root] -- BASE64_PAYLOAD
#
# Env:
#   FLUX_CHROOT  FLUX_PACKAGE  FLUX_HOST_TMP  FLUX_PREFIX  FLUX_BB  FLUX_SHELL
set -u

VERSION_STR="fluxlinux-chroot v2.8"
# Prefer caller-pinned env (RootShell / start_gui). Fallbacks cover both store flavors.
if [ -z "${FLUX_PACKAGE:-}" ]; then
  if [ -d /data/data/com.zenithblue.fluxlinux/files/usr ]; then
    FLUX_PACKAGE=com.zenithblue.fluxlinux
  else
    FLUX_PACKAGE=com.ivarna.fluxlinux
  fi
fi
FLUX_CHROOT="${FLUX_CHROOT:-/data/local/tmp/chrootDebian13}"
FLUX_HOST_TMP="${FLUX_HOST_TMP:-/data/data/${FLUX_PACKAGE}/files/usr/tmp}"
FLUX_PREFIX="${FLUX_PREFIX:-/data/data/${FLUX_PACKAGE}/files/usr}"
LOGIN_SHELL="${FLUX_SHELL:-zsh}"
USER_NAME="flux"
LOGIN_WORKDIR=""
WANT_X11=0
MODE=""
BB=""

die() {
  echo "fluxlinux_chroot: $*" >&2
  exit 2
}

usage() {
  cat <<'EOF' >&2
usage:
  fluxlinux_chroot.sh version
  fluxlinux_chroot.sh mount [--x11]
  fluxlinux_chroot.sh login [--user flux|root] [--shell zsh|bash] [--workdir PATH]
  fluxlinux_chroot.sh sh    [--user flux|root] -- SHELL_STRING
  fluxlinux_chroot.sh exec  [--user flux|root] -- CMD [ARGS...]
  fluxlinux_chroot.sh b64   [--user flux|root] -- BASE64_PAYLOAD
EOF
  exit 2
}

require_root() {
  [ "$(id -u)" = "0" ] || die "must run as root (id=$(id -u))"
}

# Shared resolver (sidecar). Helper stays single-file if only this script was copied.
_rr=""
for _c in \
  "$(dirname "$0")/resolve_bb.sh" \
  /data/local/tmp/fluxlinux_resolve_bb.sh
do
  [ -n "$_c" ] && [ -f "$_c" ] && _rr="$_c" && break
done
_RESOLVE_BB_SOURCED=0
if [ -n "$_rr" ]; then
  # shellcheck disable=SC1090
  . "$_rr" && _RESOLVE_BB_SOURCED=1 || true
fi
if [ "$_RESOLVE_BB_SOURCED" != "1" ]; then
  bb_has() {
    "$1" --list 2>/dev/null | tr ' \t' '\n' | grep -qx "$2"
  }

  bb_ok() {
    [ -n "${1:-}" ] && [ -x "$1" ] || return 1
    case "$1" in *com.termux*|*fluxlinux*|*nativecode*) return 1 ;; esac
    bb_has "$1" chroot && bb_has "$1" mount
  }

  resolve_bb() {
    BB=""
    if [ -n "${FLUX_BB:-}" ] && bb_ok "$FLUX_BB"; then
      BB="$FLUX_BB"
    elif bb_ok /data/local/tmp/flux_busybox; then
      BB=/data/local/tmp/flux_busybox
    else
      for path in \
        /data/adb/ksu/bin/busybox \
        /data/adb/ap/bin/busybox \
        /data/adb/magisk/busybox \
        /data/adb/modules/busybox-ndk/system/xbin/busybox \
        /data/adb/modules/busybox-ndk/system/bin/busybox \
        /debug_ramdisk/busybox \
        /sbin/busybox
      do
        if bb_ok "$path"; then
          BB="$path"
          break
        fi
      done
      if [ -z "$BB" ]; then
        _det=$(command -v busybox 2>/dev/null || true)
        if [ -n "${_det:-}" ] && bb_ok "$_det"; then
          BB="$_det"
        fi
      fi
      if [ -z "$BB" ]; then
        for path in \
          /system/xbin/busybox \
          /system/bin/busybox
        do
          if bb_ok "$path"; then
            BB="$path"
            break
          fi
        done
      fi
    fi
    [ -n "$BB" ] || return 1
    if [ "$BB" != /data/local/tmp/flux_busybox ]; then
      cp -f "$BB" /data/local/tmp/flux_busybox 2>/dev/null \
        && chmod 755 /data/local/tmp/flux_busybox 2>/dev/null \
        && bb_ok /data/local/tmp/flux_busybox \
        && BB=/data/local/tmp/flux_busybox || true
    fi
    return 0
  }
fi

# True if target path is already a mount point (exact match in /proc/mounts).
is_mounted() {
  _tgt="$1"
  grep -q " ${_tgt} " /proc/mounts 2>/dev/null
}

bind_if_missing() {
  _src="$1"
  _dst="$2"
  mkdir -p "$_dst" 2>/dev/null || true
  if is_mounted "$_dst"; then
    return 0
  fi
  $BB mount --bind "$_src" "$_dst" 2>/dev/null \
    || /system/bin/mount --bind "$_src" "$_dst" 2>/dev/null || true
}

mount_type_if_missing() {
  _type="$1"
  _src="$2"
  _dst="$3"
  _opts="${4:-}"
  mkdir -p "$_dst" 2>/dev/null || true
  if is_mounted "$_dst"; then
    return 0
  fi
  if [ -n "$_opts" ]; then
    $BB mount -t "$_type" -o "$_opts" "$_src" "$_dst" 2>/dev/null \
      || /system/bin/mount -t "$_type" -o "$_opts" "$_src" "$_dst" 2>/dev/null || true
  else
    $BB mount -t "$_type" "$_src" "$_dst" 2>/dev/null \
      || /system/bin/mount -t "$_type" "$_src" "$_dst" 2>/dev/null || true
  fi
}

ensure_sticky_tmp() {
  mkdir -p "$FLUX_CHROOT/tmp" "$FLUX_CHROOT/var/tmp" 2>/dev/null || true
  # Never keep a host bind/tmpfs on guest /tmp (breaks apt _apt mkstemp).
  if is_mounted "$FLUX_CHROOT/tmp"; then
    $BB umount "$FLUX_CHROOT/tmp" 2>/dev/null || $BB umount -l "$FLUX_CHROOT/tmp" 2>/dev/null || true
  fi
  chmod 1777 "$FLUX_CHROOT/tmp" 2>/dev/null || true
  chmod 1777 "$FLUX_CHROOT/var/tmp" 2>/dev/null || true
}

# devpts with usable ptmx (stock mount often leaves ptmxmode=000 → c--------- pts/ptmx).
# Root still "passes" test -w on mode 000 — check numeric mode, not -w.
ensure_devpts() {
  _pts="$FLUX_CHROOT/dev/pts"
  mkdir -p "$_pts" 2>/dev/null || true
  if is_mounted "$_pts"; then
    _perm=""
    if [ -c "$_pts/ptmx" ]; then
      _perm=$($BB stat -c '%a' "$_pts/ptmx" 2>/dev/null || stat -c '%a' "$_pts/ptmx" 2>/dev/null || echo "")
    fi
    # 0 / 000 / empty missing → heal once (no mount storm: single umount+remount)
    case "$_perm" in
      ""|0|000)
        $BB umount "$_pts" 2>/dev/null || $BB umount -l "$_pts" 2>/dev/null || true
        ;;
    esac
  fi
  if ! is_mounted "$_pts"; then
    $BB mount -t devpts devpts "$_pts" -o newinstance,ptmxmode=0666,mode=0620 2>/dev/null \
      || $BB mount -t devpts devpts "$_pts" -o ptmxmode=0666,mode=0620 2>/dev/null \
      || $BB mount -t devpts -o ptmxmode=0666,mode=0620 devpts "$_pts" 2>/dev/null \
      || $BB mount -t devpts devpts "$_pts" 2>/dev/null \
      || true
  fi
  if [ ! -c "$FLUX_CHROOT/dev/ptmx" ] && [ -c "$_pts/ptmx" ]; then
    $BB ln -sf pts/ptmx "$FLUX_CHROOT/dev/ptmx" 2>/dev/null || true
  fi
  unset _pts _perm
}

ensure_mounts() {
  [ -d "$FLUX_CHROOT" ] || die "chroot missing: $FLUX_CHROOT"
  mkdir -p \
    "$FLUX_CHROOT/dev" "$FLUX_CHROOT/dev/pts" "$FLUX_CHROOT/dev/shm" \
    "$FLUX_CHROOT/proc" "$FLUX_CHROOT/sys" \
    "$FLUX_CHROOT/tmp" "$FLUX_CHROOT/mnt/host-tmp" "$FLUX_CHROOT/sdcard" \
    "$FLUX_HOST_TMP" 2>/dev/null || true

  # Soft remount /data for dev,suid (KSU/Magisk; fail soft)
  /system/bin/mount -o remount,dev,suid /data >/dev/null 2>&1 \
    || $BB mount -o remount,dev,suid /data >/dev/null 2>&1 \
    || $BB mount -o remount,dev,suid / >/dev/null 2>&1 \
    || true

  bind_if_missing /dev "$FLUX_CHROOT/dev"
  bind_if_missing /sys "$FLUX_CHROOT/sys"
  # /dev bind usually exposes kgsl. If the node is still missing, mknod (fail-soft).
  if [ -e /dev/kgsl-3d0 ] && [ ! -e "$FLUX_CHROOT/dev/kgsl-3d0" ]; then
    $BB mknod "$FLUX_CHROOT/dev/kgsl-3d0" c $($BB stat -c "%t %T" /dev/kgsl-3d0 2>/dev/null) \
      >/dev/null 2>&1 || true
    chmod 666 "$FLUX_CHROOT/dev/kgsl-3d0" >/dev/null 2>&1 || true
  fi
  mount_type_if_missing proc proc "$FLUX_CHROOT/proc"
  ensure_devpts
  mount_type_if_missing tmpfs tmpfs "$FLUX_CHROOT/dev/shm" "size=512M,mode=1777"

  ensure_sticky_tmp

  bind_if_missing "$FLUX_HOST_TMP" "$FLUX_CHROOT/mnt/host-tmp"
  bind_if_missing /sdcard "$FLUX_CHROOT/sdcard"

  # launch_tool bridge: host-tmp → guest sticky /tmp
  if [ -f "$FLUX_HOST_TMP/launch_tool.sh" ]; then
    cp -f "$FLUX_HOST_TMP/launch_tool.sh" "$FLUX_CHROOT/tmp/launch_tool.sh" 2>/dev/null || true
    chmod 755 "$FLUX_CHROOT/tmp/launch_tool.sh" 2>/dev/null || true
  fi

  if [ "$WANT_X11" = "1" ]; then
    mkdir -p "$FLUX_PREFIX/tmp/.X11-unix" "$FLUX_CHROOT/tmp/.X11-unix" 2>/dev/null || true
    chmod 1777 "$FLUX_PREFIX/tmp/.X11-unix" 2>/dev/null || true
    # Refresh X11 bind so new host sockets appear
    if is_mounted "$FLUX_CHROOT/tmp/.X11-unix"; then
      $BB umount "$FLUX_CHROOT/tmp/.X11-unix" 2>/dev/null \
        || $BB umount -l "$FLUX_CHROOT/tmp/.X11-unix" 2>/dev/null || true
    fi
    $BB mount --bind "$FLUX_PREFIX/tmp/.X11-unix" "$FLUX_CHROOT/tmp/.X11-unix" 2>/dev/null \
      || mount --bind "$FLUX_PREFIX/tmp/.X11-unix" "$FLUX_CHROOT/tmp/.X11-unix" 2>/dev/null || true
  fi
}

# Single-quote escape for embedding into su -c '…'
sq() {
  # shellcheck disable=SC2001
  printf "%s" "$1" | sed "s/'/'\\\\''/g"
}

# shell-join argv into a single-quoted string safe for su -c
quote_argv() {
  _out=""
  for _a in "$@"; do
    _q=$(sq "$_a")
    if [ -z "$_out" ]; then
      _out="'$_q'"
    else
      _out="$_out '$_q'"
    fi
  done
  printf "%s" "$_out"
}

# Canonical Debian PATH inside rootfs (no Android /system).
GUEST_PATH_ROOT="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

guest_path_for_user() {
  if [ "$USER_NAME" = "root" ]; then
    printf '%s' "$GUEST_PATH_ROOT"
  else
    printf '%s' "/home/flux/.local/bin:/home/flux/bin:/home/flux/.cargo/bin:/opt/nodejs/bin:$GUEST_PATH_ROOT"
  fi
}

# KEY=VAL list for guest /usr/bin/env -i (space-separated; values have no spaces).
build_guest_env_args() {
  _gp=$(guest_path_for_user)
  _term="${TERM:-xterm-256color}"
  # POSIX C until guest profile.d/zshrc picks a locale that exists.
  # Forcing en_US.UTF-8 here prints setlocale warnings on Manjaro ARM.
  _lang="${LANG:-C}"
  if [ "$USER_NAME" = "root" ]; then
    GUEST_ENV_ARGS="PATH=$_gp HOME=/root USER=root LOGNAME=root TERM=$_term LANG=$_lang TMPDIR=/tmp XDG_RUNTIME_DIR=/tmp DEBIAN_FRONTEND=noninteractive"
  else
    GUEST_ENV_ARGS="PATH=$_gp HOME=/home/flux USER=flux LOGNAME=flux NVM_DIR=/home/flux/.nvm TERM=$_term LANG=$_lang TMPDIR=/tmp XDG_RUNTIME_DIR=/tmp DEBIAN_FRONTEND=noninteractive"
  fi
}

# chroot + clean env -i + remaining guest argv (drops Android PATH/LD_*).
guest_chroot_env() {
  build_guest_env_args
  # shellcheck disable=SC2086
  if [ -n "${BB:-}" ] && bb_has "$BB" chroot; then
    exec $BB chroot "$FLUX_CHROOT" /usr/bin/env -i $GUEST_ENV_ARGS "$@"
  fi
  exec /system/bin/chroot "$FLUX_CHROOT" /usr/bin/env -i $GUEST_ENV_ARGS "$@"
}

guest_bin_exists() {
  [ -x "$FLUX_CHROOT/bin/$1" ] || [ -x "$FLUX_CHROOT/usr/bin/$1" ]
}

# Absolute guest path for an applet (/bin, /usr/bin, /sbin, /usr/sbin).
# Fedora 44+ puts su/runuser under util-linux, often only in /usr/sbin.
guest_bin_path() {
  for _d in /bin /usr/bin /sbin /usr/sbin; do
    if [ -x "$FLUX_CHROOT$_d/$1" ]; then
      printf '%s' "$_d/$1"
      return 0
    fi
  done
  return 1
}

# Switch to USER_NAME without guest su/runuser.
# Android busybox chroot is "NEWROOT [PROG]" only — GNU --userspec is treated
# as NEWROOT and fails with "can't change root directory to '--userspec=…'".
# Stage host busybox into the guest (argv0 must be "busybox") and drop uid
# with setuidgid NUMERIC (name lookup does not see guest "flux").
guest_userspec() {
  _pw="$FLUX_CHROOT/etc/passwd"
  _uid=$(awk -F: -v u="$USER_NAME" '$1==u {print $3; exit}' "$_pw")
  [ -n "${_uid:-}" ] || \
    die "cannot switch to $USER_NAME: no su/runuser and no passwd entry"
  if ! "$BB" --list 2>/dev/null | tr ' \t' '\n' | grep -qx setuidgid; then
    die "busybox setuidgid missing; cannot drop to $USER_NAME (no guest su)"
  fi
  mkdir -p "$FLUX_CHROOT/tmp" || true
  _bb=/tmp/busybox
  cp -f "$BB" "$FLUX_CHROOT$_bb" || die "cannot stage busybox for setuidgid"
  chmod 755 "$FLUX_CHROOT$_bb"
  build_guest_env_args
  # shellcheck disable=SC2086
  exec $BB chroot "$FLUX_CHROOT" "$_bb" setuidgid "$_uid" \
    /usr/bin/env -i $GUEST_ENV_ARGS "$@"
}

# Drop to USER_NAME then exec remaining argv (non-interactive).
# Order matches start_guest_gui: runuser, then su, then numeric setuidgid.
guest_as_user() {
  [ "$#" -ge 1 ] || die "guest_as_user requires CMD"
  _runuser=$(guest_bin_path runuser || true)
  if [ -n "${_runuser:-}" ]; then
    guest_chroot_env "$_runuser" -u "$USER_NAME" -- "$@"
  fi
  _su=$(guest_bin_path su || true)
  if [ -n "${_su:-}" ]; then
    # su - USER -s SHELL [ -c CMD … ]
    guest_chroot_env "$_su" - "$USER_NAME" -s "$@"
  fi
  guest_userspec "$@"
}

# Interactive login as USER_NAME. LOGIN_SHELL is already resolve_login_shell'd.
# su - handles home/cd; runuser/userspec cd via $HOME from build_guest_env_args.
guest_login_user() {
  case "$LOGIN_SHELL" in
    zsh)
      _bin=/bin/zsh
      _boot="exec /bin/zsh -l"
      ;;
    sh|ash)
      _bin=/bin/sh
      _boot="exec /bin/sh -l"
      ;;
    bash|*)
      _bin=/bin/bash
      _boot="exec /bin/bash --login"
      ;;
  esac
  _inner="${_cd}${_boot}"

  _runuser=$(guest_bin_path runuser || true)
  if [ -n "${_runuser:-}" ]; then
    guest_chroot_env "$_runuser" -u "$USER_NAME" -- /bin/sh -c "cd 2>/dev/null || true; $_inner"
  fi
  _su=$(guest_bin_path su || true)
  if [ -n "${_su:-}" ]; then
    if [ -n "$_cd" ]; then
      guest_chroot_env "$_su" - "$USER_NAME" -s "$_bin" -c "$_inner"
    else
      guest_chroot_env "$_su" - "$USER_NAME" -s "$_bin"
    fi
  fi
  guest_userspec /bin/sh -c "cd 2>/dev/null || true; $_inner"
}

# App uid cannot stat /data/local/tmp (SELinux). Resolve as root from the rootfs.
resolve_login_shell() {
  _req="${1:-zsh}"
  case "$_req" in
    ash) _req=sh ;;
  esac
  if guest_bin_exists "$_req"; then
    printf '%s' "$_req"
    return
  fi
  for _s in zsh bash sh; do
    if guest_bin_exists "$_s"; then
      printf '%s' "$_s"
      return
    fi
  done
  printf '%s' "sh"
}

# Seed Flux zsh profile when customization never wrote one (Alpine chroot).
ensure_flux_zsh_profile() {
  [ "$USER_NAME" = "flux" ] || return 0
  _home="$FLUX_CHROOT/home/flux"
  [ -d "$_home" ] || return 0
  if [ ! -f "$_home/.zshrc" ]; then
    cat > "$_home/.zshrc" <<'ZSHRC'
# Guest PATH only — never inherit host PREFIX/bin (nested proot glue errors).
export PATH="$HOME/.local/bin:/opt/nodejs/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

_have=$(locale -a 2>/dev/null || true)
_pick=""
for _c in en_US.UTF-8 en_US.utf8 C.UTF-8 C.utf8; do
  echo "$_have" | grep -qxFi "$_c" && { _pick="$_c"; break; }
done
if [ -n "$_pick" ]; then
  export LANG="$_pick" LC_ALL="$_pick"
else
  unset LC_ALL
  export LANG=C
fi
unset _have _c _pick
unset PROOT_TMP_DIR
export TMPDIR="${TMPDIR:-/tmp}"
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp}"

{
  if command -v fastfetch >/dev/null 2>&1; then
    _ff="$HOME/.local/share/fastfetch/presets/termux.jsonc"
    if [ -f "$_ff" ]; then
      fastfetch --config "$_ff" 2>/dev/null || true
    else
      fastfetch --config termux 2>/dev/null || true
    fi
    unset _ff
  fi
  if command -v pokemon-colorscripts >/dev/null 2>&1; then
    pokemon-colorscripts --no-title -r 1,2,3 2>/dev/null || true
  fi
} &!

export ZSH="${ZSH:-$HOME/.oh-my-zsh}"
if [ -f "$ZSH/oh-my-zsh.sh" ]; then
  ZSH_THEME="agnosterzak"
  DISABLE_UPDATE_PROMPT=true
  DISABLE_AUTO_UPDATE=true
  ZSH_DISABLE_COMPFIX=true
  plugins=(git zsh-autosuggestions zsh-syntax-highlighting)
  source "$ZSH/oh-my-zsh.sh"
fi

if command -v apk >/dev/null 2>&1 && command -v sudo >/dev/null 2>&1; then
  apk() { command sudo apk "$@"; }
fi
ZSHRC
  fi
  if [ ! -f "$_home/.zprofile" ]; then
    printf '%s\n' '[[ -o interactive ]] || { [ -f "$HOME/.zshrc" ] && . "$HOME/.zshrc"; }' \
      > "$_home/.zprofile"
  fi
  if [ -x "$FLUX_CHROOT/bin/zsh" ] && [ -f "$FLUX_CHROOT/etc/passwd" ] && \
     grep -q '^flux:' "$FLUX_CHROOT/etc/passwd" && \
     ! grep -q '^flux:.*zsh$' "$FLUX_CHROOT/etc/passwd"; then
    sed -i 's|^\(flux:[^:]*:[^:]*:[^:]*:[^:]*:[^:]*\):.*|\1:/bin/zsh|' \
      "$FLUX_CHROOT/etc/passwd" 2>/dev/null || true
  fi
  chown --reference="$_home" "$_home/.zshrc" "$_home/.zprofile" 2>/dev/null || true
}

guest_login() {
  ensure_flux_zsh_profile
  LOGIN_SHELL="$(resolve_login_shell "${LOGIN_SHELL:-zsh}")"
  # Optional project cwd (workspace shell). Path must not contain single quotes.
  _cd=""
  if [ -n "${LOGIN_WORKDIR:-}" ]; then
    case "$LOGIN_WORKDIR" in
      *"'"*) die "workdir must not contain single quotes" ;;
    esac
    _cd="cd '$LOGIN_WORKDIR' 2>/dev/null || true; "
  fi
  case "$USER_NAME" in
    root)
      case "$LOGIN_SHELL" in
        zsh)
          if [ -n "$_cd" ]; then
            guest_chroot_env /bin/zsh -c "${_cd}exec /bin/zsh -l"
          else
            guest_chroot_env /bin/zsh -l
          fi
          ;;
        sh|ash)
          # Alpine/Chimera minirootfs — resolver may return sh when bash/zsh missing
          if [ -n "$_cd" ]; then
            guest_chroot_env /bin/sh -c "${_cd}exec /bin/sh -l"
          else
            guest_chroot_env /bin/sh -l
          fi
          ;;
        bash|*)
          if [ -n "$_cd" ]; then
            guest_chroot_env /bin/bash --login -c "${_cd}exec /bin/bash --login"
          else
            guest_chroot_env /bin/bash --login
          fi
          ;;
      esac
      ;;
    flux|*)
      guest_login_user
      ;;
  esac
}

# Non-interactive: one shell string as USER_NAME (single chroot + one su layer).
# Host-encode to base64 then guest_b64 — avoids double-quote/$/` breakage (G3).
guest_sh() {
  _cmd="$1"
  _b64=""
  if command -v base64 >/dev/null 2>&1; then
    _b64=$(printf '%s' "$_cmd" | base64 | tr -d '\n')
  elif [ -n "$BB" ]; then
    _b64=$(printf '%s' "$_cmd" | $BB base64 2>/dev/null | tr -d '\n')
  fi
  if [ -n "$_b64" ]; then
    guest_b64 "$_b64"
    return
  fi
  # Fallback only if host has no base64 (should not happen on Android root)
  # Prefer bash when present; Alpine minirootfs only has /bin/sh until bootstrap.
  _gshell=/bin/sh
  [ -x "${FLUX_CHROOT:-}/bin/bash" ] || [ -x /bin/bash ] && _gshell=/bin/bash
  if [ "$USER_NAME" = "root" ]; then
    guest_chroot_env "$_gshell" -c "$_cmd"
  else
    guest_as_user "$_gshell" -c "$_cmd"
  fi
}

# Argv-preserving exec (root: chroot + binary; flux: su -c with quoted argv).
guest_exec() {
  if [ "$#" -lt 1 ]; then
    die "exec requires CMD"
  fi
  if [ "$USER_NAME" = "root" ]; then
    guest_chroot_env "$@"
  else
    _joined=$(quote_argv "$@")
    _eshell=/bin/bash
    guest_bin_exists bash || _eshell=/bin/sh
    guest_as_user "$_eshell" -c "exec $_joined"
  fi
}

# Base64 payload → guest shell as USER_NAME (Kotlin / RootShell path).
# Absolute /usr/bin/base64 — never depend on guest PATH for decode bootstrap.
# TTY-safe: decode to temp script then run FILE (do NOT pipe into bash — that steals stdin
# and breaks TUI tools needing /dev/tty: bubbletea, grok, claude, opencode).
# Prefers /bin/bash when present; falls back to /bin/sh for Alpine minirootfs.
guest_b64() {
  _b64="$1"
  [ -n "$_b64" ] || die "b64 requires payload"
  # alphabet-only payload — safe inside single quotes
  # \$ preserved for guest; host expands only ${_b64}
  _inner="_b='${_b64}'; _f=/tmp/.nc_b64_\$\$; { echo \$_b | /usr/bin/base64 -d 2>/dev/null || echo \$_b | /bin/base64 -d; } >\$_f || exit 2; if [ -x /bin/bash ]; then /bin/bash --noprofile --norc \$_f; else /bin/sh \$_f; fi; _e=\$?; rm -f \$_f; exit \$_e"
  if [ "$USER_NAME" = "root" ]; then
    # Outer bootstrap: sh always exists; inner script picks bash if available
    guest_chroot_env /bin/sh -c "$_inner"
  else
    guest_as_user /bin/sh -c "$_inner"
  fi
}

parse_common_flags() {
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --user)
        [ "$#" -ge 2 ] || die "--user needs value"
        USER_NAME="$2"
        shift 2
        ;;
      --shell)
        [ "$#" -ge 2 ] || die "--shell needs value"
        LOGIN_SHELL="$2"
        shift 2
        ;;
      --x11)
        WANT_X11=1
        shift
        ;;
      --)
        shift
        REMAINING_ARGS="$@"
        return 0
        ;;
      -*)
        die "unknown flag: $1"
        ;;
      *)
        REMAINING_ARGS="$@"
        return 0
        ;;
    esac
  done
  REMAINING_ARGS=""
}

# --- main ---
[ "$#" -ge 1 ] || usage
MODE="$1"
shift

case "$MODE" in
  version|-V|--version)
    echo "$VERSION_STR"
    exit 0
    ;;
  mount|login|sh|exec|b64) ;;
  -h|--help|help) usage ;;
  *) die "unknown mode: $MODE" ;;
esac

require_root
resolve_bb || die "root-capable busybox not found"

# Flag parse: collect until -- or end; modes sh/exec/b64 need --
REMAINING_ARGS=""
WANT_X11=0
USER_NAME="flux"
LOGIN_SHELL="${FLUX_SHELL:-zsh}"

case "$MODE" in
  mount)
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --x11) WANT_X11=1; shift ;;
        *) die "mount: unknown arg $1" ;;
      esac
    done
    ensure_mounts
    exit 0
    ;;
  login)
    LOGIN_WORKDIR=""
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --user)
          [ "$#" -ge 2 ] || die "--user needs value"
          USER_NAME="$2"; shift 2
          ;;
        --shell)
          [ "$#" -ge 2 ] || die "--shell needs value"
          LOGIN_SHELL="$2"; shift 2
          ;;
        --workdir)
          [ "$#" -ge 2 ] || die "--workdir needs value"
          LOGIN_WORKDIR="$2"; shift 2
          ;;
        --x11) WANT_X11=1; shift ;;
        *) die "login: unknown arg $1" ;;
      esac
    done
    case "$USER_NAME" in root|flux) ;; *) die "user must be flux|root" ;; esac
    ensure_mounts
    guest_login
    ;;
  sh)
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --user)
          [ "$#" -ge 2 ] || die "--user needs value"
          USER_NAME="$2"; shift 2
          ;;
        --x11) WANT_X11=1; shift ;;
        --) shift; break ;;
        *)
          # allow bare string without -- for convenience
          break
          ;;
      esac
    done
    case "$USER_NAME" in root|flux) ;; *) die "user must be flux|root" ;; esac
    [ "$#" -ge 1 ] || die "sh requires a shell string"
    # Join remaining as one command string (caller may pass one quoted arg)
    CMD_STR="$*"
    ensure_mounts
    guest_sh "$CMD_STR"
    ;;
  exec)
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --user)
          [ "$#" -ge 2 ] || die "--user needs value"
          USER_NAME="$2"; shift 2
          ;;
        --x11) WANT_X11=1; shift ;;
        --) shift; break ;;
        *) break ;;
      esac
    done
    case "$USER_NAME" in root|flux) ;; *) die "user must be flux|root" ;; esac
    [ "$#" -ge 1 ] || die "exec requires CMD"
    ensure_mounts
    guest_exec "$@"
    ;;
  b64)
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --user)
          [ "$#" -ge 2 ] || die "--user needs value"
          USER_NAME="$2"; shift 2
          ;;
        --x11) WANT_X11=1; shift ;;
        --) shift; break ;;
        *) break ;;
      esac
    done
    case "$USER_NAME" in root|flux) ;; *) die "user must be flux|root" ;; esac
    [ "$#" -ge 1 ] || die "b64 requires payload"
    B64_PAYLOAD="$1"
    ensure_mounts
    guest_b64 "$B64_PAYLOAD"
    ;;
esac
