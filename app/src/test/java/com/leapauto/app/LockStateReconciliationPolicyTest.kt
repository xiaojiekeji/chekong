package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockStateReconciliationPolicyTest {

    private val window = 60_000L

    @Test
    fun `fresh optimistic unlock wins over stale locked telemetry`() {
        assertEquals(
            false,
            LockStateReconciliationPolicy.resolve(
                telemetryLocked = true,
                optimisticLocked = false,
                optimisticAgeMs = 5_000L,
                windowMs = window
            )
        )
    }

    @Test
    fun `fresh optimistic lock wins over stale unlocked telemetry`() {
        assertEquals(
            true,
            LockStateReconciliationPolicy.resolve(
                telemetryLocked = false,
                optimisticLocked = true,
                optimisticAgeMs = 2_000L,
                windowMs = window
            )
        )
    }

    @Test
    fun `expired optimistic state falls back to telemetry`() {
        assertEquals(
            true,
            LockStateReconciliationPolicy.resolve(
                telemetryLocked = true,
                optimisticLocked = false,
                optimisticAgeMs = window + 1,
                windowMs = window
            )
        )
    }

    @Test
    fun `state at the window boundary is still trusted`() {
        assertEquals(
            false,
            LockStateReconciliationPolicy.resolve(
                telemetryLocked = true,
                optimisticLocked = false,
                optimisticAgeMs = window,
                windowMs = window
            )
        )
    }

    @Test
    fun `missing optimistic record means telemetry is the answer`() {
        assertEquals(
            true,
            LockStateReconciliationPolicy.resolve(
                telemetryLocked = true,
                optimisticLocked = null,
                optimisticAgeMs = null,
                windowMs = window
            )
        )
    }

    @Test
    fun `unknown telemetry still shows the optimistic target in window`() {
        assertEquals(
            false,
            LockStateReconciliationPolicy.resolve(
                telemetryLocked = null,
                optimisticLocked = false,
                optimisticAgeMs = 1_000L,
                windowMs = window
            )
        )
        assertNull(
            LockStateReconciliationPolicy.resolve(
                telemetryLocked = null,
                optimisticLocked = null,
                optimisticAgeMs = null,
                windowMs = window
            )
        )
    }

    @Test
    fun `negative age is treated as expired to avoid trusting clock skew`() {
        assertEquals(
            true,
            LockStateReconciliationPolicy.resolve(
                telemetryLocked = true,
                optimisticLocked = false,
                optimisticAgeMs = -1_000L,
                windowMs = window
            )
        )
    }
}
