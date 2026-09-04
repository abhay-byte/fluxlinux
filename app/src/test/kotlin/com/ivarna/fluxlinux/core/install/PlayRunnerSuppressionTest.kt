package com.ivarna.fluxlinux.core.install

import android.content.Context
import android.content.ContextWrapper
import com.ivarna.fluxlinux.core.terminal.HostCommandBuilder
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayRunnerSuppressionTest {

    private fun mockContext(pkgName: String): Context {
        return object : ContextWrapper(null) {
            override fun getPackageName(): String = pkgName
            override fun getApplicationContext(): Context = this
        }
    }

    @Test
    fun `isPlayDistro evaluates true only on zenithblue with registered distro`() {
        val zenithblueCtx = mockContext(HostBootstrap.ZENITHBLUE_PACKAGE)
        val ivarnaCtx = mockContext(HostBootstrap.IVARNA_PACKAGE)

        // Alpine on zenithblue -> true
        val isAlpineZenith = ZenithbluePayloadProviders.isZenithblue(zenithblueCtx) &&
            ZenithbluePayloadProviders.supports(zenithblueCtx, "alpine")
        assertTrue(isAlpineZenith)

        // Alpine on ivarna -> false (isZenithblue is false)
        val isAlpineIvarna = ZenithbluePayloadProviders.isZenithblue(ivarnaCtx) &&
            ZenithbluePayloadProviders.supports(ivarnaCtx, "alpine")
        assertFalse(isAlpineIvarna)

        // Adelie on zenithblue -> false (not in registry / coming soon)
        val isAdelieZenith = ZenithbluePayloadProviders.isZenithblue(zenithblueCtx) &&
            ZenithbluePayloadProviders.supports(zenithblueCtx, "adelie")
        assertFalse(isAdelieZenith)
    }

    @Test
    fun `verify-only rejects when local archive is missing or corrupted`() {
        val fakeHome = File.createTempFile("fake_home", "_dir")
        fakeHome.delete()
        fakeHome.mkdirs()

        try {
            val alpineProfile = DistroInstallProfile.require("alpine")
            val missingFile = File(fakeHome, alpineProfile.rootfsFileName)
            assertFalse(RootfsDownloader.isValid(missingFile, alpineProfile))

            missingFile.writeBytes(byteArrayOf(1, 2, 3, 4))
            assertFalse(RootfsDownloader.isValid(missingFile, alpineProfile))
        } finally {
            fakeHome.deleteRecursively()
        }
    }

    @Test
    fun `runner FLUX_ROOTFS_URL is omitted on Play path`() {
        val zenithblueCtx = mockContext(HostBootstrap.ZENITHBLUE_PACKAGE)
        val ivarnaCtx = mockContext(HostBootstrap.IVARNA_PACKAGE)
        val profile = DistroInstallProfile.require("alpine")

        val isZenithPlay = ZenithbluePayloadProviders.isZenithblue(zenithblueCtx) &&
            ZenithbluePayloadProviders.supports(zenithblueCtx, "alpine")
        assertTrue(isZenithPlay)

        // Simulate environment generation for proot runner
        val playEnv = mutableMapOf<String, String>().apply {
            put("FLUX_ROOTFS_PATH", "${TermuxHostPaths.HOME}/${profile.rootfsFileName}")
            put("FLUX_ROOTFS_NAME", profile.rootfsFileName)
            put("FLUX_ROOTFS_SHA256", profile.rootfsSha256)
            if (!isZenithPlay) {
                put("FLUX_ROOTFS_URL", profile.rootfsUrl)
            }
        }
        assertNull("FLUX_ROOTFS_URL must be omitted on Play path", playEnv["FLUX_ROOTFS_URL"])

        val isIvarnaPlay = ZenithbluePayloadProviders.isZenithblue(ivarnaCtx) &&
            ZenithbluePayloadProviders.supports(ivarnaCtx, "alpine")
        assertFalse(isIvarnaPlay)

        val ivarnaEnv = mutableMapOf<String, String>().apply {
            put("FLUX_ROOTFS_PATH", "${TermuxHostPaths.HOME}/${profile.rootfsFileName}")
            put("FLUX_ROOTFS_NAME", profile.rootfsFileName)
            put("FLUX_ROOTFS_SHA256", profile.rootfsSha256)
            if (!isIvarnaPlay) {
                put("FLUX_ROOTFS_URL", profile.rootfsUrl)
            }
        }
        assertEquals(profile.rootfsUrl, ivarnaEnv["FLUX_ROOTFS_URL"])

        // Simulate chroot runner urlExport
        val playUrlExport = if (isZenithPlay) "" else "export FLUX_ROOTFS_URL='${profile.rootfsUrl}'; "
        assertEquals("", playUrlExport)

        val ivarnaUrlExport = if (isIvarnaPlay) "" else "export FLUX_ROOTFS_URL='${profile.rootfsUrl}'; "
        assertEquals("export FLUX_ROOTFS_URL='${profile.rootfsUrl}'; ", ivarnaUrlExport)
    }
}

