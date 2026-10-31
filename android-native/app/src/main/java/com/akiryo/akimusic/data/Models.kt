package com.akiryo.akimusic.data

import org.json.JSONArray
import org.json.JSONObject

data class Song(
    val id: Long,
    val name: String,
    val artist: String,
    val album: String,
    val cover: String,
    val duration: Long,
    val vip: Boolean,
)

data class Playlist(
    val id: Long,
    val name: String,
    val cover: String,
    val creator: String = "",
    val description: String = "",
    val playCount: Long = 0,
    val trackCount: Int = 0,
)

data class Chart(
    val id: Long,
    val name: String,
    val cover: String,
    val updateFrequency: String = "",
    val trackCount: Int = 0,
)

private fun JSONObject.str(vararg keys: String): String {
    for (k in keys) {
        if (!has(k) || isNull(k)) continue
        val v = opt(k)
        if (v is String && v.isNotBlank() && v != "null") return v
    }
    return ""
}

private fun JSONObject.num(vararg keys: String): Long {
    for (k in keys) {
        if (!has(k) || isNull(k)) continue
        val v = opt(k)
        when (v) {
            is Number -> return v.toLong()
            is String -> v.toLongOrNull()?.let { return it }
        }
    }
    return 0L
}

private fun JSONArray?.firstArtist(): String {
    if (this == null || length() == 0) return ""
    val o = optJSONObject(0) ?: return ""
    return o.str("name")
}

/** 兼容歌单/搜索两种返回结构 */
fun JSONObject.toSong(): Song? {
    val id = num("id")
    if (id == 0L) return null
    val artists = optJSONArray("ar") ?: optJSONArray("artists")
    val albumObj = optJSONObject("al") ?: optJSONObject("album")
    val cover = (albumObj?.str("picUrl") ?: str("picUrl"))
        .replaceFirst("http://", "https://")
    val fee = num("fee")
    return Song(
        id = id,
        name = str("name"),
        artist = artists.firstArtist(),
        album = albumObj?.str("name") ?: "",
        cover = cover,
        duration = if (has("dt")) num("dt") else num("duration"),
        vip = fee == 1L || fee == 4L,
    )
}

fun JSONArray.toSongs(): List<Song> = buildList {
    for (i in 0 until length()) {
        optJSONObject(i)?.toSong()?.let { add(it) }
    }
}
