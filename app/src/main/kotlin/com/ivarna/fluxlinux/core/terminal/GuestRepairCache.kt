package com.ivarna.fluxlinux.core.terminal

import android.content.Context

/**
 * Versioned SharedPreferences cache flags for one-shot guest repairs
 * (proot-opt-01). Each key carries a version suffix — bump [ZSHRC_VERSION] /
 * [APK_DB_VERSION] whenever the corresponding repair logic changes so existing
 * installs re-run the sweep once. [clearDistro] is invoked on distro uninstall
 * so a fresh installation re-introduces the repair pass.
 */
object GuestRepairCache {

    /** SharedPreferences file shared by all guest repair flags. */
    const val PREFS = "flux_guest_repairs"

    /** Bump when GuestZshrcRepair logic changes. */
    const val ZSHRC_VERSION = 2

    /** Bump when GuestApkDbRepair logic changes. */
    const val APK_DB_VERSION = 1

    /** Key format mandated by the plan: `guest_zshrc_repair_v2_<distroId|method>`. */
    fun zshrcKey(id: String): String = "guest_zshrc_repair_v${ZSHRC_VERSION}_$id"

    /** Key format mandated by the plan: `guest_apk_db_repair_v1_<distroId|method>`. */
    fun apkDbKey(id: String): String = "guest_apk_db_repair_v${APK_DB_VERSION}_$id"

    /** Forget both flags for a distro so a reinstall re-runs the repairs. */
    fun clearDistro(ctx: Context, distroId: String) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(zshrcKey(distroId))
                .remove(apkDbKey(distroId))
                .apply()
        } catch (_: Exception) {
        }
    }
}