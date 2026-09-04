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
    )

    @Test
    fun `registry contains exactly 7 proot distros`() {
        val all = PlayPayloadRegistry.all()
        assertEquals(7, all.size)
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
    fun `registry does not contain chroot or unsupported distros`() {
        val nonRegistry = listOf(
            "debian13_chroot",
            "alpine_chroot",
            "fedora",
            "void",
            "opensuse",
            "deepin",
            "parrot"
        )
        nonRegistry.forEach { id ->
            assertFalse(PlayPayloadRegistry.contains(id))
            assertEquals(null, PlayPayloadRegistry.find(id))
        }
    }
}
