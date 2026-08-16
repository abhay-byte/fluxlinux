package com.ivarna.fluxlinux.core.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegacyTermuxScriptsContractTest {

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

    private val scriptsDir: File
        get() = repoFile("src/main/assets/scripts/legacy-termux")

    @Test
    fun allScripts_shebangAndNoEmbeddedOrFlavorPollution() {
        val scripts = scriptsDir.listFiles { f -> f.isFile && f.name.endsWith(".sh") }
            ?: error("no scripts found in $scriptsDir")

        assertTrue("Expected 5 legacy termux scripts", scripts.size >= 5)

        for (script in scripts) {
            val content = script.readText()
            val lines = content.lines()
            assertTrue(
                "${script.name} shebang must start with #!/data/data/com.termux/files/usr/bin/bash",
                lines.firstOrNull()?.startsWith("#!/data/data/com.termux/files/usr/bin/bash") == true
            )
            assertFalse(
                "${script.name} must not contain com.ivarna.fluxlinux",
                content.contains("com.ivarna.fluxlinux")
            )
            assertFalse(
                "${script.name} must not contain com.zenithblue.fluxlinux",
                content.contains("com.zenithblue.fluxlinux")
            )
            assertFalse(
                "${script.name} must not contain fluxlinux-host.env",
                content.contains("fluxlinux-host.env")
            )
            assertFalse(
                "${script.name} must not contain TERMUX_X11_OVERRIDE",
                content.contains("TERMUX_X11_OVERRIDE")
            )
            assertFalse(
                "${script.name} must not contain /system/bin/su",
                content.contains("/system/bin/su")
            )
        }
    }

    @Test
    fun startDisplay_contract() {
        val content = File(scriptsDir, "start_display.sh").readText()
        assertTrue(content.contains("com.termux.x11/com.termux.x11.MainActivity"))
        assertTrue(content.contains("virgl_test_server_android"))
        assertTrue(content.contains("proot-distro login"))
        assertTrue(content.contains("su - flux"))
        assertTrue(content.contains("startxfce4"))
        assertFalse(content.contains("TERMUX_X11_OVERRIDE"))
        assertFalse(content.contains("fluxlinux-host.env"))
    }

    @Test
    fun stopDisplay_contract() {
        val content = File(scriptsDir, "stop_display.sh").readText()
        assertTrue(content.contains("ACTION_STOP -p com.termux.x11"))
        assertFalse(content.contains("-p com.ivarna.fluxlinux"))
        assertFalse(content.contains("-p com.zenithblue.fluxlinux"))
    }

    @Test
    fun uninstallProot_contract() {
        val content = File(scriptsDir, "uninstall_proot.sh").readText()
        assertTrue(content.contains("proot-distro remove"))
        assertTrue(content.contains("installed-rootfs"))
        assertTrue(content.contains("containers"))
        assertTrue(content.contains("legacy_termux_uninstall_"))
        assertTrue(content.contains("reason=unknown"))
        assertTrue(content.contains("reason=bad_id"))
        assertTrue(content.contains("! -e \"\$OLD\"") && content.contains("! -e \"\$NEW\""))
    }

    @Test
    fun loginProot_contract() {
        val content = File(scriptsDir, "login_proot.sh").readText()
        assertTrue(content.contains("proot-distro login"))
        assertTrue(content.contains("--user flux"))
    }

    @Test
    fun listProot_contract() {
        val content = File(scriptsDir, "list_proot.sh").readText()
        assertTrue(content.contains("legacy_termux_list"))
        assertTrue(content.contains("installed-rootfs"))
        assertTrue(content.contains("containers"))
        assertTrue(content.contains("du -sb"))
    }
}
