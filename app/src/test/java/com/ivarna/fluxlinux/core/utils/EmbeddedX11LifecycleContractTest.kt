package com.ivarna.fluxlinux.core.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmbeddedX11LifecycleContractTest {
    private fun repoFile(rel: String): File {
        val cwd = File("").absoluteFile
        return listOf(File(cwd, rel), File(cwd, "app/$rel"), File(cwd.parentFile, rel))
            .firstOrNull { it.isFile } ?: error("missing $rel from $cwd")
    }

    @Test
    fun serverHasExplicitLifecycleAndNativeReturnPath() {
        val java = repoFile("../termux-x11/src/main/java/com/termux/x11/CmdEntryPoint.java").readText()
        val native = repoFile("../termux-x11/src/main/cpp/lorie/cmdentrypoint.cpp").readText()
        val embedded = repoFile(
            "src/main/kotlin/com/ivarna/fluxlinux/core/utils/EmbeddedX11.kt"
        ).readText()
        assertFalse(java.contains("System.exit"))
        assertFalse(native.contains("exit(dix_main"))
        assertTrue(native.contains("waitForServer"))
        assertTrue(native.contains("GiveUp(SIGTERM)"))
        assertTrue(embedded.contains("STOPPED, STARTING, RUNNING, STOPPING"))
        assertTrue(embedded.contains("fun restartServer"))
        assertTrue(embedded.contains("stopServer"))
    }
}
