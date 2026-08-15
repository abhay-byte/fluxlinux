#!/usr/bin/env bash
# upload_rootfs_release.sh — verify + upload the 12 distro rootfs tarballs to the
# GitHub release tag `rootfs` (FluxLinux rootfs SSOT host, plan
# docs/plans/rootfs-github-release-no-apk-bloat.md P0).
#
# Usage:
#   ./scripts/upload_rootfs_release.sh --check        # verify SHAs + print URLs, no upload
#   ./scripts/upload_rootfs_release.sh                # verify then upload all 12 + sha256sums.txt
#   ./scripts/upload_rootfs_release.sh --clobber      # allow re-upload only for identical-byte assets
#
# Safety (D9): never overwrite an existing release asset whose remote bytes differ.
# A changed SHA requires a NEW filename (old assets stay for old APKs).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RELEASE_TAG="rootfs"
ASSET_DIR="$ROOT/assets/rootfs"
REPO="${FLUX_REPO:-abhay-byte/fluxlinux}"
BASE_URL="https://github.com/$REPO/releases/download/$RELEASE_TAG"

# <release filename>|<sha256>
# Mirror of DistroInstallProfile SSOT — cross-checked by scripts/verify_rootfs_shas.sh.
# Alpine source is the real .tar.gz (D8); .minirootfs is an APK-only historical name.
PINS=(
  "debian_13_rootfs.tar.xz|13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803"
  "alpine_3.24_rootfs.tar.gz|f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259"
  "fedora_44_rootfs.tar.xz|2d89fe437973e4596d56bf096f71c182d273942a307e7e1e51462dba43db1bd4"
  "void_20250202_rootfs.tar.xz|01a30f17ae06d4d5b322cd579ca971bc479e02cc284ec1e5a4255bea6bac3ce6"
  "opensuse_tumbleweed_rootfs.tar.xz|bdcb8522a9672cfa513081313b2788f8844340e800918d16a2154e4ed785a12a"
  "deepin_25_rootfs.tar.xz|2c7abfe859db36249459251d0b29f853e9ffb79cd1b42c7661e997ba99193698"
  "chimera_20251220_rootfs.tar.xz|0900e3f2554faaf005c14a6850596dadae1e7d8a996138180eebb0b4694a4a6c"
  "manjaro_arm_rootfs.tar.xz|b7339bcc289e8bbb40d1ffdc6ece4404865383d14d4b7f0fb83aa81e01720156"
  "ubuntu_26.04_rootfs.tar.xz|e648a5302dd273c476e5658e652f88d1e66ece69b487431521c5caef4b960efc"
  "kali_2026_2_rootfs.tar.xz|01c48a29ebb543954ef200e766076a143cf42744760d7ccdc31683a19f670689"
  "parrot_7.2_rootfs.tar.xz|49f4c2899ef9574cc3b0d9aaa6eaff38c4b32a9ac1abea2faec73cfbaf8094d4"
  "archlinux_arm_rootfs.tar.xz|40209ef6318d3aad732299d46ce224c6a0ecded80b6f8091f5e38b40fa031d75"
)

CLOBBER=0
CHECK_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --check) CHECK_ONLY=1 ;;
    --clobber) CLOBBER=1 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

command -v gh >/dev/null 2>&1 || { echo "ERROR: gh CLI required" >&2; exit 1; }
command -v sha256sum >/dev/null 2>&1 || { echo "ERROR: sha256sum required" >&2; exit 1; }

sha256_of() { sha256sum "$1" | awk '{print $1}'; }

# Local source path for a release filename (Alpine uses the real .tar.gz name).
local_path() {
  case "$1" in
    alpine_3.24_rootfs.tar.gz) printf '%s/alpine_3.24_rootfs.tar.gz' "$ASSET_DIR" ;;
    *) printf '%s/%s' "$ASSET_DIR" "$1" ;;
  esac
}

# Remote digest (lowercase sha256 hex, empty when asset absent) for a release filename.
remote_digest() {
  gh api "repos/$REPO/releases/tags/$RELEASE_TAG" \
    --jq ".assets[] | select(.name == \"$1\") | .digest" 2>/dev/null \
    | sed 's/^sha256://' | tr '[:upper:]' '[:lower:]' || true
}

echo "=== FluxLinux rootfs release verification ($RELEASE_TAG) ==="

FAIL=0
SUMS_FILE="$(mktemp)"
for pin in "${PINS[@]}"; do
  name="${pin%%|*}"
  expected="${pin#*|}"
  src="$(local_path "$name")"
  if [ ! -f "$src" ]; then
    echo "[MISSING] $name — expected $src"
    FAIL=1
    continue
  fi
  got="$(sha256_of "$src")"
  if [ "$got" != "$expected" ]; then
    echo "[SHA MISMATCH] $name"
    echo "  expected $expected"
    echo "  got      $got"
    FAIL=1
    continue
  fi
  printf '%s  %s\n' "$got" "$name" >> "$SUMS_FILE"
  echo "[OK] $name  sha256=$got"
done

if [ "$FAIL" -ne 0 ]; then
  echo "FAIL: local SHA verification failed — refusing to continue" >&2
  exit 1
fi

# Cross-check the pin copies against the Kotlin SSOT + runtime scripts.
echo ""
bash "$ROOT/scripts/verify_rootfs_shas.sh" || {
  echo "FAIL: pin SSOT cross-check failed — refusing to continue" >&2
  exit 1
}

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
sha256sums_path="$WORK_DIR/sha256sums.txt"
cp -f "$SUMS_FILE" "$sha256sums_path"
rm -f "$SUMS_FILE"
echo ""
echo "sha256sums.txt (computed from files):"
cat "$sha256sums_path"

echo ""
echo "=== release URLs ==="
for pin in "${PINS[@]}"; do
  name="${pin%%|*}"
  echo "  $BASE_URL/$name"
done

if [ "$CHECK_ONLY" -eq 1 ]; then
  echo ""
  echo "CHECK ONLY — no upload performed."
  exit 0
fi

# Upload with a few retries — multi-hundred-MiB assets hit transient API drops.
gh_upload() {
  local args=("$@")
  local attempt
  for attempt in 1 2 3 4 5; do
    if gh release upload "$RELEASE_TAG" "${args[@]}" --repo "$REPO"; then
      return 0
    fi
    echo "WARN: gh release upload failed (attempt $attempt) — retrying in $((attempt * 15))s" >&2
    sleep $((attempt * 15))
  done
  return 1
}

echo ""
echo "=== upload to github.com/$REPO release tag $RELEASE_TAG ==="
for pin in "${PINS[@]}"; do
  name="${pin%%|*}"
  expected="${pin#*|}"
  src="$(local_path "$name")"
  remote="$(remote_digest "$name")"
  if [ -z "$remote" ]; then
    echo "[UPLOAD] $name (no remote asset)"
    gh_upload "$src"
  elif [ "$remote" = "$expected" ]; then
    if [ "$CLOBBER" -eq 1 ]; then
      echo "[RE-UPLOAD] $name (remote bytes already match — identical retry)"
      gh_upload "$src" --clobber
    else
      echo "[SKIP] $name already on the release with identical bytes"
    fi
  else
    echo "[REFUSED] $name already exists with DIFFERENT bytes" >&2
    echo "  remote sha256: $remote" >&2
    echo "  local  sha256: $expected" >&2
    echo "  Never clobber a SHA-changing asset — old APKs pin the old bytes (D9)." >&2
    echo "  Upload under a NEW filename instead." >&2
    exit 1
  fi
done

echo "[UPLOAD] sha256sums.txt"
gh_upload "$sha256sums_path" --clobber

echo ""
echo "=== release title ==="
gh release edit "$RELEASE_TAG" --repo "$REPO" --title "FluxLinux rootfs (12 distros)" 2>/dev/null \
  || echo "WARN: could not edit release title"

echo ""
echo "DONE — verify with:"
echo "  bash scripts/upload_rootfs_release.sh --check"
for pin in "${PINS[@]}"; do
  name="${pin%%|*}"
  echo "  curl -sIL $BASE_URL/$name | tail -1"
done
