package com.ivarna.fluxlinux.core.terminal

import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import java.io.File

/**
 * Minimal Context stub for JVM unit tests of builders.
 * Wraps a null base; only the members the builders touch are wired.
 */
class FakeContext(
    private val filesDir: File,
    private val nativeLibDir: String
) : ContextWrapper(null) {

    private val appInfo = ApplicationInfo().apply {
        nativeLibraryDir = nativeLibDir
        sourceDir = "/data/app/fake/fake.apk"
    }

    override fun getApplicationInfo(): ApplicationInfo = appInfo
    override fun getApplicationContext() = this
    override fun getFilesDir(): File = filesDir
    override fun getCacheDir(): File = File(filesDir, "cache").also { it.mkdirs() }
    override fun getAssets(): android.content.res.AssetManager =
        throw UnsupportedOperationException("assets not stubbed")
}
