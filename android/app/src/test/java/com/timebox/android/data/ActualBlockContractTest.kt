package com.timebox.android.data

import com.timebox.android.data.remote.ApiFactory
import com.timebox.android.data.remote.DayDto
import com.timebox.android.ui.day.parseActualInput
import com.timebox.android.ui.day.resolveActualMinute
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActualBlockContractTest {
    @Test
    fun `day renders a cross-midnight Actual projection without mutating its authoritative interval`() {
        val dto = ApiFactory.json.decodeFromString(
            DayDto.serializer(),
            """{
              "id":1,"date":"2026-08-30","start_hour":0,"end_hour":24,"show_full_day":true,
              "time_blocks":[{
                "id":44,"lane":"planned","task_type_id":3,"task_type":{"id":3,"name":"coding"},
                "task_id":10,"task":{"id":10,"title":"Ship Android","status":"open","task_type_id":3},
                "start_minute":540,"end_minute":600
              }],"planned_minutes":60,"actual_minutes":15,
              "actual_blocks":[{
                "date":"2026-08-30","start_minute":1425,"end_minute":1440,"duration_minutes":15,
                "actual_block":{
                  "id":44,"task_type_id":3,"task_type":{"id":3,"name":"coding"},
                  "task_id":10,"task":{"id":10,"title":"Ship Android","status":"open","task_type_id":3},
                  "planned_block_id":8,"start_at":"2026-08-30T15:45:00Z","end_at":"2026-08-30T16:15:00Z",
                  "created_at":"2026-08-30T15:45:00Z","updated_at":"2026-08-30T16:15:00Z"
                }
              }],
              "meta":{"timezone":"Asia/Singapore","today":"2026-08-30","server_now_iso":"2026-08-30T23:50:00+08:00"}
            }""",
        ).toModel()

        val projection = dto.actualBlocks.single()
        assertEquals(1425, projection.startMinute)
        assertEquals(1440, projection.endMinute)
        assertEquals(Instant.parse("2026-08-30T15:45:00Z"), projection.actualBlock.startAt)
        assertEquals(Instant.parse("2026-08-30T16:15:00Z"), projection.actualBlock.endAt)
        val actualTimelineBlock = dto.blocks.single { it.lane == Lane.Actual }
        val plannedTimelineBlock = dto.blocks.single { it.lane == Lane.Planned }
        assertEquals(44, actualTimelineBlock.actualBlockId)
        assertEquals(-44, actualTimelineBlock.id)
        assertNotEquals(plannedTimelineBlock.id, actualTimelineBlock.id)
    }

    @Test
    fun `Actual editor parses app-zone minutes across midnight and rejects invalid input`() {
        val zone = ZoneId.of("Asia/Singapore")
        assertEquals(Instant.parse("2026-08-30T15:59:00Z"), parseActualInput("2026-08-30 23:59", zone))
        assertEquals(Instant.parse("2026-08-30T16:01:00Z"), parseActualInput("2026-08-31 00:01", zone))
        assertNull(parseActualInput("2026-02-30 10:00", zone))
        assertNull(parseActualInput("2026-08-30 24:00", zone))
        assertNull(parseActualInput("2026-03-08 02:30", ZoneId.of("America/New_York")))
        assertNull(resolveActualMinute(LocalDate.parse("2026-03-08"), 150, ZoneId.of("America/New_York")))
    }
}
