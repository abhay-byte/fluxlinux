package com.ivarna.fluxlinux.core.chroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChrootProcessManagerTest {

    @Test
    fun parseList_parsesPidCommCmdline() {
        val raw = """
            # chroot_processes v1
            # path=/data/local/tmp/chrootDebian13
            123	zsh	/bin/zsh
            456	bash	
            # count=2
        """.trimIndent()
        val r = ChrootProcessManager.parseList(raw, "/data/local/tmp/chrootDebian13")
        assertEquals(2, r.processes.size)
        assertEquals(123, r.processes[0].pid)
        assertEquals("zsh", r.processes[0].comm)
        assertEquals(456, r.processes[1].pid)
    }

    @Test
    fun parseKill_readsSummaryAndRemaining() {
        val raw = """
            # chroot_processes v1
            # path=/data/local/tmp/chrootDebian13
            # killed=3 failed=0
            # chroot_processes v1
            # path=/data/local/tmp/chrootDebian13
            99	orphan	/usr/bin/foo
            # count=1
        """.trimIndent()
        val r = ChrootProcessManager.parseKill(raw, "/data/local/tmp/chrootDebian13", rootOk = true)
        assertEquals(3, r.killed)
        assertEquals(0, r.failed)
        assertEquals(1, r.remaining.size)
        assertEquals(99, r.remaining[0].pid)
        assertTrue(!r.verifiedClean)
    }
}
