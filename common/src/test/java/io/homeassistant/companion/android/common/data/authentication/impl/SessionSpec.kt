package io.homeassistant.companion.android.common.data.authentication.impl

import org.assertj.core.api.Assertions.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SessionSpec : Spek({

    describe("an expired session") {
        val session by memoized { Session("", System.currentTimeMillis() / 1000 - 1800, "", "") }

        it("should be expired") {
            assertThat(session.isExpired()).isTrue()
        }
    }

    describe("an valid session") {
        val session by memoized { Session("", System.currentTimeMillis() / 1000 + 1800, "", "") }

        it("should be valid") {
            assertThat(session.isExpired()).isFalse()
        }

        it("should be valid") {
            assertThat(session.expiresIn()).isEqualTo(1800)
        }
    }
})
