package com.akiryo.akimusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass 的基础材质。
 *
 * 分四层合成：
 * 1. 底色（半透明的玻璃体，深浅色各自适配）
 * 2. 斜向白色高光，模拟表面反射
 * 3. 顶部一道极亮的细线 —— specular highlight
 * 4. 1px 暗边 —— iOS 27 加深的 darkened edge，用来把控件和内容分开
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    tint: Color? = null,
    alpha: Float? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val base = tint ?: palette.glassTint
    val a = alpha ?: palette.glassAlpha

    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    0f to base.copy(alpha = (a + 0.06f).coerceAtMost(1f)),
                    0.45f to base.copy(alpha = a),
                    1f to base.copy(alpha = (a - 0.08f).coerceAtLeast(0f)),
                )
            )
            .then(
                Modifier.drawWithContent {
                    drawContent()
                    // 斜向反射
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (palette.isDark) 0.10f else 0.34f),
                                Color.White.copy(alpha = 0.02f),
                                Color.Transparent,
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width * 0.85f, size.height * 1.1f),
                        )
                    )
                    // 高光与暗边
                    val stroke = 1.2f
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (palette.isDark) 0.22f else 0.55f),
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f),
                        style = Stroke(width = stroke),
                    )
                }
            )
            .border(
                width = 0.7.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = if (palette.isDark) 0.45f else 0.12f),
                        Color.Black.copy(alpha = if (palette.isDark) 0.18f else 0.05f),
                    )
                ),
                shape = shape,
            ),
        content = content,
    )
}

/** 玻璃胶囊按钮 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        content = content,
    )
}

/**
 * 底部栏 / 播放条的玻璃底：比普通玻璃更"厚"，因为要压住下面的内容。
 */
fun Modifier.barGlass(palette: Palette): Modifier {
    val base = if (palette.isDark) Color(0xFF141418) else Color(0xFFFDFDFE)
    return this
        .background(
            Brush.verticalGradient(
                0f to base.copy(alpha = 0.93f),
                1f to base.copy(alpha = 0.97f),
            )
        )
        .drawWithContent {
            drawContent()
            // 顶部 specular 线
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.0f),
                        Color.White.copy(alpha = if (palette.isDark) 0.30f else 0.9f),
                        Color.White.copy(alpha = 0.0f),
                    )
                ),
                start = Offset(0f, 0.6f),
                end = Offset(size.width, 0.6f),
                strokeWidth = 1.2f,
            )
        }
}

/** 细分割线 */
@Composable
fun hairline(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(0.7.dp)
            .background(palette.separator)
    )
}

/** 统一的圆角 */
object Radius {
    val card = 14.dp
    val cardLarge = 20.dp
    val sheet = 24.dp
}
