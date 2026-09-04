package com.ivarna.fluxlinux.core.install

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Constructor
import java.util.concurrent.CopyOnWriteArrayList

class PlayFeatureDeliveryTest {

    private class TestSplitInstallManager : SplitInstallManager {
        val listeners = CopyOnWriteArrayList<SplitInstallStateUpdatedListener>()
        var triggerListenerBeforeSuccess: ((SplitInstallStateUpdatedListener, Int) -> Unit)? = null
        var installed = mutableSetOf<String>()

        override fun registerListener(listener: SplitInstallStateUpdatedListener) {
            listeners.add(listener)
        }

        override fun unregisterListener(listener: SplitInstallStateUpdatedListener) {
            listeners.remove(listener)
        }

        override fun startInstall(request: SplitInstallRequest): Task<Int> {
            val sessionId = 42
            triggerListenerBeforeSuccess?.let { trigger ->
                listeners.forEach { listener ->
                    trigger(listener, sessionId)
                }
            }
            return Tasks.forResult(sessionId)
        }

        override fun cancelInstall(sessionId: Int): Task<Void> = Tasks.forResult(null)
        override fun getInstalledModules(): Set<String> = installed
        override fun getSessionState(sessionId: Int): Task<SplitInstallSessionState> = Tasks.forResult(null)
        override fun getSessionStates(): Task<List<SplitInstallSessionState>> = Tasks.forResult(emptyList())
        override fun deferredInstall(modules: List<String>): Task<Void> = Tasks.forResult(null)
        override fun deferredUninstall(modules: List<String>): Task<Void> = Tasks.forResult(null)
        override fun deferredLanguageInstall(languages: List<java.util.Locale>): Task<Void> = Tasks.forResult(null)
        override fun deferredLanguageUninstall(languages: List<java.util.Locale>): Task<Void> = Tasks.forResult(null)
        override fun getInstalledLanguages(): Set<String> = emptySet()
        override fun startConfirmationDialogForResult(
            sessionState: SplitInstallSessionState,
            activity: android.app.Activity,
            requestCode: Int
        ): Boolean = true
        override fun startConfirmationDialogForResult(
            sessionState: SplitInstallSessionState,
            starter: com.google.android.play.core.common.IntentSenderForResultStarter,
            requestCode: Int
        ): Boolean = true
        override fun startConfirmationDialogForResult(
            sessionState: SplitInstallSessionState,
            launcher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>
        ): Boolean = true
        override fun zza(listener: SplitInstallStateUpdatedListener) {}
        override fun zzb(listener: SplitInstallStateUpdatedListener) {}
    }

    private fun createConfirmationSessionState(sessionId: Int, moduleName: String): SplitInstallSessionState {
        val zzaClass = Class.forName("com.google.android.play.core.splitinstall.zza")
        val ctor = zzaClass.declaredConstructors.first()
        ctor.isAccessible = true
        return ctor.newInstance(
            sessionId,
            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION,
            0,
            0L,
            0L,
            listOf(moduleName),
            emptyList<String>(),
            null,
            null
        ) as SplitInstallSessionState
    }

    @Test
    fun `listener receives state before startInstall success callback handles module correctly`() = runBlocking {
        val fakeManager = TestSplitInstallManager()
        val module = "distro_alpine"

        fakeManager.triggerListenerBeforeSuccess = { listener, realSessionId ->
            val state = SplitInstallSessionState.create(
                realSessionId,
                SplitInstallSessionStatus.DOWNLOADING,
                0,
                500L,
                1000L,
                listOf(module),
                emptyList()
            )
            listener.onStateUpdate(state)
        }

        val delivery = PlayFeatureDelivery(fakeManager)
        val progress = delivery.requestModule(module).first()

        assertTrue(progress is SplitInstallProgress.Downloading)
        val downloading = progress as SplitInstallProgress.Downloading
        assertEquals(500L, downloading.bytesDownloaded)
        assertEquals(1000L, downloading.totalBytesToDownload)
    }

    @Test
    fun `handles REQUIRES_USER_CONFIRMATION status emission`() = runBlocking {
        val fakeManager = TestSplitInstallManager()
        val module = "distro_alpine"

        fakeManager.triggerListenerBeforeSuccess = { listener, realSessionId ->
            val state = createConfirmationSessionState(realSessionId, module)
            listener.onStateUpdate(state)
        }

        val delivery = PlayFeatureDelivery(fakeManager)
        val progress = delivery.requestModule(module).first()

        assertTrue(progress is SplitInstallProgress.RequiresUserConfirmation)
        val confirmation = progress as SplitInstallProgress.RequiresUserConfirmation
        assertEquals(42, confirmation.sessionId)
        assertEquals(fakeManager, confirmation.splitInstallManager)
    }
}
