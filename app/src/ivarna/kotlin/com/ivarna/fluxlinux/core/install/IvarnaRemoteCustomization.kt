package com.ivarna.fluxlinux.core.install

import android.content.Context
import kotlin.jvm.functions.Function1

/** Ivarna-only implementation; this class is not compiled into zenithblue. */
object IvarnaRemoteCustomization {
    @JvmStatic
    fun install(
        context: Context,
        prootName: String,
        onLog: Function1<String, Unit>
    ): Boolean = ProotZshBootstrap.install(context, prootName) { onLog.invoke(it) }
}
