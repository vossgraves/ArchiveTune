package moe.rukamori.archivetune.paxsenix

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.paxsenix.models.PaxsenixLyricsResponse

object PaxsenixLyrics {
    private const val API_KEY = "Sk-paxsenix-Cd3wtTnii7rZYR_vFUbsNuY408zwRUh079PDLhVgQI2LdDPr"
    private const val DIRECT_ENDPOINT = "https://api.paxsenix.biz.id/lyrics"

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = runCatching {
        val response = client.get(DIRECT_ENDPOINT) {
            header("Authorization", "Bearer $API_KEY")
            parameter("title", title)
            parameter("artist", artist)
            parameter("duration", duration)
        }.body<PaxsenixLyricsResponse>()

        response.lyrics ?: throw Exception("No lyrics found")
    }

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(title, artist, duration).onSuccess {
            callback(it)
        }
    }
}
