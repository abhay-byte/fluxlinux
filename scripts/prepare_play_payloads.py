#!/usr/bin/env python3
"""Stage byte-exact Play Feature Delivery payloads and provenance.

The source archives are maintainer/CI inputs and are intentionally not tracked
in git. This script copies them into ignored dynamic-feature asset directories,
computes the final archive hash/size, measures tar member bytes without
extracting the archive, and writes a manifest beside each payload.

Example:
  SOURCE_DATE_EPOCH=1755129600 \
    python3 scripts/prepare_play_payloads.py \
      --source-root /path/to/rootfs \
      --host-source native/bootstrap/com.zenithblue.fluxlinux/bootstrap.tar

The script never downloads payloads. Missing inputs, hash drift, or an unknown
source record fail closed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import tarfile
from datetime import datetime, timezone
from pathlib import Path
from shutil import copyfile, rmtree


ROOT = Path(__file__).resolve().parents[1]
PAYLOAD_VERSION = "2.0.0"
ARCHITECTURE = "arm64-v8a"
SCHEMA_VERSION = 1


# Source records are deliberately descriptive where the original upstream
# URL/checksum was not captured in the existing release notes. A missing
# upstream checksum is represented as null, never invented.
ROOTFS = [
    ("distro_debian", "rootfs.debian", "debian", "debian_13_rootfs.tar.xz", "13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803", 50 * 1024 * 1024, "Debian 13 arm64 maintainer-supplied Flux rootfs; source URL not recorded", None, "docs/playstore/v2_0_compliance_roadmap.md"),
    ("distro_alpine", "rootfs.alpine", "alpine", "alpine_3.24_rootfs.tar.gz", "f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259", 1 * 1024 * 1024, "Official Alpine minirootfs 3.24.1; source URL not recorded in the archive record", None, "docs/distro/alpine.md"),
    ("distro_fedora", "rootfs.fedora", "fedora", "fedora_44_rootfs.tar.xz", "2d89fe437973e4596d56bf096f71c182d273942a307e7e1e51462dba43db1bd4", 20 * 1024 * 1024, "Fedora Container Base Generic-Minimal 44 aarch64 OCI input; source URL not recorded", None, "docs/plan/fedora-void-opensuse.md"),
    ("distro_void", "rootfs.void", "void", "void_20250202_rootfs.tar.xz", "01a30f17ae06d4d5b322cd579ca971bc479e02cc284ec1e5a4255bea6bac3ce6", 20 * 1024 * 1024, "Void Linux glibc aarch64 2025-02-02 maintainer-supplied rootfs; source URL not recorded", None, "docs/plan/fedora-void-opensuse.md"),
    ("distro_opensuse", "rootfs.opensuse", "opensuse", "opensuse_tumbleweed_rootfs.tar.xz", "bdcb8522a9672cfa513081313b2788f8844340e800918d16a2154e4ed785a12a", 15 * 1024 * 1024, "openSUSE Tumbleweed aarch64 20251127 maintainer-supplied rootfs; source URL not recorded", None, "docs/plan/fedora-void-opensuse.md"),
    ("distro_chimera", "rootfs.chimera", "chimera", "chimera_20251220_rootfs.tar.xz", "0900e3f2554faaf005c14a6850596dadae1e7d8a996138180eebb0b4694a4a6c", 4 * 1024 * 1024, "Chimera Linux aarch64 2025-12-20 bootstrap input; source filename recorded in release notes", "65f738dad84c8d81dc0e17b686a6e1eaf88820d7555ea920ca906f83a7e962b3", "docs/plan/deepin-chimera-manjaro.md"),
    ("distro_deepin", "rootfs.deepin", "deepin", "deepin_25_rootfs.tar.xz", "2c7abfe859db36249459251d0b29f853e9ffb79cd1b42c7661e997ba99193698", 40 * 1024 * 1024, "Deepin Docker rootfs arm64 input; source filename recorded in release notes", "f11297d18322648b8182213d29ef8b841bc023fecfba034188dae22e16412ee6", "docs/plan/deepin-chimera-manjaro.md"),
    ("distro_manjaro", "rootfs.manjaro", "manjaro", "manjaro_arm_rootfs.tar.xz", "b7339bcc289e8bbb40d1ffdc6ece4404865383d14d4b7f0fb83aa81e01720156", 80 * 1024 * 1024, "Manjaro ARM aarch64 input; source filename recorded in release notes", "ce6701a0ddea623fb2752179666f426d9fd1c04805ace73d35a3fc1a314da1ca", "docs/plan/deepin-chimera-manjaro.md"),
    ("distro_ubuntu", "rootfs.ubuntu", "ubuntu", "ubuntu_26.04_rootfs.tar.xz", "e648a5302dd273c476e5658e652f88d1e66ece69b487431521c5caef4b960efc", 15 * 1024 * 1024, "Ubuntu Resolute arm64 input; source filename recorded in release notes", "e9dfcbf8763371965597edcb351eaa7daacfb0805bb9ae9c8d6479a0b25bf928", "docs/plan/ubuntu-kali-parrot-arch.md"),
    ("distro_kali", "rootfs.kali", "kali", "kali_2026_2_rootfs.tar.xz", "01c48a29ebb543954ef200e766076a143cf42744760d7ccdc31683a19f670689", 40 * 1024 * 1024, "Kali NetHunter minimal arm64 input, flattened for Flux; source filename recorded in release notes", "d6403a5da175df325611d23af4b92330856059c45454eced7f4cdf3ca6df2e4e", "docs/plan/ubuntu-kali-parrot-arch.md"),
    ("distro_parrot", "rootfs.parrot", "parrot", "parrot_7.2_rootfs.tar.xz", "49f4c2899ef9574cc3b0d9aaa6eaff38c4b32a9ac1abea2faec73cfbaf8094d4", 30 * 1024 * 1024, "Parrot Security arm64 input, flattened for Flux; source filename recorded in release notes", "8a486c8635918de6cebc3b339265c4cea73cb9d73f709d56d98e487769f78582", "docs/plan/ubuntu-kali-parrot-arch.md"),
    ("distro_arch", "rootfs.arch", "archlinux", "archlinux_arm_rootfs.tar.xz", "40209ef6318d3aad732299d46ce224c6a0ecded80b6f8091f5e38b40fa031d75", 40 * 1024 * 1024, "Arch Linux ARM aarch64 slim rootfs supplied to Flux; source URL not recorded", None, "docs/plan/ubuntu-kali-parrot-arch.md"),
]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def uncompressed_size(path: Path) -> int:
    total = 0
    with tarfile.open(path, mode="r:*") as archive:
        for member in archive:
            if member.isfile():
                total += member.size
    return total


def source_commit() -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
        ).strip()
    except (OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(f"ERROR: cannot determine source commit: {error}")


def build_date(value: str | None, commit: str) -> str:
    if value:
        try:
            epoch = int(value)
        except ValueError as error:
            raise SystemExit(f"ERROR: --build-date must be SOURCE_DATE_EPOCH seconds: {error}")
        return datetime.fromtimestamp(epoch, timezone.utc).isoformat().replace("+00:00", "Z")
    env_epoch = os.environ.get("SOURCE_DATE_EPOCH")
    if env_epoch:
        return build_date(env_epoch, commit)
    # Fall back to the source commit timestamp so ordinary local builds do not
    # rewrite provenance on every invocation. Release/CI may still override
    # this with SOURCE_DATE_EPOCH when the archive is assembled elsewhere.
    try:
        epoch = subprocess.check_output(
            ["git", "show", "-s", "--format=%ct", commit], cwd=ROOT, text=True
        ).strip()
        return build_date(epoch, commit)
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        raise SystemExit(f"ERROR: cannot determine deterministic build date: {error}")


def manifest_for(
    payload_id: str,
    distro_id: str,
    archive_name: str,
    digest: str,
    compressed: int,
    expanded: int,
    upstream_source: str,
    upstream_checksum: str | None,
    source_record: str,
    commit: str,
    date: str,
) -> dict[str, object]:
    return {
        "schemaVersion": SCHEMA_VERSION,
        "payloadId": payload_id,
        "payloadVersion": PAYLOAD_VERSION,
        "distroId": distro_id,
        "architecture": ARCHITECTURE,
        "archiveFileName": archive_name,
        "archiveSha256": digest,
        "compressedSize": compressed,
        "uncompressedSize": expanded,
        "upstreamSource": upstream_source,
        "upstreamChecksum": upstream_checksum,
        "sourceCommit": commit,
        "buildScript": "scripts/prepare_play_payloads.py",
        "buildDate": date,
        "fluxCustomizations": (
            "FluxLinux host-prefix package SSOT and app-id rewrite; staged byte-for-byte"
            if distro_id == "host"
            else "FluxLinux distro family setup, common guest setup, and XFCE customization"
        ),
        "sourceRecord": source_record,
    }


def verify_one(path: Path, expected_hash: str, min_bytes: int, expected_name: str) -> tuple[str, int, int]:
    if not path.is_file():
        raise SystemExit(f"ERROR: missing payload input {path}")
    size = path.stat().st_size
    if size <= min_bytes:
        raise SystemExit(f"ERROR: {path} is {size} bytes; expected more than {min_bytes}")
    digest = sha256(path)
    if digest != expected_hash:
        raise SystemExit(
            f"ERROR: SHA-256 mismatch for {expected_name}: expected {expected_hash}, got {digest}"
        )
    try:
        expanded = uncompressed_size(path)
    except (OSError, tarfile.TarError) as error:
        raise SystemExit(f"ERROR: cannot measure tar payload {path}: {error}")
    if expanded <= 0:
        raise SystemExit(f"ERROR: {path} has no regular tar members")
    return digest, size, expanded


def packaged_asset_name(archive_name: str) -> str:
    """Return the filename that aapt2 will preserve byte-for-byte.

    aapt2 treats an asset ending in ``.gz`` as a gzip-compressed asset: it
    inflates the bytes and removes the suffix when it packages the App Bundle.
    Alpine must remain a gzip archive because its provenance/hash and installer
    contract are for the original ``.tar.gz`` bytes, so use a neutral asset
    suffix while retaining the original archive name in provenance.
    """
    if archive_name.endswith(".tar.gz"):
        return f"{archive_name[:-len('.tar.gz')]}.minirootfs"
    return archive_name


def stage_payload(
    source: Path,
    module: str,
    destination_name: str,
    metadata: dict[str, object],
    output_root: Path,
) -> None:
    payload_root = output_root / module / "src" / "zenithblue" / "assets" / "payloads"
    # Staging owns this ignored directory. Removing it first prevents stale
    # flat-layout files from surviving a layout/schema change.
    rmtree(payload_root, ignore_errors=True)
    payload_dir = payload_root / module
    payload_dir.mkdir(parents=True, exist_ok=True)
    destination = payload_dir / destination_name
    copyfile(source, destination)
    os.utime(destination, (0, 0))
    manifest_path = payload_dir / "provenance.json"
    manifest_path.write_text(json.dumps(metadata, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=ROOT / "assets" / "rootfs")
    parser.add_argument(
        "--host-source",
        type=Path,
        default=ROOT / "native" / "bootstrap" / "com.zenithblue.fluxlinux" / "bootstrap.tar",
    )
    parser.add_argument("--output-root", type=Path, default=ROOT)
    parser.add_argument("--source-commit")
    parser.add_argument("--build-date", help="UTC SOURCE_DATE_EPOCH seconds")
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    host_source = args.host_source.resolve()
    output_root = args.output_root.resolve()
    commit = args.source_commit or source_commit()
    date = build_date(args.build_date, commit)

    host_digest, host_size, host_expanded = verify_one(
        host_source,
        "87da10cf99613c00b6841200c29be1d9bcebaf36a2e7c5e312660807e9f965bd",
        50 * 1024 * 1024,
        "bootstrap_com.zenithblue.fluxlinux.v2.tar",
    )
    host_manifest = manifest_for(
        "host_bootstrap_com.zenithblue.fluxlinux",
        "host",
        "bootstrap_com.zenithblue.fluxlinux.v2.tar",
        host_digest,
        host_size,
        host_expanded,
        "FluxLinux Termux package set assembled from tracked package inputs; Worker 04 libattr and embedded-X11 hardening",
        None,
        "scripts/assemble_bootstrap.py and native/package-lists/termux-lib-ssot.txt",
        commit,
        date,
    )
    if not args.verify_only:
        stage_payload(host_source, "runtime_host", "bootstrap.tar", host_manifest, output_root)

    for module, payload_id, distro_id, archive_name, expected_hash, min_bytes, upstream, upstream_hash, record in ROOTFS:
        source = source_root / archive_name
        digest, compressed, expanded = verify_one(source, expected_hash, min_bytes, archive_name)
        metadata = manifest_for(
            payload_id,
            distro_id,
            archive_name,
            digest,
            compressed,
            expanded,
            upstream,
            upstream_hash,
            record,
            commit,
            date,
        )
        if not args.verify_only:
            stage_payload(
                source,
                module,
                packaged_asset_name(archive_name),
                metadata,
                output_root,
            )

    print(f"PASS: verified {len(ROOTFS)} distro payloads + runtime_host")
    if not args.verify_only:
        print(f"PASS: staged zenithblue payload assets under {output_root}")


if __name__ == "__main__":
    main()
