package com.timebox.android.data

import com.timebox.android.data.remote.ActivitySnapshotDto
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.data.remote.TaskTypeDto
import com.timebox.android.ui.day.savedBlockRangeAvailable
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityDayTest {
    @Test
    fun `projecting Today without a fetched Day keeps the server clock for Actual placement`() {
        val at = "2026-08-30T00:00:00Z"
        val row = ActualBlockDto(44, 3, TaskTypeDto(3, "coding"), startAt = at, endAt = "2026-08-30T01:00:00Z",
            createdAt = at, updatedAt = at)
        val snapshot = ActivitySnapshotDto(cursor = 1, serverAt = "2026-08-30T04:00:00Z",
            reportingTimezone = "Asia/Singapore", offlineReady = true, current = null, records = listOf(row))

        val day = snapshot.projectDay(LocalDate.parse("2026-08-30"), previous = null, elapsedRealtime = { 0L })

        assertEquals(LocalDate.parse("2026-08-30"), day.today)
        assertEquals(12 * 60, day.serverNowMinute)
        assertTrue(savedBlockRangeAvailable(day, 44, 8 * 60 + 15, 9 * 60 + 15, lane = Lane.Actual))
    }
}
