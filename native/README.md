# Native Termux package builds (FluxLinux)

Custom-prefix Termux packages for the **embedded native terminal**, one tree per application ID.

| Application ID | Intended channel | Output |
|----------------|------------------|--------|
| `com.ivarna.fluxlinux` | F-Droid / GitHub | `output/com.ivarna.fluxlinux/` |
| `com.zenithblue.fluxlinux` | Google Play | `output/com.zenithblue.fluxlinux/` |

Plan: [`docs/plans/termux-native-packages-dual-appid.md`](../docs/plans/termux-native-packages-dual-appid.md)

## Layout

```text
native/
  termux-packages -> symlink to ~/repos/termux-lib/termux-packages (or a clone)
  package-lists/bootstrap-host.txt
  loader/shell-loader.apk
  output/<applicationId>/*.deb
  output/logs/
  bootstrap/<applicationId>/bootstrap.tar
  bootstrap/<applicationId>/jniLibs/arm64-v8a/
  bootstrap/<applicationId>/root/   # assemble workdir
```

**Tracked in git:** `native/output/<applicationId>/*.deb` (so bootstrap can be assembled on any machine).  
**Gitignored:** build logs, `bootstrap/*/root/` workdirs, and `bootstrap.tar` itself (regenerate with `assemble_bootstrap.py` — files are ~122 MB and over GitHub’s soft limit for everyday clones).

Docker only mounts the `termux-packages` tree. Builds write debs under:

```text
native/termux-packages/output/*.deb              # default; deps always land here
native/output/<applicationId>/                   # mirrored debs whose path prefix matches app id
```

**Note:** `build-package.sh -o` only affects the *top-level* package; dependencies still go to
`termux-packages/output/`. The build script therefore mirrors by **verifying archive paths**
against the target applicationId (skipping leftover `com.zenithblue.nativecode` / other debs).

Sharing one `termux-packages` tree with nativecode means FORCE rebuilds overwrite debs in
`output/`. Prefer a dedicated clone long-term, or re-mirror after each app-id switch.

## Prerequisites

- Docker
- Host network for builder (default in scripts): `TERMUX_DOCKER_RUN_EXTRA_ARGS=--network host …`
- Disk: multi-GB per applicationId
- `termux-packages` available at `native/termux-packages`

```bash
ln -sfn ~/repos/termux-lib/termux-packages native/termux-packages
```

## Commands

### Set package name only

```bash
./scripts/set_termux_package_name.sh com.ivarna.fluxlinux
```

### Dry-run: single package

```bash
./scripts/build_packages_for_appid.sh com.ivarna.fluxlinux bash
./scripts/verify_deb_prefix.sh \
  native/output/com.ivarna.fluxlinux/bash_*_aarch64.deb \
  com.ivarna.fluxlinux
```

### Full bootstrap package set (termux-lib SSOT)

```bash
# Exact list: native/package-lists/termux-lib-ssot.txt
FORCE=1 CONTINUE_ON_FAIL=1 ./scripts/build_packages_for_appid.sh \
  com.ivarna.fluxlinux --list termux-lib-ssot
FORCE=1 CONTINUE_ON_FAIL=1 ./scripts/build_packages_for_appid.sh \
  com.zenithblue.fluxlinux --list termux-lib-ssot
# Reuse Docker builder by default; RECREATE_BUILDER=1 wipes .built-packages
```

### Assemble bootstrap + jniLibs

```bash
./scripts/assemble_bootstrap.py --package-name com.ivarna.fluxlinux \
  --list native/package-lists/termux-lib-ssot.txt --mode full
./scripts/verify_bootstrap.sh com.ivarna.fluxlinux

./scripts/assemble_bootstrap.py --package-name com.zenithblue.fluxlinux \
  --list native/package-lists/termux-lib-ssot.txt --mode full
./scripts/verify_bootstrap.sh com.zenithblue.fluxlinux
```

**Status (2026-08-05):** both app IDs have SSOT 62/62 debs, `bootstrap.tar` (~122 MB), jniLibs, `verify_bootstrap.sh` **PASS**.

### Stage into the app APK (before every APK build)

**Gradle runs packaging automatically** — `assembleIvarna*` / `assembleZenithblue*` depend on
`:app:packageHostAssetsIvarna` / `:app:packageHostAssetsZenithblue` and **fail the build**
with a clear message when `native/bootstrap/<appId>/` is missing (P0-T6/T7). The script below
is the task's implementation; use it directly only for offline staging / debugging.

```bash
# Staging script: verifies + copies per-flavor assets + jniLibs (gitignored, regenerate)
./scripts/package_host_assets.sh com.ivarna.fluxlinux      # → app/src/ivarna/{assets,jniLibs}
./scripts/package_host_assets.sh com.zenithblue.fluxlinux  # → app/src/zenithblue/{assets,jniLibs}
./scripts/package_host_assets.sh --all                     # both

# Then build the APK flavor (package task runs as dependency)
./gradlew :app:assembleIvarnaDebug      # or :app:assembleZenithblueDebug

# Optional CI check of the built APK contents
./scripts/verify_apk_host_assets.sh app/build/outputs/apk/ivarna/debug/app-ivarna-debug.apk
```

Staged files are gitignored (`app/src/*/assets/bootstrap.tar`, `app/src/*/jniLibs/`);
rootfs is NOT packaged and NOT in git — distributed via the GitHub release tag
`rootfs` and downloaded at install time (`RootfsDownloader`).

### Force rebuild

```bash
FORCE=1 ./scripts/build_packages_for_appid.sh com.ivarna.fluxlinux bash
FORCE_DEPS=1 ./scripts/build_packages_for_appid.sh com.ivarna.fluxlinux bash
```

## Important rules

1. **Never** ship a bootstrap built for another applicationId.
2. Changing `TERMUX_APP__PACKAGE_NAME` invalidates reuse of stock `com.termux` repo debs; dependencies are built locally.
3. Do **not** reuse `com.zenithblue.nativecode` debs from termux-lib for FluxLinux.
4. Keep `loader.apk` Java class names as `com.termux.x11.Loader` (do not sed class names).
