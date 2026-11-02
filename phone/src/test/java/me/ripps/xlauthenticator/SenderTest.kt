package me.ripps.xlauthenticator

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class SenderTest {
    /** Mimics XIVLauncher's HttpServer: read one buffer, regex out the path, reply, close. */
    private fun fakeLauncher(server: ServerSocket, paths: MutableList<String>) = thread {
        repeat(2) {
            server.accept().use { client ->
                val buffer = ByteArray(1024)
                val read = client.getInputStream().read(buffer)
                val message = if (read > 0) String(buffer, 0, read) else ""
                paths += Regex("GET (?<url>.+) HTTP").find(message)?.groups?.get("url")?.value ?: ""
                client.getOutputStream().write("HTTP/1.1 200 OK\r\n\r\n".toByteArray())
            }
        }
    }

    @Test
    fun sendsCodeInXivLauncherFormatAndProbeSendsNothing() {
        ServerSocket(0).use { server ->
            val paths = mutableListOf<String>()
            val launcher = fakeLauncher(server, paths)
            assertEquals(Sender.Result.ACCEPTED, Sender.probe("127.0.0.1", server.localPort))
            val results = Sender.send(listOf("127.0.0.1"), "123456", server.localPort)
            launcher.join(5000)
            assertEquals(Sender.Result.ACCEPTED, Sender.summarize(results))
            assertEquals(listOf("", "/ffxivlauncher/123456"), paths)
        }
    }

    @Test
    fun closedPortMeansNotWaiting() {
        val port = ServerSocket(0).use { it.localPort }
        assertEquals(Sender.Result.NOT_WAITING, Sender.probe("127.0.0.1", port))
    }

    @Test
    fun summaryPrefersBestResult() {
        assertEquals(
            Sender.Result.NOT_WAITING,
            Sender.summarize(mapOf("a" to Sender.Result.UNREACHABLE, "b" to Sender.Result.NOT_WAITING)),
        )
    }

    @Test
    fun parsesIpv4() {
        assertEquals(0xC0A80102.toInt(), AutoPrompt.parseIpv4("192.168.1.2"))
        assertEquals(null, AutoPrompt.parseIpv4("my-pc.local"))
        assertEquals(null, AutoPrompt.parseIpv4("192.168.1.256"))
    }
}
