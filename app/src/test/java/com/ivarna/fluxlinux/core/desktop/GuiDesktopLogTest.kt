package com.ivarna.fluxlinux.core.desktop

import com.ivarna.fluxlinux.core.terminal.FakeContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GuiDesktopLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var ctx: FakeContext

    @Before
    fun setUp() {
        val files = tmp.newFolder("files")
        ctx = FakeContext(files, nativeLibDir = File(files, "lib").absolutePath)
    }

    @Test
    fun emptyLog_hasNoContent() {
        GuiDesktopLog.clear(ctx)
        assertFalse(GuiDesktopLog.hasContent(ctx))
        assertTrue(GuiDesktopLog.read(ctx).isEmpty())
    }

    @Test
    fun appendAndHeader_areReadable() {
        GuiDesktopLog.clear(ctx)
        GuiDesktopLog.header(ctx, "START", "start_gui.sh", "proot")
        GuiDesktopLog.append(ctx, "FluxLinux: Starting termux-x11 server...")
        GuiDesktopLog.append(ctx, "FluxLinux: X server PID=123")
        assertTrue(GuiDesktopLog.hasContent(ctx))
        val text = GuiDesktopLog.read(ctx)
        assertTrue(text.contains("=== START method=proot script=start_gui.sh ==="))
        assertTrue(text.contains("X server PID=123"))
    }

    @Test
    fun clear_wipesFile() {
        GuiDesktopLog.append(ctx, "keep-me")
        assertTrue(GuiDesktopLog.hasContent(ctx))
        GuiDesktopLog.clear(ctx)
        assertTrue(GuiDesktopLog.read(ctx).isEmpty() || !GuiDesktopLog.hasContent(ctx))
    }
}
