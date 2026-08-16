#!/data/data/com.termux/files/usr/bin/bash
ID="${1:-}"
if ! echo "$ID" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9._-]*$'; then
    echo "bad id"
    am start -a android.intent.action.VIEW \
      -d "fluxlinux://callback?result=error&name=legacy_termux_uninstall_bad&reason=bad_id"
    exit 2
fi
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
OLD="$PREFIX/var/lib/proot-distro/installed-rootfs/$ID"
NEW="$PREFIX/var/lib/proot-distro/containers/$ID"

echo "Attempting to remove $ID..."
if proot-distro remove "$ID" 2>/dev/null; then
    echo "FluxLinux: $ID Uninstalled."
else
    echo "First attempt failed, retrying..."
    sleep 1
    if proot-distro remove "$ID" 2>/dev/null; then
        echo "FluxLinux: $ID Uninstalled."
    else
        echo "proot-distro command failed, using manual removal..."
        rm -rf "$OLD"
        rm -rf "$NEW"
        echo "FluxLinux: $ID manually removed."
    fi
fi
# proot-distro remove can leave a dir; sweep both names (Termux PREFIX only)
rm -rf "$OLD"
rm -rf "$NEW"
sleep 2
if [ ! -e "$OLD" ] && [ ! -e "$NEW" ]; then
    am start -a android.intent.action.VIEW \
      -d "fluxlinux://callback?result=success&name=legacy_termux_uninstall_${ID}"
else
    echo "FluxLinux: $ID still present after remove."
    am start -a android.intent.action.VIEW \
      -d "fluxlinux://callback?result=error&name=legacy_termux_uninstall_${ID}&reason=unknown"
fi
