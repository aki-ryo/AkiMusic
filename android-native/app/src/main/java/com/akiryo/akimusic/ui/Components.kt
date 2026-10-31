package com.akiryo.akimusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akiryo.akimusic.data.Playlist
import com.akiryo.akimusic.data.Song

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

fun formatCount(n: Long): String = when {
    n >= 100_000_000 -> "%.1f亿".format(n / 100_000_000.0)
    n >= 10_000 -> "%.1f万".format(n / 10_000.0)
    else -> "$n"
}

@Composable
fun Cover(
    url: String,
    modifier: Modifier = Modifier,
    radius: Int = 12,
    placeholderTint: Color? = null,
) {
    val palette = LocalPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(radius.dp))
            .background(placeholderTint ?: palette.separator)
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    onMore: (() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 26.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (onMore != null) {
            Row(
                Modifier.clickable { onMore() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("全部", style = MaterialTheme.typography.labelLarge, color = Accent)
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(start = 1.dp),
                )
            }
        }
    }
}

/** 首页大卡：整张封面做底，压一层渐变，文字落在左下角 */
@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    cover: String,
    badge: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.05f),
                        0.55f to Color.Black.copy(alpha = 0.25f),
                        1f to Color.Black.copy(alpha = 0.72f),
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            if (badge != null) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
fun AlbumCard(
    title: String,
    subtitle: String,
    cover: String,
    width: Int = 150,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .width(width.dp)
            .clickable { onClick() }
    ) {
        Cover(cover, Modifier.fillMaxWidth().aspectRatio(1f), radius = 12)
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

@Composable
fun SongRow(
    song: Song,
    index: Int,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLike: () -> Unit,
) {
    val palette = LocalPalette.current
    val liked = com.akiryo.akimusic.data.Library.isLiked(song.id)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPlaying) {
            Icon(
                Icons.Filled.Equalizer,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier
                    .width(22.dp)
                    .height(16.dp),
            )
        } else {
            Text(
                "$index",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textTertiary,
                modifier = Modifier.width(22.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Cover(song.cover, Modifier.size(44.dp), radius = 7)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 11.dp)
        ) {
            Text(
                song.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (isPlaying) Accent else palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song.vip) {
                    Text(
                        "VIP",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD8A200),
                        fontSize = 9.sp,
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .background(Color(0x1AD8A200), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Icon(
            imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = null,
            tint = if (liked) Accent else palette.textTertiary,
            modifier = Modifier
                .size(20.dp)
                .clickable { onLike() },
        )
        Spacer(Modifier.width(14.dp))
        Text(
            formatDuration(song.duration),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textTertiary,
        )
    }
}

@Composable
fun PlaylistGridCard(p: Playlist, onClick: () -> Unit) {
    AlbumCard(
        title = p.name,
        subtitle = if (p.playCount > 0) formatCount(p.playCount) + " 次播放" else p.creator,
        cover = p.cover,
        onClick = onClick,
    )
}
