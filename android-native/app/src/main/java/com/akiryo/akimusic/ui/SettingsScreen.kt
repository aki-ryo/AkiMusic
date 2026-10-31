package com.akiryo.akimusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akiryo.akimusic.data.Auth
import com.akiryo.akimusic.data.Library
import com.akiryo.akimusic.data.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ---------------- 设置 / 个人页 ---------------- */

@Composable
fun SettingsScreen(
    dark: Boolean,
    onToggleDark: () -> Unit,
    onClose: () -> Unit,
    onLogin: () -> Unit,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf("检查更新") }
    var updateUrl by remember { mutableStateOf<String?>(null) }

    val user = Auth.user

    fun doCheckUpdate() {
        if (checking) return
        checking = true
        updateState = "检查中…"
        scope.launch {
            val res = Updater.check()
            checking = false
            res.onSuccess { info ->
                if (info == null) {
                    updateState = "已是最新版本"
                } else if (Updater.isNewer(info.version)) {
                    updateState = "发现新版本 v${info.version}"
                    updateUrl = info.apkUrl
                } else {
                    updateState = "已是最新版本"
                }
            }.onFailure {
                updateState = "检查失败，点击重试"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
    ) {
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "设置",
                style = MaterialTheme.typography.titleLarge,
                color = palette.textPrimary,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭",
                tint = palette.textPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onClose() },
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 账号卡片
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .background(palette.separator, RoundedCornerShape(18.dp))
                    .clickable { onLogin() }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (user != null && user.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .background(palette.separator, CircleShape),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(56.dp)
                                .background(Accent.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("A", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Accent)
                        }
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp)
                    ) {
                        Text(
                            user?.nickname ?: "点击登录网易云音乐",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (user != null) "已登录 · 同步 VIP 权益" else "登录后可获取 VIP 曲目与听歌记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        if (user != null && user.vipType > 0) {
                            Text(
                                "VIP 黑胶会员",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFD8A200),
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .background(Color(0x1AD8A200), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.textTertiary)
                }
            }

            // 数据统计（登录后显示）
            if (user != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatCell("听歌次数", "${user.listenSongs}")
                    StatCell("网易云等级", "Lv.${user.level}")
                    StatCell("喜欢", "${Library.liked.size}")
                }
            }

            Spacer(Modifier.height(12.dp))

            // 设置项
            SettingRow(
                Icons.Filled.SystemUpdate,
                updateState,
                accent = updateUrl != null,
            ) {
                if (updateUrl != null) {
                    Updater.openDownload(context, updateUrl!!)
                } else {
                    doCheckUpdate()
                }
            }

            SettingRow(Icons.Filled.DarkMode, "深色模式", trailingSwitch = true, switchValue = dark) {
                onToggleDark()
            }

            SettingRow(Icons.Filled.Info, "关于 AkiMusic") { }

            Spacer(Modifier.weight(1f))

            Text(
                "AkiMusic 0.1.2-beta · 数据来自网易云音乐",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    val palette = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = palette.textPrimary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    accent: Boolean = false,
    trailingSwitch: Boolean = false,
    switchValue: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (accent) Accent else palette.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        )
        if (trailingSwitch) {
            Switch(
                checked = switchValue,
                onCheckedChange = { onClick() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                ),
            )
        } else {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.textTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

/* ---------------- 网易云登录（手机号 + 验证码） ---------------- */

@Composable
fun LoginScreen(onClose: () -> Unit, onLoggedIn: () -> Unit) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    fun send() {
        if (phone.length != 11 || loading) return
        loading = true
        status = "正在发送验证码…"
        scope.launch {
            val ok = Auth.sendCaptcha(phone)
            loading = false
            if (ok) {
                sent = true
                status = "验证码已发送，请查收短信"
            } else {
                status = "发送失败，请检查手机号或稍后再试"
            }
        }
    }

    fun login() {
        if (phone.length != 11 || captcha.isBlank() || loading) return
        loading = true
        status = "正在登录…"
        scope.launch {
            val ok = Auth.loginByCaptcha(phone, captcha)
            loading = false
            if (ok) {
                status = "登录成功"
                onLoggedIn()
            } else {
                status = "登录失败，请检查验证码"
            }
        }
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
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(30.dp))
            Text("登录网易云音乐", style = MaterialTheme.typography.headlineMedium, color = palette.textPrimary)
            Text(
                "登录后可解锁 VIP 曲目、同步听歌记录",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(32.dp))

            // 手机号输入
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(palette.separator, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+86", style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                Spacer(Modifier.width(10.dp))
                androidx.compose.material3.TextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() }.take(11) },
                    placeholder = { Text("手机号", color = palette.textTertiary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
            }

            Spacer(Modifier.height(12.dp))

            // 验证码输入 + 发送按钮
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(palette.separator, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.TextField(
                    value = captcha,
                    onValueChange = { captcha = it.filter { c -> c.isDigit() }.take(6) },
                    placeholder = { Text("验证码", color = palette.textTertiary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                Text(
                    if (sent) "重新发送" else "获取验证码",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (phone.length == 11) Accent else palette.textTertiary,
                    modifier = Modifier
                        .clickable { send() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }

            if (status.isNotBlank()) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            // 登录按钮
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (phone.length == 11 && captcha.isNotBlank()) Accent else palette.separator,
                        RoundedCornerShape(50),
                    )
                    .clickable { login() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text("登录", style = MaterialTheme.typography.labelLarge, color = if (phone.length == 11 && captcha.isNotBlank()) Color.White else palette.textTertiary)
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "未注册的手机号将自动注册",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
