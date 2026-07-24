package com.android.runbeat.update

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {

    private val validJson = """
        {
          "version_code": 10002,
          "version_name": "1.0.2",
          "update_url": "https://example.com/app-1.0.2.apk",
          "release_notes": "修复若干问题",
          "force_update": true
        }
    """.trimIndent()

    @Test
    fun `parses valid manifest`() {
        val m = UpdateManifest.parse(validJson)
        assertEquals(10002, m.versionCode)
        assertEquals("1.0.2", m.versionName)
        assertEquals("https://example.com/app-1.0.2.apk", m.updateUrl)
        assertEquals("修复若干问题", m.releaseNotes)
        assertTrue(m.forceUpdate)
    }

    @Test
    fun `force_update defaults to false when absent`() {
        val m = UpdateManifest.parse("""
            {"version_code": 2, "version_name": "1.1", "update_url": "https://e.com/a.apk"}
        """.trimIndent())
        assertFalse(m.forceUpdate)
        assertEquals("", m.releaseNotes)
    }

    @Test(expected = JSONException::class)
    fun `missing version_code throws`() {
        UpdateManifest.parse("""{"version_name": "1.1", "update_url": "https://e.com/a.apk"}""")
    }

    @Test(expected = JSONException::class)
    fun `missing update_url throws`() {
        UpdateManifest.parse("""{"version_code": 2, "version_name": "1.1"}""")
    }

    @Test(expected = JSONException::class)
    fun `invalid json throws`() {
        UpdateManifest.parse("not json at all")
    }

    @Test
    fun `version compare semantics`() {
        assertTrue(isUpdateAvailable(remoteVersionCode = 10, localVersionCode = 9))
        assertTrue(isUpdateAvailable(remoteVersionCode = 10, localVersionCode = 0))
        assertFalse(isUpdateAvailable(remoteVersionCode = 10, localVersionCode = 10))
        assertFalse(isUpdateAvailable(remoteVersionCode = 10, localVersionCode = 11))
        assertFalse(isUpdateAvailable(remoteVersionCode = 0, localVersionCode = 1))
    }
}
