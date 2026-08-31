package com.ivarna.fluxlinux.core.install

import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayFeatureDeliveryTest {

    @Test
    fun coordinator_reentersExistingSessionAfterProcessRestart() {
        val client = FakeClient(
            existing = listOf(
                state(
                    sessionId = 41,
                    status = SplitInstallSessionStatus.DOWNLOADING,
                    moduleNames = listOf("distro_alpine")
                )
            )
        )
        val progress = CopyOnWriteArrayList<PayloadProgress>()
        val worker = Thread {
            Thread.sleep(20L)
            client.emit(state(41, SplitInstallSessionStatus.INSTALLED, listOf("distro_alpine")))
        }
        worker.start()

        val result = PlayFeatureDeliveryCoordinator(client).ensureInstalled(
            moduleName = "distro_alpine",
            isCancelled = { false },
            onProgress = progress::add
        )
        worker.join(1_000L)

        assertEquals(null, result)
        assertEquals(0, client.startCalls)
        assertTrue(progress.any { it.phase.contains("Downloading") })
    }

    @Test
    fun coordinator_cancelsActiveSessionWhenCallerCancels() {
        val client = FakeClient()
        var checks = 0

        val result = PlayFeatureDeliveryCoordinator(client).ensureInstalled(
            moduleName = "runtime_host",
            isCancelled = { checks++ > 0 },
            onProgress = {}
        )

        assertTrue(result.orEmpty().contains("cancelled"))
        assertEquals(listOf(7), client.cancelledSessions)
    }

    private class FakeClient(
        private val existing: List<FeatureInstallState> = emptyList()
    ) : PlayFeatureDeliveryClient {
        private var callback: ((FeatureInstallState) -> Unit)? = null
        var startCalls = 0
            private set
        val cancelledSessions = mutableListOf<Int>()

        override val installedModules: Set<String> = emptySet()

        override fun sessionStates(): List<FeatureInstallState> = existing

        override fun startInstall(moduleName: String): Int {
            startCalls++
            return 7
        }

        override fun listen(listener: (FeatureInstallState) -> Unit): Closeable {
            callback = listener
            return Closeable { callback = null }
        }

        override fun cancelInstall(sessionId: Int): Boolean {
            cancelledSessions += sessionId
            return true
        }

        fun emit(state: FeatureInstallState) {
            callback?.invoke(state)
        }
    }

    private fun state(
        sessionId: Int,
        status: Int,
        moduleNames: List<String>
    ) = FeatureInstallState(
        sessionId = sessionId,
        status = status,
        errorCode = 0,
        bytesDownloaded = 1L,
        totalBytes = 2L,
        moduleNames = moduleNames,
        confirmationSender = null
    )
}
