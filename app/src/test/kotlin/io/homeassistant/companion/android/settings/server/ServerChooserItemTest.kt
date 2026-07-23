package io.homeassistant.companion.android.settings.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ServerChooserItemTest {

    @ParameterizedTest(name = "userName=\"{0}\", serverName=\"{1}\" -> \"{2}\"")
    @CsvSource(
        // userName, serverName, expected initials. Single quotes preserve spaces / empty values.
        "Alice Smith,        Home,          AS", // first letter of the first two words
        "John Ronald Reuel,  Home,          JR", // capped at two words
        "Bob,                Home,          B", // a single word yields a single initial
        "alice,              Home,          A", // lower-case input is uppercased
        "'Alice  Smith',     Home,          AS", // extra inner whitespace is ignored
        "' Bob',             Home,          B", // leading whitespace is ignored
        "'',                 Friends home,  FH", // blank user name falls back to the server name
        "'   ',              Home,          H", // blank user name falls back to the server name
        "'',                 '',            ?", // nothing usable falls back to a placeholder
    )
    fun `Given a user and server name when reading initials then up to two uppercase initials are derived`(
        userName: String,
        serverName: String,
        expected: String,
    ) {
        val item = ServerChooserItem(serverId = 1, userName = userName, serverName = serverName)

        assertEquals(expected, item.initials)
    }
}
