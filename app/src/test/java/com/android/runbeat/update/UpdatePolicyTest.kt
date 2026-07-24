package com.android.runbeat.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePolicyTest {

    private val policy = UpdatePolicy::decide

    @Test
    fun `no update when remote not newer`() {
        assertEquals(UpdatePolicy.Decision.NO_UPDATE, policy(10, 10, null, false))
        assertEquals(UpdatePolicy.Decision.NO_UPDATE, policy(9, 10, null, false))
        assertEquals(UpdatePolicy.Decision.NO_UPDATE, policy(10, 10, null, true))
    }

    @Test
    fun `newer version shows normal dialog`() {
        assertEquals(UpdatePolicy.Decision.SHOW, policy(11, 10, null, false))
    }

    @Test
    fun `suppressed version is skipped`() {
        // 用户对该版本选择过「不再提示」
        assertEquals(UpdatePolicy.Decision.SUPPRESSED, policy(11, 10, 11, false))
    }

    @Test
    fun `higher version overrides suppression`() {
        // 曾对 v11 不再提示，但发布了 v12 仍应提示
        assertEquals(UpdatePolicy.Decision.SUPPRESSED, policy(11, 10, 11, false))
        assertEquals(UpdatePolicy.Decision.SHOW, policy(12, 10, 11, false))
    }

    @Test
    fun `force update bypasses suppression and shows forced dialog`() {
        assertEquals(UpdatePolicy.Decision.SHOW_FORCED, policy(11, 10, 11, true))
        assertEquals(UpdatePolicy.Decision.SHOW_FORCED, policy(11, 10, null, true))
        assertEquals(UpdatePolicy.Decision.SHOW_FORCED, policy(100, 10, 11, true))
    }

    @Test
    fun `never update when remote equals local even if suppressed`() {
        assertEquals(UpdatePolicy.Decision.NO_UPDATE, policy(10, 10, 11, false))
    }
}
