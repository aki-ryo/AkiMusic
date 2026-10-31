package com.akiryo.akimusic.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.akiryo.akimusic.data.Library
import com.akiryo.akimusic.data.PlayerHub
import com.akiryo.akimusic.data.Song

private sealed interface Overlay {
    data object Player : Overlay
    data object Lyrics : Overlay
    data object Profile : Overlay
    data class PlaylistDetail(val id: Long) : Overlay
}

@Composable
fun AkiApp(dark: Boolean, onToggleDark: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.Home) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    val context = LocalContext.current

    BackHandler(enabled = overlay != null) { overlay = null }

    LaunchedEffect(PlayerHub.message) {
        PlayerHub.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            PlayerHub.message = null
        }
    }

    Box(Modifier.fillMaxSize().background(LocalPalette.current.background)) {
        Scaffold(
            containerColor = LocalPalette.current.background,
            bottomBar = {
                Column {
                    MiniPlayer { overlay = Overlay.Player }
                    BottomBar(tab) { tab = it }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    Tab.Home -> HomeScreen(
                        onOpenTab = { tab = it },
                        onOpenList = { list, index ->
                            PlayerHub.playAll(list, index)
                            overlay = Overlay.Player
                        },
                        onProfile = { overlay = Overlay.Profile },
                        onOpenChart = { overlay = Overlay.PlaylistDetail(it) },
                        onOpenPlaylist = { overlay = Overlay.PlaylistDetail(it) },
                    )
                    Tab.New -> NewScreen(
                        onOpenTab = { tab = it },
                        onOpenList = { list, index ->
                            PlayerHub.playAll(list, index)
                            overlay = Overlay.Player
                        },
                        onOpenPlaylist = { overlay = Overlay.PlaylistDetail(it) },
                        onOpenChart = { overlay = Overlay.PlaylistDetail(it) },
                    )
                    Tab.Radio -> RadioScreen(
                        onOpenList = { list, index ->
                            PlayerHub.playAll(list, index)
                            overlay = Overlay.Player
                        },
                        onOpenChart = { overlay = Overlay.PlaylistDetail(it) },
                    )
                    Tab.Library -> LibraryScreen { list, index ->
                        PlayerHub.playAll(list, index)
                        overlay = Overlay.Player
                    }
                    Tab.Search -> SearchScreen { list, index ->
                        PlayerHub.playAll(list, index)
                        overlay = Overlay.Player
                    }
                }
            }
        }

        when (val o = overlay) {
            Overlay.Player -> PlayerScreen(
                onClose = { overlay = null },
                onOpenLyrics = { overlay = Overlay.Lyrics },
            )
            Overlay.Lyrics -> LyricsScreen(onClose = { overlay = Overlay.Player })
            Overlay.Profile -> ProfileScreen(
                dark = dark,
                onToggleDark = onToggleDark,
                onClose = { overlay = null },
            )
            is Overlay.PlaylistDetail -> PlaylistScreen(
                id = o.id,
                onClose = { overlay = null },
                onPlay = { list, index ->
                    PlayerHub.playAll(list, index)
                    overlay = Overlay.Player
                },
            )
            null -> Unit
        }
    }
}
