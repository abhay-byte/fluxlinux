package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuAccelDetectorTest {

    @Test
    fun classify_kalamaKgsl_isTurnipAdreno() {
        val d = GpuAccelDetector.classify("HARDWARE=kalama BOARD=taro", kgslPresent = true)
        assertEquals(GpuAccelDetector.MODE_TURNIP, d.mode)
        assertEquals("adreno/snapdragon", d.vendorHint)
    }

    @Test
    fun classify_kalamaNoKgsl_isTurnipAdreno() {
        val d = GpuAccelDetector.classify("ro.board.platform=kalama", kgslPresent = false)
        assertEquals(GpuAccelDetector.MODE_TURNIP, d.mode)
        assertEquals("adreno/snapdragon", d.vendorHint)
    }

    @Test
    fun classify_dimensityMali_isVirglMali() {
        val d = GpuAccelDetector.classify("ro.hardware=mt6983 dimensity mali", kgslPresent = false)
        assertEquals(GpuAccelDetector.MODE_VIRGL, d.mode)
        assertEquals("mali", d.vendorHint)
    }

    @Test
    fun classify_powervr_isVirglPowerVr() {
        val d = GpuAccelDetector.classify("ro.hardware.egl=powervr imgtec", kgslPresent = false)
        assertEquals(GpuAccelDetector.MODE_VIRGL, d.mode)
        assertEquals("powervr", d.vendorHint)
    }

    @Test
    fun classify_emptyNoKgsl_isVirglUnknown() {
        val d = GpuAccelDetector.classify("", kgslPresent = false)
        assertEquals(GpuAccelDetector.MODE_VIRGL, d.mode)
        assertEquals("unknown", d.vendorHint)
    }

    @Test
    fun classify_kgslAlone_isTurnip() {
        val d = GpuAccelDetector.classify("unknown-board", kgslPresent = true)
        assertEquals(GpuAccelDetector.MODE_TURNIP, d.mode)
        assertEquals("adreno/snapdragon", d.vendorHint)
    }

    @Test
    fun classify_xclipse_isVirglXclipse() {
        val d = GpuAccelDetector.classify("samsung_xclipse amdgpu", kgslPresent = false)
        assertEquals(GpuAccelDetector.MODE_VIRGL, d.mode)
        assertEquals("xclipse", d.vendorHint)
    }

    @Test
    fun matchesAdreno_smSoCShaped_yes() {
        assertTrue(GpuAccelDetector.matchesAdreno("ro.soc.model=sm8550"))
        assertTrue(GpuAccelDetector.matchesAdreno("sm8150"))
        assertTrue(GpuAccelDetector.matchesAdreno("board=sm4450"))
    }

    @Test
    fun matchesAdreno_bareSm4Substring_no() {
        // "sm4" as a loose substring must not match random tokens.
        assertFalse(GpuAccelDetector.matchesAdreno("prism42"))
        assertFalse(GpuAccelDetector.matchesAdreno("asm4lite"))
    }

    @Test
    fun matchesAdreno_samsungDoesNotLookLikeSunBoard() {
        assertFalse(GpuAccelDetector.matchesAdreno("samsung_xclipse"))
        assertTrue(GpuAccelDetector.matchesAdreno("BOARD=sun"))
    }

    @Test
    fun normalize_aliases() {
        assertEquals(GpuAccelDetector.MODE_TURNIP, GpuAccelDetector.normalize("adreno"))
        assertEquals(GpuAccelDetector.MODE_TURNIP, GpuAccelDetector.normalize("snapdragon"))
        assertEquals(GpuAccelDetector.MODE_TURNIP, GpuAccelDetector.normalize("QCOM"))
        assertEquals(GpuAccelDetector.MODE_TURNIP, GpuAccelDetector.normalize("zink"))
        assertEquals(GpuAccelDetector.MODE_VIRGL, GpuAccelDetector.normalize("mali"))
        assertEquals(GpuAccelDetector.MODE_VIRGL, GpuAccelDetector.normalize("virpipe"))
        assertEquals(GpuAccelDetector.MODE_VIRGL, GpuAccelDetector.normalize("software"))
        assertEquals(GpuAccelDetector.MODE_VIRGL, GpuAccelDetector.normalize("nope"))
        assertEquals(GpuAccelDetector.MODE_AUTO, GpuAccelDetector.normalize("auto"))
        assertEquals(GpuAccelDetector.MODE_AUTO, GpuAccelDetector.normalize("AUTO"))
        assertEquals(GpuAccelDetector.MODE_AUTO, GpuAccelDetector.normalize(""))
        assertEquals(GpuAccelDetector.MODE_AUTO, GpuAccelDetector.normalize(null))
        assertEquals(GpuAccelDetector.MODE_ASK, GpuAccelDetector.normalize("ask"))
        assertEquals(GpuAccelDetector.MODE_ASK, GpuAccelDetector.normalize("manual"))
        assertEquals(GpuAccelDetector.MODE_TURNIP, GpuAccelDetector.normalize("turnip"))
        assertEquals(GpuAccelDetector.MODE_VIRGL, GpuAccelDetector.normalize("virgl"))
    }
}
