package moe.rukamori.archivetune.lastfm.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class UserInfoTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `decodes profile and long playcount from user getInfo envelope`() {
        val response =
            json.decodeFromString<UserInfoResponse>(
                """
                {
                  "user": {
                    "name": "archive-tune",
                    "url": "https://www.last.fm/user/archive-tune",
                    "playcount": "2147483648"
                  }
                }
                """.trimIndent(),
            )

        assertEquals("archive-tune", response.user.name)
        assertEquals("https://www.last.fm/user/archive-tune", response.user.url)
        assertEquals(2_147_483_648L, response.user.playcount)
    }
}
