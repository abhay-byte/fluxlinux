package com.ivarna.fluxlinux.core.install

import java.io.File
import com.ivarna.fluxlinux.core.data.DistroRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenithbluePayloadProviderTest {

    private fun repoFile(rel: String): File {
        val cwd = File("").absoluteFile
        val candidates = listOf(File(cwd, rel), File(cwd, "app/$rel"))
        return candidates.firstOrNull { it.isFile }
            ?: error("missing $rel (cwd=$cwd)")
    }

    @Test
    fun flavorWiring_usesPlayFeatureDeliveryOnly() {
        assertEquals("zenithblue-play-feature-delivery", PayloadProviders.rootfs.id)
        assertEquals("zenithblue-play-feature-delivery", PayloadProviders.hostRuntime.id)
        assertFalse(PayloadProviders.androidRoot.enabled)

        val source = repoFile(
            "src/zenithblue/kotlin/com/ivarna/fluxlinux/core/install/ZenithbluePayloadProviders.kt"
        ).readText()
        assertTrue(source.contains("PlayFeatureDelivery"))
        assertTrue(source.contains("VerifiedPayloadStore.materializeStream"))
        assertFalse(source.contains("RootfsDownloader"))
        assertFalse(source.contains("https://"))
        assertFalse(source.contains("/sdcard/Download"))
        assertFalse(source.contains("storage/emulated/0/Download"))
    }

    @Test
    fun registry_mapsOnlyTheFastReleaseDistrosToOnDemandModules() {
        assertEquals(7, PlayPayloadRegistry.allRootfs().size)
        assertEquals(
            setOf(
                "distro_debian", "distro_alpine", "distro_ubuntu", "distro_kali",
                "distro_arch", "distro_manjaro", "distro_chimera"
            ),
            PlayPayloadRegistry.allRootfs().map { it.moduleName }.toSet()
        )
        assertTrue(PlayPayloadRegistry.allRootfs().all { it.assetPath.startsWith("payloads/") })
        assertTrue(PlayPayloadRegistry.allRootfs().all { it.sha256.matches(Regex("[0-9a-f]{64}")) })
    }

    @Test
    fun providerPins_separateRawIvarnaAndProvisionedPlayHashes() {
        val debian = DistroInstallProfile.require("debian")
        assertEquals(
            "13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803",
            debian.rootfsSha256
        )
        assertEquals(
            PlayPayloadRegistry.DEBIAN_PLAY_ROOTFS_SHA256,
            PlayFeatureRootfsProvider.verifiedSpec(debian).sha256
        )
        assertFalse(
            PlayFeatureRootfsProvider.verifiedSpec(debian).sha256 == debian.rootfsSha256
        )
        assertTrue(PlayFeatureRootfsProvider.supports(debian))
        assertFalse(
            PlayFeatureRootfsProvider.supports(DistroInstallProfile.require("fedora"))
        )
        assertEquals(
            setOf("debian", "alpine", "ubuntu", "kali", "archlinux", "manjaro", "chimera"),
            DistroRepository.filterForPayloadProvider(
                DistroRepository.supportedDistros,
                PlayFeatureRootfsProvider
            ).filter { !it.comingSoon && !it.id.endsWith("_chroot") }
                .map { it.id }
                .toSet()
        )
    }

    @Test
    fun provenance_rejectsIdentityDrift() {
        val expected = PlayPayloadRegistry.allRootfs().first()
        val json = """
            {
              "schemaVersion": 1,
              "payloadId": "${expected.payloadId}",
              "payloadVersion": "2.0.0",
              "distroId": "${expected.distroId}",
              "architecture": "arm64-v8a",
              "archiveFileName": "${expected.archiveFileName}",
              "archiveSha256": "${expected.sha256}",
              "compressedSize": ${expected.minBytes + 1},
              "uncompressedSize": 1,
              "upstreamSource": "test fixture",
              "sourceCommit": "test",
              "buildScript": "scripts/prepare_play_payloads.py",
              "buildDate": "2026-01-01T00:00:00Z",
              "fluxCustomizations": "none"
            }
        """.trimIndent()

        assertEquals(null, PlayPayloadProvenance.parse(json).validationError(expected))
        assertEquals(
            "archive SHA-256 mismatch",
            PlayPayloadProvenance.parse(json.replace(expected.sha256, "0".repeat(64)))
                .validationError(expected)
        )
    }

    @Test
    fun hostRegistry_hasDedicatedRuntimeFeature() {
        val host = PlayPayloadRegistry.runtimeHost
        assertEquals("runtime_host", host.moduleName)
        assertEquals("payloads/runtime_host/bootstrap.tar", host.assetPath)
        assertEquals("payloads/runtime_host/provenance.json", host.provenanceAssetPath)
        assertEquals(HostBootstrap.ZENITHBLUE.fileName, host.archiveFileName)
        assertNotNull(PlayPayloadRegistry.forProfile(DistroInstallProfile.require("debian")))
    }

    @Test
    fun alpineRegistrySeparatesPackagedAssetNameFromArchiveIdentity() {
        val alpine = PlayPayloadRegistry.forProfile(DistroInstallProfile.require("alpine"))!!
        assertEquals(
            "payloads/distro_alpine/alpine_3.24_rootfs.minirootfs",
            alpine.assetPath
        )
        assertEquals("alpine_3.24_rootfs.tar.gz", alpine.archiveFileName)
    }
}
