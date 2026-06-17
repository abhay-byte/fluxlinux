package com.zenithblue.fluxlinux.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector

// --- Data Models for UI ---
data class ComponentDetail(
    val icon: ImageVector,
    val packages: List<Pair<String, String>>, // Name -> Size
    val totalSizeValues: Double // In GB for calculation
)

// Map Component ID to Details
val componentDetailsMap = mapOf(
    "xfce4_desktop" to ComponentDetail(
        icon = Icons.Default.Brush,
        packages = listOf(
            "XFCE4 Desktop Environment" to "150 MB",
            "XFCE4 Goodies" to "80 MB",
            "TigerVNC Server" to "30 MB",
            "dbus-x11" to "10 MB"
        ),
        totalSizeValues = 0.3
    ),
    "hw_accel" to ComponentDetail(
        icon = Icons.Default.Speed,
        packages = listOf(
            "VirGL Renderer" to "15 MB",
            "Mesa Zink (Vulkan)" to "25 MB",
            "Hardware Drivers" to "10 MB"
        ),
        totalSizeValues = 0.05
    ),
    "customization" to ComponentDetail(
        icon = Icons.Default.Palette,
        packages = listOf(
            "Flux Theme Assets" to "120 MB",
            "Wallpapers (4K)" to "50 MB",
            "Nerd Fonts (JetBrainsMono)" to "30 MB"
        ),
        totalSizeValues = 0.2
    ),
    "kde_plasma" to ComponentDetail(
        icon = Icons.Default.Palette,
        packages = listOf(
            "KDE Plasma Desktop" to "250 MB",
            "Konsole Terminal" to "20 MB",
            "Dolphin File Manager" to "30 MB",
            "Kate Text Editor" to "25 MB",
            "Spectacle Screenshot" to "10 MB",
            "KDE Extras (Ark, Okular, Gwenview)" to "150 MB",
            "KWin Window Manager" to "30 MB"
        ),
        totalSizeValues = 0.75
    ),
    "kde_customization" to ComponentDetail(
        icon = Icons.Default.Brush,
        packages = listOf(
            "Flux Theme Assets" to "120 MB",
            "Papirus Icons (KDE)" to "50 MB",
            "Wallpapers (4K)" to "50 MB",
            "Nerd Fonts (JetBrainsMono)" to "30 MB",
            "Konsole Profile" to "1 MB"
        ),
        totalSizeValues = 0.25
    ),
    "app_dev" to ComponentDetail(
        icon = Icons.Default.Android,
        packages = listOf(
            "Android SDK (Cmdline Tools)" to "850 MB",
            "Flutter SDK (Stable)" to "1.1 GB",
            "OpenJDK 21 (JDK)" to "320 MB",
            "IntelliJ IDEA Community" to "800 MB",
            "Kotlin Compiler" to "80 MB",
            "Gradle 8.5" to "150 MB"
        ),
        totalSizeValues = 3.3
    ),
    "web_dev" to ComponentDetail(
        icon = Icons.Default.Language,
        packages = listOf(
            "Node.js v23 (LTS)" to "60 MB",
            "Python 3.11 + Pip" to "50 MB",
            "VS Code (ARM64)" to "350 MB",
            "Firefox Browser" to "250 MB",
            "Antigravity IDE" to "150 MB"
        ),
        totalSizeValues = 0.9
    ),
    "gen_dev" to ComponentDetail(
        icon = Icons.Default.Code,
        packages = listOf(
            "GCC & Clang Toolchain" to "400 MB",
            "Rust (Cargo/Rustup)" to "600 MB",
            "Go Language (1.22+)" to "450 MB",
            "Neovim + LunarVim" to "150 MB",
            "VS Code (Core)" to "350 MB"
        ),
        totalSizeValues = 1.95
    ),
    "cybersec" to ComponentDetail(
        icon = Icons.Default.Security,
        packages = listOf(
            "Metasploit Framework" to "1.2 GB",
            "Wireshark + TShark" to "150 MB",
            "Nmap & Netcat" to "40 MB",
            "Aircrack-ng Suite" to "50 MB",
            "Burp Suite Community" to "450 MB",
            "Hashcat & John" to "300 MB"
        ),
        totalSizeValues = 2.4 // Estimated
    ),
    "data_science" to ComponentDetail(
        icon = Icons.Default.ShowChart,
        packages = listOf(
            "Jupyter Lab & Notebook" to "150 MB",
            "Python Data Stack (Pandas/NumPy)" to "400 MB",
            "TensorFlow / PyTorch" to "1.5 GB",
            "Julia Language" to "300 MB",
            "R Language Base" to "200 MB",
            "PyCharm Community" to "650 MB"
        ),
        totalSizeValues = 3.2
    ),
    "gamedev" to ComponentDetail(
        icon = Icons.Default.SportsEsports,
        packages = listOf(
            "Godot Engine 4 (ARM64)" to "150 MB",
            "Blender 3D" to "600 MB",
            "Ren'Py SDK" to "300 MB",
            "LÖVE Framework" to "50 MB",
            "Raylib Headers" to "20 MB",
            "Python Game Libs" to "150 MB"
        ),
        totalSizeValues = 1.3
    ),
    "video_editing" to ComponentDetail(
        icon = Icons.Default.MovieCreation,
        packages = listOf(
            "Kdenlive (Video Editor)" to "400 MB",
            "FFmpeg Full Codecs" to "250 MB",
            "Shotcut" to "150 MB",
            "Audacity (Audio)" to "100 MB",
            "VLC Media Player" to "80 MB",
            "GStreamer Plugins" to "300 MB"
        ),
        totalSizeValues = 1.3
    ),
    "graphic_design" to ComponentDetail(
        icon = Icons.Default.Brush,
        packages = listOf(
            "GIMP (Photo Editor)" to "350 MB",
            "Inkscape (Vector)" to "250 MB",
            "Krita (Digital Art)" to "400 MB",
            "Blender 3D" to "600 MB",
            "Darktable (RAW)" to "150 MB",
            "Scribus (Publishing)" to "100 MB"
        ),
        totalSizeValues = 1.85
    ),
    "office" to ComponentDetail(
        icon = Icons.Default.Description,
        packages = listOf(
            "LibreOffice Suite" to "650 MB",
            "Thunderbird Email" to "120 MB",
            "Evince PDF Reader" to "20 MB",
            "Xournal++" to "15 MB"
        ),
        totalSizeValues = 0.8
    ),
    "vulkan_llamacpp" to ComponentDetail(
        icon = Icons.Default.SmartToy,
        packages = listOf(
            "llama.cpp (Vulkan backend)" to "200 MB",
            "Build tools (cmake, g++)" to "150 MB",
            "Vulkan headers & libs" to "50 MB",
            "SPIRV tools" to "30 MB"
        ),
        totalSizeValues = 0.5
    ),
    "qwen35_model" to ComponentDetail(
        icon = Icons.Default.SmartToy,
        packages = listOf(
            "Qwen3.5-0.8B Q4_0 GGUF" to "507 MB"
        ),
        totalSizeValues = 0.507
    ),
    "qwen25_model" to ComponentDetail(
        icon = Icons.Default.SmartToy,
        packages = listOf(
            "Qwen2.5-1.5B Q4_0 GGUF" to "938 MB"
        ),
        totalSizeValues = 0.935
    ),
    "emulation" to ComponentDetail(
        icon = Icons.Default.Gamepad,
        packages = listOf(
            "Box64 (x86_64 Emulator)" to "50 MB",
            "Wine / xow64 (Windows Compat)" to "800 MB",
            "Heroic Games Launcher" to "200 MB",
            "RetroArch" to "150 MB",
            "DOSBox" to "10 MB"
        ),
        totalSizeValues = 1.2
    )
)
