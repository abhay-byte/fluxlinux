package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class GuestRootfsShellTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun alpine_absolute_sh_symlink_counts_as_installed() {
        val root = tmp.newFolder("alpine-rootfs")
        File(root, "bin").mkdirs()
        File(root, "bin/busybox").writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))
        Files.createSymbolicLink(File(root, "bin/sh").toPath(), File("/bin/busybox").toPath())
        // Host-style exists() is false for absolute guest symlink
        assertFalse(File(root, "bin/sh").exists())
        assertTrue(TerminalLauncher.guestRootfsHasShell(root))
    }

    @Test
    fun debian_relative_sh_works() {
        val root = tmp.newFolder("debian-rootfs")
        File(root, "bin").mkdirs()
        File(root, "bin/dash").writeText("x")
        Files.createSymbolicLink(File(root, "bin/sh").toPath(), File("dash").toPath())
        assertTrue(File(root, "bin/sh").exists())
        assertTrue(TerminalLauncher.guestRootfsHasShell(root))
    }

    @Test
    fun empty_root_false() {
        val root = tmp.newFolder("empty")
        assertFalse(TerminalLauncher.guestRootfsHasShell(root))
    }
}
