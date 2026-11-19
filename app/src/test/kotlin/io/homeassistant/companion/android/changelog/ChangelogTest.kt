package io.homeassistant.companion.android.changelog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChangelogTest {

    private fun entry() = ChangelogEntry(android.R.string.ok, setOf(ChangelogPlatform.APP))

    @Test
    fun `Given entries in every category when converting to sections then they are in display order`() {
        val changelog = Changelog(
            new = listOf(entry()),
            improved = listOf(entry()),
            fixed = listOf(entry()),
        )

        assertEquals(
            listOf(ChangelogCategory.NEW, ChangelogCategory.IMPROVED, ChangelogCategory.FIXED),
            changelog.toSections().map { it.category },
        )
    }

    @Test
    fun `Given empty categories when converting to sections then they are omitted`() {
        val changelog = Changelog(improved = listOf(entry()))

        assertEquals(
            listOf(ChangelogSection(ChangelogCategory.IMPROVED, changelog.improved)),
            changelog.toSections(),
        )
    }
}
