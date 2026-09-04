package com.ivarna.fluxlinux.core.install

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class SplitInstallProgress {
    data class Downloading(val bytesDownloaded: Long, val totalBytesToDownload: Long) : SplitInstallProgress()
    data object Installing : SplitInstallProgress()
    data object Installed : SplitInstallProgress()
    data class RequiresUserConfirmation(
        val sessionId: Int,
        val splitInstallManager: SplitInstallManager,
        val sessionState: com.google.android.play.core.splitinstall.SplitInstallSessionState
    ) : SplitInstallProgress()
    data class Failed(val errorCode: Int, val exception: Throwable?) : SplitInstallProgress()
}

open class PlayFeatureDelivery(
    private val splitInstallManager: SplitInstallManager? = null
) {
    companion object {
        fun create(context: Context): PlayFeatureDelivery =
            PlayFeatureDelivery(SplitInstallManagerFactory.create(context))
    }

    open fun isInstalled(moduleName: String): Boolean =
        splitInstallManager?.installedModules?.contains(moduleName) ?: false

    open fun requestModule(moduleName: String): Flow<SplitInstallProgress> = callbackFlow {
        val manager = splitInstallManager ?: run {
            trySend(SplitInstallProgress.Failed(-1, IllegalStateException("SplitInstallManager is null")))
            close()
            return@callbackFlow
        }
        if (isInstalled(moduleName)) {
            trySend(SplitInstallProgress.Installed)
            close()
            return@callbackFlow
        }

        var sessionId = 0

        val listener = SplitInstallStateUpdatedListener { state ->
            val matchesSession = if (sessionId != 0) {
                state.sessionId() == sessionId
            } else {
                state.moduleNames().contains(moduleName)
            }

            if (matchesSession) {
                if (sessionId == 0 && state.sessionId() != 0) {
                    sessionId = state.sessionId()
                }
                when (state.status()) {
                    SplitInstallSessionStatus.DOWNLOADING -> {
                        trySend(
                            SplitInstallProgress.Downloading(
                                bytesDownloaded = state.bytesDownloaded(),
                                totalBytesToDownload = state.totalBytesToDownload()
                            )
                        )
                    }
                    SplitInstallSessionStatus.INSTALLING -> {
                        trySend(SplitInstallProgress.Installing)
                    }
                    SplitInstallSessionStatus.INSTALLED -> {
                        trySend(SplitInstallProgress.Installed)
                        close()
                    }
                    SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                        trySend(
                            SplitInstallProgress.RequiresUserConfirmation(
                                sessionId = state.sessionId(),
                                splitInstallManager = manager,
                                sessionState = state
                            )
                        )
                    }
                    SplitInstallSessionStatus.FAILED -> {
                        trySend(
                            SplitInstallProgress.Failed(
                                errorCode = state.errorCode(),
                                exception = null
                            )
                        )
                        close(IllegalStateException("Module install failed with errorCode: ${state.errorCode()}"))
                    }
                    SplitInstallSessionStatus.CANCELED -> {
                        trySend(
                            SplitInstallProgress.Failed(
                                errorCode = state.errorCode(),
                                exception = null
                            )
                        )
                        close(IllegalStateException("Module install cancelled"))
                    }
                    else -> {}
                }
            }
        }

        manager.registerListener(listener)

        val request = SplitInstallRequest.newBuilder()
            .addModule(moduleName)
            .build()

        manager.startInstall(request)
            .addOnSuccessListener { id ->
                sessionId = id
            }
            .addOnFailureListener { exception ->
                trySend(SplitInstallProgress.Failed(errorCode = -1, exception = exception))
                close(exception)
            }

        awaitClose {
            manager.unregisterListener(listener)
        }
    }
}
