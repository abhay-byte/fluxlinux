package com.ivarna.fluxlinux.core.root

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RootShellBusyBoxTest {

    @After
    fun tearDown() {
        RootShell.clearBusyBoxCache()
    }

    @Test
    fun seed_then_cached_returns_path() {
        RootShell.seedBusyBoxForTest("/data/adb/ksu/bin/busybox")
        assertEquals("/data/adb/ksu/bin/busybox", RootShell.cachedBusyBox())
    }

    @Test
    fun clear_cache_returns_null() {
        RootShell.seedBusyBoxForTest("/data/adb/ksu/bin/busybox")
        RootShell.clearBusyBoxCache()
        assertNull(RootShell.cachedBusyBox())
    }
}
