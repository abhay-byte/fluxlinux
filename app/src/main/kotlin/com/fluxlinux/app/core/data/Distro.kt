package com.fluxlinux.app.core.data

import androidx.compose.ui.graphics.Color

data class Distro(
    val id: String,         // e.g. "debian" (used for proot-distro command)
    val name: String,       // e.g. "Debian Bookworm"
    val description: String,// e.g. "Stable, reliable, and solid."
    val color: Color        // Accent color for the Glass Card
)
