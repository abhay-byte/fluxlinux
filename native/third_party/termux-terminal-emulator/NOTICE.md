# Termux terminal-emulator native source

`termux.c` is copied from the `terminal-emulator` module of
[termux/termux-app](https://github.com/termux/termux-app) tag `v0.118.0`.
The pinned upstream source commit is `6e2689f55295fa444be8ac8592c527c2c5ef3253`.

FluxLinux rebuilds this JNI library with the pinned Android NDK and an
explicit `-z max-page-size=16384` linker flag. The Java terminal classes still
come from the pinned `com.github.termux:termux-app:v0.118.0` dependency; this
local native build only overrides the AAR's arm64/x86 native copies so the
source tree has a reproducible 16 KB-compatible native path.
