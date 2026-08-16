package com.ivarna.fluxlinux.core.chroot

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe coordinator for chroot kill operations.
 * Enforces:
 * 1. Exactly one active kill operation at a time for a detail screen.
 * 2. Generation/token-scoped cancellation that cannot be reset by subsequent calls.
 * 3. [isBusy] remains true while underlying worker is executing, even after cancel is requested.
 * 4. Second kill cannot start while current kill is active or cancelling.
 */
class ChrootKillCoordinator {

    enum class State {
        IDLE,
        RUNNING,
        CANCEL_REQUESTED
    }

    class Session(val generation: Long) {
        private val cancelFlag = AtomicBoolean(false)

        val isCancelled: Boolean
            get() = cancelFlag.get()

        fun cancel() {
            cancelFlag.set(true)
        }
    }

    private val generationSeq = AtomicLong(0)
    private val activeSession = AtomicReference<Session?>(null)
    private val stateRef = AtomicReference(State.IDLE)

    val isBusy: Boolean
        get() = stateRef.get() != State.IDLE

    val state: State
        get() = stateRef.get()

    /**
     * Attempts to start a new kill session. Returns null if already busy or cancelling.
     */
    fun startSession(): Session? {
        if (!stateRef.compareAndSet(State.IDLE, State.RUNNING)) {
            return null
        }
        val gen = generationSeq.incrementAndGet()
        val session = Session(gen)
        activeSession.set(session)
        return session
    }

    /**
     * Requests cancellation of the currently active session, if any.
     * Note: State transitions to CANCEL_REQUESTED, but [isBusy] remains true until [endSession] is called.
     */
    fun requestCancel(): Boolean {
        val session = activeSession.get() ?: return false
        session.cancel()
        stateRef.compareAndSet(State.RUNNING, State.CANCEL_REQUESTED)
        return true
    }

    /**
     * Marks the given session as completed and transitions state back to IDLE.
     */
    fun endSession(session: Session) {
        if (activeSession.compareAndSet(session, null)) {
            stateRef.set(State.IDLE)
        }
    }
}
