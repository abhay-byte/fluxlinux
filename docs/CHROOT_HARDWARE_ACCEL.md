# Hardware Acceleration and Audio for Chroot Environments

This document explains how VirGL (GPU acceleration) and PulseAudio work in chroot environments and how they are configured in FluxLinux.

## Overview

Chroot environments run as a separate Linux filesystem on Android, but they need special handling for:
- **VirGL** - Uses the host GPU for OpenGL acceleration
- **PulseAudio** - Routes audio through the embedded FluxLinux host PREFIX (app uid)

## Architecture

```
┌────────────────────────────────────────────────────────────────┐
│ Android                                                        │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ FluxLinux app uid (embedded PREFIX)                     │  │
│  │  • VirGL server (virgl_test_server_android)             │  │
│  │  • PulseAudio server (AAudio + TCP 127.0.0.1:4713)      │  │
│  │  • Creates sockets in $PREFIX/tmp                       │  │
│  └─────────────────────────────────────────────────────────┘  │
│                          ▲ ▲                                   │
│                          │ │  Sockets                          │
│                          │ │  (bind-mounted)                   │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ Chroot (Debian 13)                                      │  │
│  │  • /tmp → Termux tmp (VirGL socket at /tmp/.virgl_test) │  │
│  │  • PULSE_SERVER=tcp:127.0.0.1 (TCP audio)               │  │
│  │  • gpu-launch wrapper sets GALLIUM_DRIVER=virpipe       │  │
│  └─────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

## VirGL Setup

### How It Works

1. **Server**: `virgl_test_server_android` runs in Termux and creates a Unix socket at `$PREFIX/tmp/.virgl_test`
2. **Client**: Mesa inside chroot uses `GALLIUM_DRIVER=virpipe` to connect to this socket
3. **Socket Path**: The chroot's `/tmp` is bind-mounted from Termux's tmp, making the socket accessible

### Critical: UID/Permissions

VirGL **MUST** be started from Termux user context (not root):
- If started from root, the socket gets root ownership and chroot apps can't connect
- FluxLinux starts VirGL in Termux context before calling `su` for chroot

### Usage in Chroot

```bash
# Run any OpenGL app with GPU acceleration
gpu-launch glmark2
gpu-launch glxinfo | grep "OpenGL renderer"

# Enable debug mode
FLUX_GPU_DEBUG=1 gpu-launch glmark2
```

### Environment Variables

| Variable | Value | Purpose |
|----------|-------|---------|
| `GALLIUM_DRIVER` | `virpipe` | Use VirGL renderer |
| `VTEST_SOCKET_NAME` | `/tmp/.virgl_test` | Socket path |
| `MESA_GL_VERSION_OVERRIDE` | `4.0` | Override reported GL version |

---

## PulseAudio Setup

### How It Works

1. **Server**: PulseAudio runs in the embedded PREFIX as the **app uid** (`start_pulse_host.sh`)
2. **Client**: Guests connect via `PULSE_SERVER=tcp:127.0.0.1`
3. **No unix socket for guests**: TCP loopback only (`listen=127.0.0.1`)

### Critical: UID/Permissions

Like VirGL, PulseAudio **MUST** be started from the app uid (never root, never `--system`, never inside the guest):
- Root can't use the app `XDG_RUNTIME_DIR`
- A guest Pulse/PipeWire has no Android AAudio/SLES sink
- FluxLinux starts Pulse via `prepareHost` / desktop start (`start_pulse_host.sh`) **before** `su`

### Host Server Command

```bash
# SSOT: $HOME/start_pulse_host.sh
pulseaudio --start --exit-idle-time=-1 \
  --dl-search-path="$PREFIX/lib/pulseaudio/modules"
pactl load-module module-aaudio-sink
pactl load-module module-native-protocol-tcp \
  auth-ip-acl=127.0.0.1 auth-anonymous=1 listen=127.0.0.1
```

### Usage in Chroot

Apps automatically use audio when `PULSE_SERVER=tcp:127.0.0.1` is set (builders, helper `env -i`, and GUI wrappers).

```bash
# Test audio
paplay /usr/share/sounds/freedesktop/stereo/bell.oga

# Check connection
pactl info
```

---

## Troubleshooting

### VirGL: "lost connection to rendering server"

1. **Check server is running**: `pgrep -f virgl_test_server`
2. **Check socket exists**: `ls -la /tmp/.virgl_test`
3. **Check socket ownership**: Should be Termux user (u0_aXXX), NOT root
4. **Restart GUI**: Click GUI button in FluxLinux app (starts VirGL fresh)

### PulseAudio: No Sound

1. **Check server is running**: `pgrep -f pulseaudio`
2. **Check from chroot**: `pactl info` (should show connection)
3. **Restart**: Kill existing and restart GUI from FluxLinux app

### Quick Manual Start (Termux, NOT root)

```bash
# Kill and restart VirGL
pkill -9 -f virgl_test_server
nohup setsid virgl_test_server_android >/dev/null 2>&1 &

# Restart host Pulse (app uid — never root)
bash "$HOME/start_pulse_host.sh"
```

---

## Implementation Details

### FluxLinux App Flow

When user clicks GUI button for Debian 13 Chroot:

1. **TermuxIntentFactory.kt** runs in Termux user context:
   - Starts VirGL server
   - Starts PulseAudio server
   - Then calls `su -c "sh start_debian13_gui.sh"`

2. **start_debian13_gui.sh** runs as root:
   - Mounts filesystems
   - Starts X11 server
   - Checks (but does NOT start) VirGL/PulseAudio
   - Enters chroot

### Key Files

| File | Purpose |
|------|---------|
| `TermuxIntentFactory.kt` | Starts services in Termux context |
| `setup_debian13_chroot.sh` | Generates chroot scripts |
| `start_debian13_gui.sh` | Root script for GUI launch |
| `setup_hw_accel_debian.sh` | Creates `gpu-launch` wrapper |
