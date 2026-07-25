package com.android.runbeat.update

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class UpdateCheckerTest {

    private class FakeSource(
        private val json: String,
        private val failTimes: Int,
    ) : UpdateSource {
        private var calls = 0
        override fun fetchManifest(): UpdateManifest {
            calls++
            if (calls <= failTimes) throw IOException("network down")
            return UpdateManifest.parse(json)
        }
    }

    @Test
    fun `succeeds on first attempt`() {
        val checker = UpdateChecker(FakeSource(validJson(), failTimes = 0), retryBackoffMs = 0)
        assertEquals(2, checker.fetchWithRetry().versionCode)
    }

    @Test
    fun `recovers after transient failure via retry`() {
        val checker = UpdateChecker(FakeSource(validJson(), failTimes = 1), retryBackoffMs = 0)
        assertEquals(2, checker.fetchWithRetry(retries = 1).versionCode)
    }

    @Test(expected = IOException::class)
    fun `throws after all retries exhausted`() {
        val checker = UpdateChecker(FakeSource(validJson(), failTimes = 99), retryBackoffMs = 0)
        checker.fetchWithRetry(retries = 2)
    }

    private fun validJson() =
        """{"version_code": 2, "version_name": "1.1", "update_url": "https://e.com/a.apk"}"""
}
