package com.akiryo.akimusic.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akiryo.akimusic.data.Library
import com.akiryo.akimusic.data.LyricLine
import com.akiryo.akimusic.data.NetEase
import com.akiryo.akimusic.data.PlayerHub

/* ---------------- 正在播放 ---------------- */

@Composable
fun PlayerScreen(onClose: () -> Unit, onOpenLyrics: () -> Unit) {
    val song = PlayerHub.current ?: return
    val palette = LocalPalette.current
    var lyrics by remember(song.id) { mutableStateOf(emptyList<LyricLine>()) }
    LaunchedEffect(song.id) { lyrics = NetEase.lyric(song.id) }
    val activeIndex = lyrics.indexOfLast { it.timeMs <= PlayerHub.position }.coerceAtLeast(0)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = song.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.45f to Color.Black.copy(alpha = 0.65f),
                        1f to Color.Black.copy(alpha = 0.88f),
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // 顶部
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "收起",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onClose() },
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "正在播放",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                    Text(
                        "网易云音乐",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.35f),
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.QueueMusic,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            // 封面
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                AsyncImage(
                    model = song.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                )
            }

            // 标题
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                val liked = Library.isLiked(song.id)
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (liked) Accent else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { Library.toggleLike(song) },
                )
            }

            // 进度
            Column(Modifier.padding(top = 18.dp)) {
                Slider(
                    value = if (PlayerHub.duration > 0) PlayerHub.position.toFloat() / PlayerHub.duration else 0f,
                    onValueChange = { PlayerHub.seekTo((it * PlayerHub.duration).toLong()) },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                    ),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        formatDuration(PlayerHub.position.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatDuration(PlayerHub.duration.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }

            // 控制
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(22.dp))
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "上一首",
                    tint = Color.White,
                    modifier = Modifier
                        .size(34.dp)
                        .clickable { PlayerHub.prev() },
                )
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { PlayerHub.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (PlayerHub.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = Color.Black,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "下一首",
                    tint = Color.White,
                    modifier = Modifier
                        .size(34.dp)
                        .clickable { PlayerHub.next() },
                )
                Icon(Icons.Filled.Repeat, contentDescription = null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(22.dp))
            }

            // 歌词预览
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp)
                    .clickable { onOpenLyrics() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val current = lyrics.getOrNull(activeIndex)?.text ?: "纯音乐，请欣赏"
                val next = lyrics.getOrNull(activeIndex + 1)?.text ?: ""
                Text(
                    current,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (next.isNotEmpty()) {
                    Text(
                        next,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/* ---------------- 歌词全屏 ---------------- */

@Composable
fun LyricsScreen(onClose: () -> Unit) {
    val song = PlayerHub.current ?: return
    var lyrics by remember(song.id) { mutableStateOf(emptyList<LyricLine>()) }
    LaunchedEffect(song.id) { lyrics = NetEase.lyric(song.id) }
    val activeIndex = lyrics.indexOfLast { it.timeMs <= PlayerHub.position }.coerceAtLeast(0)
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (lyrics.isNotEmpty()) {
            listState.animateScrollToItem(activeIndex.coerceIn(0, lyrics.lastIndex))
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = song.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "收起",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onClose() },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    song.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(28.dp))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 260.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(lyrics) { i, line ->
                    val active = i == activeIndex
                    val alpha by animateFloatAsState(
                        if (active) 1f else 0.45f,
                        tween(280),
                        label = "lyrAlpha",
                    )
                    Text(
                        line.text,
                        style = if (active) {
                            MaterialTheme.typography.headlineMedium.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp)
                        },
                        color = Color.White.copy(alpha = alpha),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 9.dp)
                            // 非当前行做模糊，越远越虚
                            .then(if (active) Modifier else Modifier.blur(2.4.dp)),
                    )
                }
            }
        }
    }
}
