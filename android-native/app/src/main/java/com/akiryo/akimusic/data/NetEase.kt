package com.akiryo.akimusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 网易云音乐公开接口。Android 直连即可（没有 CORS 限制），
 * 需要补 Referer / UA，否则会被拒。
 */
object NetEase {

    private const val BASE = "https://music.163.com"
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val query = if (params.isEmpty()) "" else params.entries.joinToString("&") {
                "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
            }
            val url = URL(if (query.isEmpty()) "$BASE$path" else "$BASE$path?$query")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("Referer", "https://music.163.com/")
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Cookie", "appver=8.9.70; os=android")
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
            try {
                val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                JSONObject(body)
            } finally {
                conn.disconnect()
            }
        }

    suspend fun charts(): List<Chart> = runCatching {
        val arr = get("/api/toplist/detail").optJSONArray("list") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    Chart(
                        id = o.optLong("id"),
                        name = o.optString("name"),
                        cover = o.optString("coverImgUrl"),
                        updateFrequency = o.optString("updateFrequency"),
                        trackCount = o.optInt("trackCount"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    suspend fun newSongs(limit: Int = 12): List<Song> = runCatching {
        val arr = get("/api/personalized/newsong", mapOf("limit" to "$limit"))
            .optJSONArray("result") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val song = o.optJSONObject("song") ?: o
                val outer = o.optString("picUrl").replaceFirst("http://", "https://")
                song.toSong()?.let { add(it.copy(cover = it.cover.ifBlank { outer })) }
            }
        }
    }.getOrDefault(emptyList())

    suspend fun recommendPlaylists(limit: Int = 12): List<Playlist> = runCatching {
        val arr = get("/api/personalized/playlist", mapOf("limit" to "$limit"))
            .optJSONArray("result") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    Playlist(
                        id = o.optLong("id"),
                        name = o.optString("name"),
                        cover = o.optString("picUrl").ifBlank { o.optString("coverImgUrl") },
                        playCount = o.optLong("playCount"),
                        creator = o.optString("copywriter"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    suspend fun playlist(id: Long): Pair<Playlist, List<Song>>? = runCatching {
        val p = get("/api/v6/playlist/detail", mapOf("id" to "$id", "n" to "1000"))
            .optJSONObject("playlist") ?: return null
        val info = Playlist(
            id = p.optLong("id"),
            name = p.optString("name"),
            cover = p.optString("coverImgUrl"),
            creator = p.optJSONObject("creator")?.optString("nickname") ?: "",
            description = p.optString("description"),
            playCount = p.optLong("playCount"),
            trackCount = p.optInt("trackCount"),
        )
        val tracks = p.optJSONArray("tracks")?.toSongs() ?: emptyList()
        info to tracks
    }.getOrNull()

    suspend fun search(keyword: String, limit: Int = 30): List<Song> = runCatching {
        val res = get(
            "/api/search/get/web",
            mapOf("s" to keyword, "type" to "1", "limit" to "$limit", "offset" to "0")
        ).optJSONObject("result") ?: return emptyList()
        res.optJSONArray("songs")?.toSongs() ?: emptyList()
    }.getOrDefault(emptyList())

    /** 返回可播放地址；null 表示没有音源（VIP 且无试听）。 */
    suspend fun songUrl(id: Long): String? = runCatching {
        val arr = get("/api/song/enhance/player/url", mapOf("ids" to "[$id]", "br" to "320000"))
            .optJSONArray("data") ?: return null
        val o = arr.optJSONObject(0) ?: return null
        val url = o.optString("url")
        if (url.isBlank() || url == "null") null else url.replaceFirst("http://", "https://")
    }.getOrNull()

    suspend fun lyric(id: Long): List<LyricLine> = runCatching {
        val raw = get("/api/song/lyric", mapOf("id" to "$id", "kv" to "-1", "tv" to "-1"))
            .optJSONObject("lrc")?.optString("lyric") ?: return emptyList()
        parseLrc(raw)
    }.getOrDefault(emptyList())
}

data class LyricLine(val timeMs: Long, val text: String)

private val LRC_TAG = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

fun parseLrc(raw: String): List<LyricLine> {
    val out = mutableListOf<LyricLine>()
    for (line in raw.lines()) {
        val tags = LRC_TAG.findAll(line).toList()
        if (tags.isEmpty()) continue
        val text = line.substring(tags.last().range.last + 1).trim()
        if (text.isEmpty()) continue
        for (t in tags) {
            val m = t.groupValues[1].toLongOrNull() ?: continue
            val s = t.groupValues[2].toLongOrNull() ?: continue
            val frac = t.groupValues[3]
            val ms = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.take(3).toLong()
            }
            out.add(LyricLine(m * 60_000 + s * 1_000 + ms, text))
        }
    }
    return out.sortedBy { it.timeMs }
}
