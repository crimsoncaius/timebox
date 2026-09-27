package com.timebox.android.ui.assistant

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AssistantCardTest {
    @Test fun `bucketed hierarchy totals do not double count children`() {
        val card = AssistantCard.parse(Json.parseToJsonElement("""{
          "schema_version":3,"snapshot_id":"range","start":"2026-09-20","end":"2026-09-21",
          "reporting_timezone":"Asia/Singapore","read_at":"2026-09-21T01:41:00Z","lane":"both",
          "task_type":"Work","recurring_not_materialized":false,"types":[
            {"task_type":"Work","period":"2026-09-20","planned_seconds":3600,"actual_seconds":1800},
            {"task_type":"Work","period":"2026-09-21","planned_seconds":1800,"actual_seconds":600},
            {"task_type":"Work/Build","period":"2026-09-20","planned_seconds":3600,"actual_seconds":1800}
          ]} """).jsonObject)
        assertEquals(1, card.rootTypes.size)
        assertEquals(5400.0, card.rootTypes.single().planned, 0.0)
        assertEquals(2400.0, card.rootTypes.single().actual, 0.0)
        assertEquals(2, card.types!!.size)
        assertEquals("2026-09-21", card.todayAtRead.toString())
    }

    @Test fun `running midnight block is a frozen snapshot without private text`() {
        val data = Json.parseToJsonElement("""{
          "schema_version":2,"snapshot_id":"block","date":"2026-09-21",
          "reporting_timezone":"Asia/Singapore","read_at":"2026-09-21T01:00:00Z","lane":"actual",
          "task_type":null,"recurring_not_materialized":false,"blocks":[
            {"lane":"actual","start_at":"2026-09-20T23:00:00+08:00","end_at":"2026-09-21T09:00:00+08:00",
             "running":true,"duration_minutes":600,"minutes_in_date":540,"name":null,"task_title":null,
             "task_type":"Sleep","supporting_note":"SECRET NOTE","task_description":"SECRET DESCRIPTION"}
          ]} """).jsonObject
        val card = AssistantCard.parse(data)
        assertEquals(600, card.blocks.single().minutes)
        assertEquals(540, card.blocks.single().minutesInDate)
        assertTrue(card.blocks.single().running)
        assertFalse(card.toString().contains("SECRET"))
        assertEquals(card.readAt, card.blocks.single().end)
    }
}
