package com.ivarna.fluxlinux.core.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegacyTermuxBridgeTest {

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
    fun wrapDeploy_pureLogic() {
        val valid = LegacyTermuxBridge.wrapDeploy("QUJD", "flux_legacy_list_proot.sh", emptyList())
        assertNotNull(valid)
        assertTrue(valid!!.contains("echo 'QUJD' | base64 -d > \$HOME/flux_legacy_list_proot.sh"))
        assertTrue(valid.contains("chmod +x \$HOME/flux_legacy_list_proot.sh"))
        assertTrue(valid.contains("bash \$HOME/flux_legacy_list_proot.sh"))

        val withArgs = LegacyTermuxBridge.wrapDeploy("QUJD", "flux_legacy_login_proot.sh", listOf("debian"))
        assertNotNull(withArgs)
        assertTrue(withArgs!!.endsWith("bash \$HOME/flux_legacy_login_proot.sh debian"))

        // Unsafe destination name
        assertNull(LegacyTermuxBridge.wrapDeploy("QUJD", "start_gui.sh", listOf("debian")))
        assertNull(LegacyTermuxBridge.wrapDeploy("QUJD", "flux_legacy_bad.txt", emptyList()))
        assertNull(LegacyTermuxBridge.wrapDeploy("QUJD", "flux_legacy_evil.sh;rm", emptyList()))

        // Unsafe args
        assertNull(LegacyTermuxBridge.wrapDeploy("QUJD", "flux_legacy_login_proot.sh", listOf("debian;rm -rf /")))
        assertNull(LegacyTermuxBridge.wrapDeploy("QUJD", "flux_legacy_login_proot.sh", listOf("../etc")))
        assertNull(LegacyTermuxBridge.wrapDeploy("QUJD", "flux_legacy_login_proot.sh", listOf("")))
        assertNull(LegacyTermuxBridge.wrapDeploy("QUJD", "flux_legacy_login_proot.sh", listOf("deb ian")))
    }

    @Test
    fun specBuilders_specAttributes() {
        val listSpec = LegacyTermuxBridge.buildListSpec("QUJD")
        assertEquals("com.termux", listSpec.packageName)
        assertEquals("com.termux.app.RunCommandService", listSpec.className)
        assertEquals("/data/data/com.termux/files/usr/bin/bash", listSpec.commandPath)
        assertEquals("/data/data/com.termux/files/home", listSpec.workdir)
        assertTrue(listSpec.background)
        assertEquals("-c", listSpec.arguments[0])
        assertTrue(listSpec.arguments[1].contains("flux_legacy_list_proot.sh"))
        assertFalse(listSpec.arguments[1].contains("com.ivarna.fluxlinux"))

        val pingSpec = LegacyTermuxBridge.buildPingSpec()
        assertTrue(pingSpec.background)
        assertTrue(pingSpec.arguments[1].contains("legacy_termux_ping"))

        val loginSpec = LegacyTermuxBridge.buildLoginSpec("QUJD", "debian")
        assertNotNull(loginSpec)
        assertFalse(loginSpec!!.background)
        assertTrue(loginSpec.arguments[1].contains("flux_legacy_login_proot.sh debian"))

        val startSpec = LegacyTermuxBridge.buildStartDisplaySpec("QUJD", "debian")
        assertNotNull(startSpec)
        assertFalse(startSpec!!.background)
        assertTrue(startSpec.arguments[1].contains("flux_legacy_start_display.sh debian"))

        val stopSpec = LegacyTermuxBridge.buildStopDisplaySpec("QUJD", "debian")
        assertNotNull(stopSpec)
        assertFalse(stopSpec!!.background)
        assertTrue(stopSpec.arguments[1].contains("flux_legacy_stop_display.sh debian"))

        val uninstallSpec = LegacyTermuxBridge.buildUninstallSpec("QUJD", "debian")
        assertNotNull(uninstallSpec)
        assertFalse(uninstallSpec!!.background)
        assertTrue(uninstallSpec.arguments[1].contains("flux_legacy_uninstall_proot.sh debian"))
    }

    @Test
    fun specBuilders_nullOnUnsafeIds() {
        assertNull(LegacyTermuxBridge.buildLoginSpec("QUJD", "debian;rm"))
        assertNull(LegacyTermuxBridge.buildStartDisplaySpec("QUJD", "debian;rm"))
        assertNull(LegacyTermuxBridge.buildStopDisplaySpec("QUJD", "debian;rm"))
        assertNull(LegacyTermuxBridge.buildUninstallSpec("QUJD", "debian;rm"))

        // Termux native id must never be passed
        assertNull(LegacyTermuxBridge.buildLoginSpec("QUJD", "termux"))
        assertNull(LegacyTermuxBridge.buildStartDisplaySpec("QUJD", "termux"))
        assertNull(LegacyTermuxBridge.buildStopDisplaySpec("QUJD", "termux"))
        assertNull(LegacyTermuxBridge.buildUninstallSpec("QUJD", "termux"))

        assertNull(LegacyTermuxBridge.buildLoginSpec("QUJD", ""))
        assertNull(LegacyTermuxBridge.buildLoginSpec("QUJD", "deb ian"))
        assertNull(LegacyTermuxBridge.buildLoginSpec("QUJD", "../etc"))
    }

    @Test
    fun hostPath_resolution() {
        assertEquals(
            "/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/debian",
            LegacyTermuxBridge.hostPath("debian", "installed-rootfs")
        )
        assertEquals(
            "/data/data/com.termux/files/usr/var/lib/proot-distro/containers/debian",
            LegacyTermuxBridge.hostPath("debian", "containers")
        )
        assertNull(LegacyTermuxBridge.hostPath("debian", "unknown"))
        assertNull(LegacyTermuxBridge.hostPath("debian;rm", "containers"))
    }

    @Test
    fun isVersionOlderThan_checks() {
        assertTrue(LegacyTermuxBridge.isVersionOlderThan("0.117.0", "0.118.3"))
        assertTrue(LegacyTermuxBridge.isVersionOlderThan("0.118.2", "0.118.3"))
        assertFalse(LegacyTermuxBridge.isVersionOlderThan("0.118.3", "0.118.3"))
        assertFalse(LegacyTermuxBridge.isVersionOlderThan("0.119.0", "0.118.3"))
        assertFalse(LegacyTermuxBridge.isVersionOlderThan("Not Installed", "0.118.3"))
    }

    @Test
    fun sourceGrep_bridgeRules() {
        val src = repoFile("src/main/kotlin/com/ivarna/fluxlinux/core/legacy/LegacyTermuxBridge.kt").readText()
        assertTrue(src.contains("setClassName(spec.packageName, spec.className)"))
        assertFalse(src.contains("TermuxHostPaths.PREFIX"))
        assertFalse(src.contains("fluxlinux-host.env"))
        assertFalse(src.contains("TERMUX_X11_OVERRIDE"))
    }
}
