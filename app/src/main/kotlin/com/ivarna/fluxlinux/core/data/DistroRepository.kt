
import com.ivarna.fluxlinux.core.model.SupportedDistro
import com.ivarna.fluxlinux.ui.theme.FluxAccentMagenta
import com.ivarna.fluxlinux.ui.theme.FluxAccentCyan
import androidx.compose.ui.graphics.Color
import com.ivarna.fluxlinux.R

object DistroRepository {
    
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
            configuration = SupportedDistro.DEBIAN
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
            id = "arch",
            name = "Arch Linux",
            description = "A simple, lightweight Linux distribution.",
            color = Color(0xFF1793D1),
            iconRes = R.drawable.distro_arch,
            comingSoon = true,
            prootSupported = true,
            chrootSupported = true
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
