#!/usr/bin/env python3
"""Verify the Zenithblue fast-release AAB module and payload contract."""

from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from pathlib import Path


RELEASE_MODULES = (
    "runtime_host",
    "distro_debian",
    "distro_alpine",
    "distro_ubuntu",
    "distro_kali",
    "distro_arch",
    "distro_manjaro",
    "distro_chimera",
)
DEFERRED_MODULES = (
    "distro_fedora",
    "distro_void",
    "distro_opensuse",
    "distro_deepin",
    "distro_parrot",
)
PLAY_HASHES = {
    "distro_debian": "4285f19f4b806f74a97269d692958c8c085e107ea370709311790b86712bf638",
    "distro_alpine": "88714e4cc1637cdad5916200c5ac5b72c506506dd33166a12a0a58635618724c",
    "distro_ubuntu": "fd8481763ac0b0f4757a1a3ac51fbc432be52b75c193b194b16dd1f63fb19bd9",
    "distro_kali": "562696884422db47c19db561004b6981f9578677cb627ae3d716ad2979e8febe",
    "distro_arch": "fb5757ab558b420ca0a5bef3f5a6f9259d3456a3b37f60be052cf221d19de9ca",
    "distro_manjaro": "59ef6613c1e9e3ea63660ba893b49c100d2eb770163759f6b044dd7c75d88e0a",
    "distro_chimera": "d7b6ce933b5c0e4ea631158c87910915af8d5ae99160d212ee188087e70a1d91",
}
FEATURE_LIMIT_BYTES = 480 * 1024 * 1024
TOTAL_LIMIT_BYTES = 3_500_000_000


def sha256_entry(archive: zipfile.ZipFile, name: str) -> str:
    digest = hashlib.sha256()
    with archive.open(name) as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def module_roots(names: list[str]) -> set[str]:
    return {
        name.split("/", 1)[0]
        for name in names
        if "/" in name and not name.startswith("BUNDLE-METADATA/")
    }


def verify_aab(aab: Path) -> None:
    failures: list[str] = []
    expected_modules = {"base", *RELEASE_MODULES}
    with zipfile.ZipFile(aab) as archive:
        names = archive.namelist()
        roots = module_roots(names)
        if roots != expected_modules:
            failures.append(
                f"AAB modules differ: expected {sorted(expected_modules)}, got {sorted(roots)}"
            )
        for deferred in DEFERRED_MODULES:
            if deferred in roots:
                failures.append(f"deferred module is present: {deferred}")

        base_entries = [name for name in names if name.startswith("base/")]
        for forbidden in ("rootfs", "bootstrap", "loader.apk", "loader.bin"):
            if any(forbidden in name for name in base_entries):
                failures.append(f"base contains forbidden payload marker: {forbidden}")
        if any(name.endswith("loader.apk") or name.endswith("loader.bin") for name in names):
            failures.append("AAB contains loader.apk or loader.bin")

        for module in RELEASE_MODULES:
            manifest_name = f"{module}/manifest/AndroidManifest.xml"
            if manifest_name not in names:
                failures.append(f"missing manifest for {module}")
            elif b"on-demand" not in archive.read(manifest_name):
                failures.append(f"{module} is not marked on-demand")

            prefix = f"{module}/assets/payloads/{module}/"
            payloads = [
                name for name in names
                if name.startswith(prefix) and not name.endswith("provenance.json")
            ]
            if module == "runtime_host":
                expected_payload_name = f"{prefix}bootstrap.tar"
            elif module == "distro_alpine":
                expected_payload_name = f"{prefix}alpine_3.24_rootfs.minirootfs"
            else:
                expected_payload_name = next(
                    (name for name in payloads if name.rsplit("/", 1)[-1].endswith((".tar", ".tar.xz"))),
                    "",
                )
            if len(payloads) != 1 or payloads[0] != expected_payload_name:
                failures.append(f"{module} payload files are unexpected: {payloads}")
                continue

            provenance_name = f"{prefix}provenance.json"
            if provenance_name not in names:
                failures.append(f"missing provenance for {module}")
                continue
            provenance = json.loads(archive.read(provenance_name))
            digest = sha256_entry(archive, expected_payload_name)
            expected_digest = PLAY_HASHES.get(module, provenance.get("archiveSha256"))
            if module != "runtime_host" and digest != expected_digest:
                failures.append(f"{module} payload SHA-256 mismatch: {digest}")
            if module != "runtime_host" and provenance.get("archiveSha256") != expected_digest:
                failures.append(f"{module} provenance SHA-256 does not match Play registry")
            if provenance.get("runtimeNetworkRequired") is not False:
                failures.append(f"{module} permits runtime network access")

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        raise SystemExit(1)
    print(f"PASS: Zenithblue AAB contains base + {len(RELEASE_MODULES)} on-demand release modules")


def verify_apks(apks: Path) -> None:
    failures: list[str] = []
    total = 0
    logical_module_sizes: dict[str, int] = {}
    print("split | packaged bytes | MiB")
    print("---|---:|---:")
    with zipfile.ZipFile(apks) as archive:
        entries = [
            info for info in archive.infolist()
            if info.filename.startswith("splits/") and info.filename.endswith(".apk")
        ]
        if not entries:
            raise SystemExit("FAIL: local-testing APKS contains no split APKs")
        for info in sorted(entries, key=lambda item: item.filename):
            total += info.file_size
            print(f"{info.filename} | {info.file_size} | {info.file_size / (1024 * 1024):.2f}")
            if info.file_size >= FEATURE_LIMIT_BYTES:
                failures.append(f"generated split is at or above the feature limit: {info.filename}")
            stem = info.filename.rsplit("/", 1)[-1][:-4]
            if "-master" in stem:
                module = stem.split("-master", 1)[0]
                logical_module_sizes[module] = max(
                    logical_module_sizes.get(module, 0), info.file_size
                )
    print(f"total generated splits | {total} | {total / (1024 * 1024):.2f}")
    logical_total = sum(logical_module_sizes.values())
    print("logical module master max | packaged bytes | MiB")
    print("---|---:|---:")
    for module, size in sorted(logical_module_sizes.items()):
        print(f"{module} | {size} | {size / (1024 * 1024):.2f}")
    print(f"logical module master max total | {logical_total} | {logical_total / (1024 * 1024):.2f}")
    if logical_total >= TOTAL_LIMIT_BYTES:
        failures.append("logical generated module total is at or above the cumulative safety limit")
    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        raise SystemExit(1)
    print(
        "PASS: bundletool-generated local-testing split sizes "
        "(duplicate local-testing entries collapsed by logical module)"
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aab", type=Path, required=True)
    parser.add_argument("--apks", type=Path)
    args = parser.parse_args()
    verify_aab(args.aab)
    if args.apks:
        verify_apks(args.apks)


if __name__ == "__main__":
    main()
