/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Listen Together public server list — ported from vivi-music (beta branch),
 * vivi-music's listentogether.ListenTogetherServers (GPL-3.0), plus the
 * Metrolist project's community server (metrolistgroup/metrolist, GPL-3.0).
 */

package moe.rukamori.archivetune.listentogether

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ListenTogetherServer(
    val name: String,
    val url: String,
    val location: String,
    val operator: String
)

object ListenTogetherServers {
    private const val ServersJson = """
        [
          {
            "name": "Hugging Face Sync",
            "url": "wss://devilmi-vivi-music-listen-together.hf.space",
            "location": "Global",
            "operator": "VIVIDH"
          },
          {
            "name": "ViviMusic Sync Server",
            "url": "wss://vivimusic-listen-together.onrender.com",
            "location": "USA",
            "operator": "Vividh"
          },
          {
            "name": "The Meowery",
            "url": "wss://metroserverx.meowery.eu/ws",
            "location": "Poland",
            "operator": "Nyx"
          }
        ]
    """

    private val json = Json { ignoreUnknownKeys = true }

    val servers: List<ListenTogetherServer> by lazy {
        json.decodeFromString(ServersJson)
    }

    val defaultServerUrl: String
        get() = servers.first().url

    fun findByUrl(url: String): ListenTogetherServer? = servers.firstOrNull { it.url == url }
}
