package com.leapauto.app

/**
 * 锁车状态合并策略：把"刚执行过的控锁动作"和"云端遥测"合并成界面要显示的最终状态。
 *
 * 背景：云端车身信号有延迟。用户在小卡片/快捷开关上解锁后，组件已经把本地目标状态写进快照，
 * 但紧接着打开的 App 主页如果只信云端遥测，就会显示成"已上锁"，与组件自相矛盾。
 * 因此规定：只要本地乐观状态还在保护窗口内，就以它为准；窗口过期后完全信任遥测。
 */
object LockStateReconciliationPolicy {

    /**
     * @param telemetryLocked 云端遥测返回的锁车状态，未知为 null。
     * @param optimisticLocked 本地记录的控锁目标状态，没有记录为 null。
     * @param optimisticAgeMs 该记录距现在的毫秒数，没有记录或时间戳缺失为 null。
     * @param windowMs 保护窗口，超过后不再采信本地状态。
     */
    fun resolve(
        telemetryLocked: Boolean?,
        optimisticLocked: Boolean?,
        optimisticAgeMs: Long?,
        windowMs: Long
    ): Boolean? {
        if (optimisticLocked == null || optimisticAgeMs == null) return telemetryLocked
        if (optimisticAgeMs < 0 || optimisticAgeMs > windowMs) return telemetryLocked
        return optimisticLocked
    }
}
