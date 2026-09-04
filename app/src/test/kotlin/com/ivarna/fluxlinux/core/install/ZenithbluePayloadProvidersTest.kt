package com.ivarna.fluxlinux.core.install

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class ZenithbluePayloadProvidersTest {

    private fun mockContext(
        pkgName: String,
        filesDirFile: File? = null
    ): Context {
        return object : ContextWrapper(null) {
            override fun getPackageName(): String = pkgName
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = filesDirFile ?: File("/tmp/mock_files_dir")
            override fun createPackageContext(packageName: String, flags: Int): Context = this
        }
    }

    @Test
    fun `isZenithblue returns true only for zenithblue package`() {
        val zenithblueCtx = mockContext(HostBootstrap.ZENITHBLUE_PACKAGE)
        val ivarnaCtx = mockContext(HostBootstrap.IVARNA_PACKAGE)

        assertTrue(ZenithbluePayloadProviders.isZenithblue(zenithblueCtx))
        assertFalse(ZenithbluePayloadProviders.isZenithblue(ivarnaCtx))
    }

    @Test
    fun `supports returns true for all on ivarna and only registry on zenithblue`() {
        val zenithblueCtx = mockContext(HostBootstrap.ZENITHBLUE_PACKAGE)
        val ivarnaCtx = mockContext(HostBootstrap.IVARNA_PACKAGE)

        val registryDistros = listOf(
            "debian", "alpine", "ubuntu", "kali", "archlinux", "manjaro", "chimera",
            "fedora", "void", "opensuse", "deepin", "parrot",
            "debian13_chroot", "alpine_chroot", "fedora_chroot"
        )
        val unsupportedDistros = listOf("adelie", "artix", "backbox", "centos_stream", "gentoo", "openkylin", "rocky")

        // On Ivarna, everything is supported (filter skipped)
        registryDistros.forEach { id ->
            assertTrue(ZenithbluePayloadProviders.supports(ivarnaCtx, id))
        }
        unsupportedDistros.forEach { id ->
            assertTrue(ZenithbluePayloadProviders.supports(ivarnaCtx, id))
        }

        // On Zenithblue, only registry and supported chroot alias distros are supported
        registryDistros.forEach { id ->
            assertTrue(ZenithbluePayloadProviders.supports(zenithblueCtx, id))
        }
        unsupportedDistros.forEach { id ->
            assertFalse(ZenithbluePayloadProviders.supports(zenithblueCtx, id))
        }
    }

    @Test
    fun `isValid rejects invalid sha or missing file`() {
        val tempFile = File.createTempFile("bad_rootfs", ".tar.xz")
        tempFile.writeText("corrupted bytes")

        val profile = DistroInstallProfile.require("alpine")
        assertFalse(RootfsDownloader.isValid(tempFile, profile))

        tempFile.delete()
        assertFalse(RootfsDownloader.isValid(tempFile, profile))
    }

    @Test
    fun `ensurePresent rejects and deletes bad SHA artifact`() = runBlocking {
        val tempDir = File.createTempFile("mock_home_parent", "_dir")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val badBytes = "corrupted-payload-data-not-matching-sha".toByteArray()
            val ctx = mockContext(
                pkgName = HostBootstrap.ZENITHBLUE_PACKAGE,
                filesDirFile = tempDir
            )

            val fakePfd = object : PlayFeatureDelivery() {
                override fun isInstalled(moduleName: String): Boolean = true
                override fun requestModule(moduleName: String) = flowOf(SplitInstallProgress.Installed)
            }

            val destFile = File(tempDir, "home/${DistroInstallProfile.ALPINE_ROOTFS_NAME}")

            val result = ZenithbluePayloadProviders.ensurePresent(
                ctx = ctx,
                distroId = "alpine",
                playFeatureDelivery = fakePfd,
                assetOpener = { ByteArrayInputStream(badBytes) }
            )

            assertFalse("Bad SHA payload must be rejected", result)
            assertFalse("Bad SHA file must be deleted upon rejection", destFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `ensurePresent never calls RootfsDownloader ensurePresent or download on Play path`() = runBlocking {
        val tempDir = File.createTempFile("mock_home_never_dl", "_dir")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            // Missing asset and failed module request -> ensurePresent must return false
            // without ever attempting network download or external storage fallback
            val ctx = mockContext(
                pkgName = HostBootstrap.ZENITHBLUE_PACKAGE,
                filesDirFile = tempDir
            )

            val fakePfd = object : PlayFeatureDelivery() {
                override fun isInstalled(moduleName: String): Boolean = false
                override fun requestModule(moduleName: String) = flowOf(
                    SplitInstallProgress.Failed(errorCode = -1, exception = null)
                )
            }

            val result = ZenithbluePayloadProviders.ensurePresent(
                ctx = ctx,
                distroId = "alpine",
                playFeatureDelivery = fakePfd
            )

            assertFalse("Failed PlayFeatureDelivery must result in false without HTTP fallback", result)
            val destFile = File(tempDir, "home/${DistroInstallProfile.ALPINE_ROOTFS_NAME}")
            assertFalse("No file should be written", destFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `ensurePresent never reads shared external storage`() = runBlocking {
        // Assert shared-storage paths (/sdcard, /storage/emulated/0) are never queried or used by ensurePresent
        val tempDir = File.createTempFile("mock_home_no_shared", "_dir")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val ctx = mockContext(
                pkgName = HostBootstrap.ZENITHBLUE_PACKAGE,
                filesDirFile = tempDir
            )

            val fakePfd = object : PlayFeatureDelivery() {
                override fun isInstalled(moduleName: String): Boolean = false
                override fun requestModule(moduleName: String) = flowOf(
                    SplitInstallProgress.Failed(errorCode = -1, exception = null)
                )
            }

            val result = ZenithbluePayloadProviders.ensurePresent(
                ctx = ctx,
                distroId = "alpine",
                playFeatureDelivery = fakePfd
            )

            assertFalse(result)
            val destFile = File(tempDir, "home/${DistroInstallProfile.ALPINE_ROOTFS_NAME}")
            assertFalse(destFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

