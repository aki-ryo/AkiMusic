package com.akiryo.akimusic.data

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 全局播放器：MediaPlayer + Compose 状态。 */
object PlayerHub {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mp: MediaPlayer? = null
    private var ticker: Job? = null
    private var token = 0

    var current by mutableStateOf<Song?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var queue by mutableStateOf<List<Song>>(emptyList()); private set
    var index by mutableStateOf(-1); private set
    var position by mutableStateOf(0); private set
    var duration by mutableStateOf(0); private set
    var volume by mutableStateOf(1f); private set
    var message by mutableStateOf<String?>(null)

    fun play(song: Song, list: List<Song> = listOf(song)) {
        queue = list.ifEmpty { listOf(song) }
        index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        open(song, autoSkip = 0)
    }

    fun playAll(list: List<Song>, startAt: Int = 0) {
        if (list.isEmpty()) return
        queue = list
        index = startAt.coerceIn(list.indices)
        open(list[index], 0)
    }

    fun toggle() {
        val p = mp ?: return
        if (p.isPlaying) p.pause() else p.start()
        isPlaying = p.isPlaying
    }

    fun next(fromUser: Boolean = true) = step(+1, if (fromUser) 0 else 1)

    fun prev() = step(-1, 1)

    private fun step(dir: Int, autoSkip: Int) {
        if (queue.isEmpty()) return
        var i = index + dir
        if (i !in queue.indices) i = if (dir > 0) 0 else queue.lastIndex
        index = i
        open(queue[i], autoSkip)
    }

    fun changeVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        mp?.setVolume(volume, volume)
    }

    fun seekTo(ms: Long) {
        mp?.let {
            runCatching { it.seekTo(ms.toInt()) }
            position = ms.toInt()
        }
    }

    fun stop() {
        mp?.release()
        mp = null
        isPlaying = false
        position = 0
    }

    private fun open(song: Song, autoSkip: Int) {
        val my = ++token
        current = song
        duration = song.duration.toInt()
        position = 0
        isPlaying = false
        mp?.release()
        mp = null

        scope.launch {
            val url = NetEase.songUrl(song.id)
            if (my != token) return@launch
            if (url == null) {
                message = "「${song.name}」没有可播放音源，已跳过"
                if (autoSkip < 4) step(+1, autoSkip + 1)
                return@launch
            }
            val player = MediaPlayer()
            mp = player
            runCatching {
                player.setDataSource(url)
                player.setOnPreparedListener {
                    if (my != token) return@setOnPreparedListener
                    it.start()
                    isPlaying = true
                    duration = it.duration
                    Library.remember(song)
                    startTicker()
                }
                player.setOnCompletionListener {
                    if (my == token) step(+1, 0)
                }
                player.setOnErrorListener { _, _, _ ->
                    if (my == token) {
                        message = "播放失败，已跳到下一首"
                        step(+1, autoSkip + 1)
                    }
                    true
                }
                player.prepareAsync()
            }.onFailure {
                message = "音源加载失败"
                if (autoSkip < 4) step(+1, autoSkip + 1)
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                mp?.let {
                    runCatching {
                        if (it.isPlaying) {
                            position = it.currentPosition
                            isPlaying = true
                        }
                    }
                }
                delay(400)
            }
        }
    }
}
