#!/usr/bin/env bash
set -euo pipefail

# Release gate for Android-host native code. Guest ELF files inside PFD rootfs
# archives are not inspected here; only arm64-v8a APK libraries and the AAB's
# requested page-alignment configuration are gated.

usage() { echo "usage: $0 --apk APK --aab AAB [--bundletool PATH]"; }
apk=
aab=
bundletool=
while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk) apk=${2:?missing --apk value}; shift 2 ;;
    --aab) aab=${2:?missing --aab value}; shift 2 ;;
    --bundletool) bundletool=${2:?missing --bundletool value}; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done
[[ -f "$apk" && -f "$aab" ]] || { usage >&2; exit 2; }
command -v unzip >/dev/null 2>&1 || { echo "FAIL: unzip is required" >&2; exit 1; }
command -v awk >/dev/null 2>&1 || { echo "FAIL: awk is required" >&2; exit 1; }

readelf=${LLVM_READELF:-}
if [[ -z "$readelf" ]]; then
  for candidate in \
    "${ANDROID_NDK_HOME:-}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" \
    /opt/android-sdk/ndk/*/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf; do
    [[ -x "$candidate" ]] && readelf=$candidate && break
  done
fi
[[ -x "$readelf" ]] || { echo "FAIL: llvm-readelf is required" >&2; exit 1; }

zipalign=${ZIPALIGN:-}
if [[ -z "$zipalign" ]]; then zipalign=$(command -v zipalign 2>/dev/null || true); fi
if [[ -z "$zipalign" ]]; then
  for candidate in /opt/android-sdk/build-tools/*/zipalign; do
    [[ -x "$candidate" ]] && zipalign=$candidate
  done
fi
[[ -x "$zipalign" ]] || { echo "FAIL: zipalign is required" >&2; exit 1; }

stage=$(mktemp -d /tmp/fluxlinux-16k-audit.XXXXXX)
trap 'find "$stage" -depth -delete 2>/dev/null || true' EXIT

status=0
arm64_entries=$(unzip -Z1 "$apk" | awk '/^lib\/arm64-v8a\/[^/]+\.so$/ {print}')
[[ -n "$arm64_entries" ]] || { echo "FAIL: APK contains no arm64-v8a shared libraries"; exit 1; }
printf '%-42s %-10s %-28s %s\n' "library" "ELF class" "all LOAD alignments" "status"
while IFS= read -r entry; do
  [[ -n "$entry" ]] || continue
  name=${entry##*/}
  so="$stage/$name"
  unzip -p "$apk" "$entry" > "$so"
  elf_class=$("$readelf" -h "$so" | awk -F: '/Class:/ && !seen++ {gsub(/^[[:space:]]+/, "", $2); print $2}')
  loads=$("$readelf" -lW "$so" | awk '$1 == "LOAD" {print $NF}' | paste -sd, -)
  if [[ -z "$loads" ]]; then
    echo "FAIL: $entry has no LOAD segments"
    status=1
    continue
  fi
  bad=0
  while IFS= read -r alignment; do
    [[ -n "$alignment" ]] || continue
    value=$((alignment))
    (( value >= 0x4000 )) || bad=1
  done < <("$readelf" -lW "$so" | awk '$1 == "LOAD" {print $NF}')
  if (( bad )); then
    printf '%-42s %-10s %-28s %s\n' "$entry" "$elf_class" "$loads" "UNALIGNED"
    status=1
  else
    printf '%-42s %-10s %-28s %s\n' "$entry" "$elf_class" "$loads" "ALIGNED"
  fi
done <<< "$arm64_entries"

if ! "$zipalign" -c -P 16 -v 4 "$apk" >/dev/null; then
  echo "FAIL: zipalign -c -P 16 -v 4 failed for $apk"
  status=1
else
  echo "PASS: APK ZIP page alignment is 16 KB"
fi

if [[ -z "$bundletool" ]]; then bundletool=$(command -v bundletool 2>/dev/null || true); fi
if [[ -z "$bundletool" ]]; then
  echo "FAIL: bundletool is required to inspect AAB page alignment" >&2
  status=1
else
  if [[ "$bundletool" == *.jar ]]; then
    config=$(java -jar "$bundletool" dump config --bundle="$aab")
  else
    config=$("$bundletool" dump config --bundle="$aab")
  fi
  if grep -q 'PAGE_ALIGNMENT_16K' <<< "$config"; then
    echo "PASS: AAB requests PAGE_ALIGNMENT_16K"
  else
    echo "FAIL: AAB does not request PAGE_ALIGNMENT_16K"
    grep -E 'alignment|ALIGNMENT' <<< "$config" || true
    status=1
  fi
fi

if (( status )); then
  echo "FAIL: 16 KB page compatibility gate"
  exit 1
fi
echo "PASS: 16 KB page compatibility gate"
