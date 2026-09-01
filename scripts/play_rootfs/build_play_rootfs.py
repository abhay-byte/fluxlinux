#!/usr/bin/env python3
"""Build Play distro rootfs payloads with real package-manager transactions.

The transaction runs in a build-only aarch64 container/binfmt environment.
Nothing in this tool is an Android runtime download path. Missing source
hashes, failed transactions, invalid package databases, and incomplete desktop
images fail closed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "scripts/play_rootfs/manifests.json"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def run(command: list[str], timeout: int | None = None) -> None:
    print("+", " ".join(command), flush=True)
    subprocess.run(command, check=True, timeout=timeout)


def cleanup_stage(stage: Path, runner: str, image: str) -> None:
    shutil.rmtree(stage, ignore_errors=True)
    if stage.exists():
        subprocess.run(
            [runner, "run", "--rm", "-v", f"{stage}:/target", image, "sh", "-eu", "-c",
             "find /target -mindepth 1 -depth -delete"],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        try:
            stage.rmdir()
        except OSError:
            pass


def selected_runner() -> str:
    for candidate in ("docker", "podman"):
        if shutil.which(candidate):
            return candidate
    raise SystemExit("docker or podman is required for aarch64 rootfs builds")


def build_alpine(source: Path, output: Path, image: str) -> None:
    run([
        str(ROOT / "scripts/build_alpine_play_baseline.sh"),
        "--input", str(source),
        "--output", str(output),
        "--provenance", str(output) + ".provenance.json",
        "--image", image,
    ])


GENERIC_CONTAINER = r'''
set -eu
archive=$1
output_file=$2
distro=$3
family=$4
package_manager=$5
package_db=$6
mkdir -p /work/rootfs /work/out
case "$archive" in
  *.tar.xz|*.txz) tar -xJf "/input/$archive" -C /work/rootfs ;;
  *.tar.gz|*.tgz) tar -xzf "/input/$archive" -C /work/rootfs ;;
  *) echo "unsupported archive: $archive" >&2; exit 2 ;;
esac
mkdir -p /work/rootfs/tmp
cp /src/app/src/main/assets/scripts/common/setup/flux_guest_common.sh /work/rootfs/tmp/flux_guest_common.sh
cp "/src/app/src/main/assets/scripts/$family" /work/rootfs/tmp/family.sh
rm -f /work/rootfs/etc/resolv.conf
printf '%s\n' 'nameserver 1.1.1.1' > /work/rootfs/etc/resolv.conf
mkdir -p /work/rootfs/proc /work/rootfs/sys /work/rootfs/dev
mount -t proc proc /work/rootfs/proc 2>/dev/null || true
mount --rbind /dev /work/rootfs/dev 2>/dev/null || true
mount --rbind /sys /work/rootfs/sys 2>/dev/null || true
shell=/bin/sh
[ -x /work/rootfs/bin/bash ] && shell=/bin/bash
chroot /work/rootfs "$shell" -c '
  . /tmp/flux_guest_common.sh
  . /tmp/family.sh "$1"
' sh "$distro"
chroot /work/rootfs /bin/sh -c '
  mkdir -p /etc/fluxlinux
  printf "schema=1\nflavor=zenithblue\narchitecture=aarch64\ndistro=%s\npackageSource=maintainer package-manager transaction\nruntimeNetworkRequired=false\n" "$1" > /etc/fluxlinux/play-baseline-v1
  case "$2" in
    apt) dpkg-query -W -f="\${binary:Package}=\${Version}\n" ;;
    dnf|zypper) rpm -qa ;;
    pacman) pacman -Q ;;
    xbps) xbps-query -l ;;
    apk) apk info -vv ;;
    *) echo "unknown package manager $2" >&2; exit 2 ;;
  esac > /etc/fluxlinux/play-baseline-packages.lock
' sh "$distro" "$package_manager"
test -e "/work/rootfs$package_db"
for required in /bin/sh /usr/bin/startxfce4 /usr/bin/dbus-daemon /usr/bin/xfce4-session /usr/bin/xfwm4 /usr/bin/xfce4-panel /usr/bin/xfdesktop /usr/bin/thunar /etc/fluxlinux/play-baseline-v1; do
  test -e "/work/rootfs$required" || { echo "missing required $required" >&2; exit 1; }
done
grep -q '^schema=1$' /work/rootfs/etc/fluxlinux/play-baseline-v1
test -s /work/rootfs/etc/fluxlinux/play-baseline-packages.lock
apk add --no-cache tar xz >/dev/null
case "$output_file" in
  *.tar.xz|*.txz)
    tar --one-file-system --sort=name --mtime='UTC 1970-01-01' --owner=0 --group=0 --numeric-owner -cf - -C /work/rootfs . | xz -T0 -6 > "/work/out/$output_file"
    ;;
  *.tar.gz|*.tgz)
    tar --one-file-system --sort=name --mtime='UTC 1970-01-01' --owner=0 --group=0 --numeric-owner -cf - -C /work/rootfs . | gzip -n > "/work/out/$output_file"
    ;;
  *)
    echo "unsupported output archive: $output_file" >&2
    exit 2
    ;;
esac
'''


def build_generic(entry: dict, source: Path, output: Path, image: str, timeout: int) -> None:
    runner = selected_runner()
    stage = Path(tempfile.mkdtemp(prefix=f"fluxlinux-play-{entry['id']}-"))
    try:
        (stage / "out").mkdir()
        command = [
            runner, "run", "--rm", "--privileged", "--platform", "linux/arm64",
            "-v", f"{source.parent}:/input:ro",
            "-v", f"{stage}:/work",
            "-v", f"{ROOT}:/src:ro",
            image, "sh", "-eu", "-c", GENERIC_CONTAINER, "sh",
            source.name, output.name, entry["id"], entry["familyScript"],
            entry["packageManager"], entry["packageDb"],
        ]
        run(command, timeout=timeout)
        produced = stage / "out" / output.name
        if not produced.is_file():
            raise SystemExit(f"builder did not produce {produced}")
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(produced, output)
    finally:
        cleanup_stage(stage, runner, image)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=MANIFEST)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--distro", action="append")
    parser.add_argument("--image", default="alpine:3.24")
    parser.add_argument("--timeout", type=int, default=3600)
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    wanted = set(args.distro or [entry["id"] for entry in manifest["distros"]])
    entries = [entry for entry in manifest["distros"] if entry["id"] in wanted]
    known = {entry["id"] for entry in entries}
    if wanted != known:
        raise SystemExit(f"unknown distro(s): {sorted(wanted - known)}")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    runner = selected_runner()
    try:
        builder_id = subprocess.check_output(
            [runner, "image", "inspect", args.image, "--format", "{{.Id}}"], text=True
        ).strip()
    except subprocess.CalledProcessError:
        builder_id = "unknown"

    for entry in entries:
        source = (args.source_root / entry["archive"]).resolve()
        if not source.is_file():
            raise SystemExit(f"missing input {source}")
        if sha256(source) != entry["sha256"]:
            raise SystemExit(f"input SHA-256 mismatch for {source}")
        output = (args.output_dir / entry["archive"]).resolve()
        if entry.get("builder") == "alpine_apk_transaction":
            build_alpine(source, output, args.image)
            continue
        build_generic(entry, source, output, args.image, args.timeout)
        provenance = {
            "schemaVersion": 2,
            "payloadVersion": manifest["payloadVersion"],
            "distroId": entry["id"],
            "architecture": manifest["architecture"],
            "upstreamSource": None,
            "upstreamSha256": None,
            "inputArchive": entry["archive"],
            "inputArchiveSha256": entry["sha256"],
            "packageManager": entry["packageManager"],
            "packageTransaction": "maintainer aarch64 chroot transaction using the distro family script",
            "packageDatabase": entry["packageDb"],
            "builderImage": args.image,
            "builderImageId": builder_id,
            "sourceCommit": commit,
            "buildDate": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "archiveFileName": output.name,
            "archiveSha256": sha256(output),
            "compressedSize": output.stat().st_size,
            "sourceRecord": entry["sourceRecord"],
            "marker": "/etc/fluxlinux/play-baseline-v1",
            "runtimeNetworkRequired": False,
            "fluxCustomizations": "Flux family transaction finalization, marker, and package lock",
        }
        output.with_name(output.name + ".provenance.json").write_text(
            json.dumps(provenance, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        print(f"PASS: {entry['id']} {provenance['archiveSha256']} {output.stat().st_size} bytes", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
