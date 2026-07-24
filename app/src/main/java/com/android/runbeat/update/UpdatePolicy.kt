package com.android.runbeat.update

/**
 * 更新弹窗决策（纯逻辑，可单测）。
 *
 * 决策输入：
 *  - 远端版本号
 *  - 本地版本号
 *  - 用户「不再提示」的版本号（null 表示从未抑制）
 *  - 是否强制更新
 */
object UpdatePolicy {

    enum class Decision {
        /** 无新版本 */
        NO_UPDATE,

        /** 该版本已被用户「不再提示」，跳过 */
        SUPPRESSED,

        /** 有可用更新，显示三选项弹窗 */
        SHOW,

        /** 强制更新，仅显示「立即下载」 */
        SHOW_FORCED,
    }

    fun decide(
        remoteVersionCode: Int,
        localVersionCode: Int,
        suppressedVersionCode: Int?,
        forceUpdate: Boolean,
    ): Decision {
        if (!isUpdateAvailable(remoteVersionCode, localVersionCode)) return Decision.NO_UPDATE
        // 强制更新优先，不受「不再提示」抑制
        if (forceUpdate) return Decision.SHOW_FORCED
        if (suppressedVersionCode == remoteVersionCode) return Decision.SUPPRESSED
        return Decision.SHOW
    }
}
