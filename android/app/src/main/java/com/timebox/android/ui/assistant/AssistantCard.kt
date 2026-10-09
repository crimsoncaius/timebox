package com.timebox.android.ui.assistant

import kotlinx.serialization.json.*
import java.time.*

data class AssistantBlock(val lane: String, val start: Instant, val end: Instant, val running: Boolean,
    val minutes: Int, val minutesInDate: Int, val title: String, val taskType: String)
data class AssistantType(val path: String, val planned: Double, val actual: Double)

/** Display data only: descriptions and Supporting Notes never enter the card model. */
data class AssistantCard(val id: String, val date: LocalDate, val endDate: LocalDate, val zone: ZoneId,
    val readAt: Instant, val lane: String, val filter: String?, val future: Boolean,
    val blocks: List<AssistantBlock> = emptyList(), val types: List<AssistantType>? = null,
    val weekdays: List<Int>? = null, val actualUnavailable: String? = null) {
    val label: String get() = if (date == endDate) date.toString() else "$date – $endDate"
    val todayAtRead: LocalDate get() = readAt.atZone(zone).toLocalDate()
    val rootTypes: List<AssistantType> get() = types.orEmpty().filter { row -> types.orEmpty().none { row.path.startsWith(it.path + "/") } }
    companion object {
        fun parse(data: JsonObject): AssistantCard {
            fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.let { check(it.isString); it.content }
            fun JsonObject.optional(key: String) = get(key)?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
            val version = data.getValue("schema_version").jsonPrimitive.let { check(!it.isString); it.int }
            check(version in 2..3)
            val id = data.text("snapshot_id").also { check(it.isNotBlank()) }
            val date = LocalDate.parse(data.text(if (version == 3) "start" else "date"))
            val end = if (version == 3) LocalDate.parse(data.text("end")) else date
            check(!end.isBefore(date))
            val zone = ZoneId.of(data.text("reporting_timezone"))
            val readAt = Instant.parse(data.text("read_at"))
            val lane = data.text("lane").also { check(it in listOf("planned", "actual", "both")) }
            val blocks = if (version == 2) data.getValue("blocks").jsonArray.map { value ->
                val row = value.jsonObject
                val blockLane = row.text("lane").also { check(it in listOf("planned", "actual")); check(lane == "both" || lane == it) }
                val start = Instant.parse(row.text("start_at"))
                val finish = Instant.parse(row.text("end_at"))
                check(!finish.isBefore(start))
                val minutes = row.getValue("duration_minutes").jsonPrimitive.int.also { check(it >= 0) }
                val inDate = row.getValue("minutes_in_date").jsonPrimitive.int.also { check(it in 0..minutes) }
                val type = row.text("task_type")
                AssistantBlock(blockLane, start, finish, row.getValue("running").jsonPrimitive.boolean, minutes, inDate,
                    row.optional("name")?.takeIf { it.isNotBlank() } ?: row.optional("task_title")?.takeIf { it.isNotBlank() } ?: type, type)
            } else emptyList()
            // Bucketed reads still present totals. Hierarchy rows already include descendants;
            // sum periods of each path, never sum children into an existing parent again.
            val types = if (version == 3) data.getValue("types").jsonArray.map { value ->
                val row = value.jsonObject
                fun seconds(key: String) = row[key]?.takeUnless { it == JsonNull }?.jsonPrimitive?.double?.also { check(it.isFinite() && it >= 0) } ?: 0.0
                AssistantType(row.text("task_type"), seconds("planned_seconds"), seconds("actual_seconds"))
            }.groupBy { it.path }.map { (path, rows) -> AssistantType(path, rows.sumOf { it.planned }, rows.sumOf { it.actual }) } else null
            return AssistantCard(id, date, end, zone, readAt, lane, data.optional("task_type"),
                data.getValue("recurring_not_materialized").jsonPrimitive.boolean, blocks, types,
                data["weekdays"]?.takeUnless { it == JsonNull }?.jsonArray?.map { it.jsonPrimitive.int.also { day -> check(day in 0..6) } },
                data.optional("actual_unavailable_reason"))
        }
    }
}
