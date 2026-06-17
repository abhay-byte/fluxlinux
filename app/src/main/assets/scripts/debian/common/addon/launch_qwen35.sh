#!/bin/bash
# scripts/common/launch_qwen35.sh
# Launch Qwen3.5-0.8B interactive chat via llama-vulkan

# Error Handler
handle_error() {
    echo ""
    echo "❌ FluxLinux Pro Error: $1"
    echo "---------------------------------------------------"
    read -p "Press Enter to exit..."
    exit 1
}

MODEL_PATH="/root/models/Qwen3.5-0.8B-Q4_0.gguf"

echo "FluxLinux Pro: Starting Qwen3.5-0.8B Chat..."
echo ""

# --- Check model ---
if [ ! -f "$MODEL_PATH" ]; then
    echo " [❌] Model not found: $MODEL_PATH"
    echo "      Install 'Qwen3.5-0.8B Model' from distro settings first."
    handle_error "Model not found"
fi
echo " [✅] Model found: $MODEL_PATH"

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

# --- Launch ---
echo ""
echo "Starting interactive chat (GPU accelerated)..."
echo "Type your message and press Enter. Ctrl+C to exit."
echo "-------------------------------------------"
echo ""

exec $LAUNCHER -m "$MODEL_PATH" -cnv -ngl 99
