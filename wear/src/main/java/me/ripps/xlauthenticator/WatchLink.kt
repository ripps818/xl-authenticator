package me.ripps.xlauthenticator

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Message paths shared with the phone app. Keep in sync with phone/…/WatchLink.kt. */
object WatchLink {
    const val CAPABILITY_PHONE = "xl_phone"
    const val PATH_SEND = "/xl/send"
    const val PATH_RESULT = "/xl/result"
    const val PATH_PROMPT = "/xl/prompt"

    const val RESULT_SENT = "sent"
    const val RESULT_NOT_WAITING = "not_waiting"
    const val RESULT_UNREACHABLE = "unreachable"
    const val RESULT_NO_SETUP = "no_setup"

    /** Watch-side failures, never sent by the phone. */
    const val RESULT_NO_PHONE = "no_phone"
    const val RESULT_TIMEOUT = "timeout"

    private const val REPLY_TIMEOUT_MS = 10_000L

    /** Asks the phone to send the current code to the PC and waits for its answer. */
    suspend fun requestSend(context: Context): String {
        val capability = runCatching {
            Wearable.getCapabilityClient(context)
                .getCapability(CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await()
        }.getOrNull()
        val node = capability?.nodes?.firstOrNull { it.isNearby }
            ?: capability?.nodes?.firstOrNull()
            ?: return RESULT_NO_PHONE

        val messages = Wearable.getMessageClient(context)
        val reply = CompletableDeferred<String>()
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == PATH_RESULT && event.sourceNodeId == node.id) {
                reply.complete(String(event.data))
            }
        }
        messages.addListener(listener).await()
        try {
            runCatching { messages.sendMessage(node.id, PATH_SEND, null).await() }
                .onFailure { return RESULT_NO_PHONE }
            return withTimeoutOrNull(REPLY_TIMEOUT_MS) { reply.await() } ?: RESULT_TIMEOUT
        } finally {
            messages.removeListener(listener)
        }
    }

    /** Short text for the watch screen. */
    fun describe(result: String): String = when (result) {
        RESULT_SENT -> "Sent"
        RESULT_NOT_WAITING -> "Launcher isn't asking for a code"
        RESULT_UNREACHABLE -> "Phone can't reach the PC"
        RESULT_NO_SETUP -> "Set up the phone app first"
        RESULT_NO_PHONE -> "Phone not connected"
        else -> "Phone didn't answer"
    }
}
