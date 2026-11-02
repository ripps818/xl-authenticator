package me.ripps.xlauthenticator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/** Shows the "Send code?" prompt when the phone sees XIVLauncher waiting for 2FA. */
class WatchListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchLink.PATH_PROMPT) return
        // The phone decides the wording; these defaults cover older phone apps that send none.
        val prompt = runCatching { JSONObject(String(event.data ?: ByteArray(0))) }.getOrNull()
        val title = prompt?.optString("title").orEmpty().ifEmpty { "XIVLauncher is waiting" }
        val text = prompt?.optString("text").orEmpty().ifEmpty { "Send your 2FA code to the PC?" }
        val action = prompt?.optString("action").orEmpty().ifEmpty { "Send" }
        val timeout = prompt?.optLong("timeout_ms", 0L)?.takeIf { it > 0 } ?: PROMPT_TIMEOUT_MS
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Login prompts", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
        )
        val send = PendingIntent.getActivity(
            this, 0,
            Intent(this, SendActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_send)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(send)
            .addAction(Notification.Action.Builder(null, action, send).build())
            .setAutoCancel(true)
            // The launcher's dialog doesn't wait forever; neither should the prompt.
            .setTimeoutAfter(timeout)
            .build()
        notifications.notify(PROMPT_NOTIFICATION_ID, notification)
    }

    companion object {
        const val PROMPT_NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "login_prompt"
        private const val PROMPT_TIMEOUT_MS = 120_000L
    }
}
