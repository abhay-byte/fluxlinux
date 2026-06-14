#!/bin/bash
# scripts/common/setup_qwen35_debian.sh
# Download Qwen3.5-0.8B GGUF model for llama.cpp
# Model: https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF
# Usage: setup_qwen35_debian.sh [uninstall]

# Model files to remove on uninstall
MODEL_FILES=(
    "/root/models/Qwen3.5-0.8B-Q4_0.gguf"
    "/root/models/Qwen3.5-0.8B-Q4_0.gguf.tmp"
)

# ─── UNINSTALL MODE ──────────────────────────────────────────────────────
if [ "$1" = "uninstall" ]; then
    echo "FluxLinux: Uninstalling Qwen3.5-0.8B Model..."
    for f in "${MODEL_FILES[@]}"; do
        if [ -f "$f" ]; then
            rm -f "$f"
            echo "  Removed: $f"
        fi
    done
    echo "FluxLinux: Qwen3.5-0.8B Model Uninstalled."
    exit 0
fi
# ─── END UNINSTALL MODE ──────────────────────────────────────────────────

# Error Handler Function to pause and let user read logs
handle_error() {
    echo ""
    echo "❌ FluxLinux Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    echo "Please check the error message above."
    echo "You can copy the error output to share with support."
    echo "---------------------------------------------------"
    rm -f "$MODEL_FILE.tmp" 2>/dev/null
    read -p "Press Enter to exit..."
    exit 1
}

echo "FluxLinux: Setting up Qwen3.5-0.8B Model..."

# --- 1. Check llama.cpp is installed ---
echo "FluxLinux: Checking llama.cpp installation..."
if [ ! -f "/usr/local/bin/llama-vulkan" ] && [ ! -f "/usr/local/bin/llama-cli" ]; then
    echo " [❌] llama.cpp not found!"
    echo "      Install 'Vulkan Llama.cpp' first from distro settings."
    handle_error "llama.cpp not installed"
fi
echo " [✅] llama.cpp found."

# --- 2. Create models directory ---
MODELS_DIR="/root/models"
mkdir -p "$MODELS_DIR" || handle_error "Create models directory"
echo " [✅] Models directory: $MODELS_DIR"

# --- 3. Download model ---
MODEL_URL="https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_0.gguf"
MODEL_FILE="$MODELS_DIR/Qwen3.5-0.8B-Q4_0.gguf"
MODEL_TMP="$MODELS_DIR/Qwen3.5-0.8B-Q4_0.gguf.tmp"
EXPECTED_SIZE=507154688
EXPECTED_SHA256="444406ddd926550c724ec18d5120a9d40ded44908a063b0e66e9a7e5464c652c"

if [ -f "$MODEL_FILE" ]; then
    FILE_SIZE=$(stat -c%s "$MODEL_FILE" 2>/dev/null || echo "0")
    if [ "$FILE_SIZE" -eq "$EXPECTED_SIZE" ]; then
        FILE_SHA=$(sha256sum "$MODEL_FILE" 2>/dev/null | cut -d' ' -f1)
        if [ "$FILE_SHA" = "$EXPECTED_SHA256" ]; then
            echo " [✅] Model already downloaded and verified: $MODEL_FILE"
            echo "      Size: $(du -h "$MODEL_FILE" | cut -f1)"
        else
            echo " [⚠️] Model file SHA256 mismatch. Re-downloading..."
            rm -f "$MODEL_FILE"
        fi
    else
        echo " [⚠️] Model file size mismatch ($FILE_SIZE vs $EXPECTED_SIZE bytes). Re-downloading..."
        rm -f "$MODEL_FILE"
    fi
fi

if [ ! -f "$MODEL_FILE" ]; then
    echo "FluxLinux: Downloading Qwen3.5-0.8B Q4_0 (507 MB)..."
    echo "Source: huggingface.co/unsloth/Qwen3.5-0.8B-GGUF"
    echo ""
    curl -L -o "$MODEL_TMP" \
        --progress-bar \
        --fail \
        --retry 3 \
        "$MODEL_URL" \
        || { rm -f "$MODEL_TMP"; handle_error "Download Model"; }

    # Verify file size
    DL_SIZE=$(stat -c%s "$MODEL_TMP" 2>/dev/null || echo "0")
    if [ "$DL_SIZE" -ne "$EXPECTED_SIZE" ]; then
        echo " [❌] Download size mismatch: got $DL_SIZE bytes, expected $EXPECTED_SIZE"
        rm -f "$MODEL_TMP"
        handle_error "Download incomplete"
    fi

    # Verify SHA256
    DL_SHA=$(sha256sum "$MODEL_TMP" 2>/dev/null | cut -d' ' -f1)
    if [ "$DL_SHA" != "$EXPECTED_SHA256" ]; then
        echo " [❌] SHA256 mismatch: got $DL_SHA"
        rm -f "$MODEL_TMP"
        handle_error "SHA256 verification failed"
    fi

    # Verify GGUF magic bytes
    MAGIC=$(xxd -l 4 -p "$MODEL_TMP" 2>/dev/null || echo "")
    if [ "$MAGIC" != "47475546" ]; then
        echo " [❌] File is not a valid GGUF model (magic bytes mismatch)"
        rm -f "$MODEL_TMP"
        handle_error "Invalid GGUF file"
    fi

    mv "$MODEL_TMP" "$MODEL_FILE" || handle_error "Move model file"
    echo ""
    echo " [✅] Model downloaded and verified!"
    echo "      Size: $(du -h "$MODEL_FILE" | cut -f1)"
fi

# --- 4. Verify installation ---
if [ ! -f "$MODEL_FILE" ]; then
    handle_error "Model file not found after download"
fi
echo " [✅] Model verified: $MODEL_FILE"

echo ""
echo "============================================"
echo "  ✅ Qwen3.5-0.8B Model Ready!"
echo "============================================"
echo ""
echo "Model: $MODEL_FILE"
echo "Size:  $(du -h "$MODEL_FILE" | cut -f1)"
echo "Quant: Q4_0 (best for Adreno GPU)"
echo ""
echo "Run with GPU:"
echo "  llama-vulkan llama-cli -m $MODEL_FILE -p 'Hello' -ngl 99"
echo ""
echo "Interactive chat:"
echo "  llama-vulkan llama-cli -m $MODEL_FILE -cnv -ngl 99"
echo ""
echo "Start server:"
echo "  llama-vulkan llama-server -m $MODEL_FILE --port 8080 -ngl 99"
echo ""
echo "Use -ngl 99 to offload all layers to GPU."
echo "============================================"
