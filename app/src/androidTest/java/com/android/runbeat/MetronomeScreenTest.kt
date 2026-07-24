package com.android.runbeat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 主界面冒烟测试：关键控件均正确渲染（需在真机/模拟器运行）。 */
@RunWith(AndroidJUnit4::class)
class MetronomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun screenRendersCoreControls() {
        composeRule.onNodeWithText("当前步频").assertIsDisplayed()
        composeRule.onNodeWithText("开始").assertIsDisplayed()
        composeRule.onNodeWithText("快捷预设").assertIsDisplayed()
        composeRule.onNodeWithText("节拍音色").assertIsDisplayed()
        composeRule.onNodeWithText("音量 100%").assertIsDisplayed()
    }

    @Test
    fun presetsAreVisible() {
        composeRule.onNodeWithText("160 入门慢跑").assertIsDisplayed()
        composeRule.onNodeWithText("180 标准配速").assertIsDisplayed()
        composeRule.onNodeWithText("190 间歇冲刺").assertIsDisplayed()
    }
}
