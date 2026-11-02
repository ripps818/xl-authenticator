package me.ripps.xlauthenticator

import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.TimeUnit

/** Handles "send now" requests from the watch. Runs on a background thread, even when the app is closed. */
class PhoneListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchLink.PATH_SEND) return
        val reply = sendCode(SecretStore(this))
        runCatching {
            Tasks.await(
                Wearable.getMessageClient(this)
                    .sendMessage(event.sourceNodeId, WatchLink.PATH_RESULT, reply.toByteArray()),
                5, TimeUnit.SECONDS,
            )
        }
    }

    companion object {
        /** Generates the current code and sends it to every configured PC. Blocking. */
        fun sendCode(store: SecretStore): String {
            val code = store.currentCode()
            val ips = store.ips
            if (code == null || ips.isEmpty()) return WatchLink.RESULT_NO_SETUP
            return when (Sender.summarize(Sender.send(ips, code))) {
                Sender.Result.ACCEPTED -> WatchLink.RESULT_SENT
                Sender.Result.NOT_WAITING -> WatchLink.RESULT_NOT_WAITING
                Sender.Result.UNREACHABLE -> WatchLink.RESULT_UNREACHABLE
            }
        }
    }
}
