package com.ivarna.fluxlinux

import android.app.Application
import android.content.Context
import com.google.android.play.core.splitcompat.SplitCompat
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths

class FluxLinuxApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        SplitCompat.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        try {
            TermuxHostPaths.writeHostEnvFile(filesDir, this)
        } catch (_: Exception) {}
    }
}
