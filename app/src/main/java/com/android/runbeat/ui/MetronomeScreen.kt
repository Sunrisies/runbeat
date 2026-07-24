package com.android.runbeat.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.runbeat.BuildConfig
import com.android.runbeat.metronome.core.MetronomeConstants
import com.android.runbeat.metronome.core.MetronomeStatus
import com.android.runbeat.metronome.core.SoundType
import com.android.runbeat.metronome.core.TickTiming
import com.android.runbeat.ui.theme.AccentBeat
import com.android.runbeat.ui.theme.PausedAmber
import com.android.runbeat.ui.theme.RunCardDark
import com.android.runbeat.ui.theme.RunCardLight
import com.android.runbeat.ui.theme.RunDarkBgBottom
import com.android.runbeat.ui.theme.RunDarkBgTop
import com.android.runbeat.ui.theme.RunLightBgBottom
import com.android.runbeat.ui.theme.RunLightBgTop
import com.android.runbeat.ui.theme.RunOnDark
import com.android.runbeat.ui.theme.RunOnLight
import com.android.runbeat.ui.theme.RunningGreen
import com.android.runbeat.ui.theme.RunTeal
import kotlin.math.roundToInt

@Composable
fun MetronomeScreen(
    modifier: Modifier = Modifier,
    onCheckUpdate: () -> Unit = {},
    viewModel: MetronomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.settings
    val dark = isSystemInDarkTheme()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.start()
    }

    val onStartClick: () -> Unit = {
        if (viewModel.hasNotificationPermission()) {
            viewModel.start()
        } else {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val currentBeat = state.lastTick?.let { it.beatIndex % MetronomeConstants.BEATS_PER_BAR + 1 }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(RunDarkBgTop, RunDarkBgBottom)
                    else listOf(RunLightBgTop, RunLightBgBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = textSecondary(),
                )
                TextButton(onClick = onCheckUpdate) {
                    Text(
                        text = "检查更新",
                        style = MaterialTheme.typography.labelMedium,
                        color = textSecondary(),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            StatusHeader(
                status = state.status,
                currentBeat = currentBeat,
            )

            Spacer(Modifier.height(16.dp))

            HeroCard(
                bpm = settings.bpm,
                intervalMillis = TickTiming.intervalMillis(settings.bpm),
                isRunning = state.status == MetronomeStatus.RUNNING,
                isAccent = state.lastTick?.accent == true,
                beatKey = state.lastTick?.beatIndex ?: -1,
                currentBeat = currentBeat,
                cardColor = if (dark) RunCardDark else RunCardLight,
            )

            Spacer(Modifier.height(14.dp))

            SectionCard(title = "节拍频率", cardColor = if (dark) RunCardDark else RunCardLight) {
                BpmSliderSection(
                    bpm = settings.bpm,
                    onBpmChange = viewModel::setBpm,
                    onDecrement = { viewModel.setBpm(settings.bpm - MetronomeConstants.BPM_STEP) },
                    onIncrement = { viewModel.setBpm(settings.bpm + MetronomeConstants.BPM_STEP) },
                )
            }

            Spacer(Modifier.height(14.dp))

            SectionCard(title = "快捷预设", cardColor = if (dark) RunCardDark else RunCardLight) {
                PresetSection(
                    currentBpm = settings.bpm,
                    customPresetBpm = state.customPresetBpm,
                    onPresetSelected = viewModel::setBpm,
                    onSaveCustom = viewModel::saveCustomPreset,
                )
            }

            Spacer(Modifier.height(14.dp))

            SectionCard(title = "节拍音色", cardColor = if (dark) RunCardDark else RunCardLight) {
                SoundSection(
                    current = settings.soundType,
                    onSelect = viewModel::changeSound,
                )
            }

            Spacer(Modifier.height(14.dp))

            SectionCard(title = "音量", cardColor = if (dark) RunCardDark else RunCardLight) {
                VolumeSection(
                    volumePercent = settings.volumePercent,
                    onVolumeChange = viewModel::changeVolume,
                )
            }

            Spacer(Modifier.height(24.dp))

            ControlButtons(
                status = state.status,
                onStart = onStartClick,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onRestart = viewModel::restart,
                onReset = viewModel::stop,
                needsPermission = !viewModel.hasNotificationPermission(),
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ---------------------------------------------------------------- 状态头

@Composable
private fun StatusHeader(status: MetronomeStatus, currentBeat: Int?) {
    val (label, color) = when (status) {
        MetronomeStatus.RUNNING -> "运行中" to RunningGreen
        MetronomeStatus.PAUSED -> "已暂停" to PausedAmber
        MetronomeStatus.STOPPED -> "待机中" to textSecondary()
    }
    val beatText = if (status == MetronomeStatus.RUNNING && currentBeat != null) {
        "第 $currentBeat 拍"
    } else {
        "调整步频，跑出你的节奏"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = color.copy(alpha = 0.14f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulsingDot(color = color, pulsing = status == MetronomeStatus.RUNNING)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    color = color,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = beatText,
            style = MaterialTheme.typography.bodyMedium,
            color = textSecondary(),
        )
    }
}

@Composable
private fun PulsingDot(color: Color, pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Canvas(modifier = Modifier.size(9.dp)) {
        drawCircle(
            brush = SolidColor(color.copy(alpha = if (pulsing) alpha else 1f)),
            radius = this.size.minDimension / 2f,
            center = Offset(this.size.width / 2f, this.size.height / 2f),
        )
    }
}

// ---------------------------------------------------------------- 主卡

@Composable
private fun HeroCard(
    bpm: Int,
    intervalMillis: Long,
    isRunning: Boolean,
    isAccent: Boolean,
    beatKey: Int,
    currentBeat: Int?,
    cardColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = cardColor,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "当前步频",
                style = MaterialTheme.typography.labelLarge,
                color = textSecondary(),
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = bpm.toString(),
                    fontSize = 96.sp,
                    lineHeight = 100.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isRunning) AccentBeat else textPrimary(),
                )
                Text(
                    text = " BPM",
                    modifier = Modifier.padding(bottom = 14.dp, start = 2.dp),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = textSecondary(),
                )
            }
            Text(
                text = "每拍间隔 $intervalMillis 毫秒",
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary(),
            )

            Spacer(Modifier.height(6.dp))

            BeatVisualizer(
                isAccent = isAccent,
                beatKey = beatKey,
            )

            Spacer(Modifier.height(10.dp))

            BeatIndicator(currentBeat = currentBeat ?: 1, isRunning = isRunning)
        }
    }
}

// ---------------------------------------------------------------- 分节卡

@Composable
private fun SectionCard(
    title: String,
    cardColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(AccentBeat, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary(),
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------- 节拍频率

@Composable
private fun BpmSliderSection(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = bpm.toFloat(),
            onValueChange = { onBpmChange(it.roundToInt()) },
            valueRange = MetronomeConstants.MIN_BPM.toFloat()..MetronomeConstants.MAX_BPM.toFloat(),
            steps = MetronomeConstants.MAX_BPM - MetronomeConstants.MIN_BPM - 1,
            colors = sliderColors(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${MetronomeConstants.MIN_BPM}", style = MaterialTheme.typography.labelMedium, color = textSecondary())
            Text("${MetronomeConstants.MAX_BPM}", style = MaterialTheme.typography.labelMedium, color = textSecondary())
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StepButton(symbol = "−", onClick = onDecrement, modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier.width(112.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "$bpm",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentBeat,
                )
                Text("BPM", style = MaterialTheme.typography.labelMedium, color = textSecondary())
            }
            StepButton(symbol = "＋", onClick = onIncrement, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = AccentBeat.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentBeat.copy(alpha = 0.35f)),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(58.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = symbol,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                color = AccentBeat,
            )
        }
    }
}

// ---------------------------------------------------------------- 快捷预设

@Composable
private fun PresetSection(
    currentBpm: Int,
    customPresetBpm: Int?,
    onPresetSelected: (Int) -> Unit,
    onSaveCustom: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PresetChip(
                label = "160 慢跑",
                sub = "入门",
                selected = currentBpm == 160,
                onClick = { onPresetSelected(160) },
            )
            PresetChip(
                label = "180 配速",
                sub = "标准",
                selected = currentBpm == 180,
                onClick = { onPresetSelected(180) },
            )
            PresetChip(
                label = "190 冲刺",
                sub = "间歇",
                selected = currentBpm == 190,
                onClick = { onPresetSelected(190) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val customLabel = customPresetBpm?.let { "我的预设 $it" } ?: "我的预设"
            PresetChip(
                label = customLabel,
                sub = if (customPresetBpm != null) "已保存" else "未设置",
                selected = customPresetBpm != null && currentBpm == customPresetBpm,
                onClick = { customPresetBpm?.let(onPresetSelected) },
                enabled = customPresetBpm != null,
            )
            TextButton(onClick = onSaveCustom) {
                Text("保存当前", color = AccentBeat, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = if (selected) AccentBeat else textSecondary())
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = textPrimary(),
            selectedContainerColor = AccentBeat.copy(alpha = 0.14f),
            selectedLabelColor = AccentBeat,
        ),
        leadingIcon = null,
    )
}

// ---------------------------------------------------------------- 节拍音色

@Composable
private fun SoundSection(current: SoundType, onSelect: (SoundType) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SoundType.entries.forEach { sound ->
            val label = when (sound) {
                SoundType.CLICK -> "咔嗒"
                SoundType.BEEP -> "哔声"
                SoundType.WOOD -> "木鱼"
            }
            FilterChip(
                selected = current == sound,
                onClick = { onSelect(sound) },
                label = { Text(label, fontWeight = if (current == sound) FontWeight.Bold else FontWeight.Medium) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    labelColor = textPrimary(),
                    selectedContainerColor = RunTeal.copy(alpha = 0.14f),
                    selectedLabelColor = RunTeal,
                ),
                leadingIcon = null,
            )
        }
    }
}

// ---------------------------------------------------------------- 音量

@Composable
private fun VolumeSection(volumePercent: Int, onVolumeChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("音量", style = MaterialTheme.typography.bodyMedium, color = textSecondary())
            Text(
                text = "$volumePercent%",
                fontWeight = FontWeight.Bold,
                color = RunTeal,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Slider(
            value = volumePercent.toFloat(),
            onValueChange = { onVolumeChange(it.roundToInt()) },
            valueRange = 0f..100f,
            steps = 99,
            colors = sliderColors(active = RunTeal, thumb = RunTeal),
        )
    }
}

// ---------------------------------------------------------------- 控制按钮

@Composable
private fun ControlButtons(
    status: MetronomeStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onReset: () -> Unit,
    needsPermission: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        when (status) {
            MetronomeStatus.STOPPED -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBeat,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("开始节拍", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                if (needsPermission) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "需授予通知权限才能后台/锁屏持续运行",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary(),
                    )
                }
            }
            MetronomeStatus.RUNNING -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Button(
                        onClick = onPause,
                        modifier = Modifier.weight(1f).height(60.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PausedAmber,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("暂停", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onRestart,
                        modifier = Modifier.weight(1f).height(60.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RunningGreen,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("重新开始", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            MetronomeStatus.PAUSED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Button(
                        onClick = onResume,
                        modifier = Modifier.weight(1f).height(60.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RunningGreen,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("继续", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f).height(60.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = textSecondary(),
                        ),
                    ) {
                        Text("重置", fontSize = 19.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 主题色工具

@Composable
private fun textPrimary(): Color = if (isSystemInDarkTheme()) RunOnDark else RunOnLight

@Composable
private fun textSecondary(): Color = textPrimary().copy(alpha = 0.6f)

@Composable
private fun sliderColors(
    active: Color = AccentBeat,
    thumb: Color = AccentBeat,
) = SliderDefaults.colors(
    thumbColor = thumb,
    activeTrackColor = active,
    inactiveTrackColor = textSecondary().copy(alpha = 0.22f),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)
