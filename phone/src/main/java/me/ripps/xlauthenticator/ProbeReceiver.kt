package me.ripps.xlauthenticator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** Runs one auto-prompt check per alarm or network change, off the main thread. */
class ProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        thread(name = "xl-probe") {
            try {
                AutoPrompt.check(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
