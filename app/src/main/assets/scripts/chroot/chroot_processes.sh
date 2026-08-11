#!/system/bin/sh
# chroot_processes.sh — SSOT for list/kill/reap of chroot-rooted host processes
# Match: readlink /proc/PID/root equals CHROOT_PATH exactly.
#
# Usage:
#   chroot_processes.sh list [CHROOT_PATH]
#   chroot_processes.sh kill [CHROOT_PATH]
#   chroot_processes.sh reap [CHROOT_PATH]   # kill + sleep + list (verify)
# Exit: list/kill 0; reap 0 if clean, 2 if residual; 1 root/unknown.

CMD="${1:-list}"
CHROOT_PATH="${2:-/data/local/tmp/chrootDebian13}"

if [ "$(id -u)" != "0" ]; then
    printf '%s\n' "# chroot_processes v1"
    printf '%s\n' "# path=$CHROOT_PATH"
    printf '%s\n' "# error=root_required"
    printf '%s\n' "# count=0"
    exit 1
fi

# Collect matching PIDs (space-separated). Skip PID 1 and self.
collect_pids() {
    _pids=""
    for pid_dir in /proc/[0-9]*; do
        [ -d "$pid_dir" ] || continue
        _pid=$(basename "$pid_dir")
        [ "$_pid" = "1" ] && continue
        [ "$_pid" = "$$" ] && continue
        _root=$(readlink "$pid_dir/root" 2>/dev/null) || continue
        if [ "$_root" = "$CHROOT_PATH" ]; then
            if [ -z "$_pids" ]; then
                _pids="$_pid"
            else
                _pids="$_pids $_pid"
            fi
        fi
    done
    printf '%s' "$_pids"
}

emit_proc_line() {
    _pid="$1"
    _comm="?"
    _cmd=""
    if [ -r "/proc/$_pid/comm" ]; then
        _comm=$(tr -d '\n\r' < "/proc/$_pid/comm" 2>/dev/null || true)
        [ -z "$_comm" ] && _comm="?"
    fi
    if [ -r "/proc/$_pid/cmdline" ]; then
        _cmd=$(tr '\0' ' ' < "/proc/$_pid/cmdline" 2>/dev/null | cut -c1-80 || true)
        _cmd=$(printf '%s' "$_cmd" | sed 's/[[:space:]]*$//')
    fi
    printf '%s\t%s\t%s\n' "$_pid" "$_comm" "$_cmd"
}

do_list() {
    printf '%s\n' "# chroot_processes v1"
    printf '%s\n' "# path=$CHROOT_PATH"
    _list=$(collect_pids)
    _count=0
    if [ -n "$_list" ]; then
        # shellcheck disable=SC2086
        for _pid in $_list; do
            emit_proc_line "$_pid"
            _count=$((_count + 1))
        done
    fi
    printf '%s\n' "# count=$_count"
}

# Two-pass kill: collect → kill all → brief sleep → collect residuals → kill again.
do_kill() {
    _killed=0
    _failed=0
    _pass=1
    while [ "$_pass" -le 2 ]; do
        _list=$(collect_pids)
        [ -z "$_list" ] && break
        # shellcheck disable=SC2086
        for _pid in $_list; do
            if kill -9 "$_pid" 2>/dev/null; then
                _killed=$((_killed + 1))
            else
                if [ -d "/proc/$_pid" ]; then
                    _failed=$((_failed + 1))
                else
                    _killed=$((_killed + 1))
                fi
            fi
        done
        _pass=$((_pass + 1))
        if [ "$_pass" -le 2 ]; then
            sleep 0.2 2>/dev/null || true
        fi
    done
    printf '%s\n' "# chroot_processes v1"
    printf '%s\n' "# path=$CHROOT_PATH"
    printf '%s\n' "# killed=$_killed failed=$_failed"
}

case "$CMD" in
    list)
        do_list
        exit 0
        ;;
    kill)
        do_kill
        exit 0
        ;;
    reap)
        do_kill
        sleep 0.3 2>/dev/null || sleep 1
        do_list
        _residual=$(collect_pids)
        if [ -z "$_residual" ]; then
            exit 0
        fi
        exit 2
        ;;
    *)
        printf '%s\n' "# chroot_processes v1"
        printf '%s\n' "# error=unknown_cmd:$CMD"
        printf '%s\n' "# count=0"
        exit 1
        ;;
esac
