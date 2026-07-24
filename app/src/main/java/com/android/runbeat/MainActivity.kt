package com.android.runbeat

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.runbeat.ui.MetronomeScreen
import com.android.runbeat.ui.UpdateDialog
import com.android.runbeat.ui.UpdateDialogActions
import com.android.runbeat.ui.UpdateViewModel
import com.android.runbeat.ui.theme.RunBeatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RunBeatTheme {
                UpdateHost()
            }
        }
    }
}

@Composable
private fun UpdateHost() {
    val updateViewModel: UpdateViewModel = viewModel()
    val updateState by updateViewModel.state.collectAsState()
    val notice by updateViewModel.notices.collectAsState(null)
    val context = LocalContext.current

    // 启动自动检测（延迟片刻，避免与首帧竞争）
    LaunchedEffect(Unit) { updateViewModel.checkOnLaunch() }

    // 手动检查等即时反馈
    LaunchedEffect(notice) {
        notice?.let { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            MetronomeScreen(
                modifier = Modifier.padding(innerPadding),
                onCheckUpdate = updateViewModel::checkNow,
            )
            UpdateDialog(
                state = updateState,
                actions = UpdateDialogActions(
                    onDownload = updateViewModel::downloadNow,
                    onLater = updateViewModel::dismiss,
                    onNever = updateViewModel::suppress,
                    onInstall = updateViewModel::installNow,
                    onOpenSettings = updateViewModel::openInstallSettings,
                    onRetry = updateViewModel::retryDownload,
                    onClose = updateViewModel::dismiss,
                ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MetronomePreview() {
    RunBeatTheme {
        MetronomeScreen(modifier = Modifier)
    }
}
