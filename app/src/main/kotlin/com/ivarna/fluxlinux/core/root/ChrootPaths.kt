package com.ivarna.fluxlinux.core.root

/**
 * On-device chroot SSOT paths + helper identity.
 * Shared by [com.ivarna.fluxlinux.core.terminal.ChrootCommandBuilder] and
 * [RootShell] without an import cycle (RootShell = su only, never imports
 * ChrootCommandBuilder — plan §2.5).
 */
object ChrootPaths {
    const val CHROOT_PATH = "/data/local/tmp/chrootDebian13"

    /** On-device SSOT helper (assets/scripts/chroot/fluxlinux_chroot.sh). */
    const val CHROOT_HELPER = "/data/local/tmp/fluxlinux_chroot.sh"
    const val CHROOT_HELPER_ASSET = "scripts/chroot/fluxlinux_chroot.sh"
    /** Must match first `# fluxlinux-chroot vN` line in the asset. */
    const val CHROOT_HELPER_VERSION = "fluxlinux-chroot v2.2"

    /** Session executable — must be a system binary (SELinux blocks exec of app-data scripts). */
    const val SESSION_EXEC = "/system/bin/sh"
}
