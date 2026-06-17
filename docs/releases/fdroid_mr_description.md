# F-Droid Merge Request: FluxLinux Pro

## Required

* [x] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy)
* [x] The original app author has been notified (and does not oppose the inclusion) *(I am the author)*
* [x] All related [fdroiddata](https://gitlab.com/fdroid/fdroiddata/issues) and [RFP issues](https://gitlab.com/fdroid/rfp/issues) have been referenced in this merge request
* [x] Builds with `fdroid build` and all pipelines pass

## Strongly Recommended

* [x] The upstream app source code repo contains the app metadata in a [Fastlane](https://gitlab.com/snippets/1895688) folder structure
* [x] Releases are tagged

## Suggested

* [ ] External repos are added as git submodules instead of srclibs *(N/A - no external repos)*
* [x] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds) *(Configured with `AllowedAPKSigningKeys`)*
* [ ] Multiple apks for native code *(N/A - no native code)*

---

## App Details

**FluxLinux Pro** is an Android app that provides a complete Linux desktop environment (XFCE) inside Termux. It supports multiple distributions including Debian, Ubuntu, Fedora, and Arch Linux.

### Key Features
- One-click Linux installation with XFCE desktop
- Floating keyboard button (via AccessibilityService)
- Full proot-based environment (no root required)
- Multiple distro support

### Notes on `scanignore`
The following asset archives are bundled for **user-facing features** and are **not** used during the F-Droid build process:

| Archive | Purpose |
|---------|---------|
| `assets/busybox/Busybox for Android NDK-*.zip` | **Magisk module for root users.** Provides essential Unix commands (`wget`, `curl`, `tar`, etc.) for enhanced root terminal functionality. Users flash this via Magisk to extend their root shell capabilities. |
| `packages/android-sdk-tools-static-aarch64.zip` | **ARM64 Android build tools** (aapt, aapt2, zipalign, etc.). Required for the in-app Android development stack (`setup_appdev_debian.sh`) since official SDK ships x86-only binaries that don't run on ARM64 proot. Source: [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools). |
| `assets/xfce4/icons/papirus-icon-theme-*.tar.gz` | GPLv3 Papirus icon theme for XFCE desktop |
| `assets/xfce4/theme/theme.zip` | GTK theme files for XFCE |
| `assets/xfce4/cursor/cursor.zip` | Vimix cursor theme (GPLv3) |
| `assets/wallpaper/wallpaper.zip` | Desktop wallpapers |

All archives are extracted at runtime by the user, not during F-Droid's build.

---

/label ~"New App"
