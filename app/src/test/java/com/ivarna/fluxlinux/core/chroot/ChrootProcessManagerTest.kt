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

    @Test
    fun mergeRemaining_mergesAndDeduplicatesProductionKillResults() {
        val res1 = ChrootProcessManager.KillResult(
            killed = 2,
            failed = 0,
            remaining = listOf(
                ChrootProcessManager.Proc(101, "bash", "/bin/bash"),
                ChrootProcessManager.Proc(102, "sleep", "/bin/sleep 100")
            ),
            verifiedClean = false,
            raw = "",
            rootOk = true
        )
        val res2 = ChrootProcessManager.KillResult(
            killed = 1,
            failed = 0,
            remaining = listOf(
                ChrootProcessManager.Proc(102, "sleep", "/bin/sleep 100"), // duplicate PID across paths
                ChrootProcessManager.Proc(103, "python", "/usr/bin/python")
            ),
            verifiedClean = false,
            raw = "",
            rootOk = true
        )

        val merged = ChrootProcessManager.mergeRemaining(listOf(res1, res2))

        assertEquals(3, merged.size)
        assertEquals(listOf(101, 102, 103), merged.map { it.pid })
        assertEquals("bash", merged[0].comm)
        assertEquals("sleep", merged[1].comm)
        assertEquals("python", merged[2].comm)
    }

    @Test
    fun mergeProcs_mergesAndDeduplicatesProductionProcLists() {
        val list1 = listOf(
            ChrootProcessManager.Proc(201, "zsh", "/bin/zsh"),
            ChrootProcessManager.Proc(202, "tmux", "/usr/bin/tmux")
        )
        val list2 = listOf(
            ChrootProcessManager.Proc(202, "tmux", "/usr/bin/tmux"),
            ChrootProcessManager.Proc(203, "top", "/usr/bin/top")
        )

        val merged = ChrootProcessManager.mergeProcs(listOf(list1, list2))

        assertEquals(3, merged.size)
        assertEquals(listOf(201, 202, 203), merged.map { it.pid })
    }
}
