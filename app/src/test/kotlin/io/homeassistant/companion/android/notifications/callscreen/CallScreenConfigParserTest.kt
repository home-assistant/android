package io.homeassistant.companion.android.notifications.callscreen

import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallScreenConfigParserTest {

    private val defaults = CallScreenDefaults(answerTitle = "Answer", declineTitle = "Decline", moreTitle = "More")

    private data class A(val key: String, val behavior: String? = null, val authRequired: Boolean = false)

    private fun payload(extras: String?, actions: List<A> = emptyList(), extra: Map<String, String> = emptyMap()) = buildMap {
        put("server_id", "7")
        put("title", "Front door")
        if (extras != null) put("intent_extras", extras)
        actions.forEachIndexed { i, a ->
            val n = i + 1
            put("action_${n}_key", a.key)
            put("action_${n}_title", a.key.lowercase())
            a.behavior?.let { put("action_${n}_behavior", it) }
            if (a.authRequired) put("action_${n}_authenticationRequired", "true")
        }
        putAll(extra)
    }

    private fun parse(data: Map<String, String>) = parseCallScreenConfig(data, callId = 42, defaults = defaults)

    @Test
    fun `Given missing or invalid intent_extras when parsing then it is a normal notification`() {
        assertNull(parse(payload(null)))
        assertNull(parse(payload("[object Object]")))
        assertNull(parse(payload("""{"other":{}}""")))
        assertNull(parse(payload("""{"call_screen":"yes"}""")))
    }

    @Test
    fun `Given empty call_screen and no actions when parsing then built-in decline and answer are added`() {
        val config = parse(payload("""{"call_screen":{}}"""))!!

        assertEquals(listOf("DECLINE", "ANSWER"), config.buttons.map { it.key })
        assertEquals(listOf("Decline", "Answer"), config.buttons.map { it.title })
        assertEquals(listOf("mdi:phone-hangup", "mdi:phone"), config.buttons.map { it.icon })
        assertTrue(config.menuItems.isEmpty())
        assertEquals(CallMedia.entries.toSet(), config.media)
        assertEquals(60.seconds, config.ringTimeout)
        assertEquals("call_screen_42", config.tag)
        assertEquals(CallBackground.None, config.background)
        assertEquals(7, config.serverId)
    }

    @Test
    fun `Given documented example when parsing then buttons and menu follow the array order`() {
        val extras = """{"call_screen":{"entity":"camera.front_door","actions":{
            "BUZZER":{"icon":"mdi:door-open","end_call":true},
            "MORE":{"icon":"mdi:dots-horizontal"},
            "REPLY_DEFAULT":{"icon":"mdi:message-reply-text","end_call":true}}}}"""
        val config = parse(
            payload(
                extras,
                listOf(
                    A("DECLINE", "decline"),
                    A("BUZZER", authRequired = true),
                    A("MORE", "menu"),
                    A("ANSWER", "answer"),
                    A("REPLY_DEFAULT", "menu_item"),
                ),
            ),
        )!!

        assertEquals(listOf("DECLINE", "BUZZER", "MORE", "ANSWER"), config.buttons.map { it.key })
        assertEquals(listOf("REPLY_DEFAULT"), config.menuItems.map { it.key })
        val buzzer = config.buttons[1]
        assertTrue(buzzer.endCall)
        assertTrue(buzzer.authenticationRequired)
        assertEquals("mdi:door-open", buzzer.icon)
        assertEquals(2, buzzer.index)
    }

    @Test
    fun `Given three custom actions without answer or decline when parsing then extras overflow into a menu`() {
        val config = parse(
            payload(
                """{"call_screen":{}}""",
                listOf(A("c1"), A("c2"), A("c3")),
            ),
        )!!

        assertEquals(listOf("DECLINE", "c1", "MENU", "ANSWER"), config.buttons.map { it.key })
        assertEquals(listOf("c2", "c3"), config.menuItems.map { it.key })
        assertEquals("More", config.buttons[2].title)
        assertTrue(config.menuItems.all { it.role == CallActionRole.MenuItem })
    }

    @Test
    fun `Given menu action without menu items when parsing then the menu button is dropped`() {
        val config = parse(payload("""{"call_screen":{}}""", listOf(A("MORE", "menu"), A("c1"))))!!

        assertEquals(listOf("DECLINE", "c1", "ANSWER"), config.buttons.map { it.key })
        assertTrue(config.menuItems.isEmpty())
    }

    @Test
    fun `Given duplicate special behaviors when parsing then later ones become custom buttons`() {
        val config = parse(
            payload("""{"call_screen":{}}""", listOf(A("A1", "answer"), A("A2", "answer"), A("D1", "decline"))),
        )!!

        assertEquals(listOf("A1", "A2", "D1"), config.buttons.map { it.key }.sortedBy { it })
        assertEquals(CallActionRole.Custom, config.buttons.first { it.key == "A2" }.role)
    }

    @Test
    fun `Given URI action when parsing then uri is kept only for the URI key`() {
        val data = payload("""{"call_screen":{}}""", listOf(A("URI"), A("OTHER"))) +
            mapOf("action_1_uri" to "/lovelace/cams", "action_2_uri" to "/ignored")
        val config = parse(data)!!

        assertEquals("/lovelace/cams", config.buttons.first { it.key == "URI" }.uri)
        assertNull(config.buttons.first { it.key == "OTHER" }.uri)
    }

    @Test
    fun `Given stream and entity when parsing then stream is preferred and background is stream`() {
        val config = parse(payload("""{"call_screen":{"entity":"camera.x","stream":"front"}}"""))!!

        assertEquals(CallBackground.Stream, config.background)
        assertEquals(Go2RtcSource.Url("front"), config.stream)
        assertEquals("/api/camera_proxy/camera.x", config.poster)
    }

    @Test
    fun `Given only entity when parsing then stream source is the entity`() {
        val config = parse(payload("""{"call_screen":{"entity":"camera.x"}}"""))!!

        assertEquals(Go2RtcSource.Entity("camera.x"), config.stream)
    }

    @Test
    fun `Given entity_image background without entity when parsing then it falls back to the data image`() {
        val config = parse(
            payload("""{"call_screen":{"background":"entity_image"}}""", extra = mapOf("image" to "/local/door.png")),
        )!!

        assertEquals(CallBackground.Image("/local/door.png"), config.background)
        assertNull(config.stream)
    }

    @Test
    fun `Given explicit image background with a stream when parsing then image wins and poster stays`() {
        val config = parse(
            payload(
                """{"call_screen":{"background":"image","stream":"s"}}""",
                extra = mapOf("image" to "/local/door.png"),
            ),
        )!!

        assertEquals(CallBackground.Image("/local/door.png"), config.background)
        assertEquals(Go2RtcSource.Url("s"), config.stream)
    }

    @Test
    fun `Given media with unknown tokens when parsing then unknown are dropped and empty means default`() {
        assertEquals(
            setOf(CallMedia.AUDIO, CallMedia.MICROPHONE),
            parse(payload("""{"call_screen":{"media":"audio, MICROPHONE, foo"}}"""))!!.media,
        )
        assertEquals(
            CallMedia.entries.toSet(),
            parse(payload("""{"call_screen":{"media":"foo"}}"""))!!.media,
        )
    }

    @Test
    fun `Given non positive ring_timeout when parsing then default is used`() {
        assertEquals(60.seconds, parse(payload("""{"call_screen":{"ring_timeout":0}}"""))!!.ringTimeout)
        assertEquals(15.seconds, parse(payload("""{"call_screen":{"ring_timeout":15}}"""))!!.ringTimeout)
    }

    @Test
    fun `Given reply history keys when parsing then event data excludes them`() {
        val config = parse(
            payload("""{"call_screen":{}}""", extra = mapOf("reply_history_0" to "hi", "tag" to "doorbell")),
        )!!

        assertFalse(config.eventData.containsKey("reply_history_0"))
        assertEquals("doorbell", config.tag)
        assertEquals("doorbell", config.eventData["tag"])
    }
}
