package com.ticketkeep.app.util

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验 DateBounds 合理日期范围策略。
 */
class DateBoundsTest {
    @Test
    fun allowsTodayAndNearFuture() {
        assertTrue(DateBounds.isAllowed(LocalDate.now()))
        assertTrue(DateBounds.isAllowed(LocalDate.of(1990, 1, 1)))
        assertTrue(DateBounds.isAllowed(LocalDate.now().plusYears(40)))
    }

    @Test
    fun rejectsTooOldOrTooFar() {
        assertFalse(DateBounds.isAllowed(LocalDate.of(1989, 12, 31)))
        assertFalse(DateBounds.isAllowed(LocalDate.now().plusYears(40).plusDays(1)))
    }

    @Test
    fun allowsExactMinAndExactMax() {
        assertTrue(DateBounds.isAllowed(DateBounds.MIN))
        assertTrue(DateBounds.isAllowed(DateBounds.max()))
    }

    @Test
    fun rejectsDayBeforeMin() {
        assertFalse(DateBounds.isAllowed(DateBounds.MIN.minusDays(1)))
    }

    @Test
    fun rejectsDayAfterMax() {
        assertFalse(DateBounds.isAllowed(DateBounds.max().plusDays(1)))
    }
}
