package com.ivarna.fluxlinux.core.install

/**
 * SHA-pinned archive hosted on the GitHub release tag `rootfs`.
 * Used by [RootfsDownloader] for both distro rootfs and host bootstrap tarballs.
 */
data class PinnedReleaseArchive(
    val fileName: String,
    val sha256: String,
    val url: String,
    val minBytes: Long
)
