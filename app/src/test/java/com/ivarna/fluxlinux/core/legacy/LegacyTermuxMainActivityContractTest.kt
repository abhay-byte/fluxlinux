package com.ivarna.fluxlinux.core.legacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegacyTermuxMainActivityContractTest {

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

    @Test
    fun mainActivity_callbackContract() {
        val text = repoFile("src/main/kotlin/com/ivarna/fluxlinux/MainActivity.kt").readText()

        // 1. legacy_termux_ early-return before distro_uninstall_ and before processNextInstallTask
        val handleCallbackBody = text.substringAfter("fun handleScriptCallback(").substringBefore("private val notificationPermissionLauncher")
        val legacyIdx = handleCallbackBody.indexOf("scriptName.startsWith(\"legacy_termux_\")")
        val distroUninstallIdx = handleCallbackBody.indexOf("distro_uninstall_")
        val processQueueIdx = handleCallbackBody.indexOf("processNextInstallTask")

        assertTrue("legacy_termux_ check must exist in handleScriptCallback", legacyIdx >= 0)
        assertTrue("distro_uninstall_ check must exist in handleScriptCallback", distroUninstallIdx >= 0)
        assertTrue("processNextInstallTask must exist in handleScriptCallback", processQueueIdx >= 0)

        assertTrue("legacy_termux_ early-return must appear before distro_uninstall_", legacyIdx < distroUninstallIdx)
        assertTrue("legacy_termux_ early-return must appear before processNextInstallTask", legacyIdx < processQueueIdx)

        // 2. onCreate calls dispatchLegacyTermuxCallback guarded by savedInstanceState == null
        val onCreateBody = text.substringAfter("override fun onCreate(").substringBefore("setContent {")
        assertTrue("onCreate must guard callback dispatch with savedInstanceState == null", onCreateBody.contains("if (savedInstanceState == null)"))
        assertTrue("onCreate must call dispatchLegacyTermuxCallback", onCreateBody.contains("dispatchLegacyTermuxCallback"))

        // 3. onCreate does not call handleScriptCallback on the full dispatcher
        assertFalse("onCreate must not call handleScriptCallback", onCreateBody.contains("handleScriptCallback"))

        // 4. refreshInstalledAfterUninstall is not referenced from core/legacy/** or LegacyTermuxSettingsScreen.kt
        val bridgeDir = repoFile("src/main/kotlin/com/ivarna/fluxlinux/core/legacy")
        for (f in bridgeDir.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }) {
            assertFalse(
                "${f.name} must not call refreshInstalledAfterUninstall",
                f.readText().contains("refreshInstalledAfterUninstall")
            )
        }
        val screenFile = repoFile("src/main/kotlin/com/ivarna/fluxlinux/ui/screens/LegacyTermuxSettingsScreen.kt")
        assertFalse(
            "LegacyTermuxSettingsScreen must not call refreshInstalledAfterUninstall",
            screenFile.readText().contains("refreshInstalledAfterUninstall")
        )
    }
}
