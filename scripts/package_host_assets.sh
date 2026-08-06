#!/usr/bin/env bash
# package_host_assets.sh — stage host bootstrap + jniLibs into the app APK per flavor.
#
# Usage:
#   ./scripts/package_host_assets.sh com.ivarna.fluxlinux
#   ./scripts/package_host_assets.sh com.zenithblue.fluxlinux
#   ./scripts/package_host_assets.sh --all
#
# Actions:
#   1. Verify native/bootstrap/<id>/{bootstrap.tar,jniLibs/arm64-v8a/*.so}
#   2. Copy bootstrap.tar   → app/src/<flavor>/assets/bootstrap.tar
#   3. Copy jniLibs         → app/src/<flavor>/jniLibs/arm64-v8a/
#   4. Ensure rootfs asset  → app/src/main/assets/rootfs/debian_13_rootfs.tar.xz
#   5. Print sizes + SHA256 for CI logs.
#
# See docs/plans/embedded-terminal-bootstrap-proot-chroot.md §Phase 0.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOOTSTRAP_ROOT="$ROOT/native/bootstrap"
APP_SRC="$ROOT/app/src"
MAIN_ASSETS="$APP_SRC/main/assets"
ROOTFS_SRC="$ROOT/assets/rootfs/debian_13_rootfs.tar.xz"
ROOTFS_SHA256="13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803"

declare -A FLAVOR_FOR_ID=(
  [com.ivarna.fluxlinux]=ivarna
  [com.zenithblue.fluxlinux]=zenithblue
)

usage() {
  sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

stage_app_id() {
  local app_id="$1"
  local flavor="${FLAVOR_FOR_ID[$app_id]:-}"
  if [ -z "$flavor" ]; then
    echo "ERROR: unknown application id '$app_id' (expected com.ivarna.fluxlinux | com.zenithblue.fluxlinux)" >&2
    exit 2
  fi

  local src="$BOOTSTRAP_ROOT/$app_id"
  local flavor_assets="$APP_SRC/$flavor/assets"
  local flavor_jni="$APP_SRC/$flavor/jniLibs"

  # 1. Verify inputs
  local missing=0
  for f in "$src/bootstrap.tar" "$src/jniLibs/arm64-v8a/libbash.so" \
           "$src/jniLibs/arm64-v8a/libproot.so" "$src/jniLibs/arm64-v8a/libloader.so" \
           "$src/jniLibs/arm64-v8a/libloader32.so"; do
    if [ ! -f "$f" ]; then
      echo "ERROR: missing $f — run assemble_bootstrap.py --package-name $app_id first" >&2
      missing=1
    fi
  done
  if [ "$missing" -eq 1 ]; then exit 1; fi

  # 2-3. Stage assets + jniLibs (flavor source set)
  mkdir -p "$flavor_assets" "$flavor_jni/arm64-v8a"
  cp -f "$src/bootstrap.tar" "$flavor_assets/bootstrap.tar"
  cp -f "$src/jniLibs/arm64-v8a/"*.so "$flavor_jni/arm64-v8a/"

  # 4. Rootfs (shared, main source set)
  if [ ! -f "$MAIN_ASSETS/rootfs/debian_13_rootfs.tar.xz" ]; then
    mkdir -p "$MAIN_ASSETS/rootfs"
    cp -f "$ROOTFS_SRC" "$MAIN_ASSETS/rootfs/debian_13_rootfs.tar.xz"
  fi

  # 5. Report
  local bootstrap_sha rootfs_sha
  bootstrap_sha="$(sha256_of "$flavor_assets/bootstrap.tar")"
  rootfs_sha="$(sha256_of "$MAIN_ASSETS/rootfs/debian_13_rootfs.tar.xz")"
  local bootstrap_size rootfs_size
  bootstrap_size="$(du -h "$flavor_assets/bootstrap.tar" | awk '{print $1}')"
  rootfs_size="$(du -h "$MAIN_ASSETS/rootfs/debian_13_rootfs.tar.xz" | awk '{print $1}')"

  echo "=== packaged host assets for $app_id (flavor: $flavor) ==="
  echo "  app/src/$flavor/assets/bootstrap.tar  $bootstrap_size  sha256=$bootstrap_sha"
  echo "  app/src/$flavor/jniLibs/arm64-v8a/    $(ls "$flavor_jni/arm64-v8a" | tr '\n' ' ')"
  echo "  app/src/main/assets/rootfs/debian_13_rootfs.tar.xz  $rootfs_size  sha256=$rootfs_sha (expect $ROOTFS_SHA256)"

  if [ "$rootfs_sha" != "$ROOTFS_SHA256" ]; then
    echo "WARNING: rootfs SHA256 mismatch — expected $ROOTFS_SHA256" >&2
  fi
}

main() {
  case "${1:-}" in
    --all)
      for id in "${!FLAVOR_FOR_ID[@]}"; do
        stage_app_id "$id"
      done
      ;;
    com.*)
      stage_app_id "$1"
      ;;
    *)
      usage
      ;;
  esac
}

main "$@"
