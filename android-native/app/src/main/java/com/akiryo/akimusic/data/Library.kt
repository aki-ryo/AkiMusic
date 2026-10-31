package com.akiryo.akimusic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** 本地资料库：我喜欢的音乐、最近播放。 */
object Library {

    private lateinit var prefs: SharedPreferences

    var liked by mutableStateOf<List<Song>>(emptyList()); private set
    var recent by mutableStateOf<List<Song>>(emptyList()); private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("akimusic", Context.MODE_PRIVATE)
        liked = read("liked")
        recent = read("recent")
    }

    fun isLiked(id: Long) = liked.any { it.id == id }

    fun toggleLike(song: Song) {
        liked = if (isLiked(song.id)) liked.filterNot { it.id == song.id } else listOf(song) + liked
        write("liked", liked)
    }

    fun remember(song: Song) {
        recent = (listOf(song) + recent.filterNot { it.id == song.id }).take(50)
        write("recent", recent)
    }

    private fun read(key: String): List<Song> = runCatching {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    Song(
                        id = o.optLong("id"),
                        name = o.optString("name"),
                        artist = o.optString("artist"),
                        album = o.optString("album"),
                        cover = o.optString("cover"),
                        duration = o.optLong("duration"),
                        vip = o.optBoolean("vip"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun write(key: String, list: List<Song>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("artist", it.artist)
                    .put("album", it.album)
                    .put("cover", it.cover)
                    .put("duration", it.duration)
                    .put("vip", it.vip)
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
