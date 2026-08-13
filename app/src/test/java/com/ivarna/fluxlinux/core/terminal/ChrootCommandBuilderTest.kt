package com.ivarna.fluxlinux.core.terminal

import com.ivarna.fluxlinux.core.root.ChrootPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure argv shape tests for chroot sessions (SSOT helper; no device / no Context). */
class ChrootCommandBuilderTest {

    private val helper = ChrootPaths.CHROOT_HELPER

    @Test
    fun sessionExec_isSystemSh() {
        assertEquals("/system/bin/sh", ChrootCommandBuilder.SESSION_EXEC)
        assertEquals("/system/bin/sh", ChrootPaths.SESSION_EXEC)
    }

    @Test
    fun interactiveFlux_loginZsh() {
        val inner = ChrootCommandBuilder.buildRootInner("exec zsh", user = "flux")
        assertEquals("exec sh $helper login --user flux --shell zsh", inner)
    }

    @Test
    fun interactiveRoot_loginBash() {
        val inner = ChrootCommandBuilder.buildRootInner("", user = "root")
        assertEquals("exec sh $helper login --user root --shell bash", inner)
    }

    @Test
    fun interactiveAlpine_loginSh() {
        val inner = ChrootCommandBuilder.buildRootInner(
            "exec zsh",
            user = "flux",
            loginShellFlux = "sh",
            loginShellRoot = "sh"
        )
        assertEquals("exec sh $helper login --user flux --shell sh", inner)
    }

    @Test
    fun workdir_login_usesWorkdir() {
        val inner = ChrootCommandBuilder.buildRootInner("mkdir -p /home/flux/proj && cd /home/flux/proj && exec zsh", user = "flux")
        assertEquals("exec sh $helper login --user flux --shell zsh --workdir '/home/flux/proj'", inner)
    }

    @Test
    fun simpleCommand_routesToSh() {
        val inner = ChrootCommandBuilder.buildRootInner("whoami; id", user = "flux")
        assertEquals("exec sh $helper sh --user flux -- 'whoami; id'", inner)
    }

    @Test
    fun complexCommand_routesToB64() {
        val inner = ChrootCommandBuilder.buildRootInner("echo \$HOME && echo \"hi\"", user = "flux")
        assertTrue(inner.startsWith("exec sh $helper b64 --user flux -- "))
        assertTrue(inner.dropLast(1).endsWith("==") || inner.length > "exec sh $helper b64 --user flux -- ".length + 8)
    }

    @Test
    fun b64Payload_decodesToOriginal() {
        val payload = "echo \$HOME && echo \"hi\""
        val inner = ChrootCommandBuilder.buildRootInner(payload, user = "flux")
        val b64 = inner.substringAfter("-- ")
        val decoded = String(java.util.Base64.getDecoder().decode(b64))
        assertEquals(payload, decoded)
    }

    @Test
    fun rootSimpleCommand_routesToSh() {
        val inner = ChrootCommandBuilder.buildRootInner("echo hi", user = "root")
        assertEquals("exec sh $helper sh --user root -- 'echo hi'", inner)
    }

    @Test
    fun quotePayload_forcesB64() {
        // Single quotes break the `sh -- '...'` wrapper → must route to b64.
        val inner = ChrootCommandBuilder.buildRootInner("echo it's", user = "flux")
        assertTrue(inner.startsWith("exec sh $helper b64 --user flux -- "))
    }

    @Test
    fun winchWrap_preservesCommand() {
        val wrapped = ChrootCommandBuilder.winchWrap("su -c 'x'")
        assertTrue(wrapped.startsWith("trap 'kill -WINCH -"))
        assertTrue(wrapped.endsWith("WINCH; su -c 'x'"))
    }

    @Test
    fun rootSessionArgv_startsWithSystemShDashC() {
        val winch = InstallSessionFactory.rootSessionWinchCommand("su -c 'sh /tmp/x.sh'")
        val argv = InstallSessionFactory.rootSessionArgv(winch)
        assertEquals(3, argv.size)
        assertEquals("/system/bin/sh", argv[0])
        assertEquals("-c", argv[1])
        assertEquals(winch, argv[2])
        assertTrue(argv[2].startsWith("trap 'kill -WINCH -"))
    }

    @Test
    fun buildEnv_androidPathAndGuestHome() {
        val env = ChrootCommandBuilder.buildEnv(user = "root")
        assertEquals("/root", env["HOME"])
        assertEquals("/system/bin:/system/xbin:/sbin:" + System.getenv("PATH").orEmpty(), env["PATH"])
        val fluxEnv = ChrootCommandBuilder.buildEnv(user = "flux")
        assertEquals("/home/flux", fluxEnv["HOME"])
        assertEquals(ChrootPaths.CHROOT_PATH, fluxEnv["FLUX_CHROOT"])
    }

    @Test
    fun buildEnv_alpinePath() {
        val env = ChrootCommandBuilder.buildEnv(
            user = "flux",
            chrootPath = ChrootPaths.ALPINE_CHROOT_PATH
        )
        assertEquals(ChrootPaths.ALPINE_CHROOT_PATH, env["FLUX_CHROOT"])
    }
}
