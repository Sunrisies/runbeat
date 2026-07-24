package com.android.runbeat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.runbeat.metronome.core.MetronomeConstants
import com.android.runbeat.ui.theme.AccentBeat

/**
 * 节拍可视化：每拍触发一次脉冲动画。
 * 重音拍显示橙色并带外扩光环与光晕，普通拍显示主题色。
 * 光晕采用径向渐变，视觉更柔和、更有「能量感」。
 */
@Composable
fun BeatVisualizer(
    isAccent: Boolean,
    beatKey: Int,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
) {
    val scale = remember { Animatable(0f) }
    var animating by remember { mutableStateOf(false) }
    val beatColor = if (isAccent) AccentBeat else MaterialTheme.colorScheme.primary

    LaunchedEffect(beatKey) {
        if (beatKey < 0) {
            scale.snapTo(0f)
            animating = false
        } else {
            animating = true
            scale.snapTo(1.25f)
            scale.animateTo(1.0f, tween(90, easing = LinearOutSlowInEasing))
            scale.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
            animating = false
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val baseRadius = this.size.minDimension / 2f * 0.34f

        // 背景光晕（径向渐变，重音更亮）
        val glowRadius = baseRadius * 2.9f
        val glowAlpha = if (isAccent && animating) 0.45f else 0.20f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    beatColor.copy(alpha = glowAlpha),
                    beatColor.copy(alpha = 0f),
                ),
                center = center,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = center,
        )

        // 外扩光环（重音拍触发瞬间）
        if (isAccent && animating) {
            drawCircle(
                color = AccentBeat.copy(alpha = 0.25f),
                radius = baseRadius * 2.5f,
                center = center,
                style = Stroke(width = 5.dp.toPx()),
            )
            drawCircle(
                color = AccentBeat.copy(alpha = 0.12f),
                radius = baseRadius * 2.9f,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // 常显参考环
        drawCircle(
            color = beatColor.copy(alpha = 0.18f),
            radius = baseRadius * 1.8f,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )

        // 脉冲主体圆（带微光渐变填充）
        val fillRadius = baseRadius * (1f + scale.value * 0.55f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    beatColor.copy(alpha = 1f),
                    beatColor.copy(alpha = 0.55f),
                ),
                center = center,
                radius = fillRadius,
            ),
            radius = fillRadius.coerceAtLeast(1f),
            center = center,
        )
    }
}

/** 小节指示点：4 个圆点显示当前拍位，第 1 拍为重音点。 */
@Composable
fun BeatIndicator(
    currentBeat: Int,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 1..MetronomeConstants.BEATS_PER_BAR) {
            val isCurrent = isRunning && currentBeat == i
            val isAccent = i == 1
            val dotColor = when {
                isCurrent && isAccent -> AccentBeat
                isCurrent -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
            }
            Canvas(modifier = Modifier.size(if (isCurrent) 16.dp else 10.dp)) {
                drawCircle(
                    color = dotColor,
                    radius = this.size.minDimension / 2f,
                    center = center,
                )
            }
        }
    }
}
