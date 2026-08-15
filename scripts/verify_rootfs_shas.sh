#!/usr/bin/env bash
# verify_rootfs_shas.sh — cross-check the rootfs name/SHA pins across every
# location that carries a copy. Kotlin DistroInstallProfile is the SSOT.
#
# Locations (per docs/plans/rootfs-github-release-no-apk-bloat.md §4.1):
#   1. app/src/main/kotlin/.../DistroInstallProfile.kt (SSOT)
#   2. app/src/main/assets/scripts/debian/proot/setup/flux_install.sh
#   3. app/src/main/assets/scripts/chroot/setup_debian13_chroot.sh (debian)
#   4. app/src/main/assets/scripts/chroot/setup_alpine_chroot.sh   (alpine)
#   5. scripts/upload_rootfs_release.sh (release mirror)
#
# No NEW hand-maintained pin list is allowed; sha256sums.txt on the release is
# generated from files by the upload script.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KOTLIN="$ROOT/app/src/main/kotlin/com/ivarna/fluxlinux/core/install/DistroInstallProfile.kt"
FLUX_INSTALL="$ROOT/app/src/main/assets/scripts/debian/proot/setup/flux_install.sh"
DEBIAN_CHROOT="$ROOT/app/src/main/assets/scripts/chroot/setup_debian13_chroot.sh"
ALPINE_CHROOT="$ROOT/app/src/main/assets/scripts/chroot/setup_alpine_chroot.sh"
UPLOAD="$ROOT/scripts/upload_rootfs_release.sh"

FAIL=0

# --- parse helpers -----------------------------------------------------------

# Kotlin: pair `const val X_ROOTFS_NAME = "name"` with the following
# `const val X_ROOTFS_SHA256 = "hex"`.
parse_kotlin() {
  awk '
    /const val [A-Z_]+ROOTFS_NAME = "/ {
      n = $0; sub(/^.*ROOTFS_NAME = "/, "", n); sub(/".*$/, "", n)
      name = n; next
    }
    /const val [A-Z_]+ROOTFS_SHA256 =/ {
      pending = 1; next
    }
    pending && /^[[:space:]]*"[0-9a-f]{64}"/ {
      sha = $0; sub(/^[[:space:]]*"/, "", sha); sub(/".*$/, "", sha)
      if (name != "") { print name "|" sha }
      name = ""; pending = 0
    }
  ' "$KOTLIN"
}

# flux_install.sh: sequential ROOTFS_NAME / ROOTFS_SHA256 pairs per case block.
parse_flux_install() {
  awk '
    /ROOTFS_NAME="\$\{FLUX_ROOTFS_NAME:-/ {
      n = $0; sub(/^.*ROOTFS_NAME="\$\{FLUX_ROOTFS_NAME:-/, "", n); sub(/\}".*$/, "", n)
      names[++cnt] = n
    }
    /ROOTFS_SHA256="\$\{FLUX_ROOTFS_SHA256:-/ {
      s = $0; sub(/^.*ROOTFS_SHA256="\$\{FLUX_ROOTFS_SHA256:-/, "", s); sub(/\}".*$/, "", s)
      shas[++cnt2] = s
    }
    END { for (i = 1; i <= cnt; i++) if (names[i] != "" && shas[i] != "") print names[i] "|" shas[i] }
  ' "$FLUX_INSTALL"
}

# chroot scripts: ROOTFS_NAME="name" + ROOTFS_SHA256="hex" / ${FLUX_ROOTFS_SHA256:-hex}.
parse_chroot_script() {
  local f="$1"
  awk '
    /^ROOTFS_NAME="/ {
      n = $0; sub(/^ROOTFS_NAME="/, "", n); sub(/".*$/, "", n); name = n
    }
    /^ROOTFS_SHA256="/ {
      s = $0
      sub(/^ROOTFS_SHA256="\$\{FLUX_ROOTFS_SHA256:-/, "", s)
      sub(/^ROOTFS_SHA256="/, "", s)
      sub(/\}".*$/, "", s)
      if (name != "") print name "|" s
      name = ""
    }
  ' "$f"
}

# upload script: PINS entries "name|sha".
parse_upload() {
  awk '
    /^  "[a-z0-9_.]+(\.tar\.xz|\.tar\.gz)\|[0-9a-f]{64}"$/ {
      s = $0
      sub(/^[[:space:]]*"/, "", s); sub(/"[[:space:]]*$/, "", s)
      print s
    }
  ' "$UPLOAD"
}

# --- collect ------------------------------------------------------------------

kotlin_pins="$(parse_kotlin)"
flux_pins="$(parse_flux_install)"
debian_pin="$(parse_chroot_script "$DEBIAN_CHROOT")"
alpine_pin="$(parse_chroot_script "$ALPINE_CHROOT")"
upload_pins="$(parse_upload)"

echo "=== rootfs pin cross-check (SSOT: DistroInstallProfile.kt) ==="

check_pairs() {
  local label="$1" got="$2" want="$3"
  if diff -q <(printf '%s\n' "$want" | sort) <(printf '%s\n' "$got" | sort) >/dev/null; then
    echo "[OK] $label: $(printf '%s' "$got" | grep -c '') pins match SSOT"
  else
    echo "[FAIL] $label differs from Kotlin SSOT:" >&2
    diff <(printf '%s\n' "$want" | sort) <(printf '%s\n' "$got" | sort) >&2 || true
    FAIL=1
  fi
}

echo "SSOT entries: $(printf '%s' "$kotlin_pins" | grep -c '')"
if [ "$(printf '%s' "$kotlin_pins" | grep -c '')" -ne 12 ]; then
  echo "[FAIL] Kotlin SSOT must have exactly 12 rootfs pins" >&2
  FAIL=1
fi

check_pairs "flux_install.sh" "$flux_pins" "$kotlin_pins"
check_pairs "scripts/upload_rootfs_release.sh" "$upload_pins" "$kotlin_pins"

# Dedicated chroot scripts only pin their own distro.
debian_ssot="$(printf '%s\n' "$kotlin_pins" | grep '^debian_13_rootfs.tar.xz|')"
alpine_ssot="$(printf '%s\n' "$kotlin_pins" | grep '^alpine_3.24_rootfs.tar.gz|')"
[ "$debian_pin" = "$debian_ssot" ] || {
  echo "[FAIL] setup_debian13_chroot.sh pin mismatch: got '$debian_pin' want '$debian_ssot'" >&2
  FAIL=1
}
[ "$alpine_pin" = "$alpine_ssot" ] || {
  echo "[FAIL] setup_alpine_chroot.sh pin mismatch: got '$alpine_pin' want '$alpine_ssot'" >&2
  FAIL=1
}
echo "[OK] setup_debian13_chroot.sh: $debian_pin"
echo "[OK] setup_alpine_chroot.sh: $alpine_pin"

# Alpine must never use the legacy .minirootfs runtime name (D8).
if printf '%s' "$kotlin_pins" | grep -q '\.minirootfs|'; then
  echo "[FAIL] .minirootfs pin in Kotlin SSOT (D8)" >&2
  FAIL=1
fi
if grep 'minirootfs' "$UPLOAD" | grep -v '^[[:space:]]*#' | grep -qv 'historical'; then
  echo "[FAIL] non-comment .minirootfs reference in upload script (D8)" >&2
  FAIL=1
fi

if [ "$FAIL" -ne 0 ]; then
  echo "FAIL"
  exit 1
fi
echo "PASS"
