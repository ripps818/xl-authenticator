package me.ripps.xlauthenticator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

/**
 * The phone-side "XIVLauncher is waiting" notification, with a Send button. It is local-only:
 * the watch app posts its own prompt, so Wear OS must not also bridge this one.
 */
object PromptNotification {
    private const val CHANNEL_ID = "login_prompt"
    private const val ID = 1
    private const val PREFS = "prompt_notification"
    private const val KEY_PROMPT_SHOWING = "prompt_showing"

    /** The launcher's dialog doesn't wait forever; neither should the prompt. */
    private const val PROMPT_TIMEOUT_MS = WatchLink.PROMPT_TIMEOUT_MS
    private const val RESULT_TIMEOUT_MS = 5_000L

    fun show(context: Context) {
        val manager = manager(context) ?: return
        val send = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, SendActionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = builder(context)
            .setContentTitle(WatchLink.PROMPT_TITLE)
            .setContentText(WatchLink.PROMPT_TEXT)
            .setContentIntent(send)
            .addAction(Notification.Action.Builder(icon(context), WatchLink.PROMPT_ACTION, send).build())
            .setAutoCancel(true)
            .setTimeoutAfter(PROMPT_TIMEOUT_MS)
            .build()
        setShowing(context, true)
        manager.notify(ID, notification)
    }

    /** Replaces the prompt with the outcome of tapping Send, without buzzing again. */
    fun showResult(context: Context, reply: String) {
        val manager = manager(context) ?: return
        val notification = builder(context)
            .setContentTitle(if (reply == WatchLink.RESULT_SENT) "Sent" else "Couldn't send")
            .setContentText(WatchLink.describe(reply))
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(RESULT_TIMEOUT_MS)
            .build()
        setShowing(context, false)
        manager.notify(ID, notification)
    }

    /** Removes the prompt once the launcher stops waiting, but leaves a result message alone. */
    fun dismissPrompt(context: Context) {
        if (!prefs(context).getBoolean(KEY_PROMPT_SHOWING, false)) return
        setShowing(context, false)
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    private fun builder(context: Context) = Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_key)
        .setCategory(Notification.CATEGORY_STATUS)
        .setLocalOnly(true)

    private fun icon(context: Context) = Icon.createWithResource(context, R.drawable.ic_key)

    private fun manager(context: Context): NotificationManager? {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Login prompts", NotificationManager.IMPORTANCE_HIGH)
        )
        // Without the notification permission there is nothing to show; the watch still prompts.
        return manager.takeIf { it.areNotificationsEnabled() }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun setShowing(context: Context, showing: Boolean) =
        prefs(context).edit().putBoolean(KEY_PROMPT_SHOWING, showing).apply()
}
