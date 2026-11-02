package me.ripps.xlauthenticator

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Talks to the phone app. The watch knows nothing about XIVLauncher: it asks the phone to send
 * and shows whatever the phone answers. See docs/PROTOCOL.md before changing anything here.
 */
object WatchLink {
    const val CAPABILITY_PHONE = "xl_phone"
    const val PATH_SEND = "/xl/send"
    const val PATH_RESULT = "/xl/result"
    const val PATH_PROMPT = "/xl/prompt"

    /** Sent with each request so the phone can tell an out-of-date watch app. */
    const val PROTOCOL_VERSION = 1

    private const val REPLY_TIMEOUT_MS = 10_000L

    /** What to show: whether it worked, and the text, which the phone decides. */
    data class Outcome(val ok: Boolean, val message: String)

    /** Reads the phone's JSON reply, ignoring fields it doesn't know. Anything else is a failure. */
    fun parseReply(data: ByteArray?): Outcome {
        val json = runCatching { JSONObject(String(data ?: ByteArray(0))) }.getOrNull()
            ?: return Outcome(false, "Update the phone app")
        val ok = json.optBoolean("ok", false)
        return Outcome(ok, json.optString("message").ifEmpty { if (ok) "Sent" else "Couldn't send" })
    }

    private val NO_PHONE = Outcome(false, "Phone not connected")
    private val NO_ANSWER = Outcome(false, "Phone didn't answer")

    /** Asks the phone to send the current code to the PC and waits for its answer. */
    suspend fun requestSend(context: Context): Outcome {
        val capability = runCatching {
            Wearable.getCapabilityClient(context)
                .getCapability(CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await()
        }.getOrNull()
        val node = capability?.nodes?.firstOrNull { it.isNearby }
            ?: capability?.nodes?.firstOrNull()
            ?: return NO_PHONE

        val messages = Wearable.getMessageClient(context)
        val reply = CompletableDeferred<Outcome>()
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == PATH_RESULT && event.sourceNodeId == node.id) {
                reply.complete(parseReply(event.data))
            }
        }
        messages.addListener(listener).await()
        try {
            runCatching { messages.sendMessage(node.id, PATH_SEND, PROTOCOL_VERSION.toString().toByteArray()).await() }
                .onFailure { return NO_PHONE }
            return withTimeoutOrNull(REPLY_TIMEOUT_MS) { reply.await() } ?: NO_ANSWER
        } finally {
            messages.removeListener(listener)
        }
    }
}
