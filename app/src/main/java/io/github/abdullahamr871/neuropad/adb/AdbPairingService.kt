package io.github.abdullahamr871.neuropad.adb

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
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import io.github.abdullahamr871.neuropad.R
import io.github.abdullahamr871.neuropad.bridge.AdbStatus
import io.github.abdullahamr871.neuropad.bridge.BridgeState
import io.github.abdullahamr871.neuropad.bridge.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.concurrent.thread

/**
 * Pairs the app with the phone's Wireless debugging without leaving the Settings screen:
 * the six digit code is typed straight into a notification (the pairing dialog closes,
 * and the code changes, as soon as Settings loses focus).
 */
class AdbPairingService : Service() {

    enum class State { IDLE, SEARCHING, FOUND, PAIRING, PAIRED, FAILED }

    private var discovery: AdbDiscovery? = null
    @Volatile private var pairingPort = 0
    @Volatile private var connectPort = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSearch()
            ACTION_CODE -> {
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)?.toString()?.trim()
                if (code.isNullOrEmpty()) finish() else pair(code)
            }
            else -> finish()
        }
        return START_NOT_STICKY
    }

    private fun startSearch() {
        createChannel()
        val notification = base("Looking for the pairing dialog")
            .setContentText("Open Developer options > Wireless debugging > Pair device with pairing code")
            .setOngoing(true)
            .addAction(0, "Cancel", servicePendingIntent(ACTION_CANCEL, 3, mutable = false))
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        state.value = State.SEARCHING
        message.value = ""
        if (discovery == null) {
            discovery = AdbDiscovery(this, object : AdbDiscovery.Listener {
                override fun onConnectPort(port: Int) {
                    connectPort = port
                    Settings(this@AdbPairingService).wirelessPort = port
                }

                override fun onPairingPort(port: Int) {
                    pairingPort = port
                    if (state.value == State.SEARCHING || state.value == State.FAILED) {
                        state.value = State.FOUND
                        showCodeNotification()
                    }
                }
            })
        }
        discovery?.start()
    }

    private fun showCodeNotification() {
        val remoteInput = RemoteInput.Builder(KEY_CODE).setLabel("6 digit pairing code").build()
        val action = NotificationCompat.Action.Builder(
            0, "Enter pairing code", servicePendingIntent(ACTION_CODE, 2, mutable = true),
        ).addRemoteInput(remoteInput).build()
        notify(
            base("Pairing dialog found")
                .setContentText("Tap \"Enter pairing code\" and type the 6 digits shown in Settings.")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(action)
                .build(),
        )
    }

    private fun pair(code: String) {
        val port = pairingPort
        if (port == 0) {
            fail("The pairing dialog was not found yet. Keep it open and try again.")
            return
        }
        state.value = State.PAIRING
        notify(base("Pairing...").setProgress(0, 0, true).setOngoing(true).build())
        thread(name = "NeuroPad-Pairing") {
            val result = pairNow(this, port, code, connectPort)
            if (result == null) {
                notify(
                    base("Paired with Wireless debugging")
                        .setContentText("Go back to NeuroPad and start the bridge.")
                        .setAutoCancel(true).build(),
                    FINAL_NOTIFICATION_ID,
                )
                finish()
            } else {
                fail(result)
            }
        }
    }

    private fun fail(reason: String) {
        state.value = State.FAILED
        message.value = reason
        notify(base("Pairing failed").setContentText(reason).setAutoCancel(true).build(), FINAL_NOTIFICATION_ID)
        finish()
    }

    private fun finish() {
        discovery?.stop()
        discovery = null
        if (state.value == State.SEARCHING || state.value == State.FOUND) state.value = State.IDLE
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        discovery?.stop()
        super.onDestroy()
    }

    private fun base(title: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_neuropad)
        .setContentTitle(title)
        .setOnlyAlertOnce(false)

    private fun notify(notification: Notification, id: Int = NOTIFICATION_ID) {
        try {
            getSystemService(NotificationManager::class.java).notify(id, notification)
        } catch (_: Exception) {
        }
    }

    private fun servicePendingIntent(action: String, requestCode: Int, mutable: Boolean): PendingIntent {
        val intent = Intent(this, AdbPairingService::class.java).setAction(action)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        // RemoteInput needs a mutable PendingIntent; the intent is explicit, so that is safe.
        flags = flags or when {
            mutable && Build.VERSION.SDK_INT >= 31 -> PendingIntent.FLAG_MUTABLE
            !mutable && Build.VERSION.SDK_INT >= 23 -> PendingIntent.FLAG_IMMUTABLE
            else -> 0
        }
        return PendingIntent.getService(this, requestCode, intent, flags)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Wireless debugging pairing", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "io.github.abdullahamr871.neuropad.action.PAIR_START"
        const val ACTION_CODE = "io.github.abdullahamr871.neuropad.action.PAIR_CODE"
        const val ACTION_CANCEL = "io.github.abdullahamr871.neuropad.action.PAIR_CANCEL"
        private const val KEY_CODE = "pairing_code"
        private const val CHANNEL_ID = "neuropad_pairing"
        private const val NOTIFICATION_ID = 2802
        private const val FINAL_NOTIFICATION_ID = 2803

        val state = MutableStateFlow(State.IDLE)
        val message = MutableStateFlow("")

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, AdbPairingService::class.java).setAction(ACTION_START),
            )
        }

        fun cancel(context: Context) {
            if (state.value == State.SEARCHING || state.value == State.FOUND) {
                context.startService(Intent(context, AdbPairingService::class.java).setAction(ACTION_CANCEL))
            }
        }

        /**
         * Pairs and, when the connect port is known, connects right away.
         * Blocking. @return null on success, otherwise a message for the user.
         */
        fun pairNow(context: Context, pairingPort: Int, code: String, connectPort: Int): String? {
            val settings = Settings(context)
            return try {
                val manager = AdbConnectionManager.get(context)
                if (!manager.pair(AdbConnectionManager.LOOPBACK, pairingPort, code)) {
                    throw IllegalStateException("the phone rejected the code")
                }
                settings.paired = true
                state.value = State.PAIRED
                message.value = ""
                val port = connectPort.takeIf { it > 0 } ?: settings.wirelessPort
                if (port > 0) {
                    try {
                        if (!manager.isConnected && manager.connect(AdbConnectionManager.LOOPBACK, port)) {
                            BridgeState.adb.value = AdbStatus.Connected(port)
                        }
                    } catch (e: Throwable) {
                        BridgeState.log("ADB", "Paired, but the first connect failed: ${e.message}")
                    }
                }
                null
            } catch (e: Throwable) {
                state.value = State.FAILED
                val reason = "Pairing failed: ${e.message ?: e.javaClass.simpleName}. Check the code and try again."
                message.value = reason
                reason
            }
        }
    }
}
