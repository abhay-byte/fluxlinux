package com.ivarna.fluxlinux.core.install

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Worker04ArtifactContractTest {

    private fun repoFile(rel: String): File {
        return repoPath(rel).takeIf { it.exists() }
            ?: error("missing $rel (cwd=${File("").absoluteFile})")
    }

    private fun repoPath(rel: String): File {
        val cwd = File("").absoluteFile
        val candidates = listOf(File(cwd, rel), File(cwd, "app/$rel"))
        return candidates.firstOrNull { it.parentFile?.exists() == true }
            ?: candidates.first()
    }

    @Test
    fun nestedLoaderAndPulseOverlayAssetsAreAbsent() {
        assertFalse(repoPath("src/main/assets/loader.apk").exists())
        assertFalse(repoPath("src/main/assets/loader.bin").exists())
        assertFalse(repoPath("src/zenithblue/assets/pulse-runtime").exists())
        assertFalse(repoPath("src/ivarna/assets/pulse-runtime").exists())
    }

    @Test
    fun hostAndGuiCodeUseFeatureBootstrapAndEmbeddedX11() {
        val deployer = repoFile(
            "src/main/kotlin/com/ivarna/fluxlinux/core/terminal/HostScriptDeployer.kt"
        ).readText()
        assertTrue(deployer.contains("configurePulseRuntime"))
        assertFalse(deployer.contains("overlayPulseRuntime"))
        assertFalse(deployer.contains("deployLoaderApk"))
        assertFalse(deployer.contains("pulse-runtime/"))

        val gui = repoFile("src/main/assets/scripts/debian/proot/start/start_gui.sh").readText()
        assertTrue(gui.contains("FLUX_EMBEDDED_X11"))
        assertTrue(gui.contains("X server PID=embedded"))
        assertFalse(gui.contains("/system/bin/app_process"))
        assertFalse(gui.contains("loader.apk"))

        val build = repoFile("app/build.gradle.kts").readText()
        assertFalse(build.contains("restoreLoaderApk"))
    }

    @Test
    fun libaclDependencyIsInBootstrapContract() {
        val packageList = repoFile("native/package-lists/termux-lib-ssot.txt").readText()
        val verify = repoFile("scripts/verify_bootstrap.sh").readText()
        assertTrue(packageList.contains("\nattr\n"))
        assertTrue(verify.contains("usr/lib/libattr.so"))
        assertTrue(verify.contains("usr/lib/libacl.so"))
        assertTrue(HostBootstrap.ZENITHBLUE.fileName.endsWith(".v2.tar"))
    }

    @Test
    fun playSourceSetHasNoRemoteHostOrRootfsFallback() {
        val playRoot = repoPath("src/zenithblue/kotlin")
        val playSources = playRoot.walkTopDown().filter { it.isFile }.toList()
            .joinToString("\n") { it.readText() }
        assertFalse(playSources.contains("RootfsDownloader"))
        assertFalse(playSources.contains("github.com/abhay-byte/fluxlinux/releases"))
        assertFalse(playSources.contains("/sdcard/Download"))

        val staging = repoFile("scripts/prepare_play_payloads.py").readText()
        assertTrue(staging.contains("bootstrap_com.zenithblue.fluxlinux.v2.tar"))
    }

    @Test
    fun artifactScannerIsExecutableContract() {
        val scanner = repoFile("scripts/verify_play_host_artifacts.sh")
        assertTrue(scanner.isFile)
        assertTrue(scanner.canExecute())
        assertTrue(scanner.readText().contains("nested APK bytes"))
        assertTrue(scanner.readText().contains("unexpected ELF"))
    }
}
