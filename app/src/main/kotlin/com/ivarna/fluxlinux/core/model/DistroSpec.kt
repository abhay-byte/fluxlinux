package com.ivarna.fluxlinux.core.model

/**
 * Family of the Linux Distribution.
 * Used to group distros that share similar architecture or script requirements.
 */
enum class DistroFamily {
    DEBIAN,
    ARCH,
    FEDORA,
    ALPINE,
    VOID,
    SUSE,
    OTHER
}

/**
 * The primary Package Manager used by the distribution.
 */
enum class PackageManager {
    APT,
    PACMAN,
    DNF,
    APK,
    XBPS,
    ZYPPER,
    OTHER
}

/**
 * Release cycle type of the distribution.
 */
enum class ReleaseType {
    FIXED,      // Stable releases (e.g., Debian Stable, Ubuntu LTS)
    ROLLING,    // Rolling release (e.g., Arch, Void)
    SEMI_ROLLING // e.g. Fedora (Fixed but fast) or Debian Testing
}

/**
 * Pure enum representing supported Linux Distributions.
 * Each entry maps to its technical classification.
 */
enum class SupportedDistro(
    val id: String, // Internal ID used for proot scripts (e.g. "debian", "ubuntu")
    val family: DistroFamily,
    val packageManager: PackageManager,
    val releaseType: ReleaseType
) {
    DEBIAN(
        id = "debian",
        family = DistroFamily.DEBIAN,
        packageManager = PackageManager.APT,
        releaseType = ReleaseType.FIXED
    ),
    
    UBUNTU(
        id = "ubuntu",
        family = DistroFamily.DEBIAN, // Ubuntu is based on Debian
        packageManager = PackageManager.APT,
        releaseType = ReleaseType.FIXED
    ),
    
    // Future expansion placeholder
    KALI(
        id = "kali",
        family = DistroFamily.DEBIAN,
        packageManager = PackageManager.APT,
        releaseType = ReleaseType.ROLLING
    ),

    ARCH(
        id = "archlinux",
        family = DistroFamily.ARCH,
        packageManager = PackageManager.PACMAN,
        releaseType = ReleaseType.ROLLING
    )
}
