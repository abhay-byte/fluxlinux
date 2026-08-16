package com.ivarna.fluxlinux.core.desktop

import com.ivarna.fluxlinux.core.utils.FakePrefsContext
import com.ivarna.fluxlinux.core.utils.StateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DesktopSessionQueryTest {

    private lateinit var ctx: FakePrefsContext

    @Before
    fun setUp() {
        ctx = FakePrefsContext()
    }

    @Test
    fun idleUi_and_emptyPrefs_returnsNull() {
        val ui = DesktopLauncher.UiState(phase = DesktopLauncher.Phase.Idle, distroId = null)
        val session = DesktopSessionQuery.current(ctx, ui)
        assertNull(session)
    }

    @Test
    fun runningXfce_inUiState_returnsXfceSession() {
        val ui = DesktopLauncher.UiState(
            phase = DesktopLauncher.Phase.Running,
            distroId = "debian"
        )
        val session = DesktopSessionQuery.current(ctx, ui)
        assertNotNull(session)
        assertEquals("debian", session!!.distroId)
        assertEquals("Debian", session.distroName)
        assertEquals(DesktopSession.Type.XFCE4, session.type)
        assertEquals(DesktopSession.Phase.Running, session.phase)
    }

    @Test
    fun startingXfce_inUiState_returnsStartingSession() {
        val ui = DesktopLauncher.UiState(
            phase = DesktopLauncher.Phase.Starting,
            distroId = "alpine_chroot"
        )
        val session = DesktopSessionQuery.current(ctx, ui)
        assertNotNull(session)
        assertEquals("alpine_chroot", session!!.distroId)
        assertEquals("Alpine", session.distroName)
        assertEquals(DesktopSession.Type.XFCE4, session.type)
        assertEquals(DesktopSession.Phase.Starting, session.phase)
    }

    @Test
    fun idleUi_and_kdePref_returnsKdeSession() {
        StateManager.setGuiRunning(ctx, "debian", true)
        StateManager.setGuiRunningType(ctx, "debian", "kde")

        val ui = DesktopLauncher.UiState(phase = DesktopLauncher.Phase.Idle, distroId = null)
        val session = DesktopSessionQuery.current(ctx, ui)
        assertNotNull(session)
        assertEquals("debian", session!!.distroId)
        assertEquals("Debian", session.distroName)
        assertEquals(DesktopSession.Type.KDE, session.type)
        assertEquals(DesktopSession.Phase.Running, session.phase)
    }

    @Test
    fun runningXfceInUi_winsOverKdePrefOnOtherDistro() {
        StateManager.setGuiRunning(ctx, "alpine", true)
        StateManager.setGuiRunningType(ctx, "alpine", "kde")

        val ui = DesktopLauncher.UiState(
            phase = DesktopLauncher.Phase.Running,
            distroId = "debian"
        )
        val session = DesktopSessionQuery.current(ctx, ui)
        assertNotNull(session)
        assertEquals("debian", session!!.distroId)
        assertEquals("Debian", session.distroName)
        assertEquals(DesktopSession.Type.XFCE4, session.type)
        assertEquals(DesktopSession.Phase.Running, session.phase)
    }

    @Test
    fun staleXfcePref_withIdleUi_returnsRecoverableXfceSession() {
        StateManager.setGuiRunning(ctx, "debian", true)
        StateManager.setGuiRunningType(ctx, "debian", "")

        val ui = DesktopLauncher.UiState(phase = DesktopLauncher.Phase.Idle, distroId = null)
        val session = DesktopSessionQuery.current(ctx, ui)
        assertNotNull(session)
        assertEquals("debian", session!!.distroId)
        assertEquals("Debian", session.distroName)
        assertEquals(DesktopSession.Type.XFCE4, session.type)
        assertEquals(DesktopSession.Phase.Running, session.phase)
    }

    @Test
    fun staleXfcePref_and_liveKdePref_choosesKdeCorrectly() {
        // Alpine has stale XFCE pref, Debian has live KDE pref
        StateManager.setGuiRunning(ctx, "alpine", true)
        StateManager.setGuiRunningType(ctx, "alpine", "")
        StateManager.setGuiRunning(ctx, "debian", true)
        StateManager.setGuiRunningType(ctx, "debian", "kde")

        val ui = DesktopLauncher.UiState(phase = DesktopLauncher.Phase.Idle, distroId = null)
        val session = DesktopSessionQuery.current(ctx, ui)
        assertNotNull(session)
        assertEquals("debian", session!!.distroId)
        assertEquals("Debian", session.distroName)
        assertEquals(DesktopSession.Type.KDE, session.type)
        assertEquals(DesktopSession.Phase.Running, session.phase)
    }

    @Test
    fun evaluateStartAttempt_refusesDifferentDistro_whenSessionRunning() {
        val session = DesktopSession(
            distroId = "debian",
            distroName = "Debian",
            type = DesktopSession.Type.XFCE4,
            phase = DesktopSession.Phase.Running
        )
        val decision = DesktopLauncher.evaluateStartAttempt(
            existingSession = session,
            isSessionActive = true,
            activeDistroId = "debian",
            requestedDistroId = "alpine"
        )
        assertEquals(DesktopLauncher.StartDecision.REFUSE_DIFFERENT, decision)
    }

    @Test
    fun evaluateStartAttempt_reopensSameDistro_whenSessionRunning() {
        val session = DesktopSession(
            distroId = "debian",
            distroName = "Debian",
            type = DesktopSession.Type.XFCE4,
            phase = DesktopSession.Phase.Running
        )
        val decision = DesktopLauncher.evaluateStartAttempt(
            existingSession = session,
            isSessionActive = true,
            activeDistroId = "debian",
            requestedDistroId = "debian"
        )
        assertEquals(DesktopLauncher.StartDecision.REOPEN_SAME, decision)
    }

    @Test
    fun evaluateStartAttempt_allowsStart_whenIdle() {
        val decision = DesktopLauncher.evaluateStartAttempt(
            existingSession = null,
            isSessionActive = false,
            activeDistroId = null,
            requestedDistroId = "debian"
        )
        assertEquals(DesktopLauncher.StartDecision.ALLOW, decision)
    }

    @Test
    fun evaluateStartAttempt_refusesDifferentDistro_whenStartInFlight() {
        val decision = DesktopLauncher.evaluateStartAttempt(
            existingSession = null,
            isSessionActive = true,
            activeDistroId = "debian",
            requestedDistroId = "alpine"
        )
        assertEquals(DesktopLauncher.StartDecision.REFUSE_DIFFERENT, decision)
    }
}
