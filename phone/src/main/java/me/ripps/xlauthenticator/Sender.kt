package me.ripps.xlauthenticator

import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Talks to XIVLauncher's OTP listener. XIVLauncher only listens on port 4646 while its 2FA
 * dialog is open, reads a single buffer, matches `GET (url) HTTP` and closes the connection.
 * A raw socket is enough, and it isn't subject to Android's cleartext-HTTP policy.
 */
object Sender {
    const val PORT = 4646

    enum class Result {
        /** The launcher accepted the connection: the code was delivered, or the probe found it waiting. */
        ACCEPTED,

        /** The PC answered but XIVLauncher isn't waiting for a code (connection refused). */
        NOT_WAITING,

        /** The PC didn't answer in time: off, asleep, or not on this network. */
        UNREACHABLE,
    }

    /** Sends [code] to every IP. Overall success means at least one launcher took it. */
    fun send(ips: List<String>, code: String, port: Int = PORT): Map<String, Result> =
        ips.associateWith { ip ->
            exchange(ip, port, CONNECT_TIMEOUT_MS) { socket ->
                socket.soTimeout = CONNECT_TIMEOUT_MS
                socket.getOutputStream().apply {
                    write("GET /ffxivlauncher/$code HTTP/1.0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    flush()
                }
                // Wait for XIVLauncher's reply or close so the request is read before we hang up.
                runCatching { socket.getInputStream().read() }
            }
        }

    /** Connects and hangs up without sending. XIVLauncher ignores the empty request. */
    fun probe(ip: String, port: Int = PORT, timeoutMs: Int = PROBE_TIMEOUT_MS): Result =
        exchange(ip, port, timeoutMs) {}

    private fun exchange(ip: String, port: Int, timeoutMs: Int, block: (Socket) -> Unit): Result =
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                block(socket)
            }
            Result.ACCEPTED
        } catch (e: ConnectException) {
            Result.NOT_WAITING
        } catch (e: SocketTimeoutException) {
            Result.UNREACHABLE
        } catch (e: IOException) {
            // No route to host, network unreachable, bad address, and similar.
            Result.UNREACHABLE
        }

    fun summarize(results: Map<String, Result>): Result = when {
        results.values.any { it == Result.ACCEPTED } -> Result.ACCEPTED
        results.values.any { it == Result.NOT_WAITING } -> Result.NOT_WAITING
        else -> Result.UNREACHABLE
    }

    private const val CONNECT_TIMEOUT_MS = 1500
    private const val PROBE_TIMEOUT_MS = 700
}
