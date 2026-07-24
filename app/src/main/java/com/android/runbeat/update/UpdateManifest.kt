package com.android.runbeat.update

import org.json.JSONException
import org.json.JSONObject

/**
 * 服务器端版本清单数据模型与解析。
 * 纯 Kotlin，可 JVM 单测（依赖 org.json）。
 */
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val updateUrl: String,
    val releaseNotes: String,
    val forceUpdate: Boolean,
) {
    companion object {
        /**
         * 解析服务器返回的 JSON。
         * 字段缺失或类型错误时抛出 [JSONException]，由调用方决定容错策略。
         */
        fun parse(json: String): UpdateManifest {
            val obj = JSONObject(json)
            return UpdateManifest(
                versionCode = obj.requireInt("version_code"),
                versionName = obj.requireString("version_name"),
                updateUrl = obj.requireString("update_url"),
                releaseNotes = obj.optString("release_notes", ""),
                forceUpdate = obj.optBoolean("force_update", false),
            )
        }

        private fun JSONObject.requireInt(key: String): Int =
            optInt(key, -1).also {
                if (it < 0) throw JSONException("missing or invalid field: $key")
            }

        private fun JSONObject.requireString(key: String): String =
            optString(key, "").also {
                if (it.isEmpty()) throw JSONException("missing or empty field: $key")
            }
    }
}

/** 版本比对：远端 versionCode 高于本地视为有新版本 */
fun isUpdateAvailable(remoteVersionCode: Int, localVersionCode: Int): Boolean =
    remoteVersionCode > localVersionCode
