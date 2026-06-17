#!/bin/bash
# scripts/common/launch_qwen25.sh
# Launch Qwen2.5-1.5B-Instruct via llama-vulkan

handle_error() {
    echo ""
    echo "❌ FluxLinux Pro Error: $1"
    echo "---------------------------------------------------"
    read -p "Press Enter to exit..."
    exit 1
}

MODEL_PATH="/root/models/Qwen2.5-1.5B-Instruct-Q4_0.gguf"

echo "FluxLinux Pro: Running Qwen2.5-1.5B-Instruct..."
echo ""

# --- Check model ---
if [ ! -f "$MODEL_PATH" ]; then
    echo " [❌] Model not found: $MODEL_PATH"
    echo "      Install 'Qwen2.5-1.5B Model' from distro settings first."
    handle_error "Model not found"
fi
FILE_SIZE=$(stat -c%s "$MODEL_PATH" 2>/dev/null || echo "0")
if [ "$FILE_SIZE" -lt 900000000 ]; then
    echo " [❌] Model file appears incomplete ($FILE_SIZE bytes)."
    echo "      Re-install the model from distro settings."
    handle_error "Model incomplete"
fi
echo " [✅] Model found: $(du -h "$MODEL_PATH" | cut -f1)"

# --- Check llama.cpp ---
if command -v llama-vulkan &>/dev/null; then
    LAUNCHER="llama-vulkan llama-cli"
elif command -v llama-cli &>/dev/null; then
    LAUNCHER="llama-cli"
else
    echo " [❌] llama.cpp not found."
    echo "      Install 'Vulkan Llama.cpp' from distro settings first."
    handle_error "llama.cpp not installed"
fi
echo " [✅] llama.cpp found."

# --- Test CPU first to verify model integrity ---
echo ""
echo "Testing model with CPU (verifying file integrity)..."
CPU_OUTPUT=$(llama-cli -m "$MODEL_PATH" -ngl 0 -p "Say OK" -n 10 --temp 0.1 --no-display-prompt 2>/dev/null | tr -cd '[:print:][:space:]' | head -20)
if echo "$CPU_OUTPUT" | grep -qi "ok\|hello\|hi\|i am"; then
    echo " [✅] CPU test passed. Model file is valid."
else
    echo " [❌] CPU test failed. Model output is corrupted."
    echo "      Output: $CPU_OUTPUT"
    echo ""
    echo "      The model file may be damaged. Please:"
    echo "      1. Delete the model: rm $MODEL_PATH"
    echo "      2. Re-install from distro settings"
    handle_error "Model corrupted"
fi

# --- Test GPU ---
echo ""
echo "Testing GPU inference..."
GPU_OUTPUT=$(llama-vulkan llama-cli -m "$MODEL_PATH" -ngl 99 -p "Say OK" -n 10 --temp 0.1 --no-display-prompt 2>/dev/null | tr -cd '[:print:][:space:]' | head -20)
if echo "$GPU_OUTPUT" | grep -qi "ok\|hello\|hi\|i am"; then
    echo " [✅] GPU test passed. Using GPU acceleration."
    GPU_LAYERS=99
else
    echo " [⚠️] GPU test failed (produced garbled output)."
    echo "      GPU output: $GPU_OUTPUT"
    echo ""
    echo "      This usually means:"
    echo "      - Turnip driver compute shaders have issues with this model"
    echo "      - The Vulkan backend doesn't fully support this quantization"
    echo ""
    echo "      Falling back to CPU mode (-ngl 0)."
    echo "      CPU will be slower but should work correctly."
    GPU_LAYERS=0
fi

# --- Launch ---
echo ""
echo "Starting Qwen2.5-1.5B ($([ "$GPU_LAYERS" -gt 0 ] && echo "GPU" || echo "CPU"))..."
echo "Type your prompt and press Enter. Ctrl+C to exit."
echo "-------------------------------------------"
echo ""

exec llama-vulkan llama-cli -m "$MODEL_PATH" \
    -ngl $GPU_LAYERS \
    -c 4096 \
    --temp 0.7 \
    -n 512 \
    --no-display-prompt \
    -p "You are Qwen, a helpful assistant. User: Hello! Assistant:"
