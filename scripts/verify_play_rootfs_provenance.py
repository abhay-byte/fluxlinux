#!/usr/bin/env python3
"""Validate the twelve provisioned Play rootfs archives and provenance."""

from __future__ import annotations

import argparse
import hashlib
import json
import posixpath
import tarfile
from pathlib import Path

REQUIRED = (
    "bin/sh",
    "usr/bin/startxfce4",
    "usr/bin/dbus-daemon",
    "usr/bin/xfce4-session",
    "usr/bin/xfwm4",
    "usr/bin/xfce4-panel",
    "usr/bin/xfdesktop",
    "usr/bin/thunar",
    "etc/fluxlinux/play-baseline-v1",
)

PLAY_RELEASE_DISTRO_IDS = {
    "debian", "alpine", "ubuntu", "kali", "archlinux", "manjaro", "chimera"
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def normalized_members(tar: tarfile.TarFile) -> dict[str, tarfile.TarInfo]:
    return {
        member.name.lstrip("./").rstrip("/"): member
        for member in tar.getmembers()
    }


def contains_path(members: dict[str, tarfile.TarInfo], path: str, seen: set[str] | None = None) -> bool:
    """Resolve directory symlinks such as Debian's /bin -> usr/bin."""
    seen = set() if seen is None else seen
    path = path.lstrip("/").rstrip("/")
    if path in members:
        return True
    if path in seen:
        return False
    seen.add(path)
    parts = path.split("/")
    for index in range(1, len(parts) + 1):
        prefix = "/".join(parts[:index])
        member = members.get(prefix)
        if member is None or not member.issym():
            continue
        target = member.linkname
        if target.startswith("/"):
            target = target.lstrip("/")
        else:
            target = posixpath.normpath(posixpath.join(posixpath.dirname(prefix), target))
        remainder = "/".join(parts[index:])
        return contains_path(members, "/".join(part for part in (target, remainder) if part), seen)
    return False


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=Path("scripts/play_rootfs/manifests.json"))
    parser.add_argument("--payload-dir", type=Path, required=True)
    parser.add_argument(
        "--release-only",
        action="store_true",
        help="validate only the seven distro payloads selected for Play v2.0",
    )
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    failures: list[str] = []
    entries = [
        entry for entry in manifest["distros"]
        if not args.release_only or entry["id"] in PLAY_RELEASE_DISTRO_IDS
    ]
    for entry in entries:
        archive = args.payload_dir / entry["archive"]
        sidecar = archive.with_name(archive.name + ".provenance.json")
        if not archive.is_file():
            failures.append(f"{entry['id']}: missing archive {archive}")
            continue
        if not sidecar.is_file():
            failures.append(f"{entry['id']}: missing provenance sidecar")
            continue
        provenance = json.loads(sidecar.read_text(encoding="utf-8"))
        if provenance.get("archiveSha256") != sha256(archive):
            failures.append(f"{entry['id']}: archive hash does not match provenance")
        expected_input_sha256 = entry.get("inputSha256", entry["sha256"])
        recorded_input_sha256 = provenance.get("inputArchiveSha256")
        if recorded_input_sha256 is None and (
            provenance.get("upstreamSource") == entry.get("inputSource")
        ):
            # Older Alpine sidecars used the pinned input as upstreamSource
            # before the explicit input provenance fields were added.
            recorded_input_sha256 = provenance.get("upstreamSha256")
        if recorded_input_sha256 != expected_input_sha256:
            failures.append(f"{entry['id']}: input archive hash is missing or mismatched")
        if provenance.get("architecture") != "aarch64":
            failures.append(f"{entry['id']}: architecture is not aarch64")
        if provenance.get("runtimeNetworkRequired") is not False:
            failures.append(f"{entry['id']}: runtimeNetworkRequired is not false")
        has_upstream = provenance.get("upstreamSource") and provenance.get("upstreamSha256")
        legacy_source = provenance.get("inputSource") or entry.get("inputSource")
        legacy_sha256 = provenance.get("inputSourceSha256") or entry.get("inputSourceSha256")
        has_legacy_input = legacy_source and legacy_sha256 == expected_input_sha256
        if not has_upstream and not has_legacy_input:
            failures.append(
                f"{entry['id']}: upstream source/hash or exact legacy input source/hash is incomplete"
            )
        try:
            with tarfile.open(archive, "r:*") as tar:
                members = normalized_members(tar)
        except tarfile.TarError as error:
            failures.append(f"{entry['id']}: invalid archive: {error}")
            continue
        for required in REQUIRED:
            if not contains_path(members, required):
                failures.append(f"{entry['id']}: missing {required}")
        package_db = entry["packageDb"].lstrip("/").rstrip("/")
        if package_db not in members and not any(name.startswith(package_db + "/") for name in members):
            failures.append(f"{entry['id']}: missing package database {entry['packageDb']}")
        if entry["id"] == "alpine" and provenance.get("packageManager") != "apk":
            failures.append("alpine: provenance package manager is not apk")
        print(f"{entry['id']}: {'FAIL' if any(entry['id'] in item for item in failures) else 'PASS'}")
    if failures:
        print("\n".join(f"FAIL: {failure}" for failure in failures))
        return 1
    scope = "seven Play release" if args.release_only else "all 12"
    print(f"PASS: {scope} rootfs archives, markers, package databases, and provenance")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
