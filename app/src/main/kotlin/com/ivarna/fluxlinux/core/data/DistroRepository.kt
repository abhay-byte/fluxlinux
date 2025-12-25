package com.ivarna.fluxlinux.core.data

import com.ivarna.fluxlinux.ui.theme.FluxAccentMagenta
import com.ivarna.fluxlinux.ui.theme.FluxAccentCyan

object DistroRepository {
    
    val supportedDistros = listOf(
        Distro(
            id = "debian",
            name = "Debian",
            description = "The universal operating system. Stable and reliable.",
            color = FluxAccentMagenta
        )
        // Future: Arch, Ubuntu etc.
    )
}
