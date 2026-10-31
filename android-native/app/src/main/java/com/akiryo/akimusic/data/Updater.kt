package com.akiryo.akimusic.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 检查更新：访问 GitHub Release API，识别最新版本号并给出下载地址。
 */
object Updater {

    private const val REPO = "aki-ryo/AkiMusic"
    private const val CURRENT = "0.1.3-beta"

    data class ReleaseInfo(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val publishedAt: String,
    )

    suspend fun check(): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/$REPO/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "AkiMusic")
            }
            try {
                val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val obj = JSONObject(body)
                val tag = obj.optString("tag_name", "")
                val version = tag.removePrefix("v")
                val assets = obj.optJSONArray("assets") ?: return@runCatching null
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i)
                    val name = a.optString("name")
                    if (name.endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url")
                        break
                    }
                }
                ReleaseInfo(
                    version = version,
                    notes = obj.optString("body", ""),
                    apkUrl = apkUrl ?: return@runCatching null,
                    publishedAt = obj.optString("published_at", ""),
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    fun isNewer(remote: String): Boolean {
        fun parse(v: String): List<Int> =
            v.replace("-beta", "").replace("-alpha", "").split(".")
                .mapNotNull { it.toIntOrNull() }
        val cur = parse(CURRENT)
        val rem = parse(remote)
        val n = maxOf(cur.size, rem.size)
        for (i in 0 until n) {
            val a = cur.getOrElse(i) { 0 }
            val b = rem.getOrElse(i) { 0 }
            if (b > a) return true
            if (b < a) return false
        }
        return false
    }

    fun openDownload(context: Context, url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
