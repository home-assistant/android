package io.homeassistant.companion.android.sensors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationSensorManagerTest {

    @Test
    fun `Given small notification snapshot when building buckets then it keeps one bucket`() {
        val buckets = buildActiveNotificationBuckets(
            listOf(
                notificationRecord(1, "old"),
                notificationRecord(2, "new"),
            ),
            totalCount = 2,
            maxSerializedBytes = 2_000,
        )

        assertEquals(1, buckets.size)
        assertEquals(2, buckets[0]["total"])
        assertEquals(1, buckets[0]["part"])
        assertEquals(1, buckets[0]["parts"])
        assertEquals(true, buckets[0]["last"])
        assertTrue(buckets[0].containsKey("android.text_app_1"))
        assertTrue(buckets[0].containsKey("android.text_app_2"))
        assertFalse(buckets[0].containsKey("cut"))
    }

    @Test
    fun `Given large notification snapshot when building buckets then it sends chronological buckets`() {
        val buckets = buildActiveNotificationBuckets(
            listOf(
                notificationRecord(1, "old", "a".repeat(350)),
                notificationRecord(2, "middle", "b".repeat(350)),
                notificationRecord(3, "new", "c".repeat(350)),
            ),
            totalCount = 3,
            maxSerializedBytes = 900,
        )

        assertEquals(3, buckets.size)
        buckets.forEachIndexed { index, bucket ->
            assertEquals(index + 1, bucket["part"])
            assertEquals(3, bucket["parts"])
            assertTrue(bucket.serializedAttributeBytes() <= 900)
        }
        assertEquals(false, buckets[0]["last"])
        assertEquals(false, buckets[1]["last"])
        assertEquals(true, buckets[2]["last"])
        assertTrue(buckets[0].containsKey("android.text_app_1"))
        assertTrue(buckets[1].containsKey("android.text_app_2"))
        assertTrue(buckets[2].containsKey("android.text_app_3"))
    }

    @Test
    fun `Given newest notification can share a bucket when building buckets then newest stays in final bucket`() {
        val buckets = buildActiveNotificationBuckets(
            listOf(
                notificationRecord(1, "old", "a".repeat(420)),
                notificationRecord(2, "middle", "b".repeat(120)),
                notificationRecord(3, "new", "c".repeat(120)),
            ),
            totalCount = 3,
            maxSerializedBytes = 900,
        )

        assertEquals(2, buckets.size)
        assertTrue(buckets[0].containsKey("android.text_app_1"))
        assertTrue(buckets[1].containsKey("android.text_app_2"))
        assertTrue(buckets[1].containsKey("android.text_app_3"))
        assertEquals(true, buckets[1]["last"])
    }

    @Test
    fun `Given oversized single notification when building buckets then it trims and marks cut`() {
        val buckets = buildActiveNotificationBuckets(
            listOf(
                ActiveNotificationRecord(
                    postTime = 1,
                    attributes = linkedMapOf(
                        "app_1_post_time" to 1L,
                        "app_1_is_ongoing" to false,
                        "app_1_is_clearable" to true,
                        "android.text_app_1" to "x".repeat(5_000),
                        "android.messages_app_1" to List(20) { "message-$it-${"y".repeat(200)}" },
                    ),
                ),
            ),
            totalCount = 1,
            maxSerializedBytes = 900,
        )

        assertEquals(1, buckets.size)
        assertTrue(buckets[0].serializedAttributeBytes() <= 900)
        assertEquals(true, buckets[0]["cut"])
        assertTrue((buckets[0]["cut_count"] as Int) > 0)
        assertEquals(1L, buckets[0]["app_1_post_time"])
    }

    private fun notificationRecord(
        postTime: Long,
        label: String,
        body: String = label,
    ) = ActiveNotificationRecord(
        postTime = postTime,
        attributes = linkedMapOf(
            "app_${postTime}_post_time" to postTime,
            "app_${postTime}_is_ongoing" to false,
            "app_${postTime}_is_clearable" to true,
            "android.title_app_$postTime" to label,
            "android.text_app_$postTime" to body,
        ),
    )
}
