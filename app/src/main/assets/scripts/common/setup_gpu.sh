#!/bin/bash
# scripts/common/setup_gpu.sh
# Installs Hardware Acceleration dependencies for Termux

echo "FluxLinux: Setting up GPU Acceleration..."

# 0. Enable required repositories (Critical for mesa-zink/drivers)
pkg install -y x11-repo tur-repo
pkg update -y

# 1. Install packages
# mesa-zink: Zink driver
# virglrenderer*: Virtualized rendering
# vulkan-loader-android: Vulkan
pkg install -y mesa-zink virglrenderer-mesa-zink vulkan-loader-android virglrenderer-android

echo "FluxLinux: GPU Dependencies Installed."
echo "Use 'ha <command>' to launch with acceleration."
