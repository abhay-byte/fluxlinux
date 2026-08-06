package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure argv shape tests for proot sessions (no device / no Context). */
class ProotCommandBuilderTest {

    private val shell = "/data/data/com.ivarna.fluxlinux/files/usr/bin/bash"
    private val pd = "/data/data/com.ivarna.fluxlinux/files/usr/bin/proot-distro"

    @Test
    fun interactiveLogin_usesLoginForm() {
        val args = ProotCommandBuilder.buildArgs(shell, pd, "exec zsh")
        assertArrayEquals(
            arrayOf(
                shell, "-c",
                "exec python $pd login debian --shared-tmp --user flux"
            ),
            args
        )
    }

    @Test
    fun blankShell_isInteractive() {
        val args = ProotCommandBuilder.buildArgs(shell, pd, "", user = "root")
        assertTrue(args[2].contains("--user root"))
        assertTrue(args[2].contains("login debian"))
    }

    @Test
    fun rootUser_usesRootFlag() {
        val args = ProotCommandBuilder.buildArgs(shell, pd, "exec zsh", user = "root")
        assertTrue(args[2].contains("--user root"))
    }

    @Test
    fun payload_quotesGuestOnly() {
        val args = ProotCommandBuilder.buildArgs(shell, pd, "echo \$HOME")
        // Guest payload must stay single-quoted so host bash does not expand $HOME.
        assertEquals("exec python $pd login debian --shared-tmp --user flux -- zsh -c 'echo \$HOME'", args[2])
    }

    @Test
    fun payload_escapesEmbeddedQuotes() {
        val args = ProotCommandBuilder.buildArgs(shell, pd, "echo 'it\\'s'")
        assertEquals("exec python $pd login debian --shared-tmp --user flux -- zsh -c 'echo '\\''it\\'\\''s'\\'''", args[2])
    }

    @Test
    fun noSharedTmp_whenDisabled() {
        val args = ProotCommandBuilder.buildArgs(shell, pd, "exec zsh", useSharedTmp = false)
        assertEquals("exec python $pd login debian  --user flux".trimEnd().replace("  ", " "), args[2].replace("  ", " "))
    }
}
