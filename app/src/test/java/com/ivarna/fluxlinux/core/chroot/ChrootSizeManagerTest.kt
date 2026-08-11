package com.ivarna.fluxlinux.core.chroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChrootSizeManagerTest {

    @Test
    fun parse_sizeBytesMarker() {
        val raw = """
            Magisk su preamble junk
            # chroot_size v1
            # path=/data/local/tmp/chrootDebian13
            SIZE_BYTES=5368709120
        """.trimIndent()
        val r = ChrootSizeManager.parse(raw, "/data/local/tmp/chrootDebian13", 0, rootOk = true)
        assertEquals(5_368_709_120L, r.bytes)
        assertTrue(r.dirExists)
        assertTrue(r.viaRoot)
    }

    @Test
    fun parse_noDir() {
        val raw = """
            # chroot_size v1
            # path=/data/local/tmp/chrootDebian13
            # error=no_dir
            SIZE_BYTES=-1
        """.trimIndent()
        val r = ChrootSizeManager.parse(raw, "/data/local/tmp/chrootDebian13", 1, rootOk = true)
        assertEquals("no_dir", r.error)
        assertTrue(!r.dirExists)
    }
}
