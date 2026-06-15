package com.ivarna.fluxlinux.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ivarna.fluxlinux.MainActivity
import com.ivarna.fluxlinux.R
import com.ivarna.fluxlinux.core.utils.InstallationQueueManager
import com.ivarna.fluxlinux.core.utils.LocalInstallServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service that hosts [LocalInstallServer] for the duration of an
 * installation. Android is free to kill background processes; a foreground
 * service with a persistent notification gets a much higher priority and
 * survives the activity being backgrounded or trimmed.
 *
 * Lifecycle:
 *  - Started by MainActivity when an install begins (both onInstallStart and
 *    onInstallComponent paths).
 *  - Observes [InstallationQueueManager.installState]. When `isInstalling`
 *    flips false (queue drained or user cancelled), the service stops itself
 *    and dismisses the notification.
 *  - If the activity is destroyed mid-install, the service keeps the HTTP
 *    bridge alive so Termux's `curl localhost:PORT` still succeeds.
 *
 * Port discovery: [activePort] is a process-wide StateFlow. The activity
 * awaits on it after [start] returns to build the curl command for Termux.
 */
class InstallServerService : Service() {

    companion object {
        const val ACTION_START = "com.ivarna.fluxlinux.START_INSTALL_SERVER"
        const val ACTION_STOP = "com.ivarna.fluxlinux.STOP_INSTALL_SERVER"
        const val EXTRA_SCRIPT = "extra_script"

        private const val CHANNEL_ID = "fluxlinux_install_server"
        private const val NOTIF_ID = 4711
        private val FOREGROUND_TYPE_DATA_SYNC =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else 0

        // Port the bound LocalInstallServer is listening on, or null if none.
        // Process-scoped: activity and service share the same process, so
        // the activity can await the value here.
        private val _activePort = MutableStateFlow<Int?>(null)
        val activePort: StateFlow<Int?> = _activePort.asStateFlow()

        /**
         * Start the foreground service. If [script] is non-empty, the service
         * will also start a [LocalInstallServer] hosting it.
         *
         * @return a snapshot of [activePort]; callers should still
         *   [awaitPort] if they need a non-null value.
         */
        fun start(context: Context, script: String = ""): Int? {
            val intent = Intent(context, InstallServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SCRIPT, script)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
            return _activePort.value
        }

        /**
         * Suspend until [activePort] is non-null or [timeoutMs] elapses.
         * Returns the bound port, or null on timeout.
         */
        suspend fun awaitPort(timeoutMs: Long = 5_000L): Int? {
            val current = _activePort.value
            if (current != null) return current
            return withTimeoutOrNull(timeoutMs) {
                _activePort.first { it != null }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, InstallServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + supervisor)
    private var server: LocalInstallServer? = null
    private var stateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServerAndSelf()
                return START_NOT_STICKY
            }
            else -> {
                val script = intent?.getStringExtra(EXTRA_SCRIPT) ?: ""
                startInForeground()
                if (script.isNotEmpty()) startLocalServer(script)
                observeInstallState()
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification("Preparing install…", "Serving install script to Termux")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, FOREGROUND_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startLocalServer(script: String) {
        // If a server is already running, replace its script (handles
        // multiple startService calls during the same install).
        val existing = server
        if (existing != null) {
            existing.stop()
            server = null
            _activePort.value = null
        }
        val local = LocalInstallServer()
        server = local
        scope.launch(Dispatchers.IO) {
            try {
                val port = local.start(script)
                _activePort.value = port
            } catch (e: Exception) {
                e.printStackTrace()
                _activePort.value = null
            }
        }
    }

    private fun observeInstallState() {
        stateJob?.cancel()
        stateJob = scope.launch {
            InstallationQueueManager.installState.collectLatest { state ->
                val title = if (state.isInstalling) {
                    if (state.currentTaskName.isNotEmpty())
                        "Installing: ${state.currentTaskName}"
                    else
                        "Install in progress"
                } else {
                    "Install finished"
                }
                val body = when {
                    state.cancelledByUser -> "Cancelled by user"
                    state.isInstalling -> "Progress ${state.progressCurrent}/${state.progressTotal}"
                    else -> "Done"
                }
                updateNotification(title, body)
                if (!state.isInstalling) {
                    stopServerAndSelf()
                }
            }
        }
    }

    private fun updateNotification(title: String, body: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(title, body))
    }

    private fun buildNotification(title: String, body: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Install server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the local install script server alive during an install"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun stopServerAndSelf() {
        try { server?.stop() } catch (_: Exception) {}
        server = null
        _activePort.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { server?.stop() } catch (_: Exception) {}
        server = null
        _activePort.value = null
        stateJob?.cancel()
        scope.cancel()
    }
}
