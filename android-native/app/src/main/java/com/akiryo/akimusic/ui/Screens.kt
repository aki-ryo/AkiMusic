package com.akiryo.akimusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akiryo.akimusic.data.Chart
import com.akiryo.akimusic.data.Library
import com.akiryo.akimusic.data.NetEase
import com.akiryo.akimusic.data.Playlist
import com.akiryo.akimusic.data.PlayerHub
import com.akiryo.akimusic.data.Song

/* ---------------- 小工具 ---------------- */

@Composable
private fun <T> load(key: Any, initial: T, block: suspend () -> T): T {
    var value by remember(key) { mutableStateOf(initial) }
    LaunchedEffect(key) { value = block() }
    return value
}

@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Text(
        text,
        style = MaterialTheme.typography.displayLarge,
        color = palette.textPrimary,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
fun TopAvatar(onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .background(Accent.copy(alpha = 0.14f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text("A", style = MaterialTheme.typography.titleSmall, color = Accent)
    }
}

/* ---------------- 主页 ---------------- */

@Composable
fun HomeScreen(onOpenTab: (Tab) -> Unit, onOpenList: (List<Song>, Int) -> Unit, onProfile: () -> Unit, onOpenChart: (Long) -> Unit, onOpenPlaylist: (Long) -> Unit) {
    val palette = LocalPalette.current
    val charts = load("charts", emptyList<Chart>()) { NetEase.charts() }
    val playlists = load("playlists", emptyList<Playlist>()) { NetEase.recommendPlaylists(12) }
    val fresh = load("news", emptyList<Song>()) { NetEase.newSongs(10) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PageTitle("主页", Modifier.weight(1f))
                    TopAvatar(onProfile)
                    Spacer(Modifier.width(14.dp))
                    Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = palette.textSecondary)
                }
            }

            if (charts.isNotEmpty()) {
                item { SectionHeader("专属精选推荐") }
                item {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(charts.take(6)) { chart ->
                            FeatureCard(
                                title = chart.name,
                                subtitle = "${chart.updateFrequency} · ${chart.trackCount} 首",
                                cover = chart.cover,
                                badge = "专属推荐",
                                modifier = Modifier
                                    .width(if (wide) 320.dp else 230.dp)
                                    .height(if (wide) 400.dp else 300.dp),
                                onClick = { onOpenChart(chart.id) },
                            )
                        }
                    }
                }
            }

            if (Library.recent.isNotEmpty()) {
                item {
                    SectionHeader("最近播放", onMore = { onOpenTab(Tab.Library) })
                }
                item {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(Library.recent.take(10)) { song ->
                            AlbumCard(
                                title = song.name,
                                subtitle = song.artist,
                                cover = song.cover,
                                width = if (wide) 170 else 132,
                                onClick = { onOpenList(Library.recent, Library.recent.indexOf(song)) },
                            )
                        }
                    }
                }
            }

            if (fresh.isNotEmpty()) {
                item { SectionHeader("新歌速递") }
                itemsIndexed(fresh) { i, song ->
                    SongRow(
                        song = song,
                        index = i + 1,
                        isPlaying = PlayerHub.current?.id == song.id,
                        onClick = { onOpenList(fresh, i) },
                        onLike = { Library.toggleLike(song) },
                    )
                }
            }

            if (playlists.isNotEmpty()) {
                item { SectionHeader("为你推荐", onMore = { onOpenTab(Tab.New) }) }
                item {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(playlists) { p ->
                            PlaylistGridCard(p) { onOpenPlaylist(p.id) }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/* ---------------- 新发现 ---------------- */

@Composable
fun NewScreen(onOpenTab: (Tab) -> Unit, onOpenList: (List<Song>, Int) -> Unit, onOpenPlaylist: (Long) -> Unit, onOpenChart: (Long) -> Unit) {
    val palette = LocalPalette.current
    val playlists = load("new-playlists", emptyList<Playlist>()) { NetEase.recommendPlaylists(20) }
    val fresh = load("new-songs", emptyList<Song>()) { NetEase.newSongs(12) }
    val charts = load("new-charts", emptyList<Chart>()) { NetEase.charts() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth > 900.dp -> 4
            maxWidth > 600.dp -> 3
            else -> 2
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            item { PageTitle("新发现") }
            if (charts.isNotEmpty()) {
                item {
                    FeatureCard(
                        title = charts.first().name,
                        subtitle = "编辑精选 · ${charts.first().updateFrequency}",
                        cover = charts.first().cover,
                        badge = "本周主打",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(200.dp),
                        onClick = { onOpenChart(charts.first().id) },
                    )
                }
            }
            item { SectionHeader("精选歌单") }
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(((playlists.size + columns - 1) / columns * 210).dp.coerceAtLeast(210.dp)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    userScrollEnabled = false,
                ) {
                    items(playlists) { p -> PlaylistGridCard(p) { onOpenPlaylist(p.id) } }
                }
            }
            item { SectionHeader("新歌") }
            itemsIndexed(fresh) { i, song ->
                SongRow(
                    song = song,
                    index = i + 1,
                    isPlaying = PlayerHub.current?.id == song.id,
                    onClick = { onOpenList(fresh, i) },
                    onLike = { Library.toggleLike(song) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/* ---------------- 广播 ---------------- */

@Composable
fun RadioScreen(onOpenList: (List<Song>, Int) -> Unit, onOpenChart: (Long) -> Unit) {
    val palette = LocalPalette.current
    val charts = load("radio-charts", emptyList<Chart>()) { NetEase.charts() }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        item { PageTitle("广播") }
        item {
            Text(
                "把整张榜单当作一个电台，随机播放。",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        items(charts) { chart ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenChart(chart.id) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(chart.cover, Modifier.size(64.dp), radius = 10)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp)
                ) {
                    Text(chart.name, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    Text(
                        "${chart.updateFrequency} · ${chart.trackCount} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Accent)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/* ---------------- 资料库 ---------------- */

@Composable
fun LibraryScreen(onOpenList: (List<Song>, Int) -> Unit) {
    val palette = LocalPalette.current
    var tab by remember { mutableStateOf(0) }
    val list = if (tab == 0) Library.liked else Library.recent
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        PageTitle("资料库")
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            listOf("我喜欢的音乐" to Library.liked.size, "最近播放" to Library.recent.size)
                .forEachIndexed { i, (label, count) ->
                    val on = i == tab
                    Row(
                        Modifier
                            .padding(end = 10.dp)
                            .background(
                                if (on) palette.textPrimary else palette.separator,
                                RoundedCornerShape(50),
                            )
                            .clickable { tab = i }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "$label $count",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) palette.background else palette.textSecondary,
                        )
                    }
                }
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("这里还空着", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    Text(
                        if (tab == 0) "点歌曲右侧的心形，它就会留在这里。" else "随便听一首，这里会自动记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(list) { i, song ->
                    SongRow(
                        song = song,
                        index = i + 1,
                        isPlaying = PlayerHub.current?.id == song.id,
                        onClick = { onOpenList(list, i) },
                        onLike = { Library.toggleLike(song) },
                    )
                }
            }
        }
    }
}

/* ---------------- 搜索 ---------------- */

private val HOT = listOf("周杰伦", "告五人", "林俊杰", "陈奕迅", "五月天", "邓紫棋", "薛之谦", "李荣浩", "粤语", "轻音乐")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onOpenList: (List<Song>, Int) -> Unit) {
    val palette = LocalPalette.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Song>()) }
    var loading by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        loading = true
        kotlinx.coroutines.delay(320)
        results = NetEase.search(query)
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        PageTitle("搜索")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(palette.separator, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("歌曲、歌手、专辑", color = palette.textTertiary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary,
                ),
                modifier = Modifier.weight(1f),
            )
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "清除",
                    tint = palette.textTertiary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { query = "" },
                )
            }
        }

        if (query.isBlank()) {
            Text(
                "热门搜索",
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 10.dp),
            )
            androidx.compose.foundation.layout.FlowRow(
                Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HOT.forEach { word ->
                    Text(
                        word,
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.textPrimary,
                        modifier = Modifier
                            .background(palette.separator, RoundedCornerShape(50))
                            .clickable { query = word }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        } else if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(results) { i, song ->
                    SongRow(
                        song = song,
                        index = i + 1,
                        isPlaying = PlayerHub.current?.id == song.id,
                        onClick = { onOpenList(results, i) },
                        onLike = { Library.toggleLike(song) },
                    )
                }
            }
        }
    }
}

/* ---------------- 个人页面 ---------------- */

@Composable
fun ProfileScreen(dark: Boolean, onToggleDark: () -> Unit, onClose: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭",
                tint = palette.textPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onClose() },
            )
            Spacer(Modifier.weight(1f))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .background(Accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("A", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Accent)
            }
            Text(
                "sukiyo",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                "本地账户 · 所有数据只存在这台设备",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf("喜欢" to Library.liked.size, "最近" to Library.recent.size, "歌单" to 0)
                .forEach { (label, value) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$value", style = MaterialTheme.typography.headlineMedium, color = palette.textPrimary)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                    }
                }
        }

        SettingRow(Icons.Filled.DarkMode, "深色模式", dark) { onToggleDark() }
        SettingRow(Icons.Filled.MoreHoriz, "关于 AkiMusic", false) { }
        Spacer(Modifier.weight(1f))
        Text(
            "AkiMusic 0.1.2-beta · 数据来自网易云音乐",
            style = MaterialTheme.typography.labelMedium,
            color = palette.textTertiary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        )
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = { onClick() },
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Accent,
            ),
        )
    }
}
