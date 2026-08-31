package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class GuestApkDbRepairTest {

    private fun filesDir(): File = createTempDirectory("flux-apkdb").toFile()

    @Test
    fun prootAlpine_repairsThenCaches() {
        val dir = filesDir()
        try {
            val rootfs = File(
                dir,
                "usr/var/lib/proot-distro/containers/alpine/rootfs"
            )
            rootfs.mkdirs()
            val ctx = FakeContext(dir, "$dir/jni")
            GuestApkDbRepair.repairIfNeeded(ctx, "proot", "alpine")
            val db = File(rootfs, "lib/apk/db")
            assertTrue("apk db lock should be created", File(db, "lock").isFile)

            // Second pass is a cached no-op (proot-opt-01): delete the lock and
            // re-run — the sweep must not recreate it.
            File(db, "lock").delete()
            GuestApkDbRepair.repairIfNeeded(ctx, "proot", "alpine")
            assertFalse("cached pass should skip the apk db sweep", File(db, "lock").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun prootDebian_isNotRepaired() {
        val dir = filesDir()
        try {
            val rootfs = File(
                dir,
                "usr/var/lib/proot-distro/containers/debian/rootfs"
            )
            rootfs.mkdirs()
            val ctx = FakeContext(dir, "$dir/jni")
            GuestApkDbRepair.repairIfNeeded(ctx, "proot", "debian")
            assertFalse(File(rootfs, "lib/apk/db/lock").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun chrootMethod_isNotRepaired() {
        val dir = filesDir()
        try {
            val rootfs = File(
                dir,
                "usr/var/lib/proot-distro/containers/alpine/rootfs"
            )
            rootfs.mkdirs()
            val ctx = FakeContext(dir, "$dir/jni")
            GuestApkDbRepair.repairIfNeeded(ctx, "chroot", "alpine")
            assertFalse(File(rootfs, "lib/apk/db/lock").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}