package com.ivarna.fluxlinux.core.install

import android.content.Context
import com.ivarna.fluxlinux.BuildConfig
import kotlin.jvm.functions.Function1

/**
 * Flavor boundary for optional remote customization.
 *
 * The Play flavor has no implementation class in its source set. The
 * reflection is used only by the ivarna build so the remote Git bootstrap is
 * absent from the Play DEX and cannot be reached during Play onboarding.
 */
internal object FlavorCustomizationBridge {
    fun install(
        context: Context,
        prootName: String,
        onLog: (String) -> Unit = {}
    ): Boolean {
        if (BuildConfig.FLAVOR != "ivarna") return false
        return runCatching {
            val implementation = Class.forName(
                "com.ivarna.fluxlinux.core.install.IvarnaRemoteCustomization"
            )
            val method = implementation.getMethod(
                "install", Context::class.java, String::class.java, Function1::class.java
            )
            method.invoke(
                null,
                context,
                prootName,
                object : Function1<String, Unit> {
                    override fun invoke(value: String) {
                        onLog(value)
                    }
                }
            ) as? Boolean ?: false
        }.getOrElse {
            onLog("Ivarna-only remote customization unavailable")
            false
        }
    }
}
