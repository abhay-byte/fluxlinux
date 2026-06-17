#!/bin/bash
# GPU Acceleration Diagnostic Script
# Run this inside Debian to check your GPU setup

echo "=========================================="
echo "  FluxLinux Pro GPU Acceleration Diagnostics"
echo "=========================================="
echo ""

# Check if gpu-launch exists
if [ -f "/usr/local/bin/gpu-launch" ]; then
    echo "✓ gpu-launch wrapper found"
    
    # Extract configured mode
    MODE=$(grep "^MODE=" /usr/local/bin/gpu-launch | cut -d'"' -f2)
    echo "  Configured mode: $MODE"
else
    echo "✗ gpu-launch not found - run setup_hw_accel_debian.sh first"
    exit 1
fi

echo ""
echo "Environment Variables:"
echo "  DISPLAY: ${DISPLAY:-not set}"
echo "  XDG_RUNTIME_DIR: ${XDG_RUNTIME_DIR:-not set}"
echo "  GALLIUM_DRIVER: ${GALLIUM_DRIVER:-not set}"
echo ""

# Check Mesa/Vulkan packages
echo "Installed Packages:"
dpkg -l | grep -E "mesa|vulkan|libgl" | awk '{print "  " $2 " - " $3}'
echo ""

# Check for Turnip drivers
if [ "$MODE" = "turnip" ]; then
    echo "Turnip (Adreno) Configuration:"
    if [ -f "/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json" ]; then
        echo "  ✓ Turnip ICD found"
    else
        echo "  ✗ Turnip ICD missing"
    fi
fi

# Check for VirGL
if [ "$MODE" = "virgl" ]; then
    echo "VirGL Configuration:"
    echo "  Note: VirGL requires virgl_test_server running in Termux"
    echo ""
    echo "  To check if server is running (from Termux):"
    echo "    ps aux | grep virgl"
    echo ""
    echo "  The server should be started by start_gui.sh"
fi

echo ""
echo "OpenGL Information:"
if command -v glxinfo >/dev/null 2>&1; then
    echo "  Renderer: $(glxinfo 2>/dev/null | grep "OpenGL renderer" | cut -d: -f2)"
    echo "  Version: $(glxinfo 2>/dev/null | grep "OpenGL version" | cut -d: -f2)"
else
    echo "  glxinfo not available (install mesa-utils)"
fi

echo ""
echo "=========================================="
echo "  Test Commands:"
echo "=========================================="
echo "  gpu-launch glxinfo | grep OpenGL"
echo "  gpu-launch glmark2"
echo "=========================================="
