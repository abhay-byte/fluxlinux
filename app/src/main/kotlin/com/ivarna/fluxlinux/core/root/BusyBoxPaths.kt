package com.ivarna.fluxlinux.core.root

/**
 * On-device BusyBox resolve paths. Shared by [RootShell] and unit tests.
 * Shell resolver walks the same list (plus FLUX_BB, PINNED, and `command -v`).
 */
object BusyBoxPaths {
    const val PINNED = "/data/local/tmp/flux_busybox"
    const val RESOLVER_ASSET = "scripts/chroot/resolve_bb.sh"
    const val RESOLVER_ON_DEVICE = "/data/local/tmp/fluxlinux_resolve_bb.sh"
    val CANDIDATES: List<String> = listOf(
        "/data/adb/ksu/bin/busybox",
        "/data/adb/ap/bin/busybox",
        "/data/adb/magisk/busybox",
        "/data/adb/modules/busybox-ndk/system/xbin/busybox",
        "/data/adb/modules/busybox-ndk/system/bin/busybox",
        "/debug_ramdisk/busybox",
        "/sbin/busybox",
        "/system/xbin/busybox",
        "/system/bin/busybox",
    )
}
