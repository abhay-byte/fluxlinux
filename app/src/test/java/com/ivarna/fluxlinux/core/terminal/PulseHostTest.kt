package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PulseHostTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var ctx: FakeContext

    @Before
    fun setUp() {
        val files = tmp.newFolder("files")
        ctx = FakeContext(files, nativeLibDir = File(files, "lib").absolutePath)
    }

    @Test
    fun parseStatus_stopped() {
        val s = PulseHost.parseStatus("FLUX_PULSE_RUNNING=0\nFLUX_PULSE_TCP=0\n")
        assertFalse(s.running)
        assertFalse(s.tcpOk)
        assertFalse(s.healthy)
        assertEquals("Stopped", s.label)
    }

    @Test
    fun parseStatus_healthyAaudio() {
        val s = PulseHost.parseStatus(
            """
            FLUX_PULSE_RUNNING=1
            Server String: /data/data/com.ivarna.fluxlinux/files/home/.pulse/native
            Default Sink: AAudio_sink
            Default Source: AAudio_sink.monitor
            FLUX_PULSE_TCP=1
            """.trimIndent()
        )
        assertTrue(s.running)
        assertTrue(s.tcpOk)
        assertEquals("AAudio_sink", s.sink)
        assertTrue(s.healthy)
        assertEquals("Running", s.label)
        assertTrue(s.detail.contains("tcp=127.0.0.1:4713"))
    }

    @Test
    fun parseStatus_dummySink() {
        val s = PulseHost.parseStatus(
            "FLUX_PULSE_RUNNING=1\nDefault Sink: auto_null\nFLUX_PULSE_TCP=1\n"
        )
        assertTrue(s.running)
        assertFalse(s.healthy)
        assertEquals("Running (dummy sink)", s.label)
    }

    @Test
    fun parseDefaultSink_empty() {
        assertEquals("", PulseHost.parseDefaultSink("no sink here"))
    }

    @Test
    fun supervisorOk_successAndFail() {
        assertTrue(
            PulseHost.supervisorOk(
                "FluxLinux: [AUDIO] sink=AAudio_sink tcp=127.0.0.1:4713"
            )
        )
        assertTrue(
            PulseHost.supervisorOk(
                "FluxLinux: [AUDIO] already running sink=AAudio_sink tcp=127.0.0.1:4713"
            )
        )
        assertFalse(PulseHost.supervisorOk(""))
        assertFalse(
            PulseHost.supervisorOk(
                "FluxLinux: [AUDIO] FAIL sink=AAudio_sink but tcp:127.0.0.1:4713 not reachable"
            )
        )
        assertFalse(PulseHost.supervisorOk("daemon started"))
    }

    @Test
    fun repairToast_failPartialSuccess() {
        assertEquals(
            "Guest repair failed",
            PulseHost.repairToast("FluxLinux: [AUDIO] FAIL guest repair scripts not deployed")
        )
        assertEquals("No guests to repair", PulseHost.repairToast("nothing here"))
        assertEquals(
            "Guest repair had errors",
            PulseHost.repairToast(
                "FluxLinux: [AUDIO] repair proot debian\n" +
                    "FluxLinux: [AUDIO] WARN guest pactl still missing\n"
            )
        )
        assertEquals(
            "Guest repair partial",
            PulseHost.repairToast(
                """
                FluxLinux: [AUDIO] repair proot debian
                FluxLinux: [AUDIO] guest pactl=/usr/bin/pactl
                FluxLinux: [AUDIO] repair proot opensuse
                FluxLinux: [AUDIO] WARN guest pactl still missing
                """.trimIndent()
            )
        )
        assertEquals(
            "Guest audio repaired",
            PulseHost.repairToast(
                """
                FluxLinux: [AUDIO] repair proot debian
                FluxLinux: [AUDIO] guest pactl=/usr/bin/pactl
                FluxLinux: [AUDIO] repair chroot /data/local/tmp/chrootAlpine
                FluxLinux: [AUDIO] guest pactl=/usr/sbin/pactl
                """.trimIndent()
            )
        )
        assertEquals(
            "Guest repair had errors",
            PulseHost.repairToast(
                "FluxLinux: [AUDIO] repair proot debian\n" +
                    "FluxLinux: [AUDIO] guest pactl=/data/data/com.ivarna.fluxlinux/files/usr/bin/pactl\n"
            )
        )
    }

    @Test
    fun logRing_appendAndClear() {
        PulseHost.clearLog(ctx)
        assertFalse(PulseHost.hasLog(ctx))
        PulseHost.appendLog(ctx, "FluxLinux: [AUDIO] sink=AAudio_sink tcp=127.0.0.1:4713")
        assertTrue(PulseHost.hasLog(ctx))
        assertTrue(PulseHost.readLog(ctx).contains("AAudio_sink"))
        PulseHost.clearLog(ctx)
        assertTrue(PulseHost.readLog(ctx).isEmpty())
    }
}
