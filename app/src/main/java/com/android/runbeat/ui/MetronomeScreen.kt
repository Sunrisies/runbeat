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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
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
import com.android.runbeat.metronome.core.IntervalPhase
import com.android.runbeat.metronome.core.IntervalStatus
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
    onTestUpdate: () -> Unit = {},
    viewModel: MetronomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val dark = isSystemInDarkTheme()

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
                if (BuildConfig.DEBUG) {
                    TextButton(onClick = onTestUpdate) {
                        Text(
                            text = "测试更新",
                            style = MaterialTheme.typography.labelMedium,
                            color = com.android.runbeat.ui.theme.AccentBeat,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            ModeSelector(
                mode = state.mode,
                onModeChange = viewModel::setMode,
            )

            Spacer(Modifier.height(12.dp))

            if (state.mode == MetronomeMode.BASIC) {
                BasicModeContent(
                    state = state,
                    viewModel = viewModel,
                    currentBeat = currentBeat,
                    dark = dark,
                )
            } else {
                IntervalModeContent(
                    state = state,
                    viewModel = viewModel,
                    dark = dark,
                )
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: MetronomeMode, onModeChange: (MetronomeMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ModeChip(
            label = "基础节拍",
            selected = mode == MetronomeMode.BASIC,
            onClick = { onModeChange(MetronomeMode.BASIC) },
            modifier = Modifier.weight(1f),
        )
        ModeChip(
            label = "循环训练",
            selected = mode == MetronomeMode.INTERVAL,
            onClick = { onModeChange(MetronomeMode.INTERVAL) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AccentBeat else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, textSecondary().copy(alpha = 0.3f)),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(46.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else textPrimary(),
            )
        }
    }
}

@Composable
private fun BasicModeContent(
    state: MetronomeUiState,
    viewModel: MetronomeViewModel,
    currentBeat: Int?,
    dark: Boolean,
) {
    val settings = state.settings
    val cardColor = if (dark) RunCardDark else RunCardLight

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

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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

// ---------------------------------------------------------------- 循环训练模式

@Composable
private fun IntervalModeContent(
    state: MetronomeUiState,
    viewModel: MetronomeViewModel,
    dark: Boolean,
) {
    val settings = state.settings
    val cardColor = if (dark) RunCardDark else RunCardLight
    val iv = state.interval

    // 状态头
    val (phaseLabel, phaseColor) = when {
        iv.status == IntervalStatus.IDLE -> "循环未开始" to textSecondary()
        iv.status == IntervalStatus.PAUSED -> "已暂停" to PausedAmber
        iv.phase == IntervalPhase.WORK -> "工作中" to RunningGreen
        else -> "休息中" to PausedAmber
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // 状态卡
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cardColor,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(color = phaseColor, pulsing = iv.status == IntervalStatus.RUNNING)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = phaseLabel,
                        color = phaseColor,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (iv.status != IntervalStatus.IDLE) {
                    Text(
                        text = "第 ${iv.round} 轮 · 剩余 ${iv.remainingText()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary(),
                    )
                    Spacer(Modifier.height(10.dp))
                    // 阶段进度条
                    val fraction = if (iv.phaseDurationSec > 0) {
                        (1f - iv.phaseRemainingSec.toFloat() / iv.phaseDurationSec).coerceIn(0f, 1f)
                    } else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(textSecondary().copy(alpha = 0.12f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(Brush.horizontalGradient(listOf(AccentBeat, RunTeal))),
                        )
                    }
                } else {
                    Text(
                        text = "设置工作/休息时长后开始循环",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary(),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 间隔配置卡
        SectionCard(title = "间隔设置", cardColor = cardColor) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MinuteStepper(
                    label = "工作(分钟)",
                    value = state.intervalWorkMinutes,
                    color = RunningGreen,
                    onDecrement = { viewModel.setIntervalWorkMinutes(state.intervalWorkMinutes - 1) },
                    onIncrement = { viewModel.setIntervalWorkMinutes(state.intervalWorkMinutes + 1) },
                    modifier = Modifier.weight(1f),
                )
                MinuteStepper(
                    label = "休息(分钟)",
                    value = state.intervalRestMinutes,
                    color = RunTeal,
                    onDecrement = { viewModel.setIntervalRestMinutes(state.intervalRestMinutes - 1) },
                    onIncrement = { viewModel.setIntervalRestMinutes(state.intervalRestMinutes + 1) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "快捷组合",
                style = MaterialTheme.typography.labelMedium,
                color = textSecondary(),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IntervalPresetChip("1跑1走", 1, 1, state, viewModel)
                IntervalPresetChip("1跑2走", 1, 2, state, viewModel)
                IntervalPresetChip("2跑1走", 2, 1, state, viewModel)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "范围 1–60 分钟，参数已自动保存",
                style = MaterialTheme.typography.labelSmall,
                color = textSecondary(),
            )
        }

        Spacer(Modifier.height(14.dp))

        // 步频卡（工作阶段节拍）
        SectionCard(title = "工作步频", cardColor = cardColor) {
            BpmSliderSection(
                bpm = settings.bpm,
                onBpmChange = viewModel::setBpm,
                onDecrement = { viewModel.setBpm(settings.bpm - MetronomeConstants.BPM_STEP) },
                onIncrement = { viewModel.setBpm(settings.bpm + MetronomeConstants.BPM_STEP) },
            )
        }

        Spacer(Modifier.height(24.dp))

        IntervalControlButtons(iv.status, viewModel)

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun MinuteStepper(
    label: String,
    value: Int,
    color: Color,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = textSecondary())
        Spacer(Modifier.height(6.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = color.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = onIncrement) { Text("＋", color = color, fontSize = 20.sp) }
                Text(
                    text = "$value",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Text("分钟", style = MaterialTheme.typography.labelSmall, color = textSecondary())
                TextButton(onClick = onDecrement) { Text("－", color = color, fontSize = 20.sp) }
            }
        }
    }
}

@Composable
private fun IntervalPresetChip(
    label: String,
    work: Int,
    rest: Int,
    state: MetronomeUiState,
    viewModel: MetronomeViewModel,
) {
    val selected = state.intervalWorkMinutes == work && state.intervalRestMinutes == rest
    FilterChip(
        selected = selected,
        onClick = {
            viewModel.setIntervalWorkMinutes(work)
            viewModel.setIntervalRestMinutes(rest)
        },
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = textPrimary(),
            selectedContainerColor = AccentBeat.copy(alpha = 0.14f),
            selectedLabelColor = AccentBeat,
        ),
        leadingIcon = null,
    )
}

@Composable
private fun IntervalControlButtons(status: IntervalStatus, viewModel: MetronomeViewModel) {
    when (status) {
        IntervalStatus.IDLE -> {
            Button(
                onClick = viewModel::startInterval,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunningGreen, contentColor = Color.White),
            ) {
                Text("开始循环", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        IntervalStatus.RUNNING -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Button(
                    onClick = viewModel::pauseInterval,
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PausedAmber, contentColor = Color.White),
                ) {
                    Text("暂停", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = viewModel::stopInterval,
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text("终止", fontSize = 19.sp)
                }
            }
        }
        IntervalStatus.PAUSED -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Button(
                    onClick = viewModel::resumeInterval,
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RunningGreen, contentColor = Color.White),
                ) {
                    Text("继续", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = viewModel::stopInterval,
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text("终止", fontSize = 19.sp)
                }
            }
        }
    }
}

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
                SoundType.RUN -> "跑步节拍"
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
    Spacer(Modifier.height(6.dp))
    Text(
        text = "「跑步节拍」为参考跑步节拍音效（重音≈2.38kHz / 轻音≈727Hz），可用音调调节频率",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelSmall,
        color = textSecondary(),
    )
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
