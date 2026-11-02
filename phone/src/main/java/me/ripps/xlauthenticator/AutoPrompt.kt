package me.ripps.xlauthenticator

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.SystemClock
import java.net.Inet4Address

/**
 * Watches for XIVLauncher's 2FA dialog, which is the only time port 4646 is open, and asks the
 * watch to prompt. Built to do as little background work as possible:
 *
 * - No foreground service or wake lock. Each check is one alarm that wakes [ProbeReceiver] for a
 *   single sub-second TCP connect, then schedules the next one.
 * - Off the home network there are no alarms at all. A PendingIntent network callback wakes the
 *   app when Wi-Fi changes, and checks resume only when the phone is on the PC's subnet.
 * - The interval adapts: quick while the PC is on, slow and backing off while it isn't answering.
 * - Plain (not allow-while-idle) alarms, so deep Doze pauses checking instead of being fought.
 *   Tapping Send on the watch always works regardless.
 */
object AutoPrompt {
    const val ACTION_CHECK = "me.ripps.xlauthenticator.CHECK"
    const val ACTION_NETWORK = "me.ripps.xlauthenticator.NETWORK"

    /** PC answers (launcher waiting or not): check often enough to catch the login dialog. */
    private const val ACTIVE_INTERVAL_MS = 5_000L

    /** PC not answering: start here and double up to [MAX_IDLE_INTERVAL_MS]. */
    private const val MIN_IDLE_INTERVAL_MS = 30_000L
    private const val MAX_IDLE_INTERVAL_MS = 180_000L

    /** Window allowed when exact alarms aren't permitted. */
    private const val INEXACT_WINDOW_MS = 10_000L

    private const val STATS = "auto_prompt_stats"

    fun enable(context: Context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        runCatching { connectivity.unregisterNetworkCallback(networkIntent(context)) }
        connectivity.registerNetworkCallback(request, networkIntent(context))
        resetState(context)
        schedule(context, 0)
    }

    fun disable(context: Context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        runCatching { connectivity.unregisterNetworkCallback(networkIntent(context)) }
        context.getSystemService(AlarmManager::class.java).cancel(checkIntent(context))
        PromptNotification.dismissPrompt(context)
    }

    /** Re-arms after a reboot or app update, when registered callbacks and alarms are gone. */
    fun restore(context: Context) {
        if (SecretStore(context).autoPrompt) enable(context)
    }

    /** One check: called by [ProbeReceiver] on a background thread. */
    fun check(context: Context) {
        val store = SecretStore(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (!store.autoPrompt) {
            disable(context)
            return
        }
        val ips = store.ips
        if (ips.isEmpty() || !onHomeNetwork(context, ips)) {
            // Stay asleep until the network callback says Wi-Fi changed.
            alarms.cancel(checkIntent(context))
            resetState(context)
            return
        }

        val started = SystemClock.elapsedRealtime()
        val result = Sender.summarize(ips.associateWith { Sender.probe(it) })

        val state = context.getSharedPreferences(STATS, Context.MODE_PRIVATE)
        val wasOpen = state.getBoolean("open", false)
        if (result == Sender.Result.ACCEPTED && !wasOpen) {
            runCatching { WatchLink.promptWatches(context) }
            runCatching { PromptNotification.show(context) }
            state.edit().putLong("prompts", state.getLong("prompts", 0) + 1).apply()
        }

        if (result != Sender.Result.ACCEPTED && wasOpen) PromptNotification.dismissPrompt(context)

        val idle = state.getLong("idle_interval", MIN_IDLE_INTERVAL_MS)
        // A blocked probe won't fix itself until the permission is granted; back off like a dead PC.
        val slow = result == Sender.Result.UNREACHABLE || result == Sender.Result.BLOCKED
        val next = if (slow) idle else ACTIVE_INTERVAL_MS
        state.edit()
            .putBoolean("open", result == Sender.Result.ACCEPTED)
            .putLong(
                "idle_interval",
                if (slow) (idle * 2).coerceAtMost(MAX_IDLE_INTERVAL_MS)
                else MIN_IDLE_INTERVAL_MS,
            )
            .putLong("probes", state.getLong("probes", 0) + 1)
            .putLong("busy_ms", state.getLong("busy_ms", 0) + SystemClock.elapsedRealtime() - started)
            .putLong("last_probe", System.currentTimeMillis())
            .putString("last_result", result.name)
            .apply()

        schedule(context, next)
    }

    data class Stats(val probes: Long, val prompts: Long, val busyMs: Long, val lastProbe: Long, val lastResult: String?)

    fun stats(context: Context): Stats {
        val state = context.getSharedPreferences(STATS, Context.MODE_PRIVATE)
        return Stats(
            probes = state.getLong("probes", 0),
            prompts = state.getLong("prompts", 0),
            busyMs = state.getLong("busy_ms", 0),
            lastProbe = state.getLong("last_probe", 0),
            lastResult = state.getString("last_result", null),
        )
    }

    fun clearStats(context: Context) {
        context.getSharedPreferences(STATS, Context.MODE_PRIVATE).edit()
            .remove("probes").remove("prompts").remove("busy_ms").remove("last_probe").remove("last_result")
            .apply()
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun resetState(context: Context) {
        context.getSharedPreferences(STATS, Context.MODE_PRIVATE).edit()
            .putBoolean("open", false)
            .putLong("idle_interval", MIN_IDLE_INTERVAL_MS)
            .apply()
    }

    private fun schedule(context: Context, delayMs: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = SystemClock.elapsedRealtime() + delayMs
        val intent = checkIntent(context)
        if (canScheduleExact(context)) {
            alarms.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, intent)
        } else {
            alarms.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, INEXACT_WINDOW_MS, intent)
        }
    }

    /** True when the phone is on Wi-Fi or Ethernet in the same subnet as one of the PC IPs. */
    private fun onHomeNetwork(context: Context, ips: List<String>): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) return false
        val links = connectivity.getLinkProperties(network)?.linkAddresses ?: return false
        val targets = ips.mapNotNull { parseIpv4(it) }
        // Hostnames can't be matched without a DNS lookup; accept any local network for them.
        if (targets.size < ips.size) return true
        return links.any { link ->
            val address = link.address as? Inet4Address ?: return@any false
            val prefix = link.prefixLength
            val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
            val local = toInt(address.address)
            targets.any { (it and mask) == (local and mask) }
        }
    }

    internal fun parseIpv4(text: String): Int? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        val bytes = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return null }
        return bytes.fold(0) { acc, b -> (acc shl 8) or b }
    }

    private fun toInt(bytes: ByteArray): Int = bytes.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xff) }

    private fun checkIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ProbeReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // Must be mutable: the system adds the network to the intent's extras.
    private fun networkIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, ProbeReceiver::class.java).setAction(ACTION_NETWORK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}
