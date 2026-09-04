package com.ivarna.fluxlinux.core.install

/**
 * Single source of truth for Play Feature Delivery payloads.
 * Maps distroId to its Dynamic Feature Module name and archive metadata.
 * References constants from [DistroInstallProfile].
 */
data class PlayPayloadInfo(
    val distroId: String,
    val moduleName: String,
    val archiveFileName: String,
    val sha256: String,
    val minBytes: Long,
    val compressedSize: Long,
)

object PlayPayloadRegistry {
    private val REGISTRY: Map<String, PlayPayloadInfo> = listOf(
        PlayPayloadInfo(
            distroId = "debian",
            moduleName = "distro_debian",
            archiveFileName = DistroInstallProfile.DEBIAN_ROOTFS_NAME,
            sha256 = DistroInstallProfile.DEBIAN_ROOTFS_SHA256,
            minBytes = DistroInstallProfile.DEBIAN_ROOTFS_MIN_BYTES,
            compressedSize = 85009380L,
        ),
        PlayPayloadInfo(
            distroId = "alpine",
            moduleName = "distro_alpine",
            archiveFileName = DistroInstallProfile.ALPINE_ROOTFS_NAME,
            sha256 = DistroInstallProfile.ALPINE_ROOTFS_SHA256,
            minBytes = DistroInstallProfile.ALPINE_ROOTFS_MIN_BYTES,
            compressedSize = 4023732L,
        ),
        PlayPayloadInfo(
            distroId = "ubuntu",
            moduleName = "distro_ubuntu",
            archiveFileName = DistroInstallProfile.UBUNTU_ROOTFS_NAME,
            sha256 = DistroInstallProfile.UBUNTU_ROOTFS_SHA256,
            minBytes = DistroInstallProfile.UBUNTU_ROOTFS_MIN_BYTES,
            compressedSize = 20734792L,
        ),
        PlayPayloadInfo(
            distroId = "kali",
            moduleName = "distro_kali",
            archiveFileName = DistroInstallProfile.KALI_ROOTFS_NAME,
            sha256 = DistroInstallProfile.KALI_ROOTFS_SHA256,
            minBytes = DistroInstallProfile.KALI_ROOTFS_MIN_BYTES,
            compressedSize = 123244844L,
        ),
        PlayPayloadInfo(
            distroId = "archlinux",
            moduleName = "distro_arch",
            archiveFileName = DistroInstallProfile.ARCH_ROOTFS_NAME,
            sha256 = DistroInstallProfile.ARCH_ROOTFS_SHA256,
            minBytes = DistroInstallProfile.ARCH_ROOTFS_MIN_BYTES,
            compressedSize = 116277544L,
        ),
        PlayPayloadInfo(
            distroId = "manjaro",
            moduleName = "distro_manjaro",
            archiveFileName = DistroInstallProfile.MANJARO_ROOTFS_NAME,
            sha256 = DistroInstallProfile.MANJARO_ROOTFS_SHA256,
            minBytes = DistroInstallProfile.MANJARO_ROOTFS_MIN_BYTES,
            compressedSize = 133044216L,
        ),
        PlayPayloadInfo(
            distroId = "chimera",
            moduleName = "distro_chimera",
            archiveFileName = DistroInstallProfile.CHIMERA_ROOTFS_NAME,
            sha256 = DistroInstallProfile.CHIMERA_ROOTFS_SHA256,
            minBytes = DistroInstallProfile.CHIMERA_ROOTFS_MIN_BYTES,
            compressedSize = 5343176L,
        ),
        PlayPayloadInfo(
            distroId = "fedora",
            moduleName = "distro_fedora",
            archiveFileName = DistroInstallProfile.FEDORA_ROOTFS_NAME,
            sha256 = DistroInstallProfile.FEDORA_ROOTFS_SHA256,
            minBytes = DistroInstallProfile.FEDORA_ROOTFS_MIN_BYTES,
            compressedSize = 30917104L,
        ),
        PlayPayloadInfo(
            distroId = "void",
            moduleName = "distro_void",
            archiveFileName = DistroInstallProfile.VOID_ROOTFS_NAME,
            sha256 = DistroInstallProfile.VOID_ROOTFS_SHA256,
            minBytes = DistroInstallProfile.VOID_ROOTFS_MIN_BYTES,
            compressedSize = 45789416L,
        ),
        PlayPayloadInfo(
            distroId = "opensuse",
            moduleName = "distro_opensuse",
            archiveFileName = DistroInstallProfile.OPENSUSE_ROOTFS_NAME,
            sha256 = DistroInstallProfile.OPENSUSE_ROOTFS_SHA256,
            minBytes = DistroInstallProfile.OPENSUSE_ROOTFS_MIN_BYTES,
            compressedSize = 22130672L,
        ),
        PlayPayloadInfo(
            distroId = "deepin",
            moduleName = "distro_deepin",
            archiveFileName = DistroInstallProfile.DEEPIN_ROOTFS_NAME,
            sha256 = DistroInstallProfile.DEEPIN_ROOTFS_SHA256,
            minBytes = DistroInstallProfile.DEEPIN_ROOTFS_MIN_BYTES,
            compressedSize = 55705284L,
        ),
        PlayPayloadInfo(
            distroId = "parrot",
            moduleName = "distro_parrot",
            archiveFileName = DistroInstallProfile.PARROT_ROOTFS_NAME,
            sha256 = DistroInstallProfile.PARROT_ROOTFS_SHA256,
            minBytes = DistroInstallProfile.PARROT_ROOTFS_MIN_BYTES,
            compressedSize = 111851420L,
        ),
    ).associateBy { it.distroId }

    private val ALIASES: Map<String, String> = mapOf(
        "debian13_chroot" to "debian",
        "debian_chroot" to "debian",
        "alpine_chroot" to "alpine",
        "ubuntu_chroot" to "ubuntu",
        "kali_chroot" to "kali",
        "archlinux_chroot" to "archlinux",
        "manjaro_chroot" to "manjaro",
        "chimera_chroot" to "chimera",
        "fedora_chroot" to "fedora",
        "void_chroot" to "void",
        "opensuse_chroot" to "opensuse",
        "deepin_chroot" to "deepin",
        "parrot_chroot" to "parrot",
    )

    fun resolveCanonicalDistroId(distroId: String): String =
        ALIASES[distroId] ?: distroId

    fun find(distroId: String): PlayPayloadInfo? =
        REGISTRY[resolveCanonicalDistroId(distroId)]

    fun require(distroId: String): PlayPayloadInfo =
        find(distroId) ?: error("Distro '$distroId' is not registered in PlayPayloadRegistry")

    fun contains(distroId: String): Boolean =
        REGISTRY.containsKey(resolveCanonicalDistroId(distroId))

    fun all(): List<PlayPayloadInfo> = REGISTRY.values.toList()
}
