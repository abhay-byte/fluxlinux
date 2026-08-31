package com.ivarna.fluxlinux.core.install

import android.content.IntentSender
import android.os.Handler
import android.os.Looper

/**
 * Small flavor-neutral bridge for the Play confirmation dialog. MainActivity
 * owns the Activity Result launcher; the Play provider can therefore wait for
 * a user decision even though installation work runs on a worker thread.
 */
object PlayFeatureConfirmation {
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private var nextId = 0L
    private var registration: Pair<Registration, (IntentSender) -> Unit>? = null
    private var pending: ((Boolean) -> Unit)? = null

    class Registration internal constructor(internal val id: Long)

    fun register(launch: (IntentSender) -> Unit): Registration {
        val oldPending: ((Boolean) -> Unit)?
        val newRegistration: Registration
        synchronized(lock) {
            oldPending = pending
            pending = null
            newRegistration = Registration(++nextId)
            registration = newRegistration to launch
        }
        oldPending?.invoke(false)
        return newRegistration
    }

    fun unregister(token: Registration) {
        val oldPending: ((Boolean) -> Unit)?
        synchronized(lock) {
            if (registration?.first?.id != token.id) return
            oldPending = pending
            pending = null
            registration = null
        }
        oldPending?.invoke(false)
    }

    /** Returns false when no foreground Activity can present the dialog. */
    fun request(sender: IntentSender, result: (Boolean) -> Unit): Boolean {
        val launch: ((IntentSender) -> Unit)
        synchronized(lock) {
            val current = registration ?: return false
            if (pending != null) return false
            pending = result
            launch = current.second
        }
        main.post {
            try {
                launch(sender)
            } catch (_: Exception) {
                complete(false)
            }
        }
        return true
    }

    fun complete(accepted: Boolean) {
        val callback: ((Boolean) -> Unit)?
        synchronized(lock) {
            callback = pending
            pending = null
        }
        callback?.invoke(accepted)
    }

}
