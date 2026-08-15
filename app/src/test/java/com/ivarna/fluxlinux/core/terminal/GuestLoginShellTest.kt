package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestLoginShellTest {

    @Test
    fun fromId_defaultsToZsh() {
        assertEquals(GuestLoginShell.ZSH, GuestLoginShell.fromId(null))
        assertEquals(GuestLoginShell.ZSH, GuestLoginShell.fromId(""))
        assertEquals(GuestLoginShell.ZSH, GuestLoginShell.fromId("zsh"))
        assertEquals(GuestLoginShell.ZSH, GuestLoginShell.fromId("ZSH"))
    }

    @Test
    fun fromId_acceptsBash() {
        assertEquals(GuestLoginShell.BASH, GuestLoginShell.fromId("bash"))
        assertEquals(GuestLoginShell.BASH, GuestLoginShell.fromId("BASH"))
    }

    @Test
    fun fromId_unknownFallsBackToZsh() {
        assertEquals(GuestLoginShell.ZSH, GuestLoginShell.fromId("fish"))
    }

    @Test
    fun loginSentinels_areTrue() {
        assertTrue(GuestLoginShell.isLoginSentinel(""))
        assertTrue(GuestLoginShell.isLoginSentinel("exec zsh"))
        assertTrue(GuestLoginShell.isLoginSentinel("exec bash"))
        assertTrue(GuestLoginShell.isLoginSentinel("/bin/bash --login"))
        assertTrue(GuestLoginShell.isLoginSentinel(" exec zsh "))
    }

    @Test
    fun loginSentinels_rejectPayloads() {
        assertFalse(GuestLoginShell.isLoginSentinel("echo hi"))
        assertFalse(
            GuestLoginShell.isLoginSentinel(
                "mkdir -p /home/flux/p && cd /home/flux/p && exec zsh"
            )
        )
    }

    @Test
    fun interactiveLogin_includesWorkdirForm() {
        assertTrue(
            GuestLoginShell.isInteractiveLogin(
                "mkdir -p /home/flux/p && cd /home/flux/p && exec zsh"
            )
        )
        assertTrue(GuestLoginShell.isInteractiveLogin("exec zsh"))
        assertFalse(GuestLoginShell.isInteractiveLogin("echo hi"))
    }

    @Test
    fun qwenShapedPayload_isNotInteractive() {
        val qwen = "echo 'eHl6' | base64 -d | bash"
        assertFalse(GuestLoginShell.isLoginSentinel(qwen))
        assertFalse(GuestLoginShell.isInteractiveLogin(qwen))
    }

    @Test
    fun parseInteractiveWorkdir_extractsDir() {
        assertEquals(
            "/home/flux/p",
            GuestLoginShell.parseInteractiveWorkdir(
                "mkdir -p /home/flux/p && cd /home/flux/p && exec zsh"
            )
        )
        assertEquals(
            "/home/flux/p",
            GuestLoginShell.parseInteractiveWorkdir(
                "mkdir -p /home/flux/p && cd /home/flux/p && exec bash"
            )
        )
    }

    @Test
    fun parseInteractiveWorkdir_rejectsBadInput() {
        assertNull(
            GuestLoginShell.parseInteractiveWorkdir(
                "mkdir -p /home/flux/o'brien && cd /home/flux/o'brien && exec zsh"
            )
        )
        assertNull(GuestLoginShell.parseInteractiveWorkdir("echo hi"))
        assertNull(GuestLoginShell.parseInteractiveWorkdir("exec zsh"))
        assertNull(GuestLoginShell.parseInteractiveWorkdir("mkdir -p /a && cd /b && exec zsh"))
    }

    @Test
    fun prootLoginCascade_zshIsByteEquivalentToLegacy() {
        val legacy =
            "/bin/sh -lc 'if [ -x /bin/zsh ]; then exec /bin/zsh -l; " +
                "elif [ -x /bin/bash ]; then exec /bin/bash -l; else exec /bin/sh -l; fi'"
        assertEquals(legacy, GuestLoginShell.prootLoginCascade(GuestLoginShell.ZSH))
    }

    @Test
    fun prootLoginCascade_preferredFirst() {
        val zsh = GuestLoginShell.prootLoginCascade(GuestLoginShell.ZSH)
        assertTrue(zsh.indexOf("/bin/zsh") < zsh.indexOf("/bin/bash"))

        val bash = GuestLoginShell.prootLoginCascade(GuestLoginShell.BASH)
        assertTrue(bash.indexOf("/bin/bash") < bash.indexOf("/bin/zsh"))
        assertTrue(bash.endsWith("exec /bin/sh -l; fi'"))
    }
}
