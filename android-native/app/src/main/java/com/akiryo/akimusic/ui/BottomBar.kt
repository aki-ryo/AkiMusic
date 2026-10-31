package com.akiryo.akimusic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akiryo.akimusic.data.PlayerHub

/** 五个主标签，顺序与 Apple Music 一致 */
enum class Tab(val label: String, val icon: ImageVector) {
    Home("主页", Icons.Filled.Home),
    New("新发现", Icons.Filled.Widgets),
    Radio("广播", Icons.Filled.Radio),
    Library("资料库", Icons.Filled.LibraryMusic),
    Search("搜索", Icons.Filled.Search),
}

/** 底部标签栏：贴底不悬浮，高度 49dp + 系统手势区 */
@Composable
fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .barGlass(palette)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.7.dp)
                .background(palette.separator)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 49.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                val tint by animateColorAsState(
                    if (active) Accent else palette.textSecondary,
                    label = "tabTint",
                )
                val scale by animateDpAsState(
                    if (active) 24.dp else 23.dp,
                    spring(dampingRatio = 0.68f, stiffness = 900f),
                    label = "tabScale",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(46.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (active) Accent.copy(alpha = 0.10f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(scale))
                    }
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 迷你播放条：贴着标签栏上方，56dp */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val palette = LocalPalette.current
    val song = PlayerHub.current ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .barGlass(palette)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.7.dp)
                .background(palette.separator)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable { onOpen() }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(song.cover, Modifier.size(40.dp), radius = 6)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            ) {
                Text(
                    song.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                if (PlayerHub.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "播放/暂停",
                tint = palette.textPrimary,
                modifier = Modifier
                    .size(30.dp)
                    .clickable { PlayerHub.toggle() },
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "下一首",
                tint = palette.textPrimary,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { PlayerHub.next() },
            )
        }
    }
}
