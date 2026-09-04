package com.ivarna.fluxlinux.ui.install

import android.content.Context
import android.content.ContextWrapper
import com.ivarna.fluxlinux.core.install.HostBootstrap
import com.ivarna.fluxlinux.core.install.OnboardingInstallRunner
import com.ivarna.fluxlinux.core.install.PlayFeatureDelivery
import com.ivarna.fluxlinux.core.install.SplitInstallProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class InstallFlowHelperTest {

    private fun mockContext(
        pkgName: String,
        filesDirFile: File? = null
    ): Context {
        return object : ContextWrapper(null) {
            override fun getPackageName(): String = pkgName
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = filesDirFile ?: File("/tmp/mock_flow_files_dir")
            override fun createPackageContext(packageName: String, flags: Int): Context = this
        }
    }

    @Test
    fun `InstallFlowHelper requests correct module and retries same module on failure`() = runBlocking {
        val requestedModules = mutableListOf<String>()
        val failFirst = AtomicBoolean(true)

        val fakePfd = object : PlayFeatureDelivery() {
            override fun isInstalled(moduleName: String): Boolean = false
            override fun requestModule(moduleName: String): Flow<SplitInstallProgress> {
                requestedModules.add(moduleName)
                return if (failFirst.getAndSet(false)) {
                    flowOf(SplitInstallProgress.Failed(-1, RuntimeException("Network error")))
                } else {
                    flowOf(
                        SplitInstallProgress.Downloading(500, 1000),
                        SplitInstallProgress.Installing,
                        SplitInstallProgress.Installed
                    )
                }
            }
        }

        val tempDir = File.createTempFile("mock_helper_test", "_dir")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val ctx = mockContext(HostBootstrap.ZENITHBLUE_PACKAGE, filesDirFile = tempDir)
            val runner = OnboardingInstallRunner(ctx)

            val failedMessage = AtomicReference<String>()
            val phaseRecord = mutableListOf<String>()

            // 1. First attempt: fails
            val job1 = kotlinx.coroutines.Job()
            val scope1 = CoroutineScope(Dispatchers.Unconfined + job1)
            InstallFlowHelper.startInstall(
                context = ctx,
                scope = scope1,
                distroId = "alpine",
                theme = "default",
                runner = runner,
                playFeatureDelivery = fakePfd,
                onPhaseChange = { phaseRecord.add(it) },
                onDetailChange = {},
                onPercentChange = {},
                onLogLine = {},
                onFailed = { failedMessage.set(it) },
                onSuccess = {}
            )
            job1.children.forEach { it.join() }

            assertEquals(listOf("distro_alpine"), requestedModules)
            assertEquals("Distro download failed. Retry.", failedMessage.get())
            assertTrue(phaseRecord.contains("Install failed"))

            // 2. Retry: requests the SAME module
            val job2 = kotlinx.coroutines.Job()
            val scope2 = CoroutineScope(Dispatchers.Unconfined + job2)
            InstallFlowHelper.startInstall(
                context = ctx,
                scope = scope2,
                distroId = "alpine",
                theme = "default",
                runner = runner,
                playFeatureDelivery = fakePfd,
                onPhaseChange = { phaseRecord.add(it) },
                onDetailChange = {},
                onPercentChange = {},
                onLogLine = {},
                onFailed = { failedMessage.set(it) },
                onSuccess = {}
            )
            job2.children.forEach { it.join() }

            assertEquals(listOf("distro_alpine", "distro_alpine"), requestedModules)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `InstallFlowHelper requests correct module for different distros`() = runBlocking {
        val requestedModules = mutableListOf<String>()

        val fakePfd = object : PlayFeatureDelivery() {
            override fun isInstalled(moduleName: String): Boolean = false
            override fun requestModule(moduleName: String): Flow<SplitInstallProgress> {
                requestedModules.add(moduleName)
                return flowOf(SplitInstallProgress.Failed(-1, null))
            }
        }

        val tempDir = File.createTempFile("mock_helper_distros", "_dir")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val ctx = mockContext(HostBootstrap.ZENITHBLUE_PACKAGE, filesDirFile = tempDir)
            val runner = OnboardingInstallRunner(ctx)

            listOf("debian", "ubuntu", "kali", "archlinux", "manjaro", "chimera", "fedora", "void", "opensuse", "deepin", "parrot").forEach { distroId ->
                val job = kotlinx.coroutines.Job()
                val scope = CoroutineScope(Dispatchers.Unconfined + job)
                InstallFlowHelper.startInstall(
                    context = ctx,
                    scope = scope,
                    distroId = distroId,
                    theme = "default",
                    runner = runner,
                    playFeatureDelivery = fakePfd,
                    onPhaseChange = {},
                    onDetailChange = {},
                    onPercentChange = {},
                    onLogLine = {},
                    onFailed = {},
                    onSuccess = {}
                )
                job.children.forEach { it.join() }
            }

            val expected = listOf(
                "distro_debian",
                "distro_ubuntu",
                "distro_kali",
                "distro_arch",
                "distro_manjaro",
                "distro_chimera",
                "distro_fedora",
                "distro_void",
                "distro_opensuse",
                "distro_deepin",
                "distro_parrot"
            )
            assertEquals(expected, requestedModules)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
