# FluxLinux Assets Reference

This document provides a comprehensive overview of all assets used in the FluxLinux project for distro customization, branding, and theming.

---

## Table of Contents

- [Directory Structure](#directory-structure)
- [Logo Assets](#logo-assets)
- [Onboarding Assets](#onboarding-assets)
- [Rootfs Archives](#rootfs-archives)
- [Wallpapers](#wallpapers)
- [XFCE4 Theming Assets](#xfce4-theming-assets)
  - [Themes](#themes)
  - [Icons](#icons)
  - [Cursors](#cursors)
- [Screenshots](#screenshots)
- [GitHub Release Assets](#github-release-assets)

---

## Directory Structure

```
assets/
├── logo/                    # Application logo files
│   ├── logo.png             # High-res PNG (5.3 MB)
│   └── logo.webp            # Optimized WebP (157 KB)
├── me.png                   # Developer avatar
├── onboarding/              # Onboarding screen images
│   └── onboarding-1.webp    # First onboarding slide
├── rootfs/                  # Local rootfs sources (gitignored; NOT packaged —
│   │                        #   uploaded to the GitHub release tag `rootfs`)
│   └── <12 distro tarballs>
├── screenshots/             # App screenshots
│   └── hardware_acceleration/
│       ├── 1.png            # GPU selection
│       └── 2.png            # glmark2 running
├── wallpaper/               # Desktop wallpapers
│   ├── dark.png             # Dark theme wallpaper
│   ├── dark2.png            # Alternative dark wallpaper
│   ├── light.png            # Light theme wallpaper
│   └── wallpaper.zip        # Bundled for release
└── xfce4/                   # XFCE4 customization
    ├── cursor/              # Cursor themes
    ├── icons/               # Icon packs
    └── theme/               # GTK/XFWM themes
```

---

## Logo Assets

**Location:** `assets/logo/`

| File | Size | Format | Usage |
|------|------|--------|-------|
| `logo.png` | 5.3 MB | PNG | High-resolution, marketing materials |
| `logo.webp` | 157 KB | WebP | Optimized, app usage |

The FluxLinux logo is used throughout the app and promotional materials.

---

## Onboarding Assets

**Location:** `assets/onboarding/`

| File | Size | Description |
|------|------|-------------|
| `onboarding-1.webp` | 73 KB | Welcome/introduction slide |

These images are displayed during the first-run onboarding experience.

---

## Rootfs Archives

**Location:** local sources under `assets/rootfs/` (gitignored, release-build only)

Pre-built root filesystem archives for distro installation. **Rootfs archives are
NOT packaged inside the APK anymore** — the selected distro's archive is
downloaded on demand from the GitHub release tag `rootfs` at install time, with
SHA256 + minimum-size verification and HTTP Range resume
(`RootfsDownloader`, plan `rootfs-github-release-no-apk-bloat.md`).

| Release filename | ~Size | SHA256 | Used by |
|------------------|-------|--------|---------|
| `debian_13_rootfs.tar.xz` | 81 MiB | `13e29f60…e6803` | Debian (proot + chroot) |
| `alpine_3.24_rootfs.tar.gz` | 3.8 MiB | `f55a90f6…721259` | Alpine (proot + chroot) |
| `fedora_44_rootfs.tar.xz` | 29.5 MiB | `2d89fe43…db1bd4` | Fedora |
| `void_20250202_rootfs.tar.xz` | 43.7 MiB | `01a30f17…ac3ce6` | Void |
| `opensuse_tumbleweed_rootfs.tar.xz` | 21.1 MiB | `bdcb8522…85a12a` | openSUSE |
| `deepin_25_rootfs.tar.xz` | 53.1 MiB | `2c7abfe8…193698` | Deepin |
| `chimera_20251220_rootfs.tar.xz` | 5.1 MiB | `0900e3f2…a4a6c` | Chimera |
| `manjaro_arm_rootfs.tar.xz` | 126.9 MiB | `b7339bcc…0170156` | Manjaro |
| `ubuntu_26.04_rootfs.tar.xz` | 19.8 MiB | `e648a530…960efc` | Ubuntu |
| `kali_2026_2_rootfs.tar.xz` | 117.5 MiB | `01c48a29…670689` | Kali |
| `parrot_7.2_rootfs.tar.xz` | 106.7 MiB | `49f4c289…094d4` | Parrot |
| `archlinux_arm_rootfs.tar.xz` | 110.9 MiB | `40209ef6…31d75` | Arch |

**Download URL (GitHub Release tag `rootfs`):**

```
https://github.com/abhay-byte/fluxlinux/releases/download/rootfs/<filename>
```

### Rootfs lookup order (install time)

1. Verified archive under `$HOME/<rootfsFileName>` (the supported offline path —
   `adb push` the archive there and installs proceed with zero network).
2. Verified local candidates (`$HOME/rootfs/`, proot cache, Download dirs —
   `/sdcard/Download` is best-effort only, no storage permission is requested).
3. Network download from the release tag above, streamed to `<name>.partial`
   with Range resume, then SHA256 + min-size verified and atomically renamed.

The full SHA256 pins live in `DistroInstallProfile.kt` (Kotlin SSOT);
`scripts/verify_rootfs_shas.sh` cross-checks every copy.

---

## Wallpapers

**Location:** `assets/wallpaper/`

Desktop wallpapers for the FluxLinux-themed desktop environment.

| File | Size | Theme | Resolution |
|------|------|-------|------------|
| `dark.png` | 3.0 MB | Dark | High-res |
| `dark2.png` | 4.1 MB | Dark (Alt) | High-res |
| `light.png` | 3.9 MB | Light | High-res |
| `wallpaper.zip` | 6.9 MB | Bundle | All variants |

### Theme Mapping

| Theme Selection | Wallpaper File |
|-----------------|----------------|
| Dark Mode | `fluxlinux-dark.png` |
| Light Mode | `fluxlinux-light.png` |

**Used by:** `setup_customization_debian.sh`

**Download URL (GitHub Release):**
```
https://github.com/abhay-byte/fluxlinux/releases/download/debian-v1/wallpaper.zip
```

---

## XFCE4 Theming Assets

**Location:** `assets/xfce4/`

Complete theming package for XFCE4 desktop customization.

### Themes

**Location:** `assets/xfce4/theme/`

| File | Size | Theme Name | Style |
|------|------|------------|-------|
| `Space-transparency.tar.xz` | 3.4 MB | Space Transparency | Dark, transparent |
| `Space-light.tar.xz` | 2.1 MB | Space Light | Light, clean |
| `theme.zip` | 5.5 MB | Bundle | Both themes |

**Theme Details:**

| Theme | GTK Theme | XFWM Theme | Best For |
|-------|-----------|------------|----------|
| Space-transparency | Dark | Glass effects | Dark mode users |
| Space-light | Light | Clean borders | Light mode users |

**Download URL (GitHub Release):**
```
https://github.com/abhay-byte/fluxlinux/releases/download/debian-v1/theme.zip
```

---

### Icons

**Location:** `assets/xfce4/icons/`

| File | Size | Icon Theme | Description |
|------|------|------------|-------------|
| `papirus-icon-theme-20250501.tar.gz` | 32.3 MB | Papirus | Modern flat icons |

**Papirus Variants Used:**

| Theme Mode | Icon Variant |
|------------|--------------|
| Dark | Papirus-Dark |
| Light | Papirus |

**Features:**
- 50+ application categories
- Multiple sizes (16px to 64px)
- Symbolic icons for panels
- High DPI support

**Download URL (GitHub Release):**
```
https://github.com/abhay-byte/fluxlinux/releases/download/debian-v1/icons.zip
```

---

### Cursors

**Location:** `assets/xfce4/cursor/`

| File | Size | Cursor Theme | Style |
|------|------|--------------|-------|
| `01-Vimix-cursors.tar.xz` | 190 KB | Vimix | Dark cursor |
| `02-Vimix-white-cursors.tar.xz` | 190 KB | Vimix White | White cursor |
| `cursor.zip` | 380 KB | Bundle | Both variants |

**Theme Mapping:**

| Theme Mode | Cursor Theme | Reason |
|------------|--------------|--------|
| Dark | Vimix-white-cursors | Better visibility on dark backgrounds |
| Light | Vimix-cursors | Better contrast on light backgrounds |

**Download URL (GitHub Release):**
```
https://github.com/abhay-byte/fluxlinux/releases/download/debian-v1/cursor.zip
```

---

## Screenshots

**Location:** `assets/screenshots/`

Screenshots used in documentation and promotional materials.

### Hardware Acceleration

| File | Size | Description |
|------|------|-------------|
| `1.png` | 1.9 MB | GPU selection menu |
| `2.png` | 1.8 MB | glmark2 benchmark running |

---

## GitHub Release Assets

All theming assets are bundled and hosted on GitHub Releases for download during installation.

**Release Tag:** `debian-v1`

**Base URL:** `https://github.com/abhay-byte/fluxlinux/releases/download/debian-v1/`

### Asset List

| Asset | Filename | Size | Description |
|-------|----------|------|-------------|
| Themes | `theme.zip` | 5.5 MB | GTK + XFWM themes |
| Icons | `icons.zip` | ~32 MB | Papirus icon pack |
| Cursors | `cursor.zip` | 380 KB | Vimix cursor themes |
| Wallpapers | `wallpaper.zip` | 6.9 MB | Desktop backgrounds |

Rootfs archives live on a **separate** tag:

| Tag | Contents | Description |
|-----|----------|-------------|
| `rootfs` | 12 distro tarballs + `sha256sums.txt` | Downloaded at install time by `RootfsDownloader` / script fallback |

### Download Script Example

```bash
# Download all theming assets
BASE_URL="https://github.com/abhay-byte/fluxlinux/releases/download/debian-v1"

wget -O theme.zip "$BASE_URL/theme.zip"
wget -O icons.zip "$BASE_URL/icons.zip"
wget -O cursor.zip "$BASE_URL/cursor.zip"
wget -O wallpaper.zip "$BASE_URL/wallpaper.zip"
```

---

## Asset Installation Flow

```mermaid
flowchart TD
    A[setup_customization_debian.sh] --> B[Download Assets]
    B --> C{Extract Each}
    
    C --> D[theme.zip]
    D --> E[/usr/share/themes/]
    
    C --> F[icons.zip]
    F --> G[/usr/share/icons/]
    
    C --> H[cursor.zip]
    H --> I[/usr/share/icons/]
    
    C --> J[wallpaper.zip]
    J --> K[$HOME/Pictures/Wallpapers/]
    
    L[Apply Theme] --> M[xsettings.xml]
    L --> N[xfwm4.xml]
    L --> O[xfce4-desktop.xml]
```

---

## License

All assets are subject to the FluxLinux project license (GPLv3).

Third-party assets retain their original licenses:
- **Papirus Icons:** GPLv3
- **Vimix Cursors:** GPLv3
- **Space Theme:** GPLv3

---

## See Also

- [Scripts Reference](scripts_reference.md) - Installation scripts documentation
- [Hardware Acceleration](hardware_acceleration.md) - GPU setup guide
- [Script Execution Workflow](script_execution_workflow.md) - How assets are deployed
