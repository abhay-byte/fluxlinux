#!/bin/bash
# ============================================================
# FluxLinux — Start Native Termux KDE Plasma Desktop
# Location: termux/start/start_kde_termux.sh
# Runs on: HOST Termux (native, no container)
# Root required: no
#
# Launch sequence:
#   1. Kill previous session
#   2. Start PulseAudio (TCP mode)
#   3. Use KDE-safe software rendering
#   4. Start Termux:X11 server
#   5. Launch KDE Plasma (startplasma-x11)
#
# Note: KDE compositing is disabled by default in FluxLinux
# for stability on mobile GPUs. See docs/termux/ for details.
# ============================================================

handle_error() {
    echo ""
    echo "❌ FluxLinux Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    echo "Please check the error message above for details."
    echo "---------------------------------------------------"
    read -p "Press Enter to acknowledge error and exit..."
    exit 1
}

echo ""
echo "══════════════════════════════════════════════"
echo "  FluxLinux — Launching Native KDE Plasma"
echo "  ⚠️  Experimental — compositor is disabled"
echo "══════════════════════════════════════════════"
echo ""

# ── Read GPU config / auto-detect ────────────────────────
GPU_BACKEND="virgl"
GPU_CONFIG="$HOME/.fluxlinux/gpu_config"
if [ -f "$GPU_CONFIG" ]; then
    source "$GPU_CONFIG"
    GPU_BACKEND="${FLUX_GPU_BACKEND:-virgl}"
else
    # gpu_config not found — auto-detect now
    echo "FluxLinux: gpu_config not found, auto-detecting GPU..."
    _vulkan_hw=$(getprop ro.hardware.vulkan 2>/dev/null || echo "")
    _egl_hw=$(getprop ro.hardware.egl 2>/dev/null || echo "")
    _board=$(getprop ro.product.board 2>/dev/null || echo "")
    if echo "$_vulkan_hw$_egl_hw" | grep -qi "adreno\|freedreno"; then
        GPU_BACKEND="turnip"
    elif grep -qi "qualcomm\|snapdragon" /proc/cpuinfo 2>/dev/null; then
        GPU_BACKEND="turnip"
    elif echo "$_board" | grep -qi "snapdragon\|sm[0-9]\|sdm[0-9]\|msm[0-9]"; then
        GPU_BACKEND="turnip"
    fi
    # Save result so next launch skips detection
    mkdir -p "$HOME/.fluxlinux"
    echo "FLUX_GPU_BACKEND=$GPU_BACKEND" > "$GPU_CONFIG"
    echo "FluxLinux: Auto-detected GPU backend: [$GPU_BACKEND] (saved to gpu_config)"
fi
echo "FluxLinux: GPU backend [$GPU_BACKEND]"

# ── Step 1: Kill previous session ─────────────────────────
echo "FluxLinux: Cleaning up previous session..."
pkill -f "startplasma" 2>/dev/null || true
pkill -f "kwin_x11" 2>/dev/null || true
pkill -f "plasmashell" 2>/dev/null || true
pkill -f "ksmserver" 2>/dev/null || true
pkill -f "kded5" 2>/dev/null || true
pkill -f "termux-x11" 2>/dev/null || true
pkill -f "virgl_test_server" 2>/dev/null || true
pkill -f "pulseaudio" 2>/dev/null || true
sleep 1

# ── Step 2: PulseAudio ────────────────────────────────────
echo "FluxLinux: Starting PulseAudio..."
unset PULSE_SERVER
pulseaudio --kill 2>/dev/null || true
pulseaudio --start \
    --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" \
    --exit-idle-time=-1 2>/dev/null || \
    echo " [⚠️] PulseAudio start failed — audio may not work"
export PULSE_SERVER=127.0.0.1

# ── Step 3: KDE-safe software rendering ───────────────────
echo "FluxLinux: Using software rendering (LLVMpipe) for KDE stability..."
GPU_BACKEND="software"
export GALLIUM_DRIVER=llvmpipe
export LIBGL_ALWAYS_SOFTWARE=1
sleep 1

# ── Step 4: Termux:X11 ───────────────────────────────────
echo "FluxLinux: Starting Termux:X11..."
if ! command -v termux-x11 >/dev/null 2>&1; then
    echo "❌ termux-x11 not found. Run 'setup_kde_termux.sh' first."
    read -p "Press Enter to exit..."
    exit 1
fi
mkdir -p "${TMPDIR:-$PREFIX/tmp}/.X11-unix"
rm -f "${TMPDIR:-$PREFIX/tmp}/.X11-unix/X0"
nohup termux-x11 :0 >"${TMPDIR:-$PREFIX/tmp}/termux-x11.log" 2>&1 &
disown
sleep 4

# ── Auto-open the Termux:X11 viewer ──────────────────────
echo "FluxLinux: Opening Termux:X11 viewer..."
am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity >/dev/null 2>&1 || \
    echo " [⚠️] Could not auto-open Termux:X11 — please open it manually"
sleep 3

# ── Step 5: Export display env ───────────────────────
export DISPLAY=:0
export LIBGL_ALWAYS_INDIRECT=1
# XDG_RUNTIME_DIR must be mode 700 (owner-only) — D-Bus rejects world-writable dirs.
# $TMPDIR itself is 1777, so create a private subdirectory.
export XDG_RUNTIME_DIR="${TMPDIR:-$PREFIX/tmp}/kde-runtime"
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"

# KDE-specific env: disable compositing at env level too
export KWIN_COMPOSE=0
export KWIN_OPENGL_INTERFACE=egl

echo "FluxLinux: XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR (mode $(stat -c '%a' $XDG_RUNTIME_DIR))"

# ── Step 6: D-Bus session ────────────────────────
echo "FluxLinux: Starting D-Bus session..."
# Note: Termux dbus-launch does NOT support --exit-with-session.
# Use --sh-syntax to export DBUS_SESSION_BUS_ADDRESS into this shell.
if [ -z "$DBUS_SESSION_BUS_ADDRESS" ]; then
    eval "$(dbus-launch --sh-syntax)" 2>/dev/null || \
        echo " [⚠️] D-Bus launch failed — some KDE services may not work"
fi

# ── Step 7: Ensure KWin compositing is pre-disabled ──────
mkdir -p "$HOME/.config"
if ! grep -q "Enabled=false" "$HOME/.config/kwinrc" 2>/dev/null; then
    kwriteconfig5 --file kwinrc \
        --group "Compositing" \
        --key "Enabled" "false" 2>/dev/null || \
    echo "[Compositing]
Enabled=false" >> "$HOME/.config/kwinrc"
fi

# ── Step 8: Launch KDE Plasma ────────────────────────────

# Verify startplasma-x11 is actually installed
if ! command -v startplasma-x11 >/dev/null 2>&1; then
    echo ""
    echo "❌ FluxLinux Error: 'startplasma-x11' not found!"
    echo "   KDE Plasma does not appear to be fully installed."
    echo "   Please run the KDE setup script again:"
    echo "     bash setup_kde_termux.sh"
    echo ""
    echo "   You can also check manually in Termux:"
    echo "     pkg list-installed | grep plasma"
    echo "     which startplasma-x11"
    echo ""
    read -p "Press Enter to exit..."
    exit 1
fi

echo "FluxLinux: Launching KDE Plasma (this may take 30-60s)..."
echo ""
echo "  ┌─────────────────────────────────────────┐"
echo "  │  Open the Termux:X11 app to see desktop │"
echo "  └─────────────────────────────────────────┘"
echo ""

KDE_LOG="$HOME/.fluxlinux/kde_session.log"
mkdir -p "$HOME/.fluxlinux"

echo "✅ KDE Plasma is starting (GPU: $GPU_BACKEND)"
echo "   Session log: $KDE_LOG"
echo "   Output below — Ctrl+C to stop."
echo ""

# Run in foreground with tee so errors print live AND save to log.
# (dbus-launch --exit-with-session is NOT available in Termux)
startplasma-x11 2>&1 | tee "$KDE_LOG"

echo ""
echo "🔴 KDE Plasma session ended."
echo "   Check session log for errors: $KDE_LOG"
echo ""
