package com.akiryo.akimusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akiryo.akimusic.data.Library
import com.akiryo.akimusic.data.NetEase
import com.akiryo.akimusic.data.Playlist
import com.akiryo.akimusic.data.PlayerHub
import com.akiryo.akimusic.data.Song

@Composable
fun PlaylistScreen(
    id: Long,
    onClose: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
) {
    val palette = LocalPalette.current
    var info by remember { mutableStateOf<Playlist?>(null) }
    var tracks by remember { mutableStateOf(emptyList<Song>()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(id) {
        loading = true
        NetEase.playlist(id)?.let { (p, list) ->
            info = p
            tracks = list
        }
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "返回",
                tint = palette.textPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onClose() },
            )
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(info?.cover ?: "", Modifier.size(112.dp), radius = 14)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 14.dp)
                    ) {
                        Text(
                            info?.name ?: "歌单",
                            style = MaterialTheme.typography.headlineMedium,
                            color = palette.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${info?.creator ?: ""} · ${tracks.size} 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(
                        Modifier
                            .background(Accent, RoundedCornerShape(50))
                            .clickable { PlayerHub.playAll(tracks, 0) }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("播放", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(
                        Modifier
                            .background(palette.separator, RoundedCornerShape(50))
                            .clickable {
                                PlayerHub.playAll(tracks.shuffled(), 0)
                            }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("随机播放", style = MaterialTheme.typography.labelLarge, color = palette.textPrimary)
                    }
                }
            }
            itemsIndexed(tracks) { i, song ->
                SongRow(
                    song = song,
                    index = i + 1,
                    isPlaying = PlayerHub.current?.id == song.id,
                    onClick = { onPlay(tracks, i) },
                    onLike = { Library.toggleLike(song) },
                )
            }
            item { Spacer(Modifier.size(30.dp)) }
        }
    }
}
