package com.ivarna.fluxlinux.core.chroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ChrootKillCoordinatorTest {

    @Test
    fun singleSession_runsToCompletion_lifecycleTransitionsCleanly() {
        val coordinator = ChrootKillCoordinator()
        assertFalse("Initially not busy", coordinator.isBusy)
        assertEquals(ChrootKillCoordinator.State.IDLE, coordinator.state)

        val session = coordinator.startSession()
        assertNotNull("Session must be started", session)
        assertTrue("Coordinator must be busy", coordinator.isBusy)
        assertEquals(ChrootKillCoordinator.State.RUNNING, coordinator.state)
        assertFalse("Cancel flag must be false initially", session!!.isCancelled)

        coordinator.endSession(session)
        assertFalse("Coordinator must return to idle", coordinator.isBusy)
        assertEquals(ChrootKillCoordinator.State.IDLE, coordinator.state)
    }

    @Test
    fun cancelDuringExecution_keepsBusyTrueUntilSessionEnds_thenTransitionsToIdle() {
        val coordinator = ChrootKillCoordinator()
        val session = coordinator.startSession()
        assertNotNull(session)

        // Request cancel
        val cancelAccepted = coordinator.requestCancel()
        assertTrue("Cancel request must succeed for active session", cancelAccepted)
        assertTrue("Session cancel flag must be true", session!!.isCancelled)
        assertEquals(ChrootKillCoordinator.State.CANCEL_REQUESTED, coordinator.state)

        // CRITICAL INVARIANT: busy must remain true while blocking worker is still executing!
        assertTrue("Busy must remain true during CANCEL_REQUESTED state", coordinator.isBusy)

        // Second kill must NOT be able to start during cancellation
        val secondSession = coordinator.startSession()
        assertNull("Second kill session must be rejected while cancelling", secondSession)

        // Only after worker returns and calls endSession does state return to IDLE
        coordinator.endSession(session)
        assertFalse("Busy must be false after session ends", coordinator.isBusy)
        assertEquals(ChrootKillCoordinator.State.IDLE, coordinator.state)
    }

    @Test
    fun secondKill_cannotStartWhileFirstIsActive() {
        val coordinator = ChrootKillCoordinator()
        val session1 = coordinator.startSession()
        assertNotNull(session1)

        val session2 = coordinator.startSession()
        assertNull("Second session must be rejected while first is running", session2)

        coordinator.endSession(session1!!)

        val session3 = coordinator.startSession()
        assertNotNull("Session can start after previous session ends", session3)
        coordinator.endSession(session3!!)
    }

    @Test
    fun newSessionAfterCompletion_getsFreshCancelFlag() {
        val coordinator = ChrootKillCoordinator()
        val session1 = coordinator.startSession()!!
        coordinator.requestCancel()
        assertTrue("Session 1 cancelled", session1.isCancelled)
        coordinator.endSession(session1)

        val session2 = coordinator.startSession()!!
        assertFalse("Session 2 must have fresh cancel flag", session2.isCancelled)
        assertEquals(ChrootKillCoordinator.State.RUNNING, coordinator.state)
        coordinator.endSession(session2)
    }

    @Test
    fun cancellationStopsFuturePaths_inMultiPathExecution_withBlockingSeam() {
        val coordinator = ChrootKillCoordinator()
        val session = coordinator.startSession()!!

        val paths = listOf(
            "/data/local/tmp/chrootDebian13",
            "/data/local/tmp/chrootAlpine",
            "/data/local/tmp/chrootFedora"
        )

        val executedPaths = mutableListOf<String>()
        val path1Started = CountDownLatch(1)
        val cancelIssued = CountDownLatch(1)
        val busyDuringBlockingExecution = AtomicBoolean(false)

        val workerThread = Thread {
            val (results, wasCancelled) = ChrootSettingsModel.runMultiPathKill(
                validPaths = paths,
                isCancelled = { session.isCancelled },
                killSingle = { path ->
                    executedPaths.add(path)
                    if (path == paths[0]) {
                        path1Started.countDown()
                        // Wait for cancel to be requested mid-execution of path 1
                        cancelIssued.await(2, TimeUnit.SECONDS)
                        busyDuringBlockingExecution.set(coordinator.isBusy)
                    }
                    ChrootProcessManager.KillResult(
                        killed = 1,
                        failed = 0,
                        remaining = emptyList(),
                        verifiedClean = true,
                        raw = "",
                        rootOk = true
                    )
                }
            )
            coordinator.endSession(session)
        }

        workerThread.start()

        // Wait until path 1 starts executing inside blocking call
        assertTrue(path1Started.await(2, TimeUnit.SECONDS))
        assertTrue("Coordinator must be busy while path 1 is running", coordinator.isBusy)

        // User hits Cancel while path 1 is executing
        coordinator.requestCancel()
        assertTrue("Coordinator must remain busy immediately after cancel", coordinator.isBusy)
        cancelIssued.countDown()

        workerThread.join(3000)

        // Post-execution checks:
        assertTrue("Coordinator was busy throughout path 1 blocking execution", busyDuringBlockingExecution.get())
        assertEquals("Only path 1 executed; path 2 and 3 were never scheduled", 1, executedPaths.size)
        assertEquals(paths[0], executedPaths[0])
        assertFalse("Coordinator must be idle after worker ends", coordinator.isBusy)
        assertEquals(ChrootKillCoordinator.State.IDLE, coordinator.state)
    }
}
