package com.ivarna.fluxlinux.ui.install

import android.content.Context
import android.content.ContextWrapper
import com.ivarna.fluxlinux.core.data.DistroRepository
import com.ivarna.fluxlinux.core.install.HostBootstrap
import com.ivarna.fluxlinux.core.install.PlayPayloadRegistry
import com.ivarna.fluxlinux.core.install.ZenithbluePayloadProviders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayUiFilteringTest {

    private fun mockContext(pkgName: String): Context {
        return object : ContextWrapper(null) {
            override fun getPackageName(): String = pkgName
            override fun getApplicationContext(): Context = this
        }
    }

    @Test
    fun `zenithblue supports all 12 registered distros and their chroots`() {
        val zenithCtx = mockContext(HostBootstrap.ZENITHBLUE_PACKAGE)
        val allDistros = DistroRepository.supportedDistros

        val filtered = allDistros.filter {
            ZenithbluePayloadProviders.supports(zenithCtx, it.id)
        }

        // 12 proot + 12 chroot = 24 supported distros (excluding comingSoon distros)
        assertEquals(24, filtered.size)
        filtered.forEach { distro ->
            assertTrue(PlayPayloadRegistry.contains(distro.id))
        }

        val comingSoonIds = listOf("adelie", "artix", "backbox", "centos_stream", "gentoo", "openkylin", "rocky")
        filtered.forEach { distro ->
            assertFalse(comingSoonIds.contains(distro.id))
        }
    }

    @Test
    fun `ivarna preserves all supported distros including chroots`() {
        val ivarnaCtx = mockContext(HostBootstrap.IVARNA_PACKAGE)
        val allDistros = DistroRepository.supportedDistros

        val filtered = allDistros.filter {
            ZenithbluePayloadProviders.supports(ivarnaCtx, it.id)
        }

        assertEquals(allDistros.size, filtered.size)
        assertTrue(filtered.any { it.id.endsWith("_chroot") })
    }
}

