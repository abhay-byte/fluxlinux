package com.ivarna.fluxlinux.core.install

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadProviderContractTest {

    private fun repoFile(rel: String): File {
        val cwd = File("").absoluteFile
        val candidates = listOf(File(cwd, rel), File(cwd, "app/$rel"))
        return candidates.firstOrNull { it.isFile }
            ?: error("missing $rel (cwd=$cwd)")
    }

    @Test
    fun commonInstallContract_hasNoRemoteTransportSelection() {
        val onboarding = repoFile(
            "src/main/kotlin/com/ivarna/fluxlinux/core/install/OnboardingInstallRunner.kt"
        ).readText()
        val sessions = repoFile(
            "src/main/kotlin/com/ivarna/fluxlinux/core/terminal/InstallSessionFactory.kt"
        ).readText()
        val bootstrap = repoFile(
            "src/main/kotlin/com/ivarna/fluxlinux/core/terminal/BootstrapInstaller.kt"
        ).readText()

        assertTrue(onboarding.contains("PayloadProviders.rootfs"))
        assertFalse(onboarding.contains("RootfsDownloader"))
        assertFalse(onboarding.contains("FLUX_ROOTFS_URL"))
        assertFalse(sessions.contains("FLUX_ROOTFS_URL"))
        assertTrue(bootstrap.contains("PayloadProviders.hostRuntime"))
        assertFalse(bootstrap.contains("RootfsDownloader"))
    }
}
