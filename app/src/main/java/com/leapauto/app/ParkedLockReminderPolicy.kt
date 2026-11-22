package com.leapauto.app

/**
 * 桌面 4x2 小组件右上角的锁车状态展示规则。
 *
 * 口径（按驻车状态展示）：
 * - 已驻车 + 已锁车 → 绿点 + 灰色小字「已锁车」。
 * - 已驻车 + 未锁车 → 红点 + 红色加粗放大「未锁车」。
 * - 行驶中 → 不展示锁车指示，避免行车途中常驻告警。
 * - 锁状态未知（null）→ 不展示，避免把未知状态说成已锁或未锁。
 */
object ParkedLockReminderPolicy {
    const val UNLOCKED_WARNING_TEXT = "未锁车"
    const val LOCKED_TEXT = "已锁车"
    const val PARKED_TEXT = "已驻车"

    /** 右上角锁车状态的三种展示形态。 */
    enum class LockBadge {
        /** 已驻车且未锁车：红点 + 红色加大「未锁车」。 */
        UNLOCKED_WARNING,

        /** 已驻车且已锁车：绿点 + 灰色小字「已锁车」。 */
        LOCKED,

        /** 行驶中或锁状态未知：不展示。 */
        HIDDEN
    }

    fun badge(locked: Boolean?, driving: Boolean?): LockBadge = when {
        driving == true -> LockBadge.HIDDEN
        locked == true -> LockBadge.LOCKED
        locked == false -> LockBadge.UNLOCKED_WARNING
        else -> LockBadge.HIDDEN
    }

    /** 只有驻车且未锁车才进入红色告警形态。 */
    fun shouldWarnUnlocked(locked: Boolean?, driving: Boolean?): Boolean =
        badge(locked, driving) == LockBadge.UNLOCKED_WARNING

    /**
     * 首页顶部状态胶囊的文案：驻车且未锁车时跟随锁车口径显示「未锁车」，
     * 其余情况沿用驻车/行驶状态（缺省为「已驻车」）。
     */
    fun homeStatusLabel(drivingLabel: String?, isMoving: Boolean, locked: Boolean?): String =
        if (!isMoving && locked == false) {
            UNLOCKED_WARNING_TEXT
        } else {
            drivingLabel ?: PARKED_TEXT
        }
}
