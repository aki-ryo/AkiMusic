package com.akiryo.akimusic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 网易云音乐账号登录（手机号 + 短信验证码）。
 *
 * 网易云现在的登录接口走 weapi 加密（AES + RSA），且二维码登录已被风控限制。
 * 短信验证码登录相对稳定：先发验证码，再凭手机号+验证码登录，拿到 MUSIC_U cookie。
 */
object Auth {

    private const val BASE = "https://music.163.com"
    private const val UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"

    data class UserInfo(
        val nickname: String,
        val avatarUrl: String,
        val vipType: Int,      // 0 无 / 11 黑胶
        val level: Int,        // 网易云等级
        val listenSongs: Long, // 累计听歌数
    )

    var user by mutableStateOf<UserInfo?>(null); private set
    var loggedIn by mutableStateOf(false); private set

    private lateinit var prefs: SharedPreferences
    private var cookie = ""

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("akimusic", Context.MODE_PRIVATE)
        cookie = prefs.getString("netease_cookie", "") ?: ""
        val saved = prefs.getString("netease_user", null)
        if (saved != null) {
            runCatching {
                val o = JSONObject(saved)
                user = UserInfo(
                    nickname = o.optString("nickname"),
                    avatarUrl = o.optString("avatar"),
                    vipType = o.optInt("vip"),
                    level = o.optInt("level"),
                    listenSongs = o.optLong("listen"),
                )
                loggedIn = true
            }
        }
    }

    /** weapi POST：把 JSON 加密后 POST 到 /weapi/... */
    private suspend fun weapi(path: String, json: JSONObject): JSONObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val (params, encSecKey) = WeapiCrypto.encrypt(json.toString())
                val body = "params=${URLEncoder.encode(params, "UTF-8")}&encSecKey=${URLEncoder.encode(encSecKey, "UTF-8")}"
                val conn = (URL("$BASE$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("Referer", "https://music.163.com")
                    setRequestProperty("User-Agent", UA)
                    setRequestProperty("Cookie", "os=pc; appver=2.10.13; MUSIC_U=; __csrf=")
                }
                try {
                    DataOutputStream(conn.outputStream).use { it.write(body.toByteArray()) }
                    conn.inputStream.bufferedReader().use(BufferedReader::readText).let { JSONObject(it) }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }

    /** 发送短信验证码 */
    suspend fun sendCaptcha(phone: String): Boolean {
        val res = weapi("/weapi/sms/captcha/sent", JSONObject()
            .put("phone", phone)
            .put("ctcode", "86")
            .put("csrf_token", "")) ?: return false
        return res.optInt("code", -1) == 200
    }

    /** 验证码登录 */
    suspend fun loginByCaptcha(phone: String, captcha: String): Boolean {
        val res = weapi("/weapi/login/cellphone", JSONObject()
            .put("phone", phone)
            .put("captcha", captcha)
            .put("ctcode", "86")
            .put("countrycode", "86")
            .put("csrf_token", "")) ?: return false
        if (res.optInt("code", -1) != 200) return false
        val cookieStr = res.optString("cookie", "")
        if (cookieStr.isNotBlank()) saveCookie(cookieStr)
        val profile = res.optJSONObject("profile") ?: res.optJSONObject("account") ?: res
        val nickname = profile.optString("nickname", "")
        if (nickname.isNotBlank()) {
            user = UserInfo(
                nickname = nickname,
                avatarUrl = profile.optString("avatarUrl", ""),
                vipType = profile.optInt("vipType", 0),
                level = 0,
                listenSongs = 0,
            )
            loggedIn = true
            persistUser()
        }
        loadUserInfo()
        return true
    }

    /** 用登录 cookie 拉取用户详情（等级、听歌数） */
    suspend fun loadUserInfo() {
        if (cookie.isBlank()) return
        val res = weapi("/weapi/v1/user/detail", JSONObject().put("csrf_token", "")) ?: return
        if (res.optInt("code", -1) != 200) return
        val profile = res.optJSONObject("profile") ?: return
        val nickname = profile.optString("nickname")
        val avatar = profile.optString("avatarUrl")
        val vipType = profile.optInt("vipType", 0)
        val level = res.optInt("level", 0)
        val listenSongs = res.optLong("listenSongs", 0)
        user = UserInfo(nickname, avatar, vipType, level, listenSongs)
        loggedIn = true
        persistUser()
    }

    private fun persistUser() {
        val u = user ?: return
        prefs.edit().putString("netease_user", JSONObject()
            .put("nickname", u.nickname)
            .put("avatar", u.avatarUrl)
            .put("vip", u.vipType)
            .put("level", u.level)
            .put("listen", u.listenSongs)
            .toString()).apply()
    }

    private fun saveCookie(c: String) {
        cookie = c
        prefs.edit().putString("netease_cookie", c).apply()
    }

    fun logout() {
        cookie = ""
        user = null
        loggedIn = false
        prefs.edit().remove("netease_cookie").remove("netease_user").apply()
    }
}
