package com.zenithblue.fluxlinux.core.data

import com.zenithblue.fluxlinux.core.model.SupportedDistro
import com.zenithblue.fluxlinux.ui.theme.FluxAccentMagenta
import com.zenithblue.fluxlinux.ui.theme.FluxAccentCyan
import androidx.compose.ui.graphics.Color
import com.zenithblue.fluxlinux.R


object DistroRepository {
    
    // Shared Components for Debian-based distros
    private val debianComponents = listOf(
        DistroComponent(
            id = "xfce4_desktop",
            name = "XFCE4 Desktop",
            description = "Base XFCE4 desktop environment — re-run to repair or update.",
            scriptName = "debian/common/setup/setup_debian_family.sh",
            sizeEstimate = "300 MB",
            isMandatory = false
        ),
        DistroComponent(
            id = "hw_accel",
            name = "Hardware Acceleration",
            description = "VirGL & Zink drivers for GPU acceleration. Mandatory for GUI.",
            scriptName = "debian/common/setup/setup_hw_accel_debian.sh",
            sizeEstimate = "50 MB",
            isMandatory = true
        ),
        DistroComponent(
            id = "customization",
            name = "XFCE4 Customization",
            description = "FluxLinux Pro Theme, Wallpapers, Fonts, and 2x Scaling for XFCE4.",
            scriptName = "debian/common/setup/setup_customization_debian.sh",
            sizeEstimate = "200 MB"
        ),
        DistroComponent(
            id = "kde_plasma",
            name = "KDE Plasma Desktop",
            description = "Full KDE Plasma DE with Konsole, Dolphin, Kate, Spectacle & goodies.",
            scriptName = "debian/common/setup/setup_kde_debian.sh",
            sizeEstimate = "800 MB"
        ),
        DistroComponent(
            id = "kde_customization",
            name = "KDE Desktop Customization",
            description = "FluxLinux Pro theme, Papirus icons, wallpapers & Zsh for KDE Plasma.",
            scriptName = "debian/common/setup/setup_customization_kde_debian.sh",
            sizeEstimate = "250 MB"
        ),
        DistroComponent(
            id = "app_dev",
            name = "App Development",
            description = "Android SDK, Flutter, IntelliJ IDEA, OpenJDK.",
            scriptName = "debian/common/setup/setup_appdev_debian.sh",
            sizeEstimate = "2.5 GB"
        ),
        DistroComponent(
            id = "web_dev",
            name = "Web Development",
            description = "Node.js, VS Code, Nginx, Python, Git.",
            scriptName = "debian/common/setup/setup_webdev_debian.sh",
            sizeEstimate = "800 MB"
        ),
        DistroComponent(
            id = "gen_dev",
            name = "General Coding",
            description = "C++, Rust, Go, LunarVim, Neovim, Build Essentials.",
            scriptName = "debian/common/setup/setup_gengdev_debian.sh",
            sizeEstimate = "800 MB"
        ),
        DistroComponent(
            id = "cybersec",
            name = "Cyber Security",
            description = "Kali Tools, Metasploit, Nmap, Wireshark, Aircrack-ng.",
            scriptName = "debian/common/setup/setup_cybersec_debian.sh",
            sizeEstimate = "2 GB"
        ),
        DistroComponent(
            id = "data_science",
            name = "Data Science",
            description = "Jupyter, Python Data Stack (Pandas, NumPy), R.",
            scriptName = "debian/common/setup/setup_datascience_debian.sh",
            sizeEstimate = "1 GB"
        ),
        DistroComponent(
            id = "gamedev",
            name = "Game Development",
            description = "Godot Engine, Blender, Raylib.",
            scriptName = "debian/common/setup/setup_gamedev_debian.sh",
            sizeEstimate = "1 GB"
        ),
         DistroComponent(
            id = "video_editing",
            name = "Video Editing",
            description = "Kdenlive, Shotcut, OpenShot, Flowblade.",
            scriptName = "debian/common/setup/setup_video_editing_debian.sh",
            sizeEstimate = "1 GB"
        ),
        DistroComponent(
            id = "office",
            name = "Office Suite",
            description = "LibreOffice, PDF Viewer, Email Client.",
            scriptName = "debian/common/setup/setup_office_debian.sh",
            sizeEstimate = "500 MB"
        ),
        DistroComponent(
            id = "graphic_design",
            name = "Graphic Design",
            description = "GIMP, Inkscape, Krita, and Blender for creative work.",
            scriptName = "debian/common/setup/setup_graphic_design_debian.sh",
            sizeEstimate = "1.2 GB"
        ),
        DistroComponent(
            id = "vulkan_llamacpp",
            name = "Vulkan Llama.cpp",
            description = "GPU-accelerated LLM inference via Vulkan. Uses Turnip on Adreno devices.",
            scriptName = "debian/common/setup/setup_vulkan_llamacpp_debian.sh",
            sizeEstimate = "500 MB"
        ),
        DistroComponent(
            id = "qwen35_model",
            name = "Qwen3.5-0.8B Model",
            description = "Download Qwen3.5-0.8B GGUF (Q4_0). Requires Vulkan Llama.cpp installed first.",
            scriptName = "debian/common/setup/setup_qwen35_debian.sh",
            sizeEstimate = "507 MB"
        ),
        DistroComponent(
            id = "qwen25_model",
            name = "Qwen2.5-1.5B Model",
            description = "Qwen2.5-1.5B-Instruct GGUF (Vulkan GPU compatible). Replaces Qwen3.5 GDN.",
            scriptName = "debian/common/setup/setup_qwen25_debian.sh",
            sizeEstimate = "935 MB"
        ),
        DistroComponent(
            id = "emulation",
            name = "Retro Emulation",
            description = "RetroArch, various emulator cores.",
            scriptName = "debian/common/setup/setup_emulation_debian.sh",
            sizeEstimate = "1 GB",
            comingSoon = true
        )
    )

    // Shared Components for Termux Native
    private val termuxComponents = listOf(
        DistroComponent(
            id = "xfce4_desktop",
            name = "XFCE4 Desktop",
            description = "Native XFCE4 desktop via Termux:X11 — no container overhead.",
            scriptName = "termux/setup/setup_xfce4_termux.sh",
            sizeEstimate = "300 MB",
            isMandatory = false
        ),
        DistroComponent(
            id = "hw_accel",
            name = "Hardware Acceleration",
            description = "VirGL & Turnip GPU drivers for native Termux acceleration.",
            scriptName = "termux/setup/setup_hw_accel_termux.sh",
            sizeEstimate = "50 MB",
            isMandatory = true
        ),
        DistroComponent(
            id = "customization",
            name = "XFCE4 Customization",
            description = "FluxLinux Pro theme, wallpapers, and fonts for XFCE4.",
            scriptName = "termux/setup/setup_customization_termux.sh",
            sizeEstimate = "200 MB"
        ),
        DistroComponent(
            id = "kde_plasma",
            name = "KDE Plasma Desktop",
            description = "Full KDE Plasma desktop running natively in Termux.",
            scriptName = "termux/setup/setup_kde_termux.sh",
            sizeEstimate = "800 MB"
        ),
        DistroComponent(
            id = "kde_customization",
            name = "KDE Desktop Customization",
            description = "FluxLinux Pro theme, Papirus icons & Zsh for native KDE Plasma.",
            scriptName = "termux/setup/setup_customization_kde_termux.sh",
            sizeEstimate = "200 MB"
        )
    )

    // Shared Components for Arch-based distros
    private val archComponents = listOf(
        DistroComponent(
            id = "arch_desktop",
            name = "XFCE4 Desktop",
            description = "Installs XFCE4 Desktop Environment and TigerVNC.",
            scriptName = "arch/common/setup/setup_arch_family.sh",
            sizeEstimate = "300 MB",
            isMandatory = true
        )
    )

    val supportedDistros = listOf(
        // Currently Available
        Distro(
            id = "debian",
            name = "Debian",
            description = "The universal operating system. Stable and reliable.",
            color = FluxAccentMagenta,
            iconRes = R.drawable.distro_debian,
            comingSoon = false,
            prootSupported = true,
            chrootSupported = true,
            configuration = SupportedDistro.DEBIAN,
            components = debianComponents
        ),
        
        Distro(
            id = "termux",
            name = "Termux Native",
            description = "Run XFCE4/KDE directly in Termux for max performance (No Proot, No Container).",
            color = FluxAccentCyan,
            iconRes = R.drawable.distro_termux,
            comingSoon = false,
            prootSupported = false,
            chrootSupported = false,
            configuration = SupportedDistro.TERMUX,
            components = termuxComponents
        ),


        Distro(
            id = "debian13_chroot",
            name = "Debian (Rooted)",
            description = "High-performance Debian 13 (Trixie) environment via Chroot (Requires Root).",
            color = FluxAccentMagenta,
            iconRes = R.drawable.distro_debian,
            comingSoon = false,
            prootSupported = false,
            chrootSupported = true,
            configuration = SupportedDistro.DEBIAN,
            components = debianComponents
        ),
        
        // Coming Soon - Sorted alphabetically
        Distro(
            id = "adelie",
            name = "Adélie Linux",
            description = "Independent Linux distribution committed to integrity and simplicity.",
            color = Color(0xFF9C27B0),
            iconRes = R.drawable.distro_adelie,
            comingSoon = true,
            prootSupported = false, // no i686 support
            chrootSupported = true
        ),
        Distro(
            id = "alpine",
            name = "Alpine Linux",
            description = "Security-oriented, lightweight Linux distribution.",
            color = Color(0xFF0D597F),
            iconRes = R.drawable.distro_alpine,
            comingSoon = true,
            prootSupported = true, // frozen version
            chrootSupported = true
        ),
        Distro(
            id = "archlinux",
            name = "Arch Linux",
            description = "A simple, lightweight Linux distribution.",
            color = Color(0xFF1793D1),
            iconRes = R.drawable.distro_arch,
            comingSoon = true,
            prootSupported = true,
            chrootSupported = true,
            configuration = SupportedDistro.ARCH,
            components = archComponents
        ),
        Distro(
            id = "artix",
            name = "Artix Linux",
            description = "Arch-based distribution without systemd.",
            color = Color(0xFF10A0CC),
            iconRes = R.drawable.distro_artix,
            comingSoon = true,
            prootSupported = true, // aarch64 only
            chrootSupported = true
        ),
        Distro(
            id = "backbox",
            name = "BackBox",
            description = "Ubuntu-based distribution for penetration testing.",
            color = Color(0xFF000000),
            iconRes = R.drawable.distro_backbox,
            comingSoon = true,
            prootSupported = false, // Not in proot-distro
            chrootSupported = true
        ),
        Distro(
            id = "centos_stream",
            name = "CentOS Stream",
            description = "Continuously delivered distro that tracks ahead of RHEL.",
            color = Color(0xFF262577),
            iconRes = R.drawable.distro_centos_stream,
            comingSoon = true,
            prootSupported = false, // Not in proot-distro
            chrootSupported = true
        ),
        Distro(
            id = "chimera",
            name = "Chimera Linux",
            description = "Modern, general-purpose Linux distribution.",
            color = Color(0xFFFF6B35),
            iconRes = R.drawable.distro_chimera,
            comingSoon = true,
            prootSupported = true, // unstable
            chrootSupported = true
        ),
        Distro(
            id = "deepin",
            name = "Deepin",
            description = "Debian-based distribution with beautiful DDE.",
            color = Color(0xFF2CA7F8),
            iconRes = R.drawable.distro_deepin,
            comingSoon = true,
            prootSupported = true, // only 64bit
            chrootSupported = true
        ),
        Distro(
            id = "fedora",
            name = "Fedora",
            description = "Innovative platform for hardware, clouds, and containers.",
            color = Color(0xFF294172),
            iconRes = R.drawable.distro_fedora,
            comingSoon = true,
            prootSupported = true, // unstable
            chrootSupported = true
        ),
        Distro(
            id = "gentoo",
            name = "Gentoo",
            description = "Flexible, source-based Linux distribution.",
            color = Color(0xFF54487A),
            iconRes = R.drawable.distro_gentoo,
            comingSoon = true,
            prootSupported = false, // Not in proot-distro
            chrootSupported = true
        ),
        Distro(
            id = "kali",
            name = "Kali Linux",
            description = "Advanced penetration testing and security auditing.",
            color = Color(0xFF367BF5),
            iconRes = R.drawable.distro_kali,
            comingSoon = true,
            prootSupported = false, // Not in proot-distro
            chrootSupported = true
        ),
        Distro(
            id = "manjaro",
            name = "Manjaro",
            description = "User-friendly Arch-based distribution.",
            color = Color(0xFF35BF5C),
            iconRes = R.drawable.distro_manjaro,
            comingSoon = true,
            prootSupported = true, // aarch64 only
            chrootSupported = true
        ),
        Distro(
            id = "openkylin",
            name = "OpenKylin",
            description = "Community-driven Linux distribution from China.",
            color = Color(0xFF0066CC),
            iconRes = R.drawable.distro_openkylin,
            comingSoon = true,
            prootSupported = false, // Not in proot-distro
            chrootSupported = true
        ),
        Distro(
            id = "opensuse",
            name = "OpenSUSE",
            description = "Stable, easy to use and complete multi-purpose distribution.",
            color = Color(0xFF73BA25),
            iconRes = R.drawable.distro_opensuse,
            comingSoon = true,
            prootSupported = true, // only 64bit
            chrootSupported = true
        ),
        Distro(
            id = "parrot",
            name = "Parrot OS",
            description = "Security-focused distribution for pentesting and privacy.",
            color = Color(0xFF00D9FF),
            iconRes = R.drawable.distro_parrot,
            comingSoon = true,
            prootSupported = false, // Not in proot-distro
            chrootSupported = true
        ),
        Distro(
            id = "rocky",
            name = "Rocky Linux",
            description = "Enterprise-grade Linux distribution.",
            color = Color(0xFF10B981),
            iconRes = R.drawable.distro_rocky,
            comingSoon = true,
            prootSupported = true, // only 64bit
            chrootSupported = true
        ),
        Distro(
            id = "ubuntu",
            name = "Ubuntu",
            description = "The world's most popular Linux distribution.",
            color = Color(0xFFE95420),
            iconRes = R.drawable.distro_ubuntu,
            comingSoon = true,
            prootSupported = true, // no i686
            chrootSupported = true,
            configuration = SupportedDistro.UBUNTU
        ),
        Distro(
            id = "void",
            name = "Void Linux",
            description = "Independent distribution with runit init system.",
            color = Color(0xFF478061),
            iconRes = R.drawable.distro_void,
            comingSoon = true,
            prootSupported = true,
            chrootSupported = true
        )
    )
}
