package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Unit tests for TermuxHostPaths SSOT (no device required). */
class TermuxHostPathsTest {

    @Test
    fun rewriteStockPaths_swapsOnlyStockRoot() {
        val rewritten = TermuxHostPaths.rewriteStockPaths(
            "#!/bin/bash\nPREFIX=/data/data/com.termux/files/usr\nHOME=/data/data/com.termux/files/home"
        )
        assertFalse(rewritten.contains("com.termux"))
        assertTrue(rewritten.contains(TermuxHostPaths.PREFIX))
        assertTrue(rewritten.contains(TermuxHostPaths.HOME))
    }

    @Test
    fun rewriteStockPaths_ignoresNoMatch() {
        val text = "echo hello"
        assertEquals(text, TermuxHostPaths.rewriteStockPaths(text))
    }

    @Test
    fun writeHostEnvFile_writesPinnedKeys() {
        val dir = createTempDir()
        try {
            val f = TermuxHostPaths.writeHostEnvFile(dir)
            assertTrue(f.exists())
            val content = f.readText()
            assertTrue(content.contains("TERMUX_APP__PACKAGE_NAME=\"${TermuxHostPaths.PACKAGE}\""))
            assertTrue(content.contains("PREFIX=\"${TermuxHostPaths.PREFIX}\""))
            assertTrue(content.contains("HOME=\"${TermuxHostPaths.HOME}\""))
            assertTrue(content.contains("TMPDIR=\"${TermuxHostPaths.TMPDIR}\""))
            assertFalse(content.contains("com.termux"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun derivedPaths_followPackage() {
        assertEquals("/data/data/${TermuxHostPaths.PACKAGE}/files/usr", TermuxHostPaths.PREFIX)
        assertEquals("/data/data/${TermuxHostPaths.PACKAGE}/files/home", TermuxHostPaths.HOME)
        assertEquals("/data/data/${TermuxHostPaths.PACKAGE}/files/usr/bin/proot-distro", TermuxHostPaths.PROOT_DISTRO)
        assertEquals("usr/etc/fluxlinux-host.env", TermuxHostPaths.HOST_ENV_REL)
    }

    @Test
    fun patchProotDistroLoaderPassThrough_addsLoaderVars() {
        val dir = createTempDir()
        try {
            val rel = TermuxHostPaths.PROOT_DISTRO_LOGIN_INIT_REL
            val loginInit = File(dir, rel)
            loginInit.parentFile?.mkdirs()
            // Stock allowlist from proot-distro (pre-patch)
            loginInit.writeText(
                """
                |if not minimal:
                |    for var in ("PROOT_NO_SECCOMP", "PROOT_VERBOSE"):
                |        val = os.environ.get(var)
                |        if val:
                |            child_env[var] = val
                |""".trimMargin()
            )
            assertTrue(TermuxHostPaths.patchProotDistroLoaderPassThrough(dir))
            val content = loginInit.readText()
            assertTrue(content.contains("\"PROOT_LOADER\""))
            assertTrue(content.contains("\"PROOT_LOADER_32\""))
            // Idempotent — second call must not duplicate or fail
            assertTrue(TermuxHostPaths.patchProotDistroLoaderPassThrough(dir))
            val again = loginInit.readText()
            assertEquals(content, again)
            assertTrue(
                again.contains(
                    "\"PROOT_NO_SECCOMP\", \"PROOT_VERBOSE\", \"PROOT_LOADER\", \"PROOT_LOADER_32\""
                )
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun createTempDir(): File {
        val f = File(System.getProperty("java.io.tmpdir"), "flux_${System.nanoTime()}")
        f.mkdirs()
        return f
    }
}
