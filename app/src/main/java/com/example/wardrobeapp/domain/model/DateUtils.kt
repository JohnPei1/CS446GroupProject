package com.example.wardrobeapp.domain.model

import java.util.Calendar
import java.util.TimeZone

/**
 * Two distinct operations that must never be conflated -- doing so caused a real, reported bug:
 * a "Plan for August 8" button reading "August 7", and a selected calendar day silently fetching
 * the wrong day's forecast. Applying local-time-zone interpretation to a value that's already a
 * day-key (e.g. from Android's DatePicker, which returns UTC midnight of the visually-picked
 * date by its own convention) shifts it a day backward for anyone west of UTC (EST/EDT and
 * similar), because that instant's LOCAL calendar day is still the previous one.
 *
 * Rule of thumb: [normalizeToUtcDay] is for a genuine wall-clock reading (typically
 * System.currentTimeMillis()) becoming a day for the first time. [floorToUtcMidnight] is for
 * everything else -- a date-picker result, or any `date`/`day` parameter passed down from a
 * caller that already resolved it.
 */

/**
 * Converts a genuine wall-clock instant into a day-key: UTC midnight of that instant's LOCAL
 * calendar day. Call this exactly once, at the point a real clock reading first becomes a "day"
 * -- never on a value that's already a day-key, or it will shift backward for time zones west of
 * UTC once the local afternoon/evening has already rolled past UTC's midnight.
 */
fun normalizeToUtcDay(instantMillis: Long): Long {
    val local = Calendar.getInstance()
    local.timeInMillis = instantMillis
    val year = local.get(Calendar.YEAR)
    val month = local.get(Calendar.MONTH)
    val day = local.get(Calendar.DAY_OF_MONTH)

    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.clear()
    utc.set(year, month, day)
    return utc.timeInMillis
}

/**
 * Idempotent floor for a value that's already a day-key -- from Android's DatePicker, or a
 * `date`/`day` parameter passed down from a caller that already resolved it via
 * [normalizeToUtcDay]. Never re-interprets in the local time zone, so it's always safe to call
 * again on an already-normalized value without shifting it.
 */
fun floorToUtcMidnight(dayKeyMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.timeInMillis = dayKeyMillis
    utc.set(Calendar.HOUR_OF_DAY, 0)
    utc.set(Calendar.MINUTE, 0)
    utc.set(Calendar.SECOND, 0)
    utc.set(Calendar.MILLISECOND, 0)
    return utc.timeInMillis
}
