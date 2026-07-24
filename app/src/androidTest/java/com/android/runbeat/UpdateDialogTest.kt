package com.android.runbeat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.runbeat.ui.UpdateDialog
import com.android.runbeat.ui.UpdateDialogActions
import com.android.runbeat.update.UpdateManifest
import com.android.runbeat.update.UpdateUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 更新弹窗交互测试（需真机/模拟器运行） */
@RunWith(AndroidJUnit4::class)
class UpdateDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun manifest(forced: Boolean = false) = UpdateManifest(
        versionCode = 2,
        versionName = "1.1",
        updateUrl = "https://example.com/app.apk",
        releaseNotes = "测试更新内容",
        forceUpdate = forced,
    )

    @Test
    fun normalModeShowsThreeActions() {
        composeRule.setContent {
            UpdateDialog(
                state = UpdateUiState.Available(manifest(forced = false), forced = false),
                actions = UpdateDialogActions(),
            )
        }
        composeRule.onNodeWithText("发现新版本 v1.1").assertIsDisplayed()
        composeRule.onNodeWithText("立即下载").assertIsDisplayed()
        composeRule.onNodeWithText("暂不下载").assertIsDisplayed()
        composeRule.onNodeWithText("不再提示").assertIsDisplayed()
    }

    @Test
    fun forcedModeShowsOnlyDownload() {
        composeRule.setContent {
            UpdateDialog(
                state = UpdateUiState.Available(manifest(forced = true), forced = true),
                actions = UpdateDialogActions(),
            )
        }
        composeRule.onNodeWithText("立即下载").assertIsDisplayed()
        composeRule.onAllNodesWithText("暂不下载").assertCountEquals(0)
        composeRule.onAllNodesWithText("不再提示").assertCountEquals(0)
    }

    @Test
    fun downloadProgressShowsPercent() {
        composeRule.setContent {
            UpdateDialog(state = UpdateUiState.Downloading(progress = 0.5f))
        }
        composeRule.onNodeWithText("正在下载更新").assertIsDisplayed()
        composeRule.onNodeWithText("50%").assertIsDisplayed()
    }
}
