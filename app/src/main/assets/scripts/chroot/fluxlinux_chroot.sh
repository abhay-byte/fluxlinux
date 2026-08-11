#!/system/bin/sh
# fluxlinux-chroot v2.2
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

VERSION_STR="fluxlinux-chroot v2.2"
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

resolve_bb() {
  if [ -n "${FLUX_BB:-}" ] && [ -x "$FLUX_BB" ]; then
    BB="$FLUX_BB"
    return 0
  fi
  if command -v busybox >/dev/null 2>&1; then
    _det=$(command -v busybox)
    case "$_det" in
      *com.termux*|*fluxlinux*|*nativecode*) ;;
      *) BB="$_det" ;;
    esac
  fi
  if [ -z "$BB" ]; then
    for path in \
      /data/adb/ksu/bin/busybox \
      /data/adb/magisk/busybox \
      /data/adb/modules/busybox-ndk/system/bin/busybox \
      /debug_ramdisk/busybox \
      /sbin/busybox \
      /system/xbin/busybox \
      /system/bin/busybox
    do
      if [ -x "$path" ]; then
        BB="$path"
        break
      fi
    done
  fi
  [ -n "$BB" ] || die "root-capable busybox not found"
}

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
  $BB mount --bind "$_src" "$_dst" 2>/dev/null || true
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
    $BB mount -t "$_type" -o "$_opts" "$_src" "$_dst" 2>/dev/null || true
  else
    $BB mount -t "$_type" "$_src" "$_dst" 2>/dev/null || true
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
  _lang="${LANG:-en_US.UTF-8}"
  _lc="${LC_ALL:-en_US.UTF-8}"
  if [ "$USER_NAME" = "root" ]; then
    GUEST_ENV_ARGS="PATH=$_gp HOME=/root USER=root LOGNAME=root TERM=$_term LANG=$_lang LC_ALL=$_lc TMPDIR=/tmp XDG_RUNTIME_DIR=/tmp DEBIAN_FRONTEND=noninteractive"
  else
    GUEST_ENV_ARGS="PATH=$_gp HOME=/home/flux USER=flux LOGNAME=flux NVM_DIR=/home/flux/.nvm TERM=$_term LANG=$_lang LC_ALL=$_lc TMPDIR=/tmp XDG_RUNTIME_DIR=/tmp DEBIAN_FRONTEND=noninteractive"
  fi
}

# chroot + clean env -i + remaining guest argv (drops Android PATH/LD_*).
guest_chroot_env() {
  build_guest_env_args
  # shellcheck disable=SC2086
  exec $BB chroot "$FLUX_CHROOT" /usr/bin/env -i $GUEST_ENV_ARGS "$@"
}

guest_login() {
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
      case "$LOGIN_SHELL" in
        bash)
          if [ -n "$_cd" ]; then
            guest_chroot_env /bin/su - "$USER_NAME" -s /bin/bash -c "${_cd}exec /bin/bash -l"
          else
            guest_chroot_env /bin/su - "$USER_NAME" -s /bin/bash
          fi
          ;;
        zsh|*)
          if [ -n "$_cd" ]; then
            guest_chroot_env /bin/su - "$USER_NAME" -s /bin/zsh -c "${_cd}exec /bin/zsh -l"
          else
            guest_chroot_env /bin/su - "$USER_NAME" -s /bin/zsh
          fi
          ;;
      esac
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
  if [ "$USER_NAME" = "root" ]; then
    guest_chroot_env /bin/bash --noprofile --norc -c "$_cmd"
  else
    guest_chroot_env /bin/su - "$USER_NAME" -s /bin/bash -c "$_cmd"
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
    guest_chroot_env /bin/su - "$USER_NAME" -s /bin/bash -c "exec $_joined"
  fi
}

# Base64 payload → bash as USER_NAME (Kotlin / RootShell path).
# Absolute /usr/bin/base64 — never depend on guest PATH for decode bootstrap.
# TTY-safe: decode to temp script then bash FILE (do NOT pipe into bash — that steals stdin
# and breaks TUI tools needing /dev/tty: bubbletea, grok, claude, opencode).
guest_b64() {
  _b64="$1"
  [ -n "$_b64" ] || die "b64 requires payload"
  # alphabet-only payload — safe inside single quotes
  # \$ preserved for guest; host expands only ${_b64}
  _inner="_b='${_b64}'; _f=/tmp/.nc_b64_\$\$; { echo \$_b | /usr/bin/base64 -d 2>/dev/null || echo \$_b | /bin/base64 -d; } >\$_f || exit 2; /bin/bash --noprofile --norc \$_f; _e=\$?; rm -f \$_f; exit \$_e"
  if [ "$USER_NAME" = "root" ]; then
    guest_chroot_env /bin/bash --noprofile --norc -c "$_inner"
  else
    guest_chroot_env /bin/su - "$USER_NAME" -s /bin/bash -c "$_inner"
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
resolve_bb

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
