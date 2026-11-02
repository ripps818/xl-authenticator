package me.ripps.xlauthenticator

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to the watch app. The watch only ever shows what the phone tells it, so wording, outcomes
 * and how the code is sent can change here without a watch update. See docs/PROTOCOL.md before
 * changing anything in this file.
 */
object WatchLink {
    const val PATH_SEND = "/xl/send"
    const val PATH_RESULT = "/xl/result"
    const val PATH_PROMPT = "/xl/prompt"

    /** Outcomes of a send. The phone's own screen and notifications use these too. */
    const val RESULT_SENT = "sent"
    const val RESULT_NOT_WAITING = "not_waiting"
    const val RESULT_UNREACHABLE = "unreachable"
    const val RESULT_NO_SETUP = "no_setup"
    const val RESULT_BLOCKED = "blocked"
    const val RESULT_INCOMPATIBLE = "incompatible"

    /**
     * The oldest watch protocol this phone app still serves. Raise it only for a breaking change,
     * which should be a last resort: a watch below it is told to update. A request with no
     * version comes from a watch app that predates the protocol and counts as 0.
     */
    const val MIN_WATCH_PROTOCOL = 1

    fun watchProtocol(payload: ByteArray?): Int =
        payload?.let { String(it).trim().toIntOrNull() } ?: 0

    const val PROMPT_TITLE = "XIVLauncher is waiting"
    const val PROMPT_TEXT = "Send your 2FA code to the PC?"
    const val PROMPT_ACTION = "Send"
    const val PROMPT_TIMEOUT_MS = 120_000L

    /** Short wording for the watch screen. */
    fun watchMessage(code: String): String = when (code) {
        RESULT_SENT -> "Sent"
        RESULT_NOT_WAITING -> "Launcher isn't asking for a code"
        RESULT_UNREACHABLE -> "Phone can't reach the PC"
        RESULT_BLOCKED -> "Allow local network access in the phone app"
        RESULT_INCOMPATIBLE -> "Update the watch app"
        else -> "Set up the phone app first"
    }

    /** The reply to a send request. Never remove or repurpose a field; the watch ignores unknown ones. */
    fun reply(code: String): ByteArray =
        JSONObject().put("ok", code == RESULT_SENT).put("message", watchMessage(code))
            .toString().toByteArray()

    fun promptPayload(): ByteArray = JSONObject()
        .put("title", PROMPT_TITLE)
        .put("text", PROMPT_TEXT)
        .put("action", PROMPT_ACTION)
        .put("timeout_ms", PROMPT_TIMEOUT_MS)
        .toString().toByteArray()

    /** Asks every connected watch to show the "Send code?" prompt. Blocking; call off the main thread. */
    fun promptWatches(context: Context) {
        val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, 5, TimeUnit.SECONDS)
        val messages = Wearable.getMessageClient(context)
        for (node in nodes) {
            runCatching { Tasks.await(messages.sendMessage(node.id, PATH_PROMPT, promptPayload()), 5, TimeUnit.SECONDS) }
        }
    }

    /** Plain-language outcome of a send, for the phone's screen and notifications. */
    fun describe(reply: String) = when (reply) {
        RESULT_SENT -> "Sent."
        RESULT_NOT_WAITING -> "The PC is on, but XIVLauncher isn't asking for a code."
        RESULT_UNREACHABLE -> "Couldn't reach the PC. Check the IP, that the launcher's " +
            "XL Authenticator setting is on, and that the PC's firewall allows port 4646."
        RESULT_BLOCKED -> "Android blocked the connection. Allow local network access for this app."
        else -> "Set the key and PC address first."
    }
}
