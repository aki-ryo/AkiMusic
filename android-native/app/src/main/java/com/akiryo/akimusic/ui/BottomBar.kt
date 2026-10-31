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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akiryo.akimusic.data.PlayerHub

/** 四个主标签（已删除广播页） */
enum class Tab(val label: String, val icon: ImageVector) {
    Home("主页", Icons.Filled.Home),
    New("新发现", Icons.Filled.Widgets),
    Library("资料库", Icons.Filled.LibraryMusic),
    Search("搜索", Icons.Filled.Search),
}

/**
 * 底部标签栏：大圆角悬浮胶囊，与屏幕边缘留白，参考 iOS 27 的悬浮底栏。
 */
@Composable
fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 悬浮玻璃胶囊
        Column(
            Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            glassTop(palette),
                            glassBottom(palette),
                        )
                    )
                )
                .shadow(16.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = 0.28f), spotColor = Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 6.dp, vertical = 7.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tab.entries.forEach { tab ->
                    val active = tab == selected
                    val tint by animateColorAsState(
                        if (active) Accent else palette.textSecondary,
                        label = "tabTint",
                    )
                    val scale by animateDpAsState(
                        if (active) 23.dp else 22.dp,
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
                                .width(52.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) Accent.copy(alpha = 0.14f) else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(scale),
                            )
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
}

@Composable
private fun glassTop(palette: Palette) =
    if (palette.isDark) Color(0xFF22222A).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.9f)

@Composable
private fun glassBottom(palette: Palette) =
    if (palette.isDark) Color(0xFF15151B).copy(alpha = 0.96f) else Color(0xFFF5F5F7).copy(alpha = 0.94f)

/** 迷你播放条：悬浮玻璃条，贴着标签栏上方 */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val palette = LocalPalette.current
    val song = PlayerHub.current ?: return
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            glassTop(palette),
                            glassBottom(palette),
                        )
                    )
                )
                .shadow(12.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.22f))
                .clickable { onOpen() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(song.cover, Modifier.size(40.dp), radius = 8)
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
