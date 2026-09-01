package com.ivarna.fluxlinux.core.install

import org.json.JSONObject

/**
 * Build/runtime contract for the Play dynamic-feature payloads. The module
 * names are stable because they are part of the Play delivery API; a payload
 * hash change must be accompanied by a new archive filename/version.
 */
data class PlayFeaturePayloadSpec(
    val moduleName: String,
    val payloadId: String,
    val distroId: String,
    val assetPath: String,
    val archiveFileName: String,
    val sha256: String,
    val minBytes: Long
) {
    val verifiedSpec: VerifiedPayloadSpec
        get() = VerifiedPayloadSpec(archiveFileName, sha256, minBytes)

    val provenanceAssetPath: String
        get() = "payloads/$moduleName/provenance.json"
}

object PlayPayloadRegistry {
    const val PAYLOAD_SCHEMA_VERSION = 1
    const val PAYLOAD_VERSION = "2.0.0"
    const val ARCHITECTURE = "arm64-v8a"
    // These are provisioned Play archives, not the raw release assets used by
    // the Ivarna downloader. Keep this registry independent from the common
    // DistroInstallProfile release pins.
    const val DEBIAN_PLAY_ROOTFS_SHA256 =
        "4285f19f4b806f74a97269d692958c8c085e107ea370709311790b86712bf638"
    const val ALPINE_PLAY_BASELINE_SHA256 =
        "88714e4cc1637cdad5916200c5ac5b72c506506dd33166a12a0a58635618724c"
    const val UBUNTU_PLAY_ROOTFS_SHA256 =
        "fd8481763ac0b0f4757a1a3ac51fbc432be52b75c193b194b16dd1f63fb19bd9"
    const val KALI_PLAY_ROOTFS_SHA256 =
        "562696884422db47c19db561004b6981f9578677cb627ae3d716ad2979e8febe"
    const val ARCH_PLAY_ROOTFS_SHA256 =
        "fb5757ab558b420ca0a5bef3f5a6f9259d3456a3b37f60be052cf221d19de9ca"
    const val MANJARO_PLAY_ROOTFS_SHA256 =
        "59ef6613c1e9e3ea63660ba893b49c100d2eb770163759f6b044dd7c75d88e0a"
    const val CHIMERA_PLAY_ROOTFS_SHA256 =
        "d7b6ce933b5c0e4ea631158c87910915af8d5ae99160d212ee188087e70a1d91"

    val releaseDistroIds: Set<String> = setOf(
        "debian", "alpine", "ubuntu", "kali", "archlinux", "manjaro", "chimera"
    )

    val runtimeHost = PlayFeaturePayloadSpec(
        moduleName = "runtime_host",
        payloadId = "host_bootstrap_com.zenithblue.fluxlinux",
        distroId = "host",
        assetPath = "payloads/runtime_host/bootstrap.tar",
        archiveFileName = HostBootstrap.ZENITHBLUE.fileName,
        sha256 = HostBootstrap.ZENITHBLUE.sha256,
        minBytes = HostBootstrap.ZENITHBLUE.minBytes
    )

    private val rootfs = listOf(
        PlayFeaturePayloadSpec("distro_debian", "rootfs.debian", "debian", "payloads/distro_debian/${DistroInstallProfile.DEBIAN_ROOTFS_NAME}", DistroInstallProfile.DEBIAN_ROOTFS_NAME, DEBIAN_PLAY_ROOTFS_SHA256, DistroInstallProfile.DEBIAN_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_alpine", "rootfs.alpine", "alpine", "payloads/distro_alpine/${DistroInstallProfile.ALPINE_ROOTFS_PLAY_ASSET_NAME}", DistroInstallProfile.ALPINE_ROOTFS_NAME, ALPINE_PLAY_BASELINE_SHA256, DistroInstallProfile.ALPINE_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_ubuntu", "rootfs.ubuntu", "ubuntu", "payloads/distro_ubuntu/${DistroInstallProfile.UBUNTU_ROOTFS_NAME}", DistroInstallProfile.UBUNTU_ROOTFS_NAME, UBUNTU_PLAY_ROOTFS_SHA256, DistroInstallProfile.UBUNTU_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_kali", "rootfs.kali", "kali", "payloads/distro_kali/${DistroInstallProfile.KALI_ROOTFS_NAME}", DistroInstallProfile.KALI_ROOTFS_NAME, KALI_PLAY_ROOTFS_SHA256, DistroInstallProfile.KALI_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_arch", "rootfs.arch", "archlinux", "payloads/distro_arch/${DistroInstallProfile.ARCH_ROOTFS_NAME}", DistroInstallProfile.ARCH_ROOTFS_NAME, ARCH_PLAY_ROOTFS_SHA256, DistroInstallProfile.ARCH_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_manjaro", "rootfs.manjaro", "manjaro", "payloads/distro_manjaro/${DistroInstallProfile.MANJARO_ROOTFS_NAME}", DistroInstallProfile.MANJARO_ROOTFS_NAME, MANJARO_PLAY_ROOTFS_SHA256, DistroInstallProfile.MANJARO_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_chimera", "rootfs.chimera", "chimera", "payloads/distro_chimera/${DistroInstallProfile.CHIMERA_ROOTFS_NAME}", DistroInstallProfile.CHIMERA_ROOTFS_NAME, CHIMERA_PLAY_ROOTFS_SHA256, DistroInstallProfile.CHIMERA_ROOTFS_MIN_BYTES)
    )

    private val byArchiveName = rootfs.associateBy { it.archiveFileName }

    fun forProfile(profile: DistroInstallProfile): PlayFeaturePayloadSpec? =
        byArchiveName[profile.rootfsFileName]

    fun allRootfs(): List<PlayFeaturePayloadSpec> = rootfs
}

data class PlayPayloadProvenance(
    val schemaVersion: Int,
    val payloadId: String,
    val payloadVersion: String,
    val distroId: String,
    val architecture: String,
    val archiveFileName: String,
    val archiveSha256: String,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val upstreamSource: String?,
    val upstreamChecksum: String?,
    val inputSource: String?,
    val inputSourceSha256: String?,
    val sourceCommit: String,
    val buildScript: String,
    val buildDate: String,
    val fluxCustomizations: String
) {
    companion object {
        fun parse(json: String): PlayPayloadProvenance {
            val value = JSONObject(json)
            return PlayPayloadProvenance(
                schemaVersion = value.optInt("schemaVersion", -1),
                payloadId = value.optString("payloadId"),
                payloadVersion = value.optString("payloadVersion"),
                distroId = value.optString("distroId"),
                architecture = value.optString("architecture"),
                archiveFileName = value.optString("archiveFileName"),
                archiveSha256 = value.optString("archiveSha256"),
                compressedSize = value.optLong("compressedSize", -1L),
                uncompressedSize = value.optLong("uncompressedSize", -1L),
                upstreamSource = value.optString("upstreamSource").takeIf { it.isNotBlank() },
                upstreamChecksum = value.optString("upstreamChecksum").takeIf { it.isNotBlank() },
                inputSource = value.optString("inputSource").takeIf { it.isNotBlank() },
                inputSourceSha256 = value.optString("inputSourceSha256").takeIf { it.isNotBlank() },
                sourceCommit = value.optString("sourceCommit"),
                buildScript = value.optString("buildScript"),
                buildDate = value.optString("buildDate"),
                fluxCustomizations = value.optString("fluxCustomizations")
            )
        }
    }
}

internal fun PlayPayloadProvenance.validationError(expected: PlayFeaturePayloadSpec): String? {
    val error = when {
        schemaVersion != PlayPayloadRegistry.PAYLOAD_SCHEMA_VERSION -> "unsupported provenance schema"
        payloadId != expected.payloadId -> "payload id mismatch"
        payloadVersion != PlayPayloadRegistry.PAYLOAD_VERSION -> "payload version mismatch"
        distroId != expected.distroId -> "distro id mismatch"
        architecture != PlayPayloadRegistry.ARCHITECTURE -> "architecture mismatch"
        archiveFileName != expected.archiveFileName -> "archive filename mismatch"
        archiveSha256 != expected.sha256 -> "archive SHA-256 mismatch"
        compressedSize <= expected.minBytes -> "compressed size is below the verification floor"
        uncompressedSize <= 0L -> "uncompressed size is missing"
        upstreamSource.isNullOrBlank() &&
            (inputSource.isNullOrBlank() || inputSourceSha256.isNullOrBlank()) ->
            "upstream or legacy input source/hash is missing"
        sourceCommit.isBlank() -> "source commit is missing"
        buildScript != "scripts/prepare_play_payloads.py" -> "build script is not the approved staging script"
        buildDate.isBlank() -> "build date is missing"
        fluxCustomizations.isBlank() -> "Flux customization record is missing"
        else -> null
    }
    return error
}
