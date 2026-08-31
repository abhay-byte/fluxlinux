package com.ivarna.fluxlinux.core.install

import android.app.PendingIntent
import android.content.Context
import android.content.IntentSender
import android.util.Log
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.google.android.gms.tasks.Task
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal data class FeatureInstallState(
    val sessionId: Int,
    val status: Int,
    val errorCode: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val moduleNames: List<String>,
    val confirmationSender: IntentSender?
)

internal interface PlayFeatureDeliveryClient {
    val installedModules: Set<String>
    fun sessionStates(): List<FeatureInstallState>
    fun startInstall(moduleName: String): Int
    fun listen(listener: (FeatureInstallState) -> Unit): Closeable
    fun cancelInstall(sessionId: Int): Boolean
}

internal class SplitInstallDeliveryClient(context: Context) : PlayFeatureDeliveryClient {
    private val manager: SplitInstallManager =
        SplitInstallManagerFactory.create(context.applicationContext)

    override val installedModules: Set<String>
        get() = manager.installedModules

    override fun sessionStates(): List<FeatureInstallState> =
        await(manager.sessionStates).map(::mapState)

    override fun startInstall(moduleName: String): Int =
        await(
            manager.startInstall(
                SplitInstallRequest.newBuilder().addModule(moduleName).build()
            )
        )

    override fun listen(listener: (FeatureInstallState) -> Unit): Closeable {
        val splitListener = SplitInstallStateUpdatedListener { state ->
            listener(mapState(state))
        }
        manager.registerListener(splitListener)
        return Closeable { manager.unregisterListener(splitListener) }
    }

    override fun cancelInstall(sessionId: Int): Boolean =
        runCatching { await(manager.cancelInstall(sessionId)); true }.getOrDefault(false)

    private fun mapState(state: SplitInstallSessionState): FeatureInstallState =
        FeatureInstallState(
            sessionId = state.sessionId(),
            status = state.status(),
            errorCode = state.errorCode(),
            bytesDownloaded = state.bytesDownloaded(),
            totalBytes = state.totalBytesToDownload(),
            moduleNames = state.moduleNames(),
            // resolutionIntent() is deprecated in favor of the manager's
            // Activity API, but it is the only form that can be handed to the
            // common ActivityResult bridge without leaking Play types into it.
            confirmationSender = state.resolutionIntent()?.intentSender
        )

    private companion object {
        fun <T> await(task: Task<T>): T {
            val latch = CountDownLatch(1)
            var value: T? = null
            var error: Exception? = null
            task.addOnCompleteListener { completed ->
                if (completed.isSuccessful) {
                    value = completed.result
                } else {
                    error = completed.exception ?: IllegalStateException("Play task failed")
                }
                latch.countDown()
            }
            if (!latch.await(60L, TimeUnit.SECONDS)) {
                throw IllegalStateException("Timed out waiting for Play Feature Delivery")
            }
            error?.let { throw it }
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
    }
}

internal class PlayFeatureDeliveryCoordinator(
    private val client: PlayFeatureDeliveryClient,
    private val confirmation: PlayFeatureConfirmation = PlayFeatureConfirmation
) {
    fun ensureInstalled(
        moduleName: String,
        isCancelled: () -> Boolean,
        onProgress: (PayloadProgress) -> Unit
    ): String? {
        if (isCancelled()) return "Play Feature Delivery request cancelled"
        if (moduleName in client.installedModules) {
            onProgress(PayloadProgress(0L, 0L, "Play feature already installed: $moduleName"))
            return null
        }

        val existing = runCatching {
            client.sessionStates().firstOrNull { state ->
                moduleName in state.moduleNames && !isTerminal(state.status)
            }
        }.getOrElse {
            return "Unable to inspect Play Feature Delivery sessions: ${it.message}"
        }

        val closeable: Closeable
        val stateLock = Any()
        var latest = existing
        val stateChanged = Object()
        try {
            closeable = client.listen { state ->
                if (moduleName !in state.moduleNames) return@listen
                synchronized(stateLock) { latest = state }
                synchronized(stateChanged) { stateChanged.notifyAll() }
                onProgress(PayloadProgress(state.bytesDownloaded, state.totalBytes, statusLabel(state.status)))
            }
            closeable.use {
                val sessionId = existing?.sessionId ?: runCatching {
                    client.startInstall(moduleName)
                }.getOrElse {
                    return "Unable to request Play feature $moduleName: ${it.message}"
                }
                if (existing == null) {
                    synchronized(stateLock) {
                        // The Play listener may have delivered a newer state
                        // synchronously from startInstall; never overwrite it.
                        if (latest == null) {
                            latest = FeatureInstallState(
                                sessionId = sessionId,
                                status = SplitInstallSessionStatus.PENDING,
                                errorCode = 0,
                                bytesDownloaded = 0L,
                                totalBytes = 0L,
                                moduleNames = listOf(moduleName),
                                confirmationSender = null
                            )
                        }
                    }
                }

                var confirmationShown = false
                while (true) {
                    if (isCancelled()) {
                        client.cancelInstall(sessionId)
                        return "Play feature $moduleName installation cancelled"
                    }
                    val current = synchronized(stateLock) { latest }
                    if (current == null) {
                        waitForState(stateChanged, 250L)
                        continue
                    }
                    onProgress(PayloadProgress(current.bytesDownloaded, current.totalBytes, statusLabel(current.status)))
                    when (current.status) {
                        SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                            if (confirmationShown) {
                                waitForState(stateChanged, 250L)
                                continue
                            }
                            val sender = current.confirmationSender
                                ?: return "Play requires user confirmation but supplied no resolution"
                            confirmationShown = true
                            if (!awaitConfirmation(sender, isCancelled)) {
                                client.cancelInstall(sessionId)
                                return if (isCancelled()) {
                                    "Play feature $moduleName installation cancelled"
                                } else {
                                    "User declined Play feature $moduleName"
                                }
                            }
                        }
                        SplitInstallSessionStatus.INSTALLED -> return null
                        SplitInstallSessionStatus.FAILED ->
                            return "Play feature $moduleName failed (error ${current.errorCode})"
                        SplitInstallSessionStatus.CANCELED ->
                            return "Play feature $moduleName installation cancelled"
                    }
                    waitForState(stateChanged, 250L)
                }
            }
        } catch (e: Exception) {
            Log.e("PlayFeatureDelivery", "Feature request failed: $moduleName", e)
            return "Play Feature Delivery unavailable for $moduleName: ${e.message}"
        }
    }

    private fun awaitConfirmation(sender: IntentSender, isCancelled: () -> Boolean): Boolean {
        val result = CountDownLatch(1)
        var accepted = false
        if (!confirmation.request(sender) {
                accepted = it
                result.countDown()
            }
        ) return false
        while (!result.await(250L, TimeUnit.MILLISECONDS)) {
            if (isCancelled()) return false
        }
        return accepted
    }

    private fun waitForState(signal: Object, timeoutMs: Long) {
        synchronized(signal) {
            try {
                signal.wait(timeoutMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun isTerminal(status: Int): Boolean = when (status) {
        SplitInstallSessionStatus.INSTALLED,
        SplitInstallSessionStatus.FAILED,
        SplitInstallSessionStatus.CANCELED -> true
        else -> false
    }

    private fun statusLabel(status: Int): String = when (status) {
        SplitInstallSessionStatus.PENDING -> "Play feature pending"
        SplitInstallSessionStatus.DOWNLOADING -> "Downloading Play feature"
        SplitInstallSessionStatus.DOWNLOADED -> "Play feature downloaded"
        SplitInstallSessionStatus.INSTALLING -> "Installing Play feature"
        SplitInstallSessionStatus.INSTALLED -> "Play feature installed"
        SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> "Waiting for Play confirmation"
        SplitInstallSessionStatus.CANCELING,
        SplitInstallSessionStatus.CANCELED -> "Cancelling Play feature"
        SplitInstallSessionStatus.FAILED -> "Play feature failed"
        else -> "Preparing Play feature"
    }
}

internal object PlayFeatureDelivery {
    fun ensureInstalled(
        ctx: Context,
        moduleName: String,
        isCancelled: () -> Boolean,
        onProgress: (PayloadProgress) -> Unit
    ): String? = PlayFeatureDeliveryCoordinator(
        SplitInstallDeliveryClient(ctx)
    ).ensureInstalled(moduleName, isCancelled, onProgress)
}
