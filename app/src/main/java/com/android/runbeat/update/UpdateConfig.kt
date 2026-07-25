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

    const val CONNECT_TIMEOUT_MS = 4_000
    const val READ_TIMEOUT_MS = 4_000

    /** 单次版本检查的总超时上限，超时按失败处理，避免用户长时间无响应等待 */
    const val CHECK_TOTAL_TIMEOUT_MS = 10_000L

    /** 失败重试前的短暂间隔 */
    const val RETRY_BACKOFF_MS = 600L

    /** 下载流式读取超时（下载时读单块数据不能太短，CDN 卡顿会导致 99% 超时） */
    const val DOWNLOAD_READ_TIMEOUT_MS = 30_000

    /** 下载失败自动重试次数（利用 .part 断点续传） */
    const val DOWNLOAD_RETRIES = 2

    /** 下载重试间隔 */
    const val DOWNLOAD_RETRY_DELAY_MS = 1_000L

    /** 下载进度轮询间隔 */
    const val PROGRESS_POLL_INTERVAL_MS = 500L

    /** 检查失败的静默次数上限内重试次数 */
    const val CHECK_RETRIES = 1
}
