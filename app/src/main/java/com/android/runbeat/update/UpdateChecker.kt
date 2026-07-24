package com.android.runbeat.update

import java.io.IOException

/**
 * 版本检查器：对数据源执行带重试的拉取。
 * 纯 JVM 逻辑，可用假数据源单测网络失败重试与解析异常容错。
 */
class UpdateChecker(
    private val source: UpdateSource,
) {

    /**
     * 拉取清单，失败时最多重试 [retries] 次。
     * @throws IOException 所有重试均失败时抛出
     */
    @Throws(IOException::class)
    fun fetchWithRetry(retries: Int = UpdateConfig.CHECK_RETRIES): UpdateManifest {
        var lastError: Exception? = null
        repeat(retries + 1) {
            try {
                return source.fetchManifest()
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException("版本检查失败: ${lastError?.message}", lastError)
    }
}
