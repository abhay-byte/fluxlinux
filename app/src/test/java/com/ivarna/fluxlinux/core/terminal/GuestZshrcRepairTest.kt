package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class GuestZshrcRepairTest {

    @Test
    fun resolveProotName_alpineAndDebian() {
        assertEquals("alpine", GuestZshrcRepair.resolveProotName("alpine"))
        assertEquals("alpine", GuestZshrcRepair.resolveProotName("alpine_chroot"))
        assertEquals("debian", GuestZshrcRepair.resolveProotName("debian"))
        assertEquals("debian", GuestZshrcRepair.resolveProotName("debian13_chroot"))
        assertEquals("debian", GuestZshrcRepair.resolveProotName(null))
        assertEquals("fedora", GuestZshrcRepair.resolveProotName("fedora"))
        assertEquals("fedora", GuestZshrcRepair.resolveProotName("fedora_chroot"))
        assertEquals("void", GuestZshrcRepair.resolveProotName("void_chroot"))
        assertEquals("opensuse", GuestZshrcRepair.resolveProotName("opensuse"))
    }

    @Test
    fun needsRepair_hardOmzSource() {
        // Old non-defensive template: unconditional source, no existence guard.
        val bad = "export ZSH=\"\$HOME/.oh-my-zsh\"\nsource \$ZSH/oh-my-zsh.sh\n"
        assertTrue(GuestZshrcRepair.needsRepair(bad))
    }

    @Test
    fun needsRepair_hardPokemon() {
        val bad = "pokemon-colorscripts --no-title -r 1,2,3\n"
        assertTrue(GuestZshrcRepair.needsRepair(bad))
    }

    @Test
    fun needsRepair_guardedIsOk() {
        val good = """
            unset PROOT_TMP_DIR
            export ZSH="${'$'}{ZSH:-${'$'}HOME/.oh-my-zsh}"
            if [ -f "${'$'}ZSH/oh-my-zsh.sh" ]; then
              source "${'$'}ZSH/oh-my-zsh.sh"
            fi
        """.trimIndent()
        assertFalse(GuestZshrcRepair.needsRepair(good))
    }

    @Test
    fun needsRepair_missingProotTmpUnset() {
        val old = """
            export PATH="${'$'}HOME/.local/bin:${'$'}PATH"
            export ZSH="${'$'}{ZSH:-${'$'}HOME/.oh-my-zsh}"
            if [ -f "${'$'}ZSH/oh-my-zsh.sh" ]; then
              source "${'$'}ZSH/oh-my-zsh.sh"
            fi
        """.trimIndent()
        assertTrue(GuestZshrcRepair.needsRepair(old))
    }

    @Test
    fun ensureZprofile_writesOnce() {
        val dir = createTempDirectory("flux-zprofile").toFile()
        try {
            GuestZshrcRepair.ensureZprofile(dir)
            val f = File(dir, ".zprofile")
            assertTrue(f.isFile)
            assertTrue(f.readText().contains(".zshrc"))
            val first = f.readText()
            GuestZshrcRepair.ensureZprofile(dir)
            assertEquals(first, f.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun ensureFluxLoginShellZsh_updatesPasswd() {
        val dir = createTempDirectory("flux-zshrc").toFile()
        try {
            File(dir, "bin").mkdirs()
            File(dir, "bin/zsh").writeText("#!/bin/zsh\n")
            File(dir, "etc").mkdirs()
            val passwd = File(dir, "etc/passwd")
            passwd.writeText(
                "root:x:0:0:root:/root:/bin/sh\n" +
                    "flux:x:1000:1000::/home/flux:/bin/bash\n"
            )
            GuestZshrcRepair.ensureFluxLoginShellZsh(dir)
            val line = passwd.readLines().first { it.startsWith("flux:") }
            assertTrue(line.endsWith(":/bin/zsh"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun ensureFluxLoginShellZsh_noopWithoutZsh() {
        val dir = createTempDirectory("flux-zshrc-no").toFile()
        try {
            File(dir, "etc").mkdirs()
            val passwd = File(dir, "etc/passwd")
            passwd.writeText("flux:x:1000:1000::/home/flux:/bin/bash\n")
            GuestZshrcRepair.ensureFluxLoginShellZsh(dir)
            assertTrue(passwd.readText().contains(":/bin/bash"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
