#!/usr/bin/env bash
set -euo pipefail

# Build the Alpine Play payload from maintainer/CI inputs. Runtime onboarding
# never invokes apk or a remote installer: it only sees the resulting archive.

usage() {
  echo "usage: $0 --input ROOTFS.tar.gz --apk-cache DIR --output alpine_3.24_rootfs.tar.gz"
  echo "       $0 --input ROOTFS.tar.gz --output alpine_3.24_rootfs.tar.gz --fetch"
}

input=
apk_cache=
output=
fetch=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --input) input=$2; shift 2 ;;
    --apk-cache) apk_cache=$2; shift 2 ;;
    --output) output=$2; shift 2 ;;
    --fetch) fetch=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -n "$input" && -n "$output" ]] || { usage >&2; exit 2; }
[[ -e "$input" ]] || { echo "missing input: $input" >&2; exit 1; }
apk_cache=${apk_cache:-"$(mktemp -d /tmp/fluxlinux-alpine-apk-cache.XXXXXX)"}
mkdir -p "$apk_cache" "$(dirname "$output")"

packages=(
  bash sudo shadow ca-certificates curl wget unzip tar tzdata musl-locales
  dbus dbus-x11 xfce4 xfce4-session xfce4-settings xfce4-panel
  xfce4-terminal xfce4-screensaver xfdesktop xfwm4 thunar adwaita-icon-theme
  ttf-dejavu mesa-dri-gallium mesa-gl libpulse pulseaudio-utils
  glycin-image-rs glycin-svg gdk-pixbuf fontconfig git zsh
  xfce4-screenshooter fastfetch
)

if [[ "$fetch" -eq 1 ]]; then
  command -v docker >/dev/null 2>&1 || {
    echo "--fetch requires docker; provide --apk-cache instead" >&2
    exit 1
  }
  docker run --rm --platform linux/amd64 -v "$apk_cache:/out" alpine:3.24 \
    sh -ec 'apk update --allow-untrusted >/dev/null; apk fetch --allow-untrusted --no-cache --recursive --arch aarch64 --output /out "$@"' \
    fluxlinux-baseline "${packages[@]}"
fi

shopt -s nullglob
apk_files=("$apk_cache"/*.apk)
(( ${#apk_files[@]} > 0 )) || {
  echo "no .apk files in $apk_cache; run with --fetch or supply a cache" >&2
  exit 1
}

stage=$(mktemp -d /tmp/fluxlinux-alpine-play-baseline.XXXXXX)
trap 'rm -rf "$stage"' EXIT
case "$input" in
  *.tar.gz|*.tgz) tar -xzf "$input" -C "$stage" ;;
  *.tar.xz|*.txz) tar -xJf "$input" -C "$stage" ;;
  *) echo "input must be a tar.gz/tgz/tar.xz/txz archive" >&2; exit 2 ;;
esac

for apk_file in "${apk_files[@]}"; do
  tar -xzf "$apk_file" -C "$stage"
done

# Package post-install hooks cannot execute aarch64 binaries on an x86 CI
# worker. These deterministic entries are enough for the runtime finalizer;
# the package files themselves were selected recursively by apk above.
mkdir -p "$stage/etc/fluxlinux" "$stage/home/flux" "$stage/var/lib/dbus"
printf '%s\n' 'flux:x:1000:1000:FluxLinux:/home/flux:/bin/bash' >> "$stage/etc/passwd"
printf '%s\n' 'flux:x:1000:' >> "$stage/etc/group"
printf '%s\n' 'flux:!::0:99999:7:::' >> "$stage/etc/shadow"
chown -R 1000:1000 "$stage/home/flux"
chmod 0755 "$stage/home/flux"

cat > "$stage/etc/fluxlinux/play-baseline-v1" <<'EOF'
schema=1
flavor=zenithblue
architecture=aarch64
alpine=3.24
packageSource=Alpine 3.24 aarch64 APKINDEX resolved at maintainer build time
runtimeNetworkRequired=false
EOF
cat > "$stage/etc/fluxlinux/play-baseline-packages.txt" <<'EOF'
bash
sudo
shadow
ca-certificates
curl
wget
unzip
tar
tzdata
musl-locales
dbus
dbus-x11
xfce4
xfce4-session
xfce4-settings
xfce4-panel
xfce4-terminal
xfce4-screensaver
xfdesktop
xfwm4
thunar
adwaita-icon-theme
ttf-dejavu
mesa-dri-gallium
mesa-gl
libpulse
pulseaudio-utils
glycin-image-rs
glycin-svg
gdk-pixbuf
fontconfig
git
zsh
xfce4-screenshooter
fastfetch
EOF

# Glycin's sandbox helper cannot create namespaces under PRoot. This local
# shim is part of the pre-provisioned image; runtime setup does not download it.
cat > "$stage/usr/bin/bwrap" <<'EOF'
#!/bin/sh
while [ "$#" -gt 0 ]; do
  case "$1" in
    /*) [ -f "$1" ] && [ -x "$1" ] && exec "$@" ;;
  esac
  shift
done
exit 127
EOF
chmod 0755 "$stage/usr/bin/bwrap"

rm -f "$output"
tar --sort=name --mtime='UTC 1970-01-01' --owner=0 --group=0 --numeric-owner \
  -cf - -C "$stage" . | gzip -n > "$output"
sha256sum "$output"
printf 'PASS: Alpine Play baseline %s (%s bytes)\n' "$output" "$(stat -c %s "$output")"
