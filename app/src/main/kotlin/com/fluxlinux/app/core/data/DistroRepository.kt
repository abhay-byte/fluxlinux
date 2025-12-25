package com.fluxlinux.app.core.data

import com.fluxlinux.app.ui.theme.FluxAccentMagenta
import com.fluxlinux.app.ui.theme.FluxAccentCyan

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
