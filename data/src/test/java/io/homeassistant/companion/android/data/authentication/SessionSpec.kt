package io.homeassistant.companion.android.data.authentication

import org.assertj.core.api.Assertions.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.threeten.bp.Instant

object SessionSpec : Spek({

    describe("an expired session") {
        val session by memoized { Session("", Instant.now().epochSecond - 1800, "", "") }

        it("should be expired") {
            assertThat(session.isExpired()).isTrue()
        }
    }

    describe("an valid session") {
        val session by memoized { Session("", Instant.now().epochSecond + 1800, "", "") }

        it("should be valid") {
            assertThat(session.isExpired()).isFalse()
        }

        it("should be valid") {
            assertThat(session.expiresIn()).isEqualTo(1800)
        }
    }
})
