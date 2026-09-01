#!/bin/sh
set -eu
# Requires /etc/fluxlinux/play-baseline-v1; missing marker means refusing runtime package-network customization.
exec /usr/local/lib/fluxlinux/play_family_local_only.sh "$@"
