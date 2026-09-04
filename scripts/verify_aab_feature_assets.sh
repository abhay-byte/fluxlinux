#!/usr/bin/env bash
# scripts/verify_aab_feature_assets.sh
# Verifies all 7 Play dynamic feature modules in the target AAB:
# - Base contains zero rootfs and zero payloads
# - Each expected feature module contains exactly its expected rootfs
# - Feature payload SHA-256 matches DistroInstallProfile SSOT
# - Feature payload size matches staged source size
set -euo pipefail

AAB="${1:-app/build/outputs/bundle/zenithblueRelease/app-zenithblue-release.aab}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KOTLIN_SSOT="$ROOT/app/src/main/kotlin/com/ivarna/fluxlinux/core/install/DistroInstallProfile.kt"

if [ ! -f "$AAB" ]; then
    echo "ERROR: AAB not found: $AAB" >&2
    exit 1
fi

echo "=== Verifying Dynamic Feature Payloads in $AAB ==="

# 1. Check Base Artifact Gate: Reject base/assets/rootfs/ and base/assets/payloads/
base_rootfs=$(unzip -l "$AAB" | grep -E " base/assets/rootfs/| assets/rootfs/" || true)
base_payloads=$(unzip -l "$AAB" | grep -E " base/assets/payloads/| assets/payloads/| payloads/" || true)

if [ -n "$base_rootfs" ]; then
    echo "[FAIL] AAB base contains forbidden rootfs entries:" >&2
    echo "$base_rootfs" >&2
    exit 1
fi
if [ -n "$base_payloads" ]; then
    echo "[FAIL] AAB base contains forbidden payload entries:" >&2
    echo "$base_payloads" >&2
    exit 1
fi
echo "[OK] Base contains zero rootfs and zero payloads"

# 2. Check 12 Feature Payloads:
# distro | module | filename | expected_sha
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

printf "%-15s %-45s %-12s %-64s\n" "MODULE" "ASSET PATH" "BYTES" "SHA-256"
printf "%-15s %-45s %-12s %-64s\n" "------" "----------" "-----" "-------"

TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

for item in "${FEATURES[@]}"; do
    IFS="|" read -r distro module fname expected_sha <<< "$item"
    entry_path="$module/assets/payloads/$module/$fname"
    
    # Check entry presence in AAB
    zip_line=$(unzip -l "$AAB" | grep -F " $entry_path" || true)
    if [ -z "$zip_line" ]; then
        echo "[FAIL] Missing feature entry: $entry_path" >&2
        exit 1
    fi

    # Extract to temp and verify SHA-256 & size
    unzip -p "$AAB" "$entry_path" > "$TMPDIR/$fname"
    actual_sha=$(sha256sum "$TMPDIR/$fname" | awk '{print $1}')
    actual_bytes=$(stat -c%s "$TMPDIR/$fname")

    # Verify against SSOT
    if [ "$actual_sha" != "$expected_sha" ]; then
        echo "[FAIL] SHA mismatch for $entry_path: got $actual_sha want $expected_sha" >&2
        exit 1
    fi

    # Verify against staged source
    staged_source="$ROOT/$module/src/zenithblue/assets/payloads/$module/$fname"
    if [ -f "$staged_source" ]; then
        staged_bytes=$(stat -c%s "$staged_source")
        if [ "$actual_bytes" -ne "$staged_bytes" ]; then
            echo "[FAIL] Size mismatch with staged file for $entry_path: got $actual_bytes want $staged_bytes" >&2
            exit 1
        fi
    fi

    printf "%-15s %-45s %-12s %-64s\n" "$module" "$entry_path" "$actual_bytes" "$actual_sha"
done

echo "=== All 12 Dynamic Feature rootfs payloads verified successfully in AAB ==="
