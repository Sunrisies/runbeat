package com.android.runbeat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.android.runbeat.update.UpdateUiState
import com.android.runbeat.ui.theme.AccentBeat

/** 更新流程弹窗回调 */
data class UpdateDialogActions(
    val onDownload: () -> Unit = {},
    val onLater: () -> Unit = {},
    val onNever: () -> Unit = {},
    val onInstall: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/** 依据更新状态渲染对应弹窗；Idle/Checking/NoUpdate 等状态不渲染 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    actions: UpdateDialogActions = UpdateDialogActions(),
) {
    when (state) {
        is UpdateUiState.Available -> UpdateAvailableDialog(state, actions)
        is UpdateUiState.Downloading -> DownloadProgressDialog(state.progress)
        is UpdateUiState.DownloadFailed -> DownloadFailedDialog(state.message, actions)
        is UpdateUiState.InstallPermissionNeeded -> InstallPermissionDialog(actions)
        is UpdateUiState.Installing -> InstallingDialog()
        else -> Unit
    }
}

@Composable
private fun UpdateAvailableDialog(state: UpdateUiState.Available, actions: UpdateDialogActions) {
    val manifest = state.manifest
    AlertDialog(
        onDismissRequest = { if (!state.forced) actions.onLater() },
        title = {
            Text(
                if (state.forced) {
                    "请更新到 v${manifest.versionName} 继续使用"
                } else {
                    "发现新版本 v${manifest.versionName}"
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            if (manifest.releaseNotes.isNotEmpty()) {
                Text(manifest.releaseNotes, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(
                onClick = actions.onDownload,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBeat),
            ) {
                Text("立即下载", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = if (state.forced) {
            null
        } else {
            {
                Column {
                    TextButton(onClick = actions.onNever) {
                        Text("不再提示")
                    }
                    TextButton(onClick = actions.onLater) {
                        Text("暂不下载")
                    }
                }
            }
        },
    )
}

@Composable
private fun DownloadProgressDialog(progress: Float) {
    val percent = (progress * 100).toInt().coerceIn(0, 100)
    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "正在下载更新",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "下载完成将自动引导安装",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(top = 16.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = AccentBeat,
            )
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                text = "$percent%",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun DownloadFailedDialog(message: String, actions: UpdateDialogActions) {
    AlertDialog(
        onDismissRequest = actions.onClose,
        title = { Text("下载失败", fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = actions.onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBeat),
            ) {
                Text("重试")
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onClose) { Text("关闭") }
        },
    )
}

@Composable
private fun InstallPermissionDialog(actions: UpdateDialogActions) {
    AlertDialog(
        onDismissRequest = actions.onClose,
        title = { Text("需要安装权限", fontWeight = FontWeight.Bold) },
        text = { Text("为完成版本更新，请开启「安装未知来源应用」。开启后返回应用将自动继续安装。") },
        confirmButton = {
            Button(
                onClick = actions.onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBeat),
            ) {
                Text("打开设置")
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onClose) { Text("取消") }
        },
    )
}

@Composable
private fun InstallingDialog() {
    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "正在安装，请按系统提示完成更新…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
