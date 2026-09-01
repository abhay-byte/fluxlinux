#!/usr/bin/env bash
set -euo pipefail

# Scan the actual merged zenithblue asset tree. Main assets are selected unless
# an identically named zenithblue overlay exists. Package-manager commands are
# allowed in explicit user feature scripts, but never in family/customization
# or other automatically invoked onboarding paths.

repo=$(cd "$(dirname "$0")/.." && pwd)
main="$repo/app/src/main/assets/scripts"
play="$repo/app/src/zenithblue/assets/scripts"
stage=$(mktemp -d /tmp/fluxlinux-play-script-scan.XXXXXX)
trap 'find "$stage" -depth -delete 2>/dev/null || true' EXIT

while IFS= read -r -d '' file; do
  rel=${file#"$main/"}
  destination="$stage/$rel"
  mkdir -p "$(dirname "$destination")"
  overlay="$play/$rel"
  if [[ -f "$overlay" ]]; then
    cp "$overlay" "$destination"
  else
    cp "$file" "$destination"
  fi
done < <(find "$main" -type f -print0)
while IFS= read -r -d '' file; do
  rel=${file#"$play/"}
  [[ -f "$stage/$rel" ]] || {
    destination="$stage/$rel"
    mkdir -p "$(dirname "$destination")"
    cp "$file" "$destination"
  }
done < <(find "$play" -type f -print0)

automatic=0
user_action=0
while IFS= read -r -d '' file; do
  rel=${file#"$stage/"}
  if ! LC_ALL=C grep -Iq . "$file"; then
    continue
  fi
  text=$(<"$file")
  is_auto=0
  case "$rel" in
    */setup/*_family.sh|*/setup/setup_customization*.sh|*/setup_customization*.sh|common/setup/setup_customization_xfce.sh)
      is_auto=1
      ;;
  esac
  hits=$(printf '%s\n' "$text" | rg -n -i \
    '(^|[^[:alnum:]_-])(apt(-get)?[[:space:]]+(update|install)|apk[[:space:]]+add|dnf5?[[:space:]]+install|pacman[[:space:]]+-S|xbps-install([[:space:]]|$)|zypper[[:space:]]+install|git[[:space:]]+clone|((curl|wget)[^\n]*(https?://|[[:space:]]-O[[:space:]]))|remote[[:space:]]+install\.sh)' || true)
  if [[ -n "$hits" ]]; then
    if (( is_auto )); then
      echo "FAIL: automatic Play onboarding path has provisioning/network command: $rel"
      printf '%s\n' "$hits"
      automatic=1
    else
      echo "USER_ACTION: $rel"
      user_action=1
    fi
  fi
done < <(find "$stage" -type f -print0)

if (( automatic )); then
  exit 1
fi
echo "PASS: merged zenithblue runtime script tree has no automatic baseline provisioning/network commands"
if (( user_action )); then
  echo "INFO: user-action script hits were classified but not rejected"
fi
