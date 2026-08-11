package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Dispatch + user mapping tests for LinuxCommandBuilder (explicit method param). */
class LinuxCommandBuilderTest {

    private fun ctx(): FakeContext {
        val dir = File(System.getProperty("java.io.tmpdir"), "flux_lcb_${System.nanoTime()}")
        dir.mkdirs()
        return FakeContext(dir, "$dir/jni")
    }

    private fun withSeededSu(block: () -> Unit) {
        com.ivarna.fluxlinux.core.root.RootShell.seedSuInvocationForTest(listOf("/system/bin/sh", "-c"))
        try {
            block()
        } finally {
            com.ivarna.fluxlinux.core.root.RootShell.clearSuCache()
        }
    }

    @Test
    fun sessionUserForType_mapsShells() {
        assertEquals("flux", LinuxCommandBuilder.sessionUserForType("shell"))
        assertEquals("root", LinuxCommandBuilder.sessionUserForType("shell-root"))
        // Component installs need apt/dpkg → root (not interactive flux)
        assertEquals("root", LinuxCommandBuilder.sessionUserForType("component"))
    }

    @Test
    fun prootMethod_usesLibbashExec() {
        val (args, _) = LinuxCommandBuilder.build(ctx(), "exec zsh", method = "proot")
        assertTrue(args[0].endsWith("libbash.so"))
        assertTrue(args[2].contains("login debian"))
    }

    @Test
    fun chrootMethod_usesSystemShExec() {
        withSeededSu {
            val (args, _) = LinuxCommandBuilder.build(ctx(), "exec zsh", method = "chroot")
            assertEquals("/system/bin/sh", args[0])
            assertEquals("-c", args[1])
            assertTrue(args[2].contains("fluxlinux_chroot.sh"))
        }
    }

    @Test
    fun explicitMethod_overridesAmbientDefault() {
        withSeededSu {
            // Product paths must pass method explicitly; ambient default is only a UI convenience.
            val (prootArgs, _) = LinuxCommandBuilder.build(ctx(), "exec zsh", method = "proot")
            val (chrootArgs, _) = LinuxCommandBuilder.build(ctx(), "exec zsh", method = "chroot")
            assertTrue(prootArgs[0].endsWith("libbash.so"))
            assertEquals("/system/bin/sh", chrootArgs[0])
        }
    }
}
