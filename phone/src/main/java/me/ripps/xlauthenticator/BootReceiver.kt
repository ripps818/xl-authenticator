package me.ripps.xlauthenticator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms and network callbacks don't survive a reboot or app update, so re-arm them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutoPrompt.restore(context)
    }
}
