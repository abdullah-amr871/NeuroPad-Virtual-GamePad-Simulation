package io.github.abdullahamr871.neuropad.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.abdullahamr871.neuropad.R
import io.github.abdullahamr871.neuropad.ui.MainActivity
import io.github.abdullahamr871.neuropad.usb.UsbGamepadManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Foreground service that keeps the bridge alive while a game is on screen.
 * All state the UI needs is published through [BridgeState].
 */
class GamepadBridgeService : Service() {

    private lateinit var settings: Settings
    private val worker: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "NeuroPad-Bridge") }
    private var wakeLock: PowerManager.WakeLock? = null
    private var retries = 0

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                settings.bridgeEnabled = false
                worker.execute { stopSession(BridgeStatus.Stopped) }
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                session?.reloadSettings()
                if (session == null) stopSelf()
                return START_STICKY
            }
            ACTION_TEST_RUMBLE -> {
                val s = session
                if (s != null) {
                    worker.execute {
                        s.testRumble(
                            intent.getIntExtra(EXTRA_LEFT, 255),
                            intent.getIntExtra(EXTRA_RIGHT, 255),
                            intent.getLongExtra(EXTRA_DURATION, 400),
                        )
                    }
                } else {
                    stopSelf()
                }
                return START_STICKY
            }
            // ACTION_START, or a null intent when Android recreates the sticky service.
            else -> {
                if (intent == null && !settings.bridgeEnabled) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                settings.bridgeEnabled = true
                goForeground("Starting")
                retries = 0
                worker.execute { startSession() }
            }
        }
        return START_STICKY
    }

    private fun startSession() {
        if (session != null) return
        if (UsbGamepadManager.findCompatible(this) == null) {
            // Also covers starts from the Quick Settings tile and automatic reconnects.
            BridgeState.log("Bridge", "Not started: no gamepad is plugged in")
            settings.bridgeEnabled = false
            stopSession(BridgeStatus.Failed("No gamepad is connected. Plug it in, then start the bridge."))
            shutDown()
            return
        }
        acquireWakeLock()
        val s = BridgeSession(this, settings, ::onSessionLost, ::onGamepadGone)
        session = s
        try {
            s.start()
            if (session === s) {
                retries = 0
                updateNotification("Virtual ${s.profile.shortLabel} controller is active")
            }
        } catch (e: Throwable) {
            val message = e.message ?: e.javaClass.simpleName
            BridgeState.log("Bridge", "Start failed: $message")
            if (session === s) {
                settings.bridgeEnabled = false
                stopSession(BridgeStatus.Failed(message))
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private fun onGamepadGone() {
        worker.execute {
            if (session == null) return@execute
            BridgeState.log("Bridge", "Gamepad unplugged, stopping the bridge")
            settings.bridgeEnabled = false
            stopSession(BridgeStatus.Stopped)
            shutDown()
        }
    }

    /** Leaves the foreground and ends the service; safe to call from the worker thread. */
    private fun shutDown() {
        Handler(Looper.getMainLooper()).post {
            if (session == null) {
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private fun onSessionLost(reason: String) {
        worker.execute {
            if (session == null) return@execute
            BridgeState.log("Bridge", reason)
            if (settings.bridgeEnabled && retries < MAX_RETRIES) {
                retries++
                stopSession(BridgeStatus.Starting("Reconnecting ($retries/$MAX_RETRIES)"))
                updateNotification("Reconnecting")
                worker.schedule({ if (settings.bridgeEnabled) startSession() }, 2, TimeUnit.SECONDS)
            } else {
                settings.bridgeEnabled = false
                stopSession(BridgeStatus.Failed(reason))
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private fun stopSession(finalStatus: BridgeStatus) {
        val s = session
        session = null
        try {
            s?.stop()
        } catch (e: Exception) {
            BridgeState.log("Bridge", "Error while stopping: ${e.message}")
        }
        BridgeState.status.value = finalStatus
        releaseWakeLock()
    }

    override fun onDestroy() {
        // Covers the system destroying the service without an explicit stop.
        val s = session
        session = null
        if (s != null) {
            try {
                s.stop()
            } catch (_: Exception) {
            }
            if (BridgeState.status.value.isActive) BridgeState.status.value = BridgeStatus.Stopped
        }
        worker.shutdown()
        releaseWakeLock()
        super.onDestroy()
    }

    // ---- Foreground plumbing -------------------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Gamepad bridge", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Shown while NeuroPad is bridging your gamepad"
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), immutable or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, GamepadBridgeService::class.java).setAction(ACTION_STOP),
            immutable or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_neuropad)
            .setContentTitle("NeuroPad")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun goForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) {
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NeuroPad::Bridge").apply {
            setReferenceCounted(false)
            // Bounded, so a crash can never hold the CPU awake forever; renewed on each start.
            acquire(12 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    companion object {
        const val ACTION_START = "io.github.abdullahamr871.neuropad.action.START"
        const val ACTION_STOP = "io.github.abdullahamr871.neuropad.action.STOP"
        const val ACTION_RELOAD = "io.github.abdullahamr871.neuropad.action.RELOAD"
        const val ACTION_TEST_RUMBLE = "io.github.abdullahamr871.neuropad.action.TEST_RUMBLE"
        const val EXTRA_LEFT = "left"
        const val EXTRA_RIGHT = "right"
        const val EXTRA_DURATION = "duration"
        private const val CHANNEL_ID = "neuropad_bridge"
        private const val NOTIFICATION_ID = 2801
        private const val MAX_RETRIES = 3

        @Volatile
        private var session: BridgeSession? = null

        val isRunning: Boolean get() = session != null

        fun start(context: Context) {
            val intent = Intent(context, GamepadBridgeService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            Settings(context).bridgeEnabled = false
            if (isRunning || BridgeState.status.value.isActive) {
                context.startService(Intent(context, GamepadBridgeService::class.java).setAction(ACTION_STOP))
            } else {
                BridgeState.status.value = BridgeStatus.Stopped
            }
        }

        /** Applies changed layout / haptic settings to a running session. */
        fun reload(context: Context) {
            if (isRunning) {
                context.startService(Intent(context, GamepadBridgeService::class.java).setAction(ACTION_RELOAD))
            }
        }

        fun testRumble(context: Context, left: Int, right: Int, durationMs: Long = 400) {
            if (!isRunning) return
            context.startService(
                Intent(context, GamepadBridgeService::class.java).setAction(ACTION_TEST_RUMBLE)
                    .putExtra(EXTRA_LEFT, left).putExtra(EXTRA_RIGHT, right).putExtra(EXTRA_DURATION, durationMs),
            )
        }

        /** Stop, then start again, e.g. after switching the emulated controller. */
        fun restart(context: Context) {
            val app = context.applicationContext
            app.startService(Intent(app, GamepadBridgeService::class.java).setAction(ACTION_STOP))
            Handler(Looper.getMainLooper()).postDelayed({ start(app) }, 700)
        }
    }
}
