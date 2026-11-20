package io.homeassistant.companion.android.settings.url

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtraUrlsListTest {

    @Test
    fun `Given valid URL with path when adding then it is normalized and appended`() {
        val result = ExtraUrlsList.add(listOf("https://a.example.com/"), " https://b.example.com:8123/lovelace ")

        assertEquals(
            AddExtraUrlResult.Added(listOf("https://a.example.com/", "https://b.example.com:8123/")),
            result,
        )
    }

    @Test
    fun `Given invalid input when adding then result is Invalid`() {
        assertEquals(AddExtraUrlResult.Invalid, ExtraUrlsList.add(emptyList(), "not a url"))
        assertEquals(AddExtraUrlResult.Invalid, ExtraUrlsList.add(emptyList(), ""))
    }

    @Test
    fun `Given URL already in list or known when adding then result is Duplicate`() {
        val current = listOf("https://a.example.com/")

        assertEquals(AddExtraUrlResult.Duplicate, ExtraUrlsList.add(current, "https://a.example.com"))
        assertEquals(
            AddExtraUrlResult.Duplicate,
            ExtraUrlsList.add(current, "https://external.example.com", known = listOf("https://external.example.com/")),
        )
    }

    @Test
    fun `Given known URL stored as typed by the user when adding the same URL then result is Duplicate`() {
        // The internal URL is stored verbatim, without the trailing slash of the normalized form.
        assertEquals(
            AddExtraUrlResult.Duplicate,
            ExtraUrlsList.add(emptyList(), "https://ha.local:8123/", known = listOf("https://ha.local:8123")),
        )
        assertEquals(
            AddExtraUrlResult.Duplicate,
            ExtraUrlsList.add(emptyList(), "https://ha.local:8123", known = listOf("https://ha.local:8123/lovelace")),
        )
    }

    @Test
    fun `Given invalid known URL when adding then it is ignored`() {
        assertEquals(
            AddExtraUrlResult.Added(listOf("https://a.example.com/")),
            ExtraUrlsList.add(emptyList(), "https://a.example.com", known = listOf("not a url", "")),
        )
    }

    @Test
    fun `Given list when removing then only that entry is removed`() {
        assertEquals(listOf("a", "c"), ExtraUrlsList.remove(listOf("a", "b", "c"), 1))
        assertEquals(listOf("a", "b"), ExtraUrlsList.remove(listOf("a", "b"), 5))
    }

    @Test
    fun `Given list when moving then order changes within bounds only`() {
        val list = listOf("a", "b", "c")

        assertEquals(listOf("b", "a", "c"), ExtraUrlsList.move(list, 1, -1))
        assertEquals(listOf("a", "c", "b"), ExtraUrlsList.move(list, 1, 1))
        assertTrue(ExtraUrlsList.move(list, 0, -1) === list)
        assertTrue(ExtraUrlsList.move(list, 2, 1) === list)
    }
}
