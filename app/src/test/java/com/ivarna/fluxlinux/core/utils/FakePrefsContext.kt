package com.ivarna.fluxlinux.core.utils

import android.content.Context
import android.content.SharedPreferences
import com.ivarna.fluxlinux.core.terminal.FakeContext
import java.io.File

/**
 * Minimal Context for [TerminalPreferences] JVM tests: in-memory
 * SharedPreferences (no Robolectric). Extends the builders' [FakeContext].
 */
class FakePrefsContext(
    filesDir: File = File("."),
    nativeLibDir: String = "/fake/jni"
) : FakeContext(filesDir, nativeLibDir) {

    override val prefs = InMemoryPrefs()

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
}

/** Tiny in-memory [SharedPreferences] — enough for TerminalPreferences tests. */
class InMemoryPrefs : SharedPreferences {

    private val map = HashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(map)

    override fun getString(key: String?, defValue: String?): String? =
        map[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (map[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        map[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = InMemoryEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class InMemoryEditor : SharedPreferences.Editor {
        private val staged = HashMap<String, Any?>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            staged[key!!] = value
            return this
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor {
            staged[key!!] = values?.toSet()
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            staged[key!!] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            staged[key!!] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            staged[key!!] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            staged[key!!] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            staged[key!!] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            if (clearAll) map.clear()
            for ((k, v) in staged) {
                if (v == null) map.remove(k) else map[k] = v
            }
            staged.clear()
            clearAll = false
        }
    }
}
