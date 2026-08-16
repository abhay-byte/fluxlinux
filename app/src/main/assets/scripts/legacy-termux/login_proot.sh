#!/data/data/com.termux/files/usr/bin/bash
ID="${1:-}"
echo "$ID" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9._-]*$' || { echo "bad id"; exit 2; }
proot-distro login "$ID" --user flux || proot-distro login "$ID"
