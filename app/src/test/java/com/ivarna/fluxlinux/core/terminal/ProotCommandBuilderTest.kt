package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertTrue
import org.junit.Test

class ProotCommandBuilderTest {

    @Test
    fun login_defaults_to_debian() {
        val args = ProotCommandBuilder.buildArgs(
            shell = "/bin/bash",
            prootDistro = "/pd",
            shellCmd = "exec zsh",
            user = "flux"
        )
        val joined = args.joinToString(" ")
        assertTrue(joined.contains("login debian"))
    }

    @Test
    fun login_alpine() {
        val args = ProotCommandBuilder.buildArgs(
            shell = "/bin/bash",
            prootDistro = "/pd",
            shellCmd = "exec zsh",
            user = "flux",
            distro = "alpine"
        )
        val joined = args.joinToString(" ")
        assertTrue(joined.contains("login alpine"))
        assertTrue(!joined.contains("login debian"))
        assertTrue(joined.contains("env -i"))
        assertTrue(joined.contains("TMPDIR=/tmp"))
        assertTrue(joined.contains("LANG=C"))
        assertTrue(!joined.contains("LC_ALL=en_US.UTF-8"))
        assertTrue(!joined.contains("LANG=en_US.UTF-8"))
        assertTrue(!joined.contains("PROOT_TMP_DIR="))
    }

    @Test
    fun guest_payload_uses_sh() {
        val args = ProotCommandBuilder.buildArgs(
            shell = "/bin/bash",
            prootDistro = "/pd",
            shellCmd = "echo hi",
            user = "root",
            distro = "alpine"
        )
        val joined = args.joinToString(" ")
        assertTrue(joined.contains("login alpine"))
        assertTrue(joined.contains("sh -c"))
        assertTrue(joined.contains("env -i"))
        assertTrue(joined.contains("TMPDIR=/tmp"))
    }
}
