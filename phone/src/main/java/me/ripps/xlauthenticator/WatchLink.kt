package me.ripps.xlauthenticator

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit

/** Message paths shared with the watch app. Keep in sync with wear/…/WatchLink.kt. */
object WatchLink {
    const val PATH_SEND = "/xl/send"
    const val PATH_RESULT = "/xl/result"
    const val PATH_PROMPT = "/xl/prompt"

    /** Result payloads sent back to the watch. */
    const val RESULT_SENT = "sent"
    const val RESULT_NOT_WAITING = "not_waiting"
    const val RESULT_UNREACHABLE = "unreachable"
    const val RESULT_NO_SETUP = "no_setup"
    const val RESULT_BLOCKED = "blocked"

    /** Asks every connected watch to show the "Send code?" prompt. Blocking; call off the main thread. */
    fun promptWatches(context: Context) {
        val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, 5, TimeUnit.SECONDS)
        val messages = Wearable.getMessageClient(context)
        for (node in nodes) {
            runCatching { Tasks.await(messages.sendMessage(node.id, PATH_PROMPT, null), 5, TimeUnit.SECONDS) }
        }
    }
}
