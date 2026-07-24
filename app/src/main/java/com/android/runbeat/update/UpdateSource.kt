package com.android.runbeat.update

import android.content.Context
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** 版本清单数据源 */
interface UpdateSource {
    /** 拉取并解析版本清单；网络/解析失败时抛出 [IOException] */
    @Throws(IOException::class)
    fun fetchManifest(): UpdateManifest
}

/** 真实 HTTP 数据源：GET 远程 manifest.json */
class HttpUpdateSource(
    private val url: String,
    private val connectTimeoutMs: Int = UpdateConfig.CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = UpdateConfig.READ_TIMEOUT_MS,
) : UpdateSource {

    @Throws(IOException::class)
    override fun fetchManifest(): UpdateManifest {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
            return try {
                UpdateManifest.parse(body)
            } catch (e: Exception) {
                throw IOException("invalid manifest: ${e.message}", e)
            }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * 内置 Mock 数据源：读取 assets/update_mock.json。
 * 使用清单中的真实 version_code 与本地比对（不再强制本地+1），
 * 这样「更新到清单对应版本后」Debug 构建也不会重复弹窗。
 */
class MockUpdateSource(
    context: Context,
) : UpdateSource {

    private val json: String = context.assets.open("update_mock.json")
        .bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)

    @Throws(IOException::class)
    override fun fetchManifest(): UpdateManifest =
        UpdateManifest.parse(json)
}
