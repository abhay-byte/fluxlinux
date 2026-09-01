#!/usr/bin/env python3
"""Enforce the fast Play release payload size safety margins."""

from __future__ import annotations

import argparse
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
FEATURE_LIMIT_BYTES = 480 * 1024 * 1024
TOTAL_LIMIT_BYTES = 3_500_000_000
DEFAULT_BASE_ESTIMATE_BYTES = 500 * 1024 * 1024


def payload_bytes(repo_root: Path, module: str) -> int:
    payload_root = (
        repo_root / module / "src" / "zenithblue" / "assets" / "payloads" / module
    )
    if not payload_root.is_dir():
        raise SystemExit(f"ERROR: missing staged Play payload directory {payload_root}")
    return sum(
        path.stat().st_size
        for path in payload_root.rglob("*")
        if path.is_file()
    )


def mib(value: int) -> float:
    return value / (1024 * 1024)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument(
        "--base-estimate-bytes",
        type=int,
        default=DEFAULT_BASE_ESTIMATE_BYTES,
        help="conservative base-module estimate included in the cumulative gate",
    )
    args = parser.parse_args()
    repo_root = args.repo_root.resolve()

    sizes = {module: payload_bytes(repo_root, module) for module in RELEASE_MODULES}
    total_features = sum(sizes.values())
    total = args.base_estimate_bytes + total_features

    print("module | payload bytes | MiB")
    print("---|---:|---:")
    for module in RELEASE_MODULES:
        print(f"{module} | {sizes[module]} | {mib(sizes[module]):.2f}")
    print(f"base (estimate) | {args.base_estimate_bytes} | {mib(args.base_estimate_bytes):.2f}")
    print(f"total (base + features) | {total} | {mib(total):.2f}")

    failures = [
        f"{module} is {size} bytes, at or above the {FEATURE_LIMIT_BYTES}-byte feature limit"
        for module, size in sizes.items()
        if size >= FEATURE_LIMIT_BYTES
    ]
    if total >= TOTAL_LIMIT_BYTES:
        failures.append(
            f"cumulative estimate is {total} bytes, at or above the {TOTAL_LIMIT_BYTES}-byte limit"
        )
    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        return 1
    print("PASS: Play feature and cumulative payload size safety gates")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
