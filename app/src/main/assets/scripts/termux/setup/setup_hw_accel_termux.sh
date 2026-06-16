#!/bin/bash
# ============================================================
# FluxLinux — Native Termux Hardware Acceleration Setup
# Location: termux/setup/setup_hw_accel_termux.sh
# Runs on: HOST Termux (native, no container)
# Root required: no
#
# Installs VirGL + Turnip/Zink GPU acceleration packages
# and auto-detects the best backend for the device GPU.
#
# GPU support:
#   VirGL   → All devices (via Android OpenGL ES bridge)
#   Turnip  → Qualcomm Adreno 6xx/7xx/8xx (Snapdragon)
#   Zink    → OpenGL-over-Vulkan (requires Turnip or Mali Vulkan)
#   LLVMpipe → Software fallback (any device, slowest)
# ============================================================

CALLBACK_NAME="hw_accel"

send_callback() {
    RESULT="$1"
    am start -a android.intent.action.VIEW \
      -d "fluxlinux://callback?result=${RESULT}&name=${CALLBACK_NAME}" \
      --flags 0x10000000 2>/dev/null || true
}

handle_error() {
    echo ""
    echo "❌ FluxLinux Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    echo "Please check the error message above for details."
    echo "---------------------------------------------------"
    send_callback "error"
    read -p "Press Enter to acknowledge error and exit..."
    exit 1
}

echo ""
echo "══════════════════════════════════════════════"
echo "  FluxLinux — Native Termux Hardware Accel"
echo "══════════════════════════════════════════════"
echo ""

# ── Step 1: Repositories ──────────────────────────────────
echo "FluxLinux: Enabling required repositories..."
pkg install x11-repo -y || handle_error "x11-repo install"
pkg install tur-repo -y || handle_error "tur-repo install"
pkg update -y || handle_error "pkg update"

# ── Step 2: VirGL + Mesa packages ────────────────────────
echo ""
echo "===== Installing VirGL + Mesa Zink ====="
pkg install -y \
    virglrenderer-mesa-zink \
    mesa-zink \
    || handle_error "VirGL/Mesa install"

# ── Step 3: GPU auto-detection ───────────────────────────
echo ""
echo "===== Detecting GPU Backend ====="

detect_gpu_backend() {
    # Method 1: Android hardware property (most reliable)
    local vulkan_hw
    vulkan_hw=$(getprop ro.hardware.vulkan 2>/dev/null || echo "")
    if echo "$vulkan_hw" | grep -qi "adreno\|freedreno"; then
        echo "turnip"
        return
    fi

    # Method 2: GPU renderer property
    local gpu_renderer
    gpu_renderer=$(getprop ro.hardware.egl 2>/dev/null || echo "")
    if echo "$gpu_renderer" | grep -qi "adreno"; then
        echo "turnip"
        return
    fi

    # Method 3: CPU info (Snapdragon = Adreno GPU)
    if grep -qi "qualcomm\|snapdragon" /proc/cpuinfo 2>/dev/null; then
        echo "turnip"
        return
    fi

    # Method 4: Board property
    local board
    board=$(getprop ro.product.board 2>/dev/null || echo "")
    if echo "$board" | grep -qi "snapdragon\|sm[0-9]\|sdm[0-9]\|msm[0-9]"; then
        echo "turnip"
        return
    fi

    # Default: VirGL (works on all GPUs)
    echo "virgl"
}

GPU_BACKEND=$(detect_gpu_backend)
echo ""
echo " Detected GPU backend: [$GPU_BACKEND]"

# ── Step 4: Vulkan runtime (optional) ─────────────────────
echo ""
echo "===== Installing Vulkan Runtime (Optional) ====="
VULKAN_OK=0

# On Adreno (turnip), Termux's freedreno ICD depends on vulkan-loader-generic,
# which conflicts with vulkan-loader-android. Remove the conflicting loader
# first so the freedreno install can succeed.
remove_vulkan_android_loader() {
    if dpkg -s vulkan-loader-android >/dev/null 2>&1; then
        echo " [🔧] Removing conflicting vulkan-loader-android (needed for freedreno)..."
        apt remove -y vulkan-loader-android 2>/dev/null \
            || pkg uninstall -y vulkan-loader-android 2>/dev/null \
            || true
    fi
}

if [ "$GPU_BACKEND" = "turnip" ]; then
    echo " [🎯] Adreno GPU detected — installing Turnip Vulkan stack"
    remove_vulkan_android_loader
    # Prefer the dri3 variant (newer, better DRI3 integration) — fall back
    # to the plain freedreno ICD if -dri3 isn't available in this Termux repo.
    pkg install -y vulkan-loader-generic mesa-vulkan-icd-freedreno-dri3 \
        && VULKAN_OK=1
    if [ "$VULKAN_OK" -ne 1 ]; then
        pkg install -y vulkan-loader-generic mesa-vulkan-icd-freedreno \
            && VULKAN_OK=1
    fi
fi

if [ "$VULKAN_OK" -ne 1 ]; then
    # Non-Adreno path: keep vulkan-loader-android (works with swiftshader/swrast)
    pkg install -y vulkan-loader-android swiftshader && VULKAN_OK=1
fi

if [ "$VULKAN_OK" -ne 1 ]; then
    pkg install -y vulkan-loader-android mesa-vulkan-icd-swrast && VULKAN_OK=1
fi

if [ "$VULKAN_OK" -ne 1 ]; then
    echo " [⚠️] Vulkan runtime packages unavailable on this mirror/device."
    echo "      Falling back to VirGL compatibility mode."
    GPU_BACKEND="virgl"
fi

# Save detection result for start scripts to use
mkdir -p "$HOME/.fluxlinux"
echo "FLUX_GPU_BACKEND=$GPU_BACKEND" > "$HOME/.fluxlinux/gpu_config"

case "$GPU_BACKEND" in
    turnip)
        echo " [✅] Adreno GPU detected — Turnip + Zink will be used (best performance)"
        echo "      VirGL server will run in Zink mode for maximum GPU utilization."
        ;;
    virgl)
        echo " [✅] Using VirGL (general compatibility mode)"
        echo "      Works on Mali, Adreno, and other GPUs via Android OpenGL ES bridge."
        ;;
esac

# ── Step 5: Write GPU config helper ──────────────────────
echo ""
echo "===== Writing GPU Config Helper ====="
cat > "$HOME/.fluxlinux/start_virgl.sh" << 'GPUEOF'
#!/bin/bash
# FluxLinux GPU Acceleration Starter
# Auto-sourced by start_xfce4_termux.sh and start_kde_termux.sh

GPU_CONFIG="$HOME/.fluxlinux/gpu_config"
GPU_BACKEND="virgl"
[ -f "$GPU_CONFIG" ] && source "$GPU_CONFIG" && GPU_BACKEND="${FLUX_GPU_BACKEND:-virgl}"

# Kill any previous virgl server
pkill -f "virgl_test_server" 2>/dev/null || true
sleep 1

case "$GPU_BACKEND" in
    turnip)
        echo "FluxLinux: Starting VirGL server (Turnip/Zink mode)..."
        MESA_NO_ERROR=1 \
        MESA_GL_VERSION_OVERRIDE=4.3COMPAT \
        MESA_GLES_VERSION_OVERRIDE=3.2 \
        GALLIUM_DRIVER=zink \
        MESA_LOADER_DRIVER_OVERRIDE=zink \
        ZINK_DESCRIPTORS=lazy \
        virgl_test_server --use-egl-surfaceless --use-gles &
        ;;
    virgl)
        echo "FluxLinux: Starting VirGL server (VirGL mode)..."
        virgl_test_server_android &
        ;;
    software)
        echo "FluxLinux: Software rendering mode (no virgl server)..."
        export GALLIUM_DRIVER=llvmpipe
        export LIBGL_ALWAYS_SOFTWARE=1
        return 0
        ;;
esac

sleep 2

# Client-side env vars (always set these regardless of mode)
export LIBGL_ALWAYS_INDIRECT=1
export GALLIUM_DRIVER=virpipe
export MESA_GL_VERSION_OVERRIDE=4.3COMPAT
export MESA_GLES_VERSION_OVERRIDE=3.2

echo "FluxLinux: GPU acceleration active [$GPU_BACKEND]"
GPUEOF
chmod +x "$HOME/.fluxlinux/start_virgl.sh"
echo " [✅] GPU config helper written to ~/.fluxlinux/start_virgl.sh"

# ── Verification ─────────────────────────────────────────
verify_installation() {
    echo ""
    echo "🔎 FluxLinux: Verifying Hardware Acceleration..."
    echo "------------------------------------------------"
    MISSING=0

    if command -v virgl_test_server_android >/dev/null 2>&1 || \
       command -v virgl_test_server >/dev/null 2>&1; then
        echo " [✅] VirGL Test Server"
    else
        echo " [❌] VirGL Test Server Missing"
        MISSING=1
    fi

    # Check for mesa vulkan libs
    if ls "$PREFIX/lib/libvulkan"* 2>/dev/null | head -1 | grep -q "vulkan"; then
        echo " [✅] Vulkan Libraries"
    else
        echo " [⚠️] Vulkan libraries not found (Turnip may not work)"
    fi

    if [ -f "$HOME/.fluxlinux/gpu_config" ]; then
        echo " [✅] GPU Config: $(cat $HOME/.fluxlinux/gpu_config)"
    fi

    echo "------------------------------------------------"
    if [ $MISSING -eq 1 ]; then
        echo "⚠️  VirGL install failed. Software rendering will be used."
        return 1
    else
        echo "🎉 Hardware acceleration configured!"
        echo "   Backend: $GPU_BACKEND"
        echo "   Config saved to: ~/.fluxlinux/gpu_config"
        return 0
    fi
}

verify_installation || handle_error "verification"

# ── Callback to app ──────────────────────────────────────
send_callback "success"

echo ""
echo "Hardware acceleration setup complete."
echo "GPU backend [$GPU_BACKEND] saved — start scripts will use it automatically."
read -p "Press Enter to close..."
