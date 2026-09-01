# PRoot loader source pin

The Play host bootstrap's `libloader32.so` is generated from the pinned
[termux/proot](https://github.com/termux/proot) tag `v5.1.107.84`.

- Source archive: `proot-5.1.107.84.zip`
- Archive SHA-256: `a44ddbf18bc72c9780d56948b03aeda6d285392503ece0cae17cfc02e7bc7928`
- Build script: `scripts/rebuild_proot_loader_16k.sh`
- Toolchain: Android NDK `29.0.14206865`, ARM EABI API 24 compiler

The script builds the upstream loader32 target with an explicit
`-z max-page-size=16384` linker flag and installs the verified result into both
application-ID bootstrap trees. The 32-bit loader remains packaged because
PRoot's aarch64 build advertises the ARM32 guest ABI (`HAS_LOADER_32BIT`) and
the runtime passes `PROOT_LOADER_32`; the Play baseline is arm64, but removing
this path would break an arm32 guest executable inside a supported baseline.
