#!/system/bin/sh
# chroot_size.sh — SSOT measure of Debian chroot rootfs bytes (exclude host binds)
#
# Usage:
#   chroot_size.sh [CHROOT_PATH]
#
# Output (marker line immune to Magisk/su preamble):
#   # chroot_size v1
#   # path=/data/local/tmp/chrootDebian13
#   SIZE_BYTES=1234567890
#
# Exit: 0 measured; 1 root_required / no_dir; 2 measure_failed
#
# IMPORTANT: Do NOT sum with shell $(( )) — Android/toybox ash is 32-bit signed.
# Multi-GB rootfs overflows to negative SIZE_BYTES and the app treats it as fail.
# Sum via awk (double mantissa OK for disk sizes up to ~9 PB).

CHROOT_PATH="${1:-/data/local/tmp/chrootDebian13}"

case "$CHROOT_PATH" in
  ""|"/"|"/data"|"/data/"|"/data/local"|"/data/local/"|"/data/local/tmp"|"/data/local/tmp/")
    printf '%s\n' "# chroot_size v1"
    printf '%s\n' "# path=$CHROOT_PATH"
    printf '%s\n' "# error=refused_path"
    printf '%s\n' "SIZE_BYTES=-1"
    exit 1
    ;;
esac

printf '%s\n' "# chroot_size v1"
printf '%s\n' "# path=$CHROOT_PATH"

if [ "$(id -u)" != "0" ]; then
    printf '%s\n' "# error=root_required"
    printf '%s\n' "SIZE_BYTES=-1"
    exit 1
fi

if [ ! -d "$CHROOT_PATH" ]; then
    printf '%s\n' "# error=no_dir"
    printf '%s\n' "SIZE_BYTES=-1"
    exit 1
fi

# Exclude host bind mounts under rootfs (BusyBox/toybox du has no --exclude).
# Emit one du -sb line per allowed top-level entry; awk sums 64-bit-safe.
size_out=$(
    for e in "$CHROOT_PATH"/* "$CHROOT_PATH"/.[!.]* "$CHROOT_PATH"/..?*; do
        [ -e "$e" ] || continue
        n=$(basename "$e")
        case "$n" in
            sdcard|dev|proc|sys|mnt|run) continue ;;
        esac
        du -sb "$e" 2>/dev/null
    done | awk '{ s += $1 } END { printf "%.0f", s+0 }'
)

# Fallback: explicit Debian top-level dirs if glob walk yielded empty/zero
if [ -z "$size_out" ] || [ "$size_out" = "0" ]; then
    size_out=$(
        for n in bin boot etc home lib lib64 opt root sbin srv tmp usr var; do
            e="$CHROOT_PATH/$n"
            [ -e "$e" ] || continue
            du -sb "$e" 2>/dev/null
        done | awk '{ s += $1 } END { printf "%.0f", s+0 }'
    )
fi

[ -z "$size_out" ] && size_out="0"

printf '%s\n' "SIZE_BYTES=$size_out"

# Reject negative (should not happen with awk; guard if tool misbehaves)
case "$size_out" in
    -*) printf '%s\n' "# error=measure_failed"; exit 2 ;;
esac

exit 0
