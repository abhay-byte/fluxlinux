# F-Droid MR (update): FluxLinux 1.8.0 → 2.0.0

Copy this into the fdroiddata MR for `metadata/com.ivarna.fluxlinux.yml`. This is an
**update** MR, not a new app — do not use `docs/releases/fdroid_mr_description.md`
(the stale "New App" template).

## Required

* [x] The app complies with the Inclusion Policy
* [x] I am the upstream author
* [x] Related issues referenced (none open for this update)
* [ ] Builds with `fdroid build` / fork CI green

## Update

Bump **FluxLinux** `com.ivarna.fluxlinux` **1.8.0 (10) → 2.0.0 (12)**.

2.0.0 embeds the Termux-prefix host (bootstrap + proot) and Termux:X11; external
Termux is no longer required. Distro rootfs archives are downloaded on demand from
the GitHub `rootfs` tag (SHA256-pinned). Product flavor `ivarna` is the F-Droid/GitHub
id; `zenithblue` is Play-only and must not be built.

### Build recipe changes

* `gradle: [ivarna]` — `gradle: yes` / `assembleRelease` would also configure the
  Play flavor and fails without its `bootstrap.tar`.
* `libXlorie.so` is now **compiled from source**: `:termux-x11` runs NDK 29 +
  CMake over the vendored upstream `lorie` native tree + 16 dependency submodules
  (`xserver`, `pixman`, `libx11`, `xkbcomp`, …). `submodules: yes` makes fdroidserver
  fetch them; `sudo` installs `bison` + `patch` (configure-time deps). No prebuilt
  `libXlorie.so` is shipped.
* `sudo`: assemble `bootstrap.tar` from **tracked** `.deb`s
  (`scripts/assemble_bootstrap.py`, reproducible tar). The tarball is gitignored
  (~124 MiB) and absent from the clone.
* `scanignore`: **only** paths the scanner actually flags (host jniLibs under
  `native/bootstrap/`, both flavors' `pulse-runtime/*.so`, `libevp_md2.so`,
  `assets/xfce4/` gz/zip, `assets/wallpaper/`). Do **not** list `native/output/`
  (`.deb` is not flagged), `loader.apk` (always deleted; unused path fails), or
  `app/src/main/assets/xfce4/` (`.tar.xz` is not flagged). All remaining prebuilts
  are FLOSS; sources cited below.
* `Binaries:` still `.../v%v/app-release.apk` (ivarna APK uploaded under that name).
* `AllowedAPKSigningKeys` unchanged (`34f01166…1a3c`).
* `TetheredNet` text updated (GitHub `rootfs` / `debian-v1`; kcubeterm/easycli removed).

### Sources for prebuilts

| Blob (scanignore) | Source |
|---|---|
| `native/output/**/*.deb` (392) | **Not scanignored** (scanner does not flag `.deb`). Cite only: Termux-prefix packages, rebuilt with applicationId `com.ivarna.fluxlinux` via `scripts/build_packages_for_appid.sh` from [termux/termux-packages](https://github.com/termux/termux-packages) |
| `native/bootstrap/**/jniLibs/*/lib{bash,proot,loader,loader32,pulseaudio,pactl}.so` | Copied from those debs by `scripts/assemble_bootstrap.py` |
| `app/src/*/assets/pulse-runtime/*.so` | `usr/lib/lib{soxr,soxr-lsr,FLAC,mp3lame,android-execinfo}.so` from the same debs, staged by `scripts/package_host_assets.sh` |
| `termux-x11/src/main/cpp/**` + submodules → `lib/arm64-v8a/libXlorie.so` | **Compiled here** from [termux/termux-x11](https://github.com/termux/termux-x11) `lorie` native tree (X server), NDK `29.0.14206865` + CMake, arm64 only |
| `app/src/main/assets/loader.apk` | **Not scanignored** — scanner always deletes `.apk`. termux-x11 `shell-loader` (GPLv3, 2.3 KiB); PREFIX copy is inside the GitHub `rootfs` bootstrap tarball |
| `app/src/main/assets/scripts/opensuse/common/libevp_md2.so` | openSUSE guest sudo/preload helper; source cited here, generated in the guest |
| `assets/xfce4/**` (`.tar.gz` / `.zip`) | Papirus / Vimix / Space (GPLv3). `app/src/main/assets/xfce4/**/*.tar.xz` is **not** scanignored (`.xz` is not flagged) |

F-Droid does **not** compile the Termux prefix: `buildserver-trixie` has no Termux
docker, and Termux's own F-Droid recipe ships the prefix the same way
(`scanignore` + documented prebuilt). Details:
`docs/plans/fdroid-buildserver-native-so.md`.

### Consent

Install is user-initiated. Rootfs / GPU driver / optional SDK downloads are not
silent auto-updates. Listing already has TetheredNet.