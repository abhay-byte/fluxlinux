package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HostScriptDeployerContractTest {
    private fun repoFile(rel: String): File {
        val cwd = File("").absoluteFile
        return listOf(File(cwd, rel), File(cwd, "app/$rel"), File(cwd.parentFile, rel))
            .firstOrNull { it.isFile } ?: error("missing $rel from $cwd")
    }

    @Test
    fun guestLibraryIsNotBlindlyMarkedExecutable() {
        val source = repoFile(
            "src/main/kotlin/com/ivarna/fluxlinux/core/terminal/HostScriptDeployer.kt"
        ).readText()
        assertTrue(source.contains("enum class AssetType"))
        assertTrue(source.contains("type = AssetType.GUEST_LIBRARY"))
        assertTrue(source.contains("val executable = type == AssetType.SCRIPT"))
        assertFalse(source.contains("for (script in HOST_SCRIPTS) {\n                val out") &&
            source.contains("out.setExecutable(true, false)"))
    }
}
