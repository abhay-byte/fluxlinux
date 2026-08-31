#!/usr/bin/env bash
# package_host_assets.sh — stage directly executed host jniLibs per flavor.
#
# Usage:
#   ./scripts/package_host_assets.sh com.ivarna.fluxlinux
#   ./scripts/package_host_assets.sh com.zenithblue.fluxlinux
#   ./scripts/package_host_assets.sh --all
#
# Actions:
#   1. Verify native/bootstrap/<id>/jniLibs/arm64-v8a/*.so
#   2. Copy jniLibs → app/src/<flavor>/jniLibs/arm64-v8a/
#   3. Print the staged native files for CI logs.
#
# Bootstrap/rootfs archives are staged separately into Play dynamic features by
# scripts/prepare_play_payloads.py. Ivarna keeps its existing provider-owned
# release path; neither flavor puts bootstrap.tar in the base.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOOTSTRAP_ROOT="$ROOT/native/bootstrap"
APP_SRC="$ROOT/app/src"

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

  # 1. Verify directly executed native launchers. The large bootstrap is never
  #    copied into the base APK by this task.
  local missing=0
  local required=("$src/jniLibs/arm64-v8a/libbash.so" \
           "$src/jniLibs/arm64-v8a/libproot.so" "$src/jniLibs/arm64-v8a/libloader.so" \
           "$src/jniLibs/arm64-v8a/libloader32.so" \
           "$src/jniLibs/arm64-v8a/libpulseaudio.so" \
           "$src/jniLibs/arm64-v8a/libpactl.so")
  for f in "${required[@]}"; do
    if [ ! -f "$f" ]; then
      echo "ERROR: missing $f — run assemble_bootstrap.py --package-name $app_id first" >&2
      missing=1
    fi
  done
  if [ "$missing" -eq 1 ]; then exit 1; fi

  # 2-3. Stage assets + jniLibs (flavor source set)
  mkdir -p "$flavor_assets" "$flavor_jni/arm64-v8a"
  # Remove a stale pre-Worker-03 tarball from the base source set.
  rm -f "$flavor_assets/bootstrap.tar"
  cp -f "$src/jniLibs/arm64-v8a/"*.so "$flavor_jni/arm64-v8a/"

  # Pulse runtime overlay for already-extracted PREFIX (no EXTRACT_VERSION bump).
  mkdir -p "$flavor_assets/pulse-runtime"
  local lib_root="$src/root/data/data/$app_id/files/usr/lib"
  local so
  for so in libsoxr.so libsoxr-lsr.so libandroid-execinfo.so libFLAC.so libmp3lame.so; do
    if [ -e "$lib_root/$so" ]; then
      cp -fL "$lib_root/$so" "$flavor_assets/pulse-runtime/$so"
    fi
  done

  # 4. Report
  echo "=== packaged host assets for $app_id (flavor: $flavor) ==="
  echo "  app/src/$flavor/assets/bootstrap.tar  NOT packaged — supplied by PFD runtime_host"
  echo "  app/src/$flavor/jniLibs/arm64-v8a/    $(ls "$flavor_jni/arm64-v8a" | tr '\n' ' ')"
  echo "  rootfs: supplied by PFD distro_* dynamic features for the Play flavor"
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
