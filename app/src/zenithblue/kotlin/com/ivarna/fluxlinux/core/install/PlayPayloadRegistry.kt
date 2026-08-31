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
    const val ALPINE_PLAY_BASELINE_SHA256 =
        "da25146101274ce944472380285f09b96583dcb6093cdf57058ef2648b5f75d7"
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
        PlayFeaturePayloadSpec("distro_debian", "rootfs.debian", "debian", "payloads/distro_debian/${DistroInstallProfile.DEBIAN_ROOTFS_NAME}", DistroInstallProfile.DEBIAN_ROOTFS_NAME, DistroInstallProfile.DEBIAN_ROOTFS_SHA256, DistroInstallProfile.DEBIAN_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_alpine", "rootfs.alpine", "alpine", "payloads/distro_alpine/${DistroInstallProfile.ALPINE_ROOTFS_PLAY_ASSET_NAME}", DistroInstallProfile.ALPINE_ROOTFS_NAME, ALPINE_PLAY_BASELINE_SHA256, DistroInstallProfile.ALPINE_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_fedora", "rootfs.fedora", "fedora", "payloads/distro_fedora/${DistroInstallProfile.FEDORA_ROOTFS_NAME}", DistroInstallProfile.FEDORA_ROOTFS_NAME, DistroInstallProfile.FEDORA_ROOTFS_SHA256, DistroInstallProfile.FEDORA_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_void", "rootfs.void", "void", "payloads/distro_void/${DistroInstallProfile.VOID_ROOTFS_NAME}", DistroInstallProfile.VOID_ROOTFS_NAME, DistroInstallProfile.VOID_ROOTFS_SHA256, DistroInstallProfile.VOID_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_opensuse", "rootfs.opensuse", "opensuse", "payloads/distro_opensuse/${DistroInstallProfile.OPENSUSE_ROOTFS_NAME}", DistroInstallProfile.OPENSUSE_ROOTFS_NAME, DistroInstallProfile.OPENSUSE_ROOTFS_SHA256, DistroInstallProfile.OPENSUSE_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_chimera", "rootfs.chimera", "chimera", "payloads/distro_chimera/${DistroInstallProfile.CHIMERA_ROOTFS_NAME}", DistroInstallProfile.CHIMERA_ROOTFS_NAME, DistroInstallProfile.CHIMERA_ROOTFS_SHA256, DistroInstallProfile.CHIMERA_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_deepin", "rootfs.deepin", "deepin", "payloads/distro_deepin/${DistroInstallProfile.DEEPIN_ROOTFS_NAME}", DistroInstallProfile.DEEPIN_ROOTFS_NAME, DistroInstallProfile.DEEPIN_ROOTFS_SHA256, DistroInstallProfile.DEEPIN_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_manjaro", "rootfs.manjaro", "manjaro", "payloads/distro_manjaro/${DistroInstallProfile.MANJARO_ROOTFS_NAME}", DistroInstallProfile.MANJARO_ROOTFS_NAME, DistroInstallProfile.MANJARO_ROOTFS_SHA256, DistroInstallProfile.MANJARO_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_ubuntu", "rootfs.ubuntu", "ubuntu", "payloads/distro_ubuntu/${DistroInstallProfile.UBUNTU_ROOTFS_NAME}", DistroInstallProfile.UBUNTU_ROOTFS_NAME, DistroInstallProfile.UBUNTU_ROOTFS_SHA256, DistroInstallProfile.UBUNTU_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_kali", "rootfs.kali", "kali", "payloads/distro_kali/${DistroInstallProfile.KALI_ROOTFS_NAME}", DistroInstallProfile.KALI_ROOTFS_NAME, DistroInstallProfile.KALI_ROOTFS_SHA256, DistroInstallProfile.KALI_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_parrot", "rootfs.parrot", "parrot", "payloads/distro_parrot/${DistroInstallProfile.PARROT_ROOTFS_NAME}", DistroInstallProfile.PARROT_ROOTFS_NAME, DistroInstallProfile.PARROT_ROOTFS_SHA256, DistroInstallProfile.PARROT_ROOTFS_MIN_BYTES),
        PlayFeaturePayloadSpec("distro_arch", "rootfs.arch", "archlinux", "payloads/distro_arch/${DistroInstallProfile.ARCH_ROOTFS_NAME}", DistroInstallProfile.ARCH_ROOTFS_NAME, DistroInstallProfile.ARCH_ROOTFS_SHA256, DistroInstallProfile.ARCH_ROOTFS_MIN_BYTES)
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
    val upstreamSource: String,
    val upstreamChecksum: String?,
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
                upstreamSource = value.optString("upstreamSource"),
                upstreamChecksum = value.optString("upstreamChecksum").takeIf { it.isNotBlank() },
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
        upstreamSource.isBlank() -> "upstream source is missing"
        sourceCommit.isBlank() -> "source commit is missing"
        buildScript != "scripts/prepare_play_payloads.py" -> "build script is not the approved staging script"
        buildDate.isBlank() -> "build date is missing"
        fluxCustomizations.isBlank() -> "Flux customization record is missing"
        else -> null
    }
    return error
}
