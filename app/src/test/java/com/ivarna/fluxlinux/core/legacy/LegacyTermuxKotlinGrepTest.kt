package com.ivarna.fluxlinux.core.legacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegacyTermuxKotlinGrepTest {

    private fun repoFile(rel: String): File {
        val cwd = File("").absoluteFile
        val candidates = listOf(
            File(cwd, rel),
            File(cwd, "app/$rel"),
            File(cwd.parentFile, rel),
            File(cwd.parentFile, "app/$rel")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("missing $rel (cwd=$cwd)")
    }

    private val forbiddenSymbols = listOf(
        "reopenDisplay",
        "UninstallSessionFactory",
        "TermuxHostPaths",
        "openSessionAfterHost",
        "HostScriptDeployer",
        "openTerminalTab",
        "FluxTerminalSessionManager",
        "onOpenTerminal",
        "Screen.TERMINAL",
        "BottomTab.TERMINAL",
        "openUninstallSession",
        "isDistroInstalledOnFs",
        "setGuiRunning",
        "EmbeddedX11"
    )

    @Test
    fun legacyBridgeFiles_noForbiddenSymbols() {
        val bridgeDir = repoFile("src/main/kotlin/com/ivarna/fluxlinux/core/legacy")
        val files = bridgeDir.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()
        assertTrue("Expected files in core/legacy", files.isNotEmpty())

        for (file in files) {
            val content = file.readText()
            for (forbidden in forbiddenSymbols) {
                assertFalse(
                    "File ${file.name} must not contain forbidden symbol: $forbidden",
                    content.contains(forbidden)
                )
            }
            assertFalse(
                "File ${file.name} in core/legacy must not touch DesktopLauncher",
                content.contains("DesktopLauncher")
            )
            assertFalse(
                "File ${file.name} in core/legacy must not touch isGuiRunning",
                content.contains("isGuiRunning")
            )
        }
    }

    @Test
    fun legacySettingsScreen_noForbiddenSymbols() {
        val screenFile = repoFile("src/main/kotlin/com/ivarna/fluxlinux/ui/screens/LegacyTermuxSettingsScreen.kt")
        val content = screenFile.readText()

        for (forbidden in forbiddenSymbols) {
            assertFalse(
                "LegacyTermuxSettingsScreen.kt must not contain forbidden symbol: $forbidden",
                content.contains(forbidden)
            )
        }

        // DesktopLauncher is permitted ONLY as DesktopLauncher.isSessionActive() in isEmbeddedDesktopActive
        assertTrue(content.contains("DesktopLauncher.isSessionActive()"))
        assertFalse(content.contains("DesktopLauncher.start"))
        assertFalse(content.contains("DesktopLauncher.stop"))
    }
}
