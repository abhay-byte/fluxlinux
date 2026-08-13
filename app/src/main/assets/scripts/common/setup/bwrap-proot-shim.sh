#!/bin/sh
# FluxLinux: glycin/GTK spawn bwrap with many --ro-bind paths.
# Exec the glycin loader (or /usr/bin/true), never an earlier bind source.
pick=""
while [ $# -gt 0 ]; do
  case "$1" in
    /*)
      if [ -f "$1" ] && [ -x "$1" ]; then
        case "$1" in
          *glycin-loaders*|/usr/bin/true|/bin/true)
            exec "$@"
            ;;
        esac
        pick="$1"
        # keep scanning; last executable is fallback
        shift
        continue
      fi
      ;;
  esac
  shift
done
if [ -n "$pick" ]; then
  exec "$pick"
fi
echo "bwrap-shim: no command" >&2
exit 127
