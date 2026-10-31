package com.akiryo.akimusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import com.akiryo.akimusic.data.Library
import com.akiryo.akimusic.ui.AkiApp
import com.akiryo.akimusic.ui.AkiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Library.init(applicationContext)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var dark by remember { mutableStateOf(systemDark) }
            AkiTheme(dark = dark) {
                AkiApp(dark = dark, onToggleDark = { dark = !dark })
            }
        }
    }
}
