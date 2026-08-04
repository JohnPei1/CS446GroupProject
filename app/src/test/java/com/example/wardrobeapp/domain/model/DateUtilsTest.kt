package com.example.wardrobeapp.domain.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DateUtilsTest {
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun normalizeToUtcDay_usesLocalDayNotUtcDayLateInTheEvening() {
        // 9pm EDT on Aug 3 is already 1am Aug 4 in UTC -- normalizeToUtcDay must still resolve
        // to Aug 3, the user's actual local day. Reproduces the original reported bug directly
        // (a "today" or "August 4" label shown in the evening when it was still August 3 locally).
        val nineEdt = utcMillis(2026, Calendar.AUGUST, 4, hour = 1)
        assertEquals(utcMillis(2026, Calendar.AUGUST, 3, hour = 0), normalizeToUtcDay(nineEdt))
    }

    @Test
    fun floorToUtcMidnight_isIdempotentOnAnAlreadyNormalizedValue() {
        // Reproduces the second reported bug: a value already produced by normalizeToUtcDay (or
        // Android's DatePicker, which returns UTC midnight of the visually-picked date by its
        // own convention) must not shift when floored -- this is what made a picked "August 8"
        // display and get looked up as "August 7".
        val aug8UtcMidnight = utcMillis(2026, Calendar.AUGUST, 8, hour = 0)
        assertEquals(aug8UtcMidnight, floorToUtcMidnight(aug8UtcMidnight))
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(year, month, day, hour, 0, 0)
        return cal.timeInMillis
    }
}
