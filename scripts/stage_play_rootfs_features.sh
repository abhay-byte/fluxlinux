#!/usr/bin/env bash
# scripts/stage_play_rootfs_features.sh
# Deterministically verifies existing rootfs archives against DistroInstallProfile SSOT
# and stages them into Play Dynamic Feature asset directories.
# Fails closed if any payload is missing or SHA mismatches.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KOTLIN_SSOT="$ROOT/app/src/main/kotlin/com/ivarna/fluxlinux/core/install/DistroInstallProfile.kt"

# Default source directories to check in order:
# 1. First argument if provided
# 2. $ROOT/assets/rootfs
# 3. /home/abhaybyte/repos/fluxlinux/assets/rootfs
CUSTOM_SRC="${1:-}"

find_rootfs_file() {
    local fname="$1"
    if [ -n "$CUSTOM_SRC" ] && [ -f "$CUSTOM_SRC/$fname" ]; then
        echo "$CUSTOM_SRC/$fname"
        return 0
    fi
    if [ -f "$ROOT/assets/rootfs/$fname" ]; then
        echo "$ROOT/assets/rootfs/$fname"
        return 0
    fi
    if [ -f "/home/abhaybyte/repos/fluxlinux/assets/rootfs/$fname" ]; then
        echo "/home/abhaybyte/repos/fluxlinux/assets/rootfs/$fname"
        return 0
    fi
    return 1
}

# 12 in-scope Play Dynamic Features:
# distro_name | module_dir | rootfs_filename | expected_sha256
FEATURES=(
    "debian|distro_debian|debian_13_rootfs.tar.xz|13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803"
    "alpine|distro_alpine|alpine_3.24_rootfs.tar.gz|f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259"
    "ubuntu|distro_ubuntu|ubuntu_26.04_rootfs.tar.xz|e648a5302dd273c476e5658e652f88d1e66ece69b487431521c5caef4b960efc"
    "kali|distro_kali|kali_2026_2_rootfs.tar.xz|01c48a29ebb543954ef200e766076a143cf42744760d7ccdc31683a19f670689"
    "archlinux|distro_arch|archlinux_arm_rootfs.tar.xz|40209ef6318d3aad732299d46ce224c6a0ecded80b6f8091f5e38b40fa031d75"
    "manjaro|distro_manjaro|manjaro_arm_rootfs.tar.xz|b7339bcc289e8bbb40d1ffdc6ece4404865383d14d4b7f0fb83aa81e01720156"
    "chimera|distro_chimera|chimera_20251220_rootfs.tar.xz|0900e3f2554faaf005c14a6850596dadae1e7d8a996138180eebb0b4694a4a6c"
    "fedora|distro_fedora|fedora_44_rootfs.tar.xz|2d89fe437973e4596d56bf096f71c182d273942a307e7e1e51462dba43db1bd4"
    "void|distro_void|void_20250202_rootfs.tar.xz|01a30f17ae06d4d5b322cd579ca971bc479e02cc284ec1e5a4255bea6bac3ce6"
    "opensuse|distro_opensuse|opensuse_tumbleweed_rootfs.tar.xz|bdcb8522a9672cfa513081313b2788f8844340e800918d16a2154e4ed785a12a"
    "deepin|distro_deepin|deepin_25_rootfs.tar.xz|2c7abfe859db36249459251d0b29f853e9ffb79cd1b42c7661e997ba99193698"
    "parrot|distro_parrot|parrot_7.2_rootfs.tar.xz|49f4c2899ef9574cc3b0d9aaa6eaff38c4b32a9ac1abea2faec73cfbaf8094d4"
)

echo "=== Staging Play Dynamic Feature RootFS Payloads ==="

for item in "${FEATURES[@]}"; do
    IFS="|" read -r distro module fname expected_sha <<< "$item"
    
    # 1. Verify SSOT pin from DistroInstallProfile.kt matches our expected_sha
    if ! grep -Fq "$expected_sha" "$KOTLIN_SSOT"; then
        echo "[FAIL] Expected SHA $expected_sha for $fname not found in DistroInstallProfile.kt" >&2
        exit 1
    fi

    # 2. Locate source file
    src_file="$(find_rootfs_file "$fname" || true)"
    if [ -z "$src_file" ] || [ ! -f "$src_file" ]; then
        echo "[STOP] Missing required rootfs file: $fname" >&2
        echo "Please place it in $ROOT/assets/rootfs/ or provide path as argument." >&2
        exit 1
    fi

    # 3. Verify exact SHA-256 of source file
    actual_sha="$(sha256sum "$src_file" | awk '{print $1}')"
    if [ "$actual_sha" != "$expected_sha" ]; then
        echo "[STOP] SHA-256 mismatch for $src_file" >&2
        echo "  Expected: $expected_sha" >&2
        echo "  Actual:   $actual_sha" >&2
        exit 1
    fi

    # 4. Copy to target destination
    dest_dir="$ROOT/$module/src/zenithblue/assets/payloads/$module"
    mkdir -p "$dest_dir"
    dest_file="$dest_dir/$fname"

    if [ ! -f "$dest_file" ] || [ "$(sha256sum "$dest_file" | awk '{print $1}')" != "$expected_sha" ]; then
        echo "  [STAGE] $fname -> $dest_dir/"
        cp "$src_file" "$dest_file"
    else
        echo "  [UP-TO-DATE] $dest_file"
    fi

    # Write or keep provenance.json
    size_bytes=$(stat -c%s "$dest_file")
    cat > "$dest_dir/provenance.json" <<EOF
{
  "distroId": "$distro",
  "archiveFileName": "$fname",
  "archiveSha256": "$expected_sha",
  "compressedSize": $size_bytes,
  "source": "existing pinned FluxLinux rootfs"
}
EOF
done

echo "=== All 12 Play dynamic feature rootfs payloads successfully verified and staged ==="
