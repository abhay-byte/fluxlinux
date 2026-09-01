#!/usr/bin/env bash
set -euo pipefail

# Build the Alpine Play payload in an aarch64 Alpine userspace. Maintainer/CI
# may use binfmt/QEMU; the Android runtime never sees or invokes that emulator.
# apk performs the transaction, scriptlets, and triggers against the rootfs.

readonly ALPINE_VERSION="3.24.1"
readonly ALPINE_ARCH="aarch64"
readonly ALPINE_MINIROOTFS_URL="https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/alpine-minirootfs-3.24.1-aarch64.tar.gz"
readonly ALPINE_MINIROOTFS_SHA256="f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259"
readonly ALPINE_REPOSITORY="https://dl-cdn.alpinelinux.org/alpine/v3.24/main"
readonly ALPINE_COMMUNITY_REPOSITORY="https://dl-cdn.alpinelinux.org/alpine/v3.24/community"
readonly BUILDER_IMAGE="alpine:3.24"

packages=(
  bash sudo shadow ca-certificates curl wget unzip tar tzdata musl-locales
  dbus dbus-x11 xfce4 xfce4-session xfce4-settings xfce4-panel
  xfce4-terminal xfce4-screensaver xfdesktop xfwm4 thunar adwaita-icon-theme
  ttf-dejavu mesa-dri-gallium mesa-gl libpulse pulseaudio-utils
  glycin-image-rs glycin-svg gdk-pixbuf fontconfig git zsh
  xfce4-screenshooter fastfetch
)

usage() {
  echo "usage: $0 --input alpine-minirootfs-3.24.1-aarch64.tar.gz --output alpine_3.24_rootfs.tar.gz"
  echo "       [--provenance PATH] [--image alpine:3.24]"
}

input=
output=
provenance=
image="$BUILDER_IMAGE"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --input) input=${2:?missing --input value}; shift 2 ;;
    --output) output=${2:?missing --output value}; shift 2 ;;
    --provenance) provenance=${2:?missing --provenance value}; shift 2 ;;
    --image) image=${2:?missing --image value}; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -n "$input" && -n "$output" ]] || { usage >&2; exit 2; }
[[ -f "$input" ]] || { echo "missing input: $input" >&2; exit 1; }
command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required" >&2; exit 1; }
command -v tar >/dev/null 2>&1 || { echo "tar is required" >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 1; }

input=$(realpath "$input")
output=$(realpath -m "$output")
provenance=${provenance:-"${output}.provenance.json"}
provenance=$(realpath -m "$provenance")
output_dir=$(dirname "$output")
output_name=$(basename "$output")
mkdir -p "$output_dir" "$(dirname "$provenance")"

actual_input_sha=$(sha256sum "$input" | awk '{print $1}')
if [[ "$actual_input_sha" != "$ALPINE_MINIROOTFS_SHA256" ]]; then
  echo "ERROR: input is not the pinned Alpine ${ALPINE_VERSION} ${ALPINE_ARCH} minirootfs" >&2
  echo "expected=$ALPINE_MINIROOTFS_SHA256 actual=$actual_input_sha" >&2
  exit 1
fi

if command -v docker >/dev/null 2>&1; then
  runner=docker
elif command -v podman >/dev/null 2>&1; then
  runner=podman
else
  echo "docker or podman is required for the aarch64 apk transaction" >&2
  exit 1
fi

stage=$(mktemp -d /tmp/fluxlinux-alpine-play-transaction.XXXXXX)
cleanup() {
  find "$stage" -depth -delete 2>/dev/null || true
  if [[ -n "$(find "$stage" -mindepth 1 -print -quit 2>/dev/null)" ]]; then
    # apk creates root-owned files. Let the build image remove them so the
    # maintainer host is not left with a multi-hundred-megabyte temp tree.
    "$runner" run --rm -v "$stage:/target" "$image" sh -eu -c \
      'find /target -mindepth 1 -depth -delete' >/dev/null 2>&1 || true
  fi
  rmdir "$stage" 2>/dev/null || true
}
trap cleanup EXIT
mkdir -p "$stage/work"
input_dir=$(dirname "$input")
input_name=$(basename "$input")

"$runner" run --rm --platform linux/arm64 \
  -v "$input_dir:/input:ro" \
  -v "$stage/work:/work" \
  -v "$output_dir:/output" \
  "$image" sh -eu -c '
    archive=$1
    output_file=$2
    shift 2
    apk add --no-cache tar >/dev/null
    rootfs=/work/rootfs
    mkdir -p "$rootfs" /work/apk-cache
    tar -xzf "/input/$archive" -C "$rootfs"
    mkdir -p "$rootfs/etc/apk"
    printf "%s\n%s\n" \
      "https://dl-cdn.alpinelinux.org/alpine/v3.24/main" \
      "https://dl-cdn.alpinelinux.org/alpine/v3.24/community" \
      > "$rootfs/etc/apk/repositories"

    # The actual package-manager transaction. The aarch64 container is run
    # through build-time binfmt/QEMU on non-arm64 maintainer machines.
    apk --root "$rootfs" --arch aarch64 --cache-dir /work/apk-cache \
      --update add --no-progress "$@"

    # Flux-specific local finalization happens only after apk completed.
    chroot "$rootfs" /bin/sh -eu -c "
      if ! id flux >/dev/null 2>&1; then
        adduser -D -u 1000 -s /bin/bash flux
      fi
      addgroup flux audio 2>/dev/null || true
      addgroup flux video 2>/dev/null || true
      addgroup flux input 2>/dev/null || true
      addgroup flux netdev 2>/dev/null || true
      mkdir -p /etc/fluxlinux /var/lib/dbus /etc/pulse/client.conf.d
      printf "%s\n" 8de277067b3544d4b65c267d0edab928 > /etc/machine-id
      dbus-uuidgen --ensure=/etc/machine-id 2>/dev/null || true
      if [ ! -e /var/lib/dbus/machine-id ] && [ -f /etc/machine-id ]; then
        ln -sf /etc/machine-id /var/lib/dbus/machine-id
      fi
    "
    printf "schema=1\nflavor=zenithblue\narchitecture=aarch64\nalpine=3.24.1\npackageSource=apk transaction in %s\nruntimeNetworkRequired=false\n" "$archive" > "$rootfs/etc/fluxlinux/play-baseline-v1"
    printf "%s\n" "$@" > "$rootfs/etc/fluxlinux/play-baseline-requested-packages.txt"
    apk --root "$rootfs" info -vv > "$rootfs/etc/fluxlinux/play-baseline-packages.lock"

    for required in \
      /bin/sh /sbin/apk /usr/bin/startxfce4 /usr/bin/dbus-daemon \
      /usr/bin/xfce4-session /usr/bin/xfwm4 /usr/bin/xfce4-panel \
      /usr/bin/xfdesktop /usr/bin/thunar /usr/lib/libpulse.so.0 \
      /usr/bin/pactl /usr/lib/libGL.so.1 /etc/ssl/certs/ca-certificates.crt \
      /home/flux /etc/fluxlinux/play-baseline-v1; do
      test -e "$rootfs$required" || { echo "missing required $required" >&2; exit 1; }
    done
    test -s "$rootfs/lib/apk/db/installed"
    # Preserve the exact indexes used by the repository configuration for
    # provenance. apk owns the transaction; these copies are not extracted or
    # installed into the resulting image.
    wget -q -O /work/APKINDEX.main.tar.gz \
      "https://dl-cdn.alpinelinux.org/alpine/v3.24/main/aarch64/APKINDEX.tar.gz"
    wget -q -O /work/APKINDEX.community.tar.gz \
      "https://dl-cdn.alpinelinux.org/alpine/v3.24/community/aarch64/APKINDEX.tar.gz"
    sha256sum /work/APKINDEX.main.tar.gz /work/APKINDEX.community.tar.gz > /work/apk-index-sha256.txt
    tar --sort=name --mtime="UTC 1970-01-01" --owner=0 --group=0 --numeric-owner \
      -cf - -C "$rootfs" . | gzip -n > "/output/$output_file"
  ' sh "$input_name" "$output_name" "${packages[@]}"

image_id=$("$runner" image inspect "$image" --format '{{.Id}}' 2>/dev/null || echo unknown)
printf '%s\n' "$image_id" > "$stage/work/builder-image-id.txt"

archive_sha=$(sha256sum "$output" | awk '{print $1}')
archive_size=$(stat -c '%s' "$output")
expanded_size=$(python3 -c 'import os,sys; print(sum(os.stat(os.path.join(root,name), follow_symlinks=False).st_size for root,_,files in os.walk(sys.argv[1]) for name in files))' "$stage/work/rootfs")
apk_db_sha=$(sha256sum "$stage/work/rootfs/lib/apk/db/installed" | awk '{print $1}')
apk_indexes=$(python3 -c 'import json,sys; print(json.dumps([{"sha256": p[0], "path": " ".join(p[1:])} for p in (line.rstrip("\n").split() for line in open(sys.argv[1], encoding="utf-8"))], sort_keys=True))' "$stage/work/apk-index-sha256.txt")
package_count=$(wc -l < "$stage/work/rootfs/etc/fluxlinux/play-baseline-packages.lock" | tr -d ' ')
source_commit=$(git -C "$(dirname "$0")/.." rev-parse HEAD 2>/dev/null || echo unknown)
if [[ -n "${SOURCE_DATE_EPOCH:-}" ]]; then
  build_date=$(date -u -d "@$SOURCE_DATE_EPOCH" +%Y-%m-%dT%H:%M:%SZ)
else
  build_date=$(date -u +%Y-%m-%dT%H:%M:%SZ)
fi

python3 -c 'import json,sys; from pathlib import Path; out,idx=sys.argv[1:3]; data={"schemaVersion":2,"payloadVersion":"2.0.0","distroId":"alpine","architecture":"aarch64","upstreamSource":sys.argv[3],"upstreamSha256":sys.argv[4],"repository":sys.argv[5],"communityRepository":sys.argv[6],"repositoryIndexSha256":json.loads(idx),"packageManager":"apk","packageTransaction":"apk --root <rootfs> --arch aarch64 --update add","requestedPackageCount":int(sys.argv[7]),"apkInstalledDbSha256":sys.argv[8],"builderImage":sys.argv[9],"builderImageId":Path(sys.argv[10]).read_text().strip(),"sourceCommit":sys.argv[11],"buildDate":sys.argv[12],"archiveFileName":Path(sys.argv[13]).name,"archiveSha256":sys.argv[14],"compressedSize":int(sys.argv[15]),"uncompressedSize":int(sys.argv[16]),"marker":"/etc/fluxlinux/play-baseline-v1","runtimeNetworkRequired":False,"fluxCustomizations":"Flux user, D-Bus machine-id, local package lock, and Play marker"}; Path(out).write_text(json.dumps(data,indent=2,sort_keys=True)+"\n",encoding="utf-8")' \
  "$provenance" "$apk_indexes" "$ALPINE_MINIROOTFS_URL" "$actual_input_sha" \
  "$ALPINE_REPOSITORY" "$ALPINE_COMMUNITY_REPOSITORY" "$package_count" "$apk_db_sha" \
  "$image" "$stage/work/builder-image-id.txt" "$source_commit" "$build_date" "$output" \
  "$archive_sha" "$archive_size" "$expanded_size"

echo "PASS: real apk transaction completed"
echo "PASS: Alpine Play baseline $output ($archive_size bytes)"
echo "SHA-256: $archive_sha"
echo "Provenance: $provenance"
