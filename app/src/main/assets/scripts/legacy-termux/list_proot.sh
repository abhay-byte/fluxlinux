#!/data/data/com.termux/files/usr/bin/bash
set -u
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
OLD="$PREFIX/var/lib/proot-distro/installed-rootfs"
NEW="$PREFIX/var/lib/proot-distro/containers"

# Collect unique basenames of existing directories.
# Newer proot-distro: containers/<id>/
# v1.8 fallback:     installed-rootfs/<id>
ids=""
for dir in "$OLD" "$NEW"; do
  [ -d "$dir" ] || continue
  for p in "$dir"/*; do
    [ -d "$p" ] || continue
    b=$(basename "$p")
    case "$b" in
      ''|.*|*/*) continue ;;
    esac
    # allowlist: proot-distro names
    echo "$b" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9._-]*$' || continue
    case ",$ids," in *",$b,"*) ;; *) ids="${ids:+$ids,}$b" ;; esac
  done
done

bytes=""
layouts=""
IFS=',' 
for id in $ids; do
  [ -n "$id" ] || continue
  if [ -d "$NEW/$id" ]; then
    layout=containers
    path="$NEW/$id"
  else
    layout=installed-rootfs
    path="$OLD/$id"
  fi
  sz=$(du -sb "$path" 2>/dev/null | awk '{print $1}')
  sz=${sz:-0}
  bytes="${bytes:+$bytes,}$sz"
  layouts="${layouts:+$layouts,}$layout"
done

# Empty scan is success with empty ids (not an error).
am start -a android.intent.action.VIEW \
  -d "fluxlinux://callback?result=success&name=legacy_termux_list&ids=${ids}&bytes=${bytes}&layouts=${layouts}"
