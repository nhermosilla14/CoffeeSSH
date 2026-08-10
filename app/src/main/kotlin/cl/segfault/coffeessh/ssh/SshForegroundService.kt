package cl.segfault.coffeessh.ssh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import cl.segfault.coffeessh.CoffeeSshApp
import cl.segfault.coffeessh.MainActivity
import cl.segfault.coffeessh.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps [SshSession]s alive while the app is backgrounded (Android would otherwise kill
 * their sockets/threads soon after). One shared notification reports how many sessions
 * are active with a "Disconnect all" action; tapping the notification returns to the app.
 *
 * Uses foreground service type `specialUse`: an interactive, user-initiated network
 * session doesn't fit `dataSync` (being deprecated, meant for one-off transfers) or
 * `connectedDevice` (meant for Bluetooth/USB accessories, not general TCP sockets).
 */
class SshForegroundService : LifecycleService() {

    private lateinit var registry: SshSessionRegistry

    override fun onCreate() {
        super.onCreate()
        registry = (application as CoffeeSshApp).container.sshSessionRegistry
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_DISCONNECT_ALL) {
            registry.all().forEach { it.disconnect() }
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        watchSessions()
        return START_STICKY
    }

    /** Polls session states at a modest interval to refresh the notification and to stop
     * the service (and its notification) once nothing is left alive. Simple and cheap;
     * a fully reactive multi-session combinator wasn't worth the extra complexity here. */
    private fun watchSessions() {
        lifecycleScope.launch {
            while (isActive) {
                if (anySessionAlive()) {
                    updateNotification()
                } else {
                    ServiceCompat.stopForeground(this@SshForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }
                delay(2_000)
            }
        }
    }

    private fun anySessionAlive(): Boolean = registry.all().any { session ->
        when (session.state.value) {
            is SshSessionState.Connecting,
            is SshSessionState.Authenticating,
            is SshSessionState.AwaitingHostKeyConfirmation,
            is SshSessionState.Connected,
            -> true
            else -> false
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val activeCount = registry.all().count { it.state.value == SshSessionState.Connected }
        val contentText = when (activeCount) {
            0 -> getString(R.string.ssh_notification_connecting)
            1 -> getString(R.string.ssh_notification_one_session)
            else -> getString(R.string.ssh_notification_n_sessions, activeCount)
        }
        val activeNames = registry.activeSessions.value
            .filter { it.session.state.value == SshSessionState.Connected }
            .mapNotNull { it.name }
            .take(2)
            .joinToString(", ")
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnectAllIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, SshForegroundService::class.java).setAction(ACTION_DISCONNECT_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (activeNames.isBlank()) contentText else "$contentText: $activeNames")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.ssh_notification_disconnect_all), disconnectAllIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.ssh_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "ssh_sessions"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DISCONNECT_ALL = "cl.segfault.coffeessh.ssh.DISCONNECT_ALL"

        fun start(context: Context) {
            val intent = Intent(context, SshForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
