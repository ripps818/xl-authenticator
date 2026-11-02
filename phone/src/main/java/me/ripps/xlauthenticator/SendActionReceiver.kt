package me.ripps.xlauthenticator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** The Send button on the phone's login prompt. */
class SendActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        thread(name = "xl-send") {
            try {
                PromptNotification.showResult(appContext, PhoneListenerService.sendCode(SecretStore(appContext)))
            } finally {
                pending.finish()
            }
        }
    }
}
