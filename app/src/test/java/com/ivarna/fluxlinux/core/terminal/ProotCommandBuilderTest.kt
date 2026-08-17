package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotCommandBuilderTest {

    @Test
    fun guestLoginEnv_setsPulseTcpLocalhost() {
        val env = ProotCommandBuilder.guestLoginEnv("flux")
        assertTrue(env.contains("PULSE_SERVER=tcp:127.0.0.1"))
        assertTrue(env.startsWith("env -i "))
        assertTrue(!env.contains("LD_LIBRARY_PATH"))
    }

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
        assertTrue(joined.contains("env -i"))
        assertTrue(joined.indexOf("if [ -x /bin/zsh ]") < joined.indexOf("if [ -x /bin/bash ]"))
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
        assertTrue(joined.contains("PULSE_SERVER=tcp:127.0.0.1"))
        assertTrue(joined.contains("LANG=C"))
        assertTrue(!joined.contains("LC_ALL=en_US.UTF-8"))
        assertTrue(!joined.contains("LANG=en_US.UTF-8"))
        assertTrue(!joined.contains("PROOT_TMP_DIR="))
        assertTrue(joined.indexOf("if [ -x /bin/zsh ]") < joined.indexOf("if [ -x /bin/bash ]"))
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

    @Test
    fun bashPref_cascadePutsBashFirst_evenWhenSentinelSaysZsh() {
        val args = ProotCommandBuilder.buildArgs(
            shell = "/bin/bash",
            prootDistro = "/pd",
            shellCmd = "exec zsh",
            user = "flux",
            loginShell = GuestLoginShell.BASH
        )
        val joined = args.joinToString(" ")
        assertTrue(joined.indexOf("if [ -x /bin/bash ]") < joined.indexOf("if [ -x /bin/zsh ]"))
        assertTrue(joined.endsWith("exec /bin/sh -l; fi'"))
    }

    @Test
    fun execBashSentinel_isInteractiveWithDefaultZshCascade() {
        val args = ProotCommandBuilder.buildArgs(
            shell = "/bin/bash",
            prootDistro = "/pd",
            shellCmd = "exec bash",
            user = "flux"
        )
        val joined = args.joinToString(" ")
        assertFalse(joined.contains("/bin/sh -c '"))
        assertTrue(joined.contains("env -i"))
        assertTrue(joined.indexOf("if [ -x /bin/zsh ]") < joined.indexOf("if [ -x /bin/bash ]"))
    }

    @Test
    fun qwenShapedPayload_staysShC_noCascade() {
        val args = ProotCommandBuilder.buildArgs(
            shell = "/bin/bash",
            prootDistro = "/pd",
            shellCmd = "echo 'eHl6' | base64 -d | bash",
            user = "flux"
        )
        val joined = args.joinToString(" ")
        assertTrue(joined.contains("/bin/sh -c 'echo '\\''eHl6'\\'' | base64 -d | bash'"))
        assertFalse(joined.contains("if [ -x /bin/zsh ]"))
    }

    @Test
    fun workdirForm_staysPayloadWithMkdirAndCd() {
        val workdir = "mkdir -p /home/flux/p && cd /home/flux/p && exec zsh"
        val args = ProotCommandBuilder.buildArgs(
            shell = "/bin/bash",
            prootDistro = "/pd",
            shellCmd = workdir,
            user = "flux"
        )
        val joined = args.joinToString(" ")
        assertTrue(joined.contains("/bin/sh -c"))
        assertTrue(joined.contains("mkdir -p /home/flux/p"))
        assertTrue(joined.contains("cd /home/flux/p"))
        assertFalse(joined.contains("if [ -x /bin/zsh ]"))
    }

    @Test
    fun blankShellCmd_isInteractiveWithZshFirstCascade() {
        val args = ProotCommandBuilder.buildArgs(
            shell = "/bin/bash",
            prootDistro = "/pd",
            shellCmd = "",
            user = "flux"
        )
        val joined = args.joinToString(" ")
        assertFalse(joined.contains("/bin/sh -c '"))
        assertTrue(joined.contains("env -i"))
        assertTrue(joined.indexOf("if [ -x /bin/zsh ]") < joined.indexOf("if [ -x /bin/bash ]"))
    }
}
