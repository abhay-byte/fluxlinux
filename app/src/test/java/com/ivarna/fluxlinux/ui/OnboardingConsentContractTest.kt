package com.ivarna.fluxlinux.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OnboardingConsentContractTest {

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
    fun optionsPage_requiresDownloadConsentBeforeInstall() {
        val src = repoFile(
            "src/main/kotlin/com/ivarna/fluxlinux/ui/onboarding/OnboardingFlowScreen.kt"
        ).readText()
        assertTrue(src.contains("downloadConsent"))
        assertTrue(src.contains("DownloadConsentRow"))
        assertTrue(src.contains("enabled = downloadConsent"))
        assertTrue(src.contains("not checked by F-Droid"))
        assertTrue(src.contains("downloads Linux system images"))
        assertTrue(
            "consent must start unchecked",
            src.contains("var downloadConsent by remember { mutableStateOf(false) }")
        )
        assertFalse(src.contains("var downloadConsent by remember { mutableStateOf(true) }"))
        assertTrue(src.contains("OnboardStep.Consent"))
        assertTrue(src.contains("OnboardStep.HostSetup"))
        assertTrue(src.contains("fun ConsentPage"))
        assertTrue(src.contains("fun HostSetupPage"))
        assertTrue(src.contains("HostBootstrapStep"))
    }
}
