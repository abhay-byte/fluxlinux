package com.zenithblue.fluxlinux.core.data

import com.zenithblue.fluxlinux.core.model.SupportedDistro
import androidx.compose.ui.graphics.Color
import androidx.annotation.DrawableRes

data class DistroComponent(
    val id: String,
    val name: String,
    val description: String,
    val scriptName: String,
    val sizeEstimate: String,
    val isMandatory: Boolean = false,
    val comingSoon: Boolean = false
)

data class Distro(
    val id: String,              // e.g. "debian" (used for proot-distro command)
    val name: String,            // e.g. "Debian Bookworm"
    val description: String,     // e.g. "Stable, reliable, and solid."
    val color: Color,            // Accent color for the Glass Card
    @DrawableRes val iconRes: Int? = null,  // Drawable resource ID for logo
    val comingSoon: Boolean = false,  // If true, show "Coming Soon" badge
    val prootSupported: Boolean = true,  // PRoot compatibility
    val chrootSupported: Boolean = true,  // Chroot compatibility (requires root)
    val configuration: SupportedDistro? = null, // Technical traits (Family, Manager, etc.)
    val components: List<DistroComponent> = emptyList()
)
