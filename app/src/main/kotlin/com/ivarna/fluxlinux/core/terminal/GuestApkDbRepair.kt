package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Best-effort repair for Alpine proot apk database ownership.
 *
 * Under proot, guest "root"/sudo runs as the Android app uid. If
 * `/lib/apk/db/lock` (or sibling db files) were written as **host root (uid 0)**,
 * `sudo apk update` fails with "Unable to lock database: Permission denied".
 *
 * App-owned trees under filesDir can be chmod'd/chown'd by the app. Host-root
 * owned files require real root (KernelSU) or re-running family setup after a
 * host-side chown — this helper only fixes what the app can write.
 */
object GuestApkDbRepair {

    private const val TAG = "GuestApkDbRepair"

    /**
     * @param method `proot` | `chroot` (chroot uses real root — skip)
     * @param distroId card id; only alpine/chimera proot is repaired
     */
    fun repairIfNeeded(ctx: Context, method: String, distroId: String? = null) {
        if (method != "proot") return
        val prootName = GuestZshrcRepair.resolveProotName(distroId)
        val isChimera = prootName == "chimera"
        if (prootName != "alpine" && !isChimera) return

        val rootfs = File(
            ctx.filesDir,
            "usr/var/lib/proot-distro/containers/$prootName/rootfs"
        )
        if (!rootfs.isDirectory) return

        // Chimera apk v3 lives at usr/lib/apk/db — never chown Alpine paths
        // inside a Chimera rootfs (wrong-path chown is worse than none).
        val relPaths = if (isChimera) {
            listOf("usr/lib/apk", "var/cache/apk", "etc/apk")
        } else {
            listOf("lib/apk", "var/cache/apk", "var/log", "etc/apk")
        }
        val dbRel = if (isChimera) "usr/lib/apk/db" else "lib/apk/db"

        try {
            val etc = File(rootfs, "etc")
            // Prefer matching /etc owner (app uid under proot)
            val refUid = etc.let { f ->
                try {
                    // Android File API has no getUid; use canWrite + chmod only
                    f
                } catch (_: Exception) {
                    null
                }
            }
            if (refUid == null) return

            for (rel in relPaths) {
                val dir = File(rootfs, rel)
                if (!dir.exists()) continue
                ensureWritableTree(dir)
            }

            val db = File(rootfs, dbRel)
            db.mkdirs()
            val lock = File(db, "lock")
            if (lock.exists() && !lock.canWrite()) {
                // Try delete+recreate when parent is writable
                if (db.canWrite()) {
                    lock.delete()
                    lock.writeText("")
                    lock.setReadable(true, false)
                    lock.setWritable(true, false)
                    Log.i(TAG, "Recreated apk lock at ${lock.absolutePath}")
                } else {
                    Log.w(TAG, "apk lock not writable (host-root owned?): ${lock.absolutePath}")
                }
            } else if (!lock.exists() && db.canWrite()) {
                lock.writeText("")
                lock.setReadable(true, false)
                lock.setWritable(true, false)
            } else if (lock.exists()) {
                // Ensure world/group write when we can
                lock.setWritable(true, false)
            }

            val log = File(rootfs, "var/log/apk.log")
            if (log.parentFile?.canWrite() == true) {
                if (!log.exists()) log.writeText("")
                log.setWritable(true, false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "apk db repair failed: ${e.message}")
        }
    }

    private fun ensureWritableTree(dir: File) {
        if (!dir.isDirectory) return
        // Best-effort: make dirs traversable and files group/other readable
        dir.setExecutable(true, false)
        dir.setReadable(true, false)
        if (dir.canWrite()) {
            dir.setWritable(true, false)
        }
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                ensureWritableTree(child)
            } else if (child.canWrite() || child.parentFile?.canWrite() == true) {
                child.setReadable(true, false)
                // Only force write if parent allows mutation
                if (child.parentFile?.canWrite() == true) {
                    child.setWritable(true, false)
                }
            }
        }
    }
}
