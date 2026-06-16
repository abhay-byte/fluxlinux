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
#   3. Start VirGL server (auto-selects VirGL or Turnip)
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
pkill -f "kded5" 2>/dev/null || true
pkill -f "termux-x11" 2>/dev/null || true
pkill -f "virgl_test_server" 2>/dev/null || true
pkill -f "pulseaudio" 2>/dev/null || true
sleep 1

# ── Step 2: PulseAudio ────────────────────────────────────
echo "FluxLinux: Starting PulseAudio..."
pulseaudio --start \
    --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" \
    --exit-idle-time=-1 2>/dev/null || \
    echo " [⚠️] PulseAudio start failed — audio may not work"
export PULSE_SERVER=127.0.0.1

# ── Step 3: VirGL GPU server ──────────────────────────────
echo "FluxLinux: Starting GPU acceleration server..."
case "$GPU_BACKEND" in
    turnip)
        echo " Using Turnip + Zink (Adreno — best performance)"
        MESA_NO_ERROR=1 \
        MESA_GL_VERSION_OVERRIDE=4.3COMPAT \
        MESA_GLES_VERSION_OVERRIDE=3.2 \
        GALLIUM_DRIVER=zink \
        MESA_LOADER_DRIVER_OVERRIDE=zink \
        ZINK_DESCRIPTORS=lazy \
        virgl_test_server --use-egl-surfaceless --use-gles &
        ;;
    software)
        echo " Using software rendering (LLVMpipe)"
        export GALLIUM_DRIVER=llvmpipe
        export LIBGL_ALWAYS_SOFTWARE=1
        ;;
    virgl|*)
        echo " Using VirGL (general compatibility)"
        virgl_test_server_android &
        ;;
esac
sleep 2

# ── Step 4: Termux:X11 ───────────────────────────────────
echo "FluxLinux: Starting Termux:X11..."
if ! command -v termux-x11 >/dev/null 2>&1; then
    echo "❌ termux-x11 not found. Run 'setup_kde_termux.sh' first."
    read -p "Press Enter to exit..."
    exit 1
fi
termux-x11 :0 &
sleep 3

# ── Auto-open the Termux:X11 viewer ──────────────────────
echo "FluxLinux: Opening Termux:X11 viewer..."
am start -n com.termux.x11/.MainActivity 2>/dev/null || \
    echo " [⚠️] Could not auto-open Termux:X11 — please open it manually"
sleep 1

# ── Step 5: Export display env ───────────────────────────
export DISPLAY=:0
export LIBGL_ALWAYS_INDIRECT=1
case "$GPU_BACKEND" in
    turnip) export GALLIUM_DRIVER=virpipe; export MESA_LOADER_DRIVER_OVERRIDE=zink ;;
    software) ;; # already set above
    virgl|*) export GALLIUM_DRIVER=virpipe ;;
esac

# KDE-specific env: disable compositing at env level too
export KWIN_COMPOSE=0
export KWIN_OPENGL_INTERFACE=egl

# ── Step 6: D-Bus session ────────────────────────────────
echo "FluxLinux: Starting D-Bus session..."
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
echo "FluxLinux: Launching KDE Plasma..."
echo ""
echo "  ┌─────────────────────────────────────────┐"
echo "  │  Open the Termux:X11 app to see desktop │"
echo "  └─────────────────────────────────────────┘"
echo ""
startplasma-x11 &

sleep 5

echo ""
echo "✅ KDE Plasma is starting (GPU: $GPU_BACKEND)"
echo "   Switch to the Termux:X11 app to see the desktop."
echo "   Note: KDE may take 10-30 seconds to fully load."
echo ""
echo "   To stop: run 'stop_kde_termux.sh'"
echo "   Or press Ctrl+C here to stop."
echo ""

wait
