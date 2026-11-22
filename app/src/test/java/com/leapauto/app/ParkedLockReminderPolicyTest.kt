package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkedLockReminderPolicyTest {

    @Test
    fun `parked unlocked vehicle shows red warning badge`() {
        assertEquals(
            ParkedLockReminderPolicy.LockBadge.UNLOCKED_WARNING,
            ParkedLockReminderPolicy.badge(locked = false, driving = false)
        )
        assertTrue(ParkedLockReminderPolicy.shouldWarnUnlocked(locked = false, driving = false))
    }

    @Test
    fun `parked locked vehicle shows grey locked badge`() {
        assertEquals(
            ParkedLockReminderPolicy.LockBadge.LOCKED,
            ParkedLockReminderPolicy.badge(locked = true, driving = false)
        )
        assertFalse(ParkedLockReminderPolicy.shouldWarnUnlocked(locked = true, driving = false))
    }

    @Test
    fun `driving vehicle hides the lock badge`() {
        assertEquals(
            ParkedLockReminderPolicy.LockBadge.HIDDEN,
            ParkedLockReminderPolicy.badge(locked = false, driving = true)
        )
        assertFalse(ParkedLockReminderPolicy.shouldWarnUnlocked(locked = false, driving = true))
        assertEquals(
            ParkedLockReminderPolicy.LockBadge.HIDDEN,
            ParkedLockReminderPolicy.badge(locked = true, driving = true)
        )
    }

    @Test
    fun `unknown lock state hides the badge`() {
        assertEquals(
            ParkedLockReminderPolicy.LockBadge.HIDDEN,
            ParkedLockReminderPolicy.badge(locked = null, driving = false)
        )
        assertEquals(
            ParkedLockReminderPolicy.LockBadge.HIDDEN,
            ParkedLockReminderPolicy.badge(locked = null, driving = null)
        )
        assertFalse(ParkedLockReminderPolicy.shouldWarnUnlocked(locked = null, driving = false))
    }

    @Test
    fun `unknown motion with confirmed unlock still warns`() {
        assertTrue(ParkedLockReminderPolicy.shouldWarnUnlocked(locked = false, driving = null))
    }

    @Test
    fun `badge labels match the widget text`() {
        assertEquals("未锁车", ParkedLockReminderPolicy.UNLOCKED_WARNING_TEXT)
        assertEquals("已锁车", ParkedLockReminderPolicy.LOCKED_TEXT)
        assertEquals("已驻车", ParkedLockReminderPolicy.PARKED_TEXT)
    }

    @Test
    fun `home status pill switches to unlocked while parked and unlocked`() {
        // 驻车 + 未锁车：首页胶囊跟随锁车口径显示「未锁车」。
        assertEquals(
            "未锁车",
            ParkedLockReminderPolicy.homeStatusLabel(drivingLabel = "已驻车", isMoving = false, locked = false)
        )
        // 驻车 + 已锁车：保持驻车文案。
        assertEquals(
            "已驻车",
            ParkedLockReminderPolicy.homeStatusLabel(drivingLabel = "已驻车", isMoving = false, locked = true)
        )
        // 行驶中不做锁车口径替换。
        assertEquals(
            "行驶中",
            ParkedLockReminderPolicy.homeStatusLabel(drivingLabel = "行驶中", isMoving = true, locked = false)
        )
        // 没有状态文案或缺省锁状态时回落到「已驻车」。
        assertEquals(
            "已驻车",
            ParkedLockReminderPolicy.homeStatusLabel(drivingLabel = null, isMoving = false, locked = null)
        )
    }
}
