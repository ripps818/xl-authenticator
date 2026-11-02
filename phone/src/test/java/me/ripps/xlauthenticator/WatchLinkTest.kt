package me.ripps.xlauthenticator

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchLinkTest {
    @Test
    fun readsSentVersion() {
        assertEquals(1, WatchLink.watchProtocol("1".toByteArray()))
        assertEquals(3, WatchLink.watchProtocol(" 3\n".toByteArray()))
    }

    @Test
    fun missingOrGarbledVersionPredatesTheProtocol() {
        assertEquals(0, WatchLink.watchProtocol(null))
        assertEquals(0, WatchLink.watchProtocol(ByteArray(0)))
        assertEquals(0, WatchLink.watchProtocol("garbage".toByteArray()))
        assertTrue(WatchLink.watchProtocol(null) < WatchLink.MIN_WATCH_PROTOCOL)
    }

    @Test
    fun replyIsPhoneWrittenJson() {
        val ok = JSONObject(String(WatchLink.reply(WatchLink.RESULT_SENT)))
        assertEquals(true, ok.getBoolean("ok"))
        assertEquals("Sent", ok.getString("message"))
        val failed = JSONObject(String(WatchLink.reply(WatchLink.RESULT_NOT_WAITING)))
        assertEquals(false, failed.getBoolean("ok"))
        assertEquals(WatchLink.watchMessage(WatchLink.RESULT_NOT_WAITING), failed.getString("message"))
    }

    @Test
    fun unknownResultsStillProduceAMessage() {
        // A result added in a future phone app must still show something sensible on the watch.
        val reply = JSONObject(String(WatchLink.reply("some_future_result")))
        assertEquals(false, reply.getBoolean("ok"))
        assertTrue(reply.getString("message").isNotEmpty())
    }

    @Test
    fun promptCarriesTextAndTimeout() {
        val prompt = JSONObject(String(WatchLink.promptPayload()))
        assertEquals(WatchLink.PROMPT_TITLE, prompt.getString("title"))
        assertEquals(WatchLink.PROMPT_TIMEOUT_MS, prompt.getLong("timeout_ms"))
    }
}
