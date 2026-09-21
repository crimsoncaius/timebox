package com.timebox.android.ui.assistant

import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class AssistantPlanRow(val start: Int, val end: Int, val name: String?, val taskType: String, val taskTitle: String?) {
    val title: String get() = name?.takeIf { it.isNotBlank() } ?: taskTitle?.takeIf { it.isNotBlank() } ?: taskType
}
data class AssistantPlan(val id: String, val date: LocalDate, val zone: ZoneId, val readAt: Instant, val rows: List<AssistantPlanRow>) {
    companion object {
        fun parse(data: JsonObject): AssistantPlan {
            fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.let { check(it.isString); it.content }
            fun JsonObject.optional(key: String): String? = if (getValue(key) == JsonNull) null else text(key)
            check(data.getValue("schema_version").jsonPrimitive.let { !it.isString && it.int == 1 })
            val id = data.text("snapshot_id").also { check(it.isNotBlank()) }
            val rows = data.getValue("planned_blocks").jsonArray.map { value ->
                val row = value.jsonObject
                val start = row.getValue("start_minute").jsonPrimitive.let { check(!it.isString); it.int }
                val end = row.getValue("end_minute").jsonPrimitive.let { check(!it.isString); it.int }
                check(start in 0..1439 && end in 1..1440 && start < end)
                row.getValue("task_id").let { if (it != JsonNull) check(!it.jsonPrimitive.isString && it.jsonPrimitive.int > 0) }
                AssistantPlanRow(start, end, row.optional("name"), row.text("task_type"), row.optional("task_title"))
            }
            return AssistantPlan(id, LocalDate.parse(data.text("date")), ZoneId.of(data.text("reporting_timezone")), Instant.parse(data.text("read_at")), rows)
        }
    }
}
