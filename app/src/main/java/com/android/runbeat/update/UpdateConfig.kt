package com.android.runbeat.update

import com.android.runbeat.BuildConfig

/** 更新系统运行配置 */
object UpdateConfig {

    /** 正式版服务器版本清单地址（发布时替换为真实服务器） */
    const val CHECK_URL: String = BuildConfig.UPDATE_CHECK_URL

    /**
     * Debug 构建使用内置 Mock 清单演示完整更新流程（无需服务器）；
     * Release 构建走真实 HTTP 拉取。如需在 debug 下走真实地址，将此值改为 false。
     */
    val USE_MOCK: Boolean = BuildConfig.DEBUG

    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 10_000

    /** 下载进度轮询间隔 */
    const val PROGRESS_POLL_INTERVAL_MS = 500L

    /** 检查失败的静默次数上限内重试次数 */
    const val CHECK_RETRIES = 1
}
