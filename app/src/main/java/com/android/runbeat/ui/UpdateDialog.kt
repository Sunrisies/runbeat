package com.android.runbeat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.android.runbeat.R
import com.android.runbeat.update.UpdateUiState
import com.android.runbeat.ui.theme.AccentBeat
import com.android.runbeat.ui.theme.PausedAmber
import com.android.runbeat.ui.theme.RunCardDark
import com.android.runbeat.ui.theme.RunCardLight
import com.android.runbeat.ui.theme.RunOnDark
import com.android.runbeat.ui.theme.RunOnLight
import com.android.runbeat.ui.theme.RunTeal

/** 更新流程弹窗回调 */
data class UpdateDialogActions(
    val onDownload: () -> Unit = {},
    val onLater: () -> Unit = {},
    val onNever: () -> Unit = {},
    val onInstall: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onCancelDownload: () -> Unit = {},
)

/** 依据更新状态渲染对应弹窗；Idle/Checking/NoUpdate 等状态不渲染 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    actions: UpdateDialogActions = UpdateDialogActions(),
) {
    when (state) {
        is UpdateUiState.Available -> UpdateAvailableDialog(state, actions)
        is UpdateUiState.Downloading -> DownloadProgressDialog(state, actions.onCancelDownload)
        is UpdateUiState.DownloadFailed -> DownloadFailedDialog(state.message, actions)
        is UpdateUiState.InstallPermissionNeeded -> InstallPermissionDialog(actions)
        is UpdateUiState.Installing -> InstallingDialog()
        else -> Unit
    }
}

// ---------------------------------------------------------------- 基础

/** 品牌弹窗容器：圆角卡片 + 主题色 */
@Composable
private fun BrandedDialog(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = brandCardColor(),
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
                content = content,
            )
        }
    }
}

/** 弹窗头部：圆形图标 + 标题 + 副标题 */
@Composable
private fun DialogHeader(
    iconRes: Int,
    iconTint: Color,
    title: String,
    subtitle: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = iconTint,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = brandTextPrimary(),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = brandTextSecondary(),
            )
        }
    }
}

/** 主按钮（品牌橙，全宽） */
@Composable
private fun BrandPrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentBeat,
            contentColor = Color.White,
        ),
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- 发现新版本

@Composable
private fun UpdateAvailableDialog(state: UpdateUiState.Available, actions: UpdateDialogActions) {
    val manifest = state.manifest
    BrandedDialog(onDismissRequest = { if (!state.forced) actions.onLater() }) {
        DialogHeader(
            iconRes = R.drawable.ic_download,
            iconTint = AccentBeat,
            title = if (state.forced) "请更新应用" else "发现新版本",
            subtitle = "RunBeat v${manifest.versionName}",
        )

        if (manifest.releaseNotes.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = brandTextPrimary().copy(alpha = 0.05f),
            ) {
                Text(
                    text = manifest.releaseNotes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = brandTextSecondary(),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        BrandPrimaryButton(text = "立即下载", onClick = actions.onDownload)

        if (!state.forced) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = actions.onLater) {
                    Text("暂不下载", color = brandTextSecondary())
                }
                TextButton(onClick = actions.onNever) {
                    Text("不再提示", color = brandTextSecondary())
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 下载进度

@Composable
private fun DownloadProgressDialog(state: UpdateUiState.Downloading, onCancel: () -> Unit = {}) {
    val percent = (state.progress * 100).toInt().coerceIn(0, 100)
    val fraction = (percent / 100f).coerceIn(0f, 1f)
    BrandedDialog(onDismissRequest = {}) {
        DialogHeader(
            iconRes = R.drawable.ic_download,
            iconTint = AccentBeat,
            title = "正在下载更新",
            subtitle = "已下载 ${formatBytes(state.bytesDownloaded)} / ${formatBytes(state.totalBytes)}",
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = "$percent%",
            fontSize = 44.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.Black,
            color = AccentBeat,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.height(6.dp))

        // 渐变进度条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(brandTextPrimary().copy(alpha = 0.10f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(AccentBeat, RunTeal))
                    ),
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "下载完成后将自动引导安装",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = brandTextSecondary(),
        )
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("取消下载", color = brandTextSecondary())
        }
    }
}

// ---------------------------------------------------------------- 失败 / 权限 / 安装中

@Composable
private fun DownloadFailedDialog(message: String, actions: UpdateDialogActions) {
    BrandedDialog(onDismissRequest = actions.onClose) {
        DialogHeader(
            iconRes = R.drawable.ic_warning,
            iconTint = Color(0xFFE11D48),
            title = "下载失败",
            subtitle = "请稍后重试",
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = brandTextSecondary(),
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = actions.onClose,
                modifier = Modifier.weight(1f),
            ) {
                Text("关闭", color = brandTextSecondary())
            }
            Button(
                onClick = actions.onRetry,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBeat,
                    contentColor = Color.White,
                ),
            ) {
                Text("重试", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InstallPermissionDialog(actions: UpdateDialogActions) {
    BrandedDialog(onDismissRequest = actions.onClose) {
        DialogHeader(
            iconRes = R.drawable.ic_warning,
            iconTint = PausedAmber,
            title = "需要安装权限",
            subtitle = "最后一步",
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "为完成版本更新，请开启「安装未知来源应用」。开启后返回应用将自动继续安装。",
            style = MaterialTheme.typography.bodyMedium,
            color = brandTextSecondary(),
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = actions.onClose,
                modifier = Modifier.weight(1f),
            ) {
                Text("取消", color = brandTextSecondary())
            }
            Button(
                onClick = actions.onOpenSettings,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBeat,
                    contentColor = Color.White,
                ),
            ) {
                Text("打开设置", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InstallingDialog() {
    BrandedDialog(onDismissRequest = {}) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = AccentBeat,
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = "正在安装，请按系统提示完成更新…",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = brandTextPrimary(),
            )
        }
    }
}

// ---------------------------------------------------------------- 工具

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 MB"
    bytes < 1024L * 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
private fun brandCardColor(): Color = if (isSystemInDarkTheme()) RunCardDark else RunCardLight

@Composable
private fun brandTextPrimary(): Color = if (isSystemInDarkTheme()) RunOnDark else RunOnLight

@Composable
private fun brandTextSecondary(): Color = brandTextPrimary().copy(alpha = 0.62f)
