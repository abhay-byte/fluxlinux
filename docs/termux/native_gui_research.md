# Native Termux GUI Research — XFCE4, KDE, VirGL, Turnip

> Research gathered June 2026 for FluxLinux Pro native Termux desktop support.
> Sources: Termux Wiki, r/termux, GitHub (termux/termux-x11, LinuxDroidMaster/Termux-Desktops, sabamdarif/termux-desktop).

---

## 1. Key Differences: Native Termux vs PRoot

| Aspect | Native Termux | PRoot (Debian) |
|---|---|---|
| Package manager | `pkg` (Termux) | `apt` (Debian) |
| Shell shebang | `#!/bin/bash` | `#!/bin/bash` |
| Home dir | `/data/data/com.termux/files/home` | `/root` or `/home/flux` |
| Prefix | `$PREFIX` = `/data/data/com.termux/files/usr` | `/usr` |
| sudo / su | ❌ Not available (non-root) | ✅ Inside container |
| systemd | ❌ Not available | ❌ Not in PRoot either |
| Hardware access | ✅ Direct host GPU via Vulkan | ⚠️ Via virgl_test_server bridge |
| Package coverage | Limited (Termux repo) | Full Debian repo |
| Stability | ⚠️ Experimental (esp. KDE) | ✅ More stable |

---

## 2. Required Repositories

Native Termux GUI requires two extra repos beyond the default:

```bash
pkg install x11-repo    # Provides termux-x11-nightly, mesa-zink, virglrenderer-mesa-zink
pkg install tur-repo    # Termux User Repository — extra packages
```

---

## 3. XFCE4 Native Setup

### Packages
```bash
pkg install xfce4 xfce4-goodies dbus pulseaudio \
            termux-x11-nightly mesa-zink virglrenderer-mesa-zink \
            vulkan-loader-android
```

### Key XFCE4 packages available in Termux
| Package | Purpose |
|---|---|
| `xfce4` | Core desktop environment |
| `xfce4-goodies` | Panel plugins, extras |
| `xfce4-terminal` | Included in goodies |
| `thunar` | File manager (included in xfce4) |
| `mousepad` | Text editor |
| `ristretto` | Image viewer |
| `xfce4-taskmanager` | Task manager |
| `dbus` | Required for session management |
| `pulseaudio` | Audio (tcp module for cross-process) |

### XFCE4 Start Sequence (native)
```bash
# 1. Kill any previous session
pkill -f "xfce4-session" 2>/dev/null || true
pkill -f "termux-x11" 2>/dev/null || true
pkill -f "pulseaudio" 2>/dev/null || true
sleep 1

# 2. Start PulseAudio (TCP mode so GUI apps find it)
pulseaudio --start \
  --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" \
  --exit-idle-time=-1
export PULSE_SERVER=127.0.0.1

# 3. Start virgl server (for GPU acceleration)
virgl_test_server_android &    # VirGL path
# OR for Turnip/Zink (Adreno):
# MESA_NO_ERROR=1 MESA_GL_VERSION_OVERRIDE=4.3COMPAT \
# MESA_GLES_VERSION_OVERRIDE=3.2 GALLIUM_DRIVER=zink \
# ZINK_DESCRIPTORS=lazy virgl_test_server --use-egl-surfaceless --use-gles &

# 4. Start Termux:X11
termux-x11 :0 &
sleep 2

# 5. Set display and start XFCE4
export DISPLAY=:0
export LIBGL_ALWAYS_INDIRECT=1
export GALLIUM_DRIVER=virpipe   # virgl path (or 'zink' for Turnip)

dbus-launch --exit-with-session xfce4-session &
```

### Performance Tip
Disable XFCE4 compositor (it causes lag on mobile GPU):
```bash
xfconf-query -c xfwm4 -p /general/use_compositing -s false
```

---

## 4. KDE Plasma Native Setup

### Status: Experimental
KDE Plasma can run natively in Termux but has known issues:
- KWin compositor has OpenGL compatibility issues on many devices
- Some KDE services that depend on D-Bus activation may not work
- "Open With" dialogs often broken
- Recommended workaround: disable KWin compositing

### Packages
```bash
pkg install plasma konsole dolphin dbus pulseaudio \
            termux-x11-nightly mesa-zink virglrenderer-mesa-zink \
            vulkan-loader-android
```

### KDE Plasma packages in Termux
| Package | Purpose |
|---|---|
| `plasma` | Full KDE Plasma metapackage |
| `konsole` | Terminal emulator |
| `dolphin` | File manager |
| `kate` | Text editor (may need separate install) |
| `dbus` | Required for Plasma session |
| `pulseaudio` | Audio |

### KDE Start Sequence
```bash
# Kill previous sessions
pkill -f "startplasma" 2>/dev/null || true
pkill -f "kwin" 2>/dev/null || true
pkill -f "termux-x11" 2>/dev/null || true
sleep 1

# PulseAudio
pulseaudio --start \
  --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" \
  --exit-idle-time=-1
export PULSE_SERVER=127.0.0.1

# virgl server
virgl_test_server_android &
sleep 1

# Termux:X11
termux-x11 :0 &
sleep 2

export DISPLAY=:0
export LIBGL_ALWAYS_INDIRECT=1
export GALLIUM_DRIVER=virpipe

# Disable KWin compositing (critical for stability)
export KWIN_COMPOSE=0
export KWIN_OPENGL_INTERFACE=egl

dbus-launch --exit-with-session startplasma-x11 &
```

---

## 5. Hardware Acceleration

### VirGL (General — all GPUs)
- Works by bridging OpenGL calls through the Android OpenGL ES stack
- Use `virgl_test_server_android` (simpler, pre-configured for Android)
- Set `GALLIUM_DRIVER=virpipe` in the app environment
- Set `LIBGL_ALWAYS_INDIRECT=1`

```bash
virgl_test_server_android &
export DISPLAY=:0
export LIBGL_ALWAYS_INDIRECT=1
export GALLIUM_DRIVER=virpipe
```

### Turnip + Zink (Adreno GPUs only — Snapdragon)
- Turnip = open-source Mesa Vulkan driver for Adreno 6xx/7xx/8xx
- Zink = OpenGL-over-Vulkan Gallium driver
- Best performance on Snapdragon devices
- Requires: `mesa-zink`, `virglrenderer-mesa-zink`, `vulkan-loader-android`

```bash
# Start virgl server with Zink/Turnip mode
MESA_NO_ERROR=1 \
MESA_GL_VERSION_OVERRIDE=4.3COMPAT \
MESA_GLES_VERSION_OVERRIDE=3.2 \
GALLIUM_DRIVER=zink \
MESA_LOADER_DRIVER_OVERRIDE=zink \
ZINK_DESCRIPTORS=lazy \
virgl_test_server --use-egl-surfaceless --use-gles &

export DISPLAY=:0
export LIBGL_ALWAYS_INDIRECT=1
export GALLIUM_DRIVER=virpipe  # Client side still uses virpipe
export MESA_LOADER_DRIVER_OVERRIDE=zink
```

### GPU Auto-Detection
```bash
detect_gpu_backend() {
    # Check Android hw.vulkan property for Adreno
    local vulkan_hw
    vulkan_hw=$(getprop ro.hardware.vulkan 2>/dev/null || echo "")
    if echo "$vulkan_hw" | grep -qi "adreno\|freedreno"; then
        echo "turnip"
        return
    fi
    # Fallback: check /proc/cpuinfo for Snapdragon
    if grep -qi "qualcomm\|snapdragon" /proc/cpuinfo 2>/dev/null; then
        echo "turnip"
        return
    fi
    echo "virgl"
}
```

### Software Rendering (Fallback — all devices)
For devices where VirGL/Turnip fail:
```bash
export GALLIUM_DRIVER=llvmpipe
export LIBGL_ALWAYS_SOFTWARE=1
```

---

## 6. Customization

### XFCE4 Theming in Native Termux
Theme directories:
- GTK themes: `$HOME/.themes/` (or `$PREFIX/share/themes/`)
- Icon themes: `$HOME/.icons/` (or `$PREFIX/share/icons/`)
- Fonts: `$HOME/.fonts/` or `$PREFIX/share/fonts/`
- Wallpapers: `$HOME/.fluxlinux/wallpapers/`

Apply with xfconf:
```bash
# GTK theme
xfconf-query -c xsettings -p /Net/ThemeName -s "FluxLinux ProDark"

# Icon theme
xfconf-query -c xsettings -p /Net/IconThemeName -s "Papirus-Dark"

# Wallpaper
xfconf-query -c xfce4-desktop -p /backdrop/screen0/monitorVirtual-1/workspace0/last-image -s "$HOME/.fluxlinux/wallpapers/flux_dark.jpg"

# Disable compositing (performance)
xfconf-query -c xfwm4 -p /general/use_compositing -s false

# Scale for HiDPI (Android screens)
xfconf-query -c xsettings -p /Gdk/WindowScalingFactor -s 2
```

### KDE Theming in Native Termux
```bash
# Global theme (Breeze Dark)
kwriteconfig5 --file kdeglobals --group General --key ColorScheme "BreezeDark"
kwriteconfig5 --file kdeglobals --group KDE --key LookAndFeelPackage "org.kde.breezetweak.desktop"

# Disable compositor (critical for mobile)
kwriteconfig5 --file kwinrc --group Compositing --key Enabled false

# HiDPI scaling
kwriteconfig5 --file kdeglobals --group KScreen --key ScaleFactor 2
```

---

## 7. Known Limitations (Native Termux)

| Feature | Status | Workaround |
|---|---|---|
| XFCE4 stability | ✅ Good | Disable compositor |
| KDE Plasma stability | ⚠️ Experimental | Disable KWin compositing, use `KWIN_COMPOSE=0` |
| Audio | ✅ Works | PulseAudio TCP mode |
| VirGL GPU accel | ✅ Works on most | `virgl_test_server_android` |
| Turnip Vulkan (Adreno) | ✅ Works on Adreno 6xx+ | Check GPU via `getprop` |
| Mali GPU acceleration | ⚠️ Limited | Use VirGL or software fallback |
| Snapdragon 8 Elite (Adreno 830) | ⚠️ Experimental | Newer Mesa builds needed |
| Clipboard | ✅ Works | Via Termux:X11 |
| System fonts | ⚠️ Limited | Install Noto/DejaVu fonts manually |
| App Store / Package GUI | ❌ Not available | CLI pkg only |

---

## 8. Script Locations in FluxLinux Pro

```
scripts/termux/
├── setup_termux.sh               # Existing: host Termux bootstrap
├── termux_tweaks.sh              # Existing: host tweaks
├── install.sh / install_apps.sh  # Existing: pkg installs
├── setup_theme.sh                # Existing: terminal theming
├── setup/                        # ← NEW
│   ├── setup_xfce4_termux.sh    # Install XFCE4 natively
│   ├── setup_kde_termux.sh      # Install KDE natively
│   ├── setup_hw_accel_termux.sh # Install VirGL + Turnip packages + auto-detect
│   ├── setup_customization_termux.sh     # XFCE4 FluxLinux Pro theme
│   └── setup_customization_kde_termux.sh # KDE FluxLinux Pro theme
├── start/                        # ← NEW
│   ├── start_xfce4_termux.sh    # Launch XFCE4 (auto GPU detect)
│   └── start_kde_termux.sh      # Launch KDE (auto GPU detect)
└── stop/                         # ← NEW
    ├── stop_xfce4_termux.sh     # Kill XFCE4 + X11
    └── stop_kde_termux.sh       # Kill KDE + X11
```
