#!/usr/bin/env bash
# upload_host_bootstrap_release.sh — verify + upload flavor bootstrap tarballs
# to the GitHub release tag `rootfs` (same host as distro rootfs).
#
# Usage:
#   ./scripts/upload_host_bootstrap_release.sh --check
#   ./scripts/upload_host_bootstrap_release.sh
#   ./scripts/upload_host_bootstrap_release.sh --clobber   # identical-byte retry only
#
# D9: never overwrite an existing asset whose remote bytes differ.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RELEASE_TAG="rootfs"
REPO="${FLUX_REPO:-abhay-byte/fluxlinux}"
BASE_URL="https://github.com/$REPO/releases/download/$RELEASE_TAG"

# <release filename>|<sha256>|<local path relative to repo>
PINS=(
  "bootstrap_com.ivarna.fluxlinux.tar|5b16c6597d38380c0cab9471d3cf69a0c7a23d9a4191125bd9dd6ddc77277f5c|native/bootstrap/com.ivarna.fluxlinux/bootstrap.tar"
  "bootstrap_com.zenithblue.fluxlinux.tar|b0856e1009b8718455bfaf6cb5332e57473ab7d58200f8c28a6db89b30a30cfd|native/bootstrap/com.zenithblue.fluxlinux/bootstrap.tar"
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

remote_digest() {
  gh api "repos/$REPO/releases/tags/$RELEASE_TAG" \
    --jq ".assets[] | select(.name == \"$1\") | .digest" 2>/dev/null \
    | sed 's/^sha256://' | tr '[:upper:]' '[:lower:]' || true
}

echo "=== FluxLinux host bootstrap release verification ($RELEASE_TAG) ==="

FAIL=0
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

for pin in "${PINS[@]}"; do
  IFS='|' read -r name expected rel <<<"$pin"
  src="$ROOT/$rel"
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
  staged="$WORK_DIR/$name"
  cp -f "$src" "$staged"
  echo "[OK] $name  sha256=$got  ($(du -h "$src" | awk '{print $1}'))"
done

# Cross-check Kotlin HostBootstrap pins.
KOTLIN="$ROOT/app/src/main/kotlin/com/ivarna/fluxlinux/core/install/HostBootstrap.kt"
for pin in "${PINS[@]}"; do
  IFS='|' read -r name expected _ <<<"$pin"
  if ! grep -q "$name" "$KOTLIN"; then
    echo "[FAIL] $name not in HostBootstrap.kt" >&2
    FAIL=1
  fi
  if ! grep -q "$expected" "$KOTLIN"; then
    echo "[FAIL] $expected not in HostBootstrap.kt" >&2
    FAIL=1
  fi
done

if [ "$FAIL" -ne 0 ]; then
  echo "FAIL: local SHA verification failed — refusing to continue" >&2
  exit 1
fi

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
  IFS='|' read -r name expected _ <<<"$pin"
  staged="$WORK_DIR/$name"
  remote="$(remote_digest "$name")"
  if [ -z "$remote" ]; then
    echo "[UPLOAD] $name (no remote asset)"
    gh_upload "$staged"
  elif [ "$remote" = "$expected" ]; then
    if [ "$CLOBBER" -eq 1 ]; then
      echo "[RE-UPLOAD] $name (remote bytes already match — identical retry)"
      gh_upload "$staged" --clobber
    else
      echo "[SKIP] $name already on the release with identical bytes"
    fi
  else
    echo "[REFUSED] $name already exists with DIFFERENT bytes" >&2
    echo "  remote sha256: $remote" >&2
    echo "  local  sha256: $expected" >&2
    echo "  Never clobber a SHA-changing asset — upload under a NEW filename." >&2
    exit 1
  fi
done

# Merge bootstrap lines into the existing sha256sums.txt without dropping rootfs pins.
SUMS_REMOTE="$WORK_DIR/sha256sums.remote"
SUMS_OUT="$WORK_DIR/sha256sums.txt"
if gh release download "$RELEASE_TAG" --repo "$REPO" --pattern sha256sums.txt --dir "$WORK_DIR" --clobber 2>/dev/null \
  && [ -f "$WORK_DIR/sha256sums.txt" ]; then
  cp -f "$WORK_DIR/sha256sums.txt" "$SUMS_REMOTE"
else
  : > "$SUMS_REMOTE"
fi
# Drop previous bootstrap lines, keep rootfs pins, append current bootstrap pins.
grep -v ' bootstrap_com\.' "$SUMS_REMOTE" > "$SUMS_OUT" || true
for pin in "${PINS[@]}"; do
  IFS='|' read -r name expected _ <<<"$pin"
  printf '%s  %s\n' "$expected" "$name" >> "$SUMS_OUT"
done

echo "[UPLOAD] sha256sums.txt (merged rootfs + bootstrap)"
gh_upload "$SUMS_OUT" --clobber

echo ""
echo "DONE — verify with:"
echo "  bash scripts/upload_host_bootstrap_release.sh --check"
for pin in "${PINS[@]}"; do
  name="${pin%%|*}"
  echo "  curl -sIL $BASE_URL/$name | tail -1"
done
