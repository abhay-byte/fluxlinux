package com.ivarna.fluxlinux.core.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayPayloadRegistryTest {

    private val expectedDistros = listOf(
        "debian" to ("distro_debian" to DistroInstallProfile.DEBIAN_ROOTFS_NAME),
        "alpine" to ("distro_alpine" to DistroInstallProfile.ALPINE_ROOTFS_NAME),
        "ubuntu" to ("distro_ubuntu" to DistroInstallProfile.UBUNTU_ROOTFS_NAME),
        "kali" to ("distro_kali" to DistroInstallProfile.KALI_ROOTFS_NAME),
        "archlinux" to ("distro_arch" to DistroInstallProfile.ARCH_ROOTFS_NAME),
        "manjaro" to ("distro_manjaro" to DistroInstallProfile.MANJARO_ROOTFS_NAME),
        "chimera" to ("distro_chimera" to DistroInstallProfile.CHIMERA_ROOTFS_NAME),
        "fedora" to ("distro_fedora" to DistroInstallProfile.FEDORA_ROOTFS_NAME),
        "void" to ("distro_void" to DistroInstallProfile.VOID_ROOTFS_NAME),
        "opensuse" to ("distro_opensuse" to DistroInstallProfile.OPENSUSE_ROOTFS_NAME),
        "deepin" to ("distro_deepin" to DistroInstallProfile.DEEPIN_ROOTFS_NAME),
        "parrot" to ("distro_parrot" to DistroInstallProfile.PARROT_ROOTFS_NAME),
    )

    @Test
    fun `registry contains exactly 12 proot distros`() {
        val all = PlayPayloadRegistry.all()
        assertEquals(12, all.size)
        expectedDistros.forEach { (distroId, _) ->
            assertTrue(PlayPayloadRegistry.contains(distroId))
        }
    }

    @Test
    fun `registry maps distro id to correct module and filename and sha256`() {
        expectedDistros.forEach { (distroId, mapping) ->
            val (expectedModule, expectedFilename) = mapping
            val info = PlayPayloadRegistry.find(distroId)
            assertNotNull(info)
            assertEquals(expectedModule, info!!.moduleName)
            assertEquals(expectedFilename, info.archiveFileName)

            val profile = DistroInstallProfile.forId(distroId)
            assertNotNull(profile)
            assertEquals(profile!!.rootfsSha256, info.sha256)
            assertEquals(profile.rootfsMinBytes, info.minBytes)
        }
    }

    @Test
    fun `registry aliases map chroot ids to matching proot modules and metadata`() {
        val chrootMappings = listOf(
            "debian13_chroot" to "distro_debian",
            "debian_chroot" to "distro_debian",
            "alpine_chroot" to "distro_alpine",
            "ubuntu_chroot" to "distro_ubuntu",
            "kali_chroot" to "distro_kali",
            "archlinux_chroot" to "distro_arch",
            "manjaro_chroot" to "distro_manjaro",
            "chimera_chroot" to "distro_chimera",
            "fedora_chroot" to "distro_fedora",
            "void_chroot" to "distro_void",
            "opensuse_chroot" to "distro_opensuse",
            "deepin_chroot" to "distro_deepin",
            "parrot_chroot" to "distro_parrot",
        )
        chrootMappings.forEach { (chrootId, expectedModule) ->
            assertTrue(PlayPayloadRegistry.contains(chrootId))
            val info = PlayPayloadRegistry.find(chrootId)
            assertNotNull(info)
            assertEquals(expectedModule, info!!.moduleName)
        }
    }

    @Test
    fun `registry does not contain unknown distro names`() {
        val unknown = listOf("redhat", "centos", "gentoo", "slackware", "unknown_distro")
        unknown.forEach { id ->
            assertFalse(PlayPayloadRegistry.contains(id))
            assertEquals(null, PlayPayloadRegistry.find(id))
        }
    }
}
